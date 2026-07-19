package ddraig.net.custommobs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import ddraig.net.custommobs.data.ProjectileData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.entity.CustomProjectileEntity;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class CustomProjectileRenderer extends EntityRenderer<CustomProjectileEntity> {
    private final ThrownItemRenderer<CustomProjectileEntity> itemRenderer;
    private final EntityRendererProvider.Context context;
    private CustomMobRenderer mobRenderer;
    private CustomMobEntity dummyMob;

    public CustomProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.context = context;
        this.itemRenderer = new ThrownItemRenderer<>(context);
    }

    private void initMobRenderer() {
        if (this.mobRenderer == null) {
            this.mobRenderer = new CustomMobRenderer(this.context);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CustomProjectileEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(CustomProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ProjectileData data = MobRegistry.loadedProjectiles.get(entity.getProjectileId());
        
        if (entity.isPreview && CustomMobRenderer.showHitboxDebug && data != null) {
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

        if (data == null || data.modelType == null || data.modelType.equalsIgnoreCase("vanilla")) {
            this.itemRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        // Custom Geckolib or Java rendering via a dummy CustomMobEntity
        if (this.dummyMob == null || this.dummyMob.level() != entity.level()) {
            this.dummyMob = new CustomMobEntity(ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get(), entity.level());
        }

        // Sync position and orientation
        this.dummyMob.setPos(entity.getX(), entity.getY(), entity.getZ());
        this.dummyMob.setXRot(entity.getXRot());
        this.dummyMob.setYRot(entity.getYRot());
        this.dummyMob.xRotO = entity.xRotO;
        this.dummyMob.yRotO = entity.yRotO;
        this.dummyMob.yHeadRot = entity.getYRot();
        this.dummyMob.yHeadRotO = entity.yRotO;
        this.dummyMob.yBodyRot = entity.getYRot();
        this.dummyMob.yBodyRotO = entity.yRotO;
        this.dummyMob.tickCount = entity.tickCount;

        // Sync velocity
        this.dummyMob.setDeltaMovement(entity.getDeltaMovement());

        // Set model details using a fake template mapping on the fly
        String fakeTemplateId = "__proj_" + data.id;
        if (!MobRegistry.loadedMobs.containsKey(fakeTemplateId)) {
            ddraig.net.custommobs.data.MobData fakeMob = new ddraig.net.custommobs.data.MobData();
            fakeMob.id = fakeTemplateId;
            fakeMob.name = data.name;
            fakeMob.modelType = data.modelType;
            fakeMob.modelId = data.modelId;
            fakeMob.texturePath = data.texturePath;
            fakeMob.scale = data.scale;
            fakeMob.hitboxWidth = data.hitboxWidth;
            fakeMob.hitboxHeight = data.hitboxHeight;
            fakeMob.billboardName = false;
            MobRegistry.loadedMobs.put(fakeTemplateId, fakeMob);
        } else {
            ddraig.net.custommobs.data.MobData fakeMob = MobRegistry.loadedMobs.get(fakeTemplateId);
            fakeMob.modelType = data.modelType;
            fakeMob.modelId = data.modelId;
            fakeMob.texturePath = data.texturePath;
            fakeMob.scale = data.scale;
            fakeMob.hitboxWidth = data.hitboxWidth;
            fakeMob.hitboxHeight = data.hitboxHeight;
        }

        this.dummyMob.setTemplateId(fakeTemplateId);

        // Render using CustomMobRenderer
        initMobRenderer();

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        this.mobRenderer.render(this.dummyMob, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
