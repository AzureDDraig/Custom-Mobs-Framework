package ddraig.net.custommobs.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.ProjectileData;
import ddraig.net.custommobs.network.ModPackets;
import ddraig.net.custommobs.client.gui.MobCreatorScreen;
import ddraig.net.custommobs.client.gui.ProjectileCreatorScreen;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CustomMobsClient {
    private static final Gson GSON = new Gson();
    public static final List<String> clientDiscoveries = new ArrayList<>();
    public static net.minecraft.client.KeyMapping bestiaryKey;

    public static void init() {
        bestiaryKey = new net.minecraft.client.KeyMapping(
                "key.custom_mobs.open_bestiary",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_B,
                "category.custom_mobs.general"
        );
        dev.architectury.registry.client.keymappings.KeyMappingRegistry.register(bestiaryKey);

        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> {
            if (client.player == null) return;
            while (bestiaryKey.consumeClick()) {
                client.setScreen(new ddraig.net.custommobs.client.gui.BestiaryScreen());
            }
        });
        // Register Entity Renderers
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB,
                ddraig.net.custommobs.client.renderer.CustomMobRenderer::new
        );
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                ddraig.net.custommobs.registry.ModEntities.CUSTOM_PROJECTILE,
                ddraig.net.custommobs.client.renderer.CustomProjectileRenderer::new
        );



        // Register S2C receivers
        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_CREATOR, (buf, context) -> {
            context.queue(() -> {
                Minecraft.getInstance().setScreen(new MobCreatorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_PROJECTILE_CREATOR, (buf, context) -> {
            context.queue(() -> {
                Minecraft.getInstance().setScreen(new ProjectileCreatorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_SPAWNER, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            int rate = buf.readInt();
            int radius = buf.readInt();
            int maxAlive = buf.readInt();
            int dayNight = buf.readInt();
            int playerDist = buf.readInt();
            String templateId = buf.readUtf();
            int eliteChance = buf.readInt();

            context.queue(() -> {
                Minecraft.getInstance().setScreen(new MobCreatorScreen.SpawnerEditScreen(
                        pos, rate, radius, maxAlive, dayNight, playerDist, templateId, eliteChance
                ));
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_RAID_EDITOR, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            String raidId = buf.readUtf();
            int radius = buf.readInt();
            int waveCooldown = buf.readInt();
            int raidCooldown = buf.readInt();
            String desc = buf.readUtf(262144);
            String wavesJson = buf.readUtf(262144);
            String rewardsJson = buf.readUtf(262144);

            context.queue(() -> {
                Minecraft.getInstance().setScreen(new ddraig.net.custommobs.client.gui.RaidEditorScreen(
                        pos, raidId, radius, waveCooldown, raidCooldown, desc, wavesJson, rewardsJson
                ));
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_TEMPLATES, (buf, context) -> {
            String mobJson = buf.readUtf(1048576);
            String projJson = buf.readUtf(1048576);
            String raidsJson = buf.readUtf(1048576);

            context.queue(() -> {
                try {
                    List<MobData> mobs = GSON.fromJson(mobJson, new TypeToken<List<MobData>>(){}.getType());
                    List<ProjectileData> projs = GSON.fromJson(projJson, new TypeToken<List<ProjectileData>>(){}.getType());
                    List<ddraig.net.custommobs.data.RaidSystem.RaidDefinition> raids = GSON.fromJson(raidsJson, new TypeToken<List<ddraig.net.custommobs.data.RaidSystem.RaidDefinition>>(){}.getType());

                    ddraig.net.custommobs.client.renderer.JavaModelLoader.clearCaches();
                    MobRegistry.loadedMobs.clear();
                    if (mobs != null) {
                        for (MobData m : mobs) {
                            MobRegistry.loadedMobs.put(m.id, m);
                        }
                    }

                    MobRegistry.loadedProjectiles.clear();
                    if (projs != null) {
                        for (ProjectileData p : projs) {
                            MobRegistry.loadedProjectiles.put(p.id, p);
                        }
                    }

                    ddraig.net.custommobs.data.RaidSystem.setClientRaids(raids);

                    MobRegistry.rebuildSuggestionsCache();

                    if (Minecraft.getInstance().screen instanceof ddraig.net.custommobs.client.gui.MobCreatorScreen creatorScreen) {
                        creatorScreen.init(Minecraft.getInstance(), creatorScreen.width, creatorScreen.height);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to sync templates on client: " + e.getMessage());
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_BESTIARY, (buf, context) -> {
            int size = buf.readInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add(buf.readUtf());
            }
            context.queue(() -> {
                clientDiscoveries.clear();
                clientDiscoveries.addAll(list);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_BESTIARY, (buf, context) -> {
            context.queue(() -> {
                Minecraft.getInstance().setScreen(new ddraig.net.custommobs.client.gui.BestiaryScreen());
            });
        });
    }

    public static void populateClientBiomes(List<String> cachedBiomes) {
        try {
            if (Minecraft.getInstance().level != null) {
                var registries = Minecraft.getInstance().level.registryAccess();
                var biomeRegistryOpt = registries.registry(net.minecraft.core.registries.Registries.BIOME);
                if (biomeRegistryOpt.isPresent()) {
                    for (var key : biomeRegistryOpt.get().keySet()) {
                        String b = key.toString();
                        if (!cachedBiomes.contains(b)) {
                            cachedBiomes.add(b);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
