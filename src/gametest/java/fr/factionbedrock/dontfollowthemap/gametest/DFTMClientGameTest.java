package fr.factionbedrock.dontfollowthemap.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.factionbedrock.dontfollowthemap.client.ClientHaunting;
import fr.factionbedrock.dontfollowthemap.client.FakeHitboxes;
import fr.factionbedrock.dontfollowthemap.client.HauntedScreen;
import fr.factionbedrock.dontfollowthemap.client.HerobrineFog;
import fr.factionbedrock.dontfollowthemap.client.HerobrineSounds;
import fr.factionbedrock.dontfollowthemap.client.ScrambledCoordinates;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Client game test: ./gradlew runClientGameTest
 * Renders everything the mod adds and saves screenshots into build/run/clientGameTest/screenshots.
 */
public class DFTMClientGameTest implements FabricClientGameTest
{
    @Override public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            TestServerContext server = singleplayer.getServer();
            server.runCommand("difficulty normal");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");

            //Herobrine
            server.runCommand("summon dontfollowthemap:herobrine 0.5 -60 4.5 {NoAI:1b,Rotation:[180f,0f]}");
            singleplayer.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("dftm_herobrine_day");
            context.runOnClient(client -> check(HerobrineSounds.isFightMusicOn(), "The fight music must play near Herobrine"));
            //his eyes glow in the dark, like the spider's
            server.runCommand("summon minecraft:spider 5.5 -60 7.5 {NoAI:1b,Rotation:[180f,0f]}");
            server.runCommand("time set midnight");
            context.waitTicks(20);
            context.takeScreenshot("dftm_herobrine_night");
            discardEverythingButPlayers(server);
            context.waitTicks(15);
            context.runOnClient(client -> check(!HerobrineSounds.isFightMusicOn(), "The fight music must stop once he is gone"));

