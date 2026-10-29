package fr.factionbedrock.dontfollowthemap.haunting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Herobrine's defeats on the server. Each one brings peace to every player: those who fought him, the others,
 * and those who come back later (each player remembers how many defeats they got the peace of). Saved with the overworld.
 */
public record WorldPeace(int defeats, long defeatedAt)
{
    public static final Codec<WorldPeace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("defeats", 0).forGetter(WorldPeace::defeats),
            Codec.LONG.optionalFieldOf("defeated_at", 0L).forGetter(WorldPeace::defeatedAt)
    ).apply(instance, WorldPeace::new));

    public static WorldPeace get(MinecraftServer server) {return server.overworld().getAttachedOrElse(DFTMAttachments.WORLD_PEACE, new WorldPeace(0, 0L));}

    public static WorldPeace onHerobrineDefeated(MinecraftServer server, long now)
    {
        WorldPeace peace = new WorldPeace(get(server).defeats + 1, now);
        server.overworld().setAttached(DFTMAttachments.WORLD_PEACE, peace);
        return peace;
    }

    /** Brings this player the peace of the defeats they missed (they were elsewhere, or not connected). Checked every second. */
    public static void catchUp(ServerPlayer player)
    {
        MinecraftServer server = player.level().getServer();
        WorldPeace peace = get(server);
        HauntingData data = HauntingData.get(player);
        if (data.worldDefeats >= peace.defeats) {return;}
        boolean haunted = data.stage(HauntingDirector.now(server)) != HauntingData.QUIET;
        data.makePeace(peace.defeatedAt);
        data.worldDefeats = peace.defeats;
        //a newcomer doesn't need to know
        if (haunted) {player.sendSystemMessage(Component.translatable("message.dontfollowthemap.defeated").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));}
    }
}
