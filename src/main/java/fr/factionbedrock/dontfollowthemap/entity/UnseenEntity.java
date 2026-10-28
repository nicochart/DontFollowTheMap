package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Something invisible that walks to the items the player just mined, takes them and runs away.
 * Only its footsteps and the dust it raises give it away. Hitting it makes it drop what it took this time;
 * everything else is kept, and given back when Herobrine is defeated.
 */
public class UnseenEntity extends PathfinderMob
{
    private static final int MAX_STEALING_TICKS = 20 * 6, MAX_FLEEING_TICKS = 20 * 8;

    @Nullable private UUID victimId;
    private final List<ItemEntity> targets = new ArrayList<>();
    private final List<ItemStack> carried = new ArrayList<>();
    private int fleeingTicks = -1;
    private boolean gone;

    public UnseenEntity(EntityType<? extends UnseenEntity> type, Level level)
    {
        super(type, level);
        this.setInvisible(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** Comes for the items dropped at the given position. @return false if there was nothing to take or nowhere to come from. */
    public static boolean comeForDrops(ServerPlayer player, BlockPos brokenPos)
    {
        ServerLevel level = player.level();
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(brokenPos).inflate(1.0D), item -> item.isAlive() && item.getAge() < 10 && !item.getItem().isEmpty());
        if (drops.isEmpty()) {return false;}
        BlockPos spot = HauntSpots.findHiddenSpotAround(player, brokenPos, 3, 5, 3);
        if (spot == null) {return false;}
        spawn(level, spot, player, drops);
        return true;
    }

    public static UnseenEntity spawn(ServerLevel level, BlockPos spot, ServerPlayer victim, List<ItemEntity> drops)
    {
        UnseenEntity unseen = new UnseenEntity(DFTMEntities.UNSEEN, level);
        unseen.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, victim.getRandom().nextFloat() * 360.0F, 0.0F);
        unseen.victimId = victim.getUUID();
        unseen.targets.addAll(drops);
        //a head start, so that the player doesn't pick them up right away
        for (ItemEntity drop : drops) {drop.setPickUpDelay(30);}
        level.addFreshEntity(unseen);
        return unseen;
    }

    @Nullable private ServerPlayer getVictim(ServerLevel level) {return this.victimId != null && level.getPlayerByUUID(this.victimId) instanceof ServerPlayer player ? player : null;}

    @Override public void tick()
    {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.gone && !this.isRemoved()) {this.serverTick(level);}
    }

    private void serverTick(ServerLevel level)
    {
        ServerPlayer victim = this.getVictim(level);
        if (victim == null || victim.level() != level) {this.leave(level, false); return;}

        //the dust it raises when it walks
        if (this.tickCount % 3 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D)
        {
            BlockState ground = level.getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
            if (!ground.isAir()) {level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.05D, this.getZ(), 2, 0.15D, 0.0D, 0.15D, 0.0D);}
        }

        if (this.fleeingTicks < 0)
        {
            this.targets.removeIf(item -> !item.isAlive());
            ItemEntity target = this.targets.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (target == null || this.tickCount > MAX_STEALING_TICKS) {this.fleeingTicks = 0;}
            else if (this.distanceToSqr(target) < 1.7D) {this.steal(level, victim, target);}
            else if (this.tickCount % 10 == 1) {this.getNavigation().moveTo(target, 1.6D);}
        }
        else
        {
            this.fleeingTicks++;
            if (this.fleeingTicks % 20 == 1 || this.getNavigation().isDone())
            {
                Vec3 away = DefaultRandomPos.getPosAway(this, 20, 7, victim.position());
                if (away != null) {this.getNavigation().moveTo(away.x, away.y, away.z, 1.7D);}
            }
            if (this.fleeingTicks > MAX_FLEEING_TICKS || this.distanceTo(victim) > 24.0D) {this.leave(level, false);}
        }
    }

    private void steal(ServerLevel level, ServerPlayer victim, ItemEntity item)
    {
        ItemStack stack = item.getItem().copy();
        level.playSound(null, item.getX(), item.getY(), item.getZ(), DFTMSounds.UNSEEN_STEAL, SoundSource.HOSTILE, 0.6F, 1.0F);
        item.discard();
        this.targets.remove(item);
        this.carried.add(stack.copy());
        //kept with the player's data right away: nothing is lost if the game closes now
        HauntingData.get(victim).addStolen(stack);
    }

    private void leave(ServerLevel level, boolean withSmoke)
    {
        this.gone = true;
        if (withSmoke)
        {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.9D, this.getZ(), 20, 0.25D, 0.6D, 0.25D, 0.02D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.VANISH, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        this.discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        if (!(source.getEntity() instanceof Player) || this.gone) {return false;}
        //caught: it lets go of what it took this time
        ServerPlayer victim = this.getVictim(level);
        for (ItemStack stack : this.carried)
        {
            if (victim != null) {HauntingData.get(victim).removeStolen(stack);}
            this.spawnAtLocation(level, stack);
        }
        this.carried.clear();
        this.leave(level, true);
        return false;
    }

    @Override public boolean isInvisible() {return true;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override public boolean shouldShowName() {return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
}
