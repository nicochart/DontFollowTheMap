package fr.factionbedrock.dontfollowthemap.util;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Advancements whose criterion is "minecraft:impossible" are granted from the code, for events no vanilla trigger covers. */
public class DFTMAdvancements
{
    public static final String CRITERION = "triggered";

    public static void award(ServerPlayer player, String name)
    {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(DontFollowTheMap.id(name));
        if (advancement != null) {player.getAdvancements().award(advancement, CRITERION);}
    }
}
