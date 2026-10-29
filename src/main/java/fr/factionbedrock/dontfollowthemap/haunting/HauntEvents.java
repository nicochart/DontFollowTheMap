package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import fr.factionbedrock.dontfollowthemap.util.SignWriter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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

    /** Right behind the player, waiting for them to turn around, while their fog slowly thickens (client/StalkFog). */
    public static boolean stalk(ServerPlayer player)
    {
        if (hasActiveApparition(player)) {return false;}
        Vec3 spot = HauntSpots.findSightingSpot(player, 13.0D, 18.0D, 140.0D, 180.0D);
        if (spot == null) {return false;}
        HerobrineApparitionEntity.spawn(player.level(), spot, player, HerobrineApparitionEntity.Mode.WATCH, 20 * 40).setStalking(player);
        HauntingData.get(player).sightings++;
        return true;
    }

    public static boolean hasActiveApparition(ServerPlayer player)
    {
        return !player.level().getEntitiesOfClass(HerobrineApparitionEntity.class, player.getBoundingBox().inflate(96.0D), apparition -> apparition.getTargetPlayer() == player).isEmpty();
    }

    //sounds only the player hears
    /** What a whisper can be: always behind the player, and heard by them only. */
    public enum Whisper {FOOTSTEPS, CREEPER, ZOMBIE, MINING, BROKEN_TOOL}

    public static boolean whisper(ServerPlayer player)
    {
        Whisper[] whispers = Whisper.values();
        return whisper(player, whispers[player.getRandom().nextInt(whispers.length)]);
    }

    public static boolean whisper(ServerPlayer player, Whisper whisper)
    {
        RandomSource random = player.getRandom();
        Vec3 backwards = Vec3.directionFromRotation(0.0F, player.getYRot()).reverse();
        Vec3 behind = player.position().add(backwards.scale(3.0D));
        switch (whisper)
        {
            case FOOTSTEPS ->
            {
                //on the block behind the player
                SoundEvent step = player.level().getBlockState(BlockPos.containing(behind).below()).getSoundType().getStepSound();
                for (int i = 0; i < 4; i++)
                {
                    Vec3 stepPos = behind.add(backwards.scale(i * 0.8D));
                    DelayedTasks.schedule(1 + i * 7, () -> playSoundTo(player, step, stepPos, 0.25F, 0.9F));
                }
            }
            //a creeper about to explode, right behind
            case CREEPER -> playSoundTo(player, SoundEvents.CREEPER_PRIMED, player.position().add(backwards.scale(2.0D)).add(0.0D, 1.0D, 0.0D), 1.0F, 0.5F);
            case ZOMBIE ->
            {
                //something is hitting a zombie, a bit further
                Vec3 fight = player.position().add(backwards.scale(Mth.nextDouble(random, 6.0D, 10.0D))).add(0.0D, 1.5D, 0.0D);
                int delay = 1;
                for (int hits = Mth.nextInt(random, 2, 3); hits > 0; hits--)
                {
                    float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
                    DelayedTasks.schedule(delay, () -> playSoundTo(player, SoundEvents.ZOMBIE_HURT, fight, 1.0F, pitch));
                    delay += Mth.nextInt(random, 8, 14);
                }
            }
            case MINING ->
            {
                //someone digging through stone or dirt: the blows of the tool, then the block breaking (vanilla volumes and pitches)
                SoundType block = (random.nextBoolean() ? Blocks.STONE : Blocks.DIRT).defaultBlockState().getSoundType();
                Vec3 digging = player.position().add(backwards.scale(4.0D)).add(0.0D, 0.5D, 0.0D);
                int blows = Mth.nextInt(random, 4, 7);
                for (int blow = 0; blow < blows; blow++) {DelayedTasks.schedule(1 + blow * 4, () -> playSoundTo(player, block.getHitSound(), digging, (block.getVolume() + 1.0F) / 8.0F, block.getPitch() * 0.5F));}
                DelayedTasks.schedule(1 + blows * 4, () -> playSoundTo(player, block.getBreakSound(), digging, (block.getVolume() + 1.0F) / 2.0F, block.getPitch() * 0.8F));
            }
            case BROKEN_TOOL -> playSoundTo(player, SoundEvents.ITEM_BREAK.value(), behind.add(0.0D, 1.2D, 0.0D), 0.8F, 0.8F + random.nextFloat() * 0.4F);
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
        List<BlockPos> torches = HauntSpots.findBlocks(level, player.blockPosition(), 18, 8, state -> state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH));
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

    //possessed animals
    /**
     * Herobrine takes over one of the animals of these kinds around the player, picked at random: the more of a kind, the likelier.
     * Returns the possessed animal, or null if there was none to take.
     */
    @Nullable public static Animal possessAnimal(ServerPlayer player, Collection<PossessableAnimals.Kind<?>> kinds)
    {
        if (kinds.isEmpty()) {return null;}
        List<Animal> animals = player.level().getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(32.0D, 12.0D, 32.0D), animal -> PossessableAnimals.canBePossessed(animal, kinds));
        if (animals.isEmpty()) {return null;}
        return PossessableAnimals.possess(animals.get(player.getRandom().nextInt(animals.size())), player);
    }

    //imitator
    /** Herobrine passes for a player in a lived-in place around the player: next to workstations, in chunks where people spent time. */
    public static boolean imitator(ServerPlayer player)
    {
        ServerLevel level = player.level();
        if (!level.getEntitiesOfClass(ImitatorEntity.class, player.getBoundingBox().inflate(128.0D), imitator -> imitator.isImitatingFor(player)).isEmpty()) {return false;}
        List<BlockPos> workstations = HauntSpots.findBlocks(level, player.blockPosition(), 32, 8, state -> state.is(ImitatorEntity.IMITATED_BLOCKS));
        workstations.removeIf(pos -> level.getChunkAt(pos).getInhabitedTime() < HauntSpots.MIN_HOME_INHABITED_TIME);
        Util.shuffle(workstations, player.getRandom());
        for (BlockPos workstation : workstations)
        {
            BlockPos spot = findImitatorSpot(level, player, workstation);
            if (spot == null) {continue;}
            //it will use everything around there
            ImitatorEntity.spawn(level, spot, player, workstations.stream().filter(pos -> pos.closerThan(workstation, 12.0D)).toList());
            return true;
        }
        return false;
    }

    /** Next to the workstation, out of the player's sight and not right next to them. */
    @Nullable private static BlockPos findImitatorSpot(ServerLevel level, ServerPlayer player, BlockPos workstation)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            //beside it, or below it if it stands on something
            for (int dy = 0; dy >= -1; dy--)
            {
                BlockPos feet = workstation.relative(side).above(dy);
                Vec3 eyes = Vec3.atBottomCenterOf(feet).add(0.0D, 1.62D, 0.0D);
                if (HauntSpots.isStandable(level, feet) && player.distanceToSqr(eyes) > 8.0D * 8.0D && HauntSpots.isHiddenFrom(player, eyes)) {return feet;}
            }
        }
        return null;
    }

    //tunnels
    /** How long he waits behind his wall before giving up. */
    public static final int LURK_TICKS = 20 * 20;
    private static final int WALL_MIN_DISTANCE = 3, WALL_MAX_DISTANCE = 6;
    /** Between the bottom block and the top one: two distinct blows. */
    private static final int WALL_BLOCK_INTERVAL = 8;

    /**
     * The player digs a straight 2x1 tunnel (forward: where they dig, the wall goes up behind them): two blocks are put back a few blocks behind them, loudly,
     * and Herobrine crouches right behind this wall, his face behind its top block (see {@link HerobrineApparitionEntity.Mode#LURK} for what happens next).
     */
    public static boolean wallUpTunnel(ServerPlayer player, Direction forward)
    {
        if (hasActiveApparition(player)) {return false;}
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        Direction back = forward.getOpposite();
        for (int distance = 1; distance <= WALL_MAX_DISTANCE; distance++)
        {
            BlockPos wall = feet.relative(back, distance);
            //the tunnel stops there
            if (!HauntSpots.isFree(level, wall) || !HauntSpots.isFree(level, wall.above())) {return false;}
            if (distance < WALL_MIN_DISTANCE || !canWallUp(level, player, wall, forward)) {continue;}
            BlockState rock = getWallState(wall);
            putBack(level, wall, rock);
            DelayedTasks.schedule(WALL_BLOCK_INTERVAL, () ->
            {
                if (!level.getBlockState(wall).equals(rock) || !level.getBlockState(wall.above()).isAir()) {return;}
                putBack(level, wall.above(), rock);
                HerobrineApparitionEntity.lurk(level, wall.relative(back), player, wall, LURK_TICKS);
            });
            return true;
        }
        return false;
    }

    /** A wall here closes the tunnel off (rock all around a 1x2 hole), with room behind it for him, out of everyone's sight. */
    private static boolean canWallUp(ServerLevel level, ServerPlayer player, BlockPos wall, Direction forward)
    {
        BlockPos top = wall.above(), lurk = wall.relative(forward.getOpposite());
        if (!level.getBlockState(wall).isAir() || !level.getBlockState(top).isAir() || !HauntSpots.isStandable(level, lurk)) {return false;}
        Direction left = forward.getCounterClockWise(), right = forward.getClockWise();
        for (BlockPos around : List.of(wall.below(), top.above(), wall.relative(left), wall.relative(right), top.relative(left), top.relative(right)))
        {
            if (!level.getBlockState(around).isCollisionShapeFullBlock(level, around)) {return false;}
        }
        if (!HauntSpots.isHiddenFrom(player, Vec3.atCenterOf(top)) || !level.getEntitiesOfClass(LivingEntity.class, AABB.encapsulatingFullBlocks(wall, lurk.above())).isEmpty()) {return false;}
        //nobody on the other side of the wall who would see him from behind
        Vec3 center = Vec3.atCenterOf(wall), eyes = Vec3.atBottomCenterOf(lurk).add(0.0D, 1.27D, 0.0D);
        return level.getPlayers(other -> other != player && !other.isSpectator() && other.distanceToSqr(center) < 32.0D * 32.0D
                && other.position().subtract(center).dot(forward.getUnitVec3()) < 0.0D && HauntSpots.canSee(level, other.getEyePosition(), eyes)).isEmpty();
    }

    private static BlockState getWallState(BlockPos wall)
    {
        return (wall.getY() < 0 ? Blocks.CHISELED_DEEPSLATE : Blocks.COBBLESTONE).defaultBlockState();
    }

    /** Puts a block back, loudly: heard from twice as far as a block put down by a player. */
    private static void putBack(ServerLevel level, BlockPos pos, BlockState rock)
    {
        level.setBlock(pos, rock, Block.UPDATE_ALL);
        SoundType sound = rock.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume() + 1.0F, sound.getPitch() * 0.8F);
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
        level.playSound(null, corpse.x, corpse.y, corpse.z, DFTMSounds.HEROBRINE_APPEAR, SoundSource.HOSTILE, 1.0F, 1.0F);
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
        level.playSound(null, spot, DFTMSounds.HEROBRINE_APPEAR, SoundSource.HOSTILE, 1.0F, 1.0F);
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
