package ddraig.net.custommobs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public interface IGeckoLibRenderer {
    void render(CustomMobEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight);
    ResourceLocation getTextureLocation(CustomMobEntity entity);
}
