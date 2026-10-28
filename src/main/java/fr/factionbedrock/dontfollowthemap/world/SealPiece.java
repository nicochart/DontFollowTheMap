package fr.factionbedrock.dontfollowthemap.world;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import fr.factionbedrock.dontfollowthemap.util.SignWriter;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * The seal shrine: an octagonal deepslate platform, four pillars topped with soul lanterns,
 * and the seal in the middle, chained to the ground in the four directions.
 * Built block by block so that it fits any orientation; the shrine's front (the warning sign) is local z = SIZE - 1.
 */
public class SealPiece extends StructurePiece
{
    public static final int SIZE = 13, HEIGHT = 8, CENTER = SIZE / 2;
    public static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, DontFollowTheMap.id("chests/seal_shrine"));

    public SealPiece(int west, int floorY, int north, Direction direction)
    {
        super(DFTMStructures.SEAL_PIECE, 0, makeBoundingBox(west, floorY, north, direction, SIZE, HEIGHT, SIZE));
        this.setOrientation(direction);
    }

    public SealPiece(CompoundTag tag) {super(DFTMStructures.SEAL_PIECE, tag);}

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

    /** World position of the seal block. */
    public BlockPos getSealPos() {return this.getWorldPos(CENTER, 1, CENTER);}

    private static boolean isInside(int x, int z)
    {
        int dx = Math.abs(x - CENTER), dz = Math.abs(z - CENTER);
        return dx <= CENTER && dz <= CENTER && dx + dz <= CENTER + 3;
    }

    private static boolean isBorder(int x, int z)
    {
        int dx = Math.abs(x - CENTER), dz = Math.abs(z - CENTER);
        return dx == CENTER || dz == CENTER || dx + dz >= CENTER + 2;
    }

    /** Same result in every chunk the piece crosses, unlike the chunk's random. */
    private float noise(int x, int y, int z)
    {
        BlockPos pos = this.getWorldPos(x, y, z);
        return RandomSource.create(Mth.getSeed(pos.getX(), pos.getY(), pos.getZ())).nextFloat();
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos)
    {
        BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(), crackedTiles = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), crackedBricks = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), polished = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState foundation = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

        for (int x = 0; x < SIZE; x++)
        {
            for (int z = 0; z < SIZE; z++)
            {
                if (!isInside(x, z)) {continue;}
                //clear the terrain above the platform, then lay the floor and fill the gap under it
                for (int y = 1; y < HEIGHT; y++) {this.placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, chunkBB);}
                BlockState floor;
                if (isBorder(x, z)) {floor = this.noise(x, 0, z) < 0.2F ? crackedBricks : bricks;}
                else if (Math.abs(x - CENTER) <= 1 && Math.abs(z - CENTER) <= 1) {floor = chiseled;}
                else {floor = this.noise(x, 0, z) < 0.15F ? crackedTiles : tiles;}
                this.placeBlock(level, floor, x, 0, z, chunkBB);
                this.fillColumnDown(level, foundation, x, -1, z, chunkBB);
            }
        }

        //pillars topped with soul lanterns
        for (int[] corner : new int[][] {{2, 2}, {2, SIZE - 3}, {SIZE - 3, 2}, {SIZE - 3, SIZE - 3}})
        {
            for (int y = 1; y <= 4; y++) {this.placeBlock(level, this.noise(corner[0], y, corner[1]) < 0.25F ? crackedBricks : polished, corner[0], y, corner[1], chunkBB);}
            this.placeBlock(level, chiseled, corner[0], 5, corner[1], chunkBB);
            this.placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), corner[0], 6, corner[1], chunkBB);
        }

        //the seal, chained in the four directions to anchors holding a candle
        this.placeBlock(level, DFTMBlocks.SEAL.defaultBlockState(), CENTER, 1, CENTER, chunkBB);
        BlockState chainX = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X);
        BlockState chainZ = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z);
        BlockState anchorCandle = Blocks.DYED_CANDLE.pick(DyeColor.WHITE).defaultBlockState().setValue(CandleBlock.LIT, true);
        for (int offset = 1; offset <= 3; offset++)
        {
            this.placeBlock(level, chainX, CENTER - offset, 1, CENTER, chunkBB);
            this.placeBlock(level, chainX, CENTER + offset, 1, CENTER, chunkBB);
            this.placeBlock(level, chainZ, CENTER, 1, CENTER - offset, chunkBB);
            this.placeBlock(level, chainZ, CENTER, 1, CENTER + offset, chunkBB);
        }
        for (int[] anchor : new int[][] {{CENTER - 4, CENTER}, {CENTER + 4, CENTER}, {CENTER, CENTER - 4}, {CENTER, CENTER + 4}})
        {
            this.placeBlock(level, chiseled, anchor[0], 1, anchor[1], chunkBB);
            this.placeBlock(level, anchorCandle, anchor[0], 2, anchor[1], chunkBB);
        }

        //melted candles around the seal
        for (int[] spot : new int[][] {{CENTER - 2, CENTER - 2}, {CENTER + 2, CENTER + 2}, {CENTER - 2, CENTER + 2}, {CENTER + 2, CENTER - 2}})
        {
            int count = 1 + (int) (this.noise(spot[0], 1, spot[1]) * 4.0F);
            this.placeBlock(level, Blocks.DYED_CANDLE.pick(DyeColor.WHITE).defaultBlockState().setValue(CandleBlock.CANDLES, Math.min(4, count)).setValue(CandleBlock.LIT, count > 1), spot[0], 1, spot[1], chunkBB);
        }

        //something was buried under the seal
        this.createChest(level, chunkBB, random, CENTER, -1, CENTER - 1, CHEST_LOOT);

        //a warning at the entrance, facing away from the shrine
        this.placeBlock(level, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), CENTER, 1, SIZE - 1, chunkBB);
        BlockPos signPos = this.getWorldPos(CENTER, 1, SIZE - 1);
        if (chunkBB.isInside(signPos) && level.getBlockEntity(signPos) instanceof SignBlockEntity sign)
        {
            SignWriter.write(sign, level.registryAccess(), SignWriter.centered(List.of(Component.translatable("sign.dontfollowthemap.shrine.1"), Component.translatable("sign.dontfollowthemap.shrine.2"))), false);
        }
    }
}
