package fr.factionbedrock.dontfollowthemap.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingDirector;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.haunting.TorchSwaps;
import fr.factionbedrock.dontfollowthemap.haunting.TunnelWatcher;
import fr.factionbedrock.dontfollowthemap.haunting.WorldPeace;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMItems;
import fr.factionbedrock.dontfollowthemap.world.SealPiece;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Server game tests: ./gradlew runGameTest */
public class DFTMGameTests
{
    //progression and saving
    @GameTest
    public void hauntingDataProgressesAndSurvivesSaving(GameTestHelper helper)
    {
        HauntingData data = new HauntingData();
        helper.assertTrue(data.stage(0L) == HauntingData.QUIET, "A new player must not be haunted");
        data.setStage(HauntingData.HAUNTED, 0L);
        helper.assertTrue(data.stage(0L) == HauntingData.HAUNTED, "setStage(HAUNTED) must give the haunted stage");
        data.mapDeliveredAt = 1234L;
        data.addStolen(new ItemStack(Items.DIAMOND, 3));
        data.addStolen(new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(data.stolen.size() == 1 && data.stolen.get(0).getCount() == 5, "Stolen items must stack");
        data.removeStolen(new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(data.stolen.get(0).getCount() == 1, "Items taken back must be removed from the stolen ones");

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        Tag saved = HauntingData.CODEC.encodeStart(ops, data).getOrThrow();
        HauntingData loaded = HauntingData.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(loaded.hauntTicks == data.hauntTicks && loaded.mapDeliveredAt == 1234L && loaded.stolen.size() == 1 && loaded.stolen.get(0).is(Items.DIAMOND), "Haunting data must survive a save");

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
                    helper.assertTrue(unseen.isAlive() && !unseen.isFleeing(), "With one stack out of three, it must keep following the player");
                    //what falls next around the player
                    helper.spawnItem(Items.EMERALD, new BlockPos(6, 1, 2));
                    helper.spawnItem(Items.GOLD_INGOT, new BlockPos(2, 1, 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(unseen.isFleeing(), "With three stacks, it must run away"))
                .thenExecute(() ->
                {
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
        BlockPos first = helper.absolutePos(new BlockPos(3, 1, 6)), second = helper.absolutePos(new BlockPos(5, 1, 6));
        level.setBlock(first, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(second, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
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
        BlockPos table = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
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
        BlockPos barrel = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
        ImitatorEntity imitator = ImitatorEntity.spawn(level, helper.absolutePos(new BlockPos(4, 1, 5)), player, List.of(barrel));
        imitator.setPunches(false);
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
                    helper.assertTrue(!level.getEntitiesOfClass(HerobrineApparitionEntity.class, imitator.getBoundingBox().inflate(1.0D), apparition -> apparition.getMode() == HerobrineApparitionEntity.Mode.FLASH).isEmpty(), "Herobrine must show himself where it stood");
                })
                .thenExecute(() -> removePlayer(player))
                .thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void imitatorMayPunchThePlayerWhoComesTooClose(GameTestHelper helper)
    {
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 1, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        //what a real client says once the world is loaded: players can't be hurt before
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        ImitatorEntity imitator = ImitatorEntity.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 3)), player, List.of());
        imitator.setPunches(true);
        helper.succeedWhen(() ->
        {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "The imitator must punch the player");
            imitator.discard();
            removePlayer(player);
        });
    }

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
        BlockPos bottom = helper.absolutePos(new BlockPos(4, 1, 3)), feet = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(bottom, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bottom.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
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
        BlockPos seal = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(seal, DFTMBlocks.SEAL.defaultBlockState(), Block.UPDATE_ALL);
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

    @GameTest(maxTicks = 300, skyAccess = true, padding = 8)
    public void herobrineFightsThenFadesAway(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -4, 11, Blocks.STONE);
        BlockPos seal = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(seal, DFTMBlocks.SEAL.defaultBlockState().setValue(SealBlock.STATE, SealBlock.State.OPEN), Block.UPDATE_ALL);
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

    //helpers
    private static void floor(GameTestHelper helper, int from, int to, Block block)
    {
        for (int x = from; x <= to; x++) {for (int z = from; z <= to; z++) {helper.setBlock(x, 0, z, block);}}
    }

    private static ServerPlayer player(GameTestHelper helper, int x, int y, int z, float yaw)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        teleport(helper, player, x, y, z, yaw);
        return player;
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

    /** Runs a command as this player, with operator rights (mock players all share the same name: they can't be targeted by name). */
    private static void runCommandAs(GameTestHelper helper, ServerPlayer player, String command)
    {
        MinecraftServer server = helper.getLevel().getServer();
        try {server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withEntity(player).withSuppressedOutput());}
        catch (CommandSyntaxException e) {helper.fail("/" + command + " failed: " + e.getMessage());}
    }
}
