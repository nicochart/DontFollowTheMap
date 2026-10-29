package fr.factionbedrock.dontfollowthemap;

import fr.factionbedrock.dontfollowthemap.command.DFTMCommand;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingDirector;
import fr.factionbedrock.dontfollowthemap.network.FightStartingPayload;
import fr.factionbedrock.dontfollowthemap.network.HauntedPayload;
import fr.factionbedrock.dontfollowthemap.network.HerobrineAppearedPayload;
import fr.factionbedrock.dontfollowthemap.registry.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DontFollowTheMap implements ModInitializer
{
    public static final String MODID = "dontfollowthemap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override public void onInitialize()
    {
        DFTMConfig.load();
        DFTMSounds.load();
        DFTMBlocks.load();
        DFTMEntities.load();
        DFTMItems.load();
        DFTMStructures.load();
        DFTMAttachments.load();
        DFTMEntities.registerAttributes();
        HauntingDirector.registerEvents();
        DFTMCommand.register();
        PayloadTypeRegistry.clientboundPlay().register(FightStartingPayload.TYPE, FightStartingPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HauntedPayload.TYPE, HauntedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HerobrineAppearedPayload.TYPE, HerobrineAppearedPayload.CODEC);
    }

    public static Identifier id(String path) {return Identifier.fromNamespaceAndPath(MODID, path);}
}
