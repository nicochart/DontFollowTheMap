package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * While Herobrine watches the player from afar (a sighting) or stalks them, their fog slowly thickens up to the boss fight one,
 * then lifts once he is gone. Applied on top of the overworld fog by AtmosphericFogEnvironmentMixin.
 */
public class HerobrineFog
{
    private static final int THICKENING_TICKS = 20 * 20;
    private static final int LIFTING_TICKS = 20 * 4;
    //the fog of a boss bar that creates world fog, as during the fight
    private static final float FULL_FOG_START = 10.0F;
    private static final float FULL_FOG_END = 96.0F;

    private static float amount;
    private static float previousAmount;

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            amount = previousAmount = 0.0F;
            return;
        }
        if (minecraft.isPaused()) {return;}
        previousAmount = amount;
        amount = isWatched(minecraft, minecraft.player) ? Math.min(1.0F, amount + 1.0F / THICKENING_TICKS) : Math.max(0.0F, amount - 1.0F / LIFTING_TICKS);
    }

    /** How thick the fog is, from 0 (none of his) to 1 (the boss fight one). */
    public static float amount() {return amount;}

    private static boolean isWatched(Minecraft minecraft, LocalPlayer player)
    {
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof HerobrineApparitionEntity apparition && apparition.isFogging(player)) {return true;}
        }
        return false;
    }

    public static void apply(FogData fog, float partialTick)
    {
        float progress = Mth.lerp(partialTick, previousAmount, amount);
        if (progress <= 0.0F) {return;}
        //eased: hardly noticeable at first
        float thickness = progress * progress * (3.0F - 2.0F * progress);
        float fullEnd = Math.min(fog.environmentalEnd, FULL_FOG_END);
        fog.environmentalStart = Mth.lerp(thickness, fog.environmentalStart, Math.min(fog.environmentalStart, FULL_FOG_START));
        fog.environmentalEnd = thicken(fog.environmentalEnd, fullEnd, thickness);
        fog.skyEnd = thicken(fog.skyEnd, Math.min(fog.skyEnd, fullEnd), thickness);
        fog.cloudEnd = thicken(fog.cloudEnd, Math.min(fog.cloudEnd, fullEnd), thickness);
    }

    /** Brings a fog distance closer as a density (1 / distance), so that the fog thickens evenly instead of all at the end. */
    private static float thicken(float distance, float fullDistance, float thickness)
    {
        return 1.0F / Mth.lerp(thickness, 1.0F / Math.max(distance, 1.0F), 1.0F / Math.max(fullDistance, 1.0F));
    }
}
