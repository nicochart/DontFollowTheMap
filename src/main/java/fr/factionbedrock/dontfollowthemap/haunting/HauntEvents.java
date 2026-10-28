package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.PossessedSheepEntity;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import fr.factionbedrock.dontfollowthemap.util.SignWriter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Every haunting event. Each one returns false when it could not happen right now (no room, nothing to haunt...). */
public class HauntEvents
{
    //sightings
    public static boolean sighting(ServerPlayer player, int stage, boolean leavesMap)
    {
        if (hasActiveApparition(player)) {return false;}
        double minDistance = stage >= HauntingData.HUNTED ? 22.0D : stage == HauntingData.HAUNTED ? 26.0D : 30.0D;
        //in the corner of the eye first, anywhere around if there is no room there
        Vec3 spot = HauntSpots.findSightingSpot(player, minDistance, minDistance + 14.0D, 20.0D, 75.0D);
        if (spot == null) {spot = HauntSpots.findSightingSpot(player, minDistance, minDistance + 14.0D, 0.0D, 180.0D);}
        if (spot == null) {return false;}
        HerobrineApparitionEntity apparition = HerobrineApparitionEntity.spawn(player.level(), spot, player, HerobrineApparitionEntity.Mode.WATCH, 20 * 90);
        apparition.setLeavesMap(leavesMap);
        HauntingData.get(player).sightings++;
        return true;
    }

    /** Right behind the player, waiting for them to turn around. */
    public static boolean stalk(ServerPlayer player)
    {
        if (hasActiveApparition(player)) {return false;}
        Vec3 spot = HauntSpots.findSightingSpot(player, 13.0D, 18.0D, 140.0D, 180.0D);
        if (spot == null) {return false;}
        HerobrineApparitionEntity.spawn(player.level(), spot, player, HerobrineApparitionEntity.Mode.WATCH, 20 * 40);
        HauntingData.get(player).sightings++;
        return true;
    }

    public static boolean hasActiveApparition(ServerPlayer player)
    {
        return !player.level().getEntitiesOfClass(HerobrineApparitionEntity.class, player.getBoundingBox().inflate(96.0D), apparition -> apparition.getTargetPlayer() == player).isEmpty();
    }

    //sounds only the player hears
    public static boolean whisper(ServerPlayer player)
    {
        RandomSource random = player.getRandom();
        Vec3 behind = player.position().subtract(player.getViewVector(1.0F).multiply(3.0D, 0.0D, 3.0D));
        if (random.nextBoolean()) {playSoundTo(player, DFTMSounds.WHISPER, behind.add(0.0D, 1.5D, 0.0D), 0.6F, 0.9F + random.nextFloat() * 0.2F);}
        else
        {
            //footsteps on the block behind the player
            BlockPos ground = BlockPos.containing(behind).below();
            SoundEvent step = player.level().getBlockState(ground).getSoundType().getStepSound();
            for (int i = 0; i < 4; i++)
            {
                Vec3 stepPos = behind.subtract(player.getViewVector(1.0F).multiply(i * 0.8D, 0.0D, i * 0.8D));
                DelayedTasks.schedule(1 + i * 7, () -> playSoundTo(player, step, stepPos, 0.25F, 0.9F));
            }
        }
        return true;
    }

