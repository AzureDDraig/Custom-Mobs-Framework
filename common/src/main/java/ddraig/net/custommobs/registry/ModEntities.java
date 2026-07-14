package ddraig.net.custommobs.registry;

import ddraig.net.custommobs.CustomMobs;
import ddraig.net.custommobs.entity.CustomMobEntity;
import ddraig.net.custommobs.entity.CustomProjectileEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = 
            DeferredRegister.create(CustomMobs.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<CustomMobEntity>> CUSTOM_MOB = ENTITIES.register("custom_mob",
            () -> EntityType.Builder.of(CustomMobEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f) // Default size, will be dynamically scaled by model dimensions
                    .build("custom_mob")
    );

    public static final RegistrySupplier<EntityType<CustomProjectileEntity>> CUSTOM_PROJECTILE = ENTITIES.register("custom_projectile",
            () -> EntityType.Builder.<CustomProjectileEntity>of(CustomProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("custom_projectile")
    );

    public static void init() {
        ENTITIES.register();
        dev.architectury.registry.level.entity.EntityAttributeRegistry.register(
                CUSTOM_MOB,
                CustomMobEntity::createAttributes
        );
        dev.architectury.registry.level.entity.SpawnPlacementsRegistry.register(
                CUSTOM_MOB,
                net.minecraft.world.entity.SpawnPlacements.Type.NO_RESTRICTIONS,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CustomMobEntity::checkCustomMobSpawnRules
        );
    }
}
