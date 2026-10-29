package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Herobrine's sounds that loop on each client: his ambience (a sighting, a stalk), heard by each player who is close to him
 * or looks at him, and by them only; and the music of the fight, for those around it, from his summoning on. Each round starts before the previous one is over, so that they overlap
 * where their fades were made to. The offsets come from the sounds' editing project (tools/sounds/HerobrineSounds.kdenlive, 25 fps).
 */
public class HerobrineSounds
{
    /** A round lasts 14.71 s; the next one starts 13.08 s in (1:35:17 to 1:48:19 on the project). */
    private static final Loop AMBIENCE = new Loop(DFTMSounds.HEROBRINE_AMBIENT, SoundSource.HOSTILE, 262);
    /**
     * A round lasts 21.35 s; the next one starts 14.32 s in (0:29:15 to 0:43:23 on the project).
     * A hostile creature's sound, not music: players who turned the game's music off hear it too.
     */
    private static final Loop FIGHT_MUSIC = new Loop(DFTMSounds.HEROBRINE_FIGHT_MUSIC, SoundSource.HOSTILE, 286);
    /** Close to him: felt whether the player looks at him or not, fully within the first distance, fading out to the second. */
    private static final double FULL_NEAR_RANGE = 12.0D, NEAR_RANGE = 28.0D;
    /** Looking at him: felt from this far, fully up to the first distance. */
    private static final double FULL_LOOKING_RANGE = 20.0D, LOOKING_RANGE = 48.0D;
    /** Looking at him: within 30 degrees of where the player looks, fully within the first 8 (the rest is the corner of the eye). */
    private static final double FULL_LOOKING_ANGLE = 8.0D, LOOKING_ANGLE = 30.0D;
    /** How faintly he is felt at the edge, at the end of the ranges or in the corner of the eye. */
    private static final float EDGE_PRESENCE = 0.3F;
    private static final double FIGHT_RANGE = 64.0D;
    /** The summoning lasts until he appears; a little longer, in case he takes a few more ticks to show up on this client. */
    private static final int SUMMONING_TICKS = SealEvents.SUMMON_DELAY + 30;

    @Nullable private static BlockPos summoningSeal;
    private static int summoningTicks;
    private static float presence;

