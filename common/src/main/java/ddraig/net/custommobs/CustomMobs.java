package ddraig.net.custommobs;

import ddraig.net.custommobs.command.CustomMobsCommands;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.network.ModPackets;
import ddraig.net.custommobs.registry.ModBlocks;
import ddraig.net.custommobs.registry.ModEntities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CustomMobs {
    public static final String MOD_ID = "custom_mobs";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static void init() {
        ddraig.net.custommobs.data.ModConfig.load();
        ddraig.net.custommobs.data.DatabaseManager.init();
        ModEntities.init();
        ModBlocks.init();
        MobRegistry.init();
        RaidSystem.init();
        dev.architectury.event.events.common.TickEvent.SERVER_POST.register(server -> {
            RaidSystem.tick(server);
        });
        dev.architectury.event.events.common.PlayerEvent.PLAYER_JOIN.register(player -> {
            ModPackets.syncTemplates(player);
        });
        CustomMobsCommands.init();
        ModPackets.init();

        dev.architectury.registry.level.biome.BiomeModifications.addProperties(
                ctx -> true,
                (ctx, mutable) -> {
                    for (net.minecraft.world.entity.MobCategory category : new net.minecraft.world.entity.MobCategory[]{
                            net.minecraft.world.entity.MobCategory.MONSTER,
                            net.minecraft.world.entity.MobCategory.CREATURE,
                            net.minecraft.world.entity.MobCategory.AMBIENT,
                            net.minecraft.world.entity.MobCategory.WATER_CREATURE,
                            net.minecraft.world.entity.MobCategory.WATER_AMBIENT,
                            net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE
                    }) {
                        mutable.getSpawnProperties().addSpawn(
                                category,
                                new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(
                                        ModEntities.CUSTOM_MOB.get(),
                                        50,
                                        1,
                                        4
                                )
                        );
                    }
                }
        );

        // Append custom items to the vanilla Spawn Eggs creative tab
        dev.architectury.registry.CreativeTabRegistry.append(
                net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS,
                ModBlocks.BESTIARY,
                ModBlocks.RPG_MOB_SPAWNER_ITEM,
                ModBlocks.RAID_BLOCK_ITEM
        );
        
        LOGGER.info("Custom Mobs Framework initialized successfully!");
    }
}
