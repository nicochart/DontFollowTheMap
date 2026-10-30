package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.util.SignWoods;
import fr.factionbedrock.dontfollowthemap.util.SignWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Digs a closed grave in the natural ground near the player's bed: a 1x2 pit, two blocks deep, under the untouched surface,
 * with a headstone at its end. The player wakes up inside, in the dark, and has to dig their way up.
 */
public class GraveDigger
{
    /** @return the position of the player's feet in the grave, or null if no suitable ground was found. */
    @Nullable public static BlockPos dig(ServerLevel level, BlockPos bed, ServerPlayer player)
    {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 30; attempt++)
        {
            int x = bed.getX() + HauntSpots.randomOffset(random, 4, 10), z = bed.getZ() + HauntSpots.randomOffset(random, 4, 10);
            if (!level.hasChunkAt(x, z)) {continue;}
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(surfaceY - bed.getY()) > 12) {continue;}
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos head = new BlockPos(x, surfaceY, z), feet = head.relative(direction);
            BlockPos headstone = head.relative(direction.getOpposite()).above(), sign = head.above();
            if (!canDig(level, head, feet) || !canPlaceHeadstone(level, headstone, sign)) {continue;}

            for (BlockPos column : List.of(head, feet))
            {
                level.setBlock(column.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(column.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(headstone, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sign, SignWoods.localSign(level, sign, random, true).defaultBlockState().setValue(WallSignBlock.FACING, direction), Block.UPDATE_ALL);
            if (level.getBlockEntity(sign) instanceof SignBlockEntity signEntity)
            {
                SignWriter.write(signEntity, level.registryAccess(), SignWriter.centered(List.of(Component.translatable("sign.dontfollowthemap.grave"), Component.literal(player.getPlainTextName()))), false);
            }
            return head.below(2);
        }
        return null;
    }

    /** Ground that can be dug by hand, and closed on every side so that nothing pours in. */
    private static boolean canDig(ServerLevel level, BlockPos head, BlockPos feet)
    {
        for (BlockPos column : List.of(head, feet))
        {
            if (!isSoftGround(level.getBlockState(column))) {return false;}
            for (int depth = 1; depth <= 2; depth++)
            {
                BlockPos pos = column.below(depth);
                if (!isNaturalGround(level.getBlockState(pos))) {return false;}
                for (Direction side : Direction.Plane.HORIZONTAL)
                {
                    BlockPos wall = pos.relative(side);
                    if (wall.equals(head.below(depth)) || wall.equals(feet.below(depth))) {continue;}
                    if (!isClosedWall(level, wall)) {return false;}
                }
            }
            BlockPos floor = column.below(3);
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {return false;}
        }
        return true;
    }

    private static boolean canPlaceHeadstone(ServerLevel level, BlockPos headstone, BlockPos sign)
    {
        return HauntSpots.isReplaceable(level, headstone) && HauntSpots.isReplaceable(level, sign) && level.getBlockState(headstone.below()).isFaceSturdy(level, headstone.below(), Direction.UP);
    }

    /** The ceiling of the grave: what the player will have to dig through with bare hands. Never sand or gravel, which would fall in. */
    private static boolean isSoftGround(BlockState state)
    {
        return !state.hasBlockEntity() && (state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(Blocks.SNOW_BLOCK));
    }

    private static boolean isNaturalGround(BlockState state)
    {
        return !state.hasBlockEntity() && (isSoftGround(state) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.CLAY));
    }

    private static boolean isClosedWall(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos);
    }
}
