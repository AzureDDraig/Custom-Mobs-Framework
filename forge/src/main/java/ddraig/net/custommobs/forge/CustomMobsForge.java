package ddraig.net.custommobs.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import ddraig.net.custommobs.CustomMobs;

@Mod(CustomMobs.MOD_ID)
public final class CustomMobsForge {
    public CustomMobsForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(CustomMobs.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        CustomMobs.init();

        dev.architectury.utils.EnvExecutor.runInEnv(dev.architectury.utils.Env.CLIENT, () -> () -> {
            ddraig.net.custommobs.forge.client.CustomMobsForgeClient.init();
        });
    }
}
