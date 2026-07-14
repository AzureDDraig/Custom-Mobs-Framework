package ddraig.net.custommobs.fabric.client;

import net.fabricmc.api.ClientModInitializer;

public final class CustomMobsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ddraig.net.custommobs.client.renderer.GeckoLibRendererBridge.register(context ->
            new ddraig.net.custommobs.fabric.client.renderer.CustomMobGeoRenderer(context)
        );
        dev.architectury.registry.client.rendering.BlockEntityRendererRegistry.register(
                ddraig.net.custommobs.registry.ModBlocks.RPG_MOB_SPAWNER_BE.get(),
                ddraig.net.custommobs.client.renderer.RPGMobSpawnerRenderer::new
        );
        dev.architectury.registry.client.rendering.BlockEntityRendererRegistry.register(
                ddraig.net.custommobs.registry.ModBlocks.RAID_BLOCK_BE.get(),
                ddraig.net.custommobs.client.renderer.RaidBlockRenderer::new
        );
        dev.architectury.registry.client.rendering.RenderTypeRegistry.register(
                net.minecraft.client.renderer.RenderType.cutout(),
                ddraig.net.custommobs.registry.ModBlocks.RPG_MOB_SPAWNER.get(),
                ddraig.net.custommobs.registry.ModBlocks.RAID_BLOCK.get()
        );
        ddraig.net.custommobs.client.CustomMobsClient.init();
    }
}
