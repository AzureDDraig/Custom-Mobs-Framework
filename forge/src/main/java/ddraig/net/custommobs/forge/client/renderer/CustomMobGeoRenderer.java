package ddraig.net.custommobs.forge.client.renderer;

import ddraig.net.custommobs.client.renderer.IGeckoLibRenderer;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CustomMobGeoRenderer extends GeoEntityRenderer<CustomMobEntity> implements IGeckoLibRenderer {
    public CustomMobGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CustomMobGeoModel());
    }

    @Override
    public software.bernie.geckolib.core.object.Color getRenderColor(CustomMobEntity animatable, float partialTick, int packedLight) {
        if (animatable.isSilhouette()) {
            return software.bernie.geckolib.core.object.Color.ofRGBA(0, 0, 0, 255);
        }
        return super.getRenderColor(animatable, partialTick, packedLight);
    }
}
