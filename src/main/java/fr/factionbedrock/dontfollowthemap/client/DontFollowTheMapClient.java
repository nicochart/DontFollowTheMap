package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;

public class DontFollowTheMapClient implements ClientModInitializer
{
    @Override public void onInitializeClient()
    {
        EntityRendererRegistry.register(DFTMEntities.APPARITION, HerobrineRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.HEROBRINE, HerobrineRenderer::new);
        //exactly the vanilla sheep: same model, same texture, same wool colors
        EntityRendererRegistry.register(DFTMEntities.POSSESSED_SHEEP, SheepRenderer::new);
        EntityRendererRegistry.register(DFTMEntities.UNSEEN, NoopRenderer::new);
    }
}
