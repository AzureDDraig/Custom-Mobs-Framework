package ddraig.net.custommobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ddraig.net.custommobs.CustomMobs;
import dev.architectury.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import ddraig.net.custommobs.block.entity.RaidBlockEntity;

public class RaidSystem {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, RaidDefinition> raids = new ConcurrentHashMap<>();
    public static final Map<String, ActiveRaid> activeRaids = new ConcurrentHashMap<>();
    private static final List<RaidBlockEntity> activeBlockEntities = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static File raidsFile;

    public static void registerBlockEntity(RaidBlockEntity be) {
        if (!activeBlockEntities.contains(be)) {
            activeBlockEntities.add(be);
        }
    }

    public static void unregisterBlockEntity(RaidBlockEntity be) {
        activeBlockEntities.remove(be);
    }

    public static List<RaidBlockEntity> getActiveBlockEntities() {
        return activeBlockEntities;
    }

    public static void init() {
        File baseDir = new File(Platform.getConfigFolder().toFile(), "CustomMobs");
        if (!baseDir.exists()) baseDir.mkdirs();
        raidsFile = new File(baseDir, "raids.json");
        loadRaids();
    }

    public static void setClientRaids(List<RaidDefinition> list) {
        raids.clear();
        if (list != null) {
            for (RaidDefinition rd : list) {
                raids.put(rd.raidId, rd);
            }
        }
    }

    public static void loadRaids() {
        raids.clear();
        if (raidsFile.exists()) {
            try (FileReader reader = new FileReader(raidsFile)) {
                List<RaidDefinition> loaded = GSON.fromJson(reader, new TypeToken<List<RaidDefinition>>(){}.getType());
                if (loaded != null) {
                    for (RaidDefinition rd : loaded) {
                        raids.put(rd.raidId, rd);
                    }
                }
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to load raids from json", e);
            }
        }
    }

    public static void saveRaids() {
        try (FileWriter writer = new FileWriter(raidsFile)) {
            GSON.toJson(new ArrayList<>(raids.values()), writer);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to save raids to json", e);
        }
    }

    public static void saveOrUpdateRaid(RaidDefinition rd) {
        if (rd == null || rd.raidId.isEmpty()) return;
        raids.put(rd.raidId, rd);
        saveRaids();
    }

    public static Collection<RaidDefinition> getRaids() {
        return raids.values();
    }

    public static void addRaidReward(String raidId, String reward) {
        RaidDefinition rd = raids.computeIfAbsent(raidId, id -> {
            RaidDefinition newRd = new RaidDefinition();
            newRd.raidId = id;
            return newRd;
        });
        rd.rewards.add(new RaidReward(reward));
        saveRaids();
    }

    public static RaidDefinition getRaid(String id) {
        if (id == null) return null;
        RaidDefinition rd = raids.get(id);
        if (rd != null) return rd;

        String normId = id.replace('_', ' ').trim();
        for (RaidDefinition definition : raids.values()) {
            if (definition.raidId.equalsIgnoreCase(id) || definition.raidId.equalsIgnoreCase(normId)) {
                return definition;
            }
            String defNorm = definition.raidId.replace('_', ' ').trim();
            if (defNorm.equalsIgnoreCase(normId)) {
                return definition;
            }
        }
        return null;
    }

    public static boolean deleteRaid(String id) {
        if (id == null) return false;
        RaidDefinition rd = getRaid(id);
        if (rd != null) {
            raids.remove(rd.raidId);
            saveRaids();
            return true;
        }
        return false;
    }

