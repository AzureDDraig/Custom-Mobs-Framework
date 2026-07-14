package ddraig.net.custommobs.fabric;

import net.fabricmc.api.ModInitializer;

import ddraig.net.custommobs.CustomMobs;

public final class CustomMobsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        CustomMobs.init();
    }
}
