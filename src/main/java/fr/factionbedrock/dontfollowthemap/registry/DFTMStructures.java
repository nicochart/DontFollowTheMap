package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.world.SealPiece;
import fr.factionbedrock.dontfollowthemap.world.SealStructure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class DFTMStructures
{
    public static final StructureType<SealStructure> SEAL = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, DontFollowTheMap.id("seal"), () -> SealStructure.CODEC);
    public static final StructurePieceType SEAL_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, DontFollowTheMap.id("seal"), (StructurePieceType.ContextlessType) SealPiece::new);

    /** The structures a seal map can lead to (data/dontfollowthemap/tags/worldgen/structure/on_seal_maps.json). */
    public static final TagKey<Structure> ON_SEAL_MAPS = TagKey.create(Registries.STRUCTURE, DontFollowTheMap.id("on_seal_maps"));

    public static void load() {}
}
