package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Herobrine that is not really there. It never attacks (except the shades summoned during the fight):
 * it stares at one player and vanishes before anybody can reach it. Whoever is close to it or looks at it hears his ambience (client/HerobrineSounds).
 */
public class HerobrineApparitionEntity extends PathfinderMob
{
    public enum Mode
    {
        /** Seen from afar: vanishes when approached, or when stared at for too long, by anybody. */
        WATCH,
        /** A brief flash (kill apparition, bed scare). */
        FLASH,
        /** Summoned by Herobrine during the fight: rushes at the player and vanishes on contact. */
        SHADE,
        /**
         * Crouching behind the wall he put up in the player's tunnel, his face right behind its top block: digging that one first shows him for an instant.
         * If the bottom one goes first, or if nobody comes through in time, he is gone and a redstone torch stands where he was.
         */
        LURK
    }

    public static final float VANISH_DISTANCE = 10.0F;
    private static final int STARE_TICKS_TO_VANISH = 70;
    /** How long he stays once the top block of his wall is dug. */
    public static final int REVEAL_TICKS = 20;
    /** Crouching like a player: his face is as high as the top block of a 2 high tunnel. */
    private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.5F).withEyeHeight(1.27F);
    /** The network id of the player it stalks (standing behind them), -1 if none: their client thickens its fog meanwhile. */
    private static final EntityDataAccessor<Integer> DATA_STALKED_PLAYER = SynchedEntityData.defineId(HerobrineApparitionEntity.class, EntityDataSerializers.INT);
    /** Whether it is one that stays and watches: each client plays Herobrine's ambience if its player is close to it or looks at it. */
    private static final EntityDataAccessor<Boolean> DATA_WATCHING = SynchedEntityData.defineId(HerobrineApparitionEntity.class, EntityDataSerializers.BOOLEAN);

    private Mode mode = Mode.WATCH;
    @Nullable private UUID targetId;
    private int lifetime = 20 * 90;
    private int stareTicks;
    private boolean leavesMap;
    private boolean vanished;
    /** LURK: the bottom block of his wall (the top one is above it). */
    @Nullable private BlockPos wall;
    private boolean revealed;
    private int revealTicks;

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
        apparition.entityData.set(DATA_WATCHING, mode == Mode.WATCH || mode == Mode.LURK);
        if (mode == Mode.LURK) {apparition.setPose(Pose.CROUCHING);}
        apparition.targetId = target.getUUID();
        apparition.lifetime = lifetime;
        apparition.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        apparition.faceTowards(target);
        level.addFreshEntity(apparition);
        return apparition;
    }

    /** Crouching on feet, behind the wall whose bottom block is wallBottom, for lifetime ticks at most: see {@link Mode#LURK}. */
    public static HerobrineApparitionEntity lurk(ServerLevel level, BlockPos feet, ServerPlayer target, BlockPos wallBottom, int lifetime)
    {
        HerobrineApparitionEntity apparition = spawn(level, Vec3.atBottomCenterOf(feet), target, Mode.LURK, lifetime);
        apparition.wall = wallBottom.immutable();
        return apparition;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_STALKED_PLAYER, -1);
        builder.define(DATA_WATCHING, false);
    }

    public void setLeavesMap(boolean leavesMap) {this.leavesMap = leavesMap;}

    public void setStalking(ServerPlayer player) {this.entityData.set(DATA_STALKED_PLAYER, player.getId());}

    /** Whether it is standing behind this player, waiting for them to turn around (known on both sides). */
    public boolean isStalking(Player player) {return this.entityData.get(DATA_STALKED_PLAYER) == player.getId();}

    public Mode getMode() {return this.mode;}

    /** One that stays a while (a sighting, a stalk, one lurking behind a wall), not a flash nor a shade (known on both sides). */
    public boolean isWatching() {return this.entityData.get(DATA_WATCHING);}

    /** LURK: the top block of his wall was dug, he is showing himself. */
    public boolean isRevealed() {return this.revealed;}

    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {return pose == Pose.CROUCHING ? CROUCHING_DIMENSIONS : super.getDefaultDimensions(pose);}

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
        if (this.mode == Mode.LURK)
        {
            this.waitBehindWall(level, target);
            return;
        }
        if (this.tickCount >= this.lifetime)
        {
            this.vanish(level, this.mode != Mode.FLASH);
            return;
        }

        this.faceTowards(target);
        if (this.mode == Mode.WATCH) {this.watch(level);}
        else if (this.mode == Mode.SHADE)
        {
            if (this.tickCount % 10 == 0) {this.getNavigation().moveTo(target, 1.4D);}
            if (this.distanceTo(target) < 1.4D)
            {
                target.hurtServer(level, this.damageSources().mobAttack(this), 4.0F);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50), this);
                this.vanish(level, true);
            }
        }
    }

    /** It stares at its player, but anybody who comes close to it or stares back long enough makes it vanish (and saw it), not only that one. */
    private void watch(ServerLevel level)
    {
        ServerPlayer starer = null;
        for (ServerPlayer player : level.getPlayers(player -> player.isAlive() && !player.isSpectator() && this.distanceToSqr(player) < 96.0D * 96.0D))
        {
            if (this.distanceToSqr(player) < VANISH_DISTANCE * VANISH_DISTANCE) {this.vanish(level, true, player); return;}
            if (starer == null && this.tickCount % 4 == 0 && this.isStaredAtBy(player)) {starer = player;}
        }
        if (this.tickCount % 4 != 0) {return;}
        this.stareTicks = starer != null ? this.stareTicks + 4 : Math.max(0, this.stareTicks - 2);
        if (starer != null && this.stareTicks >= STARE_TICKS_TO_VANISH) {this.vanish(level, true, starer);}
    }

    private void waitBehindWall(ServerLevel level, ServerPlayer target)
    {
        this.faceTowards(target);
        if (this.revealed)
        {
            //gone in a flash, like he came
            if (++this.revealTicks >= REVEAL_TICKS) {this.vanish(level, false);}
            return;
        }
        if (this.wall == null) {this.vanish(level, false);}
        else if (isDug(level, this.wall.above())) {this.reveal(level, target);}
        //too soon (only his legs would show) or too late
        else if (isDug(level, this.wall) || this.tickCount >= this.lifetime) {this.vanish(level, false);}
    }

    /** Mined, blown up...: nothing solid left there. */
    private static boolean isDug(ServerLevel level, BlockPos pos) {return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();}

    private void reveal(ServerLevel level, ServerPlayer target)
    {
        this.revealed = true;
        HauntEvents.appearSuddenly(level, this.getEyePosition());
        if (this.distanceToSqr(target) < 8.0D * 8.0D) {DFTMAdvancements.award(target, "knock_knock");}
    }

    /** He didn't wait: a redstone torch stands where he crouched. */
    private void leaveTorch(ServerLevel level)
    {
        BlockPos feet = this.blockPosition();
        BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
        if (!HauntSpots.isReplaceable(level, feet) || !torch.canSurvive(level, feet) || HauntSpots.isNearRedstoneSensitiveBlock(level, feet)) {return;}
        level.setBlock(feet, torch, Block.UPDATE_ALL);
        level.playSound(null, feet, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.15F, 0.6F);
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

    public void vanish(ServerLevel level, boolean withSmoke) {this.vanish(level, withSmoke, null);}

    /** witness: the player who made it vanish by coming close, staring at it or hitting it (they saw it), null when it just left. */
    public void vanish(ServerLevel level, boolean withSmoke, @Nullable ServerPlayer witness)
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
        if (this.mode == Mode.LURK && !this.revealed) {this.leaveTorch(level);}
        if (this.mode == Mode.WATCH && witness != null) {DFTMAdvancements.award(witness, "watched");}
        this.discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        if (this.mode == Mode.SHADE || source.getEntity() instanceof Player) {this.vanish(level, true, source.getEntity() instanceof ServerPlayer player ? player : null);}
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
