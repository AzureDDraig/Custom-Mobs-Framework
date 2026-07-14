package ddraig.net.custommobs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.animation.AnimationDefinition;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class CustomMobRenderer extends MobRenderer<CustomMobEntity, CowModel<CustomMobEntity>> {
    public static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("minecraft", "textures/entity/cow/cow.png");
    private final Map<CustomMobEntity, Map<String, Entity>> dummyEntities = new WeakHashMap<>();
    private final IGeckoLibRenderer geckolibRenderer;
    public static boolean showHitboxDebug = false;

    public CustomMobRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.COW)), 0.5f);
        this.geckolibRenderer = GeckoLibRendererBridge.createRenderer(context);
    }

    private Entity getOrCreateDummy(CustomMobEntity mob, String modelId) {
        Map<String, Entity> mobDummies = dummyEntities.computeIfAbsent(mob, m -> new HashMap<>());
        return mobDummies.computeIfAbsent(modelId, id -> {
            try {
                ResourceLocation loc = new ResourceLocation(id);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(loc);
                if (type != null) {
                    Entity dummy = type.create(mob.level());
                    if (dummy != null) {
                        if (dummy instanceof net.minecraft.world.entity.Mob m) {
                            m.setNoAi(true);
                        }
                        return dummy;
                    }
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String templateId = entity.getTemplateId();
        MobData data = MobRegistry.loadedMobs.get(templateId);

        if (data != null) {
            if (data.id.toLowerCase().contains("portal") || data.name.toLowerCase().contains("portal") || templateId.toLowerCase().contains("portal")) {
                poseStack.pushPose();
                
                // Billboard towards camera
                net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                poseStack.mulPose(dispatcher.cameraOrientation());
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
                
                // Scale based on size
                float scaleVal = data.scale;
                poseStack.scale(scaleVal, scaleVal, scaleVal);
                
                // Get dominant color from summoner or boss texture
                int color = 0xFFFFFF; // fallback
                int summonerId = entity.getSummonerId();
                if (summonerId != -1) {
                    Entity summoner = entity.level().getEntity(summonerId);
                    if (summoner instanceof CustomMobEntity boss) {
                        ResourceLocation bossTex = getTextureLocation(boss);
                        color = TextureColorExtractor.getDominantColor(bossTex);
                    }
                } else {
                    ResourceLocation portalTex = getTextureLocation(entity);
                    color = TextureColorExtractor.getDominantColor(portalTex);
                }
                
                float r = ((color >> 16) & 0xFF) / 255.0F;
                float g = ((color >> 8) & 0xFF) / 255.0F;
                float b = (color & 0xFF) / 255.0F;
                
                ResourceLocation portalTex = new ResourceLocation("minecraft", "textures/entity/end_portal.png");
                
                // Render billboarded oval
                com.mojang.blaze3d.vertex.VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(portalTex));
                
                int segments = 24;
                float radiusX = data.hitboxWidth * 0.7f;
                float radiusY = data.hitboxHeight * 0.8f;
                
                float centerU = 0.5f;
                float centerV = 0.5f;
                
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (i * 2.0 * Math.PI / segments);
                    float angleNext = (float) ((i + 1) * 2.0 * Math.PI / segments);
                    
                    float x1 = (float) (Math.cos(angle) * radiusX);
                    float y1 = (float) (Math.sin(angle) * radiusY) + (radiusY / 2.0f);
                    float u1 = (float) (Math.cos(angle) * 0.5f + 0.5f);
                    float v1 = (float) (Math.sin(angle) * 0.5f + 0.5f);
                    
                    float x2 = (float) (Math.cos(angleNext) * radiusX);
                    float y2 = (float) (Math.sin(angleNext) * radiusY) + (radiusY / 2.0f);
                    float u2 = (float) (Math.cos(angleNext) * 0.5f + 0.5f);
                    float v2 = (float) (Math.sin(angleNext) * 0.5f + 0.5f);
                    
                    consumer.vertex(poseStack.last().pose(), 0.0f, radiusY / 2.0f, 0.0f).color(r, g, b, 0.85f).uv(centerU, centerV).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(poseStack.last().normal(), 0.0f, 0.0f, -1.0f).endVertex();
                    consumer.vertex(poseStack.last().pose(), x1, y1, 0.0f).color(r, g, b, 0.85f).uv(u1, v1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(poseStack.last().normal(), 0.0f, 0.0f, -1.0f).endVertex();
                    consumer.vertex(poseStack.last().pose(), x2, y2, 0.0f).color(r, g, b, 0.85f).uv(u2, v2).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(poseStack.last().normal(), 0.0f, 0.0f, -1.0f).endVertex();
                }
                
                poseStack.popPose();
                return;
            }

            if (entity.isPreview && showHitboxDebug) {
                float scale = data.scale;
                float w = data.hitboxWidth * scale;
                float h = data.hitboxHeight * scale;
                com.mojang.blaze3d.vertex.VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.lines());
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(
                    poseStack, consumer, 
                    -w / 2.0, 0.0, -w / 2.0, 
                    w / 2.0, h, w / 2.0, 
                    1.0f, 1.0f, 1.0f, 1.0f
                );
            }

            float scale = data.scale;
            if (entity.isElite()) {
                scale *= 1.5f;
            }

            if (data.modelType.equalsIgnoreCase("geckolib") && this.geckolibRenderer != null) {
                String modelId = (data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : templateId;
                if (modelFileExists(modelId)) {
                    int initialSize = getPoseStackSize(poseStack);
                    poseStack.pushPose();
                    poseStack.scale(scale, scale, scale);
                    try {
                        this.geckolibRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
                    } catch (Exception e) {
                        ddraig.net.custommobs.CustomMobs.LOGGER.error("Error rendering Geckolib model: " + modelId, e);
                    } finally {
                        cleanPoseStack(poseStack, initialSize);
                    }
                    return;
                }
            } else if (data.modelType.equalsIgnoreCase("java")) {
                HierarchicalModel<CustomMobEntity> modelInstance = JavaModelLoader.getModel(data.modelId);
                if (modelInstance != null) {
                    ResourceLocation tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
                    
                    poseStack.pushPose();
                    float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
                    float f1 = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
                    float f2 = f1 - f;
                    float f6 = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - f));
                    poseStack.scale(-scale, -scale, scale);
                    poseStack.translate(0.0D, -1.501D, 0.0D);

                    modelInstance.root().getAllParts().forEach(ModelPart::resetPose);

                    Map<String, AnimationDefinition> anims = JavaModelLoader.getAnimations(data.modelId);
                    boolean isMoving = entity.getDeltaMovement().horizontalDistanceSqr() > 1E-4;
                    String activeAnimState = isMoving ? "walk" : "idle";
                    String animName = data.animations.getOrDefault(activeAnimState, isMoving ? "walk" : "idle");
                    
                    AnimationDefinition animDef = anims.get(animName.toLowerCase());
                    if (animDef != null) {
                        float currentTick = entity.tickCount + partialTicks;
                        float elapsedTicks = currentTick % (animDef.lengthInSeconds() * 20.0f);
                        long elapsedMillis = (long) (elapsedTicks * 50.0F);
                        net.minecraft.client.animation.KeyframeAnimations.animate(modelInstance, animDef, elapsedMillis, 1.0F, new org.joml.Vector3f());
                    }

                    com.mojang.blaze3d.vertex.VertexConsumer vc = buffer.getBuffer(modelInstance.renderType(tex));
                    modelInstance.renderToBuffer(poseStack, vc, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

                    poseStack.popPose();
                    return;
                }
            } else if (data.modelType.equalsIgnoreCase("vanilla")) {
                Entity dummy = getOrCreateDummy(entity, data.modelId);
                if (dummy != null) {
                    dummy.setPos(entity.getX(), entity.getY(), entity.getZ());
                    dummy.setXRot(entity.getXRot());
                    dummy.setYRot(entity.getYRot());
                    dummy.xRotO = entity.xRotO;
                    dummy.yRotO = entity.yRotO;
                    dummy.tickCount = entity.tickCount;

                    if (dummy instanceof LivingEntity livingDummy) {
                        livingDummy.yBodyRot = entity.yBodyRot;
                        livingDummy.yBodyRotO = entity.yBodyRotO;
                        livingDummy.yHeadRot = entity.yHeadRot;
                        livingDummy.yHeadRotO = entity.yHeadRotO;

                        if (livingDummy.walkAnimation instanceof ddraig.net.custommobs.mixin.WalkAnimationStateAccessor dummyWalk &&
                            entity.walkAnimation instanceof ddraig.net.custommobs.mixin.WalkAnimationStateAccessor entityWalk) {
                            dummyWalk.setSpeed(entityWalk.getSpeed());
                            dummyWalk.setSpeedOld(entityWalk.getSpeedOld());
                            dummyWalk.setPosition(entityWalk.getPosition());
                        }
                    }

                    poseStack.pushPose();
                    poseStack.scale(scale, scale, scale);

                    EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                    EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(dummy);
                    if (renderer != null) {
                        renderer.render(dummy, entityYaw, partialTicks, poseStack, buffer, packedLight);
                    }

                    poseStack.popPose();
                    return;
                }
            }
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static java.lang.reflect.Field poseStackDequeField = null;

    private static int getPoseStackSize(PoseStack poseStack) {
        try {
            if (poseStackDequeField == null) {
                for (java.lang.reflect.Field field : PoseStack.class.getDeclaredFields()) {
                    if (java.util.Deque.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        poseStackDequeField = field;
                        break;
                    }
                }
            }
            if (poseStackDequeField != null) {
                java.util.Deque<?> deque = (java.util.Deque<?>) poseStackDequeField.get(poseStack);
                if (deque != null) {
                    return deque.size();
                }
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private static void cleanPoseStack(PoseStack poseStack, int expectedSize) {
        try {
            if (poseStackDequeField != null) {
                java.util.Deque<?> deque = (java.util.Deque<?>) poseStackDequeField.get(poseStack);
                if (deque != null) {
                    while (deque.size() > expectedSize) {
                        poseStack.popPose();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean modelFileExists(String modelId) {
        File configFolder = new File(Minecraft.getInstance().gameDirectory, "config/CustomMobs/Mobs/Unpacked");
        File unpackedFolder = new File(configFolder, modelId);
        if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
            return findFileRecursively(unpackedFolder, ".geo.json") != null;
        }
        return false;
    }

    private File findFileRecursively(File dir, String suffix) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix.toLowerCase())) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findFileRecursively(f, suffix);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public ResourceLocation getTextureLocation(CustomMobEntity entity) {
        String templateId = entity.getTemplateId();
        MobData data = MobRegistry.loadedMobs.get(templateId);

        if (data != null) {
            ResourceLocation tex = null;
            if (data.modelType.equalsIgnoreCase("vanilla")) {
                Entity dummy = getOrCreateDummy(entity, data.modelId);
                if (dummy != null) {
                    if (data.texturePath != null && !data.texturePath.isEmpty()) {
                        tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
                    } else {
                        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(dummy);
                        if (renderer != null) {
                            tex = renderer.getTextureLocation(dummy);
                        }
                    }
                }
            } else if (data.modelType.equalsIgnoreCase("java")) {
                tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
            } else if (data.modelType.equalsIgnoreCase("geckolib") && this.geckolibRenderer != null) {
                try {
                    tex = this.geckolibRenderer.getTextureLocation(entity);
                } catch (Exception ignored) {}
            }
            if (tex != null) {
                return tex;
            }
        }
        return DEFAULT_TEXTURE;
    }

    @Override
    protected boolean shouldShowName(CustomMobEntity entity) {
        MobData data = MobRegistry.loadedMobs.get(entity.getTemplateId());
        if (data != null && data.billboardName) {
            return true;
        }
        return super.shouldShowName(entity);
    }
}
