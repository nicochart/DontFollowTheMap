package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Finding the players Herobrine's creatures remember, and telling which ones they deal with. */
public class Players
{
    /** The player with this UUID while they are in this level (null once they logged out or went to another dimension). */
    @Nullable public static ServerPlayer inLevel(ServerLevel level, @Nullable UUID uuid)
    {
        ServerPlayer player = uuid == null ? null : level.getServer().getPlayerList().getPlayer(uuid);
        return player != null && player.level() == level ? player : null;
    }

    /** Alive and not a spectator: someone his creatures stay for. */
    public static boolean isInTheGame(Player player) {return player.isAlive() && !player.isSpectator();}

    /** In the game and not in creative: someone they attack. */
    public static boolean isHuntable(Player player) {return isInTheGame(player) && !player.isCreative();}
}
