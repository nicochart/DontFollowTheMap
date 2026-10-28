package fr.factionbedrock.dontfollowthemap.item;

import fr.factionbedrock.dontfollowthemap.registry.DFTMStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/** The map Herobrine leaves behind: a vanilla explorer map leading to the nearest seal shrine. */
public class SealMaps
{
    private static final String MARKER = "dontfollowthemap_seal_map";
    private static final int SEARCH_RADIUS_CHUNKS = 100;

    public static boolean isSealMap(ItemStack stack)
    {
        if (!stack.is(Items.FILLED_MAP)) {return false;}
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER, false);
    }

    public static boolean hasSealMap(ServerPlayer player)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {if (isSealMap(player.getInventory().getItem(slot))) {return true;}}
        return isSealMap(player.containerMenu.getCarried());
    }

    /** @return null if no shrine could be found around (or the world has none, like a superflat world without structures). */
    @Nullable public static ItemStack create(ServerLevel level, BlockPos origin)
    {
        BlockPos shrine = level.findNearestMapStructure(DFTMStructures.ON_SEAL_MAPS, origin, SEARCH_RADIUS_CHUNKS, false);
        return shrine == null ? null : create(level, shrine.getX(), shrine.getZ());
    }

    public static ItemStack create(ServerLevel level, int shrineX, int shrineZ)
    {
        ItemStack map = MapItem.create(level, shrineX, shrineZ, (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(shrineX, 0, shrineZ), "+", MapDecorationTypes.RED_X);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.dontfollowthemap.seal_map"));
        map.set(DataComponents.RARITY, Rarity.RARE);
        map.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.dontfollowthemap.seal_map.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
        return map;
    }
}
