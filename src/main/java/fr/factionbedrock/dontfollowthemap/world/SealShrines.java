package fr.factionbedrock.dontfollowthemap.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seal shrine is built on demand. The first map given out picks a place for it, every map then leads there,
 * until Herobrine is defeated in it: the shrine then falls into ruins as soon as nobody is around to see it, the maps leading to it burn,
 * and the next map picks a new place. Saved with the overworld, where shrines stand.
 */
public class SealShrines
{
    public enum Stage implements StringRepresentable
    {
        /** Its place is picked (the maps lead there): it is built as soon as its chunks are loaded. */
        PLANNED("planned"),
        BUILT("built"),
        /** Herobrine was defeated there, or another shrine replaced it: it falls into ruins once nobody sees it. */
        FORSAKEN("forsaken");

        public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);
        private final String name;

        Stage(String name) {this.name = name;}

        @Override public String getSerializedName() {return this.name;}
    }

    /** center: the column of the seal, and its height once built. */
    public record Shrine(int id, BlockPos center, Direction facing, Stage stage)
    {
        public static final Codec<Shrine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("id").forGetter(Shrine::id),
                BlockPos.CODEC.fieldOf("center").forGetter(Shrine::center),
                Direction.CODEC.fieldOf("facing").forGetter(Shrine::facing),
                Stage.CODEC.fieldOf("stage").forGetter(Shrine::stage)
        ).apply(instance, Shrine::new));

        private Shrine with(Stage stage) {return new Shrine(this.id, this.center, this.facing, stage);}
    }

    public static final Codec<SealShrines> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("last_id", 0).forGetter(shrines -> shrines.lastId),
            Shrine.CODEC.listOf().optionalFieldOf("shrines", List.of()).forGetter(shrines -> shrines.shrines),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, BlockPos.CODEC).optionalFieldOf("homes", Map.of()).forGetter(shrines -> shrines.homes)
    ).apply(instance, SealShrines::new));

    /** The biomes it can stand in (data/dontfollowthemap/tags/worldgen/biome/seal_shrines.json). */
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, DontFollowTheMap.id("seal_shrines"));
    /** From the player who gets the first map. */
    private static final int MIN_DISTANCE = 250, MAX_DISTANCE = 500;
    private static final int PLACE_ATTEMPTS = 32;
    /** Away from everyone's home, and from the players around (nobody sees it rise). */
    private static final double HOME_DISTANCE = 128.0D, PLAYER_DISTANCE = 128.0D;
    /** Players spent half an hour around such a chunk: it is someone's place. */
    private static final long LIVED_IN = 20L * 60L * 30L;
    private static final int MAX_HEIGHT_DIFFERENCE = 5;
    /** Closer than that, a player sees it, wherever they look. */
    private static final double WATCH_DISTANCE = 32.0D;

    private int lastId;
    /** At most one planned or built shrine (the one the maps lead to), and the forsaken ones still standing. */
    private final List<Shrine> shrines;
    /** The last known home of each player, kept away from. */
    private final Map<UUID, BlockPos> homes;

    public SealShrines() {this(0, List.of(), Map.of());}

    private SealShrines(int lastId, List<Shrine> shrines, Map<UUID, BlockPos> homes)
    {
        this.lastId = lastId;
        this.shrines = new ArrayList<>(shrines);
        this.homes = new HashMap<>(homes);
    }

    public static SealShrines get(MinecraftServer server) {return server.overworld().getAttachedOrCreate(DFTMAttachments.SEAL_SHRINES);}

    /** The shrine the maps lead to (planned or built), if any. */
    @Nullable public Shrine active()
    {
        for (Shrine shrine : this.shrines) {if (shrine.stage != Stage.FORSAKEN) {return shrine;}}
        return null;
    }

    /** Whether the shrine of this id is still there (or still to come): the maps leading to it are still good. */
    public boolean exists(int id)
    {
        for (Shrine shrine : this.shrines) {if (shrine.id == id) {return true;}}
        return false;
    }

    /** The shrine the maps lead to; if there is none, a place is picked for a new one around origin (null if none was found). */
    @Nullable public static Shrine activeOrPlan(ServerLevel overworld, BlockPos origin)
    {
        Shrine active = get(overworld.getServer()).active();
        return active != null ? active : plan(overworld, origin);
    }

    /** Picks a place for a new shrine around origin, which replaces the current one. Null if no place was found. */
    @Nullable public static Shrine plan(ServerLevel overworld, BlockPos origin)
    {
        SealShrines shrines = get(overworld.getServer());
        BlockPos center = shrines.findPlace(overworld, origin);
        if (center == null)
        {
            DontFollowTheMap.LOGGER.debug("No place found for a seal shrine around {}", origin);
            return null;
        }
        return shrines.replaceActive(new Shrine(++shrines.lastId, center, Direction.Plane.HORIZONTAL.getRandomDirection(overworld.getRandom()), Stage.PLANNED));
    }

    /** Builds a new shrine right here (its chunks must be loaded), which replaces the current one. */
    public static Shrine buildAt(ServerLevel overworld, BlockPos center)
    {
        SealShrines shrines = get(overworld.getServer());
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(overworld.getRandom());
        return shrines.replaceActive(new Shrine(++shrines.lastId, build(overworld, center, facing), facing, Stage.BUILT));
    }

    private Shrine replaceActive(Shrine shrine)
    {
        Shrine active = this.active();
        //never built: there is nothing to leave behind
        if (active != null && active.stage == Stage.PLANNED) {this.shrines.remove(active);}
        else if (active != null) {this.shrines.set(this.shrines.indexOf(active), active.with(Stage.FORSAKEN));}
        this.shrines.add(shrine);
        return shrine;
    }

    /** Herobrine was defeated at this seal: if it is the one the maps lead to, the next map will pick a new place. */
    public static void onDefeated(ServerLevel level, BlockPos sealPos)
    {
        SealShrines shrines = get(level.getServer());
        Shrine active = shrines.active();
        if (level == level.getServer().overworld() && active != null && active.stage == Stage.BUILT && active.center.equals(sealPos))
        {
            shrines.shrines.set(shrines.shrines.indexOf(active), active.with(Stage.FORSAKEN));
        }
    }

    /** Only the seal of the shrine the map leads to can be broken with it. */
    public static boolean opens(ServerLevel level, BlockPos sealPos, ItemStack map)
    {
        Shrine active = get(level.getServer()).active();
        return level == level.getServer().overworld() && active != null && active.stage == Stage.BUILT && active.center.equals(sealPos) && SealMaps.shrineId(map) == active.id;
    }

    /** Once per second: remembers where players live, builds the planned shrine and lets the forsaken ones fall into ruins, once their chunks are loaded. */
    public static void tick(ServerLevel overworld)
    {
        SealShrines shrines = get(overworld.getServer());
        for (ServerPlayer player : overworld.players())
        {
            BlockPos home = HauntSpots.findHome(player);
            if (home != null) {shrines.homes.put(player.getUUID(), home.immutable());}
        }
        for (Shrine shrine : List.copyOf(shrines.shrines))
        {
            if (!isLoaded(overworld, shrine.center)) {continue;}
            if (shrine.stage == Stage.PLANNED && !isAnyoneInside(overworld, shrine.center))
            {
                BlockPos center = findBuildSpot(overworld, shrine.center);
                shrines.shrines.set(shrines.shrines.indexOf(shrine), new Shrine(shrine.id, build(overworld, center, shrine.facing), shrine.facing, Stage.BUILT));
            }
            else if (shrine.stage == Stage.FORSAKEN && !isWatched(overworld, shrine)) {fallIntoRuins(overworld, shrine);}
        }
    }

    //where
    /** A rather flat, dry place in a fitting biome, at a distance from origin, where nobody lives or stands. */
    @Nullable private BlockPos findPlace(ServerLevel level, BlockPos origin)
    {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D, distance = Mth.nextDouble(random, MIN_DISTANCE, MAX_DISTANCE);
            BlockPos center = new BlockPos(origin.getX() + Mth.floor(Math.cos(angle) * distance), 0, origin.getZ() + Mth.floor(Math.sin(angle) * distance));
            if (this.isGoodPlace(level, center)) {return center;}
        }
        return null;
    }

    private boolean isGoodPlace(ServerLevel level, BlockPos center)
    {
        int reach = SealPiece.CENTER + 8;
        if (!level.getWorldBorder().isWithinBounds(center.offset(-reach, 0, -reach)) || !level.getWorldBorder().isWithinBounds(center.offset(reach, 0, reach))) {return false;}
        for (BlockPos home : this.homes.values()) {if (home.distToCenterSqr(center.getX(), home.getY(), center.getZ()) < HOME_DISTANCE * HOME_DISTANCE) {return false;}}
        for (ServerPlayer player : level.players()) {if (player.distanceToSqr(center.getX(), player.getY(), center.getZ()) < PLAYER_DISTANCE * PLAYER_DISTANCE) {return false;}}
        //as far as the loaded chunks tell: the others are only checked once loaded, when it is built
        if (isLivedIn(level, center)) {return false;}

        //the place may never have been generated: the terrain generator tells what the ground will be like there
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        int last = SealPiece.SIZE - 1, west = center.getX() - SealPiece.CENTER, north = center.getZ() - SealPiece.CENTER;
        int[][] samples = {{center.getX(), center.getZ()}, {west, north}, {west + last, north}, {west, north + last}, {west + last, north + last}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] sample : samples)
        {
            int surface = generator.getFirstOccupiedHeight(sample[0], sample[1], Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            int floor = generator.getFirstOccupiedHeight(sample[0], sample[1], Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            //water above the ground
            if (surface != floor) {return false;}
            min = Math.min(min, surface);
            max = Math.max(max, surface);
            //the biome, once the height of the ground is known
            if (sample == samples[0] && !level.getBiome(center.atY(surface)).is(BIOMES)) {return false;}
        }
        return max - min <= MAX_HEIGHT_DIFFERENCE;
    }

    /** Players spent a long time around one of its loaded chunks (a home, a farm...). Chunks that aren't loaded are not looked at. */
    private static boolean isLivedIn(ServerLevel level, BlockPos center)
    {
        for (long chunk : footprintChunks(center))
        {
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (loaded != null && loaded.getInhabitedTime() > LIVED_IN) {return true;}
        }
        return false;
    }

    public static boolean isLoaded(ServerLevel level, BlockPos center)
    {
        for (long chunk : footprintChunks(center)) {if (!level.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {return false;}}
        return true;
    }

    private static long[] footprintChunks(BlockPos center)
    {
        int minX = SectionPos.blockToSectionCoord(center.getX() - SealPiece.CENTER), maxX = SectionPos.blockToSectionCoord(center.getX() + SealPiece.CENTER);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - SealPiece.CENTER), maxZ = SectionPos.blockToSectionCoord(center.getZ() + SealPiece.CENTER);
        long[] chunks = new long[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int index = 0;
        for (int x = minX; x <= maxX; x++) {for (int z = minZ; z <= maxZ; z++) {chunks[index++] = ChunkPos.pack(x, z);}}
        return chunks;
    }

    //building
    /** Where it was planned, unless players live there (only loaded chunks were known when the place was picked): then a little further, if possible. */
    private static BlockPos findBuildSpot(ServerLevel level, BlockPos planned)
    {
        if (!isLivedIn(level, planned)) {return planned;}
        for (int radius = 16; radius <= 48; radius += 16)
        {
            for (int step = 0; step < 8; step++)
            {
                double angle = step * Math.PI / 4.0D;
                BlockPos candidate = planned.offset(Mth.floor(Math.cos(angle) * radius), 0, Mth.floor(Math.sin(angle) * radius));
                if (isLoaded(level, candidate) && !isLivedIn(level, candidate) && isFlatAndDry(level, candidate)) {return candidate;}
            }
        }
        //nowhere better around: he builds it anyway
        return planned;
    }

    private static boolean isFlatAndDry(ServerLevel level, BlockPos center)
    {
        int[] grounds = groundHeights(level, center);
        for (int i = 0; i < grounds.length; i++)
        {
            if (!level.getFluidState(sample(center, i).atY(grounds[i])).isEmpty()) {return false;}
        }
        return Arrays.stream(grounds).max().orElse(0) - Arrays.stream(grounds).min().orElse(0) <= MAX_HEIGHT_DIFFERENCE;
    }

    /** The top of the ground (trees aside) at the center and at the four corners. */
    private static int[] groundHeights(ServerLevel level, BlockPos center)
    {
        int[] grounds = new int[5];
        for (int i = 0; i < grounds.length; i++)
        {
            BlockPos sample = sample(center, i);
            grounds[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ()) - 1;
        }
        return grounds;
    }

    private static BlockPos sample(BlockPos center, int index)
    {
        int reach = SealPiece.CENTER;
        return switch (index) {case 1 -> center.offset(-reach, 0, -reach); case 2 -> center.offset(reach, 0, -reach); case 3 -> center.offset(-reach, 0, reach); case 4 -> center.offset(reach, 0, reach); default -> center;};
    }

    private static boolean isAnyoneInside(ServerLevel level, BlockPos center)
    {
        AABB area = new AABB(center).inflate(SealPiece.CENTER + 2, level.getHeight(), SealPiece.CENTER + 2);
        return !level.getEntitiesOfClass(ServerPlayer.class, area).isEmpty();
    }

    /** Builds the shrine around this column, on the ground (its floor replaces the top layer), and returns where its seal stands. */
    private static BlockPos build(ServerLevel level, BlockPos center, Direction facing)
    {
        int[] grounds = groundHeights(level, center);
        Arrays.sort(grounds);
        //the median: a tree trunk or a hole on one of the samples doesn't matter
        int floorY = grounds[grounds.length / 2];
        SealPiece piece = new SealPiece(center.getX() - SealPiece.CENTER, floorY, center.getZ() - SealPiece.CENTER, facing);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), BoundingBox.infinite(), ChunkPos.containing(center), center);
        return piece.getSealPos();
    }

    //ruins
    /** Someone close to it, or further away but looking its way, with nothing in between. */
    public static boolean isWatched(ServerLevel level, Shrine shrine)
    {
        BlockPos seal = shrine.center;
        int reach = SealPiece.CENTER - 2;
        //the middle and the tops of the pillars
        List<Vec3> marks = List.of(Vec3.atCenterOf(seal.above()), Vec3.atCenterOf(seal.offset(-reach, 5, -reach)), Vec3.atCenterOf(seal.offset(reach, 5, -reach)), Vec3.atCenterOf(seal.offset(-reach, 5, reach)), Vec3.atCenterOf(seal.offset(reach, 5, reach)));
        //beyond, nothing is drawn (and nothing on the way is loaded for sure)
        double viewDistance = level.getServer().getPlayerList().getViewDistance() * 16.0D;
        for (ServerPlayer player : level.players())
        {
            double distance = player.position().distanceTo(marks.getFirst());
            if (distance < WATCH_DISTANCE) {return true;}
            if (distance < viewDistance && marks.stream().anyMatch(mark -> !HauntSpots.isHiddenFrom(player, mark))) {return true;}
        }
        return false;
    }

    /** The seal goes, and most of the shrine with it: a few stones of the floor and of the pillars stay, as ruins. The maps leading to it are worthless from now on. */
    public static void fallIntoRuins(ServerLevel level, Shrine shrine)
    {
        BlockPos seal = shrine.center;
        int floorY = seal.getY() - 1, west = seal.getX() - SealPiece.CENTER, north = seal.getZ() - SealPiece.CENTER;
        Ground ground = groundAround(level, seal);
        //the same ruins whenever it happens
        RandomSource random = RandomSource.create(shrine.id * 341873128712L + seal.asLong());
        for (int x = 0; x < SealPiece.SIZE; x++)
        {
            for (int z = 0; z < SealPiece.SIZE; z++)
            {
                //-1: nothing stays; 0: the floor stone; 1 or 2: the floor and the stones above it (pillar stumps)
                int stump = random.nextFloat() < 0.3F ? random.nextInt(3) : -1;
                //from the top down, so that nothing is left hanging (a candle on a stone that goes would drop)
                for (int height = SealPiece.HEIGHT - 1; height >= 0; height--)
                {
                    BlockPos pos = new BlockPos(west + x, floorY + height, north + z);
                    BlockState state = level.getBlockState(pos);
                    if (!isShrineBlock(state) || (height <= stump && isShrineStone(state))) {continue;}
                    if (height > 0)
                    {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        continue;
                    }
                    //where the floor was, the ground comes back as it is all around (snow included)
                    level.setBlock(pos, ground.top, Block.UPDATE_ALL);
                    if (!ground.cover.isAir() && level.getBlockState(pos.above()).isAir()) {level.setBlock(pos.above(), ground.cover, Block.UPDATE_ALL);}
                }
            }
        }
        get(level.getServer()).shrines.removeIf(standing -> standing.id == shrine.id);
    }

    private static boolean isShrineStone(BlockState state)
    {
        return state.is(Blocks.DEEPSLATE_TILES) || state.is(Blocks.CRACKED_DEEPSLATE_TILES) || state.is(Blocks.POLISHED_BLACKSTONE) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || state.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) || state.is(Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    /** What the shrine is made of (its foundation and its buried chest aside). */
    public static boolean isShrineBlock(BlockState state)
    {
        return isShrineStone(state) || state.is(DFTMBlocks.SEAL) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.IRON_CHAIN) || state.is(BlockTags.CANDLES) || state.is(Blocks.DARK_OAK_SIGN);
    }

    /** The top of the ground, and what lies on it (a layer of snow, or nothing). */
    private record Ground(BlockState top, BlockState cover) {}

    /**
     * The ground all around the shrine, just outside it: the most common top block and the most common cover.
     * A single spot (a patch of gravel by the water...) doesn't decide.
     */
    private static Ground groundAround(ServerLevel level, BlockPos seal)
    {
        Map<BlockState, Integer> tops = new HashMap<>(), covers = new HashMap<>();
        int reach = SealPiece.CENTER + 2;
        for (int offset = -reach; offset <= reach; offset += 2)
        {
            for (BlockPos column : List.of(seal.offset(offset, 0, -reach), seal.offset(offset, 0, reach), seal.offset(-reach, 0, offset), seal.offset(reach, 0, offset)))
            {
                //beyond the shrine's chunks: maybe not loaded
                if (!level.hasChunkAt(column)) {continue;}
                BlockPos ground = column.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1);
                BlockState top = level.getBlockState(ground);
                if (!(top.is(BlockTags.SUBSTRATE_OVERWORLD) || top.is(BlockTags.SAND) || top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(Blocks.GRAVEL) || top.is(Blocks.SNOW_BLOCK))) {continue;}
                tops.merge(top, 1, Integer::sum);
                BlockState above = level.getBlockState(ground.above());
                covers.merge(above.is(Blocks.SNOW) ? above : Blocks.AIR.defaultBlockState(), 1, Integer::sum);
            }
        }
        return new Ground(mostCommon(tops, Blocks.DIRT.defaultBlockState()), mostCommon(covers, Blocks.AIR.defaultBlockState()));
    }

    private static BlockState mostCommon(Map<BlockState, Integer> counts, BlockState fallback)
    {
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(fallback);
    }
}
