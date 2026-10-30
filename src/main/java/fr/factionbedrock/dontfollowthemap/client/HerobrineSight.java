package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.network.HerobrineSeenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether the player sees one of Herobrine's apparitions: on their screen as the camera draws it (field of view, spyglass, sprinting),
 * with nothing in between; anywhere on it when he is close (the corner of the eye), only right where they look when he is far.
 * The screen effect follows it (HauntedScreen), and the server is told when the player starts or stops seeing one that is after them
 * (HerobrineSeenPayload): the stalker runs at them once seen.
 */
public class HerobrineSight
{
    /** Further, he can't be seen. */
    public static final double SIGHT_RANGE = 48.0D;
    /** Seen anywhere on the screen up to the first distance, only within NARROW_SIGHT_ANGLE degrees of where the player looks from the second one on; the angle narrows in between. */
    private static final double WIDE_SIGHT_RANGE = 12.0D, NARROW_SIGHT_RANGE = 24.0D;
    private static final double NARROW_SIGHT_ANGLE = 5.0D;

    /** The apparitions after the player that the server was told they see (network ids). */
    private static Set<Integer> told = new HashSet<>();

    public static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null)
        {
            told.clear();
            return;
        }
        if (minecraft.isPaused()) {return;}
        Set<Integer> seen = new HashSet<>();
        for (HerobrineApparitionEntity apparition : HerobrineAround.apparitions())
        {
            //the ones watching or stalking this player (they thicken their fog)
            if (!apparition.isFogging(player) || !isInSight(minecraft, player, apparition, Math.sqrt(apparition.distanceToSqr(player)), 1.0D)) {continue;}
            seen.add(apparition.getId());
            if (!told.contains(apparition.getId())) {tell(apparition.getId(), true);}
        }
        for (int id : told) {if (!seen.contains(id)) {tell(id, false);}}
        told = seen;
    }

    private static void tell(int entityId, boolean seen)
    {
        if (ClientPlayNetworking.canSend(HerobrineSeenPayload.TYPE)) {ClientPlayNetworking.send(new HerobrineSeenPayload(entityId, seen));}
    }

    /** On the screen, within angleFactor times the angle he can be seen in from that far, with nothing in between. */
    public static boolean isInSight(Minecraft minecraft, LocalPlayer player, HerobrineApparitionEntity apparition, double distance, double angleFactor)
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
}
