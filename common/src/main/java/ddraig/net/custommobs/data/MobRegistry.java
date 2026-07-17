package ddraig.net.custommobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ddraig.net.custommobs.CustomMobs;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MobRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static final Map<String, MobData> loadedMobs = new ConcurrentHashMap<>();
    public static final Map<String, ProjectileData> loadedProjectiles = new ConcurrentHashMap<>();
    public static final Map<String, MobData.AbilityData> abilityTemplates = new ConcurrentHashMap<>();

    private static final String[] PASSIVE_ABILITIES = {
        "Fireproof Scales", "Gills of the Deep", "Traction Tread", "Feather Light", "Rejuvenation Aura",
        "Deep Diver", "Step Assist", "Shadow Camouflage", "Thorn Guard", "Photosynthesis",
        "Toxic Secretions", "Glacial Aura", "Magnetosphere", "Night Eyes", "Reinforced Hide",
        "Spider Climb"
    };

    private static final String[] ACTIVE_ABILITIES = {
        "Flame Breath", "Tail Sweep", "Venomous Bite", "Sonic Screech",
        "Thunder Stomp", "Frost Nova", "Blight Spit", "Healing Touch", "Sonic Dash",
        "Abyssal Stealth", "High Jump", "Wind Glide", "Frightening Roar", "Lightning Strike",
        "Spore Blast", "Iron Wall", "Infernal Charge", "Life Steal Bite", "Teleport Dash",
        "Aqua Propulsion", "Trample", "Feather Hover"
    };

    private static String getDeductedType(String name) {
        if (name.contains("Flame") || name.contains("Fire") || name.contains("Infernal")) {
            return "BURNING";
        } else if (name.contains("Frost") || name.contains("Nova") || name.contains("Glacial")) {
            return "FREEZE";
        } else if (name.contains("Healing") || name.contains("Rejuvenation")) {
            return "HEALING";
        } else if (name.contains("Teleport")) {
            return "TELEPORT";
        } else if (name.contains("Dash") || name.contains("Charge")) {
            return "DASH";
        } else {
            return "POISON";
        }
    }

    private static void loadAbilitiesConfig(File baseDir) {
        File file = new File(baseDir, "abilities.json");
        if (!file.exists()) {
            abilityTemplates.clear();
            for (String p : PASSIVE_ABILITIES) {
                MobData.AbilityData ab = new MobData.AbilityData();
                ab.name = p;
                ab.isPassive = true;
                ab.cooldownTicks = 100;
                ab.power = 2.0;
                ab.durationTicks = 60;
                ab.particle = "";
                ab.type = getDeductedType(p);
                abilityTemplates.put(p, ab);
            }
            for (String a : ACTIVE_ABILITIES) {
                MobData.AbilityData ab = new MobData.AbilityData();
                ab.name = a;
                ab.isPassive = false;
                ab.cooldownTicks = 100;
                ab.power = 2.0;
                ab.durationTicks = 60;
                ab.particle = "";
                ab.type = getDeductedType(a);
                abilityTemplates.put(a, ab);
            }
            try (java.io.Writer writer = new java.io.FileWriter(file)) {
                GSON.toJson(abilityTemplates, writer);
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to save default abilities.json", e);
            }
        } else {
            try (java.io.Reader reader = new java.io.FileReader(file)) {
                java.lang.reflect.Type typeOfHashMap = new com.google.gson.reflect.TypeToken<Map<String, MobData.AbilityData>>() {}.getType();
                Map<String, MobData.AbilityData> loaded = GSON.fromJson(reader, typeOfHashMap);
                if (loaded != null) {
                    abilityTemplates.clear();
                    abilityTemplates.putAll(loaded);
                }
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to load abilities.json", e);
            }
        }
    }

    // Suggestion Cache Arrays to prevent lagging on Visual Creator Screens
    public static final List<String> cachedSounds = new ArrayList<>();
    public static final List<String> cachedParticles = new ArrayList<>();
    public static final List<String> cachedBiomes = new ArrayList<>();
    public static final List<String> cachedModels = new ArrayList<>();

    private static File mobsFolder;
    private static File mobsPacksFolder;
    private static File projFolder;
    private static File projPacksFolder;
    private static File soundsFolder;

    public static File getMobsFolder() { return mobsFolder; }
    public static File getSoundsFolder() { return soundsFolder; }

    public static void init() {
        File baseDir = new File(Platform.getConfigFolder().toFile(), "CustomMobs");
        mobsFolder = new File(baseDir, "Mobs/Unpacked");
        mobsPacksFolder = new File(baseDir, "Mobs/Packs");
        projFolder = new File(baseDir, "Projectiles/Unpacked");
        projPacksFolder = new File(baseDir, "Projectiles/Packs");
        soundsFolder = new File(baseDir, "Sounds");

        if (!mobsFolder.exists()) mobsFolder.mkdirs();
        if (!mobsPacksFolder.exists()) mobsPacksFolder.mkdirs();
        if (!projFolder.exists()) projFolder.mkdirs();
        if (!projPacksFolder.exists()) projPacksFolder.mkdirs();
        if (!soundsFolder.exists()) soundsFolder.mkdirs();

        loadAbilitiesConfig(baseDir);

        reloadAll();
    }

    public static void reloadAll() {
        reloadProjectiles();
        reloadMobs();
        rebuildSuggestionsCache();
    }

    public static void reloadMobs() {
        loadedMobs.clear();
        
        // Scan packs first and unpack
        File[] packs = mobsPacksFolder.listFiles();
        if (packs != null) {
            for (File p : packs) {
                if (p.isFile() && p.getName().endsWith(".zip")) {
                    String name = p.getName().substring(0, p.getName().length() - 4);
                    unpackMob(name);
                }
            }
        }

        // Scan unpacked directories
        File[] folders = mobsFolder.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    File mobJson = new File(folder, "mob.json");
                    if (mobJson.exists()) {
                        try {
                            String content = java.nio.file.Files.readString(mobJson.toPath());
                            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                            if (obj.has("gravity") || obj.has("sticky") || obj.has("particleType")) {
                                CustomMobs.LOGGER.warn("Skipping projectile template found in mobs folder: " + mobJson.getAbsolutePath());
                                continue;
                            }
                            MobData data = GSON.fromJson(content, MobData.class);
                            if (data != null && !data.id.isEmpty()) {
                                loadedMobs.put(data.id, data);
                            }
                        } catch (Exception e) {
                            CustomMobs.LOGGER.error("Failed to parse mob config at " + mobJson.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }

        // Generate a default mob template if empty
        if (loadedMobs.isEmpty()) {
            generateDefaultMobTemplate();
        }
    }

    public static void reloadProjectiles() {
        loadedProjectiles.clear();

        File[] packs = projPacksFolder.listFiles();
        if (packs != null) {
            for (File p : packs) {
                if (p.isFile() && p.getName().endsWith(".zip")) {
                    String name = p.getName().substring(0, p.getName().length() - 4);
                    unpackProjectile(name);
                }
            }
        }

        File[] folders = projFolder.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    File projJson = new File(folder, "projectile.json");
                    if (projJson.exists()) {
                        try {
                            String content = java.nio.file.Files.readString(projJson.toPath());
                            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                            if (obj.has("behaviorMode") || obj.has("aiGoals") || obj.has("spawnRules")) {
                                CustomMobs.LOGGER.warn("Skipping mob template found in projectiles folder: " + projJson.getAbsolutePath());
                                continue;
                            }
                            ProjectileData data = GSON.fromJson(content, ProjectileData.class);
                            if (data != null && !data.id.isEmpty()) {
                                loadedProjectiles.put(data.id, data);
                            }
                        } catch (Exception e) {
                            CustomMobs.LOGGER.error("Failed to parse projectile config at " + projJson.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }

        if (loadedProjectiles.isEmpty()) {
            generateDefaultProjectileTemplate();
        }
    }

    public static void saveMob(MobData mob) {
        saveMob(mob, mob.id);
    }

    public static void saveMob(MobData mob, String oldId) {
        if (mob.id == null || mob.id.isEmpty()) return;

        if (oldId != null && !oldId.isEmpty() && !oldId.equalsIgnoreCase(mob.id)) {
            // Delete old packs if they exist
            File oldZip = new File(mobsPacksFolder, oldId + ".zip");
            if (oldZip.exists()) {
                oldZip.delete();
            }

            // Rename unpacked folder from oldId to mob.id
            File oldFolder = new File(mobsFolder, oldId.toLowerCase());
            File newFolder = new File(mobsFolder, mob.id.toLowerCase());
            if (oldFolder.exists() && oldFolder.isDirectory()) {
                if (!newFolder.exists()) {
                    oldFolder.renameTo(newFolder);
                } else {
                    // Merge/move contents of old folder to new folder, then delete old folder
                    File[] files = oldFolder.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.renameTo(new File(newFolder, f.getName()));
                        }
                    }
                    deleteDirRecursively(oldFolder);
                }
            }
            loadedMobs.remove(oldId);
        }

        File folder = new File(mobsFolder, mob.id.toLowerCase());
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, "mob.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(mob, writer);
            loadedMobs.put(mob.id, mob);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to save mob config: " + mob.id, e);
        }
    }

    public static void saveProjectile(ProjectileData proj) {
        saveProjectile(proj, proj.id);
    }

    public static void saveProjectile(ProjectileData proj, String oldId) {
        if (proj.id == null || proj.id.isEmpty()) return;

        if (oldId != null && !oldId.isEmpty() && !oldId.equalsIgnoreCase(proj.id)) {
            // Delete old packs if they exist
            File oldZip = new File(projPacksFolder, oldId + ".zip");
            if (oldZip.exists()) {
                oldZip.delete();
            }

            // Rename unpacked folder from oldId to proj.id
            File oldFolder = new File(projFolder, oldId.toLowerCase());
            File newFolder = new File(projFolder, proj.id.toLowerCase());
            if (oldFolder.exists() && oldFolder.isDirectory()) {
                if (!newFolder.exists()) {
                    oldFolder.renameTo(newFolder);
                } else {
                    File[] files = oldFolder.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.renameTo(new File(newFolder, f.getName()));
                        }
                    }
                    deleteDirRecursively(oldFolder);
                }
            }
            loadedProjectiles.remove(oldId);
        }

        File folder = new File(projFolder, proj.id.toLowerCase());
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, "projectile.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(proj, writer);
            loadedProjectiles.put(proj.id, proj);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to save projectile config: " + proj.id, e);
        }
    }

    public static void rebuildSuggestionsCache() {
        // Run safely on client-side threads
        try {
            cachedSounds.clear();
            for (ResourceLocation sound : BuiltInRegistries.SOUND_EVENT.keySet()) {
                cachedSounds.add(sound.toString());
            }

            cachedParticles.clear();
            for (ResourceLocation particle : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
                cachedParticles.add(particle.toString());
            }

            cachedBiomes.clear();
            cachedBiomes.addAll(List.of(
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes",
                "minecraft:desert", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:forest",
                "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest",
                "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:river",
                "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
                "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:ocean",
                "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean",
                "minecraft:deep_frozen_ocean", "minecraft:mushroom_fields", "minecraft:savanna", "minecraft:savanna_plateau",
                "minecraft:windswept_savanna", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
                "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:meadow",
                "minecraft:grove", "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks",
                "minecraft:stony_peaks", "minecraft:cherry_grove", "minecraft:nether_wastes", "minecraft:soul_sand_valley",
                "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:basalt_deltas", "minecraft:the_end",
                "minecraft:small_end_islands", "minecraft:end_midlands", "minecraft:end_highlands", "minecraft:end_barrens",
                "minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:deep_dark"
            ));

            try {
                Class<?> clientClass = Class.forName("ddraig.net.custommobs.client.CustomMobsClient");
                java.lang.reflect.Method method = clientClass.getMethod("populateClientBiomes", List.class);
                method.invoke(null, cachedBiomes);
            } catch (Throwable ignored) {}

            java.util.Collections.sort(cachedBiomes);

            cachedModels.clear();
            File[] folders = mobsFolder.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory()) {
                        if (hasModelFile(folder)) {
                            cachedModels.add(folder.getName());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void unpackMob(String id) {
        File zipFile = new File(mobsPacksFolder, id + ".zip");
        File destDir = new File(mobsFolder, id);
        if (!zipFile.exists()) return;
        if (!destDir.exists()) destDir.mkdirs();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File filePath = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    filePath.mkdirs();
                } else {
                    filePath.getParentFile().mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to unpack mob zip: " + id, e);
        }
    }

    public static void packMob(String id) {
        File zipFile = new File(mobsPacksFolder, id + ".zip");
        File srcDir = new File(mobsFolder, id);
        if (!srcDir.exists()) return;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            packDirectory(srcDir, srcDir, zos);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to pack mob zip: " + id, e);
        }
    }

    public static void unpackProjectile(String id) {
        File zipFile = new File(projPacksFolder, id + ".zip");
        File destDir = new File(projFolder, id);
        if (!zipFile.exists()) return;
        if (!destDir.exists()) destDir.mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File filePath = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    filePath.mkdirs();
                } else {
                    filePath.getParentFile().mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to unpack projectile zip: " + id, e);
        }
    }

    public static void packProjectile(String id) {
        File zipFile = new File(projPacksFolder, id + ".zip");
        File srcDir = new File(projFolder, id);
        if (!srcDir.exists()) return;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            packDirectory(srcDir, srcDir, zos);
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to pack projectile zip: " + id, e);
        }
    }

    private static void packDirectory(File root, File dir, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                packDirectory(root, f, zos);
            } else {
                String entryName = root.toPath().relativize(f.toPath()).toString().replace("\\", "/");
                zos.putNextEntry(new ZipEntry(entryName));
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void generateDefaultMobTemplate() {
        MobData defMob = new MobData();
        defMob.id = "zombie_warrior";
        defMob.name = "Zombie Warrior";
        defMob.description = "A powerful armored custom zombie variant.";
        defMob.modelType = "vanilla";
        defMob.modelId = "minecraft:zombie";
        defMob.scale = 1.2f;
        defMob.billboardName = true;
        
        defMob.stats.maxHealth = 40.0;
        defMob.stats.movementSpeed = 0.28;
        defMob.stats.attackDamage = 6.0;
        defMob.stats.armor = 4.0;
        
        defMob.sounds.ambient = "minecraft:entity.zombie.ambient";
        defMob.sounds.hurt = "minecraft:entity.zombie.hurt";
        defMob.sounds.death = "minecraft:entity.zombie.death";
        defMob.sounds.attack = "minecraft:entity.zombie.attack_wooden_door";

        defMob.spawnRules.naturalSpawning = true;
        defMob.spawnRules.weight = 20;
        defMob.spawnRules.biomes.add("minecraft:plains");

        MobData.AbilityData poisonAbility = new MobData.AbilityData();
        poisonAbility.name = "Poison Strike";
        poisonAbility.type = "POISON";
        poisonAbility.cooldownTicks = 120;
        poisonAbility.power = 1.0;
        poisonAbility.durationTicks = 100;
        poisonAbility.particle = "minecraft:happy_villager";
        defMob.abilities.add(poisonAbility);

        MobData.AIGoalData targetPlayers = new MobData.AIGoalData();
        targetPlayers.type = "TARGET_PLAYER";
        targetPlayers.priority = 1;
        defMob.aiGoals.add(targetPlayers);

        MobData.AIGoalData meleeGoal = new MobData.AIGoalData();
        meleeGoal.type = "MELEE";
        meleeGoal.priority = 2;
        meleeGoal.params.put("speed", "1.0");
        defMob.aiGoals.add(meleeGoal);

        MobData.AIGoalData wanderGoal = new MobData.AIGoalData();
        wanderGoal.type = "WANDER";
        wanderGoal.priority = 3;
        wanderGoal.params.put("radius", "8");
        defMob.aiGoals.add(wanderGoal);

        saveMob(defMob);
    }

    private static void generateDefaultProjectileTemplate() {
        ProjectileData defProj = new ProjectileData();
        defProj.id = "fireball";
        defProj.name = "Fireball";
        defProj.modelType = "vanilla";
        defProj.modelId = "minecraft:small_fireball";
        defProj.scale = 1.5f;
        defProj.gravity = false;
        
        defProj.sounds.fire = "minecraft:entity.blaze.shoot";
        defProj.sounds.land = "minecraft:entity.generic.explode";

        defProj.effects.explosion = true;
        defProj.effects.explosionRadius = 2.5f;
        defProj.effects.destroyBlocks = false;

        saveProjectile(defProj);
    }

    public static void deleteMob(String id) {
        loadedMobs.remove(id);
        File baseDir = new File(Platform.getConfigFolder().toFile(), "CustomMobs");
        File folder = new File(baseDir, "Mobs/Unpacked/" + id.toLowerCase(java.util.Locale.ROOT));
        if (folder.exists()) {
            deleteDirRecursively(folder);
        }
        File packFile = new File(baseDir, "Mobs/Packs/" + id + ".zip");
        if (packFile.exists()) {
            packFile.delete();
        }
    }

    public static void deleteProjectile(String id) {
        loadedProjectiles.remove(id);
        File baseDir = new File(Platform.getConfigFolder().toFile(), "CustomMobs");
        File folder = new File(baseDir, "Projectiles/Unpacked/" + id.toLowerCase(java.util.Locale.ROOT));
        if (folder.exists()) {
            deleteDirRecursively(folder);
        }
        File packFile = new File(baseDir, "Projectiles/Packs/" + id + ".zip");
        if (packFile.exists()) {
            packFile.delete();
        }
    }

    private static void deleteDirRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirRecursively(f);
                }
            }
        }
        file.delete();
    }

    private static boolean hasModelFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".geo.json") || name.endsWith(".java")) {
                    return true;
                }
            } else if (f.isDirectory()) {
                if (hasModelFile(f)) return true;
            }
        }
        return false;
    }
}
