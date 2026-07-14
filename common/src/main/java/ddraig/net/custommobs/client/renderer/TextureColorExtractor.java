package ddraig.net.custommobs.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import ddraig.net.custommobs.data.ProjectileData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TextureColorExtractor {
    private static final Map<ResourceLocation, Integer> colorCache = new HashMap<>();

    public static int getDominantColor(ResourceLocation textureLocation) {
        if (textureLocation == null) {
            return 0xFFFFFF;
        }
        if (colorCache.containsKey(textureLocation)) {
            return colorCache.get(textureLocation);
        }

        int color = 0xFFFFFF;
        try {
            var manager = Minecraft.getInstance().getResourceManager();
            var resourceOpt = manager.getResource(textureLocation);
            if (resourceOpt.isPresent()) {
                try (InputStream is = resourceOpt.get().open()) {
                    NativeImage nativeImage = NativeImage.read(is);
                    long rSum = 0, gSum = 0, bSum = 0, count = 0;
                    int width = nativeImage.getWidth();
                    int height = nativeImage.getHeight();
                    
                    // Sample every 2nd pixel to be fast and performant
                    for (int x = 0; x < width; x += 2) {
                        for (int y = 0; y < height; y += 2) {
                            int abgr = nativeImage.getPixelRGBA(x, y);
                            int a = (abgr >> 24) & 0xFF;
                            if (a > 10) { // Skip transparent/semi-transparent pixels
                                int r = abgr & 0xFF;
                                int g = (abgr >> 8) & 0xFF;
                                int b = (abgr >> 16) & 0xFF;
                                rSum += r;
                                gSum += g;
                                bSum += b;
                                count++;
                            }
                        }
                    }
                    nativeImage.close();

                    if (count > 0) {
                        int rAvg = (int) (rSum / count);
                        int gAvg = (int) (gSum / count);
                        int bAvg = (int) (bSum / count);
                        color = (rAvg << 16) | (gAvg << 8) | bAvg;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback if resource could not be read or does not exist
        }

        colorCache.put(textureLocation, color);
        return color;
    }

    public static ResourceLocation getProjectileTexture(ProjectileData data) {
        if (data == null) {
            return new ResourceLocation("minecraft", "textures/entity/projectiles/arrow.png");
        }

        if ("vanilla".equalsIgnoreCase(data.modelType)) {
            String itemPath = data.modelId;
            if (itemPath != null && !itemPath.isEmpty()) {
                if (itemPath.contains(":")) {
                    String[] parts = itemPath.split(":");
                    return new ResourceLocation(parts[0], "textures/item/" + parts[1] + ".png");
                } else {
                    return new ResourceLocation("minecraft", "textures/item/" + itemPath + ".png");
                }
            }
        } else if ("java".equalsIgnoreCase(data.modelType) || "geckolib".equalsIgnoreCase(data.modelType)) {
            String texPath = data.texturePath;
            if (texPath != null && !texPath.isEmpty()) {
                if (texPath.startsWith("textures/")) {
                    return new ResourceLocation("custom_mobs", texPath);
                } else if (texPath.contains(":")) {
                    return new ResourceLocation(texPath);
                } else {
                    return new ResourceLocation("custom_mobs", "textures/entity/projectile/" + texPath + ".png");
                }
            }
        }
        return new ResourceLocation("minecraft", "textures/entity/projectiles/arrow.png");
    }
}
