package fr.factionbedrock.dontfollowthemap.registry;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.PossessedSheepEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class DFTMEntities
{
    //not saved: apparitions and the thief only exist for a few seconds
    public static final EntityType<HerobrineApparitionEntity> APPARITION = register("apparition", EntityType.Builder.of(HerobrineApparitionEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().noSave().noSummon().clientTrackingRange(10));
    public static final EntityType<UnseenEntity> UNSEEN = register("unseen", EntityType.Builder.of(UnseenEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().noSave().noSummon().clientTrackingRange(8));
    public static final EntityType<HerobrineEntity> HEROBRINE = register("herobrine", EntityType.Builder.of(HerobrineEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F).fireImmune().clientTrackingRange(10));
    public static final EntityType<PossessedSheepEntity> POSSESSED_SHEEP = register("possessed_sheep", EntityType.Builder.of(PossessedSheepEntity::new, MobCategory.CREATURE).sized(0.9F, 1.3F).eyeHeight(1.235F).passengerAttachments(1.2375F).clientTrackingRange(10));

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder)
    {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DontFollowTheMap.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void registerAttributes()
    {
        FabricDefaultAttributeRegistry.register(APPARITION, HerobrineApparitionEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(UNSEEN, UnseenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HEROBRINE, HerobrineEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(POSSESSED_SHEEP, PossessedSheepEntity.createAttributes());
    }

    public static void load() {}
}
