package ddraig.net.custommobs.client.renderer;

import ddraig.net.custommobs.CustomMobs;
import dev.architectury.platform.Platform;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaModelLoader {
    private static final ConcurrentHashMap<String, HierarchicalModel<CustomMobEntity>> modelCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, AnimationDefinition>> animationCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private static final Set<String> failedModels = ConcurrentHashMap.newKeySet();

    public static void clearCaches() {
        modelCache.clear();
        animationCache.clear();
        textureCache.clear();
        failedModels.clear();
        try {
            Class<?> cacheClass = Class.forName("software.bernie.geckolib.cache.GeckoLibCache");
            java.lang.reflect.Method getModelsMethod = cacheClass.getMethod("getBakedModels");
            java.util.Map<?, ?> models = (java.util.Map<?, ?>) getModelsMethod.invoke(null);
            if (models != null) {
                models.keySet().removeIf(key -> {
                    if (key instanceof ResourceLocation loc) {
                        return loc.getNamespace().equals("custom_mobs");
                    }
                    return key.toString().startsWith("custom_mobs:");
                });
            }
            java.lang.reflect.Method getAnimsMethod = cacheClass.getMethod("getBakedAnimations");
            java.util.Map<?, ?> anims = (java.util.Map<?, ?>) getAnimsMethod.invoke(null);
            if (anims != null) {
                anims.keySet().removeIf(key -> {
                    if (key instanceof ResourceLocation loc) {
                        return loc.getNamespace().equals("custom_mobs");
                    }
                    return key.toString().startsWith("custom_mobs:");
                });
            }
        } catch (Throwable ignored) {}
    }

    public static void clearCacheFor(String mobId, String modelId) {
        if (mobId != null) {
            textureCache.remove(mobId);
        }
        if (modelId != null) {
            modelCache.remove(modelId);
            animationCache.remove(modelId);
            failedModels.remove(modelId);
        }
        try {
            Class<?> cacheClass = Class.forName("software.bernie.geckolib.cache.GeckoLibCache");
            
            java.lang.reflect.Method getModelsMethod = cacheClass.getMethod("getBakedModels");
            java.util.Map<?, ?> models = (java.util.Map<?, ?>) getModelsMethod.invoke(null);
            if (models != null && modelId != null) {
                ResourceLocation modelLoc = new ResourceLocation("custom_mobs", "geo/" + modelId + ".geo.json");
                models.remove(modelLoc);
            }
            
            java.lang.reflect.Method getAnimsMethod = cacheClass.getMethod("getBakedAnimations");
            java.util.Map<?, ?> anims = (java.util.Map<?, ?>) getAnimsMethod.invoke(null);
            if (anims != null) {
                anims.keySet().removeIf(key -> {
                    if (key instanceof ResourceLocation loc) {
                        if (!loc.getNamespace().equals("custom_mobs")) {
                            return false;
                        }
                        String path = loc.getPath();
                        if (mobId != null && path.contains(mobId)) {
                            return true;
                        }
                        if (modelId != null && path.contains(modelId)) {
                            return true;
                        }
                        return false;
                    }
                    String keyStr = key.toString();
                    if (!keyStr.startsWith("custom_mobs:")) {
                        return false;
                    }
                    return (mobId != null && keyStr.contains(mobId)) || (modelId != null && keyStr.contains(modelId));
                });
            }
        } catch (Throwable ignored) {}
    }

    public static HierarchicalModel<CustomMobEntity> getModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) return null;
        if (failedModels.contains(modelId)) return null;
        if (!modelCache.containsKey(modelId)) {
            loadJavaModel(modelId);
        }
        return modelCache.get(modelId);
    }

    public static Map<String, AnimationDefinition> getAnimations(String modelId) {
        if (modelId == null || modelId.isEmpty()) return Collections.emptyMap();
        if (failedModels.contains(modelId)) return Collections.emptyMap();
        if (!animationCache.containsKey(modelId)) {
            loadJavaModel(modelId);
        }
        return animationCache.getOrDefault(modelId, Collections.emptyMap());
    }

    public static ResourceLocation getTexture(String mobId, String modelId, String texturePath) {
        if (mobId == null || mobId.isEmpty()) return CustomMobRenderer.DEFAULT_TEXTURE;
        if (!textureCache.containsKey(mobId)) {
            loadTexture(mobId, modelId, texturePath);
        }
        return textureCache.getOrDefault(mobId, CustomMobRenderer.DEFAULT_TEXTURE);
    }

    public static void loadJavaModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) return;
        if (failedModels.contains(modelId)) return;
        
        File configFolder = new File(Platform.getConfigFolder().toFile(), "CustomMobs/Mobs/Unpacked");
        File unpackedFolder = new File(configFolder, modelId);
        if (!unpackedFolder.exists() || !unpackedFolder.isDirectory()) {
            failedModels.add(modelId);
            return;
        }

        java.util.List<File> javaFiles = new java.util.ArrayList<>();
        findJavaFilesRecursively(unpackedFolder, javaFiles);

        File modelFile = null;
        File animFile = null;

        for (File f : javaFiles) {
            String name = f.getName().toLowerCase();
            if (name.contains("anim")) {
                animFile = f;
            } else {
                modelFile = f;
            }
        }

        if (modelFile == null) {
            failedModels.add(modelId);
            return;
        }

        try {
            String modelContent = Files.readString(modelFile.toPath());
            HierarchicalModel<CustomMobEntity> model = parseModel(modelContent);
            if (model != null) {
                modelCache.put(modelId, model);
            } else {
                failedModels.add(modelId);
            }

            if (animFile != null) {
                String animContent = Files.readString(animFile.toPath());
                Map<String, AnimationDefinition> animations = parseAnimations(animContent);
                animationCache.put(modelId, animations);
            } else {
                animationCache.put(modelId, Collections.emptyMap());
            }
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Exception occurred while loading Java model for ID: " + modelId, e);
            failedModels.add(modelId);
        }
    }

    public static void loadTexture(String mobId, String modelId, String texturePath) {
        if (mobId == null || mobId.isEmpty()) return;

        if (texturePath != null && texturePath.contains(":")) {
            try {
                ResourceLocation loc = new ResourceLocation(texturePath);
                textureCache.put(mobId, loc);
                return;
            } catch (Exception ignored) {}
        }

        File configFolder = new File(Platform.getConfigFolder().toFile(), "CustomMobs/Mobs/Unpacked");
        File unpackedFolder = null;
        if (modelId != null && !modelId.isEmpty() && !modelId.contains(":")) {
            unpackedFolder = new File(configFolder, modelId);
        }
        if (unpackedFolder == null || !unpackedFolder.exists() || !unpackedFolder.isDirectory()) {
            unpackedFolder = new File(configFolder, mobId);
        }

        if (!unpackedFolder.exists() || !unpackedFolder.isDirectory()) {
            textureCache.put(mobId, CustomMobRenderer.DEFAULT_TEXTURE);
            return;
        }

        File pngFile = null;
        if (texturePath != null && !texturePath.isEmpty()) {
            pngFile = findPngFile(unpackedFolder, texturePath);
        }
        if (pngFile == null && modelId != null && !modelId.isEmpty()) {
            pngFile = findPngFile(unpackedFolder, modelId);
        }
        if (pngFile == null || !pngFile.exists()) {
            pngFile = findAnyPngFile(unpackedFolder);
        }

        if (pngFile != null && pngFile.exists()) {
            try (FileInputStream fis = new FileInputStream(pngFile)) {
                NativeImage nativeImage = NativeImage.read(fis);
                DynamicTexture texture = new DynamicTexture(nativeImage);
                ResourceLocation loc = new ResourceLocation(CustomMobs.MOD_ID, "textures/dynamic/" + mobId.toLowerCase());
                
                net.minecraft.client.renderer.texture.AbstractTexture old = Minecraft.getInstance().getTextureManager().getTexture(loc);
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception ignored) {}
                }

                Minecraft.getInstance().getTextureManager().register(loc, texture);
                textureCache.put(mobId, loc);
            } catch (Exception e) {
                textureCache.put(mobId, CustomMobRenderer.DEFAULT_TEXTURE);
            }
        } else {
            textureCache.put(mobId, CustomMobRenderer.DEFAULT_TEXTURE);
        }
    }

    private static String cleanCommentsAndWhitespace(String content) {
        content = content.replaceAll("//.*", "");
        content = content.replaceAll("/\\*(?s:.*?)\\*/", "");
        content = content.replaceAll("\\s+", " ");
        return content;
    }

    public static HierarchicalModel<CustomMobEntity> parseModel(String content) {
        try {
            content = cleanCommentsAndWhitespace(content);

            int texWidth = 64;
            int texHeight = 64;
            Pattern sizePattern = Pattern.compile("LayerDefinition\\.create\\(\\s*meshdefinition\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
            Matcher sizeMatcher = sizePattern.matcher(content);
            if (sizeMatcher.find()) {
                texWidth = Integer.parseInt(sizeMatcher.group(1));
                texHeight = Integer.parseInt(sizeMatcher.group(2));
            }

            MeshDefinition meshdefinition = new MeshDefinition();
            PartDefinition partdefinition = meshdefinition.getRoot();

            Map<String, PartDefinition> partMap = new HashMap<>();
            partMap.put("partdefinition", partdefinition);

            String[] statements = content.split(";");
            for (String stmt : statements) {
                stmt = stmt.trim();
                if (stmt.contains("addOrReplaceChild")) {
                    Pattern addPattern = Pattern.compile("PartDefinition\\s+(\\w+)\\s*=\\s*(\\(PartDefinition\\)\\s*)?(\\w+)\\.addOrReplaceChild\\(\\s*\"([^\"]+)\"\\s*,\\s*(.+)\\s*\\)");
                    Matcher matcher = addPattern.matcher(stmt);
                    if (matcher.find()) {
                        String varName = matcher.group(1);
                        String parentVarName = matcher.group(3);
                        String partName = matcher.group(4);
                        String remainingArgs = matcher.group(5);

                        PartDefinition parentPart = partMap.get(parentVarName);
                        if (parentPart == null) {
                            parentPart = partdefinition;
                        }

                        int lastComma = remainingArgs.lastIndexOf("PartPose.");
                        if (lastComma == -1) {
                            lastComma = remainingArgs.lastIndexOf("PartPose.ZERO");
                        }
                        if (lastComma == -1) continue;

                        int separatorIndex = remainingArgs.lastIndexOf(",", lastComma);
                        if (separatorIndex == -1) continue;

                        String cubesCode = remainingArgs.substring(0, separatorIndex).trim();
                        String poseCode = remainingArgs.substring(separatorIndex + 1).trim();

                        CubeListBuilder cubes = parseCubes(cubesCode);
                        PartPose pose = parsePose(poseCode);

                        PartDefinition part = parentPart.addOrReplaceChild(partName, cubes, pose);
                        partMap.put(varName, part);
                    }
                }
            }

            LayerDefinition layerDef = LayerDefinition.create(meshdefinition, texWidth, texHeight);
            ModelPart rootPart = layerDef.bakeRoot();
            return new DynamicHierarchicalModel(rootPart);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to parse dynamic Java model:", e);
            return null;
        }
    }

    private static CubeListBuilder parseCubes(String cubesCode) {
        CubeListBuilder builder = CubeListBuilder.create();
        cubesCode = cubesCode.trim();
        if (cubesCode.equals("CubeListBuilder.create()")) {
            return builder;
        }

        List<String> calls = splitChainedMethods(cubesCode);
        for (String call : calls) {
            if (call.startsWith(".texOffs")) {
                Pattern pattern = Pattern.compile("\\.texOffs\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
                Matcher m = pattern.matcher(call);
                if (m.find()) {
                    int x = Integer.parseInt(m.group(1));
                    int y = Integer.parseInt(m.group(2));
                    builder = builder.texOffs(x, y);
                }
            } else if (call.startsWith(".addBox")) {
                int open = call.indexOf('(');
                int close = call.lastIndexOf(')');
                if (open != -1 && close != -1 && close > open) {
                    String inner = call.substring(open + 1, close).trim();
                    float defVal = 0.0f;
                    boolean hasDef = false;
                    if (inner.contains("CubeDeformation")) {
                        hasDef = true;
                        Pattern defPattern = Pattern.compile("CubeDeformation\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*\\)");
                        Matcher defMatcher = defPattern.matcher(inner);
                        if (defMatcher.find()) {
                            defVal = parseFloat(defMatcher.group(1));
                        }
                        int lastComma = inner.lastIndexOf(',');
                        if (lastComma != -1) {
                            inner = inner.substring(0, lastComma).trim();
                        }
                    }
                    String[] parts = inner.split(",");
                    if (parts.length >= 6) {
                        float x = parseFloat(parts[0]);
                        float y = parseFloat(parts[1]);
                        float z = parseFloat(parts[2]);
                        float w = parseFloat(parts[3]);
                        float h = parseFloat(parts[4]);
                        float d = parseFloat(parts[5]);
                        if (hasDef) {
                            builder = builder.addBox(x, y, z, w, h, d, new CubeDeformation(defVal));
                        } else {
                            builder = builder.addBox(x, y, z, w, h, d);
                        }
                    }
                }
            } else if (call.startsWith(".mirror")) {
                if (call.contains("mirror(false)")) {
                    builder = builder.mirror(false);
                } else if (call.contains("mirror(true)")) {
                    builder = builder.mirror(true);
                } else {
                    builder = builder.mirror();
                }
            }
        }
        return builder;
    }

    private static List<String> splitChainedMethods(String cubesCode) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int lastStart = 0;
        for (int i = 0; i < cubesCode.length(); i++) {
            char c = cubesCode.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '.' && depth == 0) {
                if (i > lastStart) {
                    list.add(cubesCode.substring(lastStart, i).trim());
                }
                lastStart = i;
            }
        }
        if (cubesCode.length() > lastStart) {
            list.add(cubesCode.substring(lastStart).trim());
        }
        return list;
    }

    private static PartPose parsePose(String poseCode) {
        poseCode = poseCode.trim();
        if (poseCode.equals("PartPose.ZERO")) {
            return PartPose.ZERO;
        }

        if (poseCode.startsWith("PartPose.offset(")) {
            Pattern pattern = Pattern.compile("PartPose\\.offset\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*\\)");
            Matcher m = pattern.matcher(poseCode);
            if (m.find()) {
                float x = parseFloat(m.group(1));
                float y = parseFloat(m.group(2));
                float z = parseFloat(m.group(3));
                return PartPose.offset(x, y, z);
            }
        } else if (poseCode.startsWith("PartPose.offsetAndRotation(")) {
            Pattern pattern = Pattern.compile("PartPose\\.offsetAndRotation\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*\\)");
            Matcher m = pattern.matcher(poseCode);
            if (m.find()) {
                float x = parseFloat(m.group(1));
                float y = parseFloat(m.group(2));
                float z = parseFloat(m.group(3));
                float pitch = parseFloat(m.group(4));
                float yaw = parseFloat(m.group(5));
                float roll = parseFloat(m.group(6));
                return PartPose.offsetAndRotation(x, y, z, pitch, yaw, roll);
            }
        }

        return PartPose.ZERO;
    }

    private static float parseFloat(String s) {
        s = s.trim().replaceAll("[Ff]", "");
        return Float.parseFloat(s);
    }

    public static Map<String, AnimationDefinition> parseAnimations(String content) {
        Map<String, AnimationDefinition> animations = new HashMap<>();
        try {
            content = cleanCommentsAndWhitespace(content);

            String[] statements = content.split(";");
            for (String stmt : statements) {
                stmt = stmt.trim();
                if (stmt.contains("AnimationDefinition.Builder")) {
                    Pattern defPattern = Pattern.compile("public\\s+static\\s+final\\s+AnimationDefinition\\s+([a-zA-Z0-9_\\.]+)\\s*=");
                    Matcher matcher = defPattern.matcher(stmt);
                    if (matcher.find()) {
                        String fullVarName = matcher.group(1);
                        String animName = fullVarName;
                        int lastDot = fullVarName.lastIndexOf('.');
                        if (lastDot != -1) {
                            animName = fullVarName.substring(lastDot + 1);
                        }

                        float length = 1.0f;
                        Pattern lenPattern = Pattern.compile("withLength\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*\\)");
                        Matcher lenMatcher = lenPattern.matcher(stmt);
                        if (lenMatcher.find()) {
                            length = parseFloat(lenMatcher.group(1));
                        }

                        boolean looping = stmt.contains(".looping()");

                        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(length);
                        if (looping) {
                            builder = builder.looping();
                        }

                        Pattern channelPattern = Pattern.compile("\\.addAnimation\\(\\s*\"([^\"]+)\"\\s*,\\s*new\\s+AnimationChannel\\(\\s*AnimationChannel\\.Targets\\.(\\w+)\\s*,(.*?)\\)\\s*\\)");
                        Matcher chanMatcher = channelPattern.matcher(stmt);
                        while (chanMatcher.find()) {
                            String partName = chanMatcher.group(1);
                            String targetStr = chanMatcher.group(2);
                            String keyframesCode = chanMatcher.group(3).trim();
                            if (!keyframesCode.endsWith(")")) {
                                keyframesCode += ")";
                            }

                            AnimationChannel.Target target = AnimationChannel.Targets.ROTATION;
                            if (targetStr.equals("POSITION")) {
                                target = AnimationChannel.Targets.POSITION;
                            } else if (targetStr.equals("SCALE")) {
                                target = AnimationChannel.Targets.SCALE;
                            }

                            List<Keyframe> keyframes = new ArrayList<>();
                            Pattern keyframePattern = Pattern.compile("new\\s+Keyframe\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*KeyframeAnimations\\.(\\w+)\\(\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?F?)\\s*\\)\\s*,\\s*AnimationChannel\\.Interpolations\\.(\\w+)\\s*\\)");
                            Matcher kfMatcher = keyframePattern.matcher(keyframesCode);
                            while (kfMatcher.find()) {
                                float time = parseFloat(kfMatcher.group(1));
                                String vecType = kfMatcher.group(2);
                                float vx = parseFloat(kfMatcher.group(3));
                                float vy = parseFloat(kfMatcher.group(4));
                                float vz = parseFloat(kfMatcher.group(5));
                                String interpStr = kfMatcher.group(6);

                                Vector3f vec;
                                if (vecType.equals("posVec")) {
                                    vec = KeyframeAnimations.posVec(vx, vy, vz);
                                } else if (vecType.equals("scaleVec")) {
                                    vec = KeyframeAnimations.scaleVec(vx, vy, vz);
                                } else {
                                    vec = KeyframeAnimations.degreeVec(vx, vy, vz);
                                }

                                AnimationChannel.Interpolation interp = AnimationChannel.Interpolations.LINEAR;
                                if (interpStr.equals("CATMULLROM")) {
                                    interp = AnimationChannel.Interpolations.CATMULLROM;
                                }

                                keyframes.add(new Keyframe(time, vec, interp));
                            }

                            if (!keyframes.isEmpty()) {
                                AnimationChannel channel = new AnimationChannel(target, keyframes.toArray(new Keyframe[0]));
                                builder = builder.addAnimation(partName, channel);
                            }
                        }

                        animations.put(animName.toLowerCase(), builder.build());
                    }
                }
            }
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to parse dynamic Java animations:", e);
        }
        return animations;
    }

    public static class DynamicHierarchicalModel extends HierarchicalModel<CustomMobEntity> {
        private final ModelPart root;

        public DynamicHierarchicalModel(ModelPart root) {
            this.root = root;
        }

        @Override
        public void setupAnim(CustomMobEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        }

        @Override
        public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }

        @Override
        public ModelPart root() {
            return this.root;
        }
    }

    private static void findJavaFilesRecursively(File dir, java.util.List<File> list) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".java")) {
                list.add(f);
            } else if (f.isDirectory()) {
                findJavaFilesRecursively(f, list);
            }
        }
    }

    private static File findPngFile(File dir, String filename) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        String nameWithPng = filename.toLowerCase().endsWith(".png") ? filename : filename + ".png";
        for (File f : files) {
            if (f.isFile() && (f.getName().equalsIgnoreCase(filename) || f.getName().equalsIgnoreCase(nameWithPng))) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findPngFile(f, filename);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static File findAnyPngFile(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findAnyPngFile(f);
                if (found != null) return found;
            }
        }
        return null;
    }
}
