package ddraig.net.custommobs.block.entity;

import ddraig.net.custommobs.registry.ModBlocks;
import ddraig.net.custommobs.registry.ModEntities;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class RPGMobSpawnerBlockEntity extends BlockEntity {
    private int spawnRate = 200; // 10 seconds default
    private int spawnRadius = 16;
    private int maxAlive = 5;
    private int dayNight = 0; // 0 = Both, 1 = Day only, 2 = Night only
    private int playerDistance = 16;
    private String templateId = "";
    private int eliteChance = 0;
    private boolean redstonePulseOnly = false;
    private int spawnerCooldown = 0;
    private int cooldownTimer = 0;
    private boolean wasPowered = false;
    
    private int tickCounter = 0;

    public RPGMobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RPG_MOB_SPAWNER_BE.get(), pos, state);
    }

    private double oSpin;
    private double spin;

    public double getSpinAngle(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.oSpin, this.spin);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RPGMobSpawnerBlockEntity spawner) {
        if (level.isClientSide) {
            spawner.oSpin = spawner.spin;
            int rateVal = spawner.spawnerCooldown > 0 ? spawner.spawnerCooldown * 20 : spawner.spawnRate;
            spawner.spin = (spawner.spin + (double)(1000.0F / ((float)rateVal + 200.0F))) % 360.0D;
            if (!spawner.templateId.isEmpty() && level.random.nextInt(4) == 0) {
                double px = pos.getX() + level.random.nextDouble();
                double py = pos.getY() + level.random.nextDouble();
                double pz = pos.getZ() + level.random.nextDouble();
                level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.WITCH,
                        px, py, pz,
                        0.0D, 0.0D, 0.0D
                );
                level.addParticle(
                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.50F, 0.13F, 0.69F), 1.0F),
                        px, py, pz,
                        0.0D, 0.0D, 0.0D
                );
            }
            return;
        }

        if (spawner.templateId.isEmpty()) {
            return;
        }

        if (spawner.cooldownTimer > 0) {
            spawner.cooldownTimer--;
        }

        boolean isPowered = level.hasNeighborSignal(pos);
        if (spawner.redstonePulseOnly) {
            boolean pulse = isPowered && !spawner.wasPowered;
            spawner.wasPowered = isPowered;
            if (pulse) {
                if (spawner.cooldownTimer <= 0) {
                    attemptSpawn(level, pos, spawner);
                    spawner.cooldownTimer = spawner.spawnerCooldown * 20;
                }
            }
        } else {
            spawner.wasPowered = isPowered;
            spawner.tickCounter++;
            int effectiveRate = spawner.spawnerCooldown > 0 ? spawner.spawnerCooldown * 20 : spawner.spawnRate;
            if (spawner.tickCounter >= effectiveRate) {
                spawner.tickCounter = 0;
                if (spawner.cooldownTimer <= 0) {
                    attemptSpawn(level, pos, spawner);
                    spawner.cooldownTimer = spawner.spawnerCooldown * 20;
                }
            }
        }
    }

    private static boolean attemptSpawn(Level level, BlockPos pos, RPGMobSpawnerBlockEntity spawner) {
        // Check player distance
        boolean playerNearby = level.hasNearbyAlivePlayer(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 
                spawner.playerDistance
        );
        if (!playerNearby) {
            return false;
        }

        // Check day/night
        boolean isDay = level.isDay();
        if (spawner.dayNight == 1 && !isDay) return false;
        if (spawner.dayNight == 2 && isDay) return false;

        // Check active mob limit within radius
        AABB area = new AABB(pos).inflate(spawner.spawnRadius);
        List<CustomMobEntity> activeMobs = level.getEntitiesOfClass(
                CustomMobEntity.class, 
                area, 
                mob -> spawner.templateId.equals(mob.getTemplateId())
        );

        if (activeMobs.size() < spawner.maxAlive) {
            // Find a spawn position (try a few times)
            for (int i = 0; i < 5; i++) {
                double spawnX = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * spawner.spawnRadius * 2;
                double spawnY = pos.getY() + level.random.nextInt(3) - 1;
                double spawnZ = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * spawner.spawnRadius * 2;
                BlockPos spawnPos = new BlockPos((int)spawnX, (int)spawnY, (int)spawnZ);
                boolean validSpawnSpot = false;
                ddraig.net.custommobs.data.MobData data = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(spawner.templateId);
                if (data != null) {
                    if (data.spawnRules.aquatic) {
                        validSpawnSpot = level.getFluidState(spawnPos).is(net.minecraft.tags.FluidTags.WATER);
                    } else if (data.spawnRules.lava) {
                        validSpawnSpot = level.getFluidState(spawnPos).is(net.minecraft.tags.FluidTags.LAVA);
                    } else {
                        BlockPos below = spawnPos.below();
                        net.minecraft.world.level.block.state.BlockState belowState = level.getBlockState(below);
                        net.minecraft.world.level.block.state.BlockState spawnState = level.getBlockState(spawnPos);
                        
                        boolean canSpawnInside = spawnState.isAir() 
                                || spawnState.is(net.minecraft.world.level.block.Blocks.WATER)
                                || spawnState.getCollisionShape(level, spawnPos).isEmpty();
                                
                        boolean validFloor = belowState.isValidSpawn(level, below, ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get())
                                || !belowState.getCollisionShape(level, below).isEmpty();
                                
                        validSpawnSpot = canSpawnInside && validFloor;
                    }
                } else {
                    BlockPos below = spawnPos.below();
                    net.minecraft.world.level.block.state.BlockState belowState = level.getBlockState(below);
                    net.minecraft.world.level.block.state.BlockState spawnState = level.getBlockState(spawnPos);
                    
                    boolean canSpawnInside = spawnState.isAir() || spawnState.getCollisionShape(level, spawnPos).isEmpty();
                    boolean validFloor = belowState.isValidSpawn(level, below, ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get())
                            || !belowState.getCollisionShape(level, below).isEmpty();
                            
                    validSpawnSpot = canSpawnInside && validFloor;
                }

                if (validSpawnSpot) {
                    if (ddraig.net.custommobs.data.MobRegistry.loadedMobs.containsKey(spawner.templateId)) {
                        CustomMobEntity customMob = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(level);
                        if (customMob != null) {
                            customMob.setTemplateId(spawner.templateId);
                            if (level.random.nextInt(100) < spawner.eliteChance) {
                                customMob.setElite(true);
                            }
                            customMob.moveTo(spawnX, spawnY, spawnZ, level.random.nextFloat() * 360F, 0.0F);
                            if (!level.noCollision(customMob) || !level.isUnobstructed(customMob)) {
                                customMob.discard();
                                continue;
                            }
                            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                                customMob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.SPAWNER, null, null);
                                serverLevel.sendParticles(
                                        net.minecraft.core.particles.ParticleTypes.WITCH,
                                        spawnX, spawnY + 0.5, spawnZ,
                                        20, 0.5, 0.5, 0.5, 0.1
                                );
                                serverLevel.sendParticles(
                                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.50F, 0.13F, 0.69F), 1.5F),
                                        spawnX, spawnY + 0.5, spawnZ,
                                        20, 0.5, 0.5, 0.5, 0.1
                                );
                            }
                            level.addFreshEntity(customMob);
                            return true;
                        }
                    } else {
                        net.minecraft.resources.ResourceLocation resLoc = net.minecraft.resources.ResourceLocation.tryParse(spawner.templateId);
                        if (resLoc != null) {
                            if (resLoc.getNamespace().equals("minecraft") && !spawner.templateId.contains(":")) {
                                resLoc = new net.minecraft.resources.ResourceLocation("minecraft", spawner.templateId);
                            }
                            var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc);
                            if (entityTypeOpt.isPresent()) {
                                net.minecraft.world.entity.Entity entity = entityTypeOpt.get().create(level);
                                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                                    mob.moveTo(spawnX, spawnY, spawnZ, level.random.nextFloat() * 360F, 0.0F);
                                    if (!level.noCollision(mob) || !level.isUnobstructed(mob)) {
                                        mob.discard();
                                        continue;
                                    }
                                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                                        mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.SPAWNER, null, null);
                                        serverLevel.sendParticles(
                                                net.minecraft.core.particles.ParticleTypes.WITCH,
                                                spawnX, spawnY + 0.5, spawnZ,
                                                20, 0.5, 0.5, 0.5, 0.1
                                        );
                                        serverLevel.sendParticles(
                                                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.50F, 0.13F, 0.69F), 1.5F),
                                                spawnX, spawnY + 0.5, spawnZ,
                                                20, 0.5, 0.5, 0.5, 0.1
                                        );
                                    }
                                    level.addFreshEntity(mob);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public void openEditScreen(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ddraig.net.custommobs.network.ModPackets.openSpawnerUi(serverPlayer, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("SpawnRate", this.spawnRate);
        tag.putInt("SpawnRadius", this.spawnRadius);
        tag.putInt("MaxAlive", this.maxAlive);
        tag.putInt("DayNight", this.dayNight);
        tag.putInt("PlayerDistance", this.playerDistance);
        tag.putString("TemplateId", this.templateId);
        tag.putInt("EliteChance", this.eliteChance);
        tag.putBoolean("RedstonePulseOnly", this.redstonePulseOnly);
        tag.putInt("SpawnerCooldown", this.spawnerCooldown);
        tag.putInt("CooldownTimer", this.cooldownTimer);
        tag.putBoolean("WasPowered", this.wasPowered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.spawnRate = tag.getInt("SpawnRate");
        this.spawnRadius = tag.getInt("SpawnRadius");
        this.maxAlive = tag.getInt("MaxAlive");
        this.dayNight = tag.getInt("DayNight");
        this.playerDistance = tag.getInt("PlayerDistance");
        this.templateId = tag.getString("TemplateId");
        this.eliteChance = tag.getInt("EliteChance");
        this.redstonePulseOnly = tag.getBoolean("RedstonePulseOnly");
        this.spawnerCooldown = tag.getInt("SpawnerCooldown");
        this.cooldownTimer = tag.getInt("CooldownTimer");
        this.wasPowered = tag.getBoolean("WasPowered");
        
        if (this.spawnRate <= 0) this.spawnRate = 200;
        if (this.spawnRadius <= 0) this.spawnRadius = 16;
        if (this.maxAlive <= 0) this.maxAlive = 5;
        if (this.playerDistance <= 0) this.playerDistance = 16;
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

    // Getters and Setters for sync
    public int getSpawnRate() { return spawnRate; }
    public void setSpawnRate(int spawnRate) { this.spawnRate = spawnRate; setChanged(); }

    public int getSpawnRadius() { return spawnRadius; }
    public void setSpawnRadius(int spawnRadius) { this.spawnRadius = spawnRadius; setChanged(); }

    public int getMaxAlive() { return maxAlive; }
    public void setMaxAlive(int maxAlive) { this.maxAlive = maxAlive; setChanged(); }

    public int getDayNight() { return dayNight; }
    public void setDayNight(int dayNight) { this.dayNight = dayNight; setChanged(); }

    public int getPlayerDistance() { return playerDistance; }
    public void setPlayerDistance(int playerDistance) { this.playerDistance = playerDistance; setChanged(); }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; setChanged(); }

    public int getEliteChance() { return eliteChance; }
    public void setEliteChance(int eliteChance) { this.eliteChance = eliteChance; setChanged(); }

    public boolean isRedstonePulseOnly() { return redstonePulseOnly; }
    public void setRedstonePulseOnly(boolean redstonePulseOnly) { this.redstonePulseOnly = redstonePulseOnly; setChanged(); }

    public int getSpawnerCooldown() { return spawnerCooldown; }
    public void setSpawnerCooldown(int spawnerCooldown) { this.spawnerCooldown = spawnerCooldown; setChanged(); }
}
