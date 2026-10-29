package fr.factionbedrock.dontfollowthemap.mixin;

import fr.factionbedrock.dontfollowthemap.client.FakeHitboxes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hitboxes (F3 + B) of things that aren't there, for haunted players (client/FakeHitboxes). */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin
{
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void dontfollowthemap$emitFakeHitboxes(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks, CallbackInfo ci)
    {
        FakeHitboxes.emit(partialTicks);
    }
}
