package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.world.SealPiece;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** The seal shrine is not part of the world generation: it is built on demand (see SealShrines), from this piece. */
public class DFTMStructures
{
    public static final StructurePieceType SEAL_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, DontFollowTheMap.id("seal"), (StructurePieceType.ContextlessType) SealPiece::new);

    public static void load() {}
}
