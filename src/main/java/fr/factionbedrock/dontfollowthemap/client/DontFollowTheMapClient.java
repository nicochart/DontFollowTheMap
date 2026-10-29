package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.network.FightStartingPayload;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;

public class DontFollowTheMapClient implements ClientModInitializer
{
    @Override public void onInitializeClient()
    {
        EntityRendererRegistry.register(DFTMEntities.APPARITION, HerobrineRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.HEROBRINE, HerobrineRenderer::new);
        //exactly the vanilla animals: same models, same textures, same wool colors, variants and saddles
        EntityRendererRegistry.register(DFTMEntities.POSSESSED_SHEEP, SheepRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.POSSESSED_COW, CowRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.POSSESSED_PIG, PigRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.POSSESSED_CHICKEN, ChickenRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.UNSEEN, NoopRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.IMITATOR, ImitatorRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(StalkFog::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HerobrineSounds::tick);
        ClientPlayNetworking.registerGlobalReceiver(FightStartingPayload.TYPE, (payload, context) -> HerobrineSounds.onFightStarting(payload.seal()));
    }
}
