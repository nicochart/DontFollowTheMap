package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.block.SealBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class DFTMBlocks
{
    public static final ResourceKey<Block> SEAL_KEY = ResourceKey.create(Registries.BLOCK, DontFollowTheMap.id("seal"));

    //unbreakable and unmovable, like bedrock (withers and the dragon included: tags wither_immune and dragon_immune): the only way to deal with it is the map
    public static final SealBlock SEAL = Registry.register(BuiltInRegistries.BLOCK, SEAL_KEY, new SealBlock(BlockBehaviour.Properties.of().setId(SEAL_KEY)
            .mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).strength(-1.0F, 3600000.0F).noLootTable()
            .sound(SoundType.DEEPSLATE_TILES).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((state, level, pos, type) -> false)
            .lightLevel(state -> state.getValue(SealBlock.STATE) == SealBlock.State.OPEN ? 7 : state.getValue(SealBlock.STATE) == SealBlock.State.SEALED ? 3 : 0)));

    public static void load() {}
}
