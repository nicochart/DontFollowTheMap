package fr.factionbedrock.dontfollowthemap.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.block.SealBlock;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedAnimal;
import fr.factionbedrock.dontfollowthemap.entity.possessed.Possession;
import fr.factionbedrock.dontfollowthemap.haunting.GraveDigger;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingDirector;
import fr.factionbedrock.dontfollowthemap.haunting.Reaction;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.haunting.TorchSwaps;
import fr.factionbedrock.dontfollowthemap.haunting.TunnelWatcher;
import fr.factionbedrock.dontfollowthemap.haunting.WorldPeace;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMItems;
import fr.factionbedrock.dontfollowthemap.util.Facing;
import fr.factionbedrock.dontfollowthemap.util.Players;
import fr.factionbedrock.dontfollowthemap.util.SignWoods;
import fr.factionbedrock.dontfollowthemap.world.SealPiece;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowSoundVariants;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariants;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Server game tests: ./gradlew runGameTest */
public class DFTMGameTests
{
    /**
     * Tests with players in survival run on their own (data/dontfollowthemap/test_environment): the Herobrine of any other test would fight them.
     * The ones where Herobrine fights survival players each run alone: he would draw the players of the other ones into his fight.
     * SURVIVAL_STILL: survival players, and no random ticks (lava sets nothing on fire, its light melts no snow).
     */
    private static final String SURVIVAL_PLAYERS = "dontfollowthemap:survival_players", HEROBRINE_FIGHT = "dontfollowthemap:herobrine_fight",
            HEROBRINE_SHIELD = "dontfollowthemap:herobrine_shield", HEROBRINE_WALLS = "dontfollowthemap:herobrine_walls",
            SURVIVAL_STILL = "dontfollowthemap:survival_no_random_ticks";