    public static void playSoundTo(ServerPlayer player, SoundEvent sound, Vec3 pos, float volume, float pitch)
    {
        player.connection.send(new ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
    }

    //torches
    /** Turns some of the torches the player can't see into redstone torches for a while. */
    public static boolean swapTorches(ServerPlayer player, int maxTorches)
    {
        ServerLevel level = player.level();
        List<BlockPos> torches = findTorches(level, player.blockPosition(), 18, 8);
        TorchSwaps swaps = TorchSwaps.get(level);
        long now = level.getGameTime();
        RandomSource random = player.getRandom();
        int swapped = 0;
        for (int i = torches.size() - 1; i >= 0 && swapped < maxTorches; i--)
        {
            BlockPos pos = torches.remove(random.nextInt(i + 1));
            if (swaps.contains(pos) || !HauntSpots.isHiddenFrom(player, Vec3.atCenterOf(pos)) || HauntSpots.isNearRedstoneSensitiveBlock(level, pos)) {continue;}
            BlockState torch = level.getBlockState(pos);
            BlockState redstoneTorch = torch.is(Blocks.WALL_TORCH) ? Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, torch.getValue(WallTorchBlock.FACING)) : Blocks.REDSTONE_TORCH.defaultBlockState();
            //no neighbour update: the torch must not trigger anything
            level.setBlock(pos, redstoneTorch.setValue(RedstoneTorchBlock.LIT, true), Block.UPDATE_CLIENTS);
            swaps.add(new TorchSwaps.Swap(pos.immutable(), torch, now + Mth.nextInt(random, 20 * 30, 20 * 80)));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.15F, 0.6F);
            swapped++;
        }
        return swapped > 0;
    }

    private static List<BlockPos> findTorches(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius)
    {
        List<BlockPos> torches = new ArrayList<>();
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
                    //most sections have no torch at all: skip them without looking at their 4096 blocks
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH))) {continue;}
                    for (int y = 0; y < 16; y++) {for (int z = 0; z < 16; z++) {for (int x = 0; x < 16; x++)
                    {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)) {continue;}
                        BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(chunkX) + x, SectionPos.sectionToBlockCoord(sectionY) + y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                        if (Math.abs(pos.getX() - center.getX()) <= horizontalRadius && Math.abs(pos.getZ() - center.getZ()) <= horizontalRadius && Math.abs(pos.getY() - center.getY()) <= verticalRadius) {torches.add(pos);}
                    }}}
                }
            }
        }
        return torches;
    }

    //signs
    /** Number of lines of each sign message (keys sign.dontfollowthemap.message.<index>.<line>). The last ones only once the map is out. */
    private static final int[] MESSAGE_LINES = {1, 3, 2, 1, 2, 2, 2, 1, 2};
    private static final int MESSAGES_BEFORE_MAP = 6;

    public static boolean placeSign(ServerPlayer player)
    {
        BlockPos home = HauntSpots.findHome(player);
        if (home == null) {return false;}
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 30; attempt++)
        {
            BlockPos pos = home.offset(HauntSpots.randomOffset(random, 2, 8), Mth.nextInt(random, -2, 2), HauntSpots.randomOffset(random, 2, 8));
            if (!level.isLoaded(pos) || !HauntSpots.isReplaceable(level, pos) || !HauntSpots.isFree(level, pos.above()) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {continue;}
            if (!HauntSpots.isHiddenFrom(player, Vec3.atCenterOf(pos))) {continue;}
            //the text faces the bed
            double dx = pos.getX() - home.getX(), dz = pos.getZ() - home.getZ();
            float yawFromHome = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yawFromHome + 180.0F)), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {SignWriter.write(sign, level.registryAccess(), SignWriter.centered(pickMessage(player, random)), false);}
            return true;
        }
        return false;
    }

    private static List<Component> pickMessage(ServerPlayer player, RandomSource random)
    {
        //once in a while, he just says hello
        if (random.nextInt(8) == 0) {return List.of(Component.translatable("sign.dontfollowthemap.hello"), Component.literal(player.getPlainTextName()));}
        int available = HauntingData.get(player).hasMapBeenDelivered() ? MESSAGE_LINES.length : MESSAGES_BEFORE_MAP;
        int message = random.nextInt(available);
        List<Component> lines = new ArrayList<>();
        for (int line = 1; line <= MESSAGE_LINES[message]; line++) {lines.add(Component.translatable("sign.dontfollowthemap.message." + message + "." + line));}
        return lines;
    }

    //builds
    private enum Build {PILLAR, CROSS, PYRAMID}

    /** A small construction near the player's home, far enough to be found later, where nobody watches. */
    public static boolean build(ServerPlayer player)
    {
        BlockPos home = HauntSpots.findHome(player);
        if (home == null) {return false;}
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        Build build = Build.values()[random.nextInt(Build.values().length)];
        for (int attempt = 0; attempt < 30; attempt++)
        {
            int x = home.getX() + HauntSpots.randomOffset(random, 9, 22), z = home.getZ() + HauntSpots.randomOffset(random, 9, 22);
            if (!level.hasChunkAt(x, z)) {continue;}
            BlockPos base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(base.getY() - home.getY()) > 16 || !HauntSpots.isHiddenFrom(player, Vec3.atCenterOf(base.above())) || player.distanceToSqr(Vec3.atCenterOf(base)) < 16.0D * 16.0D) {continue;}
            List<BlockPos> blocks = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            describeBuild(build, base, random, blocks, states);
            if (!canBuild(level, build, base, blocks)) {continue;}
            for (int i = 0; i < blocks.size(); i++) {level.setBlock(blocks.get(i), states.get(i), Block.UPDATE_ALL);}
            return true;
        }
        return false;
    }

    private static void describeBuild(Build build, BlockPos base, RandomSource random, List<BlockPos> blocks, List<BlockState> states)
    {
        switch (build)
        {
            case PILLAR ->
            {
                blocks.add(base); states.add(Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                blocks.add(base.above()); states.add(Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                blocks.add(base.above(2)); states.add(Blocks.REDSTONE_TORCH.defaultBlockState());
            }
            case CROSS ->
            {
                Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
                for (int y = 0; y < 4; y++) {blocks.add(base.above(y)); states.add(Blocks.COBBLESTONE.defaultBlockState());}
                blocks.add(base.above(2).relative(axis, 1)); states.add(Blocks.COBBLESTONE.defaultBlockState());
                blocks.add(base.above(2).relative(axis, -1)); states.add(Blocks.COBBLESTONE.defaultBlockState());
            }
            case PYRAMID ->
            {
                for (int dx = -1; dx <= 1; dx++) {for (int dz = -1; dz <= 1; dz++) {blocks.add(base.offset(dx, 0, dz)); states.add(Blocks.SAND.defaultBlockState());}}
                blocks.add(base.above()); states.add(Blocks.SAND.defaultBlockState());
            }
        }
    }

    private static boolean canBuild(ServerLevel level, Build build, BlockPos base, List<BlockPos> blocks)
    {
        for (BlockPos pos : blocks) {if (!level.isLoaded(pos) || !HauntSpots.isReplaceable(level, pos)) {return false;}}
        //on natural ground only, never on the player's own constructions
        List<BlockPos> footprint = build == Build.PYRAMID ? blocks.subList(0, 9) : List.of(base);
        for (BlockPos pos : footprint)
        {
            BlockState ground = level.getBlockState(pos.below());
            if (!ground.isFaceSturdy(level, pos.below(), Direction.UP) || !(ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SNOW_BLOCK))) {return false;}
        }
        return build != Build.PILLAR || !HauntSpots.isNearRedstoneSensitiveBlock(level, base.above(2));
    }

    //possessed sheep
    public static boolean possessSheep(ServerPlayer player)
    {
        List<Sheep> sheep = player.level().getEntitiesOfClass(Sheep.class, player.getBoundingBox().inflate(32.0D, 12.0D, 32.0D),
                candidate -> candidate.getType() == EntityTypes.SHEEP && candidate.isAlive() && !candidate.isBaby() && !candidate.isLeashed() && !candidate.isPassenger() && !candidate.isVehicle() && !candidate.isNoAi());
        if (sheep.isEmpty()) {return false;}
        return PossessedSheepEntity.possess(sheep.get(player.getRandom().nextInt(sheep.size())), player) != null;
    }

    //apparitions caused by the player
    /** Herobrine appears for an instant where the player just killed something. */
    public static boolean killApparition(ServerPlayer player, Vec3 corpse)
    {
        ServerLevel level = player.level();
        BlockPos pos = BlockPos.containing(corpse);
        //a creature killed in a 1 block high gap or in mid air: try just above, give up otherwise
        BlockPos standing = HauntSpots.isStandable(level, pos) ? pos : HauntSpots.isStandable(level, pos.above()) ? pos.above() : HauntSpots.findStandableDown(level, pos.getX(), pos.getZ(), pos.getY(), pos.getY() - 3);
        if (standing == null) {return false;}
        HerobrineApparitionEntity.spawn(level, new Vec3(corpse.x, standing.getY(), corpse.z), player, HerobrineApparitionEntity.Mode.FLASH, 22);
        level.playSound(null, corpse.x, corpse.y, corpse.z, DFTMSounds.STING, SoundSource.HOSTILE, 1.2F, 1.0F);
        return true;
    }

    /** Herobrine stands next to the bed, and the player wakes up. */
    public static boolean bedScare(ServerPlayer player, BlockPos bedHead)
    {
        ServerLevel level = player.level();
        BlockState bed = level.getBlockState(bedHead);
        if (!(bed.getBlock() instanceof BedBlock)) {return false;}
        BlockPos spot = findBedside(level, bedHead, bed.getValue(BedBlock.FACING));
        if (spot == null) {return false;}
        HerobrineApparitionEntity apparition = HerobrineApparitionEntity.spawn(level, Vec3.atBottomCenterOf(spot), player, HerobrineApparitionEntity.Mode.FLASH, 30);
        apparition.faceTowards(player);
        player.stopSleepInBed(true, true);
        level.playSound(null, spot, DFTMSounds.STING, SoundSource.HOSTILE, 1.5F, 0.9F);
        player.sendOverlayMessage(Component.translatable("message.dontfollowthemap.bed_scare"));
        return true;
    }

    @Nullable private static BlockPos findBedside(ServerLevel level, BlockPos head, Direction facing)
    {
        BlockPos foot = head.relative(facing.getOpposite());
        //by the pillow first
        List<BlockPos> candidates = List.of(head.relative(facing.getClockWise()), head.relative(facing.getCounterClockWise()), head.relative(facing),
                foot.relative(facing.getClockWise()), foot.relative(facing.getCounterClockWise()), foot.relative(facing.getOpposite()));
        for (BlockPos candidate : candidates)
        {
            for (int dy = 0; dy >= -1; dy--) {if (HauntSpots.isStandable(level, candidate.above(dy))) {return candidate.above(dy);}}
        }
        return null;
    }
}
