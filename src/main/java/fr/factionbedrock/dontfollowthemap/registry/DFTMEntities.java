package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.ImitatorEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedChickenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedCowEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedPigEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedSheepEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

public class DFTMEntities
{
    //not saved: apparitions, the thief and the imitator only stay for a little while
    public static final EntityType<HerobrineApparitionEntity> APPARITION = register("apparition", EntityType.Builder.of(HerobrineApparitionEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().noSave().noSummon().clientTrackingRange(10));
    public static final EntityType<UnseenEntity> UNSEEN = register("unseen", EntityType.Builder.of(UnseenEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().noSave().noSummon().clientTrackingRange(8));
    public static final EntityType<ImitatorEntity> IMITATOR = register("imitator", EntityType.Builder.of(ImitatorEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().noSave().noSummon().clientTrackingRange(10));
    public static final EntityType<HerobrineEntity> HEROBRINE = register("herobrine", EntityType.Builder.of(HerobrineEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().clientTrackingRange(10));
    //possessed animals: exactly the size of the plain ones
    public static final EntityType<PossessedSheepEntity> POSSESSED_SHEEP = register("possessed_sheep", EntityType.Builder.of(PossessedSheepEntity::new, MobCategory.CREATURE).sized(0.9F, 1.3F).eyeHeight(1.235F).passengerAttachments(1.2375F).clientTrackingRange(10));
    public static final EntityType<PossessedCowEntity> POSSESSED_COW = register("possessed_cow", EntityType.Builder.of(PossessedCowEntity::new, MobCategory.CREATURE).sized(0.9F, 1.4F).eyeHeight(1.3F).passengerAttachments(1.36875F).clientTrackingRange(10));
    public static final EntityType<PossessedPigEntity> POSSESSED_PIG = register("possessed_pig", EntityType.Builder.of(PossessedPigEntity::new, MobCategory.CREATURE).sized(0.9F, 0.9F).passengerAttachments(0.86875F).clientTrackingRange(10));
    public static final EntityType<PossessedChickenEntity> POSSESSED_CHICKEN = register("possessed_chicken", EntityType.Builder.of(PossessedChickenEntity::new, MobCategory.CREATURE).sized(0.4F, 0.7F).eyeHeight(0.644F).passengerAttachments(new Vec3(0.0D, 0.7D, -0.1D)).clientTrackingRange(10));

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder)
    {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DontFollowTheMap.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void registerAttributes()
    {
        FabricDefaultAttributeRegistry.register(APPARITION, HerobrineApparitionEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(UNSEEN, UnseenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(IMITATOR, ImitatorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HEROBRINE, HerobrineEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POSSESSED_SHEEP, PossessedSheepEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POSSESSED_COW, PossessedCowEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POSSESSED_PIG, PossessedPigEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POSSESSED_CHICKEN, PossessedChickenEntity.createAttributes());
    }

    public static void load() {}
}
