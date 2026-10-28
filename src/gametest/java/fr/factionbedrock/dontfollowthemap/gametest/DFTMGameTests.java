package fr.factionbedrock.dontfollowthemap.gametest;

import fr.factionbedrock.dontfollowthemap.block.SealBlock;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.PossessedSheepEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.haunting.GraveDigger;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.haunting.TorchSwaps;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMItems;
import fr.factionbedrock.dontfollowthemap.world.SealPiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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

    //possessed sheep
    @GameTest(maxTicks = 800, skyAccess = true, padding = 16)
    public void possessedSheepStaresBitesAndLetsGo(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, -14, 21, Blocks.GRASS_BLOCK);
        ServerPlayer player = player(helper, 1, 1, 1, 0.0F);
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 1, 5));
        sheep.setColor(DyeColor.RED);
        PossessedSheepEntity possessed = PossessedSheepEntity.possess(sheep, player);
        helper.assertTrue(possessed != null && sheep.isRemoved(), "The sheep must be replaced by a possessed one");
        helper.assertTrue(possessed.getColor() == DyeColor.RED && possessed.isStaring(), "The possessed sheep must keep its wool and start by staring");
        teleport(helper, player, 5, 1, 4, 0.0F);
        helper.runAfterDelay(40, () -> helper.assertTrue(possessed.isRemoved() || !possessed.isStaring(), "The sheep must charge when the player comes close"));
        helper.succeedWhen(() ->
        {
            helper.assertTrue(possessed.isRemoved(), "The possession must end");
            List<Sheep> released = level.getEntitiesOfClass(Sheep.class, helper.getBounds().inflate(24.0D), candidate -> candidate.getType() == EntityTypes.SHEEP && candidate.getColor() == DyeColor.RED);
            helper.assertTrue(!released.isEmpty(), "A normal red sheep must be left");
            released.forEach(Sheep::discard);
            removePlayer(player);
        });
    }

    //thief
    @GameTest(maxTicks = 200)
    public void unseenStealsTheDropsAndGivesThemBackWhenCaught(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        floor(helper, 0, 7, Blocks.STONE);
        ServerPlayer player = player(helper, 4, 1, 0, 180.0F);
        //the test area is walled with barriers: the thief comes from inside it
        ItemEntity diamond = helper.spawnItem(Items.DIAMOND, new BlockPos(6, 1, 6));
        UnseenEntity unseen = UnseenEntity.spawn(level, helper.absolutePos(new BlockPos(1, 1, 6)), player, List.of(diamond));
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    helper.assertTrue(diamond.isRemoved(), "The diamond must be taken");
                    helper.assertTrue(HauntingData.get(player).stolen.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "The stolen diamond must be kept in the player's data");
                })
                .thenExecute(() ->
                {
                    unseen.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
                    helper.assertTrue(unseen.isRemoved(), "Caught, the thief must flee");
                    helper.assertTrue(HauntingData.get(player).stolen.isEmpty(), "Caught, the thief must give back what it took");
                    helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4.0D), item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "The diamond must be dropped back");
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

    //seal and boss
    @GameTest(maxTicks = 20, skyAccess = true, padding = 12)
    public void sealShrineIsBuilt(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 3, 0));
        for (int x = 0; x < SealPiece.SIZE; x++) {for (int z = 0; z < SealPiece.SIZE; z++) {level.setBlock(origin.offset(x, -3, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);}}
        SealPiece piece = new SealPiece(origin.getX(), origin.getY(), origin.getZ(), Direction.NORTH);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), BoundingBox.infinite(), ChunkPos.containing(origin), origin);

        BlockState seal = level.getBlockState(piece.getSealPos());
        helper.assertTrue(seal.is(DFTMBlocks.SEAL) && seal.getValue(SealBlock.STATE) == SealBlock.State.SEALED, "The seal must stand in the middle of the shrine");
        BoundingBox box = piece.getBoundingBox();
        List<BlockPos> area = BlockPos.betweenClosedStream(box.minX(), box.minY() - 2, box.minZ(), box.maxX(), box.maxY(), box.maxZ()).map(BlockPos::immutable).toList();
        helper.assertTrue(area.stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.CHEST)), "Something must be buried under the seal");
        helper.assertTrue(area.stream().filter(pos -> level.getBlockState(pos).is(Blocks.SOUL_LANTERN)).count() == 4, "The four pillars must hold a soul lantern");
        boolean warning = area.stream().anyMatch(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.isWaxed()
                && sign.getText(SignTextSlot.FRONT).getMessages(false).get(1).getContents() instanceof TranslatableContents text && text.getKey().equals("sign.dontfollowthemap.shrine.1"));
        helper.assertTrue(warning, "The shrine must have its warning sign");
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
        ItemStack map = SealMaps.create(level, seal.getX(), seal.getZ());
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
}
