package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Which way things face. */
public class Facing
{
    /** The yaw of something facing this horizontal direction (0 faces south, 90 west, like an entity's). */
    public static float yawTowards(double dx, double dz) {return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;}

    /** Turns the mob (body, head and eyes) towards this point at once. */
    public static void turn(Mob mob, Vec3 point)
    {
        double dx = point.x - mob.getX(), dz = point.z - mob.getZ(), dy = point.y - mob.getEyeY();
        float yaw = yawTowards(dx, dz);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        //its look control would turn it back otherwise
        mob.getLookControl().setLookAt(point.x, point.y, point.z, 360.0F, 360.0F);
    }
}
