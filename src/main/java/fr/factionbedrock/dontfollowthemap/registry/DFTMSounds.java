package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Herobrine's own sounds, then vanilla sound files reshaped (pitch, volume) in assets/dontfollowthemap/sounds.json. */
public class DFTMSounds
{
    //original sounds (assets/dontfollowthemap/sounds, made with tools/sounds/HerobrineSounds.kdenlive)
    public static final SoundEvent HEROBRINE_AMBIENT = register("entity.herobrine.ambient");
    public static final SoundEvent HEROBRINE_APPEAR = register("entity.herobrine.appear");
    public static final SoundEvent HEROBRINE_FIGHT_MUSIC = register("music.herobrine_fight");
    //reshaped vanilla sounds
    public static final SoundEvent VANISH = register("entity.herobrine.vanish");
    public static final SoundEvent STING = register("entity.herobrine.sting");
    public static final SoundEvent WHISPER = register("ambient.whisper");
    public static final SoundEvent BLINK = register("entity.herobrine.blink");
    public static final SoundEvent HEROBRINE_HURT = register("entity.herobrine.hurt");
    public static final SoundEvent HEROBRINE_DEATH = register("entity.herobrine.death");
    public static final SoundEvent HEROBRINE_CALL = register("entity.herobrine.call");
    public static final SoundEvent POSSESSED_SHEEP_AMBIENT = register("entity.possessed_sheep.ambient");
    public static final SoundEvent POSSESSED_SHEEP_ATTACK = register("entity.possessed_sheep.attack");
    public static final SoundEvent POSSESSED_COW_AMBIENT = register("entity.possessed_cow.ambient");
    public static final SoundEvent POSSESSED_COW_ATTACK = register("entity.possessed_cow.attack");
    public static final SoundEvent POSSESSED_PIG_AMBIENT = register("entity.possessed_pig.ambient");
    public static final SoundEvent POSSESSED_PIG_ATTACK = register("entity.possessed_pig.attack");
    public static final SoundEvent POSSESSED_CHICKEN_AMBIENT = register("entity.possessed_chicken.ambient");
    public static final SoundEvent POSSESSED_CHICKEN_ATTACK = register("entity.possessed_chicken.attack");
    public static final SoundEvent UNSEEN_STEAL = register("entity.unseen.steal");
    public static final SoundEvent SEAL_REFUSE = register("block.seal.refuse");
    public static final SoundEvent SEAL_BREAK = register("block.seal.break");
    public static final SoundEvent GRAVE_WAKE = register("ambient.grave_wake");

    private static SoundEvent register(String name)
    {
        Identifier id = DontFollowTheMap.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void load() {}
}
