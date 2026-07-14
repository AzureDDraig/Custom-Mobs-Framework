package ddraig.net.custommobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import java.io.File;
import java.nio.file.Files;

public class ModConfig {
    public static JsonObject rawConfig = new JsonObject();

    public static void load() {
        File folder = Platform.getConfigFolder().resolve("CustomMobs").toFile();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, "custommobs.json");
        try {
            if (!file.exists()) {
                rawConfig = createDefaultConfig();
                save(file);
            } else {
                String content = Files.readString(file.toPath());
                rawConfig = JsonParser.parseString(content).getAsJsonObject();
                JsonObject def = createDefaultConfig();
                merge(rawConfig, def);
                save(file);
            }
        } catch (Exception e) {
            rawConfig = createDefaultConfig();
        }
    }

    public static void reload() {
        load();
        for (MobData mob : MobRegistry.loadedMobs.values()) {
            clampMobStats(mob);
        }
    }

    private static void merge(JsonObject target, JsonObject source) {
        for (String key : source.keySet()) {
            if (!target.has(key)) {
                target.add(key, source.get(key));
            } else if (target.get(key).isJsonObject() && source.get(key).isJsonObject()) {
                merge(target.getAsJsonObject(key), source.getAsJsonObject(key));
            }
        }
    }

    private static void save(File file) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file.toPath(), gson.toJson(rawConfig));
        } catch (Exception ignored) {}
    }

    private static JsonObject createDefaultConfig() {
        JsonObject root = new JsonObject();
        
        JsonObject limits = new JsonObject();
        addLimit(limits, "maxHealth", 1.0, 1000.0, 20.0, "Minimum and maximum allowable max health.");
        addLimit(limits, "movementSpeed", 0.0, 2.0, 0.25, "Minimum and maximum allowable movement speed.");
        addLimit(limits, "followRange", 2.0, 128.0, 16.0, "Minimum and maximum target follow range (blocks).");
        addLimit(limits, "attackDamage", 0.0, 500.0, 2.0, "Minimum and maximum base melee attack damage.");
        addLimit(limits, "armor", 0.0, 100.0, 0.0, "Minimum and maximum base armor points.");
        addLimit(limits, "attackSpeed", 0.1, 10.0, 2.0, "Minimum and maximum melee attack speed.");
        addLimit(limits, "attackReach", 1.0, 15.0, 3.0, "Minimum and maximum melee reach distance (blocks).");
        addLimit(limits, "knockbackResistance", 0.0, 1.0, 0.0, "Minimum and maximum knockback resistance ratio.");
        addLimit(limits, "knockbackInflicted", 0.0, 10.0, 0.0, "Minimum and maximum knockback power inflicted on target hit.");
        addLimit(limits, "regenSpeed", 0.0, 50.0, 0.0, "Health regenerated per second on the server.");
        addLimit(limits, "stepHeight", 0.0, 2.5, 0.6, "Block step-up traversal height override.");
        addLimit(limits, "fallDamageResistance", 0.0, 1.0, 0.0, "Fall damage resistance multiplier (0 = full damage, 1 = immune).");
        addLimit(limits, "projectileReflectionChance", 0.0, 1.0, 0.0, "Chance to bounce incoming projectiles back at shooter.");
        addLimit(limits, "animSpeed", 0.1, 5.0, 1.0, "Multiplier for model animation playback speeds.");
        root.add("attribute_limits", limits);

        JsonObject constants = new JsonObject();
        addLimit(constants, "delay_ticks", 1.0, 600.0, 60.0, "Idle delay duration (ticks) for the DELAY AI behavior goal.");
        addLimit(constants, "stalk_sight_count", 1.0, 10.0, 3.0, "Number of times player must be lost/regained from sight to trigger STALK aggro.");
        addLimit(constants, "leap_velocity", 0.1, 3.0, 0.8, "Horizontal leap launch velocity scaling for LEAP_ATTACK.");
        addLimit(constants, "bright_light_threshold", 0.0, 15.0, 8.0, "Light level above which bright-avoiding mobs trigger AVOID_LIGHT flee.");
        addLimit(constants, "return_to_spawn_leash_distance", 5.0, 256.0, 32.0, "Distance from spawn coordinates before RETURN_TO_SPAWN forces recall pathfind.");
        root.add("goal_constants", constants);

        JsonObject general = new JsonObject();
        addLimit(general, "global_elite_spawn_chance", 0.0, 100.0, 5.0, "Global percentage chance for custom mobs to spawn as Elites naturally.");
        root.add("general", general);

        return root;
    }

    private static void addLimit(JsonObject category, String key, double min, double max, double def, String desc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("min", min);
        obj.addProperty("max", max);
        obj.addProperty("default", def);
        obj.addProperty("description", desc);
        category.add(key, obj);
    }

    public static double getMin(String category, String key) {
        try {
            return rawConfig.getAsJsonObject(category).getAsJsonObject(key).get("min").getAsDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double getMax(String category, String key) {
        try {
            return rawConfig.getAsJsonObject(category).getAsJsonObject(key).get("max").getAsDouble();
        } catch (Exception e) {
            return 100.0;
        }
    }

    public static double getVal(String category, String key) {
        try {
            return rawConfig.getAsJsonObject(category).getAsJsonObject(key).get("default").getAsDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double clamp(String category, String key, double val) {
        double min = getMin(category, key);
        double max = getMax(category, key);
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    public static void clampMobStats(MobData mob) {
        mob.stats.maxHealth = clamp("attribute_limits", "maxHealth", mob.stats.maxHealth);
        mob.stats.movementSpeed = clamp("attribute_limits", "movementSpeed", mob.stats.movementSpeed);
        mob.stats.followRange = clamp("attribute_limits", "followRange", mob.stats.followRange);
        mob.stats.attackDamage = clamp("attribute_limits", "attackDamage", mob.stats.attackDamage);
        mob.stats.armor = clamp("attribute_limits", "armor", mob.stats.armor);
        mob.stats.attackSpeed = clamp("attribute_limits", "attackSpeed", mob.stats.attackSpeed);
        mob.stats.attackReach = clamp("attribute_limits", "attackReach", mob.stats.attackReach);
        mob.stats.knockbackResistance = clamp("attribute_limits", "knockbackResistance", mob.stats.knockbackResistance);
        mob.stats.knockbackInflicted = clamp("attribute_limits", "knockbackInflicted", mob.stats.knockbackInflicted);
        mob.stats.regenSpeed = clamp("attribute_limits", "regenSpeed", mob.stats.regenSpeed);
        mob.stats.stepHeight = clamp("attribute_limits", "stepHeight", mob.stats.stepHeight);
        mob.stats.fallDamageResistance = clamp("attribute_limits", "fallDamageResistance", mob.stats.fallDamageResistance);
        mob.stats.projectileReflectionChance = clamp("attribute_limits", "projectileReflectionChance", mob.stats.projectileReflectionChance);
        mob.stats.animSpeed = clamp("attribute_limits", "animSpeed", mob.stats.animSpeed);
    }
}
