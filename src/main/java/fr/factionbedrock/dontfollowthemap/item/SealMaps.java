package fr.factionbedrock.dontfollowthemap.item;

import fr.factionbedrock.dontfollowthemap.haunting.DelayedTasks;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The map Herobrine leaves behind: a vanilla explorer map leading to the seal shrine (see {@link SealShrines}).
 * It remembers which shrine: once that one has fallen into ruins, the map burns as soon as it leaves a container or an item frame.
 */
public class SealMaps
{
    private static final String MARKER = "dontfollowthemap_seal_map";
    private static final String SHRINE = "dontfollowthemap_shrine";
    /** A worthless map thrown away catches fire after BURN_DELAY ticks, and is gone BURN_TICKS later. */
    private static final int BURN_DELAY = 40, BURN_TICKS = 40;

    public static boolean isSealMap(ItemStack stack)
    {
        if (!stack.is(Items.FILLED_MAP)) {return false;}
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER, false);
    }

    /** The shrine a seal map leads to (0 for the maps of the shrines that were part of the world, before they were built on demand). */
    public static int shrineId(ItemStack map)
    {
        CustomData data = map.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr(SHRINE, 0);
    }

    /** A seal map whose shrine has fallen into ruins. */
    public static boolean isWorthless(MinecraftServer server, ItemStack stack) {return isSealMap(stack) && !SealShrines.get(server).exists(shrineId(stack));}

    public static boolean hasSealMap(ServerPlayer player)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {if (isSealMap(player.getInventory().getItem(slot))) {return true;}}
        return isSealMap(player.containerMenu.getCarried());
    }

    /** A map to the seal shrine; if there is none, a place is picked for one, around origin. Null if no place could be found. */
    @Nullable public static ItemStack create(ServerLevel level, BlockPos origin)
    {
        ServerLevel overworld = level.getServer().overworld();
        SealShrines.Shrine shrine = SealShrines.activeOrPlan(overworld, level == overworld ? origin : overworld.getRespawnData().globalPos().pos());
        return shrine == null ? null : create(overworld, shrine);
    }

    public static ItemStack create(ServerLevel overworld, SealShrines.Shrine shrine)
    {
        BlockPos center = shrine.center();
        ItemStack map = MapItem.create(overworld, center.getX(), center.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(overworld, map);
        MapItemSavedData.addTargetDecoration(map, center, "+", MapDecorationTypes.RED_X);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        tag.putInt(SHRINE, shrine.id());
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.dontfollowthemap.seal_map"));
        map.set(DataComponents.RARITY, Rarity.RARE);
        map.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.dontfollowthemap.seal_map.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
        return map;
    }

    /** Worthless seal maps jump out of the player's inventory (and burn, see {@link #onItemLoaded}). Containers and item frames are not looked into. */
    public static void throwAwayWorthless(ServerPlayer player)
    {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!isWorthless(player.level().getServer(), stack)) {continue;}
            inventory.setItem(slot, ItemStack.EMPTY);
            ItemEntity item = player.createItemStackToDrop(stack, false, true);
            if (item != null) {player.level().addFreshEntity(item);}
        }
    }

    /** A worthless seal map lying around (thrown away, dropped by an item frame, or found there again): nobody can pick it up, and it soon burns away. */
    public static void onItemLoaded(ItemEntity item)
    {
        if (!(item.level() instanceof ServerLevel level) || !isWorthless(level.getServer(), item.getItem())) {return;}
        item.setNeverPickUp();
        DelayedTasks.schedule(BURN_DELAY, () -> {if (!item.isRemoved()) {item.igniteForTicks(BURN_TICKS + 20);}});
        DelayedTasks.schedule(BURN_DELAY + BURN_TICKS, () ->
        {
            if (item.isRemoved() || !(item.level() instanceof ServerLevel itemLevel)) {return;}
            itemLevel.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.15D, item.getZ(), 8, 0.1D, 0.05D, 0.1D, 0.01D);
            itemLevel.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.3F, 1.6F);
            item.discard();
        });
    }
}
