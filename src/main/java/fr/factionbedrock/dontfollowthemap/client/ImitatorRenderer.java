package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Draws the imitator exactly like a player. Its render state is an avatar one, which the game always hands over to the vanilla
 * player renderer: wide or slim arms from the skin, outer skin layers, cape, held item, name tag. This renderer only fills it in.
 */
public class ImitatorRenderer extends LivingEntityRenderer<ImitatorEntity, AvatarRenderState, PlayerModel>
{
    public ImitatorRenderer(EntityRendererProvider.Context context)
    {
        //never drawn with: see EntityRenderDispatcher.getRenderer(EntityRenderState)
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override public AvatarRenderState createRenderState() {return new AvatarRenderState();}

    @Override public void extractRenderState(ImitatorEntity entity, AvatarRenderState state, float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
        state.leftArmPose = armPose(entity, HumanoidArm.LEFT);
        state.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
        state.skin = skinOf(entity);
        state.id = entity.getId();
    }

    private static HumanoidModel.ArmPose armPose(ImitatorEntity entity, HumanoidArm arm)
    {
        InteractionHand hand = entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return entity.getItemInHand(hand).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }

    /** The skin of the player it passes for while they are connected, else the default skin their UUID gives. */
    private static PlayerSkin skinOf(ImitatorEntity entity)
    {
        UUID owner = entity.getSkinOwner();
        if (owner == null) {return DefaultPlayerSkin.getDefaultSkin();}
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    /** The player it passes for has their name shown as players do; a stranger has none. */
    @Override protected boolean shouldShowName(ImitatorEntity entity, double distanceToCameraSq) {return entity.hasCustomName() && super.shouldShowName(entity, distanceToCameraSq);}

    @Override public Identifier getTextureLocation(AvatarRenderState state) {return state.skin.body().texturePath();}
}
