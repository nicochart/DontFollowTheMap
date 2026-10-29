package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.network.HauntedPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps each client told whether its player is haunted (see HauntedPayload): sent when it changes, and to every player who joins. */
public class HauntedSync
{
    private static final Map<UUID, Boolean> SENT = new HashMap<>();

    public static void update(ServerPlayer player, boolean haunted)
    {
        if (Boolean.valueOf(haunted).equals(SENT.get(player.getUUID())) || !ServerPlayNetworking.canSend(player, HauntedPayload.TYPE)) {return;}
        ServerPlayNetworking.send(player, new HauntedPayload(haunted));
        SENT.put(player.getUUID(), haunted);
    }

    /** Left: a new client will have to be told. */
    public static void forget(ServerPlayer player) {SENT.remove(player.getUUID());}

    public static void clear() {SENT.clear();}
}
