package ddraig.net.custommobs.registry;

import ddraig.net.custommobs.CustomMobs;
import ddraig.net.custommobs.block.RPGMobSpawnerBlock;
import ddraig.net.custommobs.block.entity.RPGMobSpawnerBlockEntity;
import ddraig.net.custommobs.block.RaidBlock;
import ddraig.net.custommobs.block.entity.RaidBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(CustomMobs.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(CustomMobs.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(CustomMobs.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<Block> RPG_MOB_SPAWNER = BLOCKS.register("rpg_mob_spawner",
            () -> new RPGMobSpawnerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(5.0F).noOcclusion())
    );

    public static final RegistrySupplier<Item> RPG_MOB_SPAWNER_ITEM = ITEMS.register("rpg_mob_spawner",
            () -> new BlockItem(RPG_MOB_SPAWNER.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Block> RAID_BLOCK = BLOCKS.register("raid_block",
            () -> new RaidBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion())
    );

    public static final RegistrySupplier<Item> RAID_BLOCK_ITEM = ITEMS.register("raid_block",
            () -> new BlockItem(RAID_BLOCK.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> BESTIARY = ITEMS.register("bestiary",
            () -> new ddraig.net.custommobs.item.BestiaryItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistrySupplier<BlockEntityType<RPGMobSpawnerBlockEntity>> RPG_MOB_SPAWNER_BE = BLOCK_ENTITIES.register("rpg_mob_spawner",
            () -> BlockEntityType.Builder.of(RPGMobSpawnerBlockEntity::new, RPG_MOB_SPAWNER.get()).build(null)
    );

    public static final RegistrySupplier<BlockEntityType<RaidBlockEntity>> RAID_BLOCK_BE = BLOCK_ENTITIES.register("raid_block",
            () -> BlockEntityType.Builder.of(RaidBlockEntity::new, RAID_BLOCK.get()).build(null)
    );

    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
    }
}
