package fr.factionbedrock.dontfollowthemap.haunting;

import com.mojang.serialization.Codec;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import net.minecraft.util.StringRepresentable;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * What answers what a player does, once in a while (see HauntingDirector): its option and its chance in the config (1.5 times higher once haunted, for some),
 * the stage it starts at, and how long the player is left alone after it (HauntingData remembers when it last happened).
 */
public enum Reaction implements StringRepresentable
{
    /** Killing something: he appears on it for an instant. */
    KILL_APPARITION("kill_apparition", config -> config.killApparitions, config -> config.killApparitionChance, 1.5D, HauntingData.WATCHED, 20L * 60L * 4L),
    /** Going to bed: he stands next to it. */
    BED_SCARE("bed_scare", config -> config.bedScares, config -> config.bedScareChance, 1.5D, HauntingData.WATCHED, HauntingData.DAY / 2L),
    /** Waking up after a full night: in a grave. */
    GRAVE("grave", config -> config.graves, config -> config.graveChance, 1.0D, HauntingData.HAUNTED, HauntingData.DAY * 4L),
    /** Mining: the thief comes for the drops. */
    THEFT("theft", config -> config.thief, config -> config.thiefChance, 1.0D, HauntingData.HAUNTED, 20L * 60L * 6L),
    /** Digging a straight tunnel: it is walled up behind them. */
    TUNNEL("tunnel", config -> config.tunnels, config -> config.tunnelChance, 1.0D, HauntingData.HAUNTED, HauntingData.DAY);

    public static final Codec<Reaction> CODEC = StringRepresentable.fromEnum(Reaction::values);

    private final String name;
    private final Predicate<DFTMConfig> enabled;
    private final ToDoubleFunction<DFTMConfig> chance;
    private final double hauntedChanceFactor;
    private final int minStage;
    private final long cooldown;

    Reaction(String name, Predicate<DFTMConfig> enabled, ToDoubleFunction<DFTMConfig> chance, double hauntedChanceFactor, int minStage, long cooldown)
    {
        this.name = name;
        this.enabled = enabled;
        this.chance = chance;
        this.hauntedChanceFactor = hauntedChanceFactor;
        this.minStage = minStage;
        this.cooldown = cooldown;
    }

    @Override public String getSerializedName() {return this.name;}

    public boolean isEnabled() {return this.enabled.test(DFTMConfig.get());}

    public int minStage() {return this.minStage;}

    /** Ticks after it happened before it can happen again. */
    public long cooldown() {return this.cooldown;}

    /** Its chance for a player at this stage. */
    public double chance(int stage) {return this.chance.applyAsDouble(DFTMConfig.get()) * (stage >= HauntingData.HAUNTED ? this.hauntedChanceFactor : 1.0D);}
}
