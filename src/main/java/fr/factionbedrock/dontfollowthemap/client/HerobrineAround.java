package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedAnimal;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Whatever of Herobrine's this client knows of (his apparitions, himself, the imitator, the thief, the possessed animals),
 * gathered once per tick, before the fog, the sounds, the screen and the debug screen look at it.
 */
public class HerobrineAround
{
    private static final List<HerobrineApparitionEntity> APPARITIONS = new ArrayList<>();
    private static final List<HerobrineEntity> HEROBRINES = new ArrayList<>();
    private static final List<Entity> EVERYTHING = new ArrayList<>();

    public static void tick(Minecraft minecraft)
    {
        APPARITIONS.clear();
        HEROBRINES.clear();
        EVERYTHING.clear();
        if (minecraft.level == null) {return;}
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof HerobrineApparitionEntity apparition) {APPARITIONS.add(apparition);}
            else if (entity instanceof HerobrineEntity herobrine) {HEROBRINES.add(herobrine);}
            else if (!(entity instanceof ImitatorEntity || entity instanceof UnseenEntity || entity instanceof PossessedAnimal)) {continue;}
            EVERYTHING.add(entity);
        }
    }

    public static List<HerobrineApparitionEntity> apparitions() {return APPARITIONS;}

    /** Himself, freed from the seal (living or fading away). */
    public static List<HerobrineEntity> herobrines() {return HEROBRINES;}

    /** Whether something of his is that close to this entity. */
    public static boolean isAnythingWithin(Entity entity, double range)
    {
        for (Entity his : EVERYTHING) {if (his.distanceToSqr(entity) < range * range) {return true;}}
        return false;
    }
}
