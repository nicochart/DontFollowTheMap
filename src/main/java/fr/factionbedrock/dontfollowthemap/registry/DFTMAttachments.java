package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.TorchSwaps;
import fr.factionbedrock.dontfollowthemap.haunting.WorldPeace;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class DFTMAttachments
{
    /** On players. */
    public static final AttachmentType<HauntingData> HAUNTING = AttachmentRegistry.create(DontFollowTheMap.id("haunting"), builder -> builder.persistent(HauntingData.CODEC).copyOnDeath().initializer(HauntingData::new));
    /** On server levels: torches that were turned into redstone torches and must be put back. */
    public static final AttachmentType<TorchSwaps> TORCH_SWAPS = AttachmentRegistry.create(DontFollowTheMap.id("torch_swaps"), builder -> builder.persistent(TorchSwaps.CODEC).initializer(TorchSwaps::new));
    /** On the overworld: Herobrine's defeats, which bring peace to every player. */
    public static final AttachmentType<WorldPeace> WORLD_PEACE = AttachmentRegistry.create(DontFollowTheMap.id("world_peace"), builder -> builder.persistent(WorldPeace.CODEC));
    /** On the overworld: the seal shrines, built on demand. */
    public static final AttachmentType<SealShrines> SEAL_SHRINES = AttachmentRegistry.create(DontFollowTheMap.id("seal_shrines"), builder -> builder.persistent(SealShrines.CODEC).initializer(SealShrines::new));

    public static void load() {}
}
