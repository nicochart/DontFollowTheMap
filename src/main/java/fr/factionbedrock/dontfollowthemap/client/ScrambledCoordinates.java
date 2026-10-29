package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedAnimal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * While something of Herobrine's is around (an apparition, Herobrine himself, an imitator, the thief, a possessed animal),
 * the debug screen (F3) lies about where the player is: its coordinates jump from one random place to another.
 */
public class ScrambledCoordinates
{
    private static final double RANGE = 96.0D;
    /** A new lie every half second. */
    private static final int INTERVAL = 10;
    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean active;
    private static double x, y, z;

    public static boolean isActive() {return active;}

    public static void tick(Minecraft minecraft)
    {
        Entity camera = minecraft.getCameraEntity();
        if (minecraft.level == null || camera == null)
        {
            active = false;
            return;
        }
        if (camera.tickCount % INTERVAL != 0) {return;}
        active = isSomethingAround(minecraft.level, camera);
        if (!active) {return;}
        x = Mth.nextDouble(RANDOM, -30000.0D, 30000.0D);
        y = Mth.nextDouble(RANDOM, minecraft.level.getMinY(), minecraft.level.getMaxY());
        z = Mth.nextDouble(RANDOM, -30000.0D, 30000.0D);
    }

    private static boolean isSomethingAround(ClientLevel level, Entity camera)
    {
        for (Entity entity : level.entitiesForRendering())
        {
            if ((entity instanceof HerobrineApparitionEntity || entity instanceof HerobrineEntity || entity instanceof ImitatorEntity || entity instanceof UnseenEntity || entity instanceof PossessedAnimal)
                    && entity.distanceToSqr(camera) < RANGE * RANGE) {return true;}
        }
        return false;
    }

    /** The position lines of the debug screen (DebugEntryPositionMixin), with the made up place instead of the real one; the other lines stay. */
    public static List<String> scramble(Collection<String> lines)
    {
        int blockX = Mth.floor(x), blockY = Mth.floor(y), blockZ = Mth.floor(z);
        ChunkPos chunk = new ChunkPos(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
        List<String> scrambled = new ArrayList<>();
        for (String line : lines)
        {
            if (line.startsWith("XYZ:")) {scrambled.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", x, y, z));}
            else if (line.startsWith("Block:")) {scrambled.add(String.format(Locale.ROOT, "Block: %d %d %d", blockX, blockY, blockZ));}
            else if (line.startsWith("Chunk:"))
            {
                scrambled.add(String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]", chunk.x(), SectionPos.blockToSectionCoord(blockY), chunk.z(),
                        chunk.getRegionLocalX(), chunk.getRegionLocalZ(), chunk.getRegionX(), chunk.getRegionZ()));
            }
            else {scrambled.add(line);}
        }
        return scrambled;
    }
}
