package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.function.Function;

public class DFTMItems
{
    public static final Item SEAL = register("seal", props -> new BlockItem(DFTMBlocks.SEAL, props.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
    public static final Item SEAL_FRAGMENT = register("seal_fragment", props -> new Item(props.rarity(Rarity.EPIC).fireResistant().stacksTo(16)
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.dontfollowthemap.seal_fragment.lore"))))));
    public static final Item HEROBRINE_SPAWN_EGG = register("herobrine_spawn_egg", props -> new SpawnEggItem(props.spawnEgg(DFTMEntities.HEROBRINE).rarity(Rarity.EPIC)));
    public static final Item POSSESSED_SHEEP_SPAWN_EGG = register("possessed_sheep_spawn_egg", props -> new SpawnEggItem(props.spawnEgg(DFTMEntities.POSSESSED_SHEEP)));

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DontFollowTheMap.id("main"), FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dontfollowthemap"))
            .icon(SEAL_FRAGMENT::getDefaultInstance)
            .displayItems((parameters, output) ->
            {
                output.accept(SEAL);
                output.accept(SEAL_FRAGMENT);
                output.accept(HEROBRINE_SPAWN_EGG);
                output.accept(POSSESSED_SHEEP_SPAWN_EGG);
            })
            .build());

    private static Item register(String name, Function<Item.Properties, Item> factory)
    {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DontFollowTheMap.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }

    public static void load() {}
}
