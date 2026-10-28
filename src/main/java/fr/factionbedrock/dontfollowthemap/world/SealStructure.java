package fr.factionbedrock.dontfollowthemap.world;

import com.mojang.serialization.MapCodec;
import fr.factionbedrock.dontfollowthemap.registry.DFTMStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** The shrine holding Herobrine's seal. Only generates on rather flat, dry ground. */
public class SealStructure extends Structure
{
    public static final MapCodec<SealStructure> CODEC = simpleCodec(SealStructure::new);
    private static final int MAX_HEIGHT_DIFFERENCE = 5;

    public SealStructure(StructureSettings settings) {super(settings);}

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        ChunkPos chunkPos = context.chunkPos();
        int west = chunkPos.getMinBlockX() + 1, north = chunkPos.getMinBlockZ() + 1;
        int last = SealPiece.SIZE - 1;
        int[][] samples = {{west, north}, {west + last, north}, {west, north + last}, {west + last, north + last}, {west + last / 2, north + last / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] sample : samples)
        {
            int surface = context.chunkGenerator().getFirstOccupiedHeight(sample[0], sample[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            int floor = context.chunkGenerator().getFirstOccupiedHeight(sample[0], sample[1], Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            //water above the ground
            if (surface != floor) {return Optional.empty();}
            min = Math.min(min, surface);
            max = Math.max(max, surface);
        }
        if (max - min > MAX_HEIGHT_DIFFERENCE) {return Optional.empty();}

        //the floor replaces the top layer of the ground
        int floorY = (min + max + 1) / 2;
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        return Optional.of(new GenerationStub(new BlockPos(west + last / 2, floorY, north + last / 2), builder -> builder.addPiece(new SealPiece(west, floorY, north, direction))));
    }

    @Override public StructureType<?> type() {return DFTMStructures.SEAL;}
}
