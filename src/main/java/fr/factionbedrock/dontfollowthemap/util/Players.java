package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Finding the players Herobrine's creatures remember, telling which ones they deal with, and whether a shield stands in their way. */
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

    /** Whether the target holds up a shield (or anything that blocks) that would stop a blow of this attacker, from the side it stands on. */
    public static boolean isShieldedFrom(LivingEntity target, LivingEntity attacker)
    {
        ItemStack blocking = target.getItemBlockingWith();
        BlocksAttacks blocks = blocking == null ? null : blocking.get(DataComponents.BLOCKS_ATTACKS);
        if (blocks == null) {return false;}
        //as the game reckons it: the angle between where they look and the attacker
        Vec3 view = Vec3.directionFromRotation(0.0F, target.getYHeadRot());
        Vec3 towards = attacker.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        return blocks.resolveBlockedDamage(attacker.damageSources().mobAttack(attacker), 1.0F, Math.acos(Mth.clamp(towards.dot(view), -1.0D, 1.0D))) > 0.0F;
    }
}
