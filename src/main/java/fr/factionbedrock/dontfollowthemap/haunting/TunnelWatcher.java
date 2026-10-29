package fr.factionbedrock.dontfollowthemap.haunting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the last blocks each player broke, to tell when they are digging a straight tunnel, 1 block wide and 2 blocks high.
 * A slice of tunnel is the two blocks dug one above the other. Not saved.
 */
public class TunnelWatcher
{
    /** A tunnel is at least this many slices long. */
    public static final int MIN_LENGTH = 6;
    private static final int MEMORY = 48;
    /** Older breaks don't count: the tunnel must be dug now, not found. */
    private static final long FORGET_AFTER = 20L * 60L * 3L;
    /** Since the last break: they are still digging. */
    private static final long STILL_DIGGING = 20L * 20L;
    /** The player stands at most this many slices behind the front. */
    private static final int MAX_DISTANCE_TO_FRONT = 2;

    private record Break(BlockPos pos, long time) {}

    private static final Map<UUID, Deque<Break>> BREAKS = new HashMap<>();

    public static void onBlockBroken(ServerPlayer player, BlockPos pos, long now)
    {
        Deque<Break> breaks = BREAKS.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>());
        breaks.addLast(new Break(pos.immutable(), now));
        while (breaks.size() > MEMORY) {breaks.removeFirst();}
    }

    public static void clear() {BREAKS.clear();}

    /** The direction in which this player is digging a straight 2x1 tunnel right now (standing in it, near its front), or null. */
    @Nullable public static Direction diggingDirection(ServerPlayer player, long now)
    {
        Deque<Break> breaks = BREAKS.get(player.getUUID());
        if (breaks == null || breaks.isEmpty() || now - breaks.getLast().time() > STILL_DIGGING) {return null;}
        Map<BlockPos, Long> dug = new HashMap<>();
        for (Break dugBlock : breaks) {if (now - dugBlock.time() <= FORGET_AFTER) {dug.put(dugBlock.pos(), dugBlock.time());}}
        BlockPos feet = player.blockPosition();
        for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z))
        {
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            //the dug slices on each side of the player's own one
            int ahead = 0, behind = 0;
            while (sliceTime(dug, feet.relative(positive, ahead + 1)) >= 0L) {ahead++;}
            while (sliceTime(dug, feet.relative(positive, -(behind + 1))) >= 0L) {behind++;}
            if (ahead + behind + 1 < MIN_LENGTH) {continue;}
            //the player stands near one end: the front, unless it was dug before the other one (they are walking back out)
            boolean positiveFront = ahead <= MAX_DISTANCE_TO_FRONT;
            if (!positiveFront && behind > MAX_DISTANCE_TO_FRONT) {continue;}
            BlockPos front = feet.relative(positive, positiveFront ? ahead : -behind), start = feet.relative(positive, positiveFront ? -behind : ahead);
            if (sliceTime(dug, front) < sliceTime(dug, start)) {continue;}
            return positiveFront ? positive : positive.getOpposite();
        }
        return null;
    }

    /** When both blocks of the slice standing on this floor position were dug (the later of the two), -1 if they weren't. */
    private static long sliceTime(Map<BlockPos, Long> dug, BlockPos floor)
    {
        Long bottom = dug.get(floor), top = dug.get(floor.above());
        return bottom != null && top != null ? Math.max(bottom, top) : -1L;
    }
}
