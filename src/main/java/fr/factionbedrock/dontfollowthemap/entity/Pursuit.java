package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.util.Facing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * How Herobrine (his apparitions, himself) and the imitator get to a player they can't walk to (no complete path for 2 seconds).
 * A pickaxe in hand, they mine what stands in between, the block cracking as under a player's pickaxe, only as far as needed:
 * once there is a hole they can hit them through, from as far as a player could, they do. A player standing high (a pillar, a ledge)
 * gets what they stand on mined from under their feet as soon as it is within a player's reach; until it is, Herobrine floats up beside their pillar
 * and the imitator builds itself a pillar. Herobrine also floats over what can't be walked or dug (a ravine, a lava lake), and lands
 * as soon as he can walk on. In water or in lava, deep or not (a current would hold him back), paths or not, he goes straight at them
 * the same way, as if through the air.
 * Floating, he can't step over anything, not even a snow layer or a carpet: once he stops getting any closer, he mines what he runs into.
 * With no way at all, it is stuck: its owner decides what then.
 */
public class Pursuit
{
    public enum Rise {FLOAT, BUILD}

    /** Without a complete path for this long, it finds another way (paths are looked for this often). */
    private static final int UNREACHABLE_TICKS = 40, PATH_CHECK_INTERVAL = 10;
    /** A player's reach: the blocks it can mine, and how far it hits through a hole. */
    public static final double MINING_REACH = 4.5D, HOLE_REACH = 3.0D;
    /** A target this much higher stands on something that can't be walked up (a pillar, a ledge). */
    private static final double ABOVE = 1.5D;
    private static final double FLOAT_SPEED = 0.15D;
    /** Floating, it lands once on the ground this close to the target (or anywhere it can walk on). */
    private static final double LANDING_DISTANCE = 2.5D;
    /** Floating without getting any closer for this long, it mines what it runs into. */
    private static final int BLOCKED_TICKS = 10;
    /** Floating up to a target standing high, it keeps this far from the middle of their pillar (its edge 0.2 block from it). */
    private static final double BESIDE_COLUMN = 1.0D;
    /** Without mining, building or getting any closer for this long, there is no way. */
    private static final int STUCK_TICKS = 20 * 5;

    private final PathfinderMob mob;
    private final Rise rise;
    private final ItemStack pickaxe;
    /** Whether it can hit the target from where it is, its own way. */
    private final Predicate<LivingEntity> canHit;
    private int unreachableTicks;
    @Nullable private BlockPos mining;
    private float miningProgress;
    /** What it held before taking a tool out (put back once done). */
    @Nullable private ItemStack putAway;
    /** BUILD: the block it jumped from, where it puts a block once above it. */
    @Nullable private BlockPos pillarBase;
    private boolean floating;
    private int idleTicks;
    private double closest = Double.MAX_VALUE;

    public Pursuit(PathfinderMob mob, Rise rise, ItemStack pickaxe, Predicate<LivingEntity> canHit)
    {
        this.mob = mob;
        this.rise = rise;
        this.pickaxe = pickaxe;
        this.canHit = canHit;
    }

    public static boolean canWalkTo(PathfinderMob mob, LivingEntity target)
    {
        Path path = mob.getNavigation().createPath(target, 0);
        return path != null && path.canReach();
    }

    /** Called every tick while its owner is after this target: whether they can still be walked to (looked for from the ground only, not while swimming). */
    public void track(ServerLevel level, LivingEntity target)
    {
        if (!this.mob.onGround() || this.isInLiquid() || this.mob.tickCount % PATH_CHECK_INTERVAL != 0) {return;}
        if (!canWalkTo(this.mob, target)) {this.unreachableTicks = Math.min(UNREACHABLE_TICKS, this.unreachableTicks + PATH_CHECK_INTERVAL);}
        else if (this.unreachableTicks > 0) {this.reset(level);}
    }

    /** Whether it gets to the target another way: they can't be walked to, it is floating towards them already, or Herobrine is swimming. */
    public boolean isActive() {return this.unreachableTicks >= UNREACHABLE_TICKS || this.floating || this.isInLiquid();}

    /** Herobrine in water or lava, even shallow (a current would push him back): he goes straight at them, as through the air. */
    private boolean isInLiquid() {return this.rise == Rise.FLOAT && this.mob.isInLiquid();}

