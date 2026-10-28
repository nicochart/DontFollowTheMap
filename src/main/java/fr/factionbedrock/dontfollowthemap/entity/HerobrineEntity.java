package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The real one, freed from the seal. Three phases, by remaining health:
 * 1. he hunts the player, blinks behind them and calls lightning on marked spots;
 * 2. he also sends his shades (apparitions that rush at the player);
 * 3. lights out: darkness, faster blinks, more lightning.
 * He leaves (and can be called back from the open seal) if nobody fights him; he dies in a slow, silent ascension.
 */
public class HerobrineEntity extends Monster
{
    public static final int DEATH_DURATION = 60;
    private static final int STRIKE_WARNING_TICKS = 30;
    private static final float STRIKE_DAMAGE = 7.0F, STRIKE_RADIUS = 1.6F;

    private final ServerBossEvent bossEvent = Util.make(new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10),
            event -> event.setDarkenScreen(true).setCreateWorldFog(true));

    @Nullable private BlockPos sealPos;
    private int phase = 1;
    private int blinkCooldown = 60, strikeCooldown = 120, shadeCooldown = 60, darknessCooldown = 0;
    private final List<Vec3> pendingStrikes = new ArrayList<>();
    private int strikeTimer = -1;
    private int idleTicks;
    private boolean defeated;

    public HerobrineEntity(EntityType<? extends HerobrineEntity> type, Level level)
    {
        super(type, level);
        this.xpReward = 150;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 240.0D).add(Attributes.ARMOR, 8.0D).add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FOLLOW_RANGE, 64.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.9D).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public void setSealPos(@Nullable BlockPos sealPos) {this.sealPos = sealPos;}
    @Nullable public BlockPos getSealPos() {return this.sealPos;}
    public int getPhase() {return this.phase;}

    @Override protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.updatePhase(level);
        this.tickStrikes(level);

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || (this.sealPos != null && target.distanceToSqr(Vec3.atCenterOf(this.sealPos)) > Mth.square(SealEvents.ARENA_RADIUS + 16.0D)))
        {
            //nobody to fight: after a while, he goes back where he came from
            if (++this.idleTicks > 20 * 20) {this.leave(level);}
            return;
        }
        this.idleTicks = 0;

        this.blinkCooldown--;
        this.strikeCooldown--;
        this.shadeCooldown--;
        this.darknessCooldown--;
        double distance = this.distanceTo(target);
        if (this.blinkCooldown <= 0 && (distance > 10.0D || this.random.nextInt(this.phase == 3 ? 30 : 60) == 0))
        {
            if (this.blinkBehind(level, target)) {this.blinkCooldown = (this.phase == 3 ? 50 : 90) + this.random.nextInt(60);}
        }
        if (this.strikeCooldown <= 0 && this.strikeTimer < 0)
        {
            this.prepareStrikes(target, this.phase == 3 ? 5 : 3);
            this.strikeCooldown = (this.phase == 3 ? 110 : 160) + this.random.nextInt(80);
        }
        if (this.phase >= 2 && this.shadeCooldown <= 0 && target instanceof ServerPlayer player)
        {
            this.summonShades(level, player, this.phase == 3 ? 3 : 2);
            this.shadeCooldown = 20 * 18;
        }
        if (this.phase == 3 && this.darknessCooldown <= 0)
        {
            for (ServerPlayer player : this.getPlayersInArena(level)) {player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 7), this);}
            this.darknessCooldown = 20 * 15;
        }
    }

    private void updatePhase(ServerLevel level)
    {
        float health = this.getHealth() / this.getMaxHealth();
        int newPhase = health > 0.5F ? 1 : health > 0.25F ? 2 : 3;
        if (newPhase <= this.phase) {return;}
        this.phase = newPhase;
        //a new phase starts right away, announced by a scream and smoke
        this.shadeCooldown = 0;
        this.darknessCooldown = 0;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.STING, SoundSource.HOSTILE, 3.0F, 0.7F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(), 50, 0.5D, 1.0D, 0.5D, 0.05D);
    }

    //blink
    private boolean blinkBehind(ServerLevel level, Entity target)
    {
        Vec3 back = target.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        back = back.lengthSqr() < 1.0E-4D ? Vec3.ZERO : back.normalize();
        List<Vec3> candidates = new ArrayList<>();
        candidates.add(target.position().subtract(back.scale(2.5D)));
        for (int i = 0; i < 6; i++) {candidates.add(target.position().add(Mth.nextDouble(this.random, -3.0D, 3.0D), 0.0D, Mth.nextDouble(this.random, -3.0D, 3.0D)));}
        for (Vec3 candidate : candidates)
        {
            BlockPos ground = HauntSpots.findStandableDown(level, Mth.floor(candidate.x), Mth.floor(candidate.z), Mth.floor(candidate.y) + 2, Mth.floor(candidate.y) - 2);
            if (ground == null || ground.distToCenterSqr(target.position()) < 1.5D) {continue;}
            this.teleportWithSmoke(level, Vec3.atBottomCenterOf(ground));
            this.getLookControl().setLookAt(target);
            return true;
        }
        return false;
    }

    private void teleportWithSmoke(ServerLevel level, Vec3 destination)
    {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.9D, this.getZ(), 20, 0.25D, 0.6D, 0.25D, 0.02D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.BLINK, SoundSource.HOSTILE, 1.0F, 1.0F);
        this.teleportTo(destination.x, destination.y, destination.z);
        this.getNavigation().stop();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, destination.x, destination.y + 0.9D, destination.z, 20, 0.25D, 0.6D, 0.25D, 0.02D);
        level.playSound(null, destination.x, destination.y, destination.z, DFTMSounds.BLINK, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    //lightning
    private void prepareStrikes(LivingEntity target, int count)
    {
        this.pendingStrikes.clear();
        //the first one exactly where the player stands: they must move
        this.pendingStrikes.add(target.position());
        for (int i = 1; i < count; i++) {this.pendingStrikes.add(target.position().add(Mth.nextDouble(this.random, -4.0D, 4.0D), 0.0D, Mth.nextDouble(this.random, -4.0D, 4.0D)));}
        this.strikeTimer = STRIKE_WARNING_TICKS;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.HEROBRINE_CALL, SoundSource.HOSTILE, 1.5F, 1.3F);
    }

    private void tickStrikes(ServerLevel level)
    {
        if (this.strikeTimer < 0) {return;}
        if (this.strikeTimer % 3 == 0)
        {
            for (Vec3 strike : this.pendingStrikes) {level.sendParticles(ParticleTypes.ELECTRIC_SPARK, strike.x, strike.y + 0.1D, strike.z, 6, 0.4D, 0.05D, 0.4D, 0.0D);}
        }
        if (this.strikeTimer-- > 0) {return;}
        for (Vec3 strike : this.pendingStrikes)
        {
            LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (lightning == null) {continue;}
            //visual only: no fire, no charged creepers; the damage is dealt here
            lightning.snapTo(strike);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(strike, strike).inflate(STRIKE_RADIUS, 3.0D, STRIKE_RADIUS), entity -> entity != this && !(entity instanceof HerobrineApparitionEntity)))
            {
                victim.hurtServer(level, this.damageSources().indirectMagic(this, this), STRIKE_DAMAGE);
            }
        }
        this.pendingStrikes.clear();
    }

    //shades
    private void summonShades(ServerLevel level, ServerPlayer player, int count)
    {
        for (int i = 0; i < count; i++)
        {
            Vec3 spot = HauntSpots.findSightingSpot(player, 7.0D, 11.0D, 0.0D, 180.0D);
            if (spot != null) {HerobrineApparitionEntity.spawn(level, spot, player, HerobrineApparitionEntity.Mode.SHADE, 20 * 12);}
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.HEROBRINE_CALL, SoundSource.HOSTILE, 2.0F, 0.8F);
    }

    private List<ServerPlayer> getPlayersInArena(ServerLevel level)
    {
        Vec3 center = this.sealPos != null ? Vec3.atCenterOf(this.sealPos) : this.position();
        return level.getPlayers(player -> !player.isSpectator() && !player.isCreative() && player.distanceToSqr(center) < Mth.square(SealEvents.ARENA_RADIUS + 16.0D));
    }

    /** He fled: the seal stays open, he can be called back. */
    private void leave(ServerLevel level)
    {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.9D, this.getZ(), 40, 0.3D, 0.8D, 0.3D, 0.03D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.VANISH, SoundSource.HOSTILE, 1.5F, 0.8F);
        this.discard();
    }

    //combat
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        if (this.defeated || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_LIGHTNING)) {return false;}
        //arrows often find nothing but smoke
        if (source.is(DamageTypeTags.IS_PROJECTILE) && this.random.nextFloat() < 0.4F && source.getEntity() instanceof LivingEntity shooter && this.blinkBehind(level, shooter)) {return false;}
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.random.nextInt(4) == 0) {this.blinkCooldown = Math.min(this.blinkCooldown, 10);}
        return hurt;
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target)
    {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && this.random.nextInt(3) == 0) {living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60), this);}
        return hit;
    }

    @Override public void die(DamageSource source)
    {
        super.die(source);
        if (this.level() instanceof ServerLevel level && !this.defeated)
        {
            this.defeated = true;
            this.pendingStrikes.clear();
            this.strikeTimer = -1;
            this.bossEvent.setProgress(0.0F);
            SealEvents.onHerobrineDefeated(level, this);
        }
    }

    /** No falling over: he slowly rises and fades away. */
    @Override protected void tickDeath()
    {
        this.deathTime++;
        if (!(this.level() instanceof ServerLevel level)) {return;}
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.setPos(this.getX(), this.getY() + 0.04D, this.getZ());
        if (this.deathTime % 2 == 0) {level.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 1.0D, this.getZ(), 12, 0.3D, 0.9D, 0.3D, 0.0D);}
        if (this.deathTime >= DEATH_DURATION && !this.isRemoved())
        {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), this.getX(), this.getY() + 1.0D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(), 60, 0.4D, 1.0D, 0.4D, 0.05D);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override public boolean isNoGravity() {return this.isDeadOrDying() || super.isNoGravity();}
    @Override public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override protected boolean canRide(Entity vehicle) {return false;}
    @Override protected SoundEvent getHurtSound(DamageSource source) {return DFTMSounds.HEROBRINE_HURT;}
    @Override protected SoundEvent getDeathSound() {return DFTMSounds.HEROBRINE_DEATH;}

    //boss bar
    @Override public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override public void setCustomName(@Nullable Component name)
    {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.storeNullable("SealPos", BlockPos.CODEC, this.sealPos);
    }

    @Override protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        this.sealPos = input.read("SealPos", BlockPos.CODEC).orElse(null);
        if (this.hasCustomName()) {this.bossEvent.setName(this.getDisplayName());}
    }
}
