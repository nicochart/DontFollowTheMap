package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import fr.factionbedrock.dontfollowthemap.util.Facing;
import fr.factionbedrock.dontfollowthemap.util.HauntEffects;
import fr.factionbedrock.dontfollowthemap.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Herobrine that is not really there. Most of them stare at one player and vanish before anybody can reach them; some attack:
 * the stalker, the shades summoned during the fight, and what an imitator hit too soon turns into.
 * Whoever is close to one that stays or looks at it hears his ambience (client/HerobrineSounds).
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
        LURK,
        /**
         * Behind the player: it creeps up on them while their back is turned, and runs at them once it is on their screen. Each of its blows comes with
         * the darkness, then it is gone, behind them again. Running away from it only brings them face to face with it, sometimes behind a cobweb
         * it puts in their way. Only the third blow it takes makes it go away: until then, it can kill.
         */
        STALK,
        /** What an imitator hit before it had its sword turns into: it runs at the player who hit it, strikes once, hard, and vanishes. */
        STRIKE
    }

    public static final float VANISH_DISTANCE = 10.0F;
    public static final int STALK_LIFETIME = 20 * 60 * 3;
    /**
     * STALK: creeping up at a walking player's pace (4.3 blocks per second, see ImitatorEntity.WALKING_SPEED), running at 7, faster than a sprinting player.
     * STRIKE: running at 7.8.
     */
    private static final double CREEP_SPEED = 0.93D, CHARGE_SPEED = 1.18D, STRIKE_SPEED = 1.25D;
    private static final double STALK_REACH = 1.8D, SHADE_REACH = 1.4D, STRIKE_REACH = 1.6D;
    private static final float STALK_DAMAGE = 7.0F, STRIKE_DAMAGE = 10.0F;
    private static final int STALK_HITS = 3, STALK_DARKNESS_TICKS = 20 * 6;
    /** STALK: it blinks away this long after its blow; it waits this long once back behind its player; it blinks behind them if it can't get closer for this long. */
    private static final int BLINK_AFTER_BLOW_TICKS = 6, WAIT_TICKS = 40, STUCK_TICKS = 20 * 5;
    /**
     * STALK: a player running away from it (RUN_DISTANCE covered in the last second, getting farther from it, now FLEE_DISTANCE away at least) finds it
     * in their way, AHEAD_MIN to AHEAD_MAX blocks ahead of them, once every BLINK_AHEAD_COOLDOWN ticks at most; it stands there for AHEAD_WAIT_TICKS.
     */
    private static final double RUN_DISTANCE = 3.0D, FLEE_DISTANCE = 8.0D, AHEAD_MIN = 5.0D, AHEAD_MAX = 8.0D;
    private static final int BLINK_AHEAD_COOLDOWN = 20 * 4, AHEAD_WAIT_TICKS = 10;
    /** Half the time, it puts a cobweb in their way instead: in its hand, put down after WEB_PUT_TICKS, its hand down after WEB_HOLD_TICKS. */
    private static final float WEB_CHANCE = 0.5F;
    private static final int WEB_PUT_TICKS = 5, WEB_HOLD_TICKS = 14;
    private static final int STARE_TICKS_TO_VANISH = 70;
    /** How long he stays once the top block of his wall is dug. */
    public static final int REVEAL_TICKS = 20;
    /** Crouching like a player: his face is as high as the top block of a 2 high tunnel. */
    private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.5F).withEyeHeight(1.27F);
    /** The network id of the player it watches from afar or stalks, -1 if none: their client thickens its fog meanwhile (client/HerobrineFog). */
    private static final EntityDataAccessor<Integer> DATA_FOGGED_PLAYER = SynchedEntityData.defineId(HerobrineApparitionEntity.class, EntityDataSerializers.INT);
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
    /** STALK: the blows it took (and when it took the last one), the ticks before it blinks away or goes on, how close it got to its player. */
    private int hits;
    private int lastHitTick = -100;
    private int blinkTicks = -1;
    private int waitTicks;
    private int stuckTicks;
    private double closest = Double.MAX_VALUE;
    /** STALK: whether its player sees it on their screen, as their client says (see setSeenBy). */
    private boolean seenByTarget;
    /** STALK: where its player was a second ago, and how far from it (to tell whether they run away); when it may blink ahead of them again. */
    @Nullable private Vec3 lastTargetPosition;
    private double lastDistance;
    private int nextBlinkAheadTick;
    /** STALK: where the cobweb in its hand goes (null once it is down), and the ticks before its hand is down again. */
    @Nullable private BlockPos web;
    private int webTicks;
    /** STALK, SHADE, STRIKE: how it gets to its player when they can't be walked to. */
    private final Pursuit pursuit = new Pursuit(this, Pursuit.Rise.FLOAT, new ItemStack(Items.DIAMOND_PICKAXE), this::canHit);

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
        apparition.entityData.set(DATA_WATCHING, mode == Mode.WATCH || mode == Mode.LURK || mode == Mode.STALK);
        if (mode == Mode.LURK) {apparition.setPose(Pose.CROUCHING);}
        apparition.targetId = target.getUUID();
        apparition.lifetime = lifetime;
        apparition.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        apparition.faceTowards(target);
        level.addFreshEntity(apparition);
        return apparition;
    }

    /** Something of his (an imitator, a possessed animal) drops its disguise: a stalker stands where it was, after this player. */
    public static HerobrineApparitionEntity replace(ServerLevel level, Entity disguise, ServerPlayer target)
    {
        Vec3 pos = disguise.position();
        disguise.discard();
        HauntEffects.puffOfSmoke(level, pos, 25, DFTMSounds.VANISH, 1.0F, 0.8F);
        HauntEvents.appearSuddenly(level, pos);
        HerobrineApparitionEntity stalker = spawn(level, pos, target, Mode.STALK, STALK_LIFETIME);
        stalker.setFogging(target);
        return stalker;
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
        builder.define(DATA_FOGGED_PLAYER, -1);
        builder.define(DATA_WATCHING, false);
    }

    public void setLeavesMap(boolean leavesMap) {this.leavesMap = leavesMap;}

    /** Thickens the fog of this player while it is there. */
    public void setFogging(ServerPlayer player) {this.entityData.set(DATA_FOGGED_PLAYER, player.getId());}

    /** Whether it thickens the fog of this player (known on both sides). */
    public boolean isFogging(Player player) {return this.entityData.get(DATA_FOGGED_PLAYER) == player.getId();}

    public Mode getMode() {return this.mode;}

    /** STALK: the blows it took. */
    public int getHits() {return this.hits;}

    /**
     * A client tells whether its player sees this apparition on their screen (client/HerobrineSight, network/HerobrineSeenPayload):
     * only the word of the player it is after counts. The server can't tell by itself: it doesn't know their field of view.
     */
    public void setSeenBy(ServerPlayer player, boolean seen) {if (player.getUUID().equals(this.targetId)) {this.seenByTarget = seen;}}

    /** Whether the player it is after sees it, as their client last said. */
    public boolean isSeenByTarget() {return this.seenByTarget;}

    /** One that stays a while (a sighting, a stalk, one lurking behind a wall), not a flash, a shade nor a strike (known on both sides). */
    public boolean isWatching() {return this.entityData.get(DATA_WATCHING);}

    /** LURK: the top block of his wall was dug, he is showing himself. */
    public boolean isRevealed() {return this.revealed;}

    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {return pose == Pose.CROUCHING ? CROUCHING_DIMENSIONS : super.getDefaultDimensions(pose);}

    @Nullable public ServerPlayer getTargetPlayer() {return this.level() instanceof ServerLevel level ? Players.inLevel(level, this.targetId) : null;}

    @Override public void tick()
    {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.vanished && !this.isRemoved()) {this.serverTick(level);}
    }

    private void serverTick(ServerLevel level)
    {
        ServerPlayer target = this.getTargetPlayer();
        if (target == null || !Players.isInTheGame(target) || this.distanceToSqr(target) > 96.0D * 96.0D)
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

        //it stares at its player: all of it while it stands, only its head on its way (turned towards them, it couldn't go round anything)
        if (this.getNavigation().isDone()) {this.faceTowards(target);}
        else {this.getLookControl().setLookAt(target, 180.0F, 180.0F);}
        if (this.mode == Mode.WATCH) {this.watch(level);}
        else if (this.mode == Mode.STALK) {this.stalk(level, target);}
        else if (this.mode == Mode.SHADE) {this.rush(level, target, 1.4D, 4.0F, 0.0D, new MobEffectInstance(MobEffects.BLINDNESS, 50));}
        else if (this.mode == Mode.STRIKE) {this.rush(level, target, STRIKE_SPEED, STRIKE_DAMAGE, 0.8D, null);}
    }

    /** How close it must be to hit its player (STALK, SHADE, STRIKE), when they can be walked to. */
    private double reach() {return switch (this.mode) {case STALK -> STALK_REACH; case SHADE -> SHADE_REACH; case STRIKE -> STRIKE_REACH; default -> 0.0D;};}

    private boolean canHit(LivingEntity target) {return this.distanceTo(target) < this.reach() && this.hasLineOfSight(target);}

    /** SHADE, STRIKE: runs at its player and hits them once, then it is gone. */
    private void rush(ServerLevel level, ServerPlayer target, double speed, float damage, double extraKnockback, @Nullable MobEffectInstance effect)
    {
        //walls, a pillar...: it gets to them another way
        this.pursuit.track(level, target);
        if (this.pursuit.isActive() && this.pursuit.act(level, target)) {return;}
        if (this.tickCount % 5 == 1) {this.getNavigation().moveTo(target, speed);}
        if (!this.canHit(target) && !this.pursuit.canHitThrough(target)) {return;}
        if (Players.isShieldedFrom(target, this)) {this.blinkRightBehind(level, target);}
        this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        DamageSource source = this.damageSources().mobAttack(this);
        if (target.hurtServer(level, source, damage))
        {
            if (extraKnockback > 0.0D) {target.knockback(extraKnockback, this.getX() - target.getX(), this.getZ() - target.getZ(), source, damage);}
            if (effect != null) {target.addEffect(effect, this);}
        }
        this.vanish(level, true);
    }

    /** See {@link Mode#STALK}. */
    private void stalk(ServerLevel level, ServerPlayer target)
    {
        Vec3 escape = this.tickCount % 20 == 0 ? this.escape(target) : null;
        //its blow struck: it lets them see who it was, for an instant
        if (this.blinkTicks >= 0)
        {
            this.getNavigation().stop();
            if (this.blinkTicks-- == 0) {this.blinkBehind(level, target);}
            return;
        }
        //a cobweb for their way in its hand
        if (this.webTicks > 0)
        {
            this.putWeb(level, target);
            return;
        }
        //back behind them, in the dark (or in their way, facing them)
        if (this.waitTicks > 0)
        {
            this.waitTicks--;
            this.getNavigation().stop();
            return;
        }
        //walls, a pillar...: it gets to them another way (unless there is none: then it comes back from behind them, see below)
        this.pursuit.track(level, target);
        if (this.pursuit.isActive() && !this.pursuit.isStuck() && this.pursuit.act(level, target))
        {
            this.stuckTicks = 0;
            this.closest = Double.MAX_VALUE;
            return;
        }
        //it runs once it is on their screen
        double speed = this.seenByTarget ? CHARGE_SPEED : CREEP_SPEED;
        if (this.tickCount % 5 == 1) {this.getNavigation().moveTo(target, speed);}
        else {this.getNavigation().setSpeedModifier(speed);}
        double distance = this.distanceTo(target);
        if (this.canHit(target) || this.pursuit.canHitThrough(target))
        {
            //a raised shield: it is behind them at once
            if (Players.isShieldedFrom(target, this)) {this.blinkRightBehind(level, target);}
            this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            if (target.hurtServer(level, this.damageSources().mobAttack(this), STALK_DAMAGE)) {target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, STALK_DARKNESS_TICKS), this);}
            this.blinkTicks = BLINK_AFTER_BLOW_TICKS;
        }
        else if (this.tickCount % 20 == 0) {this.keepUp(level, target, escape, distance);}
    }

    /** STALK, once a second while it can't hit its player: running away from it, they find it in their way; kept away (by a door, a ravine...), it comes back from behind them. */
    private void keepUp(ServerLevel level, ServerPlayer target, @Nullable Vec3 escape, double distance)
    {
        if (escape != null && this.tickCount >= this.nextBlinkAheadTick)
        {
            boolean cobweb = this.random.nextFloat() < WEB_CHANCE && !target.isFallFlying() && !target.getAbilities().flying;
            if (this.blinkAhead(level, target, escape, cobweb)) {return;}
        }
        if (distance < this.closest - 1.0D)
        {
            this.closest = distance;
            this.stuckTicks = 0;
        }
        else if ((this.stuckTicks += 20) >= STUCK_TICKS) {this.blinkBehind(level, target);}
    }

    /** STALK, once a second: which way its player ran in the last second if they ran away from it, null if they didn't (it remembers where they were, and how far). */
    @Nullable private Vec3 escape(ServerPlayer target)
    {
        Vec3 run = this.lastTargetPosition == null ? Vec3.ZERO : target.position().subtract(this.lastTargetPosition).multiply(1.0D, 0.0D, 1.0D);
        double distance = this.distanceTo(target), lastDistance = this.lastDistance;
        this.lastTargetPosition = target.position();
        this.lastDistance = distance;
        return run.length() >= RUN_DISTANCE && distance > lastDistance + 0.5D && distance >= FLEE_DISTANCE ? run.normalize() : null;
    }

    /**
     * STALK: its player runs away from it, that way: gone in a puff of smoke, it suddenly stands in their way a few blocks ahead, where they see it.
     * With a cobweb, it puts one down in front of itself, on their way, if there is room. False if there is no place for it ahead of them.
     */
    public boolean blinkAhead(ServerLevel level, ServerPlayer target, Vec3 way, boolean cobweb)
    {
        for (double ahead = AHEAD_MAX; ahead >= AHEAD_MIN; ahead -= 1.0D)
        {
            Vec3 spot = target.position().add(way.scale(ahead));
            BlockPos ground = HauntSpots.findRoomForHerobrineDown(level, Mth.floor(spot.x), Mth.floor(spot.z), Mth.floor(target.getY()) + 2, Mth.floor(target.getY()) - 3);
            if (ground == null) {continue;}
            Vec3 feet = Vec3.atBottomCenterOf(ground);
            if (!HauntSpots.canSee(level, target.getEyePosition(), feet.add(0.0D, this.getEyeHeight(), 0.0D))) {continue;}
            this.pursuit.reset(level);
            HauntEffects.puffOfSmoke(level, this.position(), 20, DFTMSounds.BLINK, 0.6F, 1.0F);
            this.teleportTo(feet.x, feet.y, feet.z);
            this.faceTowards(target);
            this.getNavigation().stop();
            HauntEvents.appearSuddenly(level, feet);
            this.nextBlinkAheadTick = this.tickCount + BLINK_AHEAD_COOLDOWN;
            this.closest = Double.MAX_VALUE;
            this.stuckTicks = 0;
            if (!cobweb || !this.takeOutWeb(level, way)) {this.waitTicks = AHEAD_WAIT_TICKS;}
            return true;
        }
        return false;
    }

    /** A cobweb in its hand, for the block in front of it on the way of its player (coming from there), on the ground: false if there is no room. */
    private boolean takeOutWeb(ServerLevel level, Vec3 way)
    {
        BlockPos web = BlockPos.containing(this.position().subtract(way));
        if (!HauntSpots.isReplaceable(level, web) || !level.getBlockState(web.below()).isFaceSturdy(level, web.below(), Direction.UP)) {return false;}
        this.web = web;
        this.webTicks = WEB_HOLD_TICKS;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBWEB));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        return true;
    }

    /** It looks down at where the cobweb goes, puts it down (unless its player is already there), then lets its hand down. */
    private void putWeb(ServerLevel level, ServerPlayer target)
    {
        this.getNavigation().stop();
        this.webTicks--;
        if (this.web != null && this.webTicks > WEB_HOLD_TICKS - WEB_PUT_TICKS) {Facing.turn(this, Vec3.atCenterOf(this.web));}
        else if (this.web != null)
        {
            if (HauntSpots.isReplaceable(level, this.web) && !target.getBoundingBox().intersects(new AABB(this.web)))
            {
                BlockState cobweb = Blocks.COBWEB.defaultBlockState();
                level.setBlock(this.web, cobweb, Block.UPDATE_ALL);
                SoundType sound = cobweb.getSoundType();
                level.playSound(null, this.web, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                level.gameEvent(this, GameEvent.BLOCK_PLACE, this.web);
                this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            }
            this.web = null;
        }
        if (this.webTicks == 0) {this.putWebAway();}
    }

    private void putWebAway()
    {
        if (this.getMainHandItem().is(Items.COBWEB)) {this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);}
        this.web = null;
        this.webTicks = 0;
    }

    /**
     * STALK: gone in a puff of smoke, back behind its player, where it waits a moment. It swims, so the water of a lake will do; with no room behind
     * them, anywhere around them. With no room at all, it only waits where it is (no smoke: it doesn't go anywhere).
     */
    private void blinkBehind(ServerLevel level, ServerPlayer target)
    {
        this.pursuit.reset(level);
        this.putWebAway();
        Vec3 spot = HauntSpots.findSightingSpot(target, 7.0D, 12.0D, 110.0D, 180.0D, 0.0D, true);
        if (spot == null) {spot = HauntSpots.findSightingSpot(target, 7.0D, 12.0D, 0.0D, 180.0D, 0.0D, true);}
        if (spot != null)
        {
            HauntEffects.puffOfSmoke(level, this.position(), 20, DFTMSounds.BLINK, 0.6F, 1.0F);
            this.teleportTo(spot.x, spot.y, spot.z);
            this.faceTowards(target);
        }
        this.getNavigation().stop();
        this.waitTicks = WAIT_TICKS;
        this.closest = Double.MAX_VALUE;
        this.stuckTicks = 0;
    }

    /** A shield raised against it: right behind its player in a blink, out of the shield's way (unless there is no room there). */
    private void blinkRightBehind(ServerLevel level, LivingEntity target)
    {
        Vec3 back = Vec3.directionFromRotation(0.0F, target.getYHeadRot()).reverse();
        for (double distance = 1.2D; distance <= 2.0D; distance += 0.4D)
        {
            Vec3 spot = target.position().add(back.scale(distance));
            BlockPos ground = HauntSpots.findRoomForHerobrineDown(level, Mth.floor(spot.x), Mth.floor(spot.z), Mth.floor(spot.y) + 1, Mth.floor(spot.y) - 1);
            if (ground == null) {continue;}
            this.pursuit.reset(level);
            HauntEffects.puffOfSmoke(level, this.position(), 15, DFTMSounds.BLINK, 0.6F, 1.3F);
            this.teleportTo(spot.x, ground.getY(), spot.z);
            this.faceTowards(target);
            return;
        }
    }

    /** It stares at its player, but anybody who comes close to it or stares back long enough makes it vanish (and saw it), not only that one. */
    private void watch(ServerLevel level)
    {
        ServerPlayer starer = null;
        for (ServerPlayer player : level.players())
        {
            if (!Players.isInTheGame(player)) {continue;}
            double distanceSqr = this.distanceToSqr(player);
            if (distanceSqr < VANISH_DISTANCE * VANISH_DISTANCE) {this.vanish(level, true, player); return;}
            if (starer == null && distanceSqr < 96.0D * 96.0D && this.tickCount % 4 == 0 && this.isStaredAtBy(player)) {starer = player;}
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

    public void faceTowards(Entity target) {Facing.turn(this, new Vec3(target.getX(), target.getEyeY(), target.getZ()));}

    public void vanish(ServerLevel level, boolean withSmoke) {this.vanish(level, withSmoke, null);}

    /** witness: the player who made it vanish by coming close, staring at it or hitting it (they saw it), null when it just left. */
    public void vanish(ServerLevel level, boolean withSmoke, @Nullable ServerPlayer witness)
    {
        if (this.vanished) {return;}
        this.vanished = true;
        this.pursuit.reset(level);
        if (withSmoke) {HauntEffects.puffOfSmoke(level, this.position(), 25, DFTMSounds.VANISH, 1.0F, 1.0F);}
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
        if (this.mode == Mode.STALK) {this.takeBlow(level, source);}
        else if (this.mode == Mode.SHADE || source.getEntity() instanceof Player) {this.vanish(level, true, source.getEntity() instanceof ServerPlayer player ? player : null);}
        return false;
    }

    /** STALK: a blow of a player sends it away for a moment, the third one for good. */
    private void takeBlow(ServerLevel level, DamageSource source)
    {
        //one swing, one blow (a sweep hits once more in the same tick)
        if (!(source.getEntity() instanceof Player) || this.tickCount - this.lastHitTick < 10) {return;}
        this.lastHitTick = this.tickCount;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.HEROBRINE_HURT, SoundSource.HOSTILE, 1.0F, 1.0F);
        if (++this.hits >= STALK_HITS)
        {
            this.vanish(level, true);
            return;
        }
        ServerPlayer target = this.getTargetPlayer();
        this.blinkTicks = -1;
        if (target != null) {this.blinkBehind(level, target);}
    }

    @Override public void remove(RemovalReason reason)
    {
        //no crack left on a block it was mining
        if (this.level() instanceof ServerLevel level) {this.pursuit.reset(level);}
        super.remove(reason);
    }

    //no fall hurts it: it drops from anywhere to get to its player
    @Override public int getMaxFallDistance() {return 32;}
    //cobwebs don't hold it back (it puts them in its players' way)
    @Override public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {if (!state.is(Blocks.COBWEB)) {super.makeStuckInBlock(state, speedMultiplier);}}
    @Override public boolean isPushable() {return false;}
    @Override public void push(Entity entity) {}
    @Override protected void doPush(Entity entity) {}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override public boolean shouldShowName() {return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
}