    /** No way at all: nothing to mine, nowhere to build, not getting any closer. */
    public boolean isStuck() {return this.idleTicks >= STUCK_TICKS;}

    /** Can't be walked to, but can be hit from here: through a hole, from as far as a player could. */
    public boolean canHitThrough(LivingEntity target)
    {
        return this.isActive() && target.getBoundingBox().distanceToSqr(this.mob.getEyePosition()) <= HOLE_REACH * HOLE_REACH && this.mob.hasLineOfSight(target);
    }

    /** One more step towards the target: true while it takes over from its owner, false once it can hit them from where it is. */
    public boolean act(ServerLevel level, LivingEntity target)
    {
        //standing high, they are brought down first, as soon as what they stand on can be reached (even if they could be hit from below)
        boolean high = this.isAbove(target);
        BlockPos support = high ? support(level, target) : null;
        boolean bringDown = support != null && canMine(level, support) && this.isInReach(support);
        if (!bringDown && (this.canHit.test(target) || this.canHitThrough(target)))
        {
            this.stopMining(level);
            this.putBack();
            this.idleTicks = 0;
            return false;
        }
        Vec3 aim = support != null ? this.besideColumn(level, target, support) : target.position();
        //it mines standing still: floating, or on its feet (not halfway through a jump while building up)
        BlockPos block = this.rise == Rise.FLOAT || this.mob.onGround() ? this.blockToMine(level, target, high, support, bringDown) : null;
        if (block == null && this.floating && this.idleTicks >= BLOCKED_TICKS) {block = this.obstacle(level, aim, support);}
        if (block != null)
        {
            this.mine(level, block);
            return true;
        }
        this.stopMining(level);
        this.idleTicks++;
        if (high) {this.rise(level, target, support, aim);}
        else if (this.floating || this.isInLiquid()) {this.floatTowards(level, target, aim);}
        else {this.getCloser(level, target);}
        double distance = this.mob.distanceTo(target);
        if (distance < this.closest - 0.5D)
        {
            this.closest = distance;
            this.idleTicks = 0;
        }
        return true;
    }

    /** It stops whatever it was doing (it blinked away, it vanished...): it starts over from the ground. */
    public void reset(ServerLevel level)
    {
        this.stopMining(level);
        this.putBack();
        if (this.floating) {this.land();}
        this.pillarBase = null;
        this.unreachableTicks = 0;
        this.idleTicks = 0;
        this.closest = Double.MAX_VALUE;
    }

    //mining
    /**
     * What a target standing high stands on, as soon as it can be reached. Otherwise the block it started (a player finishes it too,
     * as long as they can reach it), unless it is in their pillar, under what they stand on: they built up over it, finishing it would only
     * leave a gap. Otherwise the block in the way (not when they stand high: it would only dig into their pillar, they wouldn't come down).
     */
    @Nullable private BlockPos blockToMine(ServerLevel level, LivingEntity target, boolean standingHigh, @Nullable BlockPos support, boolean bringDown)
    {
        if (bringDown) {return support;}
        if (this.mining != null && canMine(level, this.mining) && this.isInReach(this.mining) && !isUnder(this.mining, support)) {return this.mining;}
        if (standingHigh) {return null;}
        BlockPos inTheWay = this.blockInTheWay(level, target);
        return inTheWay != null && canMine(level, inTheWay) && this.isInReach(inTheWay) ? inTheWay : null;
    }

