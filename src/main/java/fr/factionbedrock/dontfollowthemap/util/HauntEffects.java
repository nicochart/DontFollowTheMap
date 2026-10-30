package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/** What shows when he comes and goes. */
public class HauntEffects
{
    /** A figure standing there goes (or comes) in a puff of smoke, with this sound. */
    public static void puffOfSmoke(ServerLevel level, Vec3 feet, int particles, SoundEvent sound, float volume, float pitch)
    {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.9D, feet.z, particles, 0.25D, 0.6D, 0.25D, 0.02D);
        level.playSound(null, feet.x, feet.y, feet.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** A lightning bolt that only shows: no fire, no charged creeper, no damage (what calls it deals its own). */
    public static void lightning(ServerLevel level, Vec3 pos)
    {
        LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (lightning == null) {return;}
        lightning.snapTo(pos);
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }
}
