package fr.factionbedrock.dontfollowthemap.gametest;

import fr.factionbedrock.dontfollowthemap.client.HerobrineSounds;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

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
            context.waitTicks(15);
            context.takeScreenshot("dftm_apparition");
            context.runOnClient(client -> check(HerobrineSounds.isAmbienceOn(), "Close to him, the player must hear his ambience"));
            discardEverythingButPlayers(server);

            //his ambience from afar: not heard while he stands far behind the player, heard as soon as they look at him
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(0.5D, -60.0D, -39.5D), player, HerobrineApparitionEntity.Mode.WATCH, 600);
            });
            context.waitTicks(15);
            context.runOnClient(client -> check(!HerobrineSounds.isAmbienceOn(), "Far behind the player, he must not be heard"));
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            context.waitTicks(10);
            context.runOnClient(client -> check(HerobrineSounds.isAmbienceOn(), "The player looking at him from afar must hear his ambience"));
            discardEverythingButPlayers(server);
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitTicks(15);

            //a stalk: he waits right behind the player while the fog slowly thickens, up to the boss fight one
            server.runOnServer(minecraftServer ->
            {
                if (!HauntEvents.stalk(minecraftServer.getPlayerList().getPlayers().getFirst())) {throw new AssertionError("The stalk must find room behind the player");}
            });
            context.waitTicks(20);
            context.takeScreenshot("dftm_stalk_start");
            context.runOnClient(client -> check(HerobrineSounds.isAmbienceOn(), "Close behind the player, he must be heard"));
            context.waitTicks(20 * 10);
            context.takeScreenshot("dftm_stalk_thickening");
            context.waitTicks(20 * 10);
            context.takeScreenshot("dftm_stalk_full");
            //turning around: there he is
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            context.waitTicks(5);
            context.takeScreenshot("dftm_stalk_turned_around");
            discardEverythingButPlayers(server);
            //the fog lifts once he is gone
            context.waitTicks(20 * 5);

            //an imitator at the crafting table: a default skin, the player being alone; then wearing the player's own skin and name
            server.runCommand("setblock 1 -60 5 minecraft:crafting_table");
            server.runCommand("setblock -1 -60 5 minecraft:chest[facing=north]");
            server.runCommand("tp @a 0.5 -60 0.5 0 15");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                ImitatorEntity.spawn(minecraftServer.overworld(), new BlockPos(0, -60, 5), player, List.of(new BlockPos(1, -60, 5), new BlockPos(-1, -60, 5))).setPunches(false);
            });
            context.waitTicks(30);
            context.takeScreenshot("dftm_imitator");
            server.runOnServer(minecraftServer -> imitator(minecraftServer).disguiseAs(minecraftServer.getPlayerList().getPlayers().getFirst()));
            context.waitTicks(10);
            context.takeScreenshot("dftm_imitator_disguised");
            //hit: it stares, then shows its real face for an instant
            server.runOnServer(minecraftServer ->
            {
                ServerLevel level = minecraftServer.overworld();
                imitator(minecraftServer).hurtServer(level, level.damageSources().playerAttack(minecraftServer.getPlayerList().getPlayers().getFirst()), 1.0F);
            });
            context.waitTicks(ImitatorEntity.STARE_TICKS + 3);
            context.takeScreenshot("dftm_imitator_unmasked");
            context.waitTicks(20);
            server.runCommand("setblock 1 -60 5 minecraft:air");
            server.runCommand("setblock -1 -60 5 minecraft:air");
            discardEverythingButPlayers(server);

            //the shrine, seen from above
            server.runCommand("place structure dontfollowthemap:seal 0 -60 40");
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
                player.setItemInHand(InteractionHand.MAIN_HAND, SealMaps.create(minecraftServer.overworld(), 7, 39));
            });
            server.runCommand("tp @a 0.5 -60 0.5 0 60");
            context.waitTicks(40);
            context.takeScreenshot("dftm_seal_map");
        }
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

    /** Removed, not killed: killing Herobrine would count as defeating him. */
    private static void discardEverythingButPlayers(TestServerContext server)
    {
        server.runOnServer(minecraftServer -> minecraftServer.overworld().getAllEntities().forEach(entity -> {if (!(entity instanceof ServerPlayer)) {entity.discard();}}));
    }
}