    public static void startRaid(MinecraftServer server, String raidId, ServerLevel level, BlockPos pos) {
        RaidDefinition rd = raids.get(raidId);
        if (rd == null) {
            rd = new RaidDefinition();
            rd.raidId = raidId;
            rd.rewards.add(new RaidReward("/give <player> minecraft:diamond 5"));
            raids.put(raidId, rd);
            saveRaids();
        }

        ActiveRaid active = new ActiveRaid(rd, pos, level.dimension());
        activeRaids.put(raidId, active);
        active.startNextWave(server, level);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<String, ActiveRaid>> it = activeRaids.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveRaid> entry = it.next();
            ActiveRaid active = entry.getValue();
            active.tick(server);
            if (active.isFinished()) {
                active.distributeRewards(server);
                it.remove();
            }
        }
    }

    public static void onMobDeath(UUID mobUuid) {
        for (ActiveRaid ar : activeRaids.values()) {
            if (ar.spawnedMobUuids.remove(mobUuid)) {
                break;
            }
        }
    }

    public static boolean stopRaid(MinecraftServer server, String raidId) {
        ActiveRaid ar = activeRaids.remove(raidId);
        if (ar != null) {
            ar.discardSpawnedMobs(server);
            return true;
        }
        return false;
    }

    public static List<String> getActiveRaidIds() {
        return new ArrayList<>(activeRaids.keySet());
    }

    public static class RaidReward {
        public String value = "";
        public double chance = 1.0;
        public boolean perPlayer = false;

        public RaidReward() {}
        public RaidReward(String value) {
            this.value = value;
            if (value.trim().startsWith("/")) {
                this.perPlayer = true;
            }
        }
        public RaidReward(String value, double chance, boolean perPlayer) {
            this.value = value;
            this.chance = chance;
            this.perPlayer = perPlayer;
        }
    }

    public static class RaidDefinition {
        public String raidId = "";
        public int radius = 16;
        public int waveCooldown = 10;
        public int raidCooldown = 60;
        public String description = "";
        public List<RaidWave> waves = new ArrayList<>();
        public List<RaidReward> rewards = new ArrayList<>();

        public RaidDefinition() {
            // Default setup with waves if created empty
            waves.add(new RaidWave(Map.of("zombie_warrior", 5)));
            waves.add(new RaidWave(Map.of("zombie_warrior", 10)));
        }
    }

    public static class RaidWave {
        public Map<String, Integer> mobCounts = new HashMap<>(); // Template ID -> Count
        public Map<String, Integer> mobEliteChances = new HashMap<>(); // Template ID -> Elite Chance (0-100)

        public RaidWave() {}
        public RaidWave(Map<String, Integer> counts) {
            this.mobCounts = counts;
        }
    }

    public static class ActiveRaid {
        private final RaidDefinition definition;
        private final BlockPos spawnPos;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        public final Set<String> participatingPlayers = new HashSet<>();
        public final Set<UUID> spawnedMobUuids = new HashSet<>();
        private int currentWave = -1;
        private boolean finished = false;
        private int checkCooldown = 0;

        public ActiveRaid(RaidDefinition def, BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
            this.definition = def;
            this.spawnPos = pos;
            this.dimension = dimension;
        }

        public void startNextWave(MinecraftServer server, ServerLevel level) {
            currentWave++;
            if (currentWave >= definition.waves.size()) {
                finished = true;
                return;
            }

            RaidWave wave = definition.waves.get(currentWave);
            for (Map.Entry<String, Integer> entry : wave.mobCounts.entrySet()) {
                String templateId = entry.getKey();
                int count = entry.getValue();
                
                MobData mobTemplate = MobRegistry.loadedMobs.get(templateId);
                if (mobTemplate == null) continue;

                for (int i = 0; i < count; i++) {
                    double theta = level.random.nextDouble() * 2.0 * Math.PI;
                    double d = 4.0 + level.random.nextDouble() * (Math.max(6.0, definition.radius) - 4.0);
                    double rx = spawnPos.getX() + Math.cos(theta) * d;
                    double rz = spawnPos.getZ() + Math.sin(theta) * d;
                    
                    BlockPos targetPos = level.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            BlockPos.containing(rx, spawnPos.getY(), rz)
                    );
                    
                    var entity = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(level);
                    if (entity != null) {
                        entity.setTemplateId(templateId);
                        entity.activeRaidId = definition.raidId;
                        entity.moveTo(targetPos.getX() + 0.5, targetPos.getY() + 0.1, targetPos.getZ() + 0.5, level.random.nextFloat() * 360F, 0.0F);
                        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(targetPos), MobSpawnType.EVENT, null, null);
                        level.addFreshEntity(entity);
                        spawnedMobUuids.add(entity.getUUID());
                    }
                }
            }

            // Broadcast message
            server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("chat.custom_mobs.raid.wave_starting", currentWave + 1, definition.waves.size()),
                    false
            );
        }

        public void tick(MinecraftServer server) {
            if (finished) return;

            checkCooldown++;
            if (checkCooldown >= 40) { // Every 2 seconds
                checkCooldown = 0;
                
                ServerLevel level = server.getLevel(this.dimension);
                if (level != null) {
                    // Check player range
                    AABB playerBox = new AABB(spawnPos).inflate(Math.max(32.0, definition.radius + 16.0));
                    List<Player> players = level.getEntitiesOfClass(Player.class, playerBox);
                    boolean hasPlayer = !players.isEmpty();
                    if (!hasPlayer) {
                        server.getPlayerList().broadcastSystemMessage(
                                net.minecraft.network.chat.Component.translatable("chat.custom_mobs.raid.failed_no_players"),
                                false
                        );
                        discardSpawnedMobs(server);
                        finished = true;
                        return;
                    }
                    for (Player p : players) {
                        participatingPlayers.add(p.getGameProfile().getName());
                    }

                    // Scan alive mobs for players who damaged them
                    for (UUID uuid : spawnedMobUuids) {
                        net.minecraft.world.entity.Entity ent = level.getEntity(uuid);
                        if (ent instanceof net.minecraft.world.entity.LivingEntity living) {
                            Player p = getLastHurtByPlayerReflective(living);
                            if (p != null) {
                                participatingPlayers.add(p.getGameProfile().getName());
                            }
                        }
                    }

                    // Clean up dead/missing UUIDs and collect final attackers
                    spawnedMobUuids.removeIf(uuid -> {
                        net.minecraft.world.entity.Entity ent = level.getEntity(uuid);
                        if (ent instanceof net.minecraft.world.entity.LivingEntity living) {
                            Player p = getLastHurtByPlayerReflective(living);
                            if (p != null) {
                                participatingPlayers.add(p.getGameProfile().getName());
                            }
                        }
                        return ent == null || !ent.isAlive();
                    });

                    // Check if wave is cleared
                    if (spawnedMobUuids.isEmpty()) {
                        startNextWave(server, level);
                    }
                }
            }
        }

        public boolean isFinished() {
            return finished;
        }

        public void distributeRewards(MinecraftServer server) {
            server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("chat.custom_mobs.raid.defeated_location"),
                    false
            );

            ServerLevel level = server.getLevel(this.dimension);
            if (level != null) {
                // Final definitive scan of players in range at completion time
                AABB finalSearchBox = new AABB(spawnPos).inflate(Math.max(32.0, definition.radius + 16.0));
                List<Player> playersInRange = level.getEntitiesOfClass(Player.class, finalSearchBox);
                for (var p : playersInRange) {
                    participatingPlayers.add(p.getGameProfile().getName());
                }

                for (String name : participatingPlayers) {
                    net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                    if (p != null) {
                        DatabaseManager.discoverMob(p.getUUID(), definition.raidId);
                        ddraig.net.custommobs.network.ModPackets.syncBestiaryDiscoveries(p);
                    }
                }

                // Find nearby participating player entities to drop items at their feet
                List<Player> participantEntities = new ArrayList<>();
                for (String name : participatingPlayers) {
                    var p = server.getPlayerList().getPlayerByName(name);
                    if (p != null && p.isAlive()) {
                        participantEntities.add(p);
                    }
                }

                // 1. Drop item rewards
                for (RaidReward reward : definition.rewards) {
                    String cmd = reward.value.trim();
                    double rand = level.random.nextDouble();
                    if (rand > reward.chance) continue;

                    if (!cmd.isEmpty() && !cmd.startsWith("/")) {
                        net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.EMPTY;
                        if (cmd.startsWith("nbt:")) {
                            String nbtStr = cmd.substring(4).trim();
                            try {
                                net.minecraft.nbt.CompoundTag compound = net.minecraft.nbt.TagParser.parseTag(nbtStr);
                                stack = net.minecraft.world.item.ItemStack.of(compound);
                            } catch (Exception e) {
                                CustomMobs.LOGGER.error("Failed to parse reward NBT item stack: " + nbtStr, e);
                            }
                        } else {
                            String[] split = cmd.split(" ");
                            String itemId = split[0];
                            int count = 1;
                            if (split.length > 1) {
                                try {
                                    count = Integer.parseInt(split[1]);
                                } catch (Exception ignored) {}
                            }
                            try {
                                net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(itemId);
                                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                    stack = new net.minecraft.world.item.ItemStack(item, count);
                                }
                            } catch (Exception ignored) {}
                        }

                        if (!stack.isEmpty()) {
                            if (reward.perPlayer) {
                                // Drop at participants' feet
                                for (var p : participantEntities) {
                                    net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                            level,
                                            p.getX(),
                                            p.getY() + 0.5,
                                            p.getZ(),
                                            stack.copy()
                                    );
                                    level.addFreshEntity(itemEntity);
                                }
                            } else {
                                // Drop at the spawner spawnPos
                                net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                        level,
                                        spawnPos.getX() + 0.5,
                                        spawnPos.getY() + 1.2,
                                        spawnPos.getZ() + 0.5,
                                        stack.copy()
                                );
                                level.addFreshEntity(itemEntity);
                            }
                        }
                    }
                }

                // 2. Perform command rewards
                for (RaidReward reward : definition.rewards) {
                    String cmd = reward.value.trim();
                    double rand = level.random.nextDouble();
                    if (rand > reward.chance) continue;

                    if (cmd.startsWith("/")) {
                        String rawCmd = cmd.substring(1);
                        if (reward.perPlayer || rawCmd.contains("<player>")) {
                            for (String playerName : participatingPlayers) {
                                String execCmd = rawCmd.replace("<player>", playerName);
                                server.getCommands().performPrefixedCommand(
                                        server.createCommandSourceStack().withPermission(4),
                                        execCmd
                                );
                            }
                        } else {
                            server.getCommands().performPrefixedCommand(
                                    server.createCommandSourceStack().withPermission(4),
                                    rawCmd
                            );
                        }
                    }
                }
            }
        }

        public void discardSpawnedMobs(MinecraftServer server) {
            ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
            if (level != null) {
                for (UUID uuid : spawnedMobUuids) {
                    var entity = level.getEntity(uuid);
                    if (entity != null) {
                        entity.discard();
                    }
                }
            }
            spawnedMobUuids.clear();
        }

        private static Player getLastHurtByPlayerReflective(net.minecraft.world.entity.LivingEntity living) {
            try {
                try {
                    java.lang.reflect.Method m = net.minecraft.world.entity.LivingEntity.class.getMethod("getLastHurtByPlayer");
                    return (Player) m.invoke(living);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getField("lastHurtByPlayer");
                        return (Player) f.get(living);
                    } catch (Exception e2) {
                        java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("lastHurtByPlayer");
                        f.setAccessible(true);
                        return (Player) f.get(living);
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}
