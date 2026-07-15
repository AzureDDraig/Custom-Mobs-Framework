package ddraig.net.custommobs.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ddraig.net.custommobs.CustomMobs;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.ProjectileData;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.block.entity.RPGMobSpawnerBlockEntity;
import ddraig.net.custommobs.data.DatabaseManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class ModPackets {
    private static final Gson GSON = new GsonBuilder().create();

    public static final ResourceLocation S2C_OPEN_CREATOR = new ResourceLocation(CustomMobs.MOD_ID, "open_creator");
    public static final ResourceLocation S2C_OPEN_PROJECTILE_CREATOR = new ResourceLocation(CustomMobs.MOD_ID, "open_projectile_creator");
    public static final ResourceLocation S2C_OPEN_SPAWNER = new ResourceLocation(CustomMobs.MOD_ID, "open_spawner");
    public static final ResourceLocation S2C_OPEN_RAID_EDITOR = new ResourceLocation(CustomMobs.MOD_ID, "open_raid_editor");
    public static final ResourceLocation S2C_SYNC_TEMPLATES = new ResourceLocation(CustomMobs.MOD_ID, "sync_templates");
    public static final ResourceLocation S2C_OPEN_BESTIARY = new ResourceLocation(CustomMobs.MOD_ID, "open_bestiary");
    public static final ResourceLocation S2C_SYNC_BESTIARY = new ResourceLocation(CustomMobs.MOD_ID, "sync_bestiary");

    public static final ResourceLocation C2S_SAVE_MOB_TEMPLATE = new ResourceLocation(CustomMobs.MOD_ID, "save_mob_template");
    public static final ResourceLocation C2S_SAVE_PROJECTILE_TEMPLATE = new ResourceLocation(CustomMobs.MOD_ID, "save_projectile_template");
    public static final ResourceLocation C2S_SAVE_SPAWNER_SETTINGS = new ResourceLocation(CustomMobs.MOD_ID, "save_spawner_settings");
    public static final ResourceLocation C2S_SAVE_RAID_SETTINGS = new ResourceLocation(CustomMobs.MOD_ID, "save_raid_settings");
    public static final ResourceLocation C2S_DELETE_MOB_TEMPLATE = new ResourceLocation(CustomMobs.MOD_ID, "delete_mob_template");
    public static final ResourceLocation C2S_DELETE_PROJECTILE_TEMPLATE = new ResourceLocation(CustomMobs.MOD_ID, "delete_projectile_template");

    public static void init() {
        // C2S Save Mob Template
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_MOB_TEMPLATE, (buf, context) -> {
            String oldId = buf.readUtf();
            String json = buf.readUtf(262144);
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    try {
                        MobData mob = GSON.fromJson(json, MobData.class);
                        if (mob != null && !mob.id.isEmpty()) {
                            MobRegistry.saveMob(mob, oldId);
                            MobRegistry.packMob(mob.id);
                            // Sync back to all players
                            syncTemplatesToAll(player.server);
                        }
                    } catch (Exception e) {
                        CustomMobs.LOGGER.error("Failed to parse/save mob template on server", e);
                    }
                }
            });
        });

        // C2S Save Projectile Template
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_PROJECTILE_TEMPLATE, (buf, context) -> {
            String oldId = buf.readUtf();
            String json = buf.readUtf(262144);
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    try {
                        ProjectileData proj = GSON.fromJson(json, ProjectileData.class);
                        if (proj != null && !proj.id.isEmpty()) {
                            MobRegistry.saveProjectile(proj, oldId);
                            MobRegistry.packProjectile(proj.id);
                            syncTemplatesToAll(player.server);
                        }
                    } catch (Exception e) {
                        CustomMobs.LOGGER.error("Failed to parse/save projectile template on server", e);
                    }
                }
            });
        });

        // C2S Save Spawner Settings
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_SPAWNER_SETTINGS, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            int rate = buf.readInt();
            int radius = buf.readInt();
            int maxAlive = buf.readInt();
            int dayNight = buf.readInt();
            int playerDist = buf.readInt();
            String templateId = buf.readUtf();
            int eliteChance = buf.readInt();
            boolean redstonePulseOnly = buf.readBoolean();
            int spawnerCooldown = buf.readInt();

            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    BlockEntity be = player.level().getBlockEntity(pos);
                    if (be instanceof RPGMobSpawnerBlockEntity spawner) {
                        spawner.setSpawnRate(rate);
                        spawner.setSpawnRadius(radius);
                        spawner.setMaxAlive(maxAlive);
                        spawner.setDayNight(dayNight);
                        spawner.setPlayerDistance(playerDist);
                        spawner.setTemplateId(templateId);
                        spawner.setEliteChance(eliteChance);
                        spawner.setRedstonePulseOnly(redstonePulseOnly);
                        spawner.setSpawnerCooldown(spawnerCooldown);
                        
                        player.level().sendBlockUpdated(pos, spawner.getBlockState(), spawner.getBlockState(), 3);
                    }
                }
            });
        });

        // C2S Save Raid Settings
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_RAID_SETTINGS, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            String raidId = buf.readUtf();
            int radius = buf.readInt();
            int waveCooldown = buf.readInt();
            int raidCooldown = buf.readInt();
            String desc = buf.readUtf(262144);
            String wavesJson = buf.readUtf(262144);
            String rewardsJson = buf.readUtf(262144);

            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    BlockEntity be = player.level().getBlockEntity(pos);
                    if (be instanceof ddraig.net.custommobs.block.entity.RaidBlockEntity spawner) {
                        spawner.setRaidId(raidId);
                        spawner.setRadius(radius);
                        spawner.setWaveCooldown(waveCooldown);
                        spawner.setRaidCooldown(raidCooldown);
                        spawner.setDescription(desc);

                        try {
                            List<RaidSystem.RaidWave> parsedWaves = GSON.fromJson(wavesJson, new TypeToken<List<RaidSystem.RaidWave>>(){}.getType());
                            if (parsedWaves != null) spawner.setWaves(parsedWaves);
                        } catch (Exception ignored) {}

                        try {
                            List<RaidSystem.RaidReward> parsedRewards = GSON.fromJson(rewardsJson, new TypeToken<List<RaidSystem.RaidReward>>(){}.getType());
                            if (parsedRewards != null) spawner.setRewards(parsedRewards);
                        } catch (Exception ignored) {}

                        player.level().sendBlockUpdated(pos, spawner.getBlockState(), spawner.getBlockState(), 3);

                        // Save this raid definition globally to raids.json database
                        RaidSystem.RaidDefinition def = new RaidSystem.RaidDefinition();
                        def.raidId = raidId;
                        def.waves = spawner.getWaves();
                        def.rewards = spawner.getRewards();
                        RaidSystem.saveOrUpdateRaid(def);

                        // Sync templates and global raids to all players
                        syncTemplatesToAll(player.server);
                    }
                }
            });
        });

        // C2S Delete Mob Template
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_DELETE_MOB_TEMPLATE, (buf, context) -> {
            String mobId = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    MobRegistry.deleteMob(mobId);
                    syncTemplatesToAll(player.server);
                }
            });
        });

        // C2S Delete Projectile Template
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_DELETE_PROJECTILE_TEMPLATE, (buf, context) -> {
            String projId = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    MobRegistry.deleteProjectile(projId);
                    syncTemplatesToAll(player.server);
                }
            });
        });
    }

    public static void openCreatorUi(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToPlayer(player, S2C_OPEN_CREATOR, buf);
    }

    public static void openProjectileCreatorUi(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToPlayer(player, S2C_OPEN_PROJECTILE_CREATOR, buf);
    }

    public static void openSpawnerUi(ServerPlayer player, RPGMobSpawnerBlockEntity spawner) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(spawner.getBlockPos());
        buf.writeInt(spawner.getSpawnRate());
        buf.writeInt(spawner.getSpawnRadius());
        buf.writeInt(spawner.getMaxAlive());
        buf.writeInt(spawner.getDayNight());
        buf.writeInt(spawner.getPlayerDistance());
        buf.writeUtf(spawner.getTemplateId());
        buf.writeInt(spawner.getEliteChance());
        buf.writeBoolean(spawner.isRedstonePulseOnly());
        buf.writeInt(spawner.getSpawnerCooldown());

        NetworkManager.sendToPlayer(player, S2C_OPEN_SPAWNER, buf);
    }

    public static void openRaidEditorUi(ServerPlayer player, ddraig.net.custommobs.block.entity.RaidBlockEntity spawner) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(spawner.getBlockPos());
        buf.writeUtf(spawner.getRaidId());
        buf.writeInt(spawner.getRadius());
        buf.writeInt(spawner.getWaveCooldown());
        buf.writeInt(spawner.getRaidCooldown());
        buf.writeUtf(spawner.getDescription(), 262144);
        buf.writeUtf(GSON.toJson(spawner.getWaves()), 262144);
        buf.writeUtf(GSON.toJson(spawner.getRewards()), 262144);

        NetworkManager.sendToPlayer(player, S2C_OPEN_RAID_EDITOR, buf);
    }

    public static void syncTemplates(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        
        String mobJson = GSON.toJson(new ArrayList<>(MobRegistry.loadedMobs.values()));
        String projJson = GSON.toJson(new ArrayList<>(MobRegistry.loadedProjectiles.values()));
        String raidsJson = GSON.toJson(new ArrayList<>(RaidSystem.getRaids()));
        
        buf.writeUtf(mobJson, 1048576);
        buf.writeUtf(projJson, 1048576);
        buf.writeUtf(raidsJson, 1048576);

        NetworkManager.sendToPlayer(player, S2C_SYNC_TEMPLATES, buf);
        syncBestiaryDiscoveries(player);
    }

    public static void syncBestiaryDiscoveries(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        List<String> list = DatabaseManager.bestiaryCache.getOrDefault(player.getUUID(), new java.util.concurrent.CopyOnWriteArrayList<>());
        buf.writeInt(list.size());
        for (String id : list) {
            buf.writeUtf(id);
        }
        NetworkManager.sendToPlayer(player, S2C_SYNC_BESTIARY, buf);
    }

    public static void reapplyTemplatesToAllEntities(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof ddraig.net.custommobs.entity.CustomMobEntity customMob) {
                    customMob.reapplyTemplate();
                }
            }
        }
    }

    public static void syncTemplatesToAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        reapplyTemplatesToAllEntities(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTemplates(player);
        }
    }
}