            //each plain animal (left) next to its possessed twin (right): they must look exactly the same, wool, variant and saddle included
            server.runCommand("time set noon");
            summonAnimals(server, 0.9D, "plain");
            summonAnimals(server, -0.9D, "possess");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                minecraftServer.overworld().getEntitiesOfClass(Animal.class, new AABB(-9.0D, -61.0D, 7.0D, 9.0D, -57.0D, 10.0D), animal -> animal.entityTags().contains("possess"))
                        .forEach(animal -> PossessableAnimals.possess(animal, player));
            });
            server.runCommand("tp @a 0.5 -60 0.5 0 10");
            context.waitTicks(20);
            context.takeScreenshot("dftm_possessed_animals");
            discardEverythingButPlayers(server);

            //the trigger command, with a real player: first the given animal type, then whatever animal is left around
            server.runCommand("summon minecraft:cow 3.5 -60 6.5");
            server.runCommand("dontfollowthemap trigger possession minecraft:cow @p");
            server.runCommand("summon minecraft:pig -2.5 -60 6.5");
            server.runCommand("execute as @p run dontfollowthemap trigger possession");
            server.runOnServer(minecraftServer ->
            {
                ServerLevel level = minecraftServer.overworld();
                if (level.getEntities(DFTMEntities.POSSESSED_COW, entity -> true).size() != 1 || level.getEntities(DFTMEntities.POSSESSED_PIG, entity -> true).size() != 1)
                {
                    throw new AssertionError("/dontfollowthemap trigger possession must have possessed the cow, then the pig");
                }
            });
            discardEverythingButPlayers(server);

            //an apparition watching from afar
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(2.5D, -60.0D, 18.5D), player, HerobrineApparitionEntity.Mode.WATCH, 600);
            });
            context.waitTicks(10);
            context.runOnClient(client -> check(HauntedScreen.strength() > 0.0F && HauntedScreen.strength() < 0.5F, "The screen effect must come in gradually (" + HauntedScreen.strength() + ")"));
            context.waitTicks(35);
            context.takeScreenshot("dftm_apparition");
            context.runOnClient(client ->
            {
                check(HerobrineSounds.isAmbienceOn(), "Close to him, the player must hear his ambience");
                check(HerobrineSounds.presence() > 0.8F, "Close to him and looking at him, the player must feel him fully (" + HerobrineSounds.presence() + ")");
                check(client.gameRenderer.getAppliedPostEffects().contains(HauntedScreen.EFFECT), "Seeing him, the screen must turn into an old film");
                check(ScrambledCoordinates.isActive() && !positionLines(client).getFirst().equals(realPosition(client)), "With him around, the debug screen must lie about where the player is");
                client.debugEntries.toggleDebugOverlay();
            });
            context.waitTicks(2);
            context.takeScreenshot("dftm_debug_coordinates");
            context.runOnClient(client -> client.debugEntries.toggleDebugOverlay());
            discardEverythingButPlayers(server);

            //from afar: neither heard nor seen while he stands far behind the player; heard as soon as they look at him,
            //but seen only right where they look, not in the corner of their eye; looking away is enough for the screen effect to go
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, -39.5D), player, HerobrineApparitionEntity.Mode.WATCH, 600);
            });
            context.waitTicks(15);
            context.runOnClient(client ->
            {
                check(!HerobrineSounds.isAmbienceOn(), "Far behind the player, he must not be heard");
                check(HauntedScreen.strength() > 0.0F && HauntedScreen.strength() < 0.9F, "Once he is not felt any more, the screen effect must fade out, not stop at once (" + HauntedScreen.strength() + ")");
            });
            context.waitTicks(50);
            context.runOnClient(client -> check(HauntedScreen.strength() == 0.0F, "Far behind the player, he must not turn the screen into an old film (" + HauntedScreen.strength() + ")"));
            //20 degrees away from him
            server.runCommand("tp @a 0.5 -60 0.5 160 0");
            context.waitTicks(10);
            context.runOnClient(client ->
            {
                check(HerobrineSounds.isAmbienceOn(), "The player looking at him from afar must hear his ambience");
                check(HauntedScreen.strength() == 0.0F, "From afar, he must not be seen in the corner of the eye (" + HauntedScreen.strength() + ")");
            });
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            context.waitTicks(10);
            context.runOnClient(client ->
            {
                check(HerobrineSounds.presence() > 0.3F && HerobrineSounds.presence() < 0.8F, "From afar, he must be felt more faintly (" + HerobrineSounds.presence() + ")");
                check(HauntedScreen.strength() > 0.0F, "Looking right at him from afar, the screen must turn into an old film");
            });
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitTicks(20);
            context.runOnClient(client -> check(HauntedScreen.strength() == 0.0F, "From afar, looking away from him must let the screen effect go (" + HauntedScreen.strength() + ")"));
            discardEverythingButPlayers(server);

            //close (just further than what makes him vanish): not seen while he stands behind the player, but in the corner of their eye already;
            //once seen, turning their back on him is not enough for the screen effect to go
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, -11.5D), player, HerobrineApparitionEntity.Mode.WATCH, 600);
            });
            context.waitTicks(15);
            context.runOnClient(client ->
            {
                check(HerobrineSounds.isAmbienceOn(), "Close behind the player, he must be heard");
                check(HauntedScreen.strength() == 0.0F, "Close behind the player but unseen, he must not turn the screen into an old film (" + HauntedScreen.strength() + ")");
            });
            //40 degrees away from him
            server.runCommand("tp @a 0.5 -60 0.5 140 0");
            context.waitTicks(10);
            context.runOnClient(client -> check(HauntedScreen.strength() > 0.0F, "Close, he must be seen in the corner of the eye"));
            context.takeScreenshot("dftm_apparition_corner_of_the_eye");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitTicks(30);
            context.runOnClient(client -> check(HauntedScreen.strength() > 0.5F, "Close, turning away from him must not stop the screen effect (" + HauntedScreen.strength() + ")"));
            discardEverythingButPlayers(server);
            context.waitTicks(15);
            context.runOnClient(client -> check(!ScrambledCoordinates.isActive() && positionLines(client).getFirst().equals(realPosition(client)), "With nothing of his around, the debug screen must tell the truth"));

            //a sighting: while he watches from afar, the player's fog slowly thickens, as during a stalk; it lifts once he is gone
            server.runOnServer(minecraftServer ->
            {
                if (!HauntEvents.sighting(minecraftServer.getPlayerList().getPlayers().getFirst(), HauntingData.HUNTED, false)) {throw new AssertionError("The sighting must find room around the player");}
            });
            context.waitTicks(20 * 3);
            context.runOnClient(client -> check(HerobrineFog.amount() > 0.1F, "While he watches from afar, the fog must thicken (" + HerobrineFog.amount() + ")"));
            context.takeScreenshot("dftm_sighting_fog");
            discardEverythingButPlayers(server);
            context.waitTicks(20 * 5);
            context.runOnClient(client -> check(HerobrineFog.amount() == 0.0F, "Once he is gone, the fog must lift (" + HerobrineFog.amount() + ")"));

            //a stalk: right behind the player while the fog slowly thickens (up to the boss fight one), he creeps up on them and strikes, with the darkness
            //(they can't die of it here)
            server.runCommand("effect give @a minecraft:resistance 60 4 true");
            server.runOnServer(minecraftServer ->
            {
                if (!HauntEvents.stalk(minecraftServer.getPlayerList().getPlayers().getFirst())) {throw new AssertionError("The stalk must find room behind the player");}
            });
            context.waitTicks(20);
            context.takeScreenshot("dftm_stalk_start");
            context.runOnClient(client ->
            {
                check(HerobrineSounds.isAmbienceOn(), "Close behind the player, he must be heard");
                check(HauntedScreen.strength() == 0.0F, "Behind the player, the stalker must not turn the screen into an old film before they see him (" + HauntedScreen.strength() + ")");
            });
            server.runOnServer(minecraftServer -> check(!stalker(minecraftServer).isSeenByTarget(), "Behind the player, the stalker must know it is not seen (it creeps up)"));
            context.waitTicks(20 * 4);
            context.runOnClient(client -> check(client.player.hasEffect(MobEffects.DARKNESS), "Creeping up on the player while their back is turned, he must strike them, with the darkness"));
            context.takeScreenshot("dftm_stalk_struck");
            context.waitTicks(20 * 6);
            context.takeScreenshot("dftm_stalk_thickening");
            context.waitTicks(20 * 10);
            context.takeScreenshot("dftm_stalk_full");
            //turning around: he is somewhere there
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            context.waitTicks(5);
            context.takeScreenshot("dftm_stalk_turned_around");
            discardEverythingButPlayers(server);
            //right in front of the player: their client tells the server that it is seen (it runs at them then), and that it is not once they turn away
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, -11.5D), player, HerobrineApparitionEntity.Mode.STALK, 200).setFogging(player);
            });
            context.waitTicks(10);
            server.runOnServer(minecraftServer -> check(stalker(minecraftServer).isSeenByTarget(), "Right in front of the player, the stalker must be told it is seen"));
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitTicks(10);
            server.runOnServer(minecraftServer -> check(!stalker(minecraftServer).isSeenByTarget(), "Once the player turned away, the stalker must be told it is not seen any more"));
            discardEverythingButPlayers(server);
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            server.runCommand("effect clear @a");
            //the fog lifts once he is gone
            context.waitTicks(20 * 5);

            //walled in (in glass, to see him): he mines his way in, a pickaxe in hand, the glass cracking
            server.runCommand("effect give @a minecraft:resistance 60 4 true");
            server.runCommand("fill -1 -61 -1 1 -58 1 minecraft:glass hollow");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, 6.5D), player, HerobrineApparitionEntity.Mode.STALK, 20 * 60).setFogging(player);
            });
            context.waitFor(client -> clientStalker(client, stalker -> stalker.getMainHandItem().is(Items.DIAMOND_PICKAXE)), 20 * 15);
            context.waitTicks(4);
            context.takeScreenshot("dftm_herobrine_mining");
            discardEverythingButPlayers(server);
            server.runCommand("fill -1 -61 -1 1 -58 1 minecraft:air");
            server.runCommand("fill -1 -61 -1 1 -61 1 minecraft:grass_block");
            //up on a high pillar: he floats up to mine it from under the player's feet
            server.runCommand("fill 0 -60 0 0 -52 0 minecraft:cobblestone");
            //at the edge, looking down: he comes up along the pillar
            server.runCommand("tp @a 0.5 -51 0.9 0 85");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, 5.5D), player, HerobrineApparitionEntity.Mode.STALK, 20 * 60).setFogging(player);
            });
            context.waitFor(client -> clientStalker(client, stalker -> stalker.isNoGravity() && stalker.getMainHandItem().is(Items.DIAMOND_PICKAXE)), 20 * 15);
            context.waitTicks(4);
            context.takeScreenshot("dftm_herobrine_floating");
            discardEverythingButPlayers(server);
            server.runCommand("fill 0 -60 0 0 -52 0 minecraft:air");
            //running away from him: he stands in the way, a cobweb in his hand, and puts it down in front of himself
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity stalker = HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, -6.5D), player, HerobrineApparitionEntity.Mode.STALK, 20 * 60);
                stalker.setFogging(player);
                check(stalker.blinkAhead(minecraftServer.overworld(), player, new Vec3(0.0D, 0.0D, 1.0D), true), "There must be room for him ahead of the player");
            });
            context.waitFor(client -> clientStalker(client, stalker -> stalker.getMainHandItem().is(Items.COBWEB)), 20 * 5);
            context.waitTicks(6);
            context.takeScreenshot("dftm_herobrine_cobweb");
            discardEverythingButPlayers(server);
            server.runCommand("fill -2 -60 4 3 -59 10 minecraft:air replace minecraft:cobweb");
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            server.runCommand("effect clear @a");
            context.waitFor(client -> HauntedScreen.strength() == 0.0F, 20 * 10);

            //a sudden apparition, on what the player just killed: the screen jolts at once, then settles down
            context.runOnClient(client -> check(HauntedScreen.strength() == 0.0F, "With him gone for a while, the screen effect must be over"));
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                if (!HauntEvents.killApparition(player, player.position().add(0.0D, 0.0D, -4.0D))) {throw new AssertionError("He must appear on the corpse");}
            });
            context.waitTicks(2);
            context.runOnClient(client -> check(HauntedScreen.strength() > 0.9F, "A sudden apparition must jolt the screen at once (" + HauntedScreen.strength() + ")"));
            context.takeScreenshot("dftm_sudden_apparition");
            context.waitTicks(30);
            discardEverythingButPlayers(server);

            //an imitator at the crafting table: a default skin, the player being alone; then wearing the player's own skin and name
            //(the player in creative: it pays no attention to them)
            server.runCommand("gamemode creative @a");
            server.runCommand("setblock 1 -60 5 minecraft:crafting_table");
            server.runCommand("setblock -1 -60 5 minecraft:chest[facing=north]");
            server.runCommand("tp @a 0.5 -60 0.5 0 15");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                ImitatorEntity.spawn(minecraftServer.overworld(), new BlockPos(0, -60, 5), player, List.of(new BlockPos(1, -60, 5), new BlockPos(-1, -60, 5)));
            });
            context.waitTicks(30);
            context.takeScreenshot("dftm_imitator");
            server.runOnServer(minecraftServer -> imitator(minecraftServer).disguiseAs(minecraftServer.getPlayerList().getPlayers().getFirst()));
            context.waitTicks(10);
            context.takeScreenshot("dftm_imitator_disguised");
            //hit before it had a sword: it stares, then Herobrine comes out of it to strike back
            server.runOnServer(minecraftServer ->
            {
                ServerLevel level = minecraftServer.overworld();
                imitator(minecraftServer).hurtServer(level, level.damageSources().playerAttack(minecraftServer.getPlayerList().getPlayers().getFirst()), 1.0F);
            });
            context.waitTicks(ImitatorEntity.STARE_TICKS + 3);
            context.takeScreenshot("dftm_imitator_unmasked");
            context.waitTicks(20);
            discardEverythingButPlayers(server);
            //in survival, close to it: it makes itself a sword at the crafting table, and comes for the player with it
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 30 4 true");
            server.runCommand("tp @a 0.5 -60 2.5 0 15");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                ImitatorEntity.spawn(minecraftServer.overworld(), new BlockPos(0, -60, 5), player, List.of(new BlockPos(1, -60, 5), new BlockPos(-1, -60, 5)));
            });
            context.waitTicks(80);
            server.runOnServer(minecraftServer -> check(imitator(minecraftServer).isArmed(), "With the player close, the imitator must have made itself a sword"));
            context.takeScreenshot("dftm_imitator_armed");
            discardEverythingButPlayers(server);
            server.runCommand("effect clear @a");
            server.runCommand("setblock 1 -60 5 minecraft:air");
            server.runCommand("setblock -1 -60 5 minecraft:air");
            discardEverythingButPlayers(server);

            //a 2x1 tunnel dug southwards, walled up behind the player: the wall when they turn around, then his face once its top block is dug
            server.runCommand("fill -2 -61 -2 2 -57 14 minecraft:stone");
            server.runCommand("fill 0 -60 0 0 -59 12 minecraft:air");
            server.runCommand("tp @a 0.5 -60 11.5 0 0");
            server.runCommand("effect give @a minecraft:night_vision 60 0 true");
            context.waitTicks(5);
            server.runOnServer(minecraftServer ->
            {
                if (!HauntEvents.wallUpTunnel(minecraftServer.getPlayerList().getPlayers().getFirst(), Direction.SOUTH)) {throw new AssertionError("The tunnel must be walled up behind the player");}
            });
            context.waitTicks(15);
            context.runOnClient(client -> check(HerobrineSounds.isAmbienceOn(), "Behind his wall, close to the player, he must be heard"));
            server.runCommand("tp @a 0.5 -60 11.5 180 0");
            context.waitTicks(5);
            context.takeScreenshot("dftm_tunnel_walled_up");
            //the wall is 3 blocks behind the player
            server.runCommand("setblock 0 -59 8 minecraft:air");
            context.waitTicks(3);
            context.takeScreenshot("dftm_tunnel_top_block_dug");
            context.waitTicks(HerobrineApparitionEntity.REVEAL_TICKS + 5);
            server.runCommand("fill -2 -60 -2 2 -57 14 minecraft:air");
            server.runCommand("fill -2 -61 -2 2 -61 14 minecraft:grass_block");
            server.runCommand("effect clear @a minecraft:night_vision");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            discardEverythingButPlayers(server);

            //the shrine, built on demand (centered on its seal), seen from above
            server.runCommand("dontfollowthemap generate-seal 7 -60 39");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 7.5 -49 27.5 0 42");
            singleplayer.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("dftm_seal_shrine");
            server.runCommand("gamemode creative @a");

            //the map, in hand
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, SealMaps.create(minecraftServer.overworld(), player.blockPosition()));
            });
            server.runCommand("tp @a 0.5 -60 0.5 0 60");
            context.waitTicks(40);
            context.takeScreenshot("dftm_seal_map");

            //the seal breaks: the fight music starts with the summoning, before he is there
            BlockPos seal = new BlockPos(7, -60, 39);
            server.runOnServer(minecraftServer ->
            {
                ServerLevel level = minecraftServer.overworld();
                check(level.getBlockState(seal).is(DFTMBlocks.SEAL), "The seal must stand in the middle of the shrine");
                SealEvents.breakSeal(level, seal, minecraftServer.getPlayerList().getPlayers().getFirst(), SealMaps.create(level, seal));
            });
            context.waitTicks(10);
            server.runOnServer(minecraftServer -> check(SealEvents.findHerobrine(minecraftServer.overworld(), seal) == null, "Herobrine must not be there yet"));
            context.runOnClient(client -> check(HerobrineSounds.isFightMusicOn(), "The fight music must start as soon as the seal breaks"));
            context.waitTicks(SealEvents.SUMMON_DELAY);
            server.runOnServer(minecraftServer -> check(SealEvents.findHerobrine(minecraftServer.overworld(), seal) != null, "Herobrine must come out of the seal"));
            context.runOnClient(client -> check(HerobrineSounds.isFightMusicOn(), "The fight music must go on once he is there"));

            //defeated: the shrine stands as long as someone sees it, then falls into ruins, and the maps leading to it burn
            server.runOnServer(minecraftServer -> SealEvents.findHerobrine(minecraftServer.overworld(), seal).kill(minecraftServer.overworld()));
            context.waitTicks(HerobrineEntity.DEATH_DURATION + 20);
            server.runOnServer(minecraftServer -> check(minecraftServer.overworld().getBlockState(seal).is(DFTMBlocks.SEAL), "The shrine must stand while the player looks at it"));
            //far away, looking the other way
            server.runCommand("tp @a 0.5 -60 -60 180 0");
            context.waitTicks(25);
            server.runOnServer(minecraftServer ->
            {
                check(!minecraftServer.overworld().getBlockState(seal).is(DFTMBlocks.SEAL), "Once nobody sees it, the shrine must fall into ruins");
                check(!SealMaps.hasSealMap(minecraftServer.getPlayerList().getPlayers().getFirst()), "The map leading to it must jump out of the player's inventory");
            });
            context.waitTicks(40);
            context.takeScreenshot("dftm_seal_map_burning");
            context.waitTicks(60);
            server.runOnServer(minecraftServer -> check(minecraftServer.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-16.0D, -64.0D, -80.0D, 16.0D, -50.0D, -40.0D), item -> SealMaps.isSealMap(item.getItem())).isEmpty(), "The map must burn away"));
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 7.5 -49 27.5 0 42");
            singleplayer.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("dftm_seal_shrine_ruins");
            server.runCommand("gamemode creative @a");
            discardEverythingButPlayers(server);

            //no seal any more, until the next map (or the command) picks a place for a new one, in these plains
            server.runOnServer(minecraftServer ->
            {
                check(fails(minecraftServer, "dontfollowthemap locate-seal"), "/dontfollowthemap locate-seal must fail while no seal is active");
                check(!fails(minecraftServer, "dontfollowthemap generate-seal") && !fails(minecraftServer, "dontfollowthemap locate-seal"), "/dontfollowthemap generate-seal must pick a place for a new seal");
                SealShrines.Shrine planned = SealShrines.get(minecraftServer).active();
                BlockPos spawn = minecraftServer.overworld().getRespawnData().globalPos().pos();
                double distance = Math.sqrt(planned.center().distToLowCornerSqr(spawn.getX(), planned.center().getY(), spawn.getZ()));
                check(planned.stage() == SealShrines.Stage.PLANNED && distance >= 240.0D && distance <= 510.0D, "The new seal must rise 250 to 500 blocks away, once its place is loaded (" + (int) distance + ")");
                ItemStack map = SealMaps.create(minecraftServer.overworld(), spawn);
                check(map != null && SealMaps.shrineId(map) == planned.id(), "The maps must lead to it");
                check(!fails(minecraftServer, "dontfollowthemap generate-seal") && SealMaps.isWorthless(minecraftServer, map), "A shrine never built leaves nothing behind: its maps are worthless at once");
            });

            //a haunted player who shows the hitboxes (F3 + B) sees some that belong to nothing, besides the pig's
            server.runCommand("summon minecraft:pig 0.5 -60 6.5 {NoAI:1b}");
            server.runCommand("tp @a 0.5 -60 0.5 0 15");
            server.runCommand("gamemode survival @a");
            server.runCommand("dontfollowthemap stage @p 1");
            context.waitTicks(30);
            context.runOnClient(client ->
            {
                check(ClientHaunting.isHaunted(), "The server must tell the client that its player is haunted");
                client.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
            });
            context.waitTicks(10);
            context.runOnClient(client -> check(FakeHitboxes.count() > 0, "Some hitboxes must belong to nothing"));
            //the test screenshots are drawn apart, without the hitboxes: what the game shows is grabbed instead
            context.runOnClient(client -> grabFrame(client, "dftm_fake_hitboxes.png"));
            //one keeps behind the player
            server.runCommand("tp @a 0.5 -60 0.5 180 15");
            context.waitTicks(5);
            context.runOnClient(client -> grabFrame(client, "dftm_fake_hitboxes_behind.png"));
            context.waitTicks(5);
            context.runOnClient(client -> client.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES));
            server.runCommand("gamemode creative @a");
        }
    }

    /** The last picture the game drew, as the player saw it (screenshots/name in the game directory). */
    private static void grabFrame(Minecraft client, String name)
    {
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {});
    }

    /** The position lines the debug screen (F3) would show right now. */
    private static List<String> positionLines(Minecraft client)
    {
        List<String> lines = new ArrayList<>();
        new DebugEntryPosition().display(new DebugScreenDisplayer()
        {
            @Override public void addPriorityLine(String line) {lines.add(line);}
            @Override public void addLine(String line) {lines.add(line);}
            @Override public void addToGroup(Identifier group, Collection<String> groupLines) {lines.addAll(groupLines);}
            @Override public void addToGroup(Identifier group, String line) {lines.add(line);}
        }, client.level, null, null);
        return lines;
    }

    private static String realPosition(Minecraft client)
    {
        Entity camera = client.getCameraEntity();
        return String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", camera.getX(), camera.getY(), camera.getZ());
    }

    /** Runs a command as the server: whether it failed. */
    private static boolean fails(MinecraftServer server, String command)
    {
        try
        {
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
            return false;
        }
        catch (CommandSyntaxException e) {return true;}
    }

    /** A red sheep, a warm cow, a cold saddled pig and a cold chicken in a row 8 blocks ahead, facing the player, each shifted by offsetX. */
    private static void summonAnimals(TestServerContext server, double offsetX, String tag)
    {
        summon(server, "sheep", 6.0D + offsetX, tag, "Color:14b");
        summon(server, "cow", 2.0D + offsetX, tag, "variant:\"minecraft:warm\"");
        summon(server, "pig", -2.0D + offsetX, tag, "variant:\"minecraft:cold\",equipment:{saddle:{id:\"minecraft:saddle\",count:1}}");
        summon(server, "chicken", -6.0D + offsetX, tag, "variant:\"minecraft:cold\"");
    }

    private static void summon(TestServerContext server, String animal, double x, String tag, String data)
    {
        server.runCommand(String.format(Locale.ROOT, "summon minecraft:%s %.1f -60 8.5 {NoAI:1b,Rotation:[180f,0f],Tags:[\"%s\"],%s}", animal, x, tag, data));
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) {throw new AssertionError(message);}
    }

    private static ImitatorEntity imitator(MinecraftServer server)
    {
        return server.overworld().getEntitiesOfClass(ImitatorEntity.class, new AABB(-16.0D, -64.0D, -16.0D, 16.0D, -50.0D, 16.0D)).getFirst();
    }

    /** Whether this client sees a stalker like that. */
    private static boolean clientStalker(Minecraft client, Predicate<HerobrineApparitionEntity> like)
    {
        for (Entity entity : client.level.entitiesForRendering()) {if (entity instanceof HerobrineApparitionEntity apparition && like.test(apparition)) {return true;}}
        return false;
    }

    private static HerobrineApparitionEntity stalker(MinecraftServer server)
    {
        return server.overworld().getEntitiesOfClass(HerobrineApparitionEntity.class, new AABB(-32.0D, -64.0D, -32.0D, 32.0D, -50.0D, 32.0D), apparition -> apparition.getMode() == HerobrineApparitionEntity.Mode.STALK).getFirst();
    }

    /** Removed, not killed: killing Herobrine would count as defeating him. */
    private static void discardEverythingButPlayers(TestServerContext server)
    {
        server.runOnServer(minecraftServer -> minecraftServer.overworld().getAllEntities().forEach(entity -> {if (!(entity instanceof ServerPlayer)) {entity.discard();}}));
    }
}
