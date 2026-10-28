package fr.factionbedrock.dontfollowthemap.entity.possessed;

import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** The animals Herobrine can take over, and how each one becomes its possessed self (and back) without anything showing. */
public class PossessableAnimals
{
    public static final Kind<Sheep> SHEEP = new Kind<>(Sheep.class, EntityTypes.SHEEP, DFTMEntities.POSSESSED_SHEEP, config -> config.possessedSheep,
            DFTMSounds.POSSESSED_SHEEP_AMBIENT, DFTMSounds.POSSESSED_SHEEP_ATTACK, (from, to) ->
            {
                to.setColor(from.getColor());
                to.setSheared(from.isSheared());
            });
    public static final Kind<Cow> COW = new Kind<>(Cow.class, EntityTypes.COW, DFTMEntities.POSSESSED_COW, config -> config.possessedCows,
            DFTMSounds.POSSESSED_COW_AMBIENT, DFTMSounds.POSSESSED_COW_ATTACK, (from, to) ->
            {
                copyComponent(from, to, DataComponents.COW_VARIANT);
                copyComponent(from, to, DataComponents.COW_SOUND_VARIANT);
            });
    //its saddle is equipment: the conversion keeps it
    public static final Kind<Pig> PIG = new Kind<>(Pig.class, EntityTypes.PIG, DFTMEntities.POSSESSED_PIG, config -> config.possessedPigs,
            DFTMSounds.POSSESSED_PIG_AMBIENT, DFTMSounds.POSSESSED_PIG_ATTACK, (from, to) ->
            {
                copyComponent(from, to, DataComponents.PIG_VARIANT);
                copyComponent(from, to, DataComponents.PIG_SOUND_VARIANT);
            });
    public static final Kind<Chicken> CHICKEN = new Kind<>(Chicken.class, EntityTypes.CHICKEN, DFTMEntities.POSSESSED_CHICKEN, config -> config.possessedChickens,
            DFTMSounds.POSSESSED_CHICKEN_AMBIENT, DFTMSounds.POSSESSED_CHICKEN_ATTACK, (from, to) ->
            {
                copyComponent(from, to, DataComponents.CHICKEN_VARIANT);
                copyComponent(from, to, DataComponents.CHICKEN_SOUND_VARIANT);
                to.eggTime = from.eggTime;
            });
    public static final List<Kind<?>> ALL = List.of(SHEEP, COW, PIG, CHICKEN);

    /** The kinds the config allows the haunting to possess. */
    public static List<Kind<?>> enabled()
    {
        DFTMConfig config = DFTMConfig.get();
        return ALL.stream().filter(kind -> kind.enabled().test(config)).toList();
    }

    @Nullable public static Kind<?> byPlainType(EntityType<?> type)
    {
        for (Kind<?> kind : ALL) {if (kind.plainType() == type) {return kind;}}
        return null;
    }

    /** A plain adult animal of one of these kinds, free to move: not leashed, ridden, riding, or without AI. */
    public static boolean canBePossessed(Animal animal, Collection<Kind<?>> kinds)
    {
        Kind<?> kind = byPlainType(animal.getType());
        return kind != null && kinds.contains(kind) && animal.isAlive() && !animal.isBaby() && !animal.isLeashed() && !animal.isPassenger() && !animal.isVehicle() && !animal.isNoAi()
                && !(animal instanceof Chicken chicken && chicken.isChickenJockey());
    }

    /** Herobrine takes over this animal and makes it stare at the victim. Returns the possessed animal, or null if this animal can't be possessed. */
    @Nullable public static Animal possess(Animal animal, Player victim)
    {
        Kind<?> kind = byPlainType(animal.getType());
        return kind == null ? null : kind.possess(animal, victim);
    }

    private static <T> void copyComponent(Animal from, Animal to, DataComponentType<T> type)
    {
        T value = from.get(type);
        if (value != null) {to.setComponent(type, value);}
    }

    /**
     * One kind of possessable animal. Its possessed entity extends the plain one, so that the vanilla renderer, model, variants,
     * sounds and loot all apply to it; copyLooks carries over everything that makes this particular animal look like itself.
     */
    public record Kind<A extends Animal>(Class<A> animalClass, EntityType<A> plainType, EntityType<? extends A> possessedType, Predicate<DFTMConfig> enabled,
                                         SoundEvent ambientSound, SoundEvent attackSound, BiConsumer<A, A> copyLooks)
    {
        @Nullable public Animal possess(Animal animal, Player victim)
        {
            A plain = this.animalClass.cast(animal);
            return plain.convertTo(this.possessedType, ConversionParams.single(plain, true, false), possessed ->
            {
                this.copy(plain, possessed);
                ((PossessedAnimal) possessed).getPossession().setVictim(victim);
            });
        }

        public void release(Animal possessed)
        {
            A from = this.animalClass.cast(possessed);
            from.convertTo(this.plainType, ConversionParams.single(from, true, false), plain -> this.copy(from, plain));
        }

        private void copy(A from, A to)
        {
            to.setHealth(from.getHealth());
            to.setYHeadRot(from.getYHeadRot());
            this.copyLooks.accept(from, to);
        }
    }
}
