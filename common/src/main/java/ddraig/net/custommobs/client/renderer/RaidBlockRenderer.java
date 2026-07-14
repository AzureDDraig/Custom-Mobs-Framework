package ddraig.net.custommobs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ddraig.net.custommobs.block.entity.RaidBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;

public class RaidBlockRenderer implements BlockEntityRenderer<RaidBlockEntity> {
    private Entity cachedEntity;
    private String cachedTemplateId = "";

    public RaidBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RaidBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        String templateId = blockEntity.getActiveDisplayTemplate();
        if (templateId == null || templateId.isEmpty()) {
            return;
        }

        if (cachedEntity == null || !cachedTemplateId.equals(templateId)) {
            var level = blockEntity.getLevel();
            if (level != null) {
                if (templateId.contains(":")) {
                    net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(templateId);
                    if (loc != null) {
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(loc).ifPresent(type -> {
                            cachedEntity = type.create(level);
                        });
                    }
                } else {
                    cachedEntity = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(level);
                    if (cachedEntity instanceof ddraig.net.custommobs.entity.CustomMobEntity customMob) {
                        customMob.setTemplateId(templateId);
                    }
                }
                cachedTemplateId = templateId;
            }
        }

        if (cachedEntity != null) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.15D, 0.5D);
            float scale = 0.45F;
            float maxDim = Math.max(cachedEntity.getBbWidth(), cachedEntity.getBbHeight());
            if (maxDim > 1.0F) {
                scale /= maxDim;
            }
            poseStack.translate(0.0D, 0.2D, 0.0D);
            float spinAngle = (float) blockEntity.getSpinAngle(partialTick);
            poseStack.mulPose(Axis.YP.rotationDegrees(spinAngle * 10.0F));
            poseStack.translate(0.0D, -0.15D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));
            poseStack.scale(scale, scale, scale);
            
            Minecraft.getInstance().getEntityRenderDispatcher().render(cachedEntity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, poseStack, bufferSource, combinedLight);
            
            poseStack.popPose();
        }
    }
}
