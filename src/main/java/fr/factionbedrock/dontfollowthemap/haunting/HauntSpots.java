package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Finds where things can happen: free spots, hidden spots, the player's home. */
public class HauntSpots
{
    /** A chunk must have been lived in for this long (summed over all players) to count as a home. */
    public static final long MIN_HOME_INHABITED_TIME = 12000L;
    /** Ground nobody built: data/dontfollowthemap/tags/block/natural_ground.json. */
    public static final TagKey<Block> NATURAL_GROUND = TagKey.create(Registries.BLOCK, DontFollowTheMap.id("natural_ground"));

    /**
     * Every block matching the predicate in a box around the center, in loaded chunks. Cheap enough to run on the fly:
     * most sections can tell from their palette that they hold no such block, and are skipped without looking at their 4096 blocks.
     */
    public static List<BlockPos> findBlocks(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius, Predicate<BlockState> predicate)
    {
        List<BlockPos> found = new ArrayList<>();
        int minSectionY = SectionPos.blockToSectionCoord(center.getY() - verticalRadius), maxSectionY = SectionPos.blockToSectionCoord(center.getY() + verticalRadius);
        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - horizontalRadius); chunkX <= SectionPos.blockToSectionCoord(center.getX() + horizontalRadius); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - horizontalRadius); chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + horizontalRadius); chunkZ++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {continue;}
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++)
                {
                    int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {continue;}
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir() || !section.maybeHas(predicate)) {continue;}
                    for (int y = 0; y < 16; y++) {for (int z = 0; z < 16; z++) {for (int x = 0; x < 16; x++)
                    {
                        if (!predicate.test(section.getBlockState(x, y, z))) {continue;}
                        BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(chunkX) + x, SectionPos.sectionToBlockCoord(sectionY) + y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                        if (Math.abs(pos.getX() - center.getX()) <= horizontalRadius && Math.abs(pos.getZ() - center.getZ()) <= horizontalRadius && Math.abs(pos.getY() - center.getY()) <= verticalRadius) {found.add(pos);}
                    }}}
                }
            }
        }
        return found;
    }

    public static boolean isFree(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE) && !state.is(Blocks.POWDER_SNOW) && !state.is(Blocks.SWEET_BERRY_BUSH);
    }

    /** Room for a player-sized entity, standing on solid ground. */
    public static boolean isStandable(ServerLevel level, BlockPos pos)
    {
        return isFree(level, pos) && isFree(level, pos.above()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Can a block be put here without destroying anything (air, grass, flowers...)? */
    public static boolean isReplaceable(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty() && !state.hasBlockEntity();
    }

    @Nullable public static BlockPos findStandableDown(ServerLevel level, int x, int z, int fromY, int toY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int y = fromY; y >= toY; y--)
        {
            pos.setY(y);
            if (isStandable(level, pos)) {return pos.immutable();}
        }
        return null;
    }

    /** Ground spot at the given column, near the given height: the surface when outside, the floor of the cave or room otherwise. */
    @Nullable public static BlockPos findGround(ServerLevel level, int x, int z, int nearY, boolean surface)
    {
        if (!level.hasChunkAt(x, z)) {return null;}
        if (surface)
        {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return Math.abs(top - nearY) > 24 ? null : findStandableDown(level, x, z, top, top - 3);
        }
        return findStandableDown(level, x, z, nearY + 8, nearY - 8);
    }

    public static boolean canSee(ServerLevel level, Vec3 from, Vec3 to)
    {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /** Horizontal angle, in degrees, between where the player looks and the given point. */
    public static double angleFromView(ServerPlayer player, Vec3 point)
    {
        Vec3 look = player.getViewVector(1.0F);
        double lookAngle = Math.atan2(look.z, look.x), pointAngle = Math.atan2(point.z - player.getZ(), point.x - player.getX());
        return Math.abs(Mth.wrapDegrees((pointAngle - lookAngle) * Mth.RAD_TO_DEG));
    }

    /** True if the player can't see the point right now (behind them, or behind a wall). */
    public static boolean isHiddenFrom(ServerPlayer player, Vec3 point)
    {
        return angleFromView(player, point) > 75.0D || !canSee(player.level(), player.getEyePosition(), point);
    }

    /**
     * A spot where a standing figure can be seen by the player, at a given distance and angle from where they look.
     * Angles are absolute values in degrees (0 = right in front, 180 = right behind).
     */
    @Nullable public static Vec3 findSightingSpot(ServerPlayer player, double minDistance, double maxDistance, double minAngle, double maxAngle)
    {
        return findSightingSpot(player, minDistance, maxDistance, minAngle, maxAngle, 0.0D);
    }

    /** The same, at least othersDistance away from every other player: one of them would come across the figure first. */
    @Nullable public static Vec3 findSightingSpot(ServerPlayer player, double minDistance, double maxDistance, double minAngle, double maxAngle, double othersDistance)
    {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        boolean outside = level.canSeeSky(player.blockPosition().above());
        float lookYaw = player.getYRot();
        for (int attempt = 0; attempt < 24; attempt++)
        {
            double angle = Mth.nextDouble(random, minAngle, maxAngle) * (random.nextBoolean() ? 1.0D : -1.0D);
            double yaw = (lookYaw + angle) * Mth.DEG_TO_RAD;
            double distance = Mth.nextDouble(random, minDistance, maxDistance);
            int x = Mth.floor(player.getX() - Math.sin(yaw) * distance), z = Mth.floor(player.getZ() + Math.cos(yaw) * distance);
            BlockPos ground = findGround(level, x, z, player.getBlockY(), outside);
            if (ground == null) {continue;}
            Vec3 feet = Vec3.atBottomCenterOf(ground);
            if (othersDistance > 0.0D && isNearOtherPlayer(player, feet, othersDistance)) {continue;}
            if (canSee(level, feet.add(0.0D, 1.6D, 0.0D), player.getEyePosition())) {return feet;}
        }
        return null;
    }

    /** Whether a player other than this one (spectators aside) is within distance of the point. */
    public static boolean isNearOtherPlayer(ServerPlayer player, Vec3 point, double distance)
    {
        return !player.level().getPlayers(other -> other != player && !other.isSpectator() && other.distanceToSqr(point) < distance * distance, 1).isEmpty();
    }

    /** A spot from which the player can't be seen being approached: out of view and on the ground. */
    @Nullable public static BlockPos findHiddenSpotAround(ServerPlayer player, BlockPos center, int minRadius, int maxRadius, int verticalRange)
    {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 20; attempt++)
        {
            int x = center.getX() + randomOffset(random, minRadius, maxRadius), z = center.getZ() + randomOffset(random, minRadius, maxRadius);
            BlockPos ground = level.hasChunkAt(x, z) ? findStandableDown(level, x, z, center.getY() + verticalRange, center.getY() - verticalRange) : null;
            if (ground != null && isHiddenFrom(player, Vec3.atCenterOf(ground.above()))) {return ground;}
        }
        return null;
    }

    public static int randomOffset(RandomSource random, int minRadius, int maxRadius)
    {
        int offset = Mth.nextInt(random, minRadius, maxRadius);
        return random.nextBoolean() ? offset : -offset;
    }

    /** The player's bed (or respawn anchor), if it is in their current dimension, loaded, and in a chunk they really live in. */
    @Nullable public static BlockPos findHome(ServerPlayer player)
    {
        ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn == null) {return null;}
        GlobalPos home = respawn.respawnData().globalPos();
        ServerLevel level = player.level();
        if (home.dimension() != level.dimension() || !level.isLoaded(home.pos())) {return null;}
        return level.getChunkAt(home.pos()).getInhabitedTime() >= MIN_HOME_INHABITED_TIME ? home.pos() : null;
    }

    /** Would a redstone torch placed here power something? (doors, pistons, TNT, redstone dust, lamps...) */
    public static boolean isNearRedstoneSensitiveBlock(ServerLevel level, BlockPos torchPos)
    {
        //a redstone torch powers its neighbours and, strongly, the block above it, which then powers its own neighbours
        for (BlockPos pos : BlockPos.betweenClosed(torchPos.offset(-1, -1, -1), torchPos.offset(1, 2, 1)))
        {
            if (pos.equals(torchPos)) {continue;}
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.TNT) || state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_LAMP) || state.isSignalSource()
                    || state.hasProperty(BlockStateProperties.POWERED) || state.hasProperty(BlockStateProperties.TRIGGERED)
                    || state.hasProperty(BlockStateProperties.EXTENDED) || state.hasProperty(BlockStateProperties.ENABLED)
                    || state.is(BlockTags.RAILS) || state.is(Blocks.NOTE_BLOCK)) {return true;}
        }
        return false;
    }
}
