package ddraig.net.custommobs.block.entity;

import ddraig.net.custommobs.registry.ModBlocks;
import ddraig.net.custommobs.registry.ModEntities;
import ddraig.net.custommobs.entity.CustomMobEntity;
import ddraig.net.custommobs.data.RaidSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class RaidBlockEntity extends BlockEntity {
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private String raidId = "";
    private int radius = 16;
    private int waveCooldown = 10; // seconds
    private int raidCooldown = 60; // seconds
    private String description = "";
    private List<RaidSystem.RaidWave> waves = new ArrayList<>();
    private List<RaidSystem.RaidReward> rewards = new ArrayList<>();

    // Runtime state variables
    public enum RaidState { IDLE, RUNNING, WAVE_COOLDOWN }
    private RaidState activeRaidState = RaidState.IDLE;
    private int currentWave = -1;
    private int cooldownTicksRemaining = 0;
    private int raidCooldownRemaining = 0;
    private final Set<UUID> spawnedMobUuids = new HashSet<>();
    public final Set<String> participatingPlayers = new HashSet<>();

    private int checkCooldown = 0;

    // Client-side display variables
    private double oSpin;
    private double spin;
    private int clientDisplayTimer = 0;
    private int clientDisplayIndex = 0;
    private String activeDisplayTemplate = "";

    private boolean registered = false;

    public RaidBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RAID_BLOCK_BE.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            RaidSystem.unregisterBlockEntity(this);
        }
    }

    public double getSpinAngle(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.oSpin, this.spin);
    }

    public String getActiveDisplayTemplate() {
        return activeDisplayTemplate;
    }

    public List<String> getNextWaveMobTemplates() {
        if (waves.isEmpty()) return List.of();
        int waveIdx = currentWave + 1;
        if (waveIdx < 0 || waveIdx >= waves.size()) waveIdx = 0;
        RaidSystem.RaidWave wave = waves.get(waveIdx);
        return new ArrayList<>(wave.mobCounts.keySet());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RaidBlockEntity spawner) {
        if (level.isClientSide) {
            spawner.oSpin = spawner.spin;
            spawner.spin = (spawner.spin + 2.5D) % 360.0D;

            // Spawn crimson particles
            if (level.random.nextInt(4) == 0) {
                double px = pos.getX() + level.random.nextDouble();
                double py = pos.getY() + level.random.nextDouble();
                double pz = pos.getZ() + level.random.nextDouble();
                level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                        px, py, pz,
                        0.0D, 0.0D, 0.0D
                );
                level.addParticle(
                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8F, 0.0F, 0.1F), 1.0F),
                        px, py, pz,
                        0.0D, 0.0D, 0.0D
                );
            }

            // Rotate client templates to show in spawner
            List<String> templates = spawner.getNextWaveMobTemplates();
            if (!templates.isEmpty()) {
                spawner.clientDisplayTimer++;
                if (spawner.clientDisplayTimer >= 60) {
                    spawner.clientDisplayTimer = 0;
                    spawner.clientDisplayIndex = (spawner.clientDisplayIndex + 1) % templates.size();
                }
                spawner.activeDisplayTemplate = templates.get(spawner.clientDisplayIndex);
            } else {
                spawner.activeDisplayTemplate = "";
            }
            return;
        }

        // Server-side Raid logic
        if (!spawner.registered) {
            RaidSystem.registerBlockEntity(spawner);
            spawner.registered = true;
        }

        if (spawner.activeRaidState == RaidState.IDLE) {
            if (spawner.raidCooldownRemaining > 0) {
                spawner.raidCooldownRemaining--;
            }
        } else if (spawner.activeRaidState == RaidState.RUNNING) {
            spawner.checkCooldown++;
            if (spawner.checkCooldown >= 40) {
                spawner.checkCooldown = 0;

                // Check player proximity to avoid despawning/ignoring the raid
                AABB playerSearchBox = new AABB(pos).inflate(Math.max(32.0, spawner.radius + 16.0));
                List<Player> playersInRange = level.getEntitiesOfClass(Player.class, playerSearchBox);
                if (playersInRange.isEmpty()) {
                    spawner.broadcastMessage(Component.translatable("chat.custom_mobs.raid.failed_no_players"));
                    spawner.abortRaidWithCooldown();
                    return;
                }
                for (Player p : playersInRange) {
                    spawner.participatingPlayers.add(p.getGameProfile().getName());
                }

                // Scan alive mobs for players who damaged them
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    for (UUID uuid : spawner.spawnedMobUuids) {
                        net.minecraft.world.entity.Entity ent = sl.getEntity(uuid);
                        if (ent instanceof net.minecraft.world.entity.LivingEntity living) {
                            Player p = getLastHurtByPlayerReflective(living);
                            if (p != null) {
                                spawner.participatingPlayers.add(p.getGameProfile().getName());
                            }
                        }
                    }
                }

                // Check if spawned mobs are all dead
                spawner.spawnedMobUuids.removeIf(uuid -> {
                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        net.minecraft.world.entity.Entity ent = sl.getEntity(uuid);
                        if (ent instanceof net.minecraft.world.entity.LivingEntity living) {
                            Player p = getLastHurtByPlayerReflective(living);
                            if (p != null) {
                                spawner.participatingPlayers.add(p.getGameProfile().getName());
                            }
                        }
                        return ent == null || !ent.isAlive();
                    }
                    return true;
                });

                if (spawner.spawnedMobUuids.isEmpty()) {
                    // Current wave cleared!
                    int nextWaveIdx = spawner.currentWave + 1;
                    if (nextWaveIdx >= spawner.waves.size()) {
                        // Raid victorious!
                        spawner.completeRaid();
                    } else {
                        // Start wave cooldown
                        spawner.activeRaidState = RaidState.WAVE_COOLDOWN;
                        spawner.cooldownTicksRemaining = spawner.waveCooldown * 20;
                        spawner.broadcastMessage(Component.translatable("chat.custom_mobs.raid.wave_cleared", spawner.currentWave + 1, spawner.waveCooldown));
                        spawner.setChanged();
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                }
            }
        } else if (spawner.activeRaidState == RaidState.WAVE_COOLDOWN) {
            spawner.cooldownTicksRemaining--;
            if (spawner.cooldownTicksRemaining <= 0) {
                spawner.startNextWave();
            }
        }
    }

    public void triggerRaidByPlayer(Player player) {
        triggerRaidByPlayer(player, false);
    }

    public void triggerRaidByPlayer(Player player, boolean ignoreCooldown) {
        if (level == null || level.isClientSide) return;
        if (activeRaidState != RaidState.IDLE) {
            player.sendSystemMessage(Component.translatable("chat.custom_mobs.raid.already_in_progress"));
            return;
        }
        if (!ignoreCooldown && raidCooldownRemaining > 0) {
            int seconds = raidCooldownRemaining / 20;
            player.sendSystemMessage(Component.translatable("chat.custom_mobs.raid.on_cooldown", seconds));
            return;
        }
        if (waves.isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.custom_mobs.raid.no_waves"));
            return;
        }

        activeRaidState = RaidState.RUNNING;
        currentWave = -1;
        spawnedMobUuids.clear();
        participatingPlayers.clear();
        participatingPlayers.add(player.getGameProfile().getName());
        
        broadcastMessage(Component.translatable("chat.custom_mobs.raid.triggered_by", raidId.isEmpty() ? "Unknown" : raidId, player.getScoreboardName()));
        startNextWave();
    }

    private void startNextWave() {
        if (level == null || level.isClientSide) return;
        currentWave++;
        activeRaidState = RaidState.RUNNING;
        
        if (currentWave >= waves.size()) {
            completeRaid();
            return;
        }

        broadcastMessage(Component.translatable("chat.custom_mobs.raid.wave_starting", currentWave + 1, waves.size()));

        RaidSystem.RaidWave wave = waves.get(currentWave);
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;

        for (Map.Entry<String, Integer> entry : wave.mobCounts.entrySet()) {
            String templateId = entry.getKey();
            int count = entry.getValue();
            int eliteChance = wave.mobEliteChances.getOrDefault(templateId, 0);

            for (int i = 0; i < count; i++) {
                // Find scattered spawn position inside radius (at least 4 blocks away from spawner center)
                double theta = level.random.nextDouble() * 2.0 * Math.PI;
                double d = 4.0 + level.random.nextDouble() * (Math.max(6.0, radius) - 4.0);
                double rx = worldPosition.getX() + Math.cos(theta) * d;
                double rz = worldPosition.getZ() + Math.sin(theta) * d;
                double ry = worldPosition.getY();

                net.minecraft.core.BlockPos spawnPos = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(rx, ry, rz)
                );

                if (ddraig.net.custommobs.data.MobRegistry.loadedMobs.containsKey(templateId)) {
                    CustomMobEntity customMob = ModEntities.CUSTOM_MOB.get().create(level);
                    if (customMob != null) {
                        customMob.setTemplateId(templateId);
                        customMob.spawnerPos = this.worldPosition;
                        if (level.random.nextInt(100) < eliteChance) {
                            customMob.setElite(true);
                        }
                        customMob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5, level.random.nextFloat() * 360F, 0.0F);
                        customMob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
                        
                        serverLevel.sendParticles(
                                net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                                spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                                15, 0.3, 0.3, 0.3, 0.1
                        );
                        
                        level.addFreshEntity(customMob);
                        spawnedMobUuids.add(customMob.getUUID());
                    }
                } else {
                    net.minecraft.resources.ResourceLocation resLoc = net.minecraft.resources.ResourceLocation.tryParse(templateId);
                    if (resLoc != null) {
                        if (resLoc.getNamespace().equals("minecraft") && !templateId.contains(":")) {
                            resLoc = new net.minecraft.resources.ResourceLocation("minecraft", templateId);
                        }
                        var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc);
                        if (entityTypeOpt.isPresent()) {
                            net.minecraft.world.entity.Entity entity = entityTypeOpt.get().create(level);
                            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                                mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5, level.random.nextFloat() * 360F, 0.0F);
                                mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
                                
                                serverLevel.sendParticles(
                                        net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                                        15, 0.3, 0.3, 0.3, 0.1
                                );
                                
                                level.addFreshEntity(mob);
                                spawnedMobUuids.add(mob.getUUID());
                            }
                        }
                    }
                }
            }
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void completeRaid() {
        broadcastMessage(Component.translatable("chat.custom_mobs.raid.victory"));

        if (level != null && !level.isClientSide) {
            // Final definitive scan of players in range at completion time
            AABB finalSearchBox = new AABB(worldPosition).inflate(Math.max(32.0, radius + 16.0));
            List<net.minecraft.world.entity.player.Player> playersInRange = level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, finalSearchBox);
            for (var p : playersInRange) {
                participatingPlayers.add(p.getGameProfile().getName());
            }

            // Find nearby participating player entities to drop items at their feet
            List<net.minecraft.world.entity.player.Player> participantEntities = new ArrayList<>();
            for (String name : participatingPlayers) {
                var p = level.getServer().getPlayerList().getPlayerByName(name);
                if (p != null && p.isAlive()) {
                    participantEntities.add(p);
                }
            }

            // 1. Drop item rewards
            for (RaidSystem.RaidReward reward : rewards) {
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
                            ddraig.net.custommobs.CustomMobs.LOGGER.error("Failed to parse reward NBT item stack: " + nbtStr, e);
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
                            // Drop at the spawner block
                            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                    level,
                                    worldPosition.getX() + 0.5,
                                    worldPosition.getY() + 1.2,
                                    worldPosition.getZ() + 0.5,
                                    stack.copy()
                            );
                            level.addFreshEntity(itemEntity);
                        }
                    }
                }
            }

                // 2. Perform command rewards
                if (level.getServer() != null) {
                    var server = level.getServer();
                    for (RaidSystem.RaidReward reward : rewards) {
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

            activeRaidState = RaidState.IDLE;
            currentWave = -1;
            spawnedMobUuids.clear();
            participatingPlayers.clear();
            raidCooldownRemaining = raidCooldown * 20;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        public void abortRaid() {
            if (level == null || level.isClientSide) return;
            broadcastMessage(Component.translatable("chat.custom_mobs.raid.aborted"));

            net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
            for (UUID uuid : spawnedMobUuids) {
                var entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
            
            activeRaidState = RaidState.IDLE;
            currentWave = -1;
            spawnedMobUuids.clear();
            participatingPlayers.clear();
            raidCooldownRemaining = 0;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        public void abortRaidWithCooldown() {
            if (level == null || level.isClientSide) return;

            net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level;
            for (UUID uuid : spawnedMobUuids) {
                var entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
            
            activeRaidState = RaidState.IDLE;
            currentWave = -1;
            spawnedMobUuids.clear();
            participatingPlayers.clear();
            raidCooldownRemaining = raidCooldown * 20;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        private void broadcastMessage(Component msg) {
            if (level != null && !level.isClientSide && level.getServer() != null) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        msg,
                        false
                );
            }
        }

    public void openEditScreen(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ddraig.net.custommobs.network.ModPackets.openRaidEditorUi(serverPlayer, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("RaidId", this.raidId);
        tag.putInt("Radius", this.radius);
        tag.putInt("WaveCooldown", this.waveCooldown);
        tag.putInt("RaidCooldown", this.raidCooldown);
        tag.putString("Description", this.description);
        tag.putString("WavesJson", GSON.toJson(this.waves));
        tag.putString("RewardsJson", GSON.toJson(this.rewards));

        tag.putString("RaidState", this.activeRaidState.name());
        tag.putInt("CurrentWave", this.currentWave);
        tag.putInt("CooldownTicksRemaining", this.cooldownTicksRemaining);
        tag.putInt("RaidCooldownRemaining", this.raidCooldownRemaining);

        ListTag uuidsTag = new ListTag();
        for (UUID uuid : spawnedMobUuids) {
            uuidsTag.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString()));
        }
        tag.put("SpawnedMobUuids", uuidsTag);

        ListTag playersTag = new ListTag();
        for (String p : participatingPlayers) {
            playersTag.add(net.minecraft.nbt.StringTag.valueOf(p));
        }
        tag.put("ParticipatingPlayers", playersTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.raidId = tag.getString("RaidId");
        this.radius = tag.getInt("Radius");
        this.waveCooldown = tag.getInt("WaveCooldown");
        this.raidCooldown = tag.getInt("RaidCooldown");
        this.description = tag.getString("Description");

        if (this.radius <= 0) this.radius = 16;
        if (this.waveCooldown <= 0) this.waveCooldown = 10;
        if (this.raidCooldown <= 0) this.raidCooldown = 60;

        try {
            String wavesJson = tag.getString("WavesJson");
            if (!wavesJson.isEmpty()) {
                this.waves = GSON.fromJson(wavesJson, new com.google.gson.reflect.TypeToken<List<RaidSystem.RaidWave>>(){}.getType());
            }
        } catch (Exception ignored) {}

        try {
            String rewardsJson = tag.getString("RewardsJson");
            if (!rewardsJson.isEmpty()) {
                try {
                    this.rewards = GSON.fromJson(rewardsJson, new com.google.gson.reflect.TypeToken<List<RaidSystem.RaidReward>>(){}.getType());
                } catch (Exception e) {
                    List<String> oldRewards = GSON.fromJson(rewardsJson, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                    this.rewards = new ArrayList<>();
                    if (oldRewards != null) {
                        for (String s : oldRewards) {
                            this.rewards.add(new RaidSystem.RaidReward(s));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            this.activeRaidState = RaidState.valueOf(tag.getString("RaidState"));
        } catch (Exception e) {
            this.activeRaidState = RaidState.IDLE;
        }
        this.currentWave = tag.getInt("CurrentWave");
        this.cooldownTicksRemaining = tag.getInt("CooldownTicksRemaining");
        this.raidCooldownRemaining = tag.getInt("RaidCooldownRemaining");

        this.spawnedMobUuids.clear();
        if (tag.contains("SpawnedMobUuids", 9)) {
            ListTag list = tag.getList("SpawnedMobUuids", 8);
            for (int i = 0; i < list.size(); i++) {
                try {
                    this.spawnedMobUuids.add(UUID.fromString(list.getString(i)));
                } catch (Exception ignored) {}
            }
        }

        this.participatingPlayers.clear();
        if (tag.contains("ParticipatingPlayers", 9)) {
            ListTag list = tag.getList("ParticipatingPlayers", 8);
            for (int i = 0; i < list.size(); i++) {
                this.participatingPlayers.add(list.getString(i));
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    // Getters and Setters
    public String getRaidId() { return raidId; }
    public RaidState getActiveRaidState() { return this.activeRaidState; }
    public int getCurrentWave() { return this.currentWave; }
    public void setRaidId(String raidId) { this.raidId = raidId; setChanged(); }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; setChanged(); }

    public int getWaveCooldown() { return waveCooldown; }
    public void setWaveCooldown(int waveCooldown) { this.waveCooldown = waveCooldown; setChanged(); }

    public int getRaidCooldown() { return raidCooldown; }
    public void setRaidCooldown(int raidCooldown) { this.raidCooldown = raidCooldown; setChanged(); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; setChanged(); }

    public List<RaidSystem.RaidWave> getWaves() { return waves; }
    public void setWaves(List<RaidSystem.RaidWave> waves) { this.waves = waves; setChanged(); }

    public List<RaidSystem.RaidReward> getRewards() { return rewards; }
    public void setRewards(List<RaidSystem.RaidReward> rewards) { this.rewards = rewards; setChanged(); }

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
