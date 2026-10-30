package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Once the player has seen a Herobrine who watches (a sighting, a stalk...), the screen turns into an old, worn film that glitches now and then
 * (post effect dontfollowthemap:haunted): it comes in and fades out over a few seconds, fainter from afar. A sudden apparition jolts it all at once.
 * Seen: on the screen with nothing in between, anywhere on it when he is close (the corner of the eye), right where the player looks when he is far.
 * Being close isn't enough to see him, but once he is seen it keeps him felt: the effect only goes once he is far and the player looks away.
 * The distortion effects setting scales it (none at 0).
 */
public class HauntedScreen
{
    public static final Identifier EFFECT = DontFollowTheMap.id("haunted");
    /** The shader reads how strong the effect is from this one pixel texture (its "Strength" input). */
    private static final Identifier STRENGTH_TEXTURE = DontFollowTheMap.id("textures/effect/haunted_strength.png");
    /** Full in 2.5 seconds, gone in 3. */
    private static final float FADE_IN_PER_TICK = 1.0F / 50.0F, FADE_OUT_PER_TICK = 1.0F / 60.0F;
    /** Further, he can't be seen. */
    private static final double SIGHT_RANGE = 48.0D;
    /** Seen anywhere on the screen up to the first distance, only within NARROW_SIGHT_ANGLE degrees of where the player looks from the second one on; the angle narrows in between. */
    private static final double WIDE_SIGHT_RANGE = 12.0D, NARROW_SIGHT_RANGE = 24.0D;
    private static final double NARROW_SIGHT_ANGLE = 5.0D;
    /** Once seen, he is felt while he is this close, seen or not; further, while the player keeps looking at him (within twice the angle he can be seen in). */
    private static final double FELT_RANGE = 16.0D;
    private static final double STILL_LOOKING_FACTOR = 2.0D;
    /** Felt fully up to this distance, half as much at SIGHT_RANGE. */
    private static final double FULL_STRENGTH_RANGE = 20.0D;

    @Nullable private static DynamicTexture strengthTexture;
    private static int uploadedStrength = -1;
    private static float strength;
    /** The watching apparitions the player saw and still feels (network ids). */
    private static Set<Integer> felt = new HashSet<>();

    public static float strength() {return strength;}

    /** Herobrine appears all of a sudden (HerobrineAppearedPayload): the effect is there at once, then fades out. */
    public static void jolt() {strength = 1.0F;}

    public static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null)
        {
            strength = 0.0F;
            felt.clear();
            return;
        }
        if (minecraft.isPaused()) {return;}
        float presence = feel(minecraft, minecraft.level, player);
        //nothing on top of the fight
        float target = HerobrineSounds.isFightMusicOn() ? 0.0F : presence;
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

    /** How strongly the player feels the watching apparitions they saw (0 if none), fainter from afar; keeps track of those they still feel. */
    private static float feel(Minecraft minecraft, ClientLevel level, LocalPlayer player)
    {
        Set<Integer> stillFelt = new HashSet<>();
        float strongest = 0.0F;
        for (Entity entity : level.entitiesForRendering())
        {
            if (!(entity instanceof HerobrineApparitionEntity apparition) || !apparition.isWatching()) {continue;}
            double distance = Math.sqrt(apparition.distanceToSqr(player));
            boolean feels = felt.contains(apparition.getId())
                    ? distance < FELT_RANGE || isInSight(minecraft, player, apparition, distance, STILL_LOOKING_FACTOR)
                    : isInSight(minecraft, player, apparition, distance, 1.0D);
            if (!feels) {continue;}
            stillFelt.add(apparition.getId());
            strongest = Math.max(strongest, (float) Mth.clampedMap(distance, FULL_STRENGTH_RANGE, SIGHT_RANGE, 1.0D, 0.5D));
        }
        felt = stillFelt;
        return strongest;
    }

    /** On the screen, within angleFactor times the angle he can be seen in from that far, with nothing in between. */
    private static boolean isInSight(Minecraft minecraft, LocalPlayer player, HerobrineApparitionEntity apparition, double distance, double angleFactor)
    {
        if (distance > SIGHT_RANGE || minecraft.getWindow().getHeight() <= 0) {return false;}
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 toHim = apparition.getBoundingBox().getCenter().subtract(camera.position());
        double ahead = toHim.dot(new Vec3(camera.forwardVector())), length = toHim.length();
        if (ahead <= 0.0D) {return false;}
        //the camera's field of view is the screen's height (the spyglass narrows it, sprinting widens it)
        double halfHeight = Math.tan(Math.toRadians(camera.getFov() / 2.0D)), halfWidth = halfHeight * minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
        if (Math.abs(toHim.dot(new Vec3(camera.upVector()))) > halfHeight * ahead || Math.abs(toHim.dot(new Vec3(camera.leftVector()))) > halfWidth * ahead) {return false;}
        double angle = Math.toDegrees(Math.acos(Mth.clamp(ahead / length, -1.0D, 1.0D)));
        //anywhere on the screen when he is close, right where the player looks when he is far
        double sightAngle = Mth.clampedMap(distance, WIDE_SIGHT_RANGE, NARROW_SIGHT_RANGE, 90.0D, NARROW_SIGHT_ANGLE);
        return angle <= sightAngle * angleFactor && player.hasLineOfSight(apparition);
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
