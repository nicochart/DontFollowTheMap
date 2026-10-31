package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.HauntEffects;
import fr.factionbedrock.dontfollowthemap.util.Players;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * The real one, freed from the seal. Three phases, by remaining health:
 * 1. he hunts the players, blinks behind them (behind another one each time, when they are several) and calls lightning on marked spots;
 * 2. he also sends his shades (apparitions that rush at the players);
 * 3. lights out: darkness for everyone, faster blinks, more lightning.
 * He leaves (and can be called back from the open seal) if nobody fights him; he dies in a slow, silent ascension.
 */
public class HerobrineEntity extends Monster
{
    public static final int DEATH_DURATION = 60;
    private static final int STRIKE_WARNING_TICKS = 30;
    private static final float STRIKE_DAMAGE = 7.0F, STRIKE_RADIUS = 1.6F;
    /** Against a raised shield: his axe, and a critical blow (half as hard again as his own) from a jump, from this close; between two, a little while. */
    private static final float CRIT_DAMAGE = 12.0F;
    private static final double CRIT_RANGE = 4.0D;
    private static final int CRIT_COOLDOWN = 40, AXE_PUT_AWAY_TICKS = 20;

    private final ServerBossEvent bossEvent = Util.make(new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10),
            event -> event.setDarkenScreen(true).setCreateWorldFog(true));

    @Nullable private BlockPos sealPos;
    private int phase = 1;
    private int blinkCooldown = 60, strikeCooldown = 120, shadeCooldown = 60, darknessCooldown = 0;
    private final List<Vec3> pendingStrikes = new ArrayList<>();
    private int strikeTimer = -1;
    private int idleTicks;
    private boolean defeated;
    /** How he gets to a target that can't be walked to (see PursueGoal). */
    private final Pursuit pursuit = new Pursuit(this, Pursuit.Rise.FLOAT, new ItemStack(Items.DIAMOND_PICKAXE), this::canHit);
    /** The jump of his critical blow (-1: none), the ticks before the next one, before his axe goes back. */
    private int critTicks = -1, critCooldown, axeTicks;

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
        this.goalSelector.addGoal(1, new PursueGoal());
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
        if (this.axeTicks > 0 && --this.axeTicks == 0) {this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);}

        LivingEntity target = this.getTarget();
        if (!this.isInArena(target))
        {
            //his target got away (or died): he turns to the closest one still in the fight
            target = this.getPlayersInArena(level).stream().min(Comparator.comparingDouble(player -> this.distanceToSqr(player))).orElse(null);
            if (target != null) {this.setTarget(target);}
        }
        if (target == null)
        {
            //nobody to fight: after a while, he goes back where he came from
            if (++this.idleTicks > 20 * 20) {this.leave(level);}
            return;
        }
        this.idleTicks = 0;
        this.pursuit.track(level, target);
        this.answerShield(level, target);

        this.blinkCooldown--;
        this.strikeCooldown--;
        this.shadeCooldown--;
        this.darknessCooldown--;
        double distance = this.distanceTo(target);
        if (this.blinkCooldown <= 0 && (distance > 10.0D || this.random.nextInt(this.phase == 3 ? 30 : 60) == 0))
        {
            //with several players in the fight, each blink takes him to another one
            LivingEntity next = this.pickNextTarget(level, target);
            if (this.blinkBehind(level, next))
            {
                if (next != target) {this.setTarget(next);}
                this.blinkCooldown = (this.phase == 3 ? 50 : 90) + this.random.nextInt(60);
            }
            //no room around them (on a pillar...): not again at once
            else {this.blinkCooldown = 20;}
        }
        if (this.strikeCooldown <= 0 && this.strikeTimer < 0)
        {
            this.prepareStrikes(level, target, this.phase == 3 ? 5 : 3);
            this.strikeCooldown = (this.phase == 3 ? 110 : 160) + this.random.nextInt(80);
        }
        if (this.phase >= 2 && this.shadeCooldown <= 0 && target instanceof ServerPlayer player)
        {
            this.summonShades(level, player, this.phase == 3 ? 3 : 2);
            this.shadeCooldown = 20 * 18;
        }
        if (this.phase == 3 && this.darknessCooldown <= 0)
        {
            this.darkenArena(level, 20 * 7);
            this.darknessCooldown = 20 * 15;
        }
    }

    private boolean isInArena(@Nullable LivingEntity target)
    {
        return target != null && target.isAlive() && (this.sealPos == null || target.distanceToSqr(Vec3.atCenterOf(this.sealPos)) <= Mth.square(SealEvents.ARENA_RADIUS + 16.0D));
    }

    /** Another player of the fight, at random, if there is one: he doesn't stick to the same victim. */
    private LivingEntity pickNextTarget(ServerLevel level, LivingEntity current)
    {
        List<ServerPlayer> others = this.getPlayersInArena(level).stream().filter(player -> player != current).toList();
        return others.isEmpty() ? current : others.get(this.random.nextInt(others.size()));
    }

    /** Lights out for everyone in the fight, not only for the one he is after. */
    private void darkenArena(ServerLevel level, int ticks)
    {
        for (ServerPlayer player : this.getPlayersInArena(level)) {player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks), this);}
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

    //walls, pillars, shields
    private boolean canHit(LivingEntity target) {return this.isWithinMeleeAttackRange(target) && this.getSensing().hasLineOfSight(target);}

    /** A shield raised against him: out comes his axe, he jumps, and his critical blow knocks the shield aside for a while (as any axe does). */
    private void answerShield(ServerLevel level, LivingEntity target)
    {
        if (this.critTicks >= 0)
        {
            this.tickCrit(level, target);
            return;
        }
        if (this.critCooldown > 0)
        {
            this.critCooldown--;
            return;
        }
        if (!this.onGround() || !Players.isShieldedFrom(target, this) || this.distanceTo(target) > CRIT_RANGE || !this.hasLineOfSight(target)) {return;}
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.axeTicks = 0;
        this.jumpFromGround();
        this.addDeltaMovement(target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(0.3D));
        this.critTicks = 0;
    }

    /** Coming down from his jump, within reach: the blow. Landed without it: he tries again later. */
    private void tickCrit(ServerLevel level, LivingEntity target)
    {
        this.critTicks++;
        boolean falling = !this.onGround() && this.getDeltaMovement().y < 0.0D;
        if (falling && target.getBoundingBox().distanceToSqr(this.getEyePosition()) <= Pursuit.HOLE_REACH * Pursuit.HOLE_REACH && this.hasLineOfSight(target))
        {
            this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            target.hurtServer(level, this.damageSources().mobAttack(this), CRIT_DAMAGE);
            level.getChunkSource().sendToTrackingPlayersAndSelf(target, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        else if (this.critTicks < 4 || !this.onGround()) {return;}
        this.critTicks = -1;
        this.critCooldown = CRIT_COOLDOWN;
        this.axeTicks = AXE_PUT_AWAY_TICKS;
    }

    /** While his target can't be walked to, he gets to them another way (see Pursuit), and strikes through the hole he made, from where a player could. */
    private class PursueGoal extends Goal
    {
        private int attackCooldown;

        PursueGoal() {this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));}

        @Override public boolean canUse()
        {
            LivingEntity target = HerobrineEntity.this.getTarget();
            return target != null && HerobrineEntity.this.pursuit.isActive() && !HerobrineEntity.this.canHit(target);
        }

        @Override public boolean requiresUpdateEveryTick() {return true;}

        @Override public void tick()
        {
            if (this.attackCooldown > 0) {this.attackCooldown--;}
            LivingEntity target = HerobrineEntity.this.getTarget();
            if (target == null || !(HerobrineEntity.this.level() instanceof ServerLevel level) || HerobrineEntity.this.pursuit.act(level, target)) {return;}
            if (this.attackCooldown == 0 && HerobrineEntity.this.pursuit.canHitThrough(target))
            {
                HerobrineEntity.this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                HerobrineEntity.this.doHurtTarget(level, target);
                this.attackCooldown = 20;
            }
        }
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
            BlockPos ground = HauntSpots.findRoomForHerobrineDown(level, Mth.floor(candidate.x), Mth.floor(candidate.z), Mth.floor(candidate.y) + 2, Mth.floor(candidate.y) - 2);
            //where he sees them (not in a gap under what they stand on, nor behind a wall)
            if (ground == null || ground.distToCenterSqr(target.position()) < 1.5D || !HauntSpots.canSee(level, Vec3.atBottomCenterOf(ground).add(0.0D, this.getEyeHeight(), 0.0D), target.getEyePosition())) {continue;}
            this.teleportWithSmoke(level, Vec3.atBottomCenterOf(ground));
            this.getLookControl().setLookAt(target);
            return true;
        }
        return false;
    }

    private void teleportWithSmoke(ServerLevel level, Vec3 destination)
    {
        this.pursuit.reset(level);
        HauntEffects.puffOfSmoke(level, this.position(), 20, DFTMSounds.BLINK, 1.0F, 1.0F);
        this.teleportTo(destination.x, destination.y, destination.z);
        this.getNavigation().stop();
        HauntEffects.puffOfSmoke(level, destination, 20, DFTMSounds.BLINK, 1.0F, 1.2F);
    }

    //lightning
    private void prepareStrikes(ServerLevel level, LivingEntity target, int count)
    {
        this.pendingStrikes.clear();
        //the first ones exactly where the players of the fight stand, his target first: they must move
        this.pendingStrikes.add(target.position());
        for (ServerPlayer player : this.getPlayersInArena(level))
        {
            if (player != target && this.pendingStrikes.size() < count) {this.pendingStrikes.add(player.position());}
        }
        while (this.pendingStrikes.size() < count) {this.pendingStrikes.add(target.position().add(Mth.nextDouble(this.random, -4.0D, 4.0D), 0.0D, Mth.nextDouble(this.random, -4.0D, 4.0D)));}
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
            //the damage is dealt here
            HauntEffects.lightning(level, strike);
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
        List<ServerPlayer> players = this.getPlayersInArena(level);
        for (int i = 0; i < count; i++)
        {
            //each one goes for someone of the fight
            ServerPlayer victim = players.isEmpty() ? player : players.get(this.random.nextInt(players.size()));
            Vec3 spot = HauntSpots.findSightingSpot(victim, 7.0D, 11.0D, 0.0D, 180.0D);
            if (spot != null) {HerobrineApparitionEntity.spawn(level, spot, victim, HerobrineApparitionEntity.Mode.SHADE, 20 * 12);}
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.HEROBRINE_CALL, SoundSource.HOSTILE, 2.0F, 0.8F);
    }

    /** The players of the fight: alive, in survival or adventure, around the seal (or around him when he has none). */
    private List<ServerPlayer> getPlayersInArena(ServerLevel level)
    {
        Vec3 center = this.sealPos != null ? Vec3.atCenterOf(this.sealPos) : this.position();
        return level.getPlayers(player -> Players.isHuntable(player) && player.distanceToSqr(center) < Mth.square(SealEvents.ARENA_RADIUS + 16.0D));
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
        //arrows often find nothing but smoke, and he shows up behind the archer
        if (source.is(DamageTypeTags.IS_PROJECTILE) && this.random.nextFloat() < 0.4F && source.getEntity() instanceof LivingEntity shooter && this.blinkBehind(level, shooter))
        {
            if (shooter instanceof Player && this.canAttack(shooter)) {this.setTarget(shooter);}
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.random.nextInt(4) == 0) {this.blinkCooldown = Math.min(this.blinkCooldown, 10);}
        return hurt;
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target)
    {
        boolean hit = super.doHurtTarget(level, target);
        //one blow in three puts the lights out for a moment, for everyone in the fight
        if (hit && this.random.nextInt(3) == 0) {this.darkenArena(level, 60);}
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

    @Override public void remove(RemovalReason reason)
    {
        //no crack left on a block he was mining
        if (this.level() instanceof ServerLevel level) {this.pursuit.reset(level);}
        super.remove(reason);
    }

    //no fall hurts him: he drops from anywhere to get to his target
    @Override public int getMaxFallDistance() {return 32;}
    //cobwebs don't hold him back (as they don't hold spiders back: there is no tag for it)
    @Override public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {if (!state.is(Blocks.COBWEB)) {super.makeStuckInBlock(state, speedMultiplier);}}
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
