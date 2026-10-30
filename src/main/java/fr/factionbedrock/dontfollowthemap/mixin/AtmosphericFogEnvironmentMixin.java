package fr.factionbedrock.dontfollowthemap.mixin;

import fr.factionbedrock.dontfollowthemap.client.HerobrineFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The overworld fog, once vanilla is done with it (weather, boss bars): Herobrine watching or stalking the player makes it thicker. */
@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin
{
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void dontfollowthemap$thickenWhileWatched(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci)
    {
        HerobrineFog.apply(fog, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
