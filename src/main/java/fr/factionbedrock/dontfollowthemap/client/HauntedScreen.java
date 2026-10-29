package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where Herobrine is felt (HerobrineSounds.presence: close to him, or looking at him), the screen turns into an old, worn film that glitches now and then
 * (post effect dontfollowthemap:haunted): it comes in and fades out over a few seconds, fainter at the edge. A sudden apparition jolts it all at once.
 * The distortion effects setting scales it (none at 0).
 */
public class HauntedScreen
{
    public static final Identifier EFFECT = DontFollowTheMap.id("haunted");
    /** The shader reads how strong the effect is from this one pixel texture (its "Strength" input). */
    private static final Identifier STRENGTH_TEXTURE = DontFollowTheMap.id("textures/effect/haunted_strength.png");
    /** Full in 2.5 seconds, gone in 3. */
    private static final float FADE_IN_PER_TICK = 1.0F / 50.0F, FADE_OUT_PER_TICK = 1.0F / 60.0F;

    @Nullable private static DynamicTexture strengthTexture;
    private static int uploadedStrength = -1;
    private static float strength;

    public static float strength() {return strength;}

    /** Herobrine appears all of a sudden (HerobrineAppearedPayload): the effect is there at once, then fades out. */
    public static void jolt() {strength = 1.0F;}

    public static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            strength = 0.0F;
            return;
        }
        if (minecraft.isPaused()) {return;}
        float target = HerobrineSounds.presence();
        strength = target > strength ? Math.min(target, strength + FADE_IN_PER_TICK) : Math.max(target, strength - FADE_OUT_PER_TICK);
        float shown = strength * minecraft.options.screenEffectScale().get().floatValue();
        //the post effects the server asks for: they are drawn every frame, and replaced whenever the server sends new ones
        List<Identifier> effects = player.getActivePostEffects();
        if (shown <= 0.0F)
        {
            effects.remove(EFFECT);
            return;
        }
        upload(minecraft, shown);
        if (!effects.contains(EFFECT)) {effects.add(EFFECT);}
    }

    private static void upload(Minecraft minecraft, float shown)
    {
        //registered before the post effect is first asked for: it finds this texture instead of a file
        if (strengthTexture == null)
        {
            strengthTexture = new DynamicTexture(() -> "Herobrine's presence", 1, 1, false);
            minecraft.getTextureManager().register(STRENGTH_TEXTURE, strengthTexture);
        }
        int value = Math.round(shown * 255.0F);
        if (value == uploadedStrength) {return;}
        strengthTexture.getPixels().setPixel(0, 0, ARGB.color(255, value, value, value));
        strengthTexture.upload();
        uploadedStrength = value;
    }
}
