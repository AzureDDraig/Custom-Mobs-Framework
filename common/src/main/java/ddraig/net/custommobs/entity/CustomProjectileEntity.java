package ddraig.net.custommobs.entity;

import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.ProjectileData;
import ddraig.net.custommobs.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class CustomProjectileEntity extends ThrowableProjectile implements net.minecraft.world.entity.projectile.ItemSupplier {
    public boolean isPreview = false;
    private float damage = 4.0f;

    public float getDamage() {
        return this.damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }
    public static final EntityDataAccessor<String> PROJECTILE_ID = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> STUCK_ENTITY_ID = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> STUCK_OFF_X = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> STUCK_OFF_Y = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> STUCK_OFF_Z = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> IS_ORBITING = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Float> ORBIT_RADIUS = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> ORBIT_SPEED = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> ORBIT_ANGLE = SynchedEntityData.defineId(CustomProjectileEntity.class, EntityDataSerializers.FLOAT);

    private double spawnY = -1.0D;
    private double maxHeight = -1.0D;
    public int orbitLifetime = 200;

    public boolean isGroundSummon = false;
    private int ticksAfterLanding = -1;
    private final java.util.Set<Integer> hitEntityIds = new java.util.HashSet<>();

    public void setMaxHeight(double maxHeight) {
        this.maxHeight = maxHeight;
    }

    public CustomProjectileEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public CustomProjectileEntity(Level level, LivingEntity shooter) {
        super(ModEntities.CUSTOM_PROJECTILE.get(), shooter, level);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
        float scaleVal = 1.0f;
        float w = 0.25f;
        float h = 0.25f;
        if (data != null) {
            scaleVal = data.scale;
            if (this.isPreview) {
                w = 0.25f;
                h = 0.25f;
            } else {
                w = data.hitboxWidth;
                h = data.hitboxHeight;
            }
        }
        return net.minecraft.world.entity.EntityDimensions.scalable(w * scaleVal, h * scaleVal);
    }

    @Override
    public net.minecraft.world.item.ItemStack getItem() {
        return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIRE_CHARGE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PROJECTILE_ID, "");
        this.entityData.define(STUCK_ENTITY_ID, -1);
        this.entityData.define(STUCK_OFF_X, 0.0f);
        this.entityData.define(STUCK_OFF_Y, 0.0f);
        this.entityData.define(STUCK_OFF_Z, 0.0f);
        this.entityData.define(IS_ORBITING, false);
        this.entityData.define(ORBIT_RADIUS, 1.5f);
        this.entityData.define(ORBIT_SPEED, 4.0f);
    }

    private boolean isAlly(net.minecraft.world.entity.Entity entity) {
        net.minecraft.world.entity.Entity owner = this.getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;
        if (owner instanceof CustomMobEntity mob && entity instanceof CustomMobEntity otherMob) {
            ddraig.net.custommobs.data.MobData mobData = MobRegistry.loadedMobs.get(mob.getTemplateId());
            ddraig.net.custommobs.data.MobData otherData = MobRegistry.loadedMobs.get(otherMob.getTemplateId());
            if (mobData != null && otherData != null && !mobData.mobGroup.isEmpty() && mobData.mobGroup.equalsIgnoreCase(otherData.mobGroup)) {
                return true;
            }
        }
        return false;
    }

    public String getProjectileId() {
        return this.entityData.get(PROJECTILE_ID);
    }

    public void setProjectileId(String id) {
        this.entityData.set(PROJECTILE_ID, id);
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PROJECTILE_ID.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected float getGravity() {
        ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
        return (data == null || data.gravity) ? 0.03F : 0.0F;
    }

    @Override
    public void tick() {
        if (this.ticksAfterLanding > 0) {
            this.ticksAfterLanding--;
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (this.ticksAfterLanding == 0) {
                if (!this.level().isClientSide) {
                    this.discard();
                }
            }
            return;
        }

        int stuckId = this.entityData.get(STUCK_ENTITY_ID);
        if (stuckId != -1) {
            net.minecraft.world.entity.Entity stuck = this.level().getEntity(stuckId);
            if (stuck == null || !stuck.isAlive()) {
                if (!this.level().isClientSide) {
                    this.discard();
                }
                return;
            }
            if (this.entityData.get(IS_ORBITING)) {
                float radius = this.entityData.get(ORBIT_RADIUS);
                float speed = this.entityData.get(ORBIT_SPEED);
                float angle = this.entityData.get(ORBIT_ANGLE);

                // Update angle on both server and client (clients should increment locally, server syncs)
                if (this.level().isClientSide) {
                    angle += speed;
                    if (angle >= 360.0F) angle -= 360.0F;
                } else {
                    angle += speed;
                    if (angle >= 360.0F) angle -= 360.0F;
                    this.entityData.set(ORBIT_ANGLE, angle);
                }

                double rad = Math.toRadians(angle);
                double px = stuck.getX() + radius * Math.cos(rad);
                double pz = stuck.getZ() + radius * Math.sin(rad);
                double py = stuck.getY() + stuck.getEyeHeight() * 0.5D;

                this.setPos(px, py, pz);
                this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);

                // Shield particle tick
                if (this.level().isClientSide) {
                    ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
                    String pType = (data == null || data.particleType == null || data.particleType.isEmpty()) ? "minecraft:small_flame" : data.particleType;
                    try {
                        var p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(pType));
                        if (p instanceof net.minecraft.core.particles.SimpleParticleType spt) {
                            this.level().addParticle(spt, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                        }
                    } catch (Exception ignored) {}
                }

                // Check for collisions with other living entities (shield impact)
                if (!this.level().isClientSide) {
                    double checkR = this.getBbWidth() * 0.5D;
                    net.minecraft.world.phys.AABB checkStr = this.getBoundingBox().inflate(checkR, this.getBbHeight() * 0.5D, checkR);
                    for (net.minecraft.world.entity.Entity entity : this.level().getEntities(this, checkStr, this::canHitEntity)) {
                        if (entity instanceof LivingEntity target && !isAlly(target)) {
                            EntityHitResult entityHit = new EntityHitResult(target);
                            this.onHitEntity(entityHit);
                            this.onHit(entityHit);
                            return;
                        }
                    }

                    if (this.tickCount > this.orbitLifetime) {
                        this.discard();
                    }
                }
            } else {
                float ox = this.entityData.get(STUCK_OFF_X);
                float oy = this.entityData.get(STUCK_OFF_Y);
                float oz = this.entityData.get(STUCK_OFF_Z);
                this.setPos(stuck.getX() + ox, stuck.getY() + oy, stuck.getZ() + oz);
                this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);

                if (this.level().isClientSide) {
                    ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
                    String pType = (data == null || data.particleType == null || data.particleType.isEmpty()) ? "minecraft:small_flame" : data.particleType;
                    try {
                        var p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(pType));
                        if (p instanceof net.minecraft.core.particles.SimpleParticleType spt) {
                            this.level().addParticle(spt, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                        }
                    } catch (Exception ignored) {}
                }

                if (!this.level().isClientSide && this.tickCount > 100) {
                    this.discard();
                }
            }
            return;
        }

        if (!this.level().isClientSide) {
            if (this.spawnY == -1.0D) {
                this.spawnY = this.getY();
            }
            if (this.maxHeight > 0.0D && this.getY() >= this.spawnY + this.maxHeight) {
                net.minecraft.world.phys.Vec3 vel = this.getDeltaMovement();
                if (vel.y > 0.0D) {
                    this.setDeltaMovement(vel.x, 0.0D, vel.z);
                    this.hasImpulse = true;
                }
            }
        }

        super.tick();
        
        if (this.level().isClientSide) {
            ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
            String pType = (data == null || data.particleType == null || data.particleType.isEmpty()) ? "minecraft:small_flame" : data.particleType;
            try {
                var p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(pType));
                if (p instanceof net.minecraft.core.particles.SimpleParticleType spt) {
                    this.level().addParticle(spt, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                }
            } catch (Exception ignored) {}
        }

        if (!this.level().isClientSide && this.ticksAfterLanding == -1) {
            double radiusX = this.getBbWidth() * 0.5D;
            double radiusY = this.getBbHeight() * 0.5D;
            net.minecraft.world.phys.AABB checkStr = this.getBoundingBox().inflate(radiusX, radiusY, radiusX);
            for (net.minecraft.world.entity.Entity entity : this.level().getEntities(this, checkStr, this::canHitEntity)) {
                if (entity instanceof LivingEntity target && !isAlly(target)) {
                    EntityHitResult entityHit = new EntityHitResult(target);
                    this.onHitEntity(entityHit);
                    this.onHit(entityHit);
                    return;
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        int stuckId = this.entityData.get(STUCK_ENTITY_ID);
        if (stuckId != -1 || this.ticksAfterLanding > -1) return;

        super.onHit(result);
        
        if (!this.level().isClientSide) {
            ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
            if (data != null) {
                // Play land sound
                if (!data.sounds.land.isEmpty()) {
                    SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(data.sounds.land));
                    this.level().playSound(null, this.blockPosition(), se, SoundSource.NEUTRAL, 1.0f, 1.0f);
                }

                // Explosion logic
                if (data.effects.explosion) {
                    Level.ExplosionInteraction interaction = data.effects.destroyBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), data.effects.explosionRadius, interaction);
                }

                if (this.isGroundSummon) {
                    if (result.getType() == HitResult.Type.BLOCK) {
                        this.ticksAfterLanding = 60; // Stay for 3 seconds
                        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        this.hasImpulse = true;
                        return;
                    } else if (result.getType() == HitResult.Type.ENTITY) {
                        return;
                    }
                }

                if (data.sticky && result.getType() == HitResult.Type.ENTITY) {
                    return;
                }
            }
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        int stuckId = this.entityData.get(STUCK_ENTITY_ID);
        if (stuckId != -1 || this.ticksAfterLanding > -1) return;

        if (this.level().isClientSide || isAlly(result.getEntity())) {
            return;
        }

        if (result.getEntity() instanceof LivingEntity target) {
            if (this.isGroundSummon) {
                if (this.hitEntityIds.contains(target.getId())) {
                    return; // Prevent multiple hits
                }
                this.hitEntityIds.add(target.getId());
            }

            super.onHitEntity(result);

            target.hurt(this.damageSources().thrown(this, this.getOwner() == null ? this : this.getOwner()), this.damage);

            ProjectileData data = MobRegistry.loadedProjectiles.get(getProjectileId());
            if (data != null) {
                // Apply status effects
                for (ProjectileData.StatusEffectData effect : data.effects.statusEffects) {
                    var mobEffect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effect.effectId));
                    if (mobEffect != null) {
                        target.addEffect(new MobEffectInstance(mobEffect, effect.durationTicks, effect.amplifier));
                    }
                }

                if (!this.isGroundSummon && data.sticky) {
                    this.entityData.set(STUCK_ENTITY_ID, target.getId());
                    this.entityData.set(STUCK_OFF_X, (float) (this.getX() - target.getX()));
                    this.entityData.set(STUCK_OFF_Y, (float) (this.getY() - target.getY()));
                    this.entityData.set(STUCK_OFF_Z, (float) (this.getZ() - target.getZ()));
                    this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                }
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        int stuckId = this.entityData.get(STUCK_ENTITY_ID);
        if (stuckId != -1 || this.ticksAfterLanding > -1) return;

        super.onHitBlock(result);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ProjectileId", getProjectileId());
        tag.putFloat("Damage", this.damage);
        tag.putBoolean("IsOrbiting", this.entityData.get(IS_ORBITING));
        tag.putFloat("OrbitRadius", this.entityData.get(ORBIT_RADIUS));
        tag.putFloat("OrbitSpeed", this.entityData.get(ORBIT_SPEED));
        tag.putFloat("OrbitAngle", this.entityData.get(ORBIT_ANGLE));
        tag.putInt("OrbitLifetime", this.orbitLifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ProjectileId")) setProjectileId(tag.getString("ProjectileId"));
        if (tag.contains("Damage")) this.damage = tag.getFloat("Damage");
        if (tag.contains("IsOrbiting")) this.entityData.set(IS_ORBITING, tag.getBoolean("IsOrbiting"));
        if (tag.contains("OrbitRadius")) this.entityData.set(ORBIT_RADIUS, tag.getFloat("OrbitRadius"));
        if (tag.contains("OrbitSpeed")) this.entityData.set(ORBIT_SPEED, tag.getFloat("OrbitSpeed"));
        if (tag.contains("OrbitAngle")) this.entityData.set(ORBIT_ANGLE, tag.getFloat("OrbitAngle"));
        if (tag.contains("OrbitLifetime")) this.orbitLifetime = tag.getInt("OrbitLifetime");
    }
}