    //progression and saving
    @GameTest
    public void hauntingDataProgressesAndSurvivesSaving(GameTestHelper helper)
    {
        HauntingData data = new HauntingData();
        helper.assertTrue(data.stage(0L) == HauntingData.QUIET, "A new player must not be haunted");
        data.setStage(HauntingData.HAUNTED, 0L);
        helper.assertTrue(data.stage(0L) == HauntingData.HAUNTED, "setStage(HAUNTED) must give the haunted stage");
        data.mapDeliveredAt = 1234L;
        data.startCooldown(Reaction.GRAVE, 1000L);
        data.addStolen(new ItemStack(Items.DIAMOND, 3));
        data.addStolen(new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(data.stolen.size() == 1 && data.stolen.get(0).getCount() == 5, "Stolen items must stack");
        data.removeStolen(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(data.stolen.get(0).getCount() == 1, "Items taken back must be removed from the stolen ones");

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        Tag saved = HauntingData.CODEC.encodeStart(ops, data).getOrThrow();
        HauntingData loaded = HauntingData.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(loaded.hauntTicks == data.hauntTicks && loaded.mapDeliveredAt == 1234L && loaded.stolen.size() == 1 && loaded.stolen.get(0).is(Items.DIAMOND), "Haunting data must survive a save");
        helper.assertTrue(loaded.isOnCooldown(Reaction.GRAVE, 1001L) && !loaded.isOnCooldown(Reaction.GRAVE, 1000L + Reaction.GRAVE.cooldown()) && !loaded.isOnCooldown(Reaction.THEFT, 1001L),
                "When each reaction last happened must survive a save, and its cooldown end in time");

        data.onHerobrineDefeated(1000L);
        helper.assertTrue(data.stage(1000L) == HauntingData.QUIET && !data.hasMapBeenDelivered() && data.defeats == 1, "Defeating Herobrine must bring peace");
        helper.succeed();
    }

    //apparitions
    @GameTest(maxTicks = 80, skyAccess = true, padding = 14)
    public void apparitionWatchesFromAfarAndVanishesWhenApproached(GameTestHelper helper)
    {
        floor(helper, -2, 9, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, -12, 180.0F);
        HerobrineApparitionEntity apparition = HerobrineApparitionEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(4.5D, 1.0D, 4.5D)), player, HerobrineApparitionEntity.Mode.WATCH, 20 * 90);
        helper.runAfterDelay(20, () ->
        {
            helper.assertTrue(apparition.isAlive(), "The apparition must stay while the player is far and doesn't look");
            teleport(helper, player, 4, 1, 1, 180.0F);
        });
        helper.runAfterDelay(30, () ->
        {
            helper.assertTrue(apparition.isRemoved(), "The apparition must vanish when the player comes close");
            removePlayer(player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 80, skyAccess = true, padding = 14)
    public void apparitionVanishesWhenAnybodyComesClose(GameTestHelper helper)
    {
        floor(helper, -2, 9, Blocks.STONE);
        //it watches one player, another one (who doesn't look at it) walks up to it
        ServerPlayer watched = player(helper, 4, 1, -12, 180.0F), other = player(helper, 4, 1, 20, 0.0F);
        HerobrineApparitionEntity apparition = HerobrineApparitionEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(4.5D, 1.0D, 4.5D)), watched, HerobrineApparitionEntity.Mode.WATCH, 20 * 90);
        helper.runAfterDelay(20, () ->
        {
            helper.assertTrue(apparition.isAlive(), "The apparition must stay while everybody is far and doesn't look");
            teleport(helper, other, 4, 1, 10, 0.0F);
        });
        helper.runAfterDelay(30, () ->
        {
            helper.assertTrue(apparition.isRemoved(), "The apparition must vanish when anybody comes close, not only the one it watches");
            helper.assertTrue(hasAdvancement(other, "watched") && !hasAdvancement(watched, "watched"), "The one who came close saw it, not the one it watched");
            List.of(watched, other).forEach(DFTMGameTests::removePlayer);
            helper.succeed();
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 16)
    public void stalkerCreepsUpStrikesAndTakesThreeBlows(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -8, 15, Blocks.STONE);
        removeBarriers(helper);
        //facing south, the stalker right behind them
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 0.0F);
        HerobrineApparitionEntity stalker = HerobrineApparitionEntity.spawn(level, helper.absoluteVec(new Vec3(4.5D, 1.0D, -3.5D)), player, HerobrineApparitionEntity.Mode.STALK, HerobrineApparitionEntity.STALK_LIFETIME);
        stalker.setFogging(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth() && player.hasEffect(MobEffects.DARKNESS), "Creeping up on the player while their back is turned, it must strike them, with the darkness"))
                .thenExecute(() -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4)))
                .thenWaitUntil(() -> helper.assertTrue(stalker.distanceTo(player) > 5.0D && HauntSpots.isHiddenFrom(player, stalker.getEyePosition()),"Once it struck, it must be gone behind them"))
                .thenExecute(() -> blowAndCheck(helper, stalker, player, 1))
                .thenIdle(12)
                .thenExecute(() -> blowAndCheck(helper, stalker, player, 2))
                .thenIdle(12)
                .thenExecute(() ->
                {
                    stalker.hurtServer(level, level.damageSources().playerAttack(player), 5.0F);
                    helper.assertTrue(stalker.isRemoved(), "Only the third blow must make it go away");
                    removePlayer(player);
                })
                .thenSucceed();
    }

    private static void blowAndCheck(GameTestHelper helper, HerobrineApparitionEntity stalker, ServerPlayer player, int hits)
    {
        stalker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
        helper.assertTrue(stalker.isAlive() && stalker.getHits() == hits, "Blow " + hits + " must send it away for a moment, not for good");
    }

    @GameTest(maxTicks = 140, skyAccess = true, padding = 14)
    public void apparitionVanishesWhenAnybodyStaresAtIt(GameTestHelper helper)
    {
        floor(helper, -2, 9, Blocks.STONE);
        removeBarriers(helper);
        //it watches one player, another one stares at it from afar
        ServerPlayer watched = player(helper, 4, 1, -12, 180.0F), other = player(helper, 4, 1, 24, 180.0F);
        HerobrineApparitionEntity apparition = HerobrineApparitionEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(4.5D, 1.0D, 4.5D)), watched, HerobrineApparitionEntity.Mode.WATCH, 20 * 90);
        helper.succeedWhen(() ->
        {
            helper.assertTrue(apparition.isRemoved(), "The apparition must vanish when anybody stares at it long enough");
            helper.assertTrue(hasAdvancement(other, "watched") && !hasAdvancement(watched, "watched"), "The one who stared at it saw it, not the one it watched");
            List.of(watched, other).forEach(DFTMGameTests::removePlayer);
        });
    }

    @GameTest(maxTicks = 60)
    public void killApparitionFlashesAndVanishes(GameTestHelper helper)
    {
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = player(helper, 1, 1, 1, 0.0F);
        helper.assertTrue(HauntEvents.killApparition(player, helper.absoluteVec(new Vec3(5.5D, 1.0D, 5.5D))), "The kill apparition must find room");
        helper.assertEntityPresent(DFTMEntities.APPARITION);
        helper.succeedWhen(() ->
        {
            helper.assertEntityNotPresent(DFTMEntities.APPARITION);
            removePlayer(player);
        });
    }

    @GameTest(maxTicks = 60)
    public void bedScareShowsHimNextToTheBed(GameTestHelper helper)
    {
        floor(helper, 0, 7, Blocks.STONE);
        BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        helper.setBlock(new BlockPos(3, 1, 3), bed.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(3, 1, 4), bed.setValue(BedBlock.PART, BedPart.HEAD));
        ServerPlayer player = player(helper, 3, 1, 4, 0.0F);
        helper.assertTrue(HauntEvents.bedScare(player, helper.absolutePos(new BlockPos(3, 1, 4))), "Herobrine must find room next to the bed");
        helper.assertEntityPresent(DFTMEntities.APPARITION, new BlockPos(3, 1, 4), 2.0D);
        helper.succeedWhen(() ->
        {
            helper.assertEntityNotPresent(DFTMEntities.APPARITION);
            removePlayer(player);
        });
    }

    //whispers
    @GameTest(maxTicks = 20)
    public void whisperDelaysDependOnTheStageAndAreLongerByDay(GameTestHelper helper)
    {
        RandomSource random = helper.getLevel().getRandom();
        double frequency = DFTMConfig.get().eventFrequency;
        for (int i = 0; i < 200; i++)
        {
            assertBetween(helper, HauntingDirector.whisperDelay(HauntingData.WATCHED, false, random), 5, 10, frequency, "watched, by night");
            assertBetween(helper, HauntingDirector.whisperDelay(HauntingData.WATCHED, true, random), 10, 20, frequency, "watched, by day");
            assertBetween(helper, HauntingDirector.whisperDelay(HauntingData.HAUNTED, false, random), 3, 7, frequency, "haunted, by night");
            assertBetween(helper, HauntingDirector.whisperDelay(HauntingData.HUNTED, true, random), 4, 10, frequency, "hunted, by day");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void whispersHaveTheirOwnClock(GameTestHelper helper)
    {
        //mock players always count as creative, which the haunting leaves alone: the clock is run by hand
        ServerPlayer player = player(helper, 4, 1, 4, 0.0F);
        long now = HauntingDirector.now(helper.getLevel().getServer());
        HauntingData data = HauntingData.get(player);
        data.setStage(HauntingData.WATCHED, now);
        double frequency = DFTMConfig.get().eventFrequency;
        HauntingDirector.updateWhispers(player, data, HauntingData.WATCHED, now);
        assertBetween(helper, data.nextWhisperTime - now, 5, 20, frequency, "the first whisper");
        //due, while no other event is
        long planned = data.nextWhisperTime;
        HauntingDirector.updateWhispers(player, data, HauntingData.WATCHED, planned - 1L);
        helper.assertTrue(data.nextWhisperTime == planned, "No whisper before its time");
        HauntingDirector.updateWhispers(player, data, HauntingData.WATCHED, planned);
        assertBetween(helper, data.nextWhisperTime - planned, 5, 20, frequency, "the next whisper");
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 80)
    public void everyWhisperCanBePlayed(GameTestHelper helper)
    {
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 4, 0.0F);
        for (HauntEvents.Whisper whisper : HauntEvents.Whisper.values()) {helper.assertTrue(HauntEvents.whisper(player, whisper), "The whisper " + whisper + " must play");}
        //the sounds that come one after the other are played later
        helper.runAfterDelay(60, () ->
        {
            removePlayer(player);
            helper.succeed();
        });
    }

    private static void assertBetween(GameTestHelper helper, long ticks, int minMinutes, int maxMinutes, double frequency, String what)
    {
        helper.assertTrue(ticks >= (long) (minMinutes * 1200 / frequency) - 1 && ticks <= (long) (maxMinutes * 1200 / frequency) + 40, "Wrong delay for " + what + ": " + ticks + " ticks");
    }

    //torches
    @GameTest(maxTicks = 20)
    public void torchesTurnRedOutOfSightAndComeBack(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, 0, 7, Blocks.STONE);
        for (int x = 0; x < 8; x++) {helper.setBlock(x, 1, 7, Blocks.STONE); helper.setBlock(x, 2, 7, Blocks.STONE);}
        BlockPos wallTorch = new BlockPos(2, 2, 6), torch = new BlockPos(4, 1, 5), nextToTnt = new BlockPos(6, 1, 4);
        helper.setBlock(wallTorch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        helper.setBlock(torch, Blocks.TORCH);
        helper.setBlock(nextToTnt, Blocks.TORCH);
        helper.setBlock(7, 1, 4, Blocks.TNT);
        //the player looks the other way
        ServerPlayer player = player(helper, 3, 1, 0, 180.0F);

        helper.assertTrue(HauntEvents.swapTorches(player, 10), "Torches out of sight must be swapped");
        helper.assertBlockPresent(Blocks.REDSTONE_WALL_TORCH, wallTorch);
        helper.assertTrue(level.getBlockState(helper.absolutePos(wallTorch)).getValue(WallTorchBlock.FACING) == Direction.NORTH, "The wall torch must keep its facing");
        helper.assertBlockPresent(Blocks.REDSTONE_TORCH, torch);
        helper.assertBlockPresent(Blocks.TORCH, nextToTnt);

        TorchSwaps.get(level).tick(level, Long.MAX_VALUE);
        helper.assertBlockPresent(Blocks.WALL_TORCH, wallTorch);
        helper.assertTrue(level.getBlockState(helper.absolutePos(wallTorch)).getValue(WallTorchBlock.FACING) == Direction.NORTH, "The wall torch must come back with its facing");
        helper.assertBlockPresent(Blocks.TORCH, torch);
        helper.assertBlockPresent(Blocks.TNT, new BlockPos(7, 1, 4));
        removePlayer(player);
        helper.succeed();
    }

    //home
    @GameTest(maxTicks = 20, skyAccess = true, padding = 12)
    public void signIsLeftNearHome(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -6, 13, Blocks.STONE);
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        ServerPlayer player = player(helper, 4, 1, -12, 180.0F);
        makeHome(level, player, home);
        helper.assertTrue(HauntEvents.placeSign(player), "A sign must be placed near home");
        boolean found = BlockPos.betweenClosedStream(home.offset(-9, -3, -9), home.offset(9, 3, 9)).anyMatch(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.isWaxed());
        helper.assertTrue(found, "The sign must be waxed (not editable)");
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = 28)
    public void buildAppearsNearHome(GameTestHelper helper)
    {
        floor(helper, -22, 29, Blocks.GRASS_BLOCK);
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        ServerPlayer player = player(helper, 4, 1, -40, 180.0F);
        makeHome(helper.getLevel(), player, home);
        helper.assertTrue(HauntEvents.build(player), "Something must be built near home");
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = 14)
    public void graveIsDugInNaturalGround(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        for (int x = -11; x <= 18; x++) {for (int z = -11; z <= 18; z++)
        {
            for (int y = -4; y < 0; y++) {helper.setBlock(x, y, z, Blocks.DIRT);}
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        }}
        ServerPlayer player = player(helper, 4, 1, 4, 0.0F);
        BlockPos grave = GraveDigger.dig(level, helper.absolutePos(new BlockPos(4, 1, 4)), player);
        helper.assertTrue(grave != null, "A grave must be dug in plain dirt");
        helper.assertTrue(level.getBlockState(grave).isAir() && level.getBlockState(grave.above()).isAir(), "The grave must have room for the player");
        helper.assertTrue(level.getBlockState(grave.above(2)).is(Blocks.GRASS_BLOCK), "The grave must stay closed by the untouched ground");
        helper.assertTrue(level.getBlockState(grave.above(3)).getBlock() instanceof WallSignBlock, "The grave must have a headstone with a sign");
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void signsAreMadeOfTheLocalWood(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        RandomSource random = RandomSource.create(1L);
        Map<ResourceKey<Biome>, SignWoods.Wood> woods = Map.ofEntries(Map.entry(Biomes.TAIGA, SignWoods.SPRUCE), Map.entry(Biomes.SNOWY_PLAINS, SignWoods.SPRUCE),
                Map.entry(Biomes.CHERRY_GROVE, SignWoods.CHERRY), Map.entry(Biomes.SAVANNA, SignWoods.ACACIA), Map.entry(Biomes.JUNGLE, SignWoods.JUNGLE),
                Map.entry(Biomes.DARK_FOREST, SignWoods.DARK_OAK), Map.entry(Biomes.PALE_GARDEN, SignWoods.PALE_OAK), Map.entry(Biomes.MANGROVE_SWAMP, SignWoods.MANGROVE),
                Map.entry(Biomes.DAPPLED_FOREST, SignWoods.POPLAR), Map.entry(Biomes.BIRCH_FOREST, SignWoods.BIRCH), Map.entry(Biomes.DESERT, SignWoods.OAK));
        woods.forEach((biome, wood) -> helper.assertTrue(SignWoods.localWood(biomes.getOrThrow(biome), random) == wood, "The signs of " + biome.identifier() + " must be made of the wood that grows there"));
        //once in a while, a wood from elsewhere
        Set<Block> signs = new HashSet<>();
        for (int i = 0; i < 100; i++) {signs.add(SignWoods.localSign(level, helper.absolutePos(BlockPos.ZERO), random, false));}
        helper.assertTrue(signs.size() > 3, "The signs must not all be made of the same wood");
        helper.succeed();
    }

    //possessed animals
    @GameTest(maxTicks = 800, skyAccess = true, padding = 16)
    public void possessedSheepStaresBitesAndLetsGo(GameTestHelper helper)
    {
        possessionStaresStrikesAndLetsGo(helper, PossessableAnimals.SHEEP, sheep -> sheep.setColor(DyeColor.RED), sheep -> sheep.getColor() == DyeColor.RED);
    }

    @GameTest(maxTicks = 800, skyAccess = true, padding = 16)
    public void possessedCowKeepsItsVariantAndLetsGo(GameTestHelper helper)
    {
        possessionStaresStrikesAndLetsGo(helper, PossessableAnimals.COW, cow ->
        {
            setVariant(helper, cow, DataComponents.COW_VARIANT, CowVariants.WARM);
            setVariant(helper, cow, DataComponents.COW_SOUND_VARIANT, CowSoundVariants.MOODY);
        }, cow -> hasVariant(cow, DataComponents.COW_VARIANT, CowVariants.WARM) && hasVariant(cow, DataComponents.COW_SOUND_VARIANT, CowSoundVariants.MOODY));
    }

    @GameTest(maxTicks = 800, skyAccess = true, padding = 16)
    public void possessedPigKeepsItsSaddleAndLetsGo(GameTestHelper helper)
    {
        possessionStaresStrikesAndLetsGo(helper, PossessableAnimals.PIG, pig ->
        {
            setVariant(helper, pig, DataComponents.PIG_VARIANT, PigVariants.COLD);
            pig.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        }, pig -> hasVariant(pig, DataComponents.PIG_VARIANT, PigVariants.COLD) && pig.isSaddled());
    }

    @GameTest(maxTicks = 800, skyAccess = true, padding = 16)
    public void possessedChickenKeepsItsVariantAndLetsGo(GameTestHelper helper)
    {
        possessionStaresStrikesAndLetsGo(helper, PossessableAnimals.CHICKEN, chicken -> setVariant(helper, chicken, DataComponents.CHICKEN_VARIANT, ChickenVariants.WARM),
                chicken -> hasVariant(chicken, DataComponents.CHICKEN_VARIANT, ChickenVariants.WARM));
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 10)
    public void possessedAnimalBitesHardButArmorHelps(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Possession.biteDamage(0) == 14.0F && Math.abs(Possession.biteDamage(15) - 6.0F) < 0.01F, "A bite must take 7 hearts with no armor, 3 in full iron");
        floor(helper, -1, 8, Blocks.GRASS_BLOCK);
        //one player with no armor, one in full iron (mock players never tick: their armor would never count, their armor value is given right away)
        ServerPlayer bare = survivalPlayer(helper, 1, 1, 0, 0.0F), armored = survivalPlayer(helper, 6, 1, 0, 0.0F);
        armored.getAttribute(Attributes.ARMOR).setBaseValue(15.0D);
        List<Animal> possessed = List.of(PossessableAnimals.SHEEP.possess(helper.spawn(EntityTypes.SHEEP, new BlockPos(1, 1, 5)), bare),
                PossessableAnimals.SHEEP.possess(helper.spawn(EntityTypes.SHEEP, new BlockPos(6, 1, 5)), armored));
        //close enough for them to charge
        teleport(helper, bare, 1, 1, 3, 0.0F);
        teleport(helper, armored, 6, 1, 3, 0.0F);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bare.getHealth() < bare.getMaxHealth() && armored.getHealth() < armored.getMaxHealth(), "Both possessed sheep must bite"))
                .thenExecute(() ->
                {
                    float bareDamage = bare.getMaxHealth() - bare.getHealth(), armoredDamage = armored.getMaxHealth() - armored.getHealth();
                    helper.assertTrue(Math.abs(bareDamage - scaledByDifficulty(level, 14.0F)) < 0.01F, "With no armor, a bite must take 7 hearts (on normal), not " + bareDamage / 2.0F);
                    helper.assertTrue(Math.abs(armoredDamage - scaledByDifficulty(level, Possession.biteDamage(15))) < 0.01F, "In full iron, a bite must take 3 hearts (on normal), not " + armoredDamage / 2.0F);
                    possessed.forEach(Entity::discard);
                    level.getEntitiesOfClass(Sheep.class, helper.getBounds().inflate(16.0D)).forEach(Entity::discard);
                    List.of(bare, armored).forEach(DFTMGameTests::removePlayer);
                })
                .thenSucceed();
    }

    /** What a mob's blow takes from a player at the world's difficulty. */
    private static float scaledByDifficulty(ServerLevel level, float damage)
    {
        return switch (level.getDifficulty())
        {
            case PEACEFUL -> 0.0F;
            case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5F;
        };
    }

    @GameTest(maxTicks = 20)
    public void onlyFreeAdultsOfTheAllowedKindsCanBePossessed(GameTestHelper helper)
    {
        floor(helper, 0, 7, Blocks.STONE);
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
        Cow calf = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 1));
        calf.setBaby(true);
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 1, 1));
        Pig pigWithoutAi = helper.spawn(EntityTypes.PIG, new BlockPos(1, 1, 4));
        pigWithoutAi.setNoAi(true);
        Chicken jockey = helper.spawn(EntityTypes.CHICKEN, new BlockPos(4, 1, 4));
        jockey.setChickenJockey(true);
        Animal alreadyPossessed = helper.spawn(DFTMEntities.POSSESSED_COW, new BlockPos(6, 1, 6));

        List<PossessableAnimals.Kind<?>> cowsOnly = List.of(PossessableAnimals.COW);
        helper.assertTrue(PossessableAnimals.canBePossessed(cow, cowsOnly), "An adult cow can be possessed");
        helper.assertFalse(PossessableAnimals.canBePossessed(calf, cowsOnly), "A calf can't be possessed");
        helper.assertFalse(PossessableAnimals.canBePossessed(sheep, cowsOnly), "A sheep can't be possessed when only cows are allowed");
        helper.assertTrue(PossessableAnimals.canBePossessed(sheep, PossessableAnimals.ALL), "A sheep can be possessed when every animal is allowed");
        helper.assertFalse(PossessableAnimals.canBePossessed(pigWithoutAi, PossessableAnimals.ALL), "An animal without AI can't be possessed");
        helper.assertFalse(PossessableAnimals.canBePossessed(jockey, PossessableAnimals.ALL), "A chicken jockey can't be possessed");
        helper.assertFalse(PossessableAnimals.canBePossessed(alreadyPossessed, PossessableAnimals.ALL), "A possessed animal can't be possessed again");
        List.of(cow, calf, sheep, pigWithoutAi, jockey, alreadyPossessed).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void possessionCommandTakesAnOptionalAnimal(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (String command : List.of("dontfollowthemap trigger possession", "dontfollowthemap trigger possession minecraft:cow", "dontfollowthemap trigger possession chicken", "dontfollowthemap trigger possession minecraft:pig @p"))
        {
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
            helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && parsed.getContext().getCommand() != null, "/" + command + " must be a complete command");
        }
        //the animal is checked before anything happens
        try
        {
            dispatcher.execute(dispatcher.parse("dontfollowthemap trigger possession minecraft:zombie", source));
            helper.assertTrue(false, "A zombie must not be accepted");
        }
        catch (CommandSyntaxException e)
        {
            helper.assertTrue(e.getRawMessage() instanceof Component message && message.getContents() instanceof TranslatableContents text && text.getKey().equals("commands.dontfollowthemap.not_possessable"),
                    "The command must say that a zombie can't be possessed, not: " + e.getMessage());
        }
        helper.succeed();
    }

    /** The animal is possessed without any visible change, charges the player who comes next to it, runs away, and becomes the same plain animal again. */
    private static <A extends Animal> void possessionStaresStrikesAndLetsGo(GameTestHelper helper, PossessableAnimals.Kind<A> kind, Consumer<A> setUp, Predicate<A> looksTheSame)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -14, 21, Blocks.GRASS_BLOCK);
        ServerPlayer player = player(helper, 1, 1, 1, 0.0F);
        String name = kind.plainType().toShortString();
        A animal = helper.spawn(kind.plainType(), new BlockPos(5, 1, 5));
        setUp.accept(animal);
        animal.setHealth(animal.getMaxHealth() - 1.0F);
        Animal possessed = kind.possess(animal, player);
        helper.assertTrue(possessed instanceof PossessedAnimal && possessed.getType() == kind.possessedType() && animal.isRemoved(), "The " + name + " must be replaced by a possessed one");
        helper.assertTrue(looksTheSame.test(kind.animalClass().cast(possessed)) && possessed.getHealth() == animal.getHealth(), "The possessed " + name + " must look the same and keep its health");
        Possession possession = ((PossessedAnimal) possessed).getPossession();
        helper.assertTrue(possession.isStaring(), "The possessed " + name + " must start by staring");
        teleport(helper, player, 5, 1, 4, 0.0F);
        helper.runAfterDelay(40, () -> helper.assertTrue(possessed.isRemoved() || !possession.isStaring(), "The " + name + " must charge when the player comes close"));
        helper.succeedWhen(() ->
        {
            helper.assertTrue(possessed.isRemoved(), "The possession must end");
            List<A> released = level.getEntitiesOfClass(kind.animalClass(), helper.getBounds().inflate(24.0D), candidate -> candidate.getType() == kind.plainType() && looksTheSame.test(candidate));
            helper.assertTrue(released.size() == 1, "The same plain " + name + " must be left, found " + released.size());
            released.forEach(Entity::discard);
            removePlayer(player);
        });
    }

    private static <T> void setVariant(GameTestHelper helper, Animal animal, DataComponentType<Holder<T>> type, ResourceKey<T> variant)
    {
        animal.setComponent(type, helper.getLevel().registryAccess().getOrThrow(variant));
    }

    private static <T> boolean hasVariant(Animal animal, DataComponentType<Holder<T>> type, ResourceKey<T> variant)
    {
        Holder<T> value = animal.get(type);
        return value != null && value.is(variant);
    }

    @GameTest(maxTicks = 100, skyAccess = true, padding = 32)
    public void possessionBringsAnAnimalWhenNoneIsLeft(GameTestHelper helper)
    {
        //the test area floats high above the ground: a wide floor, for the cow to come from
        floor(helper, -26, 33, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 4, 0.0F);
        //the chunks around the player load first
        helper.runAfterDelay(40, () ->
        {
            Animal possessed = HauntEvents.possessAnimal(player, List.of(PossessableAnimals.COW));
            helper.assertTrue(possessed != null && possessed.getType() == DFTMEntities.POSSESSED_COW, "With no cow around, one must come, possessed");
            double distance = possessed.distanceTo(player);
            helper.assertTrue(distance >= 16.0D && distance <= 42.0D, "It must come from a little way off (" + (int) distance + " blocks)");
            helper.assertTrue(HauntSpots.isHiddenFrom(player, possessed.getEyePosition()), "It must come from where the player can't see");
            possessed.discard();
            removePlayer(player);
            helper.succeed();
        });
    }

    //thief
    @GameTest(maxTicks = 400)
    public void unseenTakesWhatFallsAroundThePlayerThenCanBeCaught(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 0, 180.0F);
        //the test area is walled with barriers: the thief comes from inside it
        ItemEntity diamond = helper.spawnItem(Items.DIAMOND, new BlockPos(6, 1, 6));
        UnseenEntity unseen = UnseenEntity.spawn(level, helper.absolutePos(new BlockPos(1, 1, 6)), player, List.of(diamond));
        unseen.setWanted(3);
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    helper.assertTrue(diamond.isRemoved(), "The diamond must be taken");
                    helper.assertTrue(HauntingData.get(player).stolen.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "The stolen diamond must be kept in the player's data");
                })
                .thenExecute(() ->
                {
                    helper.assertFalse(unseen.hurtServer(level, level.damageSources().playerAttack(player), 1.0F), "Nothing can touch it before it has what it came for");
                    helper.assertFalse(unseen.isPickable() || unseen.canBeHitByProjectile(), "Before it runs away, blows and arrows must go right through it (it must not keep the player from mining)");
                    helper.assertTrue(level.isUnobstructed(null, Shapes.create(unseen.getBoundingBox())), "It must never keep a block from being placed");
                    helper.assertTrue(unseen.isAlive() && !unseen.isFleeing(), "With one stack out of three, it must keep following the player");
                    //what falls next around the player
                    helper.spawnItem(Items.EMERALD, new BlockPos(6, 1, 2));
                    helper.spawnItem(Items.GOLD_INGOT, new BlockPos(2, 1, 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(unseen.isFleeing(), "With three stacks, it must run away"))
                .thenExecute(() ->
                {
                    helper.assertTrue(unseen.isPickable(), "Running away, it can be hit");
                    unseen.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
                    helper.assertTrue(unseen.isRemoved(), "Caught, the thief must vanish");
                    helper.assertTrue(HauntingData.get(player).stolen.isEmpty(), "Caught, it must give back everything it took");
                    AABB around = helper.getBounds().inflate(8.0D);
                    for (Item item : List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT))
                    {
                        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, around, entity -> entity.getItem().is(item));
                        helper.assertTrue(!dropped.isEmpty(), "Everything it took must be dropped back");
                        dropped.forEach(ItemEntity::discard);
                    }
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = 10)
    public void unseenComesForFreshDropsOnly(GameTestHelper helper)
    {
        floor(helper, -8, 15, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 0, 180.0F);
        helper.assertFalse(UnseenEntity.comeForDrops(player, helper.absolutePos(new BlockPos(4, 1, 6))), "Nothing to steal: no thief");
        helper.spawnItem(Items.DIAMOND, new BlockPos(4, 1, 6));
        helper.assertTrue(UnseenEntity.comeForDrops(player, helper.absolutePos(new BlockPos(4, 1, 6))), "The thief must come for fresh drops");
        //it comes from around the drops, possibly outside of the test area
        List<UnseenEntity> thieves = helper.getLevel().getEntitiesOfClass(UnseenEntity.class, helper.getBounds().inflate(10.0D));
        helper.assertTrue(thieves.size() == 1, "Exactly one thief must come");
        thieves.forEach(UnseenEntity::discard);
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 60, skyAccess = true, padding = 10)
    public void thiefCommandStealsFromTheNextMinedBlockOnly(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -8, 15, Blocks.STONE);
        //a player who isn't haunted yet: the command doesn't care about the stage, the chance or the cooldown
        ServerPlayer player = player(helper, 4, 1, 0, 180.0F);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos first = place(helper, 3, 1, 6, Blocks.DIRT), second = place(helper, 5, 1, 6, Blocks.DIRT);
        AABB around = new AABB(first).inflate(8.0D);
        runCommandAs(helper, player, "dontfollowthemap trigger thief");
        helper.assertTrue(player.gameMode.destroyBlock(first), "The player must break the first block");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getEntitiesOfClass(UnseenEntity.class, around).size() == 1, "The thief must come for the first block"))
                .thenExecute(() ->
                {
                    level.getEntitiesOfClass(UnseenEntity.class, around).forEach(UnseenEntity::discard);
                    helper.assertTrue(player.gameMode.destroyBlock(second), "The player must break the second block");
                })
                .thenIdle(5)
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getEntitiesOfClass(UnseenEntity.class, around).isEmpty(), "The command must only send the thief once");
                    level.getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(Items.DIRT)).forEach(ItemEntity::discard);
                    removePlayer(player);
                })
                .thenSucceed();
    }

    //imitator
    @GameTest(maxTicks = 20, skyAccess = true, padding = 10)
    public void imitatorAppearsNextToAWorkstationOfALivedInPlace(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -8, 15, Blocks.STONE);
        //the crafting table is behind the player
        ServerPlayer player = player(helper, 4, 1, -4, 180.0F);
        BlockPos table = place(helper, 4, 1, 6, Blocks.CRAFTING_TABLE);
        level.getChunkAt(table).setInhabitedTime(100000L);
        helper.assertTrue(HauntEvents.imitator(player), "An imitator must appear in a lived-in place");
        List<ImitatorEntity> imitators = level.getEntitiesOfClass(ImitatorEntity.class, new AABB(table).inflate(2.0D), imitator -> imitator.isImitatingFor(player));
        helper.assertTrue(imitators.size() == 1, "It must appear next to the crafting table");
        helper.assertFalse(HauntEvents.imitator(player), "Only one imitator at a time");
        imitators.forEach(ImitatorEntity::discard);
        removePlayer(player);
        helper.succeed();
    }

    @GameTest(maxTicks = 300, skyAccess = true, padding = 10)
    public void imitatorUsesAWorkstationThenShowsItsFaceWhenHit(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -8, 15, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 0, 180.0F);
        BlockPos barrel = place(helper, 4, 1, 6, Blocks.BARREL);
        //the player is in creative: it doesn't go for a sword
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(4, 1, 5)), player, List.of(barrel));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(barrel).getValue(BarrelBlock.OPEN), "The imitator must open the barrel"))
                .thenExecute(() ->
                {
                    //a blow that would kill anyone
                    helper.assertTrue(imitator.hurtServer(level, level.damageSources().playerAttack(player), 100.0F), "The first blow must hurt it");
                    helper.assertTrue(imitator.isAlive() && imitator.getHealth() == 1.0F, "The first blow must hurt it, without killing it");
                    helper.assertTrue(imitator.getDeltaMovement().horizontalDistanceSqr() > 0.0D, "The first blow must knock it back");
                    helper.assertTrue(imitator.isUnmasked(), "Hit, it must stop pretending");
                    helper.assertFalse(level.getBlockState(barrel).getValue(BarrelBlock.OPEN), "It must close the barrel it was using");
                    helper.assertFalse(imitator.hurtServer(level, level.damageSources().playerAttack(player), 5.0F) || imitator.getHealth() < 1.0F, "Once hit, nothing can hurt it any more");
                })
                .thenWaitUntil(() ->
                {
                    helper.assertTrue(imitator.isRemoved(), "It must be gone once it showed its face");
                    helper.assertTrue(!apparitionsAround(level, imitator, HerobrineApparitionEntity.Mode.STRIKE).isEmpty(), "Hit before it had its sword, Herobrine must come out of it to strike back");
                })
                .thenExecute(() ->
                {
                    apparitionsAround(level, imitator, HerobrineApparitionEntity.Mode.STRIKE).forEach(HerobrineApparitionEntity::discard);
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 10)
    public void imitatorMakesASwordWhenAPlayerComesCloseAndTakesThreeBlows(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -1, 8, Blocks.STONE);
        BlockPos table = place(helper, 6, 1, 4, Blocks.CRAFTING_TABLE);
        ServerPlayer player = survivalPlayer(helper, 1, 1, 0, 0.0F);
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(2, 1, 6)), player, List.of(table));
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() ->
                {
                    helper.assertFalse(imitator.isAfter(player), "A player farther than 5 blocks away must not be noticed");
                    teleport(helper, player, 2, 1, 3, 0.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(imitator.isArmed(), "With a player close, it must make itself a sword"))
                .thenExecute(() ->
                {
                    helper.assertTrue(imitator.getMainHandItem().is(Items.IRON_SWORD) || imitator.getMainHandItem().is(Items.DIAMOND_SWORD), "Its sword must be an iron or a diamond one");
                    helper.assertTrue(imitator.getEyePosition().distanceTo(Vec3.atCenterOf(table)) < 3.0D, "It must make it at the crafting table");
                })
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "It must attack the player with its sword"))
                .thenExecute(() ->
                {
                    player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
                    hitAndCheck(helper, imitator, player, false, "Armed, it must take a first blow without showing its face");
                })
                .thenIdle(12)
                .thenExecute(() -> hitAndCheck(helper, imitator, player, false, "Armed, it must take a second blow without showing its face"))
                .thenIdle(12)
                .thenExecute(() -> hitAndCheck(helper, imitator, player, true, "The third blow must unmask it"))
                .thenWaitUntil(() ->
                {
                    helper.assertTrue(imitator.isRemoved(), "It must be gone once it showed its face");
                    helper.assertTrue(!apparitionsAround(level, imitator, HerobrineApparitionEntity.Mode.FLASH).isEmpty(), "Its real face must show for an instant where it stood");
                })
                .thenExecute(() -> removePlayer(player))
                .thenSucceed();
    }

    private static void hitAndCheck(GameTestHelper helper, ImitatorEntity imitator, ServerPlayer player, boolean unmasked, String message)
    {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(imitator.hurtServer(level, level.damageSources().playerAttack(player), 4.0F), "The blow must hurt it");
        helper.assertTrue(imitator.isUnmasked() == unmasked && imitator.isAlive(), message);
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 200, skyAccess = true, padding = 10)
    public void imitatorPutsDownACraftingTableWhenThereIsNone(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -1, 8, Blocks.STONE);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 1, 0.0F);
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(4, 1, 4)), player, List.of());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(imitator.isArmed(), "With a player close, it must make itself a sword"))
                .thenExecute(() ->
                {
                    boolean placed = BlockPos.betweenClosedStream(imitator.blockPosition().offset(-2, -1, -2), imitator.blockPosition().offset(2, 1, 2)).anyMatch(pos -> level.getBlockState(pos).is(Blocks.CRAFTING_TABLE));
                    helper.assertTrue(placed, "With no crafting table around, it must put one down to make its sword");
                    imitator.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 200, skyAccess = true, padding = 10)
    public void imitatorHitTooSoonStrikesBackOnce(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -1, 8, Blocks.STONE);
        //too far for it to notice them: it is still pretending when they hit it (with an arrow, say)
        ServerPlayer player = survivalPlayer(helper, 4, 1, 0, 0.0F);
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(4, 1, 7)), player, List.of());
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() ->
                {
                    helper.assertFalse(imitator.isAfter(player) || imitator.isArmed(), "Far from the player, it must still be pretending");
                    helper.assertTrue(imitator.hurtServer(level, level.damageSources().playerAttack(player), 2.0F) && imitator.isUnmasked(), "Hit before it has its sword, it must be unmasked at once");
                })
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() <= player.getMaxHealth() - 5.0F, "Herobrine must come out of it and strike the player, hard"))
                .thenExecute(() ->
                {
                    helper.assertTrue(imitator.isRemoved(), "The imitator must be gone");
                    helper.assertTrue(level.getEntitiesOfClass(HerobrineApparitionEntity.class, helper.getBounds().inflate(4.0D)).isEmpty(), "He must vanish once he struck");
                    removePlayer(player);
                })
                .thenSucceed();
    }

    private static List<HerobrineApparitionEntity> apparitionsAround(ServerLevel level, Entity entity, HerobrineApparitionEntity.Mode mode)
    {
        return level.getEntitiesOfClass(HerobrineApparitionEntity.class, entity.getBoundingBox().inflate(1.0D), apparition -> apparition.getMode() == mode);
    }

    @GameTest(maxTicks = 60, skyAccess = true, padding = 20)
    public void imitatorWalksAndSprintsLikeAPlayer(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -1, 19, Blocks.STONE);
        removeBarriers(helper);
        ServerPlayer player = player(helper, 4, 1, -6, 0.0F);
        //a workstation close by: it walks there; a far one: it sprints
        BlockPos near = place(helper, 1, 1, 10, Blocks.CRAFTING_TABLE), far = place(helper, 8, 1, 18, Blocks.CRAFTING_TABLE);
        ImitatorEntity walker = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(1, 1, 1)), player, List.of(near));
        ImitatorEntity sprinter = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(8, 1, 1)), player, List.of(far));
        Vec3[] from = new Vec3[2];
        helper.runAfterDelay(10, () ->
        {
            from[0] = walker.position();
            from[1] = sprinter.position();
        });
        helper.runAfterDelay(20, () ->
        {
            //in half a second, a player walks 2.16 blocks and sprints 2.81
            double walked = walker.position().subtract(from[0]).horizontalDistance(), sprinted = sprinter.position().subtract(from[1]).horizontalDistance();
            helper.assertTrue(!walker.isSprinting() && walked > 1.9D && walked < 2.4D, "It must walk at a player's pace, not at " + walked * 2.0D + " blocks per second");
            helper.assertTrue(sprinter.isSprinting() && sprinted > 2.5D && sprinted < 3.1D, "It must sprint at a player's pace, not at " + sprinted * 2.0D + " blocks per second");
            List.of(walker, sprinter).forEach(ImitatorEntity::discard);
            removePlayer(player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = 16)
    public void imitatorOpensTheDoorOnItsWayAndClosesItBehindItself(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos door = wallWithADoor(helper, false);
        ServerPlayer player = player(helper, 4, 1, -6, 0.0F);
        ImitatorEntity imitator = imitatorBehindTheWall(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isDoorOpen(level, door), "The imitator must open the closed door on its way"))
                .thenWaitUntil(() -> helper.assertFalse(isDoorOpen(level, door), "Once past it, the imitator must close the door it opened"))
                .thenExecute(() -> helper.assertTrue(imitator.getZ() > door.getZ() + 2.4D, "It must close the door once through it and a little away, not on itself"))
                .thenIdle(40)
                .thenExecute(() ->
                {
                    helper.assertFalse(isDoorOpen(level, door), "The door must stay closed");
                    imitator.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = 16)
    public void imitatorLeavesTheDoorsItFoundOpen(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos door = wallWithADoor(helper, true);
        ServerPlayer player = player(helper, 4, 1, -6, 0.0F);
        ImitatorEntity imitator = imitatorBehindTheWall(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(imitator.getZ() > door.getZ() + 4.0D, "The imitator must go through the open door"))
                .thenIdle(30)
                .thenExecute(() ->
                {
                    helper.assertTrue(isDoorOpen(level, door), "A door it found open must stay open");
                    imitator.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    /** A wall across the whole floor at z = 6, with a door at x = 4: the only way to the other side. */
    private static BlockPos wallWithADoor(GameTestHelper helper, boolean open)
    {
        floor(helper, -1, 15, Blocks.STONE);
        removeBarriers(helper);
        for (int x = -1; x <= 15; x++) {if (x != 4) {for (int y = 1; y <= 2; y++) {helper.setBlock(x, y, 6, Blocks.STONE_BRICKS);}}}
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, open);
        helper.setBlock(4, 1, 6, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(4, 2, 6, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        return helper.absolutePos(new BlockPos(4, 1, 6));
    }

    /** On one side of the wall, going to a crafting table far enough on the other side. */
    private static ImitatorEntity imitatorBehindTheWall(GameTestHelper helper, ServerPlayer victim)
    {
        BlockPos table = place(helper, 4, 1, 13, Blocks.CRAFTING_TABLE);
        return ImitatorEntity.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), victim, List.of(table));
    }

    private static boolean isDoorOpen(ServerLevel level, BlockPos door) {return level.getBlockState(door).getValue(DoorBlock.OPEN);}

    @GameTest(maxTicks = 20)
    public void imitatorPassesForAnotherConnectedPlayer(GameTestHelper helper)
    {
        ServerPlayer victim = player(helper, 1, 1, 1, 0.0F), near = player(helper, 2, 1, 1, 0.0F), far = player(helper, 3, 1, 1, 0.0F);
        far.snapTo(far.getX() + 200.0D, far.getY(), far.getZ());
        RandomSource random = helper.getLevel().getRandom();
        helper.assertTrue(ImitatorEntity.pickImitated(victim, List.of(victim), random) == null, "Alone, the victim must get a stranger");
        helper.assertTrue(ImitatorEntity.pickImitated(victim, List.of(victim, near), random) == near, "It must pass for another player");
        helper.assertTrue(ImitatorEntity.pickImitated(victim, List.of(victim, near, far), random) == far, "It must prefer a player who can't be seen at the same time");
        ImitatorEntity imitator = new ImitatorEntity(DFTMEntities.IMITATOR, helper.getLevel());
        imitator.disguiseAs(far);
        helper.assertTrue(far.getUUID().equals(imitator.getSkinOwner()) && imitator.getCustomName() != null && imitator.isCustomNameVisible(), "It must wear their skin and their name");
        imitator.disguiseAs(null);
        helper.assertTrue(imitator.getSkinOwner() != null && imitator.getCustomName() == null, "A stranger has a default skin and no name");
        List.of(victim, near, far).forEach(DFTMGameTests::removePlayer);
        helper.succeed();
    }

    //tunnels
    @GameTest(maxTicks = 40)
    public void diggingAStraightTunnelIsNoticed(GameTestHelper helper)
    {
        rock(helper);
        ServerPlayer digger = player(helper, 5, 1, 1, 0.0F), other = player(helper, 2, 1, 1, 0.0F);
        //a 6 slices long tunnel dug southwards, one slice per tick, and a 4 slices long one next to it
        GameTestSequence digging = helper.startSequence();
        for (int z = 1; z <= 6; z++)
        {
            int slice = z;
            digging.thenExecute(() -> dig(helper, digger, 5, slice)).thenIdle(1);
        }
        digging.thenExecute(() ->
        {
            for (int z = 1; z <= 4; z++) {dig(helper, other, 2, z);}
            long now = HauntingDirector.now(helper.getLevel().getServer());
            teleport(helper, digger, 5, 1, 6, 0.0F);
            teleport(helper, other, 2, 1, 4, 0.0F);
            helper.assertTrue(TunnelWatcher.diggingDirection(digger, now) == Direction.SOUTH, "A player digging a straight 2x1 tunnel must be noticed, with the direction they dig in");
            helper.assertTrue(TunnelWatcher.diggingDirection(other, now) == null, "4 slices are not a tunnel yet");
            helper.assertTrue(TunnelWatcher.diggingDirection(digger, now + 20L * 60L * 5L) == null, "A tunnel dug long ago is not being dug any more");
            teleport(helper, digger, 5, 1, 1, 0.0F);
            helper.assertTrue(TunnelWatcher.diggingDirection(digger, now) == null, "Walking back out of the tunnel is not digging it");
            List.of(digger, other).forEach(DFTMGameTests::removePlayer);
        }).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void tunnelIsWalledUpAndTheTopBlockShowsHim(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        rock(helper);
        carve(helper, 4, 1, 6);
        ServerPlayer player = player(helper, 4, 1, 6, 0.0F);
        BlockPos bottom = helper.absolutePos(new BlockPos(4, 1, 3)), top = bottom.above(), feet = helper.absolutePos(new BlockPos(4, 1, 2));
        helper.assertTrue(HauntEvents.wallUpTunnel(player, Direction.SOUTH), "The tunnel must be walled up behind the player");
        helper.assertTrue(level.getBlockState(bottom).isCollisionShapeFullBlock(level, bottom) && level.getBlockState(top).isAir(), "The bottom block first");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lurker(level, feet) != null, "Then the top block, with Herobrine behind"))
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getBlockState(top).equals(level.getBlockState(bottom)), "The top block must be put back with him");
                    helper.assertTrue(lurker(level, feet).isCrouching(), "He must crouch behind the wall");
                    helper.assertTrue(player.gameMode.destroyBlock(top), "The player must dig the top block");
                })
                .thenWaitUntil(() -> helper.assertTrue(lurker(level, feet) != null && lurker(level, feet).isRevealed(), "Digging the top block first must show him"))
                .thenWaitUntil(() -> helper.assertTrue(lurker(level, feet) == null, "He must be gone after an instant"))
                .thenExecute(() ->
                {
                    helper.assertFalse(level.getBlockState(feet).is(Blocks.REDSTONE_TORCH), "Seen, he leaves no torch");
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void tunnelLurkerLeavesATorchWhenTheBottomBlockGoesFirst(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        rock(helper);
        carve(helper, 4, 1, 6);
        ServerPlayer player = player(helper, 4, 1, 6, 0.0F);
        BlockPos bottom = helper.absolutePos(new BlockPos(4, 1, 3)), feet = helper.absolutePos(new BlockPos(4, 1, 2));
        //looking back: no wall can go up unseen
        teleport(helper, player, 4, 1, 6, 180.0F);
        helper.assertFalse(HauntEvents.wallUpTunnel(player, Direction.SOUTH), "The wall must not go up before the player's eyes");
        teleport(helper, player, 4, 1, 6, 0.0F);
        //the command walls up the tunnel behind the player, the way they face
        runCommandAs(helper, player, "dontfollowthemap trigger tunnel");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lurker(level, feet) != null, "The command must wall up the tunnel, with Herobrine behind"))
                .thenExecute(() -> helper.assertTrue(player.gameMode.destroyBlock(bottom), "The player must dig the bottom block"))
                .thenWaitUntil(() -> helper.assertTrue(lurker(level, feet) == null, "Digging the bottom block first must make him leave"))
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getBlockState(feet).is(Blocks.REDSTONE_TORCH), "A redstone torch must stand where he was");
                    helper.assertTrue(level.getBlockState(bottom.above()).isCollisionShapeFullBlock(level, bottom.above()), "The top block must stay");
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void tunnelLurkerLeavesATorchWhenNobodyComes(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        rock(helper);
        carve(helper, 4, 1, 6);
        ServerPlayer player = player(helper, 4, 1, 6, 0.0F);
        BlockPos bottom = place(helper, 4, 1, 3, Blocks.STONE), feet = helper.absolutePos(new BlockPos(4, 1, 2));
        place(helper, 4, 2, 3, Blocks.STONE);
        HerobrineApparitionEntity lurker = HerobrineApparitionEntity.lurk(level, feet, player, bottom, 20);
        helper.succeedWhen(() ->
        {
            helper.assertTrue(lurker.isRemoved(), "He must not wait forever");
            helper.assertTrue(level.getBlockState(feet).is(Blocks.REDSTONE_TORCH), "A redstone torch must stand where he was");
            helper.assertTrue(level.getBlockState(bottom).is(Blocks.STONE) && level.getBlockState(bottom.above()).is(Blocks.STONE), "The wall must stay");
            removePlayer(player);
        });
    }

    /** The bottom of the test area filled with stone, to dig tunnels in (x and z from 0 to 7, y from 0 to 3). */
    private static void rock(GameTestHelper helper)
    {
        for (int x = 0; x <= 7; x++) {for (int y = 0; y <= 3; y++) {for (int z = 0; z <= 7; z++) {helper.setBlock(x, y, z, Blocks.STONE);}}}
    }

    /** A 2x1 tunnel going south at this x, from fromZ to toZ, its floor at y = 0. */
    private static void carve(GameTestHelper helper, int x, int fromZ, int toZ)
    {
        for (int z = fromZ; z <= toZ; z++) {helper.setBlock(x, 1, z, Blocks.AIR); helper.setBlock(x, 2, z, Blocks.AIR);}
    }

    /** The player digs the slice of 2x1 tunnel standing at this x and z (their own hands: the mod sees each block break). */
    private static void dig(GameTestHelper helper, ServerPlayer player, int x, int z)
    {
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(x, 2, z))) && player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(x, 1, z))), "The player must dig the tunnel");
    }

    @Nullable private static HerobrineApparitionEntity lurker(ServerLevel level, BlockPos feet)
    {
        return level.getEntitiesOfClass(HerobrineApparitionEntity.class, new AABB(feet), apparition -> apparition.getMode() == HerobrineApparitionEntity.Mode.LURK).stream().findFirst().orElse(null);
    }

    //seal shrine and boss
    @GameTest(maxTicks = 200, skyAccess = true, padding = 12)
    public void sealShrineIsBuiltOnDemandThenFallsIntoRuins(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        //snowy grass under a layer of snow, all around, with a patch of gravel in a corner
        for (int x = -2; x <= SealPiece.SIZE + 1; x++)
        {
            for (int z = -2; z <= SealPiece.SIZE + 1; z++)
            {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true));
                helper.setBlock(x, 1, z, Blocks.SNOW);
            }
        }
        helper.setBlock(-2, 0, -2, Blocks.GRAVEL);
        helper.setBlock(-2, 1, -2, Blocks.AIR);
        //right in front of it
        ServerPlayer player = player(helper, SealPiece.CENTER, 1, SealPiece.SIZE + 1, 180.0F);
        BlockPos center = helper.absolutePos(new BlockPos(SealPiece.CENTER, 1, SealPiece.CENTER));
        runCommandAs(helper, player, String.format(Locale.ROOT, "dontfollowthemap generate-seal %d %d %d", center.getX(), center.getY(), center.getZ()));
        SealShrines.Shrine shrine = SealShrines.get(server).active();
        helper.assertTrue(shrine != null && shrine.stage() == SealShrines.Stage.BUILT && shrine.center().equals(center), "The command must build a shrine right there, the one the maps lead to");
        BlockState seal = level.getBlockState(center);
        helper.assertTrue(seal.is(DFTMBlocks.SEAL) && seal.getValue(SealBlock.STATE) == SealBlock.State.SEALED, "The seal must stand in the middle of the shrine");
        List<BlockPos> area = BlockPos.betweenClosedStream(new AABB(center).inflate(SealPiece.CENTER, SealPiece.HEIGHT, SealPiece.CENTER)).map(BlockPos::immutable).toList();
        helper.assertTrue(area.stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.CHEST)), "Something must be buried under the seal");
        helper.assertTrue(area.stream().filter(pos -> level.getBlockState(pos).is(Blocks.SOUL_LANTERN)).count() == 4, "The four pillars must hold a soul lantern");
        boolean warning = area.stream().anyMatch(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.isWaxed()
                && sign.getText(SignTextSlot.FRONT).getMessages(false).get(1).getContents() instanceof TranslatableContents text && text.getKey().equals("sign.dontfollowthemap.shrine.1"));
        helper.assertTrue(warning, "The shrine must have its warning sign");
        runCommandAs(helper, player, "dontfollowthemap locate-seal");

        ItemStack map = SealMaps.create(level, player.blockPosition());
        helper.assertTrue(map != null && SealMaps.shrineId(map) == shrine.id(), "The maps must lead to this shrine");
        helper.assertTrue(SealShrines.opens(level, center, map) && !SealShrines.opens(level, center.east(), map), "A map must break its seal, and no other");
        helper.assertTrue(SealShrines.isWatched(level, shrine), "The player right in front of it sees it");

        //Herobrine is defeated there: the shrine falls into ruins (once nobody sees it), then its maps are worthless
        SealShrines.onDefeated(level, center);
        helper.assertTrue(SealShrines.get(server).active() == null, "Once he is defeated, the next map must pick a new place");
        helper.assertFalse(SealMaps.isWorthless(server, map), "The maps stay good while the shrine stands");
        long before = area.stream().filter(pos -> isShrineRemain(level, pos)).count();
        SealShrines.fallIntoRuins(level, shrine);
        long after = area.stream().filter(pos -> isShrineRemain(level, pos)).count();
        helper.assertFalse(level.getBlockState(center).is(DFTMBlocks.SEAL) || area.stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.SOUL_LANTERN)), "The seal and the lanterns must be gone");
        helper.assertTrue(after > 0 && after < before / 2, "Most of the shrine must be gone, a few stones staying as ruins (" + after + " of " + before + " blocks left)");
        int floorY = center.getY() - 1;
        helper.assertTrue(area.stream().filter(pos -> pos.getY() == floorY && !isShrineRemain(level, pos)).allMatch(pos -> level.getBlockState(pos).is(Blocks.GRASS_BLOCK) && level.getBlockState(pos.above()).is(Blocks.SNOW)),
                "Where the floor was, the ground must come back as it is all around: snowy grass under a layer of snow, whatever lies in a corner");
        helper.assertTrue(SealMaps.isWorthless(server, map), "Its maps must be worthless");

        //a worthless map jumps out of the player's inventory, one dropped by an item frame lies there: nobody can take them, and they burn
        player.getInventory().add(map.copy());
        level.addFreshEntity(new ItemEntity(level, center.getX() + 3.5D, center.getY() + 1.0D, center.getZ() + 0.5D, map.copy()));
        SealMaps.throwAwayWorthless(player);
        helper.assertFalse(SealMaps.hasSealMap(player), "The worthless map must leave the player's inventory");
        List<ItemEntity> maps = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(16.0D), item -> SealMaps.isSealMap(item.getItem()));
        helper.assertTrue(maps.size() == 2 && maps.stream().allMatch(ItemEntity::hasPickUpDelay), "Nobody can pick them up");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(maps.stream().allMatch(item -> item.isOnFire() || item.isRemoved()), "They must catch fire"))
                .thenWaitUntil(() -> helper.assertTrue(maps.stream().allMatch(ItemEntity::isRemoved), "Then burn away"))
                .thenExecute(() -> removePlayer(player))
                .thenSucceed();
    }

    private static boolean isShrineRemain(ServerLevel level, BlockPos pos) {return SealShrines.isShrineBlock(level.getBlockState(pos));}

    @GameTest(maxTicks = 20)
    public void sealShrineIsNeverPlannedInTheWrongBiome(GameTestHelper helper)
    {
        //the test world is a flat desert (the client test picks a place in plains)
        SealShrines shrines = SealShrines.get(helper.getLevel().getServer());
        SealShrines.Shrine active = shrines.active();
        helper.assertTrue(SealShrines.plan(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) == null, "No place must be picked in a desert");
        helper.assertTrue(shrines.active() == active, "A place not found must change nothing");
        helper.succeed();
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = 8)
    public void sealMapFreesHerobrine(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        BlockPos seal = place(helper, 4, 1, 4, DFTMBlocks.SEAL);
        ServerPlayer player = player(helper, 1, 1, 1, 0.0F);
        //breaking the seal doesn't look at which shrine the map leads to (the seal block does)
        ItemStack map = SealMaps.create(level, new SealShrines.Shrine(-1, seal, Direction.NORTH, SealShrines.Stage.BUILT));
        helper.assertTrue(SealMaps.isSealMap(map), "The seal map must be recognized");
        helper.assertFalse(SealMaps.isSealMap(new ItemStack(Items.FILLED_MAP)), "A normal map is not a seal map");

        SealEvents.breakSeal(level, seal, player, map);
        helper.assertTrue(level.getBlockState(seal).getValue(SealBlock.STATE) == SealBlock.State.OPEN, "The seal must break open");
        helper.assertTrue(HauntingData.get(player).sealState == HauntingData.FREED, "The player freed him");
        helper.succeedWhen(() ->
        {
            //right above the seal (other tests running nearby may have their own Herobrine)
            List<HerobrineEntity> freed = level.getEntitiesOfClass(HerobrineEntity.class, new AABB(seal.above()).inflate(1.0D));
            helper.assertTrue(freed.size() == 1, "Herobrine must come out of the seal");
            freed.forEach(HerobrineEntity::discard);
            removePlayer(player);
        });
    }

    @GameTest(maxTicks = 40, skyAccess = true, padding = 10)
    public void sealWithstandsPistonsExplosionsAndWithers(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, 0, 8, Blocks.STONE);
        BlockPos seal = place(helper, 4, 1, 4, DFTMBlocks.SEAL);
        BlockState state = level.getBlockState(seal);
        helper.assertTrue(state.getDestroySpeed(level, seal) < 0.0F, "The seal must not be breakable by hand");
        helper.assertFalse(WitherBoss.canDestroy(state), "A wither must not break the seal");
        helper.assertTrue(state.is(BlockTags.DRAGON_IMMUNE), "The dragon must not break the seal");
        //a powered piston right against it
        helper.setBlock(4, 1, 3, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
        helper.setBlock(4, 1, 2, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getBlockState(seal).is(DFTMBlocks.SEAL), "A piston must not push the seal");
                    helper.assertFalse(level.getBlockState(seal.north()).getValue(PistonBaseBlock.EXTENDED), "The piston must stay stuck against the seal");
                    //TNT going off right next to it
                    level.explode(null, seal.getX() + 1.5D, seal.getY() + 0.5D, seal.getZ() + 0.5D, 4.0F, Level.ExplosionInteraction.TNT);
                })
                .thenIdle(2)
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getBlockState(seal).is(DFTMBlocks.SEAL), "An explosion must not break the seal");
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(seal).inflate(8.0D)).forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 300, skyAccess = true, padding = 8)
    public void herobrineFightsThenFadesAway(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        BlockPos seal = place(helper, 4, 1, 4, DFTMBlocks.SEAL.defaultBlockState().setValue(SealBlock.STATE, SealBlock.State.OPEN));
        HerobrineEntity herobrine = helper.spawn(DFTMEntities.HEROBRINE, new BlockPos(1, 1, 1));
        herobrine.setSealPos(seal);
        //a punching bag: it doesn't fight back and can't die, so Herobrine keeps using his powers on it
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(7, 1, 7));
        golem.setNoAi(true);
        golem.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        herobrine.setTarget(golem);
        herobrine.setHealth(herobrine.getMaxHealth() * 0.4F);

        helper.runAfterDelay(120, () ->
        {
            helper.assertTrue(herobrine.isAlive(), "Herobrine must survive his fight against a punching bag");
            helper.assertTrue(herobrine.getPhase() == 2, "Herobrine must have entered his second phase, not " + herobrine.getPhase());
            helper.assertTrue(golem.isAlive() && herobrine.getTarget() == golem, "Herobrine must still be fighting");
            herobrine.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(herobrine.isDeadOrDying(), "Herobrine must die");
        });
        helper.runAfterDelay(120 + HerobrineEntity.DEATH_DURATION + 5, () ->
        {
            helper.assertTrue(herobrine.isRemoved(), "Herobrine must be gone once he faded away");
            helper.assertTrue(level.getBlockState(seal).getValue(SealBlock.STATE) == SealBlock.State.SHATTERED, "The seal must shatter");
            AABB around = new AABB(herobrine.blockPosition()).inflate(12.0D);
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(DFTMItems.SEAL_FRAGMENT)).isEmpty(), "Herobrine must drop a seal fragment");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(environment = HEROBRINE_FIGHT, maxTicks = 500, skyAccess = true, padding = 8)
    public void herobrineFightsEveryoneInTheFight(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        BlockPos seal = place(helper, 4, 1, 4, DFTMBlocks.SEAL.defaultBlockState().setValue(SealBlock.STATE, SealBlock.State.OPEN));
        HerobrineEntity herobrine = helper.spawn(DFTMEntities.HEROBRINE, new BlockPos(1, 1, 1));
        herobrine.setSealPos(seal);
        //his last phase: the lights go out right away
        herobrine.setHealth(herobrine.getMaxHealth() * 0.2F);
        //two players in the fight (out of harm's way), the first one closer to him; one more watching in creative
        ServerPlayer first = survivalPlayer(helper, 3, 1, 1, 0.0F), second = survivalPlayer(helper, 9, 1, 9, 0.0F), creative = player(helper, 9, 1, 1, 0.0F);
        List.of(first, second).forEach(player -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4)));
        creative.setGameMode(GameType.CREATIVE);
        herobrine.setTarget(first);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() ->
                {
                    helper.assertTrue(first.hasEffect(MobEffects.DARKNESS) && second.hasEffect(MobEffects.DARKNESS), "The darkness must fall on everyone in the fight, not only on his target");
                    helper.assertFalse(creative.hasEffect(MobEffects.DARKNESS), "The darkness must spare the players in creative");
                })
                .thenWaitUntil(() -> helper.assertTrue(herobrine.getTarget() == second && herobrine.distanceTo(second) < 4.0D, "With two players in the fight, a blink must take him to the other one"))
                .thenExecute(() -> second.snapTo(second.getX() + 300.0D, second.getY(), second.getZ()))
                .thenWaitUntil(() -> helper.assertTrue(herobrine.getTarget() == first, "When his target leaves the fight, he must turn to the one still in it"))
                .thenExecute(() ->
                {
                    herobrine.discard();
                    level.getEntitiesOfClass(HerobrineApparitionEntity.class, helper.getBounds().inflate(16.0D)).forEach(HerobrineApparitionEntity::discard);
                    List.of(first, second, creative).forEach(DFTMGameTests::removePlayer);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 20)
    public void herobrineDefeatBringsPeaceToEveryone(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        long now = HauntingDirector.now(server);
        //one fought him, one was far away (both had freed him); one more comes back later, with an old save
        ServerPlayer fighter = player(helper, 1, 1, 1, 0.0F), away = player(helper, 2, 1, 1, 0.0F);
        away.snapTo(away.getX() + 300.0D, away.getY(), away.getZ());
        for (ServerPlayer player : List.of(fighter, away))
        {
            HauntingData data = HauntingData.get(player);
            data.setStage(HauntingData.HUNTED, now);
            data.sealState = HauntingData.FREED;
        }
        HerobrineEntity herobrine = helper.spawn(DFTMEntities.HEROBRINE, new BlockPos(1, 1, 3));
        SealEvents.onHerobrineDefeated(level, herobrine);
        int defeats = WorldPeace.get(server).defeats();

        HauntingData fighterData = HauntingData.get(fighter), awayData = HauntingData.get(away);
        helper.assertTrue(fighterData.stage(now) == HauntingData.QUIET && fighterData.sealState == HauntingData.SEALED && fighterData.defeats == 1 && fighterData.worldDefeats == defeats, "The one who fought him must get the peace");
        WorldPeace.catchUp(away);
        helper.assertTrue(awayData.stage(now) == HauntingData.QUIET && awayData.sealState == HauntingData.SEALED && awayData.defeats == 0, "The one far away must get the peace too, without having defeated him");
        helper.assertTrue(!awayData.hasMapBeenDelivered() && awayData.worldDefeats == defeats, "...and a new map once the haunting starts over");
        ServerPlayer late = player(helper, 3, 1, 1, 0.0F);
        HauntingData lateData = HauntingData.get(late);
        lateData.setStage(HauntingData.HUNTED, now);
        lateData.sealState = HauntingData.FREED;
        WorldPeace.catchUp(late);
        helper.assertTrue(lateData.stage(now) == HauntingData.QUIET && lateData.sealState == HauntingData.SEALED, "The one who comes back later must get the peace too");

        herobrine.discard();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(herobrine.blockPosition()).inflate(4.0D), item -> item.getItem().is(DFTMItems.SEAL_FRAGMENT)).forEach(ItemEntity::discard);
        List.of(fighter, away, late).forEach(DFTMGameTests::removePlayer);
        helper.succeed();
    }

    //players out of reach: walled in, up on a pillar, behind a shield
    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 16)
    public void stalkerMinesAHoleInAWallAndHitsThroughIt(GameTestHelper helper)
    {
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        List<BlockPos> cell = cell(helper, 4, 4);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 4, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 1));
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, -2.5D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stalker.getMainHandItem().is(Items.DIAMOND_PICKAXE), "Walled in, the player must be mined out, a pickaxe in hand"))
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Through the hole, the stalker must hit the player"))
                .thenExecute(() ->
                {
                    long mined = cell.stream().filter(pos -> helper.getLevel().getBlockState(pos).isAir()).count();
                    helper.assertTrue(mined >= 1 && mined <= 2, "Only a hole, not a whole way in (" + mined + " blocks mined)");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 16)
    public void stalkerMinesThePillarFromUnderAPlayer(GameTestHelper helper)
    {
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        BlockPos top = pillar(helper, 4, 4, 3);
        ServerPlayer player = survivalPlayer(helper, 4, 4, 4, 180.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, -1.5D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockState(top).isAir(), "Up on a pillar within reach, what the player stands on must be mined"))
                .thenExecute(() ->
                {
                    helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The player must be brought down, not hit from below");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 16)
    public void stalkerFloatsUpToMineAHighPillar(GameTestHelper helper)
    {
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        BlockPos top = pillar(helper, 4, 4, 8);
        ServerPlayer player = survivalPlayer(helper, 4, 9, 4, 180.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, 0.5D);
        double ground = helper.absoluteVec(new Vec3(0.0D, 1.0D, 0.0D)).y;
        boolean[] floated = {false};
        helper.onEachTick(() -> floated[0] |= stalker.isNoGravity() && stalker.getY() > ground + 0.5D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockState(top).isAir(), "Up on a high pillar, what the player stands on must be mined"))
                .thenExecute(() ->
                {
                    helper.assertTrue(floated[0], "Out of reach from the ground, it must float up first");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 16)
    public void floatingStalkerMinesTheCeilingInItsWay(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        BlockPos top = pillar(helper, 4, 4, 8);
        //a glass ceiling all around the pillar, just above the stalker's head
        List<BlockPos> ceiling = new ArrayList<>();
        for (int x = 1; x <= 7; x++) {for (int z = 1; z <= 7; z++) {if (x != 4 || z != 4) {ceiling.add(place(helper, x, 3, z, Blocks.GLASS));}}}
        ServerPlayer player = survivalPlayer(helper, 4, 9, 4, 180.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, 2.5D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(top).isAir(), "Up on a high pillar, what the player stands on must be mined"))
                .thenExecute(() ->
                {
                    helper.assertTrue(ceiling.stream().anyMatch(level::isEmptyBlock), "Floating up, it must mine the ceiling it runs into");
                    helper.assertTrue(BlockPos.betweenClosedStream(top.below(7), top.below()).allMatch(pos -> level.getBlockState(pos).is(Blocks.COBBLESTONE)), "It must not dig into the player's pillar");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 12)
    public void stalkerGoesStraightThroughACurrentAtAPlayer(GameTestHelper helper)
    {
        //within the barriers of the test area: a bucket of water poured at the player's feet, its current running over the floor
        //against whatever comes at them (shallow: nothing a mob would swim in)
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 180.0F);
        helper.setBlock(4, 1, 6, Blocks.WATER);
        List<HerobrineApparitionEntity> stalker = new ArrayList<>();
        boolean[] flew = {false};
        helper.onEachTick(() -> stalker.forEach(apparition -> flew[0] |= apparition.isNoGravity() && apparition.isInWater()));
        helper.startSequence()
                //the current has spread
                .thenIdle(40)
                .thenExecute(() -> stalker.add(stalker(helper, player, 4.5D, 0.5D)))
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Against the current, the stalker must still get to the player and strike them"))
                .thenExecute(() ->
                {
                    helper.assertTrue(flew[0], "In the water, even shallow, it must go straight at them, as through the air");
                    stalker.forEach(Entity::discard);
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 24)
    public void stalkerBlinksIntoTheLakeAroundAPlayer(GameTestHelper helper)
    {
        //a lake 2 blocks deep around the player, held in by a rim of stone farther than the stalker blinks (12 blocks)
        floor(helper, -10, 17, Blocks.STONE);
        removeBarriers(helper);
        for (int x = -10; x <= 17; x++)
        {
            for (int z = -10; z <= 17; z++)
            {
                boolean rim = x == -10 || x == 17 || z == -10 || z == 17;
                for (int y = 1; y <= 2; y++) {helper.setBlock(x, y, z, rim ? Blocks.STONE : Blocks.WATER);}
            }
        }
        ServerPlayer player = survivalPlayer(helper, 4, 1, 4, 180.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, 2.0D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.hasEffect(MobEffects.DARKNESS), "In the lake, the stalker must strike the player"))
                .thenWaitUntil(() -> helper.assertTrue(stalker.distanceTo(player) > 6.0D && stalker.isInWater(), "After its blow, it must be gone behind them, into the lake (not stay where it struck)"))
                .thenExecute(() ->
                {
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 16)
    public void stalkerSwimsStraightAtAPlayerInDeepWater(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        //within the barriers of the test area: a pool 4 blocks deep, cut in two by a stone wall
        floor(helper, 0, 7, Blocks.STONE);
        List<BlockPos> wall = new ArrayList<>();
        for (int x = 0; x <= 7; x++)
        {
            for (int z = 0; z <= 7; z++) {for (int y = 1; y <= 4; y++) {if (z != 4) {helper.setBlock(x, y, z, Blocks.WATER);}}}
            for (int y = 1; y <= 6; y++) {wall.add(place(helper, x, y, 4, Blocks.STONE));}
        }
        //the player swims beyond it, 2 blocks above the bottom; the stalker is at the bottom, on the other side
        ServerPlayer player = survivalPlayer(helper, 4, 3, 6, 180.0F);
        BlockPos underThePlayer = helper.absolutePos(new BlockPos(4, 0, 6));
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, 1.5D);
        boolean[] swam = {false};
        helper.onEachTick(() -> swam[0] |= stalker.isNoGravity() && stalker.isInWater());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "In the water, the stalker must get to the player and strike them"))
                .thenExecute(() ->
                {
                    helper.assertTrue(swam[0], "It must swim straight at them");
                    helper.assertTrue(wall.stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.WATER) || level.isEmptyBlock(pos)), "It must mine its way through the wall");
                    helper.assertTrue(level.getBlockState(underThePlayer).is(Blocks.STONE), "A swimming player stands on nothing: what lies under the water must not be mined");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 200, skyAccess = true, padding = 16)
    public void stalkerInAGapOfAPillarGetsOutAndFloatsUpBesideIt(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        //a pillar with a gap 2 blocks high (left when its lower part was mined): the player on top, out of reach from the gap
        pillar(helper, 4, 4, 3);
        for (int y = 6; y <= 12; y++) {helper.setBlock(4, y, 4, Blocks.COBBLESTONE);}
        BlockPos top = helper.absolutePos(new BlockPos(4, 12, 4)), aboveTheGap = helper.absolutePos(new BlockPos(4, 6, 4));
        ServerPlayer player = survivalPlayer(helper, 4, 13, 4, 180.0F);
        //the stalker right under what the player stands on, in the gap
        HerobrineApparitionEntity stalker = HerobrineApparitionEntity.spawn(level, helper.absoluteVec(new Vec3(4.5D, 4.0D, 4.5D)), player, HerobrineApparitionEntity.Mode.STALK, HerobrineApparitionEntity.STALK_LIFETIME);
        stalker.setFogging(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(top).isAir(), "Out of the gap and up beside the pillar, it must mine what the player stands on"))
                .thenExecute(() ->
                {
                    helper.assertTrue(level.getBlockState(aboveTheGap).is(Blocks.COBBLESTONE), "It must not dig up into the player's pillar");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 400, skyAccess = true, padding = 16)
    public void stalkerMinesOnlyWhatAPlayerBuildingUpStandsOn(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        int[] top = {5};
        pillar(helper, 4, 4, top[0]);
        ServerPlayer player = survivalPlayer(helper, 4, top[0] + 1, 4, 180.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 6.5D, 4.5D);
        //once it mines what they stand on, the player builds up, a block every 4 ticks (faster than it mines), 6 blocks high
        long[] start = {-1L};
        boolean[] gap = {false};
        helper.onEachTick(() ->
        {
            if (start[0] < 0L && stalker.getMainHandItem().is(Items.DIAMOND_PICKAXE)) {start[0] = helper.getTick();}
            if (start[0] >= 0L && top[0] < 11 && (helper.getTick() - start[0]) % 4 == 3)
            {
                place(helper, 4, ++top[0], 4, Blocks.COBBLESTONE);
                teleport(helper, player, 4, top[0] + 1, 4, 180.0F);
            }
            //a block mined under one still standing: a gap
            boolean air = false;
            for (int y = 1; y <= top[0]; y++)
            {
                boolean solid = level.getBlockState(helper.absolutePos(new BlockPos(4, y, 4))).is(Blocks.COBBLESTONE);
                gap[0] |= air && solid;
                air |= !solid;
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(top[0] == 11 && level.isEmptyBlock(helper.absolutePos(new BlockPos(4, 11, 4))), "Up on what they built, the player must have what they stand on mined"))
                .thenExecute(() ->
                {
                    helper.assertTrue(!gap[0], "Only what the player stands on must be mined, not the block they built over (it would leave a gap in their pillar)");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_STILL, maxTicks = 400, skyAccess = true, padding = 16)
    public void floatingStalkerMinesTheSnowLayerItRunsInto(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        //within the barriers of the test area: a moat of lava from one side to the other (on a floor raised by one block),
        //the player beyond it, behind a line of thin snow (2 layers: a step a floating stalker can't take)
        floor(helper, 0, 7, Blocks.STONE);
        for (int x = 0; x <= 7; x++)
        {
            for (int z = 0; z <= 7; z++) {helper.setBlock(x, 1, z, z == 3 ? Blocks.LAVA : Blocks.STONE);}
            helper.setBlock(x, 2, 4, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        }
        BlockPos snow = helper.absolutePos(new BlockPos(4, 2, 4));
        //out of reach across the moat (3 blocks)
        ServerPlayer player = survivalPlayer(helper, 4, 2, 7, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        HerobrineApparitionEntity stalker = HerobrineApparitionEntity.spawn(level, helper.absoluteVec(new Vec3(4.5D, 2.0D, 0.5D)), player, HerobrineApparitionEntity.Mode.STALK, HerobrineApparitionEntity.STALK_LIFETIME);
        stalker.setFogging(player);
        boolean[] floated = {false};
        helper.onEachTick(() -> floated[0] |= stalker.isNoGravity());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(snow).isAir(), "Floating over the lava, the stalker must mine the snow it runs into"))
                .thenWaitUntil(() -> helper.assertTrue(!stalker.isNoGravity() && stalker.getZ() > snow.getZ() + 0.5D, "Past it, it must land and walk on"))
                .thenExecute(() ->
                {
                    helper.assertTrue(floated[0], "It must float over the lava");
                    helper.assertTrue(level.getBlockState(snow.west()).is(Blocks.SNOW), "Only the snow in its way must be mined");
                    stalker.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 600, skyAccess = true, padding = 16)
    public void imitatorBuildsUpToMineAHighPillar(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -6, 13, Blocks.STONE);
        removeBarriers(helper);
        BlockPos table = place(helper, 1, 1, 4, Blocks.CRAFTING_TABLE), top = pillar(helper, 4, 6, 8);
        ServerPlayer player = survivalPlayer(helper, 3, 1, 3, 0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(2, 1, 4)), player, List.of(table));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(imitator.isArmed(), "With a player close, it must make itself a sword"))
                //who climbs out of its reach
                .thenExecute(() -> teleport(helper, player, 4, 9, 6, 0.0F))
                .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(top).isAir(), "It must bring the player down from their high pillar"))
                .thenExecute(() ->
                {
                    long built = BlockPos.betweenClosedStream(top.offset(-4, -8, -4), top.offset(4, 0, 4))
                            .filter(pos -> (pos.getX() != top.getX() || pos.getZ() != top.getZ()) && level.getBlockState(pos).is(Blocks.COBBLESTONE)).count();
                    helper.assertTrue(built > 0, "Out of reach from the ground, it must build itself a pillar first");
                    imitator.discard();
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 12)
    public void possessedAnimalLeapsOntoALowPillar(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.GRASS_BLOCK);
        removeBarriers(helper);
        pillar(helper, 4, 6, 3);
        ServerPlayer player = survivalPlayer(helper, 4, 4, 6, 180.0F);
        Animal sheep = PossessableAnimals.SHEEP.possess(helper.spawn(EntityTypes.SHEEP, new BlockPos(4, 1, 2)), player);
        //provoked: it charges
        sheep.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        helper.succeedWhen(() ->
        {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Up on a pillar 3 blocks high, the player must still be bitten: the animal leaps");
            level.getEntitiesOfClass(Sheep.class, helper.getBounds().inflate(16.0D)).forEach(Entity::discard);
            removePlayer(player);
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = Possession.SIEGE_MAX_TICKS + 200, skyAccess = true, padding = 12)
    public void possessedAnimalBesiegingAHighPillarTurnsIntoAStalker(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.GRASS_BLOCK);
        removeBarriers(helper);
        pillar(helper, 4, 6, 8);
        ServerPlayer player = survivalPlayer(helper, 4, 9, 6, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        Animal sheep = PossessableAnimals.SHEEP.possess(helper.spawn(EntityTypes.SHEEP, new BlockPos(4, 1, 2)), player);
        sheep.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        helper.runAfterDelay(Possession.SIEGE_MIN_TICKS - 40, () -> helper.assertTrue(sheep.isAlive(), "It must wait below the player for 20 seconds at least"));
        helper.succeedWhen(() ->
        {
            List<HerobrineApparitionEntity> stalkers = stalkersAfter(helper, player);
            helper.assertTrue(sheep.isRemoved() && !stalkers.isEmpty(), "After its siege, Herobrine's stalker must come instead of it");
            stalkers.forEach(Entity::discard);
            removePlayer(player);
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 12)
    public void stalkerBlinksBehindARaisedShield(GameTestHelper helper)
    {
        floor(helper, -4, 11, Blocks.STONE);
        removeBarriers(helper);
        //facing the stalker, the shield up
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 1));
        raiseShield(player);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, 0.5D);
        helper.assertTrue(Players.isShieldedFrom(player, stalker), "The player's shield must stand in the stalker's way");
        helper.succeedWhen(() ->
        {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "A raised shield must not stop it: it blinks behind the player");
            stalker.discard();
            removePlayer(player);
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 600, skyAccess = true, padding = 12)
    public void imitatorThatKeepsHittingARaisedShieldTurnsIntoAStalker(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        removeBarriers(helper);
        BlockPos table = place(helper, 6, 1, 4, Blocks.CRAFTING_TABLE);
        ServerPlayer player = survivalPlayer(helper, 2, 1, 2, 0.0F);
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(2, 1, 5)), player, List.of(table));
        boolean[] hurt = {false};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(imitator.isArmed(), "With a player close, it must make itself a sword"))
                .thenExecute(() ->
                {
                    raiseShield(player);
                    //whom the player keeps facing
                    helper.onEachTick(() ->
                    {
                        if (imitator.isRemoved()) {return;}
                        turnTowards(player, imitator);
                        hurt[0] |= player.getHealth() < player.getMaxHealth();
                    });
                })
                .thenWaitUntil(() -> helper.assertTrue(imitator.isRemoved() && !stalkersAfter(helper, player).isEmpty(), "After " + ImitatorEntity.PARRIED_BLOWS + " blows into the shield, it must drop its disguise: a stalker comes instead"))
                .thenExecute(() ->
                {
                    helper.assertTrue(!hurt[0] && player.getOffhandItem().getDamageValue() > 0, "Its blows must go into the shield, not round it");
                    stalkersAfter(helper, player).forEach(Entity::discard);
                    removePlayer(player);
                })
                .thenSucceed();
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 300, skyAccess = true, padding = 12)
    public void possessedAnimalThatKeepsBitingARaisedShieldTurnsIntoAStalker(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.GRASS_BLOCK);
        removeBarriers(helper);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 180.0F);
        raiseShield(player);
        Animal sheep = PossessableAnimals.SHEEP.possess(helper.spawn(EntityTypes.SHEEP, new BlockPos(4, 1, 2)), player);
        sheep.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        boolean[] bitten = {false};
        helper.onEachTick(() ->
        {
            if (sheep.isRemoved()) {return;}
            turnTowards(player, sheep);
            bitten[0] |= player.getHealth() < player.getMaxHealth();
        });
        helper.succeedWhen(() ->
        {
            List<HerobrineApparitionEntity> stalkers = stalkersAfter(helper, player);
            helper.assertTrue(sheep.isRemoved() && !stalkers.isEmpty(), "After " + Possession.PARRIED_BITES + " bites into the shield, Herobrine's stalker must come instead of it");
            helper.assertTrue(!bitten[0] && player.getOffhandItem().getDamageValue() > 0, "A raised shield must stop its bites");
            stalkers.forEach(Entity::discard);
            removePlayer(player);
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 200, skyAccess = true, padding = 24)
    public void stalkerAppearsInTheWayOfAPlayerRunningAway(GameTestHelper helper)
    {
        floor(helper, -4, 27, Blocks.STONE);
        removeBarriers(helper);
        //running south at a sprint (5.6 blocks per second, faster than it creeps), the stalker far behind them
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, -3.5D);
        Vec3 start = player.position();
        int[] ran = {0};
        boolean[] ahead = {false};
        helper.onEachTick(() ->
        {
            if (ran[0] < 60) {player.snapTo(start.x, start.y, start.z + ++ran[0] * 0.28D, 0.0F, 0.0F);}
            ahead[0] |= stalker.getZ() > player.getZ() + 2.0D;
        });
        helper.succeedWhen(() ->
        {
            helper.assertTrue(ahead[0], "Running away from it, the player must find it in their way");
            stalker.discard();
            removeCobwebs(helper);
            removePlayer(player);
        });
    }

    @GameTest(environment = SURVIVAL_PLAYERS, maxTicks = 100, skyAccess = true, padding = 16)
    public void stalkerAheadOfAPlayerRunningAwayPutsACobwebInTheirWay(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 15, Blocks.STONE);
        removeBarriers(helper);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 1, 0.0F);
        HerobrineApparitionEntity stalker = stalker(helper, player, 4.5D, -6.5D);
        helper.assertTrue(stalker.blinkAhead(level, player, new Vec3(0.0D, 0.0D, 1.0D), true), "There must be room for it ahead of the player");
        helper.assertTrue(stalker.getZ() > player.getZ() + 4.0D && stalker.getMainHandItem().is(Items.COBWEB), "It must stand ahead of the player, a cobweb in its hand");
        BlockPos web = stalker.blockPosition().north();
        helper.succeedWhen(() ->
        {
            helper.assertTrue(level.getBlockState(web).is(Blocks.COBWEB), "It must put the cobweb down in front of itself, on the player's way");
            helper.assertTrue(stalker.getMainHandItem().isEmpty(), "Then its hand must be empty again");
            stalker.discard();
            removeCobwebs(helper);
            removePlayer(player);
        });
    }

    @GameTest(environment = HEROBRINE_SHIELD, maxTicks = 400, skyAccess = true, padding = 8)
    public void herobrineKnocksARaisedShieldAsideWithAnAxe(GameTestHelper helper)
    {
        floor(helper, -4, 11, Blocks.STONE);
        HerobrineEntity herobrine = helper.spawn(DFTMEntities.HEROBRINE, new BlockPos(4, 1, 1));
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 4));
        raiseShield(player);
        herobrine.setTarget(player);
        boolean[] axe = {false};
        helper.onEachTick(() ->
        {
            //the player keeps facing him
            turnTowards(player, herobrine);
            axe[0] |= herobrine.getMainHandItem().is(Items.DIAMOND_AXE);
        });
        helper.succeedWhen(() ->
        {
            helper.assertTrue(axe[0] && player.getCooldowns().isOnCooldown(player.getOffhandItem()), "Herobrine's axe must knock the raised shield aside");
            herobrine.discard();
            removePlayer(player);
        });
    }

    @GameTest(environment = HEROBRINE_WALLS, maxTicks = 400, skyAccess = true, padding = 8)
    public void herobrineMinesHisWayToAWalledInPlayer(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        removeBarriers(helper);
        List<BlockPos> cell = cell(helper, 4, 6);
        ServerPlayer player = survivalPlayer(helper, 4, 1, 6, 180.0F);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 3));
        HerobrineEntity herobrine = helper.spawn(DFTMEntities.HEROBRINE, new BlockPos(4, 1, 1));
        herobrine.setTarget(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cell.stream().anyMatch(pos -> level.getBlockState(pos).isAir()), "Walled in, the player must be mined out"))
                .thenWaitUntil(() -> helper.assertTrue(herobrine.getLastHurtMob() == player, "Through the hole, he must strike the player"))
                .thenExecute(() ->
                {
                    herobrine.discard();
                    level.getEntitiesOfClass(HerobrineApparitionEntity.class, helper.getBounds().inflate(16.0D)).forEach(Entity::discard);
                    removePlayer(player);
                })
                .thenSucceed();
    }

    /** A stalker after this player, standing there (x and z of the test area, on its floor). */
    private static HerobrineApparitionEntity stalker(GameTestHelper helper, ServerPlayer player, double x, double z)
    {
        HerobrineApparitionEntity stalker = HerobrineApparitionEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 1.0D, z)), player, HerobrineApparitionEntity.Mode.STALK, HerobrineApparitionEntity.STALK_LIFETIME);
        stalker.setFogging(player);
        return stalker;
    }

    private static List<HerobrineApparitionEntity> stalkersAfter(GameTestHelper helper, ServerPlayer player)
    {
        return helper.getLevel().getEntitiesOfClass(HerobrineApparitionEntity.class, helper.getBounds().inflate(16.0D), apparition -> apparition.getMode() == HerobrineApparitionEntity.Mode.STALK && apparition.getTargetPlayer() == player);
    }

    /** A pillar of cobblestone on the floor at x and z, this many blocks high: gives its top block. */
    private static BlockPos pillar(GameTestHelper helper, int x, int z, int height)
    {
        for (int y = 1; y <= height; y++) {helper.setBlock(x, y, z, Blocks.COBBLESTONE);}
        return helper.absolutePos(new BlockPos(x, height, z));
    }

    /** Cobblestone walls 2 high around x and z, and a roof: the blocks of this cell. */
    private static List<BlockPos> cell(GameTestHelper helper, int x, int z)
    {
        List<BlockPos> blocks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                for (int y = 1; y <= 3; y++)
                {
                    if (dx == 0 && dz == 0 && y < 3) {continue;}
                    blocks.add(place(helper, x + dx, y, z + dz, Blocks.COBBLESTONE));
                }
            }
        }
        return blocks;
    }

    /** A shield held up at once (a real one takes a quarter of a second, and mock players never tick). */
    private static void raiseShield(ServerPlayer player)
    {
        ItemStack shield = new ItemStack(Items.SHIELD);
        BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
        shield.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.0F, blocks.disableCooldownScale(), blocks.damageReductions(), blocks.itemDamage(), blocks.bypassedBy(), blocks.blockSound(), blocks.disableSound()));
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        player.startUsingItem(InteractionHand.OFF_HAND);
    }

    /** The cobwebs a stalker put down on and around the floor of the test (the test area is cleared afterwards, not what lies around it). */
    private static void removeCobwebs(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        AABB around = helper.getBounds().inflate(24.0D);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(around.minX, around.minY, around.minZ), BlockPos.containing(around.maxX, around.minY + 4.0D, around.maxZ)))
        {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) {level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);}
        }
    }

    private static void turnTowards(ServerPlayer player, Entity entity)
    {
        float yaw = Facing.yawTowards(entity.getX() - player.getX(), entity.getZ() - player.getZ());
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    //helpers
    /** A floor at y = 0, from..to on x and z. Only the chunks of the 8x8 test area are kept ticking: those of the floor around are too, so that what stands there moves. */
    private static void floor(GameTestHelper helper, int from, int to, Block block)
    {
        for (int x = from; x <= to; x++) {for (int z = from; z <= to; z++) {helper.setBlock(x, 0, z, block);}}
        BlockPos corner = helper.absolutePos(new BlockPos(from, 0, from)), otherCorner = helper.absolutePos(new BlockPos(to, 0, to));
        for (int chunkX = SectionPos.blockToSectionCoord(Math.min(corner.getX(), otherCorner.getX())); chunkX <= SectionPos.blockToSectionCoord(Math.max(corner.getX(), otherCorner.getX())); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), otherCorner.getZ())); chunkZ <= SectionPos.blockToSectionCoord(Math.max(corner.getZ(), otherCorner.getZ())); chunkZ++) {helper.getLevel().setChunkForced(chunkX, chunkZ, true);}
        }
    }

    private static ServerPlayer player(GameTestHelper helper, int x, int y, int z, float yaw)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        teleport(helper, player, x, y, z, yaw);
        return player;
    }

    /**
     * Like makeMockServerPlayerInLevel, but in survival: those always count as players in creative. Only in tests of their own environment (see SURVIVAL_PLAYERS).
     * They can be hurt at once: their client says right away that it loaded the world (no player can be hurt before that, or for 3 seconds).
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, int x, int y, int z, float yaw)
    {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation())
        {
            @Override public GameType gameMode() {return GameType.SURVIVAL;}
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        teleport(helper, player, x, y, z, yaw);
        return player;
    }

    /** Sets this block of the test area, and gives its position in the world. */
    private static BlockPos place(GameTestHelper helper, int x, int y, int z, BlockState state)
    {
        BlockPos pos = new BlockPos(x, y, z);
        helper.setBlock(pos, state);
        return helper.absolutePos(pos);
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z, Block block) {return place(helper, x, y, z, block.defaultBlockState());}

    private static boolean hasAdvancement(ServerPlayer player, String name)
    {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(DontFollowTheMap.id(name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void teleport(GameTestHelper helper, ServerPlayer player, int x, int y, int z, float yaw)
    {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        player.setYHeadRot(yaw);
    }

    private static void makeHome(ServerLevel level, ServerPlayer player, BlockPos home)
    {
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(level.dimension(), home), 0.0F, 0.0F), true), false);
        level.getChunkAt(home).setInhabitedTime(100000L);
    }

    private static void removePlayer(ServerPlayer player) {player.level().getServer().getPlayerList().remove(player);}

    /** Takes down the barriers around the 8x8 test area: they stop whatever walks out of it, and block the view. */
    private static void removeBarriers(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        AABB bounds = helper.getBounds();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX - 1.0D, bounds.minY - 1.0D, bounds.minZ - 1.0D), BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)))
        {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);}
        }
    }

    /** Runs a command as this player, with operator rights (mock players all share the same name: they can't be targeted by name). */
    private static void runCommandAs(GameTestHelper helper, ServerPlayer player, String command)
    {
        MinecraftServer server = helper.getLevel().getServer();
        try {server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withEntity(player).withSuppressedOutput());}
        catch (CommandSyntaxException e) {helper.fail("/" + command + " failed: " + e.getMessage());}
    }
}
