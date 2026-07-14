package ddraig.net.custommobs.forge.client;

import ddraig.net.custommobs.client.CustomMobsClient;
import ddraig.net.custommobs.client.renderer.GeckoLibRendererBridge;
import ddraig.net.custommobs.forge.client.renderer.CustomMobGeoRenderer;

public class CustomMobsForgeClient {
    public static void init() {
        GeckoLibRendererBridge.register(CustomMobGeoRenderer::new);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(CustomMobsForgeClient::onRegisterRenderers);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(CustomMobsForgeClient::onClientSetup);
        CustomMobsClient.init();
    }

    private static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ddraig.net.custommobs.registry.ModBlocks.RPG_MOB_SPAWNER_BE.get(),
                ddraig.net.custommobs.client.renderer.RPGMobSpawnerRenderer::new
        );
        event.registerBlockEntityRenderer(
                ddraig.net.custommobs.registry.ModBlocks.RAID_BLOCK_BE.get(),
                ddraig.net.custommobs.client.renderer.RaidBlockRenderer::new
        );
    }

    private static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        dev.architectury.registry.client.rendering.RenderTypeRegistry.register(
                net.minecraft.client.renderer.RenderType.cutout(),
                ddraig.net.custommobs.registry.ModBlocks.RPG_MOB_SPAWNER.get(),
                ddraig.net.custommobs.registry.ModBlocks.RAID_BLOCK.get()
        );
    }
}
