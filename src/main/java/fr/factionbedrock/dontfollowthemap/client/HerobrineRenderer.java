package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Herobrine and his apparitions: the default (wide) Steve skin from the game itself, with glowing white eyes on top.
 * The eyes texture is the only image of the mod involved: four white pixels where Steve's eyes are.
 * (Not an AvatarRenderState: those are always drawn by the vanilla player renderer, which would ignore the eyes.)
 */
public class HerobrineRenderer extends HumanoidMobRenderer<Mob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>
{
    private static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final RenderType EYES = RenderTypes.eyes(DontFollowTheMap.id("textures/entity/herobrine_eyes.png"));

    public HerobrineRenderer(EntityRendererProvider.Context context)
    {
        //the player geometry (with its outer layer), animated like any humanoid
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new EyesLayer<>(this) {@Override public RenderType renderType() {return EYES;}});
    }

    @Override public HumanoidRenderState createRenderState() {return new HumanoidRenderState();}

    @Override public void extractRenderState(Mob entity, HumanoidRenderState state, float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        //no red flash while he fades away
        if (state.deathTime > 0.0F) {state.hasRedOverlay = false;}
    }

    @Override public Identifier getTextureLocation(HumanoidRenderState state) {return STEVE;}

    @Override protected float getFlipDegrees() {return 0.0F;}

    @Override protected @Nullable RenderType getRenderType(HumanoidRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing)
    {
        return state.deathTime > 0.0F && isBodyVisible ? RenderTypes.entityTranslucent(STEVE) : super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override protected int getModelTint(HumanoidRenderState state)
    {
        if (state.deathTime <= 0.0F) {return -1;}
        float alpha = 1.0F - Mth.clamp(state.deathTime / HerobrineEntity.DEATH_DURATION, 0.0F, 1.0F);
        return ARGB.white(alpha);
    }
}
