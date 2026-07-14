package ddraig.net.custommobs.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobData {
    public String id = "";
    public String name = "";
    public String description = "";
    public String modelType = "vanilla"; // vanilla, geckolib, mcmodel, java
    public String modelId = "";
    public String texturePath = "";
    public String animationPath = "";
    public Map<String, String> animations = new HashMap<>();
    public float scale = 1.0f;
    public float hitboxWidth = 0.6f;
    public float hitboxHeight = 1.8f;
    public boolean billboardName = true;
    public String behaviorMode = "hostile"; // passive, neutral, hostile
    public String mobGroup = "";
    public String nameColor = "white";
    public boolean isFlying = false;
    public boolean tameable = false;
    public String tamingItem = "minecraft:bone";
    public double tamingChance = 33.3;
    public String loreText = "";
    public boolean loopCombo = true;

    public StatsData stats = new StatsData();
    public SoundsData sounds = new SoundsData();
    public SpawnRulesData spawnRules = new SpawnRulesData();
    public List<AbilityData> abilities = new ArrayList<>();
    public LootData loot = new LootData();
    public List<AIGoalData> aiGoals = new ArrayList<>();
    public EliteData elite = new EliteData();

    public static class StatsData {
        public double maxHealth = 20.0;
        public double movementSpeed = 0.25;
        public double followRange = 16.0;
        public double attackDamage = 2.0;
        public double armor = 0.0;
        public double attackSpeed = 2.0;
        public double attackReach = 3.0;
        public double knockbackResistance = 0.0;
        public double knockbackInflicted = 0.0;
        public double regenSpeed = 0.0;
        public double stepHeight = 0.6;
        public double fallDamageResistance = 0.0;
        public boolean fireImmune = false;
        public boolean drowningImmune = false;
        public boolean projectileImmune = false;
        public double projectileReflectionChance = 0.0;
        public double animSpeed = 1.0;
    }

    public static class SoundsData {
        public String ambient = "";
        public String step = "";
        public String hurt = "";
        public String death = "";
        public String attack = "";
    }

    public static class SpawnRulesData {
        public boolean naturalSpawning = true;
        public boolean raidOnly = false;
        public boolean surfaceOnly = false;
        public boolean cavesOnly = false;
        public boolean aquatic = false;
        public boolean lava = false;
        public List<String> biomes = new ArrayList<>();
        public int weight = 10;
        public int minGroup = 1;
        public int maxGroup = 4;
        public String moonPhase = "any";
        public String timeOfDay = "any";
        public String dimension = "any";
        public int minHeight = -64;
        public int maxHeight = 320;
        public String weather = "any";
        public String spawnBlock = "";
        public int minLight = 0;
        public int maxLight = 15;
        public String allowedStructure = "";
    }

    public static class AbilityData {
        public String name = "";
        public String type = "POISON"; // POISON, BURNING, FREEZE, HEALING, TELEPORT, SUMMON, DASH
        public boolean isPassive = false;
        public int cooldownTicks = 100;
        public double power = 2.0;
        public int durationTicks = 60;
        public String particle = "";
    }

    public static class LootData {
        public int xpReward = 5;
        public List<LootItemData> items = new ArrayList<>();
    }

    public static class LootItemData {
        public String itemId = "";
        public double chance = 100.0; // 0.0 to 100.0
        public int minCount = 1;
        public int maxCount = 1;
        public String nbt = ""; // JSON string for item NBT
        public boolean lootingRequired = false;
        public int lootingLevel = 0;
    }

    public static class AIGoalData {
        public String type = ""; // MELEE, RANGED, WANDER, FLEE, FOLLOW, SUMMON_MINIONS, TARGET_PLAYER, TARGET_VILLAGER, TARGET_ANIMALS, TARGET_MOB_IDS, TARGET_REVENGE
        public int priority = 1;
        public String animation = "";
        public Map<String, String> params = new HashMap<>();
    }

    public static class EliteData {
        public double chance = 5.0; // 0.0 to 100.0
        public List<String> extraAbilities = new ArrayList<>();
    }

    // Transient fields for overall model dimensions
    private transient float calculatedWidth = -1.0f;
    private transient float calculatedHeight = -1.0f;
    private transient boolean dimsCalculated = false;

    public float getModelWidth() {
        if (!dimsCalculated) {
            calculateDimensions();
        }
        return calculatedWidth;
    }

    public float getModelHeight() {
        if (!dimsCalculated) {
            calculateDimensions();
        }
        return calculatedHeight;
    }

    public void resetDimensions() {
        this.dimsCalculated = false;
    }

    private void calculateDimensions() {
        try {
            if (this.modelType != null && this.modelType.equalsIgnoreCase("vanilla")) {
                try {
                    net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(this.modelId);
                    net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (type != null) {
                        net.minecraft.world.entity.EntityDimensions d = type.getDimensions();
                        this.calculatedWidth = d.width;
                        this.calculatedHeight = d.height;
                        this.dimsCalculated = true;
                        return;
                    }
                } catch (Exception ignored) {}
            } else if (this.id != null && !this.id.isEmpty()) {
                java.nio.file.Path baseDir = dev.architectury.platform.Platform.getConfigFolder().resolve("CustomMobs/Mobs/Unpacked/" + this.id.toLowerCase(java.util.Locale.ROOT));
                File folder = baseDir.toFile();
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles();
                    if (files != null) {
                        if (this.modelType != null && this.modelType.equalsIgnoreCase("geckolib")) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().endsWith(".geo.json")) {
                                    parseGeckoLibDimensions(f);
                                    this.dimsCalculated = true;
                                    return;
                                }
                            }
                        } else if (this.modelType != null && this.modelType.equalsIgnoreCase("java")) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().endsWith(".java")) {
                                    parseJavaDimensions(f);
                                    this.dimsCalculated = true;
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback done below
        }

        // Fallback default bounds
        this.calculatedWidth = this.hitboxWidth;
        this.calculatedHeight = this.hitboxHeight;
        this.dimsCalculated = true;
    }

    private void parseGeckoLibDimensions(File file) {
        try {
            String content = Files.readString(file.toPath());
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has("minecraft:geometry")) {
                JsonArray geometries = json.getAsJsonArray("minecraft:geometry");
                if (geometries.size() > 0) {
                    JsonObject geom = geometries.get(0).getAsJsonObject();
                    
                    float boundsW = 0.0f;
                    float boundsH = 0.0f;
                    if (geom.has("description")) {
                        JsonObject desc = geom.getAsJsonObject("description");
                        boundsW = desc.has("visible_bounds_width") ? desc.get("visible_bounds_width").getAsFloat() : 0.0f;
                        boundsH = desc.has("visible_bounds_height") ? desc.get("visible_bounds_height").getAsFloat() : 0.0f;
                    }

                    if (geom.has("bones")) {
                        JsonArray bones = geom.getAsJsonArray("bones");
                        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
                        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                        boolean hasCubes = false;
                        for (int i = 0; i < bones.size(); i++) {
                            JsonObject bone = bones.get(i).getAsJsonObject();
                            if (bone.has("cubes")) {
                                JsonArray cubes = bone.getAsJsonArray("cubes");
                                for (int j = 0; j < cubes.size(); j++) {
                                    JsonObject cube = cubes.get(j).getAsJsonObject();
                                    if (cube.has("origin") && cube.has("size")) {
                                        JsonArray origin = cube.getAsJsonArray("origin");
                                        JsonArray size = cube.getAsJsonArray("size");
                                        if (origin.size() >= 3 && size.size() >= 3) {
                                            float ox = origin.get(0).getAsFloat();
                                            float oy = origin.get(1).getAsFloat();
                                            float oz = origin.get(2).getAsFloat();
                                            float sx = size.get(0).getAsFloat();
                                            float sy = size.get(1).getAsFloat();
                                            float sz = size.get(2).getAsFloat();
                                            minX = Math.min(minX, ox);
                                            maxX = Math.max(maxX, ox + sx);
                                            minY = Math.min(minY, oy);
                                            maxY = Math.max(maxY, oy + sy);
                                            minZ = Math.min(minZ, oz);
                                            maxZ = Math.max(maxZ, oz + sz);
                                            hasCubes = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (hasCubes) {
                            this.calculatedWidth = Math.max((maxX - minX) / 16.0f, 0.1f);
                            this.calculatedHeight = Math.max((maxY - minY) / 16.0f, 0.1f);
                            return;
                        }
                    }

                    if (boundsW > 0.0f && boundsH > 0.0f) {
                        this.calculatedWidth = boundsW;
                        this.calculatedHeight = boundsH;
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        this.calculatedWidth = this.hitboxWidth;
        this.calculatedHeight = this.hitboxHeight;
    }

    private void parseJavaDimensions(File file) {
        try {
            String content = Files.readString(file.toPath());
            content = cleanCommentsAndWhitespace(content);
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            boolean hasCubes = false;
            String[] statements = content.split(";");
            for (String stmt : statements) {
                stmt = stmt.trim();
                if (stmt.contains("addBox")) {
                    int open = stmt.indexOf('(');
                    int close = stmt.lastIndexOf(')');
                    if (open != -1 && close != -1 && close > open) {
                        String inner = stmt.substring(open + 1, close).trim();
                        if (inner.contains("CubeDeformation")) {
                            int lastComma = inner.lastIndexOf(',');
                            if (lastComma != -1) {
                                inner = inner.substring(0, lastComma).trim();
                            }
                        }
                        String[] parts = inner.split(",");
                        if (parts.length >= 6) {
                            float x = parseFloatOrZero(parts[0]);
                            float y = parseFloatOrZero(parts[1]);
                            float z = parseFloatOrZero(parts[2]);
                            float w = parseFloatOrZero(parts[3]);
                            float h = parseFloatOrZero(parts[4]);
                            float d = parseFloatOrZero(parts[5]);
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x + w);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y + h);
                            minZ = Math.min(minZ, z);
                            maxZ = Math.max(maxZ, z + d);
                            hasCubes = true;
                        }
                    }
                }
            }
            if (hasCubes) {
                this.calculatedWidth = Math.max(maxX - minX, maxZ - minZ) / 16.0f;
                this.calculatedHeight = (maxY - minY) / 16.0f;
                return;
            }
        } catch (Exception e) {
            // fallback
        }
        this.calculatedWidth = this.hitboxWidth;
        this.calculatedHeight = this.hitboxHeight;
    }

    private String cleanCommentsAndWhitespace(String content) {
        content = content.replaceAll("//.*", "");
        content = content.replaceAll("/\\*(?s:.*?)\\*/", "");
        content = content.replaceAll("\\s+", " ");
        return content;
    }

    private float parseFloatOrZero(String s) {
        try {
            s = s.trim().replaceAll("[Ff]", "");
            return Float.parseFloat(s);
        } catch (Exception e) {
            return 0.0f;
        }
    }
}
