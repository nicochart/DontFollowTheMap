package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A sheep Herobrine took over. Same model, same texture, same name: nothing gives it away, except that it stares.
 * It follows its victim at a distance, charges when they come too close (or after staring long enough), bites, runs away,
 * and turns back into a normal sheep once out of sight.
 */
public class PossessedSheepEntity extends Sheep
{
    private enum Phase {STARE, ATTACK, FLEE}

    private static final int MAX_STARE_TICKS = 20 * 60 * 4;
    private static final int CLOSE_STARE_TICKS_BEFORE_ATTACK = 20 * 15;
    private static final int ATTACK_TIMEOUT = 20 * 8;
    private static final int MIN_FLEE_TICKS = 20 * 6;

    private Phase phase = Phase.STARE;
    @Nullable private UUID victimId;
    private int phaseTicks;
    private int closeStareTicks;

    public PossessedSheepEntity(EntityType<? extends PossessedSheepEntity> type, Level level) {super(type, level);}

    public static AttributeSupplier.Builder createAttributes() {return Sheep.createAttributes().add(Attributes.ATTACK_DAMAGE, 4.0D).add(Attributes.FOLLOW_RANGE, 40.0D);}

    @Nullable public static PossessedSheepEntity possess(Sheep sheep, ServerPlayer victim)
    {
        return sheep.convertTo(DFTMEntities.POSSESSED_SHEEP, ConversionParams.single(sheep, false, false), possessed ->
        {
            possessed.setColor(sheep.getColor());
            possessed.setSheared(sheep.isSheared());
            possessed.victimId = victim.getUUID();
        });
    }

    /** Still just staring (it hasn't charged yet). */
    public boolean isStaring() {return this.phase == Phase.STARE;}

    @Override protected void registerGoals()
    {
        //the sheep goals are created (the grass eating one is needed by Sheep itself), then replaced: the possession drives everything
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        this.phaseTicks++;
        //spawned from an egg: it picks whoever is around
        if (this.victimId == null && level.getNearestPlayer(this, 32.0D) instanceof Player nearest) {this.victimId = nearest.getUUID();}
        Player victim = this.victimId == null ? null : level.getPlayerByUUID(this.victimId);
        if (victim == null || !victim.isAlive() || victim.isSpectator() || this.distanceToSqr(victim) > 48.0D * 48.0D)
        {
            this.release(level);
            return;
        }

        switch (this.phase)
        {
            case STARE ->
            {
                this.stareAt(victim);
                double distance = this.distanceTo(victim);
                if (distance > 14.0D) {this.getNavigation().moveTo(victim, 0.9D);}
                else if (distance < 10.0D) {this.getNavigation().stop();}
                if (distance < 10.0D) {this.closeStareTicks++;}
                if (distance < 3.5D || this.closeStareTicks > CLOSE_STARE_TICKS_BEFORE_ATTACK) {this.setPhase(Phase.ATTACK);}
                else if (this.phaseTicks > MAX_STARE_TICKS) {this.release(level);}
            }
            case ATTACK ->
            {
                this.stareAt(victim);
                if (this.phaseTicks % 5 == 1) {this.getNavigation().moveTo(victim, 2.0D);}
                if (this.isWithinMeleeAttackRange(victim) && this.hasLineOfSight(victim))
                {
                    this.doHurtTarget(level, victim);
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.POSSESSED_SHEEP_ATTACK, SoundSource.HOSTILE, 1.0F, 1.0F);
                    this.setPhase(Phase.FLEE);
                }
                else if (this.phaseTicks > ATTACK_TIMEOUT) {this.setPhase(Phase.FLEE);}
            }
            case FLEE ->
            {
                if (this.phaseTicks % 20 == 1 || this.getNavigation().isDone())
                {
                    Vec3 away = DefaultRandomPos.getPosAway(this, 16, 7, victim.position());
                    if (away != null) {this.getNavigation().moveTo(away.x, away.y, away.z, 2.0D);}
                }
                boolean outOfSight = this.distanceTo(victim) > 16.0D || (victim instanceof ServerPlayer player && HauntSpots.isHiddenFrom(player, this.getEyePosition()));
                if ((this.phaseTicks > MIN_FLEE_TICKS && outOfSight) || this.phaseTicks > MIN_FLEE_TICKS * 4) {this.release(level);}
            }
        }
    }

    /** Head and body turned towards the victim. */
    private void stareAt(Player victim)
    {
        this.getLookControl().setLookAt(victim, 30.0F, 90.0F);
        if (this.getNavigation().isDone())
        {
            float targetYaw = (float) (Mth.atan2(victim.getZ() - this.getZ(), victim.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            this.setYBodyRot(Mth.approachDegrees(this.yBodyRot, targetYaw, 6.0F));
        }
    }

    private void setPhase(Phase phase)
    {
        this.phase = phase;
        this.phaseTicks = 0;
        this.getNavigation().stop();
    }

    /** Herobrine lets go: a perfectly normal sheep again. */
    private void release(ServerLevel level)
    {
        if (this.isRemoved()) {return;}
        this.convertTo(EntityTypes.SHEEP, ConversionParams.single(this, false, false), sheep ->
        {
            sheep.setColor(this.getColor());
            sheep.setSheared(this.isSheared());
        });
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive() && this.phase == Phase.STARE && source.getEntity() instanceof Player player)
        {
            this.victimId = player.getUUID();
            this.setPhase(Phase.ATTACK);
        }
        return hurt;
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        InteractionResult result = super.mobInteract(player, hand);
        //shearing it is not a good idea
        if (result.consumesAction() && this.phase == Phase.STARE && !this.level().isClientSide()) {this.setPhase(Phase.ATTACK);}
        return result;
    }

    @Override public boolean isFood(ItemStack stack) {return false;}
    @Override public boolean canFallInLove() {return false;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override protected SoundEvent getAmbientSound() {return DFTMSounds.POSSESSED_SHEEP_AMBIENT;}
    @Override public int getAmbientSoundInterval() {return 200;}

    @Override protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putString("Phase", this.phase.name());
        output.putInt("PhaseTicks", this.phaseTicks);
        output.putInt("CloseStareTicks", this.closeStareTicks);
        output.storeNullable("Victim", UUIDUtil.CODEC, this.victimId);
    }

    @Override protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        try {this.phase = Phase.valueOf(input.getStringOr("Phase", Phase.STARE.name()));}
        catch (IllegalArgumentException e) {this.phase = Phase.STARE;}
        this.phaseTicks = input.getIntOr("PhaseTicks", 0);
        this.closeStareTicks = input.getIntOr("CloseStareTicks", 0);
        this.victimId = input.read("Victim", UUIDUtil.CODEC).orElse(null);
    }
}
