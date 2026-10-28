package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.TorchSwaps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class DFTMAttachments
{
    /** On players. */
    public static final AttachmentType<HauntingData> HAUNTING = AttachmentRegistry.create(DontFollowTheMap.id("haunting"), builder -> builder.persistent(HauntingData.CODEC).copyOnDeath().initializer(HauntingData::new));
    /** On server levels: torches that were turned into redstone torches and must be put back. */
    public static final AttachmentType<TorchSwaps> TORCH_SWAPS = AttachmentRegistry.create(DontFollowTheMap.id("torch_swaps"), builder -> builder.persistent(TorchSwaps.CODEC).initializer(TorchSwaps::new));

    public static void load() {}
}
