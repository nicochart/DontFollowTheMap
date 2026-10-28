package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Herobrine that is not really there. It never attacks (except the shades summoned during the fight):
 * it stares at one player, rumbles when they come close and vanishes before they can reach it.
 */
public class HerobrineApparitionEntity extends PathfinderMob
{
    public enum Mode
    {
        /** Seen from afar: vanishes when approached, or when stared at for too long. */
        WATCH,
        /** A brief flash (kill apparition, bed scare). */
        FLASH,
        /** Summoned by Herobrine during the fight: rushes at the player and vanishes on contact. */
        SHADE
    }

    public static final float VANISH_DISTANCE = 10.0F;
    private static final int STARE_TICKS_TO_VANISH = 70;

    private Mode mode = Mode.WATCH;
    @Nullable private UUID targetId;
    private int lifetime = 20 * 90;
    private int stareTicks;
    private boolean leavesMap;
    private boolean vanished;

    public HerobrineApparitionEntity(EntityType<? extends HerobrineApparitionEntity> type, Level level)
    {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.34D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static HerobrineApparitionEntity spawn(ServerLevel level, Vec3 pos, ServerPlayer target, Mode mode, int lifetime)
    {
        HerobrineApparitionEntity apparition = new HerobrineApparitionEntity(DFTMEntities.APPARITION, level);
        apparition.mode = mode;
        apparition.targetId = target.getUUID();
        apparition.lifetime = lifetime;
        apparition.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        apparition.faceTowards(target);
        level.addFreshEntity(apparition);
        return apparition;
    }

    public void setLeavesMap(boolean leavesMap) {this.leavesMap = leavesMap;}

    public Mode getMode() {return this.mode;}

    @Nullable public ServerPlayer getTargetPlayer()
    {
        return this.targetId != null && this.level() instanceof ServerLevel level && level.getPlayerByUUID(this.targetId) instanceof ServerPlayer player ? player : null;
    }

    @Override public void tick()
    {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.vanished && !this.isRemoved()) {this.serverTick(level);}
    }

    private void serverTick(ServerLevel level)
    {
        ServerPlayer target = this.getTargetPlayer();
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != level || this.distanceToSqr(target) > 96.0D * 96.0D)
        {
            this.vanish(level, false);
            return;
        }
        if (this.tickCount >= this.lifetime)
        {
            this.vanish(level, this.mode != Mode.FLASH);
            return;
        }

        this.faceTowards(target);
        double distance = this.distanceTo(target);
        if (this.mode == Mode.WATCH)
        {
            if (distance < VANISH_DISTANCE) {this.vanish(level, true); return;}
            //the low rumble, louder as the player gets closer
            if (this.tickCount % 50 == 0 && distance < 28.0D) {level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.PRESENCE, SoundSource.HOSTILE, 2.0F, 0.85F + this.random.nextFloat() * 0.15F);}
            if (this.tickCount % 4 == 0) {this.stareTicks = this.isStaredAtBy(target) ? this.stareTicks + 4 : Math.max(0, this.stareTicks - 2);}
            if (this.stareTicks >= STARE_TICKS_TO_VANISH) {this.vanish(level, true);}
        }
        else if (this.mode == Mode.SHADE)
        {
            if (this.tickCount % 10 == 0) {this.getNavigation().moveTo(target, 1.4D);}
            if (distance < 1.4D)
            {
                target.hurtServer(level, this.damageSources().mobAttack(this), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50), this);
                this.vanish(level, true);
            }
        }
    }

    private boolean isStaredAtBy(Player player)
    {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 toThis = new Vec3(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
        double length = toThis.length();
        return length > 0.0D && view.dot(toThis.scale(1.0D / length)) > 1.0D - 0.03D / Math.max(1.0D, length / 8.0D) && player.hasLineOfSight(this);
    }

    public void faceTowards(Entity target)
    {
        double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
        double dy = target.getEyeY() - this.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setXRot(pitch);
    }

    public void vanish(ServerLevel level, boolean withSmoke)
    {
        if (this.vanished) {return;}
        this.vanished = true;
        if (withSmoke)
        {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.9D, this.getZ(), 25, 0.25D, 0.6D, 0.25D, 0.02D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.VANISH, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        ServerPlayer target = this.getTargetPlayer();
        boolean targetAround = target != null && target.level() == level && this.distanceToSqr(target) < 64.0D * 64.0D;
        if (this.leavesMap && targetAround) {SealEvents.leaveMap(level, this.position(), target);}
        //vanishing in a puff of smoke means the player came close or stared at it: they saw it
        if (this.mode == Mode.WATCH && withSmoke && targetAround) {DFTMAdvancements.award(target, "watched");}
        this.discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        if (this.mode == Mode.SHADE || source.getEntity() instanceof Player) {this.vanish(level, true);}
        return false;
    }

    @Override public boolean isPushable() {return false;}
    @Override public void push(Entity entity) {}
    @Override protected void doPush(Entity entity) {}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override public boolean shouldShowName() {return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
}
