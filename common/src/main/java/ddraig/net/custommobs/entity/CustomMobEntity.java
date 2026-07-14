package ddraig.net.custommobs.entity;

import ddraig.net.custommobs.CustomMobs;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.data.ModConfig;
import ddraig.net.custommobs.data.DatabaseManager;
import ddraig.net.custommobs.network.ModPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public class CustomMobEntity extends TamableAnimal implements GeoEntity {
    public boolean isPreview = false;
    public static final EntityDataAccessor<String> TEMPLATE_ID = SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Boolean> IS_ELITE = SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> BILLBOARD_NAME = SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> ACTIVE_ANIMATION = SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private final AnimatableInstanceCache geckolibCache = GeckoLibUtil.createInstanceCache(this);
    private final Map<String, Integer> abilityCooldowns = new HashMap<>();
    private final Map<String, Integer> goalCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<String> combatSequence = new ArrayList<>();
    private int currentSequenceIndex = 0;

    public void advanceCombatSequence() {
        if (!combatSequence.isEmpty()) {
            MobData data = MobRegistry.loadedMobs.get(this.getTemplateId());
            boolean loop = (data == null || data.loopCombo);
            if (loop) {
                currentSequenceIndex = (currentSequenceIndex + 1) % combatSequence.size();
            } else {
                if (currentSequenceIndex < combatSequence.size()) {
                    currentSequenceIndex++;
                }
            }
        }
    }

    public boolean checkCombatSequence(String goalType) {
        if (combatSequence.isEmpty()) return true;
        if (currentSequenceIndex >= combatSequence.size()) {
            MobData data = MobRegistry.loadedMobs.get(this.getTemplateId());
            boolean loop = (data == null || data.loopCombo);
            if (loop) {
                currentSequenceIndex = 0;
            } else {
                return false;
            }
        }
        String currentType = combatSequence.get(currentSequenceIndex);
        return currentType.equalsIgnoreCase(goalType);
    }

    public boolean isGoalOnCooldown(String type) {
        return goalCooldowns.getOrDefault(type.toUpperCase(), 0) > 0;
    }

    public void startGoalCooldown(String type, int ticks) {
        if (ticks > 0) {
            goalCooldowns.put(type.toUpperCase(), ticks);
        }
    }

    public int getGoalDelayTicks(String type, int defaultVal) {
        MobData.AIGoalData aiGoal = getGoalData(type);
        if (aiGoal != null) {
            try {
                if (aiGoal.params.containsKey("delay")) {
                    return Integer.parseInt(aiGoal.params.get("delay"));
                }
            } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    private int minionSummonCooldown = 0;
    private boolean isSilhouette = false;
    private BlockPos spawnPointPos = null;
    private boolean hasExplodedOnLowHealth = false;
    private int customSummonMinionsCooldown = 0;
    private int teleportBehindCooldown = 0;
    private int pullCooldown = 0;
    private int pullTicksRemaining = 0;
    private LivingEntity pullTargetEntity = null;
    private double pullStrengthValue = 1.2;
    private boolean isRaged = false;
    private int ambushTicks = 0;
    private boolean isAmbushing = false;
    private int fireTrailCooldown = 0;
    private int imitateSoundsCooldown = 0;
    private int burrowCooldown = 0;
    private int delayGoalCooldown = 0;
    private boolean spawnedFromBlock = false;
    private StalkGoal stalkGoalInstance = null;
    private final List<net.minecraft.world.item.ItemStack> stolenItems = new java.util.ArrayList<>();
    public BlockPos spawnerPos = null;
    public String activeRaidId = null;

    public CustomMobEntity(EntityType<? extends CustomMobEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TEMPLATE_ID, "");
        this.entityData.define(IS_ELITE, false);
        this.entityData.define(BILLBOARD_NAME, "");
        this.entityData.define(ACTIVE_ANIMATION, "");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ATTACK_SPEED, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FLYING_SPEED, 0.25);
    }

    public String getTemplateId() {
        return this.entityData.get(TEMPLATE_ID);
    }

    public void setTemplateId(String id) {
        this.entityData.set(TEMPLATE_ID, id);
        reapplyTemplate();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TEMPLATE_ID.equals(key) || IS_ELITE.equals(key)) {
            reapplyTemplate();
        }
    }

    public boolean isElite() {
        return this.entityData.get(IS_ELITE);
    }

    public void setElite(boolean elite) {
        this.entityData.set(IS_ELITE, elite);
        reapplyTemplate();
    }

    public boolean isSilhouette() {
        return this.isSilhouette;
    }

    public void setSilhouette(boolean val) {
        this.isSilhouette = val;
    }

    public String getBillboardName() {
        return this.entityData.get(BILLBOARD_NAME);
    }

    public void setBillboardName(String name) {
        this.entityData.set(BILLBOARD_NAME, name);
        if (!name.isEmpty()) {
            MobData data = MobRegistry.loadedMobs.get(getTemplateId());
            net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.WHITE;
            if (data != null && data.nameColor != null) {
                net.minecraft.ChatFormatting parsed = net.minecraft.ChatFormatting.getByName(data.nameColor.toUpperCase());
                if (parsed != null) {
                    color = parsed;
                }
            }
            if (isElite()) {
                String cleanName = name;
                if (cleanName.startsWith("§c[Elite] ")) cleanName = cleanName.substring(10);
                else if (cleanName.startsWith("[Elite] ")) cleanName = cleanName.substring(8);
                net.minecraft.network.chat.MutableComponent comp = Component.literal("[Elite] ").withStyle(net.minecraft.ChatFormatting.RED)
                        .append(Component.literal(cleanName).withStyle(color));
                this.setCustomName(comp);
            } else {
                String cleanName = name;
                if (cleanName.startsWith("§c[Elite] ")) cleanName = cleanName.substring(10);
                else if (cleanName.startsWith("[Elite] ")) cleanName = cleanName.substring(8);
                this.setCustomName(Component.literal(cleanName).withStyle(color));
            }
            this.setCustomNameVisible(true);
        }
    }

    public String getActiveAnimation() {
        return this.entityData.get(ACTIVE_ANIMATION);
    }

    public void setActiveAnimation(String anim) {
        this.entityData.set(ACTIVE_ANIMATION, anim);
    }

    public void reapplyTemplate() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return;

        this.combatSequence.clear();
        this.currentSequenceIndex = 0;
        for (MobData.AIGoalData goal : data.aiGoals) {
            String type = goal.type.toUpperCase();
            if (type.startsWith("MELEE") || type.startsWith("KNOCKBACK") || type.equals("DELAY")
                    || type.equals("RANGED") || type.startsWith("SUMMON_GROUND_ATTACK")
                    || type.startsWith("AERIAL_RANGED") || type.equals("SHOTGUN_ATTACK")
                    || type.equals("ORBITING_SHIELD")) {
                this.combatSequence.add(goal.type);
            }
        }

        double healthMult = isElite() ? 2.0 : 1.0;
        double damageMult = isElite() ? 2.0 : 1.0;

        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(data.stats.maxHealth * healthMult);
        this.setHealth((float) (data.stats.maxHealth * healthMult));
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(data.stats.movementSpeed);
        double fRange = data.stats.followRange;
        if (hasGoalType("RANGED") || hasGoalType("SUMMON_GROUND_ATTACK") || hasGoalType("AERIAL_RANGED_ATTACK")
            || hasGoalType("SHOTGUN_ATTACK") || hasAnyGoalTypeStartingWith("SUMMON_GROUND_ATTACK_AOE")
            || hasAnyGoalTypeStartingWith("AERIAL_RANGED_AOE") || hasGoalType("ORBITING_SHIELD")) {
            fRange = Math.max(fRange, 64.0);
        }
        Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(fRange);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(data.stats.attackDamage * damageMult);
        Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(data.stats.armor);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_SPEED)).setBaseValue(data.stats.attackSpeed);
        Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(data.stats.knockbackResistance);
        
        this.setMaxUpStep((float) data.stats.stepHeight);

        if (!this.level().isClientSide()) {
            if (data.isFlying || data.spawnRules.aquatic || data.spawnRules.lava) {
                this.navigation = new FlyingPathNavigation(this, this.level());
                this.moveControl = new FlyingMoveControl(this, 20, true);
                Objects.requireNonNull(this.getAttribute(Attributes.FLYING_SPEED)).setBaseValue(data.stats.movementSpeed);
            } else {
                this.navigation = new GroundPathNavigation(this, this.level());
                this.moveControl = new MoveControl(this);
            }
        }

        if (data.billboardName) {
            String namePrefix = isElite() ? "[Elite] " : "";
            setBillboardName(namePrefix + data.name);
        } else {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
            this.entityData.set(BILLBOARD_NAME, "");
        }
        
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        float scaleVal = 1.0f;
        float w = 0.6f;
        float h = 1.8f;
        if (data != null) {
            scaleVal = data.scale;
            if (this.isPreview) {
                w = 0.6f;
                h = 1.8f;
            } else {
                w = data.hitboxWidth;
                h = data.hitboxHeight;
            }
        }
        if (isElite()) {
            scaleVal *= 1.5f;
        }
        return EntityDimensions.scalable(w * scaleVal, h * scaleVal);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (isElite()) {
                MobData data = MobRegistry.loadedMobs.get(this.getTemplateId());
                double scaleVal = 1.0;
                double hpPercent = 0.5;
                if (data != null) {
                    scaleVal = data.scale;
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("RAGE_MODE")) {
                            try {
                                hpPercent = Double.parseDouble(g.params.getOrDefault("health_percent", "0.5"));
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                }
                if (isElite()) {
                    scaleVal *= 1.5;
                }
                boolean clientRaged = (this.getHealth() / this.getMaxHealth() <= hpPercent);
                double radius = this.getBbWidth() * 0.55;
                double height = this.getY() + this.getBbHeight() + (0.35 * scaleVal);
                int particleCount = 12;
                double speed = clientRaged ? 0.25 : 0.1;
                for (int i = 0; i < particleCount; i++) {
                    double angle = (this.tickCount * speed) + (i * (2.0 * Math.PI / particleCount));
                    double dx = Math.cos(angle) * radius;
                    double dz = Math.sin(angle) * radius;

                    net.minecraft.core.particles.DustParticleOptions redDust =
                            new net.minecraft.core.particles.DustParticleOptions(
                                    new org.joml.Vector3f(1.0F, 0.0F, 0.0F), 1.0F
                            );

                    this.level().addParticle(redDust,
                            this.getX() + dx,
                            height,
                            this.getZ() + dz,
                            0.0, 0.0, 0.0);
                }
            }
        } else {
            if (shouldDespawn()) {
                this.discard();
                return;
            }

            if (minionSummonCooldown > 0) minionSummonCooldown--;
            if (customSummonMinionsCooldown > 0) customSummonMinionsCooldown--;
            if (teleportBehindCooldown > 0) teleportBehindCooldown--;
            if (pullCooldown > 0) pullCooldown--;
            if (fireTrailCooldown > 0) fireTrailCooldown--;
            if (imitateSoundsCooldown > 0) imitateSoundsCooldown--;
            if (burrowCooldown > 0) burrowCooldown--;
            if (delayGoalCooldown > 0) delayGoalCooldown--;
            for (Map.Entry<String, Integer> entry : abilityCooldowns.entrySet()) {
                if (entry.getValue() > 0) {
                    abilityCooldowns.put(entry.getKey(), entry.getValue() - 1);
                }
            }
            for (Map.Entry<String, Integer> entry : goalCooldowns.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 1);
                }
            }

            MobData data = MobRegistry.loadedMobs.get(getTemplateId());
            if (data != null && data.stats.regenSpeed > 0 && this.tickCount % 20 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal((float) data.stats.regenSpeed);
                }
            }

            tickCombatAbilities();

            if (this.getTarget() == null || !this.getTarget().isAlive()) {
                this.currentSequenceIndex = 0;
            }

            if (data != null) {
                boolean inWaterOrRain = this.isInWaterOrRain();
                if (inWaterOrRain) {
                    if (hasGoalType("DAMAGED_IN_WATER") && this.tickCount % 20 == 0) {
                        this.hurt(this.damageSources().drown(), 1.0F);
                    }
                    if (hasGoalType("HEAL_IN_WATER") && this.tickCount % 20 == 0) {
                        this.heal(1.0F);
                    }
                    if (hasGoalType("SPEED_IN_WATER")) {
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, 1, true, false));
                    }
                    if (hasGoalType("SLOW_IN_WATER")) {
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1, true, false));
                    }
                }

                boolean inFireOrLava = this.isOnFire() || this.isInLava();
                if (inFireOrLava) {
                    if (hasGoalType("HEAL_IN_FIRE") && this.tickCount % 20 == 0) {
                        this.heal(1.0F);
                    }
                    if (hasGoalType("SPEED_IN_FIRE")) {
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, 1, true, false));
                    }
                }

                if (hasGoalType("INVISIBLE_AT_NIGHT") && !this.level().isDay()) {
                    this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 30, 0, true, false));
                }
                if (hasGoalType("INVISIBLE_IN_LIGHT") && this.level().getMaxLocalRawBrightness(this.blockPosition()) >= 10) {
                    this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 30, 0, true, false));
                }

                if (this.getHealth() / this.getMaxHealth() <= 0.2F) {
                    if (hasGoalType("EXPLODE_ON_LOW_HEALTH") && !hasExplodedOnLowHealth) {
                        hasExplodedOnLowHealth = true;
                        float power = 3.0F;
                        boolean breakBlocks = false;
                        boolean setFire = false;
                        for (MobData.AIGoalData g : data.aiGoals) {
                            if (g.type.equalsIgnoreCase("EXPLODE_ON_LOW_HEALTH")) {
                                try {
                                    power = Float.parseFloat(g.params.getOrDefault("power", "3.0"));
                                    breakBlocks = Boolean.parseBoolean(g.params.getOrDefault("break_blocks", "false"));
                                    setFire = Boolean.parseBoolean(g.params.getOrDefault("set_fire", "false"));
                                } catch (Exception ignored) {}
                                break;
                            }
                        }
                        this.level().explode(this, this.getX(), this.getY(), this.getZ(), power, setFire, breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
                        this.discard();
                    }
                    if (hasGoalType("TELEPORT_ON_LOW_HEALTH") && this.tickCount % 40 == 0) {
                        teleportRandomly();
                    }
                }

                tickContactBehaviors(data);

                if (this.tickCount % 10 == 0 && hasGoalType("SCARE_MOB")) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("SCARE_MOB")) {
                            String targetId = g.params.getOrDefault("target_mob", "");
                            if (!targetId.isEmpty()) {
                                double range = 8.0;
                                List<PathfinderMob> nearby = this.level().getEntitiesOfClass(PathfinderMob.class, this.getBoundingBox().inflate(range), e -> {
                                    if (e == this) return false;
                                    ResourceLocation loc = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                                    if (loc.toString().equals(targetId)) return true;
                                    if (e instanceof CustomMobEntity c) {
                                        return c.getTemplateId().equals(targetId);
                                    }
                                    return false;
                                });
                                for (PathfinderMob victim : nearby) {
                                    Vec3 away = DefaultRandomPos.getPosAway(victim, 16, 7, this.position());
                                    if (away != null) {
                                        victim.getNavigation().moveTo(away.x, away.y, away.z, 1.25D);
                                    }
                                }
                            }
                        }
                    }
                }

                // SUMMON_MINIONS
                if (hasGoalType("SUMMON_MINIONS") && !isGoalOnCooldown("SUMMON_MINIONS") && customSummonMinionsCooldown <= 0 && this.getTarget() != null) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("SUMMON_MINIONS")) {
                            String minionId = g.params.getOrDefault("minion_id", "zombie");
                            int count = 3;
                            int cooldown = getGoalDelayTicks("SUMMON_MINIONS", 300);
                            try {
                                count = Integer.parseInt(g.params.getOrDefault("count", "3"));
                            } catch (Exception ignored) {}
                            customSummonMinionsCooldown = cooldown;
                            startGoalCooldown("SUMMON_MINIONS", cooldown);
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 1.0F);
                            for (int i = 0; i < count; i++) {
                                if (MobRegistry.loadedMobs.containsKey(minionId)) {
                                    CustomMobEntity minion = new CustomMobEntity(ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get(), this.level());
                                    minion.setTemplateId(minionId);
                                    minion.setPos(this.getX() + (this.random.nextDouble() - 0.5D) * 3.0D, this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * 3.0D);
                                    this.level().addFreshEntity(minion);
                                } else {
                                    ResourceLocation resLoc = new ResourceLocation(minionId);
                                    var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc);
                                    if (entityTypeOpt.isPresent()) {
                                        Entity entity = entityTypeOpt.get().create(this.level());
                                        if (entity != null) {
                                            entity.setPos(this.getX() + (this.random.nextDouble() - 0.5D) * 3.0D, this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * 3.0D);
                                            this.level().addFreshEntity(entity);
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

                // TELEPORT_BEHIND_TARGET
                if (hasGoalType("TELEPORT_BEHIND_TARGET") && !isGoalOnCooldown("TELEPORT_BEHIND_TARGET") && teleportBehindCooldown <= 0 && this.getTarget() != null) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("TELEPORT_BEHIND_TARGET")) {
                            int cooldown = getGoalDelayTicks("TELEPORT_BEHIND_TARGET", 100);
                            teleportBehindCooldown = cooldown;
                            startGoalCooldown("TELEPORT_BEHIND_TARGET", cooldown);
                            LivingEntity target = this.getTarget();
                            Vec3 lookVec = target.getLookAngle();
                            Vec3 targetPos = target.position().subtract(lookVec.scale(1.5));
                            BlockPos safePos = new BlockPos((int) targetPos.x, (int) targetPos.y, (int) targetPos.z);
                            for (int dy = 0; dy > -4; dy--) {
                                BlockPos check = safePos.above(dy);
                                if (this.level().getBlockState(check).blocksMotion() && !this.level().getFluidState(check).isSource()) {
                                    this.teleportTo(check.getX() + 0.5D, check.getY() + 1.0D, check.getZ() + 0.5D);
                                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                                    
                                    // Turn to face the target immediately
                                    double dx = target.getX() - this.getX();
                                    double dz = target.getZ() - this.getZ();
                                    float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                                    this.setYRot(yaw);
                                    this.setYHeadRot(yaw);
                                    this.setYBodyRot(yaw);
                                    
                                    // Attack target from behind!
                                    this.doHurtTarget(target);
                                    this.swing(InteractionHand.MAIN_HAND);
                                    
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }

                // PULL_TARGET trigger
                if (hasGoalType("PULL_TARGET") && !isGoalOnCooldown("PULL_TARGET") && pullCooldown <= 0 && this.getTarget() != null) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("PULL_TARGET")) {
                            int cooldown = getGoalDelayTicks("PULL_TARGET", 120);
                            double strength = 1.2;
                            try {
                                strength = Double.parseDouble(g.params.getOrDefault("pull_strength", "1.2"));
                            } catch (Exception ignored) {}
                            pullCooldown = cooldown;
                            startGoalCooldown("PULL_TARGET", cooldown);
                            pullTicksRemaining = 3;
                            pullTargetEntity = this.getTarget();
                            pullStrengthValue = strength;
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
                            break;
                        }
                    }
                }

                if (pullTicksRemaining > 0 && pullTargetEntity != null && pullTargetEntity.isAlive()) {
                    Vec3 dir = this.position().subtract(pullTargetEntity.position()).normalize();
                    pullTargetEntity.setDeltaMovement(dir.scale(pullStrengthValue).add(0, 0.25D, 0));
                    pullTargetEntity.hurtMarked = true;
                    pullTicksRemaining--;
                }

                // RAGE_MODE
                if (hasGoalType("RAGE_MODE") || isElite()) {
                    double hpPercent = 0.5;
                    int amp = 1;
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("RAGE_MODE")) {
                            try {
                                hpPercent = Double.parseDouble(g.params.getOrDefault("health_percent", "0.5"));
                                amp = Integer.parseInt(g.params.getOrDefault("amplifier", "1"));
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                    if (this.getHealth() / this.getMaxHealth() <= hpPercent) {
                        if (!isRaged) {
                            isRaged = true;
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, amp, true, false));
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 30, amp, true, false));
                        if (this.tickCount % 5 == 0 && this.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.0D, this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                        }
                    } else {
                        isRaged = false;
                    }
                }

                // AMBUSH
                if (hasGoalType("AMBUSH")) {
                    LivingEntity target = this.getTarget();
                    if (target != null) {
                        double distSq = this.distanceToSqr(target);
                        if (distSq > 36.0D) {
                            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 30, 0, true, false));
                            isAmbushing = true;
                        } else {
                            if (isAmbushing) {
                                isAmbushing = false;
                                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.0F, 1.0F);
                            }
                        }
                    }
                }

                // FIRE_TRAIL
                if (hasGoalType("FIRE_TRAIL") && this.getTarget() != null) {
                    if (this.getDeltaMovement().horizontalDistanceSqr() > 1E-4 && this.tickCount % 5 == 0) {
                        BlockPos trailPos = this.blockPosition();
                        if (this.level().isEmptyBlock(trailPos)) {
                            this.level().setBlockAndUpdate(trailPos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                        }
                    }
                }

                // IMITATE_SOUNDS
                if (hasGoalType("IMITATE_SOUNDS") && imitateSoundsCooldown <= 0) {
                    int cooldown = 200;
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("IMITATE_SOUNDS")) {
                            try {
                                cooldown = Integer.parseInt(g.params.getOrDefault("cooldown", "200"));
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                    imitateSoundsCooldown = cooldown;
                    List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(16.0D), e -> e != this);
                    if (!nearby.isEmpty()) {
                        LivingEntity victim = nearby.get(this.random.nextInt(nearby.size()));
                        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
                        String name = typeKey.getPath();
                        String soundId = "minecraft:entity." + name + ".ambient";
                        if (name.equals("player")) soundId = "minecraft:entity.player.hurt";
                        else if (name.equals("creeper")) soundId = "minecraft:entity.creeper.primed";
                        else if (name.equals("spider")) soundId = "minecraft:entity.spider.ambient";
                        SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (se != null) {
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), se, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    }
                }

                // GIFT_GIVER
                if (hasGoalType("GIFT_GIVER") && this.tickCount % 20 == 0) {
                    int cooldown = 600;
                    String itemId = "loot";
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("GIFT_GIVER")) {
                            try {
                                cooldown = Integer.parseInt(g.params.getOrDefault("cooldown", "600"));
                                itemId = g.params.getOrDefault("item_id", "loot");
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                    if (this.tickCount % cooldown == 0) {
                        Player nearby = this.level().getNearestPlayer(this, 3.0D);
                        if (nearby != null) {
                            ItemStack giftStack = ItemStack.EMPTY;
                            if (itemId.equalsIgnoreCase("loot") && !data.loot.items.isEmpty()) {
                                MobData.LootItemData lootItem = data.loot.items.get(this.random.nextInt(data.loot.items.size()));
                                var regItem = BuiltInRegistries.ITEM.get(new ResourceLocation(lootItem.itemId));
                                if (regItem != net.minecraft.world.item.Items.AIR) {
                                    giftStack = new ItemStack(regItem, 1);
                                }
                            } else if (!itemId.equalsIgnoreCase("loot")) {
                                var regItem = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                                if (regItem != net.minecraft.world.item.Items.AIR) {
                                    giftStack = new ItemStack(regItem, 1);
                                }
                            }
                            if (!giftStack.isEmpty()) {
                                this.spawnAtLocation(giftStack);
                                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0F, 1.0F);
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.5D, this.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void tickContactBehaviors(MobData data) {
        boolean contactDmg = hasGoalType("DAMAGE_ON_CONTACT");
        boolean contactEff = hasGoalType("EFFECT_ON_CONTACT");
        if ((contactDmg || contactEff) && this.tickCount % 10 == 0) {
            float dmgAmount = 1.0F;
            String effectId = "";
            int duration = 100;
            int amp = 0;
            
            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("DAMAGE_ON_CONTACT")) {
                    try {
                        dmgAmount = Float.parseFloat(g.params.getOrDefault("amount", "1.0"));
                    } catch (Exception ignored) {}
                }
                if (g.type.equalsIgnoreCase("EFFECT_ON_CONTACT")) {
                    effectId = g.params.getOrDefault("effect", "");
                    try {
                        duration = Integer.parseInt(g.params.getOrDefault("duration", "100"));
                        amp = Integer.parseInt(g.params.getOrDefault("amplifier", "0"));
                    } catch (Exception ignored) {}
                }
            }
            
            List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.2D), e -> e != this);
            for (LivingEntity target : targets) {
                if (contactDmg) {
                    target.hurt(this.damageSources().mobAttack(this), dmgAmount);
                }
                if (contactEff && !effectId.isEmpty()) {
                    var effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId));
                    if (effect != null) {
                        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amp));
                    }
                }
            }
        }
    }

    private boolean shouldDespawn() {
        if (this.isPersistenceRequired() || this.isTame()) {
            return false;
        }
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && data.spawnRules.raidOnly) {
            return false;
        }
        Player nearby = this.level().getNearestPlayer(this, 128.0);
        return nearby == null;
    }

    private void tickCombatAbilities() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null || this.getTarget() == null) return;

        List<MobData.AbilityData> allAbilities = new ArrayList<>(data.abilities);
        if (isElite()) {
            for (String extraAbName : data.elite.extraAbilities) {
                MobData.AbilityData ab = new MobData.AbilityData();
                ab.name = extraAbName;
                ab.type = "BURNING";
                ab.cooldownTicks = 120;
                ab.power = 3.0;
                ab.durationTicks = 80;
                allAbilities.add(ab);
            }
        }

        for (MobData.AbilityData ab : allAbilities) {
            boolean isBoundToGoal = false;
            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("USE_ABILITY") && ab.name.equalsIgnoreCase(g.params.getOrDefault("ability", ""))) {
                    isBoundToGoal = true;
                    break;
                }
            }
            if (isBoundToGoal) continue;

            int currentCd = abilityCooldowns.getOrDefault(ab.name, 0);
            if (currentCd <= 0 && this.distanceToSqr(this.getTarget()) < 36.0) {
                executeAbility(ab);
                abilityCooldowns.put(ab.name, ab.cooldownTicks);
                break;
            }
        }
    }

    private void executeAbility(MobData.AbilityData ab) {
        LivingEntity target = this.getTarget();
        if (target == null) return;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.5, 0.5, 0.5, 0.1);

            switch (ab.type.toUpperCase()) {
                case "POISON" -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, ab.durationTicks, (int) ab.power));
                case "BURNING" -> target.setSecondsOnFire(ab.durationTicks / 20);
                case "FREEZE" -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ab.durationTicks, 5));
                case "HEALING" -> this.heal((float) ab.power);
                case "TELEPORT" -> {
                    double tx = this.getX() + (this.random.nextDouble() - 0.5) * 12;
                    double ty = this.getY();
                    double tz = this.getZ() + (this.random.nextDouble() - 0.5) * 12;
                    this.teleportTo(tx, ty, tz);
                }
                case "DASH" -> {
                    Vec3 dir = target.position().subtract(this.position()).normalize().scale(1.5);
                    this.setDeltaMovement(dir);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() == this || source.getDirectEntity() == this) {
            return false;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj && proj.getOwner() == this) {
            return false;
        }
        if (source.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj && proj.getOwner() == this) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            String pName = player.getGameProfile().getName();
            if (this.spawnerPos != null) {
                net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(this.spawnerPos);
                if (be instanceof ddraig.net.custommobs.block.entity.RaidBlockEntity spawner) {
                    spawner.participatingPlayers.add(pName);
                }
            }
            if (this.activeRaidId != null) {
                ddraig.net.custommobs.data.RaidSystem.ActiveRaid active = ddraig.net.custommobs.data.RaidSystem.activeRaids.get(this.activeRaidId);
                if (active != null) {
                    active.participatingPlayers.add(pName);
                }
            }
        }
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null) {
            if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                if (data.isFlying) return false;
                amount *= (float) (1.0 - data.stats.fallDamageResistance);
                if (amount <= 0.0F) return false;
            }
            if (data.stats.fireImmune && (source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.LAVA) || source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR))) {
                return false;
            }
            if (data.stats.drowningImmune && source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) {
                return false;
            }
            if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                if (data.stats.projectileImmune) {
                    return false;
                }
                if (hasGoalType("DEFLECT_PROJECTILES") || this.random.nextDouble() < data.stats.projectileReflectionChance) {
                    Vec3 vel = proj.getDeltaMovement().scale(-1.0);
                    proj.setDeltaMovement(vel);
                    proj.setOwner(this);
                    return false;
                }
            }
            if (source.getEntity() instanceof LivingEntity attacker) {
                if (hasGoalType("CALL_HELP")) {
                    double range = 100.0;
                    List<CustomMobEntity> allies = this.level().getEntitiesOfClass(CustomMobEntity.class, this.getBoundingBox().inflate(range), e -> {
                        if (e == this) return false;
                        MobData md = MobRegistry.loadedMobs.get(e.getTemplateId());
                        return md != null && md.mobGroup != null && !md.mobGroup.isEmpty() && md.mobGroup.equals(data.mobGroup);
                    });
                    for (CustomMobEntity ally : allies) {
                        if (ally.getTarget() == null) {
                            ally.setTarget(attacker);
                        }
                    }
                }
                if (hasGoalType("BURROW") && burrowCooldown <= 0) {
                    int cooldown = 300;
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("BURROW")) {
                            try {
                                cooldown = Integer.parseInt(g.params.getOrDefault("cooldown", "300"));
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                    burrowCooldown = cooldown;
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()), this.getX(), this.getY(), this.getZ(), 20, 0.5, 0.2, 0.5, 0.15);
                    }
                    teleportRandomly();
                }
            }

            if (source.getEntity() != null && hasGoalType("TELEPORT_ON_HIT")) {
                for (MobData.AIGoalData g : data.aiGoals) {
                    if (g.type.equalsIgnoreCase("TELEPORT_ON_HIT")) {
                        double chance = 0.25;
                        try {
                            chance = Double.parseDouble(g.params.getOrDefault("chance", "0.25"));
                        } catch (Exception ignored) {}
                        if (this.random.nextDouble() < chance) {
                            teleportRandomly();
                        }
                        break;
                    }
                }
            }
        }
        return super.hurt(source, amount);
    }

    private void teleportRandomly() {
        for (int i = 0; i < 16; ++i) {
            double targetX = this.getX() + (this.random.nextDouble() - 0.5D) * 10.0D;
            double targetY = this.getY() + (double)(this.random.nextInt(8) - 4);
            double targetZ = this.getZ() + (this.random.nextDouble() - 0.5D) * 10.0D;
            BlockPos pos = BlockPos.containing(targetX, targetY, targetZ);
            while (pos.getY() > this.level().getMinBuildHeight() && !this.level().getBlockState(pos).blocksMotion()) {
                pos = pos.below();
            }
            BlockState state = this.level().getBlockState(pos);
            if (state.blocksMotion() && !state.getFluidState().isSource()) {
                boolean teleported = this.randomTeleport(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, true);
                if (teleported) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                    break;
                }
            }
        }
    }
    @Override
    public void setTarget(@org.jetbrains.annotations.Nullable LivingEntity target) {
        LivingEntity oldTarget = this.getTarget();
        super.setTarget(target);
        if (target == null || target != oldTarget) {
            this.currentSequenceIndex = 0;
        }
    }

    @Override
    public boolean fireImmune() {
        MobData data = MobRegistry.loadedMobs.get(this.getTemplateId());
        if (data != null && data.spawnRules.lava) {
            return true;
        }
        return super.fireImmune();
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            MobData data = MobRegistry.loadedMobs.get(getTemplateId());
            if (data != null) {
                boolean inWaterAndAquatic = data.spawnRules.aquatic && this.isInWater();
                boolean inLavaAndLavaType = data.spawnRules.lava && this.isInLava();
                if (inWaterAndAquatic || inLavaAndLavaType) {
                    if (this.isInWater()) {
                        this.moveRelative(0.02F, travelVector);
                        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
                        this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
                    } else if (this.isInLava()) {
                        this.moveRelative(0.02F, travelVector);
                        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
                        this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                    }
                    this.calculateEntityAnimation(false);
                    return;
                }
            }
        }
        super.travel(travelVector);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && data.isFlying) {
            // No fall damage or block checks for flying mobs
        } else {
            super.checkFallDamage(y, onGround, state, pos);
        }
    }

    @Override
    public boolean onClimbable() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && data.isFlying) {
            return false;
        }
        return super.onClimbable();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        RaidSystem.onMobDeath(this.getUUID());

        if (this.level() instanceof ServerLevel serverLevel && source.getEntity() instanceof ServerPlayer player) {
            DatabaseManager.discoverMob(player.getUUID(), getTemplateId());
            ModPackets.syncBestiaryDiscoveries(player);
        }

        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !this.level().isClientSide) {
            double multiplier = isElite() ? 2.0 : 1.0;
            int lootingLvl = 0;
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity killer) {
                lootingLvl = net.minecraft.world.item.enchantment.EnchantmentHelper.getMobLooting(killer);
            }
            for (MobData.LootItemData item : data.loot.items) {
                if (item.lootingRequired && lootingLvl < item.lootingLevel) {
                    continue;
                }
                double rollChance = item.chance;
                int rolls = 0;
                while (rollChance >= 100.0) {
                    rolls++;
                    rollChance -= 100.0;
                }
                if (rollChance > 0.0 && this.random.nextDouble() * 100.0 <= rollChance) {
                    rolls++;
                }
                if (rolls > 0) {
                    int count = item.minCount + this.random.nextInt(Math.max(1, item.maxCount - item.minCount + 1));
                    count = (int) (count * multiplier * rolls);
                    ResourceLocation resLoc = new ResourceLocation(item.itemId);
                    var regItem = BuiltInRegistries.ITEM.get(resLoc);
                    if (regItem != net.minecraft.world.item.Items.AIR) {
                        ItemStack stack = new ItemStack(regItem, count);
                        if (!item.nbt.isEmpty()) {
                            try {
                                CompoundTag tag = TagParser.parseTag(item.nbt);
                                stack.setTag(tag);
                            } catch (Exception ignored) {}
                        }
                        this.spawnAtLocation(stack);
                    }
                }
            }

            for (ItemStack stack : stolenItems) {
                this.spawnAtLocation(stack);
            }
            stolenItems.clear();

            if (hasGoalType("EXPLODE_ON_DEATH")) {
                for (MobData.AIGoalData g : data.aiGoals) {
                    if (g.type.equalsIgnoreCase("EXPLODE_ON_DEATH")) {
                        float power = 3.0F;
                        boolean breakBlocks = false;
                        boolean setFire = false;
                        try {
                            power = Float.parseFloat(g.params.getOrDefault("power", "3.0"));
                            breakBlocks = Boolean.parseBoolean(g.params.getOrDefault("break_blocks", "false"));
                            setFire = Boolean.parseBoolean(g.params.getOrDefault("set_fire", "false"));
                        } catch (Exception ignored) {}
                        this.level().explode(this, this.getX(), this.getY(), this.getZ(), power, setFire, breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
                        break;
                    }
                }
            }

            if (hasGoalType("SPLIT_ON_DEATH")) {
                for (MobData.AIGoalData g : data.aiGoals) {
                    if (g.type.equalsIgnoreCase("SPLIT_ON_DEATH")) {
                        String minionId = g.params.getOrDefault("minion_id", "zombie");
                        int count = 2;
                        try {
                            count = Integer.parseInt(g.params.getOrDefault("count", "2"));
                        } catch (Exception ignored) {}
                        for (int i = 0; i < count; i++) {
                            if (MobRegistry.loadedMobs.containsKey(minionId)) {
                                CustomMobEntity minion = new CustomMobEntity(ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get(), this.level());
                                minion.setTemplateId(minionId);
                                minion.setPos(this.getX() + (this.random.nextDouble() - 0.5D) * 1.5D, this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * 1.5D);
                                this.level().addFreshEntity(minion);
                            } else {
                                ResourceLocation resLoc = new ResourceLocation(minionId);
                                var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc);
                                if (entityTypeOpt.isPresent()) {
                                    Entity entity = entityTypeOpt.get().create(this.level());
                                    if (entity != null) {
                                        entity.setPos(this.getX() + (this.random.nextDouble() - 0.5D) * 1.5D, this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * 1.5D);
                                        this.level().addFreshEntity(entity);
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isDay() && !this.level().isClientSide) {
            float brightness = this.getLightLevelDependentMagicValue();
            BlockPos blockpos = this.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat ? BlockPos.containing(this.getX(), this.getY() + this.getEyeHeight(), this.getZ()) : BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            if (hasGoalType("BURN_IN_SUN") && brightness > 0.5F && this.random.nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F && this.level().canSeeSky(blockpos)) {
                boolean hasHelmet = false;
                net.minecraft.world.entity.EquipmentSlot slot = net.minecraft.world.entity.EquipmentSlot.HEAD;
                net.minecraft.world.item.ItemStack itemstack = this.getItemBySlot(slot);
                if (!itemstack.isEmpty()) {
                    hasHelmet = true;
                    if (itemstack.isDamageableItem()) {
                        itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                        if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                            this.broadcastBreakEvent(slot);
                            this.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    }
                }
                if (!hasHelmet) {
                    this.setSecondsOnFire(8);
                }
            }
        }
    }

    @Override
    public boolean canBreatheUnderwater() {
        MobData data = MobRegistry.loadedMobs.get(this.getTemplateId());
        boolean isFluidMob = data != null && (data.spawnRules.aquatic || data.spawnRules.lava);
        return isFluidMob || hasGoalType("SWIM_UNDERWATER") || super.canBreatheUnderwater();
    }

    private SoundEvent getSoundEvent(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return BuiltInRegistries.SOUND_EVENT.getOptional(new ResourceLocation(id)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !data.sounds.step.isEmpty()) {
            SoundEvent sound = getSoundEvent(data.sounds.step);
            if (sound != null) {
                this.playSound(sound, 0.15F, 1.0F);
                return;
            }
        }
        super.playStepSound(pos, state);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !data.sounds.ambient.isEmpty()) {
            SoundEvent sound = getSoundEvent(data.sounds.ambient);
            if (sound != null) return sound;
        }
        return super.getAmbientSound();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !data.sounds.hurt.isEmpty()) {
            SoundEvent sound = getSoundEvent(data.sounds.hurt);
            if (sound != null) return sound;
        }
        return super.getHurtSound(source);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !data.sounds.death.isEmpty()) {
            SoundEvent sound = getSoundEvent(data.sounds.death);
            if (sound != null) return sound;
        }
        return super.getDeathSound();
    }

    @Override
    public int getExperienceReward() {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        int base = data != null ? data.loot.xpReward : super.getExperienceReward();
        return isElite() ? base * 2 : base;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        if (getTemplateId().isEmpty() && !level.isClientSide()) {
            List<MobData> validTemplates = new ArrayList<>();
            int totalWeight = 0;
            String biome = level.getBiome(this.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse("");
            for (MobData m : MobRegistry.loadedMobs.values()) {
                if (isValidSpawnTemplate(m, level, this.blockPosition(), spawnType, biome)) {
                    validTemplates.add(m);
                    totalWeight += m.spawnRules.weight;
                }
            }
            if (!validTemplates.isEmpty() && totalWeight > 0) {
                int rng = this.random.nextInt(totalWeight);
                int cumulative = 0;
                for (MobData m : validTemplates) {
                    cumulative += m.spawnRules.weight;
                    if (rng < cumulative) {
                        this.setTemplateId(m.id);
                        break;
                    }
                }
            } else if (!validTemplates.isEmpty()) {
                this.setTemplateId(validTemplates.get(0).id);
            }
        }

        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && !isElite()) {
            double chance = data.elite.chance;
            if (chance == 5.0) {
                chance = ddraig.net.custommobs.data.ModConfig.getVal("general", "global_elite_spawn_chance");
            }
            if (this.random.nextDouble() * 100.0 <= chance) {
                setElite(true);
            }
        }
        
        if (!level.isClientSide() && (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION || spawnType == MobSpawnType.SPAWNER)) {
            if (!checkSpawningRules(level, this.blockPosition(), spawnType)) {
                this.discard();
                return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
            }
        }

        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
        
        reapplyTemplate();
        this.setHealth(this.getMaxHealth());
        
        return result;
    }

    public static boolean checkCustomMobSpawnRules(EntityType<? extends CustomMobEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (spawnType == MobSpawnType.SPAWNER) return true;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
        for (MobData data : MobRegistry.loadedMobs.values()) {
            if (isValidSpawnTemplate(data, level, pos, spawnType, biome)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidSpawnTemplate(MobData data, ServerLevelAccessor level, BlockPos pos, MobSpawnType spawnType, String biome) {
        if (data == null) return false;
        if (data.id.startsWith("__proj_preview_")) return false;
        if (data.spawnRules.raidOnly && (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)) {
            return false;
        }
        if (!data.spawnRules.naturalSpawning) return false;
        if (data.spawnRules.surfaceOnly && !level.canSeeSky(pos)) return false;
        if (data.spawnRules.cavesOnly && level.canSeeSky(pos)) return false;

        if (data.spawnRules.aquatic) {
            if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                return false;
            }
        } else if (data.spawnRules.lava) {
            if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)) {
                return false;
            }
        } else {
            net.minecraft.world.level.block.state.BlockState spawnState = level.getBlockState(pos);
            boolean canSpawnInside = spawnState.isAir()
                    || spawnState.is(net.minecraft.world.level.block.Blocks.WATER)
                    || spawnState.getCollisionShape(level, pos).isEmpty();
            if (!canSpawnInside) {
                return false;
            }

            BlockPos below = pos.below();
            net.minecraft.world.level.block.state.BlockState belowState = level.getBlockState(below);
            if (!belowState.isValidSpawn(level, below, ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get())
                    && belowState.getCollisionShape(level, below).isEmpty()) {
                return false;
            }
        }

        if (data.spawnRules.biomes != null && !data.spawnRules.biomes.isEmpty() && !biome.isEmpty()) {
            boolean matched = false;
            for (String b : data.spawnRules.biomes) {
                if (b.equalsIgnoreCase(biome)) {
                    matched = true;
                    break;
                }
                if (!b.contains(":") && biome.contains(":")) {
                    String ns = biome.substring(biome.indexOf(":") + 1);
                    if (b.equalsIgnoreCase(ns)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) return false;
        }

        if (data.spawnRules.dimension != null && !data.spawnRules.dimension.equals("any")) {
            String dim = level.getLevel().dimension().location().toString();
            if (!dim.contains(data.spawnRules.dimension)) return false;
        }

        if (data.spawnRules.timeOfDay != null && !data.spawnRules.timeOfDay.equals("any")) {
            long timeOfDay = level.getLevel().getDayTime() % 24000L;
            boolean isDay = timeOfDay < 13000L || timeOfDay > 23000L;
            if (data.spawnRules.timeOfDay.equalsIgnoreCase("day") && !isDay) return false;
            if (data.spawnRules.timeOfDay.equalsIgnoreCase("night") && isDay) return false;
        }

        if (data.spawnRules.moonPhase != null && !data.spawnRules.moonPhase.equals("any")) {
            long dayTime = level.getLevel().getDayTime();
            int phase = (int)(dayTime / 24000L % 8L);
            if (data.spawnRules.moonPhase.equalsIgnoreCase("full") && phase != 0) return false;
            if (data.spawnRules.moonPhase.equalsIgnoreCase("new") && phase != 4) return false;
            if (data.spawnRules.moonPhase.equalsIgnoreCase("quarters") && (phase == 0 || phase == 4)) return false;
        }

        int y = pos.getY();
        if (y < data.spawnRules.minHeight || y > data.spawnRules.maxHeight) return false;

        if (data.spawnRules.weather != null && !data.spawnRules.weather.equals("any")) {
            boolean isRaining = level.getLevel().isRaining();
            boolean isThundering = level.getLevel().isThundering();
            if (data.spawnRules.weather.equalsIgnoreCase("rain") && !isRaining) return false;
            if (data.spawnRules.weather.equalsIgnoreCase("thunder") && !isThundering) return false;
            if (data.spawnRules.weather.equalsIgnoreCase("clear") && (isRaining || isThundering)) return false;
        }

        if (data.spawnRules.spawnBlock != null && !data.spawnRules.spawnBlock.isEmpty()) {
            BlockState below = level.getBlockState(pos.below());
            String blockId = BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString();
            if (!blockId.equalsIgnoreCase(data.spawnRules.spawnBlock)) return false;
        }

        int light = level.getMaxLocalRawBrightness(pos);
        if (light < data.spawnRules.minLight || light > data.spawnRules.maxLight) return false;

        if (data.spawnRules.allowedStructure != null && !data.spawnRules.allowedStructure.isEmpty()) {
            boolean inStructure = false;
            try {
                var registries = level.registryAccess();
                var structureRegistry = registries.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, new ResourceLocation(data.spawnRules.allowedStructure));
                var struct = structureRegistry.get(key);
                if (struct != null) {
                    var structureManager = level.getLevel().structureManager();
                    var start = structureManager.getStructureAt(pos, struct);
                    if (start.isValid()) {
                        inStructure = true;
                    }
                }
            } catch (Exception ignored) {}
            if (!inStructure) return false;
        }

        return true;
    }

    private boolean checkSpawningRules(ServerLevelAccessor level, BlockPos pos, MobSpawnType spawnType) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return true;
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
        return isValidSpawnTemplate(data, level, pos, spawnType, biome);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TemplateId", getTemplateId());
        tag.putBoolean("IsElite", isElite());
        tag.putBoolean("SpawnedFromBlock", spawnedFromBlock);
        tag.putString("BillboardName", getBillboardName());
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (var stack : stolenItems) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("StolenItems", list);
        if (this.spawnerPos != null) {
            tag.putLong("SpawnerPos", this.spawnerPos.asLong());
        }
        if (this.activeRaidId != null) {
            tag.putString("ActiveRaidId", this.activeRaidId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TemplateId")) setTemplateId(tag.getString("TemplateId"));
        if (tag.contains("IsElite")) setElite(tag.getBoolean("IsElite"));
        if (tag.contains("SpawnedFromBlock")) this.spawnedFromBlock = tag.getBoolean("SpawnedFromBlock");
        if (tag.contains("BillboardName")) setBillboardName(tag.getString("BillboardName"));
        if (tag.contains("StolenItems", 9)) {
            stolenItems.clear();
            var list = tag.getList("StolenItems", 10);
            for (int i = 0; i < list.size(); i++) {
                stolenItems.add(net.minecraft.world.item.ItemStack.of(list.getCompound(i)));
            }
            if (!stolenItems.isEmpty()) {
                this.setPersistenceRequired();
            }
        }
        if (tag.contains("SpawnerPos")) {
            this.spawnerPos = BlockPos.of(tag.getLong("SpawnerPos"));
        }
        if (tag.contains("ActiveRaidId")) {
            this.activeRaidId = tag.getString("ActiveRaidId");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 5, event -> {
            CustomMobEntity mob = event.getAnimatable();
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return PlayState.CONTINUE;

            String active = mob.getActiveAnimation();
            if (active != null && !active.isEmpty()) {
                event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop(active));
                return PlayState.CONTINUE;
            }

            if (mob.swinging) {
                String attackAnim = data.animations.get("attack");
                if (attackAnim != null && !attackAnim.isEmpty()) {
                    event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenPlay(attackAnim));
                    return PlayState.CONTINUE;
                }
            }

            boolean isMoving = mob.getDeltaMovement().horizontalDistanceSqr() > 1E-4;
            String animState = isMoving ? "walk" : "idle";
            String animName = data.animations.get(animState);
            if (animName == null || animName.isEmpty()) {
                animName = isMoving ? "walk" : "idle";
            }
            event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop(animName));
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geckolibCache;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                MobData data = MobRegistry.loadedMobs.get(CustomMobEntity.this.getTemplateId());
                if (data != null && (data.spawnRules.aquatic || data.spawnRules.lava)) {
                    return false;
                }
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false) {
            @Override
            public boolean canUse() {
                MobData data = MobRegistry.loadedMobs.get(CustomMobEntity.this.getTemplateId());
                return data != null && data.tameable && super.canUse();
            }
        });
        
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE"));
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE_2"));
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE_3"));
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE_4"));
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE_5"));
        this.goalSelector.addGoal(2, new CustomMeleeAttackGoal(this, 1.2D, true, "MELEE_6"));

        this.goalSelector.addGoal(2, new Goal() {
            private int attackTime = 0;
            private boolean hasAttacked = false;
            {
                this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            }
            @Override
            public boolean canUse() {
                if (hasGoalType("STALK") && stalkGoalInstance != null) {
                    if (!stalkGoalInstance.isStalkingComplete()) {
                        return false;
                    }
                }
                if (!checkCombatSequence("RANGED")) {
                    return false;
                }
                return hasGoalType("RANGED") && CustomMobEntity.this.getTarget() != null;
            }
            @Override
            public void start() {
                this.hasAttacked = false;
                this.attackTime = 0;
                MobData.AIGoalData aiGoal = getGoalData("RANGED");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public boolean canContinueToUse() {
                return !hasAttacked && CustomMobEntity.this.getTarget() != null;
            }
            @Override
            public void stop() {
                MobData.AIGoalData aiGoal = getGoalData("RANGED");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
                if (hasAttacked && !combatSequence.isEmpty()) {
                    advanceCombatSequence();
                }
                hasAttacked = false;
            }
            @Override
            public void tick() {
                LivingEntity target = CustomMobEntity.this.getTarget();
                if (target == null) return;
                CustomMobEntity.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
                
                double distSqr = CustomMobEntity.this.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr > 15.0 * 15.0) {
                    CustomMobEntity.this.getNavigation().moveTo(target, 1.0D);
                } else if (distSqr < 8.0 * 8.0) {
                    net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(CustomMobEntity.this, 16, 7, target.position());
                    if (awayPos != null) {
                        CustomMobEntity.this.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                    } else {
                        CustomMobEntity.this.getNavigation().stop();
                    }
                } else {
                    CustomMobEntity.this.getNavigation().stop();
                }

                int delay = 40;
                MobData.AIGoalData aiGoal = getGoalData("RANGED");
                if (aiGoal != null) {
                    try {
                        delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                    } catch (Exception ignored) {}
                }
                attackTime++;
                if (attackTime >= delay) {
                    attackTime = 0;
                    fireProjectile(target);
                    CustomMobEntity.this.swing(InteractionHand.MAIN_HAND);
                    this.hasAttacked = true;
                }
            }
        });

        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return hasGoalType("WANDER") && !isGoalOnCooldown("WANDER") && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("WANDER", getGoalDelayTicks("WANDER", 60));
                MobData.AIGoalData aiGoal = getGoalData("WANDER");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("WANDER");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });

        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return hasGoalType("LOOK_AT_PLAYER") && !isGoalOnCooldown("LOOK_AT_PLAYER") && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("LOOK_AT_PLAYER", getGoalDelayTicks("LOOK_AT_PLAYER", 0));
                MobData.AIGoalData aiGoal = getGoalData("LOOK_AT_PLAYER");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("LOOK_AT_PLAYER");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return hasGoalType("FLY_HOVER") && !isGoalOnCooldown("FLY_HOVER") && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("FLY_HOVER", getGoalDelayTicks("FLY_HOVER", 0));
                MobData.AIGoalData aiGoal = getGoalData("FLY_HOVER");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("FLY_HOVER");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });

        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.PanicGoal(this, 1.25D) {
            @Override
            public boolean canUse() {
                return hasGoalType("PANIC_ON_FIRE") && !isGoalOnCooldown("PANIC_ON_FIRE") && this.mob.isOnFire() && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("PANIC_ON_FIRE", getGoalDelayTicks("PANIC_ON_FIRE", 0));
                MobData.AIGoalData aiGoal = getGoalData("PANIC_ON_FIRE");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("PANIC_ON_FIRE");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });

        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomSwimmingGoal(this, 1.0D, 10) {
            @Override
            public boolean canUse() {
                return hasGoalType("SWIM_UNDERWATER") && !isGoalOnCooldown("SWIM_UNDERWATER") && this.mob.isInWater() && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("SWIM_UNDERWATER", getGoalDelayTicks("SWIM_UNDERWATER", 0));
                MobData.AIGoalData aiGoal = getGoalData("SWIM_UNDERWATER");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("SWIM_UNDERWATER");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });

        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.RestrictSunGoal(this) {
            @Override
            public boolean canUse() {
                return hasGoalType("FLEE_SUN") && super.canUse();
            }
        });

        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.FleeSunGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return hasGoalType("FLEE_SUN") && !isGoalOnCooldown("FLEE_SUN") && super.canUse();
            }
            @Override
            public void start() {
                super.start();
                startGoalCooldown("FLEE_SUN", getGoalDelayTicks("FLEE_SUN", 0));
                MobData.AIGoalData aiGoal = getGoalData("FLEE_SUN");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    setActiveAnimation(aiGoal.animation);
                }
            }
            @Override
            public void stop() {
                super.stop();
                MobData.AIGoalData aiGoal = getGoalData("FLEE_SUN");
                if (aiGoal != null && getActiveAnimation().equals(aiGoal.animation)) {
                    setActiveAnimation("");
                }
            }
        });

        this.goalSelector.addGoal(2, new CreeperLikeExplodeGoal(this));
        this.goalSelector.addGoal(3, new FleeRainGoal(this));
        this.goalSelector.addGoal(3, new AvoidWaterGoal(this));
        this.goalSelector.addGoal(3, new SeekWaterGoal(this));
        this.goalSelector.addGoal(3, new AvoidFireGoal(this));
        this.goalSelector.addGoal(3, new SeekFireGoal(this));
        this.goalSelector.addGoal(4, new SeekPlayersGoal(this));
        this.goalSelector.addGoal(4, new SeekItemsGoal(this));
        this.goalSelector.addGoal(4, new SeekLightGoal(this));

        // Extended custom AI goal classes
        this.goalSelector.addGoal(3, new ReturnToSpawnGoal(this));
        this.goalSelector.addGoal(1, new HealAlliesGoal(this));
        this.goalSelector.addGoal(3, new LeapAttackGoal(this));
        this.stalkGoalInstance = new StalkGoal(this);
        this.goalSelector.addGoal(3, this.stalkGoalInstance);
        this.goalSelector.addGoal(3, new SearchGoal(this));
        this.goalSelector.addGoal(3, new AvoidLightGoal(this));
        this.goalSelector.addGoal(3, new AvoidPlayerWearingGoal(this));
        this.goalSelector.addGoal(3, new AvoidMobGoal(this));
        this.goalSelector.addGoal(3, new AvoidGroupGoal(this));

        this.goalSelector.addGoal(2, new CustomMeleeAOEAttackGoal(this, 1.2D, true, "MELEE_AOE"));
        this.goalSelector.addGoal(2, new CustomMeleeAOEAttackGoal(this, 1.2D, true, "MELEE_AOE_2"));
        this.goalSelector.addGoal(2, new CustomMeleeAOEAttackGoal(this, 1.2D, true, "MELEE_AOE_3"));
        this.goalSelector.addGoal(2, new CustomMeleeAOEAttackGoal(this, 1.2D, true, "MELEE_AOE_4"));

        this.goalSelector.addGoal(2, new CustomKnockbackAttackGoal(this, 1.2D, true, "KNOCKBACK_ATTACK"));
        this.goalSelector.addGoal(2, new CustomKnockbackAttackGoal(this, 1.2D, true, "KNOCKBACK_ATTACK_2"));

        this.goalSelector.addGoal(2, new SummonGroundAttackGoal(this));
        this.goalSelector.addGoal(2, new SummonGroundAttackAOEGoal(this, "SUMMON_GROUND_ATTACK_AOE"));
        this.goalSelector.addGoal(2, new SummonGroundAttackAOEGoal(this, "SUMMON_GROUND_ATTACK_AOE_2"));
        this.goalSelector.addGoal(2, new SummonGroundAttackAOEGoal(this, "SUMMON_GROUND_ATTACK_AOE_3"));
        this.goalSelector.addGoal(2, new SummonGroundAttackAOEGoal(this, "SUMMON_GROUND_ATTACK_AOE_4"));

        this.goalSelector.addGoal(2, new ShotgunAttackGoal(this));
        this.goalSelector.addGoal(2, new OrbitingShieldGoal(this));
        this.goalSelector.addGoal(2, new CombatDelayGoal(this));

        this.goalSelector.addGoal(2, new AerialRangedAttackGoal(this));
        this.goalSelector.addGoal(2, new AerialRangedAOEGoal(this, "AERIAL_RANGED_AOE"));
        this.goalSelector.addGoal(2, new AerialRangedAOEGoal(this, "AERIAL_RANGED_AOE_2"));
        this.goalSelector.addGoal(2, new AerialRangedAOEGoal(this, "AERIAL_RANGED_AOE_3"));
        this.goalSelector.addGoal(2, new AerialRangedAOEGoal(this, "AERIAL_RANGED_AOE_4"));

        this.goalSelector.addGoal(5, new CustomSwimmingGoal(this, 1.0D));

        // Target selectors
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return hasGoalType("TARGET_REVENGE") || hasGoalType("FIGHT_BACK") && super.canUse();
            }
        });

        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                MobData d = MobRegistry.loadedMobs.get(CustomMobEntity.this.getTemplateId());
                return d != null && d.tameable && CustomMobEntity.this.isTame() && super.canUse();
            }
        });

        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal(this) {
            @Override
            public boolean canUse() {
                MobData d = MobRegistry.loadedMobs.get(CustomMobEntity.this.getTemplateId());
                return d != null && d.tameable && CustomMobEntity.this.isTame() && super.canUse();
            }
        });

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return hasGoalType("TARGET_PLAYER") && super.canUse();
            }
        });

        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true) {
            @Override
            public boolean canUse() {
                return hasGoalType("TARGET_VILLAGER") && super.canUse();
            }
        });

        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Animal.class, true) {
            @Override
            public boolean canUse() {
                return hasGoalType("TARGET_ANIMALS") && super.canUse();
            }
        });

        this.targetSelector.addGoal(4, new AttackOthersGoal(this));
        
        // Dynamically add TARGET_GROUP target goals and USE_ABILITY goals based on goals list
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null) {
            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("TARGET_GROUP")) {
                    String grp = g.params.getOrDefault("mobGroup", "");
                    if (!grp.isEmpty()) {
                        this.targetSelector.addGoal(4, new TargetGroupGoal(this, grp));
                    }
                }
                if (g.type.equalsIgnoreCase("USE_ABILITY")) {
                    String ability = g.params.getOrDefault("ability", "");
                    if (!ability.isEmpty()) {
                        this.goalSelector.addGoal(3, new UseAbilityGoal(this, ability));
                    }
                }
            }
        }
    }

    public boolean hasGoalType(String type) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return false;
        for (MobData.AIGoalData goal : data.aiGoals) {
            if (goal.type.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyGoalTypeStartingWith(String prefix) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return false;
        for (MobData.AIGoalData goal : data.aiGoals) {
            if (goal.type.toUpperCase().startsWith(prefix.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    public MobData.AIGoalData getGoalData(String type) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return null;
        for (MobData.AIGoalData goal : data.aiGoals) {
            if (goal.type.equalsIgnoreCase(type)) {
                return goal;
            }
        }
        return null;
    }

    public void startGoalAnimation(String type) {
        MobData.AIGoalData aiGoal = getGoalData(type);
        if (aiGoal != null && !aiGoal.animation.isEmpty()) {
            this.setActiveAnimation(aiGoal.animation);
        }
    }

    public void stopGoalAnimation() {
        this.setActiveAnimation("");
    }

    private void fireProjectile(LivingEntity target) {
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data == null) return;

        String projId = "";
        float damage = 4.0f;
        float accuracy = 100.0f;
        for (MobData.AIGoalData g : data.aiGoals) {
            if (g.type.equalsIgnoreCase("RANGED")) {
                projId = g.params.getOrDefault("projectileId", "fireball");
                try {
                    damage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                } catch (Exception ignored) {}
                try {
                    accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                } catch (Exception ignored) {}
                break;
            }
        }

        if (projId.isEmpty()) return;

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - (this.getY() + this.getEyeHeight() - 0.1);
        double dz = target.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);

        float divergence = (100.0F - accuracy) * 0.15F;

        if (MobRegistry.loadedProjectiles.containsKey(projId)) {
            CustomProjectileEntity proj = new CustomProjectileEntity(this.level(), this);
            proj.setProjectileId(projId);
            proj.setDamage(damage);
            proj.setPos(this.getX(), this.getY() + this.getEyeHeight() - 0.1, this.getZ());
            double compensation = 0.0D;
            ddraig.net.custommobs.data.ProjectileData projData = MobRegistry.loadedProjectiles.get(projId);
            if (projData == null || projData.gravity) {
                compensation = d * 0.2D;
            }
            proj.shoot(dx, dy + compensation, dz, 1.6F, divergence);
            this.level().addFreshEntity(proj);
        } else {
            net.minecraft.resources.ResourceLocation resLoc = net.minecraft.resources.ResourceLocation.tryParse(projId);
            if (resLoc != null) {
                if (resLoc.getNamespace().equals("minecraft") && !projId.contains(":")) {
                    resLoc = new net.minecraft.resources.ResourceLocation("minecraft", projId);
                }
                final float finalDamage = damage;
                final float finalDivergence = divergence;
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                    net.minecraft.world.entity.Entity entity = entityType.create(this.level());
                    if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                        proj.setOwner(this);
                        proj.setPos(this.getX(), this.getY() + this.getEyeHeight() - 0.1, this.getZ());
                        proj.shoot(dx, dy + d * 0.2D, dz, 1.6F, finalDivergence);
                        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                            arrow.setBaseDamage(finalDamage);
                        }
                        this.level().addFreshEntity(proj);
                    }
                });
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hurt = super.doHurtTarget(target);
        if (hurt && target instanceof LivingEntity living) {
            MobData data = MobRegistry.loadedMobs.get(getTemplateId());
            if (data != null) {
                if (data.stats.knockbackInflicted > 0) {
                    double kb = data.stats.knockbackInflicted;
                    living.knockback(kb * 0.5F, Math.sin(this.getYRot() * (Math.PI / 180.0)), -Math.cos(this.getYRot() * (Math.PI / 180.0)));
                }
                if (hasGoalType("EFFECT_ON_ATTACK")) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("EFFECT_ON_ATTACK")) {
                            String effectId = g.params.getOrDefault("effect", "");
                            int duration = 100;
                            int amp = 0;
                            try {
                                duration = Integer.parseInt(g.params.getOrDefault("duration", "100"));
                                amp = Integer.parseInt(g.params.getOrDefault("amplifier", "0"));
                            } catch (Exception ignored) {}
                            if (!effectId.isEmpty()) {
                                var effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId));
                                if (effect != null) {
                                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amp));
                                }
                            }
                            break;
                        }
                    }
                }

                // FROST_TOUCH
                if (hasGoalType("FROST_TOUCH")) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("FROST_TOUCH")) {
                            int duration = 100;
                            int amp = 1;
                            try {
                                duration = Integer.parseInt(g.params.getOrDefault("duration", "100"));
                                amp = Integer.parseInt(g.params.getOrDefault("amplifier", "1"));
                            } catch (Exception ignored) {}
                            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, duration, amp));
                            living.setTicksFrozen(duration);
                            this.level().playSound(null, living.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
                            break;
                        }
                    }
                }

                // DISARM_STRIKE
                if (hasGoalType("DISARM_STRIKE") && target instanceof Player player) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("DISARM_STRIKE")) {
                            double chance = 0.1;
                            try {
                                chance = Double.parseDouble(g.params.getOrDefault("chance", "0.1"));
                            } catch (Exception ignored) {}
                            if (this.random.nextDouble() < chance) {
                                ItemStack mainhand = player.getItemInHand(InteractionHand.MAIN_HAND);
                                if (!mainhand.isEmpty()) {
                                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                    player.drop(mainhand, true);
                                    this.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                                }
                            }
                            break;
                        }
                    }
                }

                // LIGHTNING_STRIKE
                if (hasGoalType("LIGHTNING_STRIKE")) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("LIGHTNING_STRIKE")) {
                            double chance = 0.05;
                            try {
                                chance = Double.parseDouble(g.params.getOrDefault("chance", "0.05"));
                            } catch (Exception ignored) {}
                            if (this.random.nextDouble() < chance) {
                                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(this.level());
                                if (bolt != null) {
                                    bolt.moveTo(living.position());
                                    this.level().addFreshEntity(bolt);
                                }
                            }
                            break;
                        }
                    }
                }

                // STEAL_ITEM
                if (hasGoalType("STEAL_ITEM") && target instanceof Player player) {
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("STEAL_ITEM")) {
                            double chance = 0.1;
                            try {
                                chance = Double.parseDouble(g.params.getOrDefault("chance", "0.1"));
                            } catch (Exception ignored) {}
                            if (this.random.nextDouble() < chance) {
                                var inv = player.getInventory();
                                List<Integer> slots = new ArrayList<>();
                                for (int i = 0; i < inv.getContainerSize(); i++) {
                                    ItemStack stack = inv.getItem(i);
                                    if (!stack.isEmpty() && stack != player.getItemInHand(InteractionHand.MAIN_HAND) && !isEquippedArmor(player, stack)) {
                                        slots.add(i);
                                    }
                                }
                                if (!slots.isEmpty()) {
                                    int randomSlot = slots.get(this.random.nextInt(slots.size()));
                                    ItemStack stolen = inv.getItem(randomSlot).copy();
                                    stolen.setCount(1);
                                    stolenItems.add(stolen);
                                    inv.removeItem(randomSlot, 1);
                                    this.setPersistenceRequired();
                                    this.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.0F, 1.0F);
                                    player.displayClientMessage(Component.literal("§cAn item was stolen from your inventory!"), true);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        return hurt;
    }

    private boolean isEquippedArmor(Player player, ItemStack stack) {
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor == stack) return true;
        }
        if (player.getOffhandItem() == stack) return true;
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MobData data = MobRegistry.loadedMobs.get(getTemplateId());
        if (data != null && data.tameable) {
            if (this.isTame()) {
                if (this.isOwnedBy(player)) {
                    if (!this.level().isClientSide) {
                        this.setOrderedToSit(!this.isOrderedToSit());
                        this.jumping = false;
                        this.navigation.stop();
                        this.setTarget(null);
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            } else {
                String itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (itemKey.equalsIgnoreCase(data.tamingItem)) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    if (!this.level().isClientSide) {
                        if (this.random.nextDouble() * 100.0 < data.tamingChance) {
                            this.tame(player);
                            this.navigation.stop();
                            this.setTarget(null);
                            this.setOrderedToSit(true);
                            this.level().broadcastEntityEvent(this, (byte)7);
                        } else {
                            this.level().broadcastEntityEvent(this, (byte)6);
                        }
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }



    private static class SummonGroundAttackGoal extends Goal {
        private final CustomMobEntity mob;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public SummonGroundAttackGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.checkCombatSequence("SUMMON_GROUND_ATTACK")) {
                return false;
            }
            return mob.hasGoalType("SUMMON_GROUND_ATTACK") && mob.getTarget() != null;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData("SUMMON_GROUND_ATTACK");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("SUMMON_GROUND_ATTACK");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSqr > 15.0 * 15.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distSqr < 8.0 * 8.0) {
                net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (awayPos != null) {
                    mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                } else {
                    mob.getNavigation().stop();
                }
            } else {
                mob.getNavigation().stop();
            }

            int delay = 40;
            MobData.AIGoalData aiGoal = mob.getGoalData("SUMMON_GROUND_ATTACK");
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                } catch (Exception ignored) {}
            }
            attackTime++;
            if (attackTime >= delay) {
                attackTime = 0;
                executeGroundAttack(target);
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeGroundAttack(LivingEntity target) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            String projId = "";
            float rawDamage = 4.0f;
            String soundId = "";

            double upwardKnockbackVal = 0.0D;
            double upwardSpeedVal = 1.0D;
            double maxHeightVal = -1.0D;
            float accuracy = 100.0f;

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("SUMMON_GROUND_ATTACK")) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        upwardKnockbackVal = Double.parseDouble(g.params.getOrDefault("upwardKnockback", "0.0"));
                    } catch (Exception ignored) {}
                    try {
                        upwardSpeedVal = Double.parseDouble(g.params.getOrDefault("upwardSpeed", "1.0"));
                    } catch (Exception ignored) {}
                    try {
                        maxHeightVal = Double.parseDouble(g.params.getOrDefault("maxHeight", "-1.0"));
                    } catch (Exception ignored) {}
                    try {
                        accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final double upwardKnockback = upwardKnockbackVal;
            final double upwardSpeed = upwardSpeedVal;
            final double maxHeight = maxHeightVal;
            final float divergence = (100.0F - accuracy) * 0.15F;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 1.0F);
                }

                if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                    CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                    proj.setProjectileId(projId);
                    proj.setDamage(damage);
                    proj.setMaxHeight(maxHeight);
                    proj.isGroundSummon = true;
                    proj.setPos(target.getX(), target.getY() + 0.125D, target.getZ());
                    proj.shoot(0.0D, 1.0D, 0.0D, (float) upwardSpeed, divergence);
                    serverLevel.addFreshEntity(proj);
                } else {
                    ResourceLocation resLoc = ResourceLocation.tryParse(projId);
                    if (resLoc != null) {
                        final float finalDivergence = divergence;
                        BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                            Entity entity = entityType.create(serverLevel);
                            if (entity != null) {
                                entity.setPos(target.getX(), target.getY() + 0.125D, target.getZ());
                                if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                                    proj.setOwner(mob);
                                    proj.shoot(0.0D, 1.0D, 0.0D, (float) upwardSpeed, finalDivergence);
                                    if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                                        arrow.setBaseDamage(damage);
                                    }
                                }
                                serverLevel.addFreshEntity(entity);
                            }
                        });
                    }
                }
                
                if (upwardKnockback > 0.0D) {
                    target.setDeltaMovement(target.getDeltaMovement().add(0.0D, upwardKnockback, 0.0D));
                    target.hurtMarked = true;
                }
            }
        }
    }

    private static class SummonGroundAttackAOEGoal extends Goal {
        private final CustomMobEntity mob;
        private final String goalType;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public SummonGroundAttackAOEGoal(CustomMobEntity mob, String goalType) {
            this.mob = mob;
            this.goalType = goalType;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.checkCombatSequence(goalType)) {
                return false;
            }
            return mob.hasGoalType(goalType) && mob.getTarget() != null;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSqr > 15.0 * 15.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distSqr < 8.0 * 8.0) {
                net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (awayPos != null) {
                    mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                } else {
                    mob.getNavigation().stop();
                }
            } else {
                mob.getNavigation().stop();
            }

            int delay = 40;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                } catch (Exception ignored) {}
            }
            attackTime++;
            if (attackTime >= delay) {
                attackTime = 0;
                executeGroundAttackAOE();
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeGroundAttackAOE() {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            String projId = "";
            float rawDamage = 4.0f;
            String soundId = "";
            double upwardKnockbackVal = 0.0D;
            double upwardSpeedVal = 1.0D;
            double maxHeightVal = -1.0D;
            double radiusVal = 12.0D;
            float accuracy = 100.0f;

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase(goalType)) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        upwardKnockbackVal = Double.parseDouble(g.params.getOrDefault("upwardKnockback", "0.0"));
                    } catch (Exception ignored) {}
                    try {
                        upwardSpeedVal = Double.parseDouble(g.params.getOrDefault("upwardSpeed", "1.0"));
                    } catch (Exception ignored) {}
                    try {
                        maxHeightVal = Double.parseDouble(g.params.getOrDefault("maxHeight", "-1.0"));
                    } catch (Exception ignored) {}
                    try {
                        radiusVal = Double.parseDouble(g.params.getOrDefault("radius", "12.0"));
                    } catch (Exception ignored) {}
                    try {
                        accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final double upwardKnockback = upwardKnockbackVal;
            final double upwardSpeed = upwardSpeedVal;
            final double maxHeight = maxHeightVal;
            final double radius = radiusVal;
            final float divergence = (100.0F - accuracy) * 0.15F;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(radius), entity -> {
                    return entity != mob && entity.isAlive() && (entity instanceof Player || (mob.getTarget() != null && entity == mob.getTarget()));
                });

                if (targets.isEmpty()) return;

                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 1.0F);
                }

                for (LivingEntity target : targets) {
                    if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                        CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                        proj.setProjectileId(projId);
                        proj.setDamage(damage);
                        proj.setMaxHeight(maxHeight);
                        proj.isGroundSummon = true;
                        proj.setPos(target.getX(), target.getY() + 0.125D, target.getZ());
                        proj.shoot(0.0D, 1.0D, 0.0D, (float) upwardSpeed, divergence);
                        serverLevel.addFreshEntity(proj);
                    } else {
                        ResourceLocation resLoc = ResourceLocation.tryParse(projId);
                        if (resLoc != null) {
                            final float finalDivergence = divergence;
                            BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                                Entity entity = entityType.create(serverLevel);
                                if (entity != null) {
                                    entity.setPos(target.getX(), target.getY() + 0.125D, target.getZ());
                                    if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                                        proj.setOwner(mob);
                                        proj.shoot(0.0D, 1.0D, 0.0D, (float) upwardSpeed, finalDivergence);
                                        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                                            arrow.setBaseDamage(damage);
                                        }
                                    }
                                    serverLevel.addFreshEntity(entity);
                                }
                            });
                        }
                    }

                    if (upwardKnockback > 0.0D) {
                        target.setDeltaMovement(target.getDeltaMovement().add(0.0D, upwardKnockback, 0.0D));
                        target.hurtMarked = true;
                    }
                }
            }
        }
    }

    private static class ShotgunAttackGoal extends Goal {
        private final CustomMobEntity mob;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public ShotgunAttackGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.checkCombatSequence("SHOTGUN_ATTACK")) {
                return false;
            }
            return mob.hasGoalType("SHOTGUN_ATTACK") && mob.getTarget() != null;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData("SHOTGUN_ATTACK");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("SHOTGUN_ATTACK");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSqr > 15.0 * 15.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distSqr < 8.0 * 8.0) {
                net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (awayPos != null) {
                    mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                } else {
                    mob.getNavigation().stop();
                }
            } else {
                mob.getNavigation().stop();
            }

            int delay = 40;
            MobData.AIGoalData aiGoal = mob.getGoalData("SHOTGUN_ATTACK");
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                } catch (Exception ignored) {}
            }
            attackTime++;
            if (attackTime >= delay) {
                attackTime = 0;
                executeShotgunAttack(target);
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeShotgunAttack(LivingEntity target) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            String projId = "";
            float rawDamage = 4.0f;
            String soundId = "";
            int quantityVal = 5;
            double spreadVal = 30.0D;
            float accuracy = 100.0f;

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("SHOTGUN_ATTACK")) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        quantityVal = Integer.parseInt(g.params.getOrDefault("quantity", "5"));
                    } catch (Exception ignored) {}
                    try {
                        spreadVal = Double.parseDouble(g.params.getOrDefault("spread", "30.0"));
                    } catch (Exception ignored) {}
                    try {
                        accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final int quantity = quantityVal;
            final double coneAngle = spreadVal;
            final float baseDivergence = (100.0F - accuracy) * 0.15F;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
                }

                double dx = target.getX() - mob.getX();
                double dy = target.getY(0.3333333333333333) - (mob.getY() + mob.getEyeHeight() - 0.1);
                double dz = target.getZ() - mob.getZ();

                double yaw = Math.atan2(dz, dx);
                double startAngle = yaw - Math.toRadians(coneAngle / 2.0D);
                double step = quantity > 1 ? Math.toRadians(coneAngle) / (quantity - 1) : 0.0D;

                for (int i = 0; i < quantity; i++) {
                    double currentYaw = startAngle + i * step;
                    double sx = Math.cos(currentYaw);
                    double sz = Math.sin(currentYaw);
                    double sy = dy;

                    if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                        CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                        proj.setProjectileId(projId);
                        proj.setDamage(damage);
                        proj.setPos(mob.getX(), mob.getY() + mob.getEyeHeight() - 0.1, mob.getZ());
                        proj.shoot(sx, sy, sz, 1.6F, baseDivergence);
                        serverLevel.addFreshEntity(proj);
                    } else {
                        ResourceLocation resLoc = ResourceLocation.tryParse(projId);
                        if (resLoc != null) {
                            final float finalDivergence = baseDivergence;
                            BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                                Entity entity = entityType.create(serverLevel);
                                if (entity != null) {
                                    entity.setPos(mob.getX(), mob.getY() + mob.getEyeHeight() - 0.1, mob.getZ());
                                    if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                                        proj.setOwner(mob);
                                        proj.shoot(sx, sy, sz, 1.6F, finalDivergence);
                                        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                                            arrow.setBaseDamage(damage);
                                        }
                                    }
                                    serverLevel.addFreshEntity(entity);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private static class OrbitingShieldGoal extends Goal {
        private final CustomMobEntity mob;
        private int cooldownTime = 0;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public OrbitingShieldGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (this.cooldownTime > 0) {
                this.cooldownTime--;
            }
            if (!mob.checkCombatSequence("ORBITING_SHIELD")) {
                return false;
            }
            return mob.hasGoalType("ORBITING_SHIELD") && mob.getTarget() != null && this.cooldownTime <= 0;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData("ORBITING_SHIELD");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("ORBITING_SHIELD");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            int delay = 40;
            MobData.AIGoalData aiGoal = mob.getGoalData("ORBITING_SHIELD");
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                } catch (Exception ignored) {}
            }
            this.attackTime++;
            if (this.attackTime >= delay) {
                this.attackTime = 0;

                int cooldown = 200;
                if (aiGoal != null) {
                    try {
                        cooldown = Integer.parseInt(aiGoal.params.getOrDefault("cooldown", "200"));
                    } catch (Exception ignored) {}
                }
                this.cooldownTime = cooldown;

                executeOrbitingShield();
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeOrbitingShield() {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            String projId = "";
            float rawDamage = 4.0f;
            String soundId = "";
            int quantityVal = 3;
            double radiusVal = 1.5D;
            double speedVal = 4.0D;
            int durationVal = 200;

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("ORBITING_SHIELD")) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        quantityVal = Integer.parseInt(g.params.getOrDefault("quantity", "3"));
                    } catch (Exception ignored) {}
                    try {
                        radiusVal = Double.parseDouble(g.params.getOrDefault("radius", "1.5"));
                    } catch (Exception ignored) {}
                    try {
                        speedVal = Double.parseDouble(g.params.getOrDefault("speed", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        durationVal = Integer.parseInt(g.params.getOrDefault("duration", "200"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final int quantity = quantityVal;
            final double radius = radiusVal;
            final double speed = speedVal;
            final int duration = durationVal;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
                }

                double angleStep = 360.0D / quantity;
                for (int i = 0; i < quantity; i++) {
                    double startAngle = i * angleStep;
                    CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                    proj.setProjectileId(projId);
                    proj.setDamage(damage);

                    double rad = Math.toRadians(startAngle);
                    proj.setPos(mob.getX() + radius * Math.cos(rad), mob.getY() + mob.getEyeHeight() * 0.5D, mob.getZ() + radius * Math.sin(rad));

                    proj.getEntityData().set(CustomProjectileEntity.STUCK_ENTITY_ID, mob.getId());
                    proj.getEntityData().set(CustomProjectileEntity.IS_ORBITING, true);
                    proj.getEntityData().set(CustomProjectileEntity.ORBIT_RADIUS, (float) radius);
                    proj.getEntityData().set(CustomProjectileEntity.ORBIT_SPEED, (float) speed);
                    proj.getEntityData().set(CustomProjectileEntity.ORBIT_ANGLE, (float) startAngle);
                    proj.orbitLifetime = duration;

                    serverLevel.addFreshEntity(proj);
                }
            }
        }
    }

    private static class AerialRangedAttackGoal extends Goal {
        private final CustomMobEntity mob;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public AerialRangedAttackGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.checkCombatSequence("AERIAL_RANGED_ATTACK")) {
                return false;
            }
            return mob.hasGoalType("AERIAL_RANGED_ATTACK") && mob.getTarget() != null;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData("AERIAL_RANGED_ATTACK");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("AERIAL_RANGED_ATTACK");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSqr > 15.0 * 15.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distSqr < 8.0 * 8.0) {
                net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (awayPos != null) {
                    mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                } else {
                    mob.getNavigation().stop();
                }
            } else {
                mob.getNavigation().stop();
            }

            int delay = 60;
            MobData.AIGoalData aiGoal = mob.getGoalData("AERIAL_RANGED_ATTACK");
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "60"));
                } catch (Exception ignored) {}
            }
            attackTime++;
            if (attackTime >= delay) {
                attackTime = 0;
                executeAerialAttack(target);
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeAerialAttack(LivingEntity target) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            double dropSpeedVal = 1.0D;
            int quantityVal = 3;
            double spreadVal = 4.0D;
            String projId = "";
            float rawDamage = 4.0f;
            float accuracy = 100.0f;
            String soundId = "";

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase("AERIAL_RANGED_ATTACK")) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        dropSpeedVal = Double.parseDouble(g.params.getOrDefault("gravity", g.params.getOrDefault("drop_speed", "1.0")));
                    } catch (Exception ignored) {}
                    try {
                        quantityVal = Integer.parseInt(g.params.getOrDefault("quantity", "3"));
                    } catch (Exception ignored) {}
                    try {
                        spreadVal = Double.parseDouble(g.params.getOrDefault("spread", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final double dropSpeed = dropSpeedVal;
            final int quantity = quantityVal;
            final double spread = spreadVal;
            final float divergence = (100.0F - accuracy) * 0.15F;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, target.getX(), target.getY() + 10.0D, target.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, target.getX(), target.getY() + 10.0D, target.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 1.0F);
                }

                for (int i = 0; i < quantity; i++) {
                    double ox = (mob.random.nextDouble() - 0.5D) * spread;
                    double oz = (mob.random.nextDouble() - 0.5D) * spread;
                    double px = target.getX() + ox;
                    double py = target.getY() + 10.0D + mob.random.nextDouble() * 4.0D;
                    double pz = target.getZ() + oz;

                    if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                        CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                        proj.setProjectileId(projId);
                        proj.setDamage(damage);
                        proj.setPos(px, py, pz);
                        proj.shoot(0.0D, -1.0D, 0.0D, (float) dropSpeed, divergence);
                        serverLevel.addFreshEntity(proj);
                    } else {
                        ResourceLocation resLoc = ResourceLocation.tryParse(projId);
                        if (resLoc != null) {
                            final float finalDivergence = divergence;
                            BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                                Entity entity = entityType.create(serverLevel);
                                if (entity != null) {
                                    entity.setPos(px, py, pz);
                                    if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                                        proj.setOwner(mob);
                                        proj.shoot(0.0D, -1.0D, 0.0D, (float) dropSpeed, finalDivergence);
                                        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                                            arrow.setBaseDamage(damage);
                                        }
                                    }
                                    serverLevel.addFreshEntity(entity);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private static class AerialRangedAOEGoal extends Goal {
        private final CustomMobEntity mob;
        private final String goalType;
        private int attackTime = 0;
        private boolean hasAttacked = false;

        public AerialRangedAOEGoal(CustomMobEntity mob, String goalType) {
            this.mob = mob;
            this.goalType = goalType;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.checkCombatSequence(goalType)) {
                return false;
            }
            return mob.hasGoalType(goalType) && mob.getTarget() != null;
        }

        @Override
        public void start() {
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
            this.attackTime = 0;
            this.hasAttacked = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !hasAttacked && mob.getTarget() != null;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (hasAttacked && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSqr > 15.0 * 15.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distSqr < 8.0 * 8.0) {
                net.minecraft.world.phys.Vec3 awayPos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (awayPos != null) {
                    mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, 1.2D);
                } else {
                    mob.getNavigation().stop();
                }
            } else {
                mob.getNavigation().stop();
            }

            int delay = 60;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null) {
                try {
                    delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "60"));
                } catch (Exception ignored) {}
            }
            attackTime++;
            if (attackTime >= delay) {
                attackTime = 0;
                executeAerialAOE(target);
                mob.swing(InteractionHand.MAIN_HAND);
                this.hasAttacked = true;
            }
        }

        private void executeAerialAOE(LivingEntity target) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            double dropSpeedVal = 1.0D;
            int quantityVal = 3;
            double spreadVal = 4.0D;
            String projId = "";
            float rawDamage = 4.0f;
            float accuracy = 100.0f;
            String soundId = "";

            for (MobData.AIGoalData g : data.aiGoals) {
                if (g.type.equalsIgnoreCase(goalType)) {
                    soundId = g.params.getOrDefault("sound", "");
                    projId = g.params.getOrDefault("projectileId", "fireball");
                    try {
                        rawDamage = Float.parseFloat(g.params.getOrDefault("damage", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        dropSpeedVal = Double.parseDouble(g.params.getOrDefault("gravity", g.params.getOrDefault("drop_speed", "1.0")));
                    } catch (Exception ignored) {}
                    try {
                        quantityVal = Integer.parseInt(g.params.getOrDefault("quantity", "3"));
                    } catch (Exception ignored) {}
                    try {
                        spreadVal = Double.parseDouble(g.params.getOrDefault("spread", "4.0"));
                    } catch (Exception ignored) {}
                    try {
                        accuracy = Float.parseFloat(g.params.getOrDefault("accuracy", "100.0"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            final float damage = rawDamage;
            final double dropSpeed = dropSpeedVal;
            final int quantity = quantityVal;
            final double spread = spreadVal;
            final float divergence = (100.0F - accuracy) * 0.15F;

            if (projId.isEmpty()) return;

            if (mob.level() instanceof ServerLevel serverLevel) {
                if (!soundId.isEmpty()) {
                    try {
                        var sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                        if (sound != null) {
                            serverLevel.playSound(null, target.getX(), target.getY() + 10.0D, target.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                        }
                    } catch (Exception ignored) {}
                } else {
                    serverLevel.playSound(null, target.getX(), target.getY() + 10.0D, target.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 1.0F);
                }

                for (int i = 0; i < quantity; i++) {
                    double ox = (mob.random.nextDouble() - 0.5D) * 2.0D * spread;
                    double oz = (mob.random.nextDouble() - 0.5D) * 2.0D * spread;
                    double px = target.getX() + ox;
                    double py = target.getY() + 10.0D + mob.random.nextDouble() * 4.0D;
                    double pz = target.getZ() + oz;

                    if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                        CustomProjectileEntity proj = new CustomProjectileEntity(serverLevel, mob);
                        proj.setProjectileId(projId);
                        proj.setDamage(damage);
                        proj.setPos(px, py, pz);
                        proj.shoot(0.0D, -1.0D, 0.0D, (float) dropSpeed, divergence);
                        serverLevel.addFreshEntity(proj);
                    } else {
                        ResourceLocation resLoc = ResourceLocation.tryParse(projId);
                        if (resLoc != null) {
                            final float finalDivergence = divergence;
                            BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc).ifPresent(entityType -> {
                                Entity entity = entityType.create(serverLevel);
                                if (entity != null) {
                                    entity.setPos(px, py, pz);
                                    if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                                        proj.setOwner(mob);
                                        proj.shoot(0.0D, -1.0D, 0.0D, (float) dropSpeed, finalDivergence);
                                        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                                            arrow.setBaseDamage(damage);
                                        }
                                    }
                                    serverLevel.addFreshEntity(entity);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private static class ReturnToSpawnGoal extends Goal {
        private final CustomMobEntity mob;

        public ReturnToSpawnGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("RETURN_TO_SPAWN")) return false;
            if (mob.getTarget() != null) return false;
            
            if (mob.spawnPointPos == null) {
                mob.spawnPointPos = mob.blockPosition();
            }
            double dist = mob.blockPosition().distSqr(mob.spawnPointPos);
            double leash = ModConfig.getVal("goal_constants", "return_to_spawn_leash_distance");
            return dist > leash * leash;
        }

        @Override
        public void start() {
            if (mob.spawnPointPos != null) {
                mob.getNavigation().moveTo(mob.spawnPointPos.getX(), mob.spawnPointPos.getY(), mob.spawnPointPos.getZ(), 1.0D);
                MobData.AIGoalData aiGoal = mob.getGoalData("RETURN_TO_SPAWN");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && !mob.getNavigation().isDone() && mob.spawnPointPos != null && mob.blockPosition().distSqr(mob.spawnPointPos) > 4.0;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("RETURN_TO_SPAWN");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class HealAlliesGoal extends Goal {
        private final CustomMobEntity mob;
        private LivingEntity target;
        private int cooldown;

        public HealAlliesGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("HEAL_ALLIES")) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null || data.mobGroup.isEmpty()) return false;

            double range = 16.0;
            List<CustomMobEntity> list = mob.level().getEntitiesOfClass(CustomMobEntity.class, mob.getBoundingBox().inflate(range),
                e -> e != mob && e.isAlive() && e.getHealth() < e.getMaxHealth());
            for (CustomMobEntity ally : list) {
                MobData allyData = MobRegistry.loadedMobs.get(ally.getTemplateId());
                if (allyData != null && data.mobGroup.equalsIgnoreCase(allyData.mobGroup)) {
                    target = ally;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(target, 1.2D);
            MobData.AIGoalData aiGoal = mob.getGoalData("HEAL_ALLIES");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public void tick() {
            if (target == null) return;
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (mob.getBoundingBox().inflate(2.0D).intersects(target.getBoundingBox())) {
                mob.getNavigation().stop();
                
                float healAmount = 5.0F;
                MobData.AIGoalData aiGoal = mob.getGoalData("HEAL_ALLIES");
                if (aiGoal != null) {
                    try {
                        healAmount = Float.parseFloat(aiGoal.params.getOrDefault("heal_amount", "5.0"));
                    } catch (Exception ignored) {}
                }
                
                target.heal(healAmount);
                cooldown = mob.getGoalDelayTicks("HEAL_ALLIES", 100);
                
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1.0, mob.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && target.getHealth() < target.getMaxHealth() && cooldown <= 0;
        }

        @Override
        public void stop() {
            target = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("HEAL_ALLIES");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class LeapAttackGoal extends Goal {
        private final CustomMobEntity mob;
        private LivingEntity target;

        public LeapAttackGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("LEAP_ATTACK") || mob.isGoalOnCooldown("LEAP_ATTACK")) return false;
            target = mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            double dist = mob.distanceToSqr(target);
            return dist >= 9.0 && dist <= 25.0 && mob.onGround();
        }

        @Override
        public void start() {
            if (target == null) return;
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double factor = ModConfig.getVal("goal_constants", "leap_velocity");
            mob.setDeltaMovement(dx * 0.15D * factor, 0.42D, dz * 0.15D * factor);
            mob.startGoalCooldown("LEAP_ATTACK", mob.getGoalDelayTicks("LEAP_ATTACK", 100));
            
            MobData.AIGoalData aiGoal = mob.getGoalData("LEAP_ATTACK");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.onGround();
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("LEAP_ATTACK");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class StalkGoal extends Goal {
        private final CustomMobEntity mob;
        private LivingEntity target;
        private int sightTransitions = 0;
        private boolean wasSeenLast = true;

        public StalkGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        public boolean isStalkingComplete() {
            return sightTransitions >= (int) ModConfig.getVal("goal_constants", "stalk_sight_count");
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("STALK")) return false;
            target = mob.getTarget();
            return target != null && target.isAlive() && sightTransitions < (int) ModConfig.getVal("goal_constants", "stalk_sight_count");
        }

        @Override
        public void start() {
            sightTransitions = 0;
            if (target != null) {
                wasSeenLast = mob.getSensing().hasLineOfSight(target);
            }
            MobData.AIGoalData aiGoal = mob.getGoalData("STALK");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public void tick() {
            if (target == null) return;
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            boolean canSee = mob.getSensing().hasLineOfSight(target);
            if (canSee != wasSeenLast) {
                sightTransitions++;
                wasSeenLast = canSee;
            }

            double dist = mob.distanceToSqr(target);
            if (dist > 64.0) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (dist < 16.0) {
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(target, 0.8D);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && sightTransitions < (int) ModConfig.getVal("goal_constants", "stalk_sight_count");
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("STALK");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            sightTransitions = 0;
        }
    }

    private static class SearchGoal extends Goal {
        private final CustomMobEntity mob;
        private Vec3 lastKnownPos;
        private int lookTime;

        public SearchGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEARCH") || mob.isGoalOnCooldown("SEARCH")) return false;
            LivingEntity target = mob.getTarget();
            if (target != null && mob.getSensing().hasLineOfSight(target)) {
                lastKnownPos = target.position();
                return false;
            }
            return lastKnownPos != null && mob.getTarget() == null;
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(lastKnownPos.x, lastKnownPos.y, lastKnownPos.z, 1.2D);
            lookTime = 60;
            mob.startGoalCooldown("SEARCH", mob.getGoalDelayTicks("SEARCH", 0));
            MobData.AIGoalData aiGoal = mob.getGoalData("SEARCH");
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone()) {
                lookTime--;
                mob.setYRot(mob.getYRot() + 5.0F);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return lastKnownPos != null && lookTime > 0 && mob.getTarget() == null;
        }

        @Override
        public void stop() {
            lastKnownPos = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("SEARCH");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class AvoidLightGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos escapePos;

        public AvoidLightGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_LIGHT") || mob.isGoalOnCooldown("AVOID_LIGHT")) return false;
            BlockPos pos = mob.blockPosition();
            int light = mob.level().getMaxLocalRawBrightness(pos);
            double threshold = ModConfig.getVal("goal_constants", "bright_light_threshold");
            if (light >= threshold) {
                escapePos = findDarkerBlock();
                return escapePos != null;
            }
            return false;
        }

        @Override
        public void start() {
            if (escapePos != null) {
                mob.getNavigation().moveTo(escapePos.getX(), escapePos.getY(), escapePos.getZ(), 1.2D);
                mob.startGoalCooldown("AVOID_LIGHT", mob.getGoalDelayTicks("AVOID_LIGHT", 0));
                MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_LIGHT");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return escapePos != null && !mob.getNavigation().isDone() && mob.level().getMaxLocalRawBrightness(mob.blockPosition()) >= 8;
        }

        @Override
        public void stop() {
            escapePos = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_LIGHT");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }

        private BlockPos findDarkerBlock() {
            RandomSource rand = mob.getRandom();
            BlockPos current = mob.blockPosition();
            for (int i = 0; i < 10; i++) {
                BlockPos p = current.offset(rand.nextInt(16) - 8, rand.nextInt(6) - 3, rand.nextInt(16) - 8);
                if (mob.level().getMaxLocalRawBrightness(p) < 8) {
                    return p;
                }
            }
            return null;
        }
    }

    private static class AvoidPlayerWearingGoal extends Goal {
        private final CustomMobEntity mob;
        private Player targetPlayer;

        public AvoidPlayerWearingGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_PLAYER_WEARING") || mob.isGoalOnCooldown("AVOID_PLAYER_WEARING")) return false;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_PLAYER_WEARING");
            if (aiGoal == null) return false;
            String armorItem = aiGoal.params.getOrDefault("armorItem", "");
            if (armorItem.isEmpty()) return false;

            List<Player> players = mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(12.0));
            for (Player p : players) {
                for (ItemStack armor : p.getArmorSlots()) {
                    String key = BuiltInRegistries.ITEM.getKey(armor.getItem()).toString();
                    if (key.equalsIgnoreCase(armorItem)) {
                        targetPlayer = p;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void start() {
            if (targetPlayer != null) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, targetPlayer.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
                }
                mob.startGoalCooldown("AVOID_PLAYER_WEARING", mob.getGoalDelayTicks("AVOID_PLAYER_WEARING", 0));
                MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_PLAYER_WEARING");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && !mob.getNavigation().isDone() && mob.distanceToSqr(targetPlayer) < 256.0;
        }

        @Override
        public void stop() {
            targetPlayer = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_PLAYER_WEARING");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class AvoidMobGoal extends Goal {
        private final CustomMobEntity mob;
        private LivingEntity targetMob;

        public AvoidMobGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_MOB") || mob.isGoalOnCooldown("AVOID_MOB")) return false;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_MOB");
            if (aiGoal == null) return false;
            String targetMobId = aiGoal.params.getOrDefault("mobId", "");
            if (targetMobId.isEmpty()) return false;

            var res = new ResourceLocation(targetMobId);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(res);
            if (type == null) return false;

            List<? extends LivingEntity> list = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12.0),
                e -> e.getType() == type);
            if (!list.isEmpty()) {
                targetMob = list.get(0);
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            if (targetMob != null) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, targetMob.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
                }
                mob.startGoalCooldown("AVOID_MOB", mob.getGoalDelayTicks("AVOID_MOB", 0));
                MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_MOB");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return targetMob != null && !mob.getNavigation().isDone() && mob.distanceToSqr(targetMob) < 256.0;
        }

        @Override
        public void stop() {
            targetMob = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_MOB");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class AvoidGroupGoal extends Goal {
        private final CustomMobEntity mob;
        private CustomMobEntity targetGroupMob;

        public AvoidGroupGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_GROUP") || mob.isGoalOnCooldown("AVOID_GROUP")) return false;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_GROUP");
            if (aiGoal == null) return false;
            String targetGroup = aiGoal.params.getOrDefault("mobGroup", "");
            if (targetGroup.isEmpty()) return false;

            List<CustomMobEntity> list = mob.level().getEntitiesOfClass(CustomMobEntity.class, mob.getBoundingBox().inflate(12.0),
                e -> e != mob && e.isAlive());
            for (CustomMobEntity e : list) {
                MobData eData = MobRegistry.loadedMobs.get(e.getTemplateId());
                if (eData != null && targetGroup.equalsIgnoreCase(eData.mobGroup)) {
                    targetGroupMob = e;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            if (targetGroupMob != null) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, targetGroupMob.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
                }
                mob.startGoalCooldown("AVOID_GROUP", mob.getGoalDelayTicks("AVOID_GROUP", 0));
                MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_GROUP");
                if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return targetGroupMob != null && !mob.getNavigation().isDone() && mob.distanceToSqr(targetGroupMob) < 256.0;
        }

        @Override
        public void stop() {
            targetGroupMob = null;
            MobData.AIGoalData aiGoal = mob.getGoalData("AVOID_GROUP");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
        }
    }

    private static class AttackOthersGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private final CustomMobEntity mob;

        public AttackOthersGoal(CustomMobEntity mob) {
            super(mob, LivingEntity.class, true);
            this.mob = mob;
            this.targetConditions.selector(target -> {
                if (target == null || target == mob) return false;
                if (target instanceof Player || target instanceof net.minecraft.world.entity.npc.AbstractVillager) return false;

                MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
                if (data != null) {
                    if (target instanceof CustomMobEntity customTarget) {
                        MobData targetData = MobRegistry.loadedMobs.get(customTarget.getTemplateId());
                        if (targetData != null && !data.mobGroup.isEmpty() && data.mobGroup.equalsIgnoreCase(targetData.mobGroup)) {
                            return false;
                        }
                    }
                    for (MobData.AIGoalData g : data.aiGoals) {
                        if (g.type.equalsIgnoreCase("DO_NOT_ATTACK_GROUP")) {
                            String group = g.params.getOrDefault("mobGroup", "");
                            if (!group.isEmpty() && target instanceof CustomMobEntity customTarget) {
                                MobData targetData = MobRegistry.loadedMobs.get(customTarget.getTemplateId());
                                if (targetData != null && group.equalsIgnoreCase(targetData.mobGroup)) {
                                    return false;
                                }
                            }
                        }
                    }
                }
                return true;
            });
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("ATTACK_OTHERS")) return false;
            return super.canUse();
        }
    }

    private static class TargetGroupGoal extends NearestAttackableTargetGoal<CustomMobEntity> {
        private final CustomMobEntity mob;
        private final String targetGroup;

        public TargetGroupGoal(CustomMobEntity mob, String targetGroup) {
            super(mob, CustomMobEntity.class, true);
            this.mob = mob;
            this.targetGroup = targetGroup;
            this.targetConditions.selector(target -> {
                if (target == null || target == mob) return false;
                if (!(target instanceof CustomMobEntity customTarget)) return false;
                MobData targetData = MobRegistry.loadedMobs.get(customTarget.getTemplateId());
                return targetData != null && targetGroup.equalsIgnoreCase(targetData.mobGroup);
            });
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("TARGET_GROUP")) return false;
            return super.canUse();
        }
    }

    private static class UseAbilityGoal extends Goal {
        private final CustomMobEntity mob;
        private final String abilityName;

        public UseAbilityGoal(CustomMobEntity mob, String abilityName) {
            this.mob = mob;
            this.abilityName = abilityName;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("USE_ABILITY") || mob.isGoalOnCooldown("USE_ABILITY")) return false;
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) return false;

            int cd = mob.abilityCooldowns.getOrDefault(abilityName, 0);
            if (cd > 0) return false;

            double dist = mob.distanceToSqr(target);
            return dist < 64.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            MobData.AbilityData ab = null;
            for (var a : data.abilities) {
                if (a.name.equalsIgnoreCase(abilityName)) {
                    ab = a;
                    break;
                }
            }

            if (ab != null) {
                mob.executeAbility(ab);
                mob.abilityCooldowns.put(ab.name, ab.cooldownTicks);
                mob.startGoalCooldown("USE_ABILITY", mob.getGoalDelayTicks("USE_ABILITY", 0));

                for (MobData.AIGoalData g : data.aiGoals) {
                    if (g.type.equalsIgnoreCase("USE_ABILITY") && abilityName.equalsIgnoreCase(g.params.getOrDefault("ability", ""))) {
                        if (!g.animation.isEmpty()) {
                            mob.setActiveAnimation(g.animation);
                        }
                    }
                }
            }
        }
    }

    public static class CreeperLikeExplodeGoal extends Goal {
        private final CustomMobEntity mob;
        private LivingEntity target;
        private int fuse;

        public CreeperLikeExplodeGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("EXPLODE_ON_CONTACT")) return false;
            this.target = mob.getTarget();
            return this.target != null && mob.distanceToSqr(this.target) < 16.0D;
        }

        @Override
        public void start() {
            mob.getNavigation().stop();
            this.fuse = 0;
            mob.startGoalAnimation("EXPLODE_ON_CONTACT");
        }

        @Override
        public void stop() {
            this.target = null;
            mob.stopGoalAnimation();
        }

        @Override
        public void tick() {
            if (this.target == null) return;
            mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (mob.distanceToSqr(this.target) > 25.0D) {
                mob.getNavigation().moveTo(this.target, 1.25D);
            } else {
                mob.getNavigation().stop();
                this.fuse++;
                if (this.fuse >= 30) {
                    explode();
                }
            }
        }

        private void explode() {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            float power = 3.0F;
            boolean breakBlocks = false;
            boolean setFire = false;
            if (data != null) {
                for (MobData.AIGoalData g : data.aiGoals) {
                    if (g.type.equalsIgnoreCase("EXPLODE_ON_CONTACT")) {
                        try {
                            power = Float.parseFloat(g.params.getOrDefault("power", "3.0"));
                            breakBlocks = Boolean.parseBoolean(g.params.getOrDefault("break_blocks", "false"));
                            setFire = Boolean.parseBoolean(g.params.getOrDefault("set_fire", "false"));
                        } catch (Exception ignored) {}
                        break;
                    }
                }
            }
            mob.level().explode(mob, mob.getX(), mob.getY(), mob.getZ(), power, setFire, breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
            mob.discard();
        }
    }

    public static class FleeRainGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos shelterPos;

        public FleeRainGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_RAIN") && !mob.hasGoalType("FLEE_RAIN")) return false;
            if (!mob.level().isRaining()) return false;
            if (!mob.level().canSeeSky(mob.blockPosition())) return false;
            this.shelterPos = this.findShelter();
            return this.shelterPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone() && mob.level().isRaining() && mob.level().canSeeSky(mob.blockPosition());
        }

        @Override
        public void start() {
            if (this.shelterPos != null) {
                mob.getNavigation().moveTo(shelterPos.getX(), shelterPos.getY(), shelterPos.getZ(), 1.25D);
            }
            mob.startGoalAnimation("AVOID_RAIN");
            if (mob.getActiveAnimation().isEmpty()) {
                mob.startGoalAnimation("FLEE_RAIN");
            }
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private BlockPos findShelter() {
            RandomSource rand = mob.getRandom();
            BlockPos mobPos = mob.blockPosition();
            for (int i = 0; i < 10; ++i) {
                BlockPos target = mobPos.offset(rand.nextInt(20) - 10, rand.nextInt(6) - 3, rand.nextInt(20) - 10);
                if (!mob.level().canSeeSky(target) && mob.getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN) >= 0.0F) {
                    return target;
                }
            }
            return null;
        }
    }

    public static class AvoidWaterGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos safePos;

        public AvoidWaterGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_WATER")) return false;
            if (!mob.isInWaterOrRain()) return false;
            this.safePos = this.findSafePos();
            return this.safePos != null;
        }

        @Override
        public void start() {
            if (this.safePos != null) {
                mob.getNavigation().moveTo(safePos.getX(), safePos.getY(), safePos.getZ(), 1.25D);
            }
            mob.startGoalAnimation("AVOID_WATER");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private BlockPos findSafePos() {
            RandomSource rand = mob.getRandom();
            BlockPos mobPos = mob.blockPosition();
            for (int i = 0; i < 10; ++i) {
                BlockPos target = mobPos.offset(rand.nextInt(10) - 5, rand.nextInt(4) - 2, rand.nextInt(10) - 5);
                if (!mob.level().getFluidState(target).isSource()) {
                    return target;
                }
            }
            return null;
        }
    }

    public static class SeekWaterGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos waterPos;

        public SeekWaterGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEEK_WATER")) return false;
            if (mob.isInWater()) return false;
            this.waterPos = this.findWater();
            return this.waterPos != null;
        }

        @Override
        public void start() {
            if (this.waterPos != null) {
                mob.getNavigation().moveTo(waterPos.getX(), waterPos.getY(), waterPos.getZ(), 1.25D);
            }
            mob.startGoalAnimation("SEEK_WATER");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private BlockPos findWater() {
            BlockPos mobPos = mob.blockPosition();
            for (BlockPos target : BlockPos.betweenClosed(mobPos.offset(-8, -3, -8), mobPos.offset(8, 3, 8))) {
                if (mob.level().getFluidState(target).isSource()) {
                    return target.immutable();
                }
            }
            return null;
        }
    }

    public static class AvoidFireGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos safePos;

        public AvoidFireGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("AVOID_FIRE") && !mob.hasGoalType("FLEE_FIRE")) return false;
            if (!mob.isOnFire() && !this.isNearFire()) return false;
            this.safePos = this.findSafePos();
            return this.safePos != null;
        }

        @Override
        public void start() {
            if (this.safePos != null) {
                mob.getNavigation().moveTo(safePos.getX(), safePos.getY(), safePos.getZ(), 1.25D);
            }
            mob.startGoalAnimation("AVOID_FIRE");
            if (mob.getActiveAnimation().isEmpty()) {
                mob.startGoalAnimation("FLEE_FIRE");
            }
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private boolean isNearFire() {
            BlockPos mobPos = mob.blockPosition();
            for (BlockPos target : BlockPos.betweenClosed(mobPos.offset(-2, -1, -2), mobPos.offset(2, 1, 2))) {
                var state = mob.level().getBlockState(target);
                if (state.is(net.minecraft.world.level.block.Blocks.FIRE) || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE) || state.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                    return true;
                }
            }
            return false;
        }

        private BlockPos findSafePos() {
            RandomSource rand = mob.getRandom();
            BlockPos mobPos = mob.blockPosition();
            for (int i = 0; i < 10; ++i) {
                BlockPos target = mobPos.offset(rand.nextInt(10) - 5, rand.nextInt(4) - 2, rand.nextInt(10) - 5);
                var state = mob.level().getBlockState(target);
                if (!state.is(net.minecraft.world.level.block.Blocks.FIRE) && !state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE) && !state.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                    return target;
                }
            }
            return null;
        }
    }

    public static class SeekFireGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos firePos;

        public SeekFireGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEEK_FIRE")) return false;
            if (mob.isOnFire() || mob.isInLava()) return false;
            this.firePos = this.findFire();
            return this.firePos != null;
        }

        @Override
        public void start() {
            if (this.firePos != null) {
                mob.getNavigation().moveTo(firePos.getX(), firePos.getY(), firePos.getZ(), 1.25D);
            }
            mob.startGoalAnimation("SEEK_FIRE");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private BlockPos findFire() {
            BlockPos mobPos = mob.blockPosition();
            for (BlockPos target : BlockPos.betweenClosed(mobPos.offset(-8, -3, -8), mobPos.offset(8, 3, 8))) {
                var state = mob.level().getBlockState(target);
                if (state.is(net.minecraft.world.level.block.Blocks.FIRE) || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE) || state.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                    return target.immutable();
                }
            }
            return null;
        }
    }

    public static class SeekPlayersGoal extends Goal {
        private final CustomMobEntity mob;
        private Player targetPlayer;

        public SeekPlayersGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEEK_PLAYERS")) return false;
            this.targetPlayer = mob.level().getNearestPlayer(mob, 16.0D);
            return this.targetPlayer != null && mob.distanceToSqr(this.targetPlayer) > 9.0D;
        }

        @Override
        public void start() {
            if (this.targetPlayer != null) {
                mob.getNavigation().moveTo(this.targetPlayer, 1.0D);
            }
            mob.startGoalAnimation("SEEK_PLAYERS");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }
    }

    public static class SeekItemsGoal extends Goal {
        private final CustomMobEntity mob;
        private net.minecraft.world.entity.item.ItemEntity targetItem;

        public SeekItemsGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEEK_ITEMS")) return false;
            List<net.minecraft.world.entity.item.ItemEntity> items = mob.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mob.getBoundingBox().inflate(8.0D));
            if (items.isEmpty()) return false;
            this.targetItem = items.get(0);
            return true;
        }

        @Override
        public void start() {
            if (this.targetItem != null) {
                mob.getNavigation().moveTo(this.targetItem, 1.0D);
            }
            mob.startGoalAnimation("SEEK_ITEMS");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        @Override
        public void tick() {
            if (this.targetItem == null || !this.targetItem.isAlive()) return;
            mob.getNavigation().moveTo(this.targetItem, 1.0D);
            if (mob.distanceToSqr(this.targetItem) < 2.0D) {
                ItemStack stack = this.targetItem.getItem();
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack.copy());
                this.targetItem.discard();
                mob.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
            }
        }
    }

    public static class SeekLightGoal extends Goal {
        private final CustomMobEntity mob;
        private BlockPos lightPos;

        public SeekLightGoal(CustomMobEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!mob.hasGoalType("SEEK_LIGHT")) return false;
            this.lightPos = this.findLight();
            return this.lightPos != null && mob.blockPosition().distSqr(this.lightPos) > 4.0D;
        }

        @Override
        public void start() {
            if (this.lightPos != null) {
                mob.getNavigation().moveTo(lightPos.getX(), lightPos.getY(), lightPos.getZ(), 1.0D);
            }
            mob.startGoalAnimation("SEEK_LIGHT");
        }

        @Override
        public void stop() {
            mob.stopGoalAnimation();
        }

        private BlockPos findLight() {
            BlockPos mobPos = mob.blockPosition();
            BlockPos best = null;
            int maxLight = mob.level().getMaxLocalRawBrightness(mobPos);
            for (BlockPos target : BlockPos.betweenClosed(mobPos.offset(-8, -2, -8), mobPos.offset(8, 2, 8))) {
                int light = mob.level().getMaxLocalRawBrightness(target);
                if (light > maxLight) {
                    maxLight = light;
                    best = target.immutable();
                }
            }
            return best;
        }
    }

    public static class CustomSwimmingGoal extends Goal {
        private final CustomMobEntity mob;
        private double x;
        private double y;
        private double z;
        private final double speed;

        public CustomSwimmingGoal(CustomMobEntity mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return false;
            
            boolean isAquatic = data.spawnRules.aquatic && mob.isInWater();
            boolean isLava = data.spawnRules.lava && mob.isInLava();
            
            if (!isAquatic && !isLava) return false;
            
            if (mob.getRandom().nextFloat() >= 0.05F) {
                return false;
            }
            
            net.minecraft.world.phys.Vec3 target = this.findVolumePos(isLava);
            if (target == null) {
                return false;
            } else {
                this.x = target.x;
                this.y = target.y;
                this.z = target.z;
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(this.x, this.y, this.z, this.speed);
        }

        private net.minecraft.world.phys.Vec3 findVolumePos(boolean isLava) {
            net.minecraft.util.RandomSource rand = mob.getRandom();
            BlockPos current = mob.blockPosition();
            for (int i = 0; i < 10; i++) {
                BlockPos p = current.offset(rand.nextInt(16) - 8, rand.nextInt(8) - 4, rand.nextInt(16) - 8);
                if (isLava) {
                    if (mob.level().getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) {
                        return net.minecraft.world.phys.Vec3.atBottomCenterOf(p);
                    }
                } else {
                    if (mob.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
                        return net.minecraft.world.phys.Vec3.atBottomCenterOf(p);
                    }
                }
            }
            return null;
        }
    }

    public int getAnimationLengthInTicks(String animName) {
        if (animName == null || animName.isEmpty()) return 0;
        try {
            String templateId = this.getTemplateId();
            if (templateId == null || templateId.isEmpty()) return 0;
            
            java.io.File configFolder = dev.architectury.platform.Platform.getConfigFolder().resolve("CustomMobs/Mobs/Unpacked").toFile();
            java.io.File unpackedFolder = new java.io.File(configFolder, templateId);
            if (!unpackedFolder.exists() || !unpackedFolder.isDirectory()) return 0;
            
            java.io.File animFile = findFileRecursively(unpackedFolder, ".animation.json");
            if (animFile != null && animFile.exists()) {
                String content = java.nio.file.Files.readString(animFile.toPath());
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                if (json.has("animations")) {
                    com.google.gson.JsonObject animations = json.getAsJsonObject("animations");
                    for (String key : animations.keySet()) {
                        if (key.equalsIgnoreCase(animName) || key.toLowerCase().endsWith("." + animName.toLowerCase())) {
                            com.google.gson.JsonObject animData = animations.getAsJsonObject(key);
                            if (animData != null && animData.has("animation_length")) {
                                double lengthSecs = animData.get("animation_length").getAsDouble();
                                int ticks = (int) Math.ceil(lengthSecs * 20.0);
                                return Math.max(1, ticks);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static java.io.File findFileRecursively(java.io.File dir, String suffix) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix.toLowerCase())) {
                return f;
            }
        }
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                java.io.File found = findFileRecursively(f, suffix);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static class CustomMeleeAttackGoal extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        private final CustomMobEntity mob;
        private final String goalType;
        private int myCooldown = 0;
        private int damageDelayTimer = -1;
        private LivingEntity attackTargetPending = null;
        private boolean hasAttacked = false;
        private boolean isAttacking = false;

        public CustomMeleeAttackGoal(CustomMobEntity mob, double speed, boolean followingTarget, String goalType) {
            super(mob, speed, followingTarget);
            this.mob = mob;
            this.goalType = goalType;
        }

        @Override
        public boolean canUse() {
            if (mob.isGoalOnCooldown(goalType)) {
                return false;
            }
            if (!mob.checkCombatSequence(goalType)) {
                return false;
            }
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.hasGoalType(goalType)) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canUse();
        }

        @Override
        public void start() {
            super.start();
            myCooldown = 0;
            damageDelayTimer = -1;
            attackTargetPending = null;
            hasAttacked = false;
            isAttacking = false;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (hasAttacked) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            damageDelayTimer = -1;
            attackTargetPending = null;
            if (hasAttacked) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
            isAttacking = false;
        }

        @Override
        public void tick() {
            super.tick();
            if (myCooldown > 0) {
                myCooldown--;
                if (myCooldown == 0 && isAttacking && damageDelayTimer <= 0) {
                    hasAttacked = true;
                    isAttacking = false;
                }
            }
            if (damageDelayTimer > 0) {
                damageDelayTimer--;
                if (damageDelayTimer == 0) {
                    damageDelayTimer = -1;
                    if (attackTargetPending != null && attackTargetPending.isAlive()) {
                        double distSqr = mob.distanceToSqr(attackTargetPending.getX(), attackTargetPending.getY(), attackTargetPending.getZ());
                        double reach = this.getAttackReachSqr(attackTargetPending);
                        if (distSqr <= reach) {
                            this.mob.doHurtTarget(attackTargetPending);
                        }
                    }
                    attackTargetPending = null;
                    if (myCooldown == 0 && isAttacking) {
                        hasAttacked = true;
                        isAttacking = false;
                    }
                }
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceVal) {
            double reach = this.getAttackReachSqr(target);
            double reachDist = Math.sqrt(reach);
            double triggerReach = reachDist + 1.5D;
            double triggerReachSqr = triggerReach * triggerReach;
            if (distanceVal <= triggerReachSqr && myCooldown <= 0 && damageDelayTimer <= 0) {
                int delay = 20;
                int dmgDelay = 0;
                MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
                if (aiGoal != null) {
                    try {
                        delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "20"));
                    } catch (Exception ignored) {}
                    try {
                        dmgDelay = Integer.parseInt(aiGoal.params.getOrDefault("damageDelay", "0"));
                    } catch (Exception ignored) {}
                    
                    if (!aiGoal.animation.isEmpty()) {
                        int animLength = mob.getAnimationLengthInTicks(aiGoal.animation);
                        if (animLength > 0) {
                            delay = animLength;
                        }
                    }
                }
                myCooldown = delay;
                mob.startGoalCooldown(goalType, delay);
                isAttacking = true;
                if (delay <= 0) {
                    hasAttacked = true;
                }

                // Play attack sound if configured
                if (aiGoal != null && aiGoal.params.containsKey("sound") && !aiGoal.params.get("sound").isEmpty()) {
                    String soundId = aiGoal.params.get("sound");
                    try {
                        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(new net.minecraft.resources.ResourceLocation(soundId)),
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
                    } catch (Exception ignored) {}
                }

                this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (dmgDelay > 0) {
                    damageDelayTimer = dmgDelay;
                    attackTargetPending = target;
                } else {
                    if (distanceVal <= reach) {
                        this.mob.doHurtTarget(target);
                    }
                }
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data != null && data.stats.attackReach > 0) {
                double r = mob.getBbWidth() * 0.5D + attackTarget.getBbWidth() * 0.5D + data.stats.attackReach;
                return r * r;
            }
            return super.getAttackReachSqr(attackTarget);
        }
    }

    public static class CustomMeleeAOEAttackGoal extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        private final CustomMobEntity mob;
        private final String goalType;
        private int myCooldown = 0;
        private int damageDelayTimer = -1;
        private boolean hasAttacked = false;
        private boolean isAttacking = false;

        public CustomMeleeAOEAttackGoal(CustomMobEntity mob, double speed, boolean followingTarget, String goalType) {
            super(mob, speed, followingTarget);
            this.mob = mob;
            this.goalType = goalType;
        }

        @Override
        public boolean canUse() {
            if (mob.isGoalOnCooldown(goalType)) {
                return false;
            }
            if (!mob.checkCombatSequence(goalType)) {
                return false;
            }
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.hasGoalType(goalType)) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canUse();
        }

        @Override
        public void start() {
            super.start();
            myCooldown = 0;
            damageDelayTimer = -1;
            hasAttacked = false;
            isAttacking = false;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (hasAttacked) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            damageDelayTimer = -1;
            if (hasAttacked) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
            isAttacking = false;
        }

        @Override
        public void tick() {
            super.tick();
            if (myCooldown > 0) {
                myCooldown--;
                if (myCooldown == 0 && isAttacking && damageDelayTimer <= 0) {
                    hasAttacked = true;
                    isAttacking = false;
                }
            }
            if (damageDelayTimer > 0) {
                damageDelayTimer--;
                if (damageDelayTimer == 0) {
                    damageDelayTimer = -1;
                    performAOESweep();
                    if (myCooldown == 0 && isAttacking) {
                        hasAttacked = true;
                        isAttacking = false;
                    }
                }
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceVal) {
            double reach = this.getAttackReachSqr(target);
            double reachDist = Math.sqrt(reach);
            double triggerReach = reachDist + 1.5D;
            double triggerReachSqr = triggerReach * triggerReach;
            if (distanceVal <= triggerReachSqr && myCooldown <= 0 && damageDelayTimer <= 0) {
                int delay = 20;
                int dmgDelay = 0;
                MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
                if (aiGoal != null) {
                    try {
                        delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "20"));
                    } catch (Exception ignored) {}
                    try {
                        dmgDelay = Integer.parseInt(aiGoal.params.getOrDefault("damageDelay", "0"));
                    } catch (Exception ignored) {}
                    
                    if (!aiGoal.animation.isEmpty()) {
                        int animLength = mob.getAnimationLengthInTicks(aiGoal.animation);
                        if (animLength > 0) {
                            delay = animLength;
                        }
                    }
                }
                myCooldown = delay;
                mob.startGoalCooldown(goalType, delay);
                isAttacking = true;
                if (delay <= 0) {
                    hasAttacked = true;
                }

                // Play attack sound if configured
                if (aiGoal != null && aiGoal.params.containsKey("sound") && !aiGoal.params.get("sound").isEmpty()) {
                    String soundId = aiGoal.params.get("sound");
                    try {
                        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(new net.minecraft.resources.ResourceLocation(soundId)),
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
                    } catch (Exception ignored) {}
                }

                this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (dmgDelay > 0) {
                    damageDelayTimer = dmgDelay;
                } else {
                    performAOESweep();
                }
            }
        }

        private void performAOESweep() {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data == null) return;

            double reachVal = 4.0D;
            double widthVal = 120.0D;

            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null) {
                try {
                    reachVal = Double.parseDouble(aiGoal.params.getOrDefault("reach", "4.0"));
                } catch (Exception ignored) {}
                try {
                    widthVal = Double.parseDouble(aiGoal.params.getOrDefault("width", "120.0"));
                } catch (Exception ignored) {}
            }

            final double reach = reachVal;
            final double width = widthVal;

            if (mob.level() instanceof ServerLevel serverLevel) {
                List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach), entity -> {
                    return entity != mob && entity.isAlive() && (entity instanceof Player || (mob.getTarget() != null && entity == mob.getTarget()));
                });

                net.minecraft.world.phys.Vec3 lookVec = mob.getLookAngle();
                double lookYaw = Math.atan2(lookVec.z, lookVec.x);
                double radLimit = Math.toRadians(width / 2.0D);

                for (LivingEntity target : targets) {
                    double tx = target.getX() - mob.getX();
                    double tz = target.getZ() - mob.getZ();
                    double targetYaw = Math.atan2(tz, tx);

                    double angleDiff = Math.abs(lookYaw - targetYaw);
                    if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;

                    double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                    double maxDist = mob.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + reach;

                    if (distSqr <= maxDist * maxDist && angleDiff <= radLimit) {
                        this.mob.doHurtTarget(target);
                    }
                }
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null) {
                try {
                    double r = Double.parseDouble(aiGoal.params.getOrDefault("reach", "4.0"));
                    double maxDist = mob.getBbWidth() * 0.5D + attackTarget.getBbWidth() * 0.5D + r;
                    return maxDist * maxDist;
                } catch (Exception ignored) {}
            }
            return super.getAttackReachSqr(attackTarget);
        }
    }

    public static class CustomKnockbackAttackGoal extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        private final CustomMobEntity mob;
        private final String goalType;
        private int myCooldown = 0;
        private int damageDelayTimer = -1;
        private LivingEntity attackTargetPending = null;
        private boolean hasAttacked = false;
        private boolean isAttacking = false;

        public CustomKnockbackAttackGoal(CustomMobEntity mob, double speed, boolean followingTarget, String goalType) {
            super(mob, speed, followingTarget);
            this.mob = mob;
            this.goalType = goalType;
        }

        @Override
        public boolean canUse() {
            if (mob.isGoalOnCooldown(goalType)) {
                return false;
            }
            if (!mob.checkCombatSequence(goalType)) {
                return false;
            }
            if (mob.hasGoalType("STALK") && mob.stalkGoalInstance != null) {
                if (!mob.stalkGoalInstance.isStalkingComplete()) {
                    return false;
                }
            }
            if (!mob.hasGoalType(goalType)) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canUse();
        }

        @Override
        public void start() {
            super.start();
            myCooldown = 0;
            damageDelayTimer = -1;
            attackTargetPending = null;
            hasAttacked = false;
            isAttacking = false;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && !aiGoal.animation.isEmpty()) {
                mob.setActiveAnimation(aiGoal.animation);
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (hasAttacked) {
                return false;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                double reach = this.getAttackReachSqr(target);
                double reachDist = Math.sqrt(reach);
                double triggerReach = reachDist + 1.5D;
                double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (distSqr <= triggerReach * triggerReach) {
                    return true;
                }
            }
            return super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            damageDelayTimer = -1;
            attackTargetPending = null;
            if (hasAttacked) {
                mob.advanceCombatSequence();
            }
            hasAttacked = false;
            isAttacking = false;
        }

        @Override
        public void tick() {
            super.tick();
            if (myCooldown > 0) {
                myCooldown--;
                if (myCooldown == 0 && isAttacking && damageDelayTimer <= 0) {
                    hasAttacked = true;
                    isAttacking = false;
                }
            }
            if (damageDelayTimer > 0) {
                damageDelayTimer--;
                if (damageDelayTimer == 0) {
                    damageDelayTimer = -1;
                    if (attackTargetPending != null && attackTargetPending.isAlive()) {
                        double distSqr = mob.distanceToSqr(attackTargetPending.getX(), attackTargetPending.getY(), attackTargetPending.getZ());
                        double reach = this.getAttackReachSqr(attackTargetPending);
                        if (distSqr <= reach) {
                            performKnockbackAttack(attackTargetPending);
                        }
                    }
                    attackTargetPending = null;
                    if (myCooldown == 0 && isAttacking) {
                        hasAttacked = true;
                        isAttacking = false;
                    }
                }
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceVal) {
            double reach = this.getAttackReachSqr(target);
            double reachDist = Math.sqrt(reach);
            double triggerReach = reachDist + 1.5D;
            double triggerReachSqr = triggerReach * triggerReach;
            if (distanceVal <= triggerReachSqr && myCooldown <= 0 && damageDelayTimer <= 0) {
                int delay = 20;
                int dmgDelay = 0;
                MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
                if (aiGoal != null) {
                    try {
                        delay = Integer.parseInt(aiGoal.params.getOrDefault("delay", "20"));
                    } catch (Exception ignored) {}
                    try {
                        dmgDelay = Integer.parseInt(aiGoal.params.getOrDefault("damageDelay", "0"));
                    } catch (Exception ignored) {}
                    
                    if (!aiGoal.animation.isEmpty()) {
                        int animLength = mob.getAnimationLengthInTicks(aiGoal.animation);
                        if (animLength > 0) {
                            delay = animLength;
                        }
                    }
                }
                myCooldown = delay;
                mob.startGoalCooldown(goalType, delay);
                isAttacking = true;
                if (delay <= 0) {
                    hasAttacked = true;
                }

                // Play attack sound if configured
                if (aiGoal != null && aiGoal.params.containsKey("sound") && !aiGoal.params.get("sound").isEmpty()) {
                    String soundId = aiGoal.params.get("sound");
                    try {
                        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(new net.minecraft.resources.ResourceLocation(soundId)),
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
                    } catch (Exception ignored) {}
                }

                this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (dmgDelay > 0) {
                    damageDelayTimer = dmgDelay;
                    attackTargetPending = target;
                } else {
                    if (distanceVal <= reach) {
                        performKnockbackAttack(target);
                    }
                }
            }
        }

        private void performKnockbackAttack(LivingEntity target) {
            this.mob.doHurtTarget(target);

            double distanceVal = 8.0D;
            MobData.AIGoalData aiGoal = mob.getGoalData(goalType);
            if (aiGoal != null) {
                try {
                    distanceVal = Double.parseDouble(aiGoal.params.getOrDefault("distance", "8.0"));
                } catch (Exception ignored) {}
            }
            double distance = Math.min(32.0D, distanceVal);

            double yVelocity = 0.15D + (distance * 0.015D);
            double estTicks = Math.max(5.0D, (2.0D * yVelocity) / 0.08D);
            double dragSum = (1.0D - Math.pow(0.91D, estTicks)) / 0.09D;
            double horizontalVelocity = distance / dragSum;

            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                dx /= dist;
                dz /= dist;
            } else {
                dx = Math.cos(Math.toRadians(mob.getYRot()));
                dz = Math.sin(Math.toRadians(mob.getYRot()));
            }

            double vx = dx * horizontalVelocity;
            double vz = dz * horizontalVelocity;
            double vy = yVelocity;

            target.setDeltaMovement(vx, vy, vz);
            target.hurtMarked = true;
            if (target instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(target));
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            MobData data = MobRegistry.loadedMobs.get(mob.getTemplateId());
            if (data != null && data.stats.attackReach > 0) {
                double r = mob.getBbWidth() * 0.5D + attackTarget.getBbWidth() * 0.5D + data.stats.attackReach;
                return r * r;
            }
            return super.getAttackReachSqr(attackTarget);
        }
    }

    private static class CombatDelayGoal extends Goal {
        private final CustomMobEntity mob;
        private int delayTicks = 0;
        private int timer = 0;

        public CombatDelayGoal(CustomMobEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.combatSequence.isEmpty()) return false;
            if (!mob.checkCombatSequence("DELAY")) {
                return false;
            }
            return mob.hasGoalType("DELAY") && mob.getTarget() != null;
        }

        @Override
        public void start() {
            this.timer = 0;
            this.delayTicks = 40; // Default delay
            MobData.AIGoalData aiGoal = mob.getGoalData("DELAY");
            if (aiGoal != null) {
                try {
                    this.delayTicks = Integer.parseInt(aiGoal.params.getOrDefault("delay", "40"));
                } catch (Exception ignored) {}
                if (!aiGoal.animation.isEmpty()) {
                    mob.setActiveAnimation(aiGoal.animation);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() != null && this.timer < this.delayTicks;
        }

        @Override
        public void stop() {
            MobData.AIGoalData aiGoal = mob.getGoalData("DELAY");
            if (aiGoal != null && mob.getActiveAnimation().equals(aiGoal.animation)) {
                mob.setActiveAnimation("");
            }
            if (this.timer >= this.delayTicks && !mob.combatSequence.isEmpty()) {
                mob.advanceCombatSequence();
            }
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target != null) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.getNavigation().stop();
            }
            this.timer++;
        }
    }
}
