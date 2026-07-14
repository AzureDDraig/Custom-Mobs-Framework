package ddraig.net.custommobs.forge.client.renderer;

import com.google.gson.JsonObject;
import ddraig.net.custommobs.CustomMobs;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.entity.CustomMobEntity;
import ddraig.net.custommobs.client.renderer.JavaModelLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.model.GeoModel;

import java.io.File;

public class CustomMobGeoModel extends GeoModel<CustomMobEntity> {

    @Override
    public void setCustomAnimations(CustomMobEntity animatable, long instanceId, AnimationState<CustomMobEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        getBone("hitbox").ifPresent(bone -> bone.setHidden(true));
        getBone("Hitbox").ifPresent(bone -> bone.setHidden(true));
    }

    @Override
    public ResourceLocation getModelResource(CustomMobEntity animatable) {
        MobData data = MobRegistry.loadedMobs.get(animatable.getTemplateId());
        String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : animatable.getTemplateId();
        return new ResourceLocation("custom_mobs", "geo/" + modelId + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CustomMobEntity animatable) {
        MobData data = MobRegistry.loadedMobs.get(animatable.getTemplateId());
        String texPath = (data != null && data.texturePath != null) ? data.texturePath : "";
        String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : animatable.getTemplateId();
        return JavaModelLoader.getTexture(animatable.getTemplateId(), modelId, texPath);
    }

    @Override
    public ResourceLocation getAnimationResource(CustomMobEntity animatable) {
        MobData data = MobRegistry.loadedMobs.get(animatable.getTemplateId());
        String animPath = (data != null && data.animationPath != null && !data.animationPath.isEmpty()) ? data.animationPath : animatable.getTemplateId();
        if (!animPath.endsWith(".animation.json")) {
            animPath = animPath + ".animation.json";
        }
        return new ResourceLocation("custom_mobs", "animations/" + animPath);
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel model = software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().get(location);
        if (model == null) {
            try {
                String path = location.getPath();
                if (path.startsWith("geo/") && path.endsWith(".geo.json")) {
                    String modelId = path.substring(4, path.length() - 9);
                    File file = findFileInUnpacked(modelId, ".geo.json");
                    if (file != null && file.exists()) {
                        String content = java.nio.file.Files.readString(file.toPath());
                        JsonObject json = GsonHelper.fromJson(software.bernie.geckolib.util.JsonUtil.GEO_GSON, content, JsonObject.class);
                        Model rawModel = software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json, Model.class);
                        BakedGeoModel bakedModel = BakedModelFactory.getForNamespace("custom_mobs").constructGeoModel(GeometryTree.fromModel(rawModel));
                        if (bakedModel != null) {
                            software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().put(location, bakedModel);
                        }
                    }
                }
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to dynamically load/bake GeckoLib model: " + location, e);
            }
        }
        return super.getBakedModel(location);
    }

    @Override
    public software.bernie.geckolib.core.animation.Animation getAnimation(CustomMobEntity animatable, String name) {
        ResourceLocation location = getAnimationResource(animatable);
        BakedAnimations bakedAnimations = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(location);
        if (bakedAnimations == null) {
            try {
                String path = location.getPath();
                if (path.startsWith("animations/") && path.endsWith(".animation.json")) {
                    String animFilename = path.substring(11);
                    MobData data = MobRegistry.loadedMobs.get(animatable.getTemplateId());
                    String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : animatable.getTemplateId();
                    
                    File configFolder = MobRegistry.getMobsFolder();
                    File unpackedFolder = new File(configFolder, modelId);
                    File file = null;
                    if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
                        file = findFileRecursivelyByName(unpackedFolder, animFilename);
                        if (file == null) {
                            file = findFileRecursively(unpackedFolder, ".animation.json");
                        }
                    }
                    if (file == null) {
                        String modelIdFromPath = path.substring(11, path.length() - 15);
                        file = findFileInUnpacked(modelIdFromPath, ".animation.json");
                    }
                    
                    if (file != null && file.exists()) {
                        String content = java.nio.file.Files.readString(file.toPath());
                        JsonObject json = GsonHelper.fromJson(software.bernie.geckolib.util.JsonUtil.GEO_GSON, content, JsonObject.class);
                        bakedAnimations = software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json.getAsJsonObject("animations"), BakedAnimations.class);
                        if (bakedAnimations != null) {
                            software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().put(location, bakedAnimations);
                        }
                    }
                }
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to dynamically load/bake GeckoLib animations: " + location, e);
            }
        }
        return super.getAnimation(animatable, name);
    }

    private File findFileInUnpacked(String id, String suffix) {
        File configFolder = MobRegistry.getMobsFolder();
        File unpackedFolder = new File(configFolder, id);
        if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
            return findFileRecursively(unpackedFolder, suffix);
        }
        return null;
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

    private File findFileRecursivelyByName(File dir, String filename) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase(filename)) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findFileRecursivelyByName(f, filename);
                if (found != null) return found;
            }
        }
        return null;
    }
}
