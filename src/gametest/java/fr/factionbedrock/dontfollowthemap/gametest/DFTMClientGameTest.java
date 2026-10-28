package fr.factionbedrock.dontfollowthemap.gametest;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

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

            //Herobrine, and a possessed sheep next to a normal one: they must look exactly the same
            server.runCommand("summon dontfollowthemap:herobrine 0.5 -60 4.5 {NoAI:1b,Rotation:[180f,0f]}");
            server.runCommand("summon minecraft:sheep 3.5 -60 6.5 {NoAI:1b,Color:14b,Rotation:[150f,0f]}");
            server.runCommand("summon dontfollowthemap:possessed_sheep -2.5 -60 6.5 {NoAI:1b,Color:14b,Rotation:[210f,0f]}");
            singleplayer.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("dftm_herobrine_day");
            //his eyes glow in the dark, like the spider's
            server.runCommand("summon minecraft:spider 5.5 -60 7.5 {NoAI:1b,Rotation:[180f,0f]}");
            server.runCommand("time set midnight");
            context.waitTicks(20);
            context.takeScreenshot("dftm_herobrine_night");
            //removed, not killed: killing Herobrine would count as defeating him
            server.runOnServer(minecraftServer -> minecraftServer.overworld().getAllEntities().forEach(entity -> {if (!(entity instanceof ServerPlayer)) {entity.discard();}}));

            //an apparition watching from afar
            server.runCommand("time set noon");
            server.runOnServer(minecraftServer ->
            {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                HerobrineApparitionEntity.spawn(minecraftServer.overworld(), new Vec3(2.5D, -60.0D, 18.5D), player, HerobrineApparitionEntity.Mode.WATCH, 600);
            });
            context.waitTicks(15);
            context.takeScreenshot("dftm_apparition");

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
}