    private void mine(ServerLevel level, BlockPos pos)
    {
        if (!pos.equals(this.mining))
        {
            this.stopMining(level);
            this.mining = pos.immutable();
        }
        this.hold(this.pickaxe);
        this.mob.getNavigation().stop();
        if (this.floating) {this.mob.setDeltaMovement(Vec3.ZERO);}
        Facing.turn(this.mob, Vec3.atCenterOf(pos));
        BlockState state = level.getBlockState(pos);
        this.miningProgress += this.destroyProgress(level, pos, state);
        this.idleTicks = 0;
        if (this.mob.tickCount % 4 == 0)
        {
            this.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            SoundType sound = state.getSoundType();
            level.playSound(null, pos, sound.getHitSound(), SoundSource.HOSTILE, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
        }
        if (this.miningProgress < 1.0F)
        {
            level.destroyBlockProgress(this.mob.getId(), pos, (int) (this.miningProgress * 10.0F));
            return;
        }
        this.stopMining(level);
        level.destroyBlock(pos, true, this.mob);
    }

    /** What a player with that pickaxe would do of the block per tick (without the slowness of mining off the ground). */
    private float destroyProgress(ServerLevel level, BlockPos pos, BlockState state)
    {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {return 1.0F;}
        boolean rightTool = !state.requiresCorrectToolForDrops() || this.pickaxe.isCorrectToolForDrops(state);
        return this.pickaxe.getDestroySpeed(state) / hardness / (rightTool ? 30.0F : 100.0F);
    }

    private void stopMining(ServerLevel level)
    {
        if (this.mining != null) {level.destroyBlockProgress(this.mob.getId(), this.mining, -1);}
        this.mining = null;
        this.miningProgress = 0.0F;
    }

    private boolean isInReach(BlockPos pos) {return new AABB(pos).distanceToSqr(this.mob.getEyePosition()) <= MINING_REACH * MINING_REACH;}

    /**
     * Floating, what it runs into on its way to where it aims: the block most in its way. Not the ground under it (unless it aims lower),
     * nor the pillar of a target standing high (it gets them down from the top).
     */
    @Nullable private BlockPos obstacle(ServerLevel level, Vec3 aim, @Nullable BlockPos support)
    {
        Vec3 way = aim.subtract(this.mob.position());
        if (way.lengthSqr() < 1.0E-4D) {return null;}
        Vec3 direction = way.normalize();
        AABB ahead = this.mob.getBoundingBox().move(direction.scale(0.25D));
        VoxelShape aheadShape = Shapes.create(ahead);
        Vec3 center = this.mob.getBoundingBox().getCenter();
        boolean targetBelow = aim.y < this.mob.getY() - 1.0D;
        BlockPos obstacle = null;
        double mostInTheWay = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY, ahead.minZ), BlockPos.containing(ahead.maxX, ahead.maxY, ahead.maxZ)))
        {
            if (pos.getY() < this.mob.getBlockY() && !targetBelow) {continue;}
            if (isUnder(pos, support)) {continue;}
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty() || !Shapes.joinIsNotEmpty(shape.move(pos), aheadShape, BooleanOp.AND) || !canMine(level, pos)) {continue;}
            double inTheWay = Vec3.atCenterOf(pos).subtract(center).dot(direction);
            if (obstacle == null || inTheWay > mostInTheWay)
            {
                obstacle = pos.immutable();
                mostInTheWay = inTheWay;
            }
        }
        return obstacle;
    }

    /** The first block in the way of its eyes to the target's, null if it sees them. */
    @Nullable private BlockPos blockInTheWay(ServerLevel level, LivingEntity target)
    {
        BlockHitResult hit = level.clip(new ClipContext(this.mob.getEyePosition(), target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    /**
     * What a target standing high stands on, or will land on when they are not on the ground (jumping, building up): the highest block
     * under their feet, down to 3 blocks, the one closest to their middle (on the edge of a pillar, their middle may be over the void).
     */
    @Nullable private static BlockPos support(ServerLevel level, LivingEntity target)
    {
        if (target.onGround()) {return target.getOnPos();}
        //swimming: they don't stand on anything (what lies under the water wouldn't bring them down)
        if (!level.getFluidState(target.blockPosition()).isEmpty()) {return null;}
        AABB feet = target.getBoundingBox();
        int top = Mth.floor(feet.minY - 0.01D);
        for (int y = top; y > top - 3; y--)
        {
            BlockPos closest = null;
            for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(feet.minX), y, Mth.floor(feet.minZ), Mth.floor(feet.maxX - 1.0E-7D), y, Mth.floor(feet.maxZ - 1.0E-7D)))
            {
                if (!isOpen(level, pos) && (closest == null || Vec3.atCenterOf(pos).distanceToSqr(target.position()) < Vec3.atCenterOf(closest).distanceToSqr(target.position()))) {closest = pos.immutable();}
            }
            if (closest != null) {return closest;}
        }
        return null;
    }

    /** Something solid that can be broken: not bedrock, the seal, a barrier... (the wither_immune tag). */
    private static boolean canMine(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return !isOpen(level, pos) && state.getDestroySpeed(level, pos) >= 0.0F && !state.is(BlockTags.WITHER_IMMUNE);
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();}

    /** Whether that block is in the pillar of a target standing high, under what they stand on. */
    private static boolean isUnder(BlockPos pos, @Nullable BlockPos support) {return support != null && pos.getX() == support.getX() && pos.getZ() == support.getZ() && pos.getY() < support.getY();}

    //getting there
    private boolean isAbove(LivingEntity target) {return target.getY() - this.mob.getY() >= ABOVE;}

    /**
     * The target stands too high for what they stand on to be mined: Herobrine floats up towards them (aim: beside their pillar),
     * the imitator builds itself a pillar next to theirs.
     */
    private void rise(ServerLevel level, LivingEntity target, @Nullable BlockPos support, Vec3 aim)
    {
        if (this.rise == Rise.FLOAT)
        {
            this.floatTowards(level, target, aim);
            return;
        }
        Vec3 column = support != null ? Vec3.atBottomCenterOf(support) : target.position();
        //from too far, building up wouldn't bring it within reach: to the foot of their pillar first
        //(not towards the target: anything it stands on up here would look closer to them)
        if (column.subtract(this.mob.position()).horizontalDistance() <= MINING_REACH - 1.5D) {this.buildUp(level);}
        else
        {
            Vec3 foot = this.footOf(level, column);
            if (foot != null) {this.walkTo(foot);}
        }
    }

    /** The closest spot to stand on right next to that column, about as high as it stands now. */
    @Nullable private Vec3 footOf(ServerLevel level, Vec3 column)
    {
        BlockPos base = BlockPos.containing(column.x, this.mob.getY(), column.z);
        Vec3 closest = null;
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            BlockPos next = base.relative(side);
            BlockPos ground = HauntSpots.findStandableDown(level, next.getX(), next.getZ(), next.getY() + 1, next.getY() - 3);
            if (ground == null) {continue;}
            Vec3 spot = Vec3.atBottomCenterOf(ground);
            if (closest == null || spot.distanceToSqr(this.mob.position()) < closest.distanceToSqr(this.mob.position())) {closest = spot;}
        }
        return closest;
    }

    /** Jumps, and puts a block under its feet at the top of the jump, like a player building up. */
    private void buildUp(ServerLevel level)
    {
        BlockPos base = this.pillarBase;
        if (this.mob.onGround())
        {
            BlockPos feet = this.mob.blockPosition();
            //no room to jump up
            if (!HauntSpots.isFree(level, feet.above(2))) {return;}
            this.hold(new ItemStack(Items.COBBLESTONE));
            this.mob.getNavigation().stop();
            this.mob.setDeltaMovement(0.0D, this.mob.getDeltaMovement().y, 0.0D);
            this.mob.jumpFromGround();
            this.pillarBase = feet;
            this.idleTicks = 0;
        }
        else if (base != null && this.mob.getY() >= base.getY() + 1.0D && HauntSpots.isReplaceable(level, base))
        {
            BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
            level.setBlock(base, stone, Block.UPDATE_ALL);
            SoundType sound = stone.getSoundType();
            level.playSound(null, base, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(this.mob, GameEvent.BLOCK_PLACE, base);
            this.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            this.pillarBase = null;
            this.idleTicks = 0;
        }
    }

    /**
     * Where Herobrine floats to, to get at a target standing high: right beside what they stand on, on his side of it, at their height.
     * Not straight at their feet: into their pillar, he would get stuck under what they stand on (in a gap of it, they are out of his way).
     * In such a gap already, he first gets out of their pillar, through a side with room.
     */
    private Vec3 besideColumn(ServerLevel level, LivingEntity target, BlockPos support)
    {
        Vec3 column = Vec3.atBottomCenterOf(support);
        Vec3 side = this.mob.position().subtract(column).multiply(1.0D, 0.0D, 1.0D);
        if (side.lengthSqr() < 0.25D)
        {
            BlockPos under = BlockPos.containing(column.x, this.mob.getY(), column.z);
            Direction out = Direction.Plane.HORIZONTAL.stream().filter(direction -> isOpen(level, under.relative(direction)) && isOpen(level, under.relative(direction).above()))
                    .findFirst().orElse(this.mob.getDirection());
            side = new Vec3(out.getStepX(), 0.0D, out.getStepZ());
        }
        //(only touching it, it isn't in it)
        double overlap = 0.5D + this.mob.getBbWidth() / 2.0D - 0.01D;
        boolean inTheirPillar = Math.abs(this.mob.getX() - column.x) < overlap && Math.abs(this.mob.getZ() - column.z) < overlap;
        return column.add(side.normalize().scale(BESIDE_COLUMN)).with(Direction.Axis.Y, inTheirPillar ? this.mob.getY() : target.getY());
    }

    /** Herobrine only: straight there, through the air; it lands back on the ground close to the target, or once it can walk on. */
    private void floatTowards(ServerLevel level, LivingEntity target, Vec3 aim)
    {
        boolean landing = target.position().subtract(this.mob.position()).horizontalDistance() < LANDING_DISTANCE || isWalkable(level, this.mob.blockPosition().relative(this.towards(target)));
        if (this.floating && !this.isInLiquid() && !this.isAbove(target) && this.hasGroundBelow(level) && landing)
        {
            this.land();
            return;
        }
        this.floating = true;
        this.mob.setNoGravity(true);
        this.mob.getNavigation().stop();
        Vec3 way = aim.subtract(this.mob.position());
        double distance = way.length();
        this.mob.setDeltaMovement(distance < 0.1D ? Vec3.ZERO : way.scale(Math.min(FLOAT_SPEED, distance) / distance));
        Facing.turn(this.mob, target.getEyePosition());
    }

    private void land()
    {
        this.floating = false;
        this.mob.setNoGravity(false);
    }

    private boolean hasGroundBelow(ServerLevel level) {return !isOpen(level, BlockPos.containing(this.mob.getX(), this.mob.getY() - 0.2D, this.mob.getZ()));}

    /** Along the path as far as it goes (looked for again now and then), then digging on. */
    private void getCloser(ServerLevel level, LivingEntity target)
    {
        if (!this.mob.getNavigation().isDone()) {return;}
        if (this.mob.tickCount % 40 == 0 && this.walkTo(target.position())) {return;}
        this.digTowards(level, target);
    }

    /** Along the path towards there, as far as it goes: false if it goes nowhere. */
    private boolean walkTo(Vec3 goal)
    {
        PathNavigation navigation = this.mob.getNavigation();
        if (!navigation.isDone()) {return true;}
        return this.mob.tickCount % 10 == 0 && navigation.moveTo(goal.x, goal.y, goal.z, 1.0D) && !navigation.isDone();
    }

    /** A tunnel towards the target, like a player would dig it: the head block, then the feet one, then a step forward (or over the gap, for Herobrine). */
    private void digTowards(ServerLevel level, LivingEntity target)
    {
        BlockPos feet = this.mob.blockPosition();
        //right above them (in a hole they closed over themselves): straight down
        if (target.position().subtract(this.mob.position()).horizontalDistanceSqr() < 1.0D && target.getY() < this.mob.getY() - 1.0D)
        {
            if (canMine(level, feet.below())) {this.mine(level, feet.below());}
            return;
        }
        BlockPos next = feet.relative(this.towards(target));
        for (BlockPos pos : List.of(next.above(), next))
        {
            if (isOpen(level, pos)) {continue;}
            if (canMine(level, pos) && this.isInReach(pos)) {this.mine(level, pos);}
            return;
        }
        if (isWalkable(level, next)) {this.mob.getMoveControl().setWantedPosition(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D, 1.0D);}
        else if (this.rise == Rise.FLOAT) {this.floatTowards(level, target, target.position());}
    }

    /** The next block towards the target along the ground (along the axis they are the farthest on). */
    private Direction towards(LivingEntity target)
    {
        double dx = target.getX() - this.mob.getX(), dz = target.getZ() - this.mob.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0.0D ? Direction.EAST : Direction.WEST) : (dz > 0.0D ? Direction.SOUTH : Direction.NORTH);
    }

    /** Whether that next block can be walked in, or down to. */
    private static boolean isWalkable(ServerLevel level, BlockPos next) {return HauntSpots.isStandable(level, next) || HauntSpots.isStandable(level, next.below());}

    //tools
    /** Takes this out of its pocket (what it held goes in, until put back). */
    private void hold(ItemStack tool)
    {
        if (this.mob.getMainHandItem().is(tool.getItem())) {return;}
        if (this.putAway == null) {this.putAway = this.mob.getMainHandItem().copy();}
        this.mob.setItemSlot(EquipmentSlot.MAINHAND, tool.copy());
        this.mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private void putBack()
    {
        if (this.putAway == null) {return;}
        this.mob.setItemSlot(EquipmentSlot.MAINHAND, this.putAway);
        this.putAway = null;
    }
}