    public static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null)
        {
            AMBIENCE.reset();
            FIGHT_MUSIC.reset();
            summoningTicks = 0;
            presence = 0.0F;
            return;
        }
        //the sounds are paused too
        if (minecraft.isPaused()) {return;}
        if (summoningTicks > 0) {summoningTicks--;}
        boolean fight = isNearTheFight(minecraft.level, player);
        FIGHT_MUSIC.tick(minecraft, fight);
        presence = fight ? 0.0F : presence(minecraft.level, player);
        AMBIENCE.tick(minecraft, presence > 0.0F);
    }

    /** While it plays, no other music does (see MusicManagerMixin). */
    public static boolean isFightMusicOn() {return FIGHT_MUSIC.isOn();}
    public static boolean isAmbienceOn() {return AMBIENCE.isOn();}

    /** How strongly the player feels a watching Herobrine right now, from 0 (not at all: no ambience) to 1 (the screen effect follows it, see HauntedScreen). */
    public static float presence() {return presence;}

    /** Herobrine is being summoned at that seal (see SealEvents): his music starts now, so that its rise is over by the time he appears. */
    public static void onFightStarting(BlockPos seal)
    {
        summoningSeal = seal;
        summoningTicks = SUMMONING_TICKS;
    }

    /** Herobrine on his way, or a living Herobrine around: the fight is on (his death fades the music out). */
    private static boolean isNearTheFight(ClientLevel level, LocalPlayer player)
    {
        if (summoningTicks > 0 && summoningSeal != null && player.distanceToSqr(Vec3.atCenterOf(summoningSeal)) < FIGHT_RANGE * FIGHT_RANGE) {return true;}
        for (Entity entity : level.entitiesForRendering())
        {
            if (entity instanceof HerobrineEntity herobrine && herobrine.isAlive() && herobrine.distanceToSqr(player) < FIGHT_RANGE * FIGHT_RANGE) {return true;}
        }
        return false;
    }

    /**
     * Close to a Herobrine who watches (a sighting, a stalk...), seen or not; or looking at him from further away, with nothing in between.
     * Fainter at the edge: at the end of the ranges, or in the corner of the eye.
     */
    private static float presence(ClientLevel level, LocalPlayer player)
    {
        Vec3 eyes = player.getEyePosition(), view = player.getViewVector(1.0F);
        float strongest = 0.0F;
        for (Entity entity : level.entitiesForRendering())
        {
            if (!(entity instanceof HerobrineApparitionEntity apparition) || !apparition.isWatching()) {continue;}
            double distance = Math.sqrt(apparition.distanceToSqr(player));
            if (distance < NEAR_RANGE) {strongest = Math.max(strongest, fade(distance, FULL_NEAR_RANGE, NEAR_RANGE, EDGE_PRESENCE));}
            if (distance > LOOKING_RANGE) {continue;}
            Vec3 toHim = apparition.getBoundingBox().getCenter().subtract(eyes);
            double length = toHim.length();
            if (length <= 0.0D) {continue;}
            double angle = Math.toDegrees(Math.acos(Mth.clamp(view.dot(toHim.scale(1.0D / length)), -1.0D, 1.0D)));
            if (angle > LOOKING_ANGLE || !player.hasLineOfSight(apparition)) {continue;}
            strongest = Math.max(strongest, fade(angle, FULL_LOOKING_ANGLE, LOOKING_ANGLE, EDGE_PRESENCE) * fade(distance, FULL_LOOKING_RANGE, LOOKING_RANGE, 0.5F));
        }
        return strongest;
    }

    /** 1 up to full, then down to edge at limit. */
    private static float fade(double value, double full, double limit, float edge)
    {
        return value <= full ? 1.0F : (float) Mth.lerp(Math.min(1.0D, (value - full) / (limit - full)), 1.0D, edge);
    }

    /** Rounds of the same sound, one every period ticks while the loop is on; they fade out when it stops. */
    private static class Loop
    {
        /** How long the loop stays on once its reason is gone (a glance away doesn't cut it). */
        private static final int GRACE_TICKS = 10;

        private final SoundEvent sound;
        private final SoundSource source;
        private final int period;
        private final List<Round> rounds = new ArrayList<>();
        private int ticksToNextRound;
        private int graceTicks;

        Loop(SoundEvent sound, SoundSource source, int period)
        {
            this.sound = sound;
            this.source = source;
            this.period = period;
        }

        boolean isOn() {return this.graceTicks > 0;}

        void tick(Minecraft minecraft, boolean wanted)
        {
            this.rounds.removeIf(round -> !minecraft.getSoundManager().isActive(round));
            if (wanted) {this.graceTicks = GRACE_TICKS;}
            else if (this.graceTicks > 0) {this.graceTicks--;}
            if (this.isOn())
            {
                if (--this.ticksToNextRound <= 0)
                {
                    //each round picks one of the sound's files at random
                    Round round = new Round(this.sound, this.source);
                    minecraft.getSoundManager().play(round);
                    this.rounds.add(round);
                    this.ticksToNextRound = this.period;
                }
            }
            else
            {
                //it will start over from the beginning
                this.ticksToNextRound = 0;
                this.rounds.forEach(Round::fadeOut);
            }
        }

        /** Leaving the world: the game stops every sound anyway. */
        void reset()
        {
            this.rounds.clear();
            this.ticksToNextRound = 0;
            this.graceTicks = 0;
        }
    }

    /** One round: played as it is (its fades are in the file), or faded out early when its loop stops. */
    private static class Round extends AbstractTickableSoundInstance
    {
        private static final float FADE_OUT_PER_TICK = 1.0F / 30.0F;
        private boolean fadingOut;

        Round(SoundEvent sound, SoundSource source)
        {
            super(sound, source, SoundInstance.createUnseededRandom());
            //around the player, not somewhere in the world (the files are in stereo anyway)
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void fadeOut() {this.fadingOut = true;}

        @Override public void tick()
        {
            if (!this.fadingOut) {return;}
            this.volume -= FADE_OUT_PER_TICK;
            if (this.volume <= 0.0F) {this.stop();}
        }
    }
}
