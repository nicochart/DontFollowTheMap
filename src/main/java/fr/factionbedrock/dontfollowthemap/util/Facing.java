package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.util.Mth;

/** Which way things face. */
public class Facing
{
    /** The yaw of something facing this horizontal direction (0 faces south, 90 west, like an entity's). */
    public static float yawTowards(double dx, double dz) {return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;}
}
