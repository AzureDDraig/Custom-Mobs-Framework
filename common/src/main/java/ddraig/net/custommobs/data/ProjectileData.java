package ddraig.net.custommobs.data;

import java.util.ArrayList;
import java.util.List;

public class ProjectileData {
    public String id = "";
    public String name = "";
    public String modelType = "vanilla"; // vanilla, geckolib, java
    public String modelId = "minecraft:arrow";
    public String texturePath = "";
    public float scale = 1.0f;
    public float hitboxWidth = 0.25f;
    public float hitboxHeight = 0.25f;
    public boolean gravity = true;
    public boolean sticky = false;
    public String particleType = "minecraft:small_flame";

    public ProjSoundsData sounds = new ProjSoundsData();
    public ProjEffectsData effects = new ProjEffectsData();

    public static class ProjSoundsData {
        public String fire = "minecraft:entity.arrow.shoot";
        public String land = "minecraft:entity.arrow.hit";
    }

    public static class ProjEffectsData {
        public List<StatusEffectData> statusEffects = new ArrayList<>();
        public boolean explosion = false;
        public float explosionRadius = 2.0f;
        public boolean destroyBlocks = false;
    }

    public static class StatusEffectData {
        public String effectId = ""; // e.g. minecraft:poison
        public int durationTicks = 100;
        public int amplifier = 0;
    }
}
