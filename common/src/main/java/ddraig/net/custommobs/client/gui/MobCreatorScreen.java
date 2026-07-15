package ddraig.net.custommobs.client.gui;

import com.google.gson.Gson;
import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.ModConfig;
import ddraig.net.custommobs.entity.CustomMobEntity;
import ddraig.net.custommobs.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobCreatorScreen extends Screen {
    private final List<MobData> mobTemplates = new ArrayList<>();
    private MobData selectedMob;
    private String activeTab = "General"; // General, Model, AI, Stats, Abilities, Sounds, Loot, Spawning

    private int panelW = 440;
    private int panelH = 260;

    // General (Basics)
    private EditBox nameField;
    private EditBox mobGroupField;
    private EditBox tamingChanceField;
    private net.minecraft.client.gui.components.MultiLineEditBox loreField;

    // Model
    private EditBox modelIdField;
    private EditBox textureField;
    private EditBox animField;

    // Attributes (Stats)
    private EditBox healthField;
    private EditBox speedField;
    private EditBox followRangeField;
    private EditBox damageField;
    private EditBox armorField;
    private EditBox attackSpeedField;
    private EditBox attackReachField;
    private EditBox knockbackResistanceField;
    private EditBox knockbackInflictedField;
    private EditBox regenSpeedField;
    private EditBox stepHeightField;
    private EditBox fallResField;
    private EditBox reflectionChanceField;

    // Sounds
    private EditBox ambientSoundField;
    private EditBox stepSoundField;
    private EditBox hurtSoundField;
    private EditBox deathSoundField;
    private EditBox attackSoundField;

    // Loot & Spawning
    private EditBox xpField;
    private EditBox biomeSearchField;
    private EditBox naturalWeightField;
    private EditBox minGroupField;
    private EditBox maxGroupField;
    private EditBox minHeightField;
    private EditBox maxHeightField;
    private EditBox spawnBlockField;
    private EditBox minLightField;
    private EditBox maxLightField;
    private EditBox structureField;

    // Edit fields for selected Loot drop
    private EditBox lootChanceField;
    private EditBox lootMinField;
    private EditBox lootMaxField;
    private EditBox lootLevelField;
    private int selectedLootIndex = -1;

    // Suggestions Autocomplete dropdown state
    private boolean showSuggestions = false;
    private List<String> activeSuggestions = new ArrayList<>();
    private EditBox behaviorSearchField;
    private final List<String> filteredBehaviors = new ArrayList<>();
    private EditBox activeField = null;
    private int suggestionsYOffset = 0;
    private int suggestionsScrollOffset = 0;

    private String lastModelType = "";
    private String lastModelId = "";
    private String lastTexturePath = "";
    private String lastAnimationPath = "";

    private EditBox lastFocusedField = null;
    private String lastQueryValue = "";

    // Reusable Item selection popup overlay state
    private boolean showItemSelector = false;
    private int itemSelectorScroll = 0;
    private java.util.function.Consumer<ItemStack> itemSelectionCallback = null;
    private final List<ItemStack> selectorItems = new ArrayList<>();
    private EditBox itemSearchField;
    private boolean selectAllItems = true;

    // Animations tab fields
    private EditBox idleAnimField;
    private EditBox walkAnimField;
    private EditBox attackAnimField;
    private EditBox deathAnimField;
    private EditBox swimAnimField;
    private EditBox flyAnimField;

    // AI Param fields
    private EditBox goalParam1Field;
    private EditBox goalParam2Field;
    private EditBox goalParam3Field;
    private EditBox goalParam4Field;
    private EditBox goalParam5Field;
    private EditBox goalParam6Field;
    private EditBox goalParam7Field;
    private EditBox goalParam8Field;

    // Model tab animation speed slider state
    private float animSpeedSlider = 0.5f; // maps to range 0.5x to 2.0x
    private float modelScaleSlider = 0.1837f; // maps 0.1 to 5.0 (default 1.0)
    private float hitboxWidthSlider = 0.0633f; // maps 0.1 to 8.0 (default 0.6)
    private float hitboxHeightSlider = 0.2152f; // maps 0.1 to 8.0 (default 1.8)

    // AI Tab state
    private int selectedGoalIndex = -1;
    private boolean isDraggingGoal = false;
    private int draggedGoalIndex = -1;
    private EditBox goalAnimationField;
    private EditBox goalDelayField;
    private EditBox goalGroupField;
    private int activeGoalsScroll = 0;
    private int availableBehaviorsScroll = 0;
    private int lootScroll = 0;
    private int aiParamsScroll = 0;
    private boolean isDraggingAiConfigScroll = false;

    // Tooltip overlay
    private List<Component> hoveredTooltip = null;
    private ItemStack hoveredItemTooltip = ItemStack.EMPTY;

    private static final String[] ALL_ABILITIES = {
        "Fireproof Scales", "Gills of the Deep", "Traction Tread", "Feather Light", "Rejuvenation Aura",
        "Deep Diver", "Step Assist", "Shadow Camouflage", "Thorn Guard", "Photosynthesis",
        "Toxic Secretions", "Glacial Aura", "Magnetosphere", "Night Eyes", "Reinforced Hide",
        "Spider Climb", "Flame Breath", "Tail Sweep", "Venomous Bite", "Sonic Screech",
        "Thunder Stomp", "Frost Nova", "Blight Spit", "Healing Touch", "Sonic Dash",
        "Abyssal Stealth", "High Jump", "Wind Glide", "Frightening Roar", "Lightning Strike",
        "Spore Blast", "Iron Wall", "Infernal Charge", "Life Steal Bite", "Teleport Dash",
        "Aqua Propulsion", "Trample", "Feather Hover"
    };
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
    private int abilitiesScrollOffset = 0;
    private boolean showPassiveAbilities = false;

    private static final Map<String, String> ABILITY_DESCS = new HashMap<>();
    static {
        ABILITY_DESCS.put("Fireproof Scales", "Immune to fire and lava damage.");
        ABILITY_DESCS.put("Gills of the Deep", "Can breathe underwater infinitely.");
        ABILITY_DESCS.put("Traction Tread", "Moves faster on slippery ice blocks.");
        ABILITY_DESCS.put("Feather Light", "Immune to all fall damage.");
        ABILITY_DESCS.put("Rejuvenation Aura", "Slowly heals nearby allies in combat.");
        ABILITY_DESCS.put("Deep Diver", "Increases swim and navigation speed underwater.");
        ABILITY_DESCS.put("Step Assist", "Allows stepping up full blocks without jumping.");
        ABILITY_DESCS.put("Shadow Camouflage", "Blends into dark light levels to reduce visibility.");
        ABILITY_DESCS.put("Thorn Guard", "Reflects a percentage of incoming melee damage.");
        ABILITY_DESCS.put("Photosynthesis", "Heals health slowly when exposed to direct sunlight.");
        ABILITY_DESCS.put("Toxic Secretions", "Poisons attackers on physical melee contact.");
        ABILITY_DESCS.put("Glacial Aura", "Creates a frost aura that slows down nearby targets.");
        ABILITY_DESCS.put("Magnetosphere", "Pulls nearby dropped items towards itself.");
        ABILITY_DESCS.put("Night Eyes", "Gains perfect sight inside dark caves.");
        ABILITY_DESCS.put("Reinforced Hide", "Reduces all incoming physical damage by 15%.");
        ABILITY_DESCS.put("Spider Climb", "Allows climbing up vertical walls.");
        ABILITY_DESCS.put("Flame Breath", "Shoots a cone of fire breath dealing fire damage.");
        ABILITY_DESCS.put("Tail Sweep", "Knocks back multiple surrounding attackers.");
        ABILITY_DESCS.put("Venomous Bite", "Bites targets and inflicts poison effect.");
        ABILITY_DESCS.put("Sonic Screech", "Deals area shockwave damage on screech.");
        ABILITY_DESCS.put("Thunder Stomp", "Stomps ground to summon lightning spark triggers.");
        ABILITY_DESCS.put("Frost Nova", "Emits a freezing wave immobilizing nearby targets.");
        ABILITY_DESCS.put("Blight Spit", "Spits decaying projectiles causing wither.");
        ABILITY_DESCS.put("Healing Touch", "Heals targeted companion mob for 10 HP.");
        ABILITY_DESCS.put("Sonic Dash", "Dashes forward at lightning speed.");
        ABILITY_DESCS.put("Abyssal Stealth", "Invisibility in dark surroundings.");
        ABILITY_DESCS.put("High Jump", "Leaps high into the air.");
        ABILITY_DESCS.put("Wind Glide", "Glides down slowly from high areas.");
        ABILITY_DESCS.put("Frightening Roar", "Roars to weaken surrounding enemies.");
        ABILITY_DESCS.put("Lightning Strike", "Calls down lightning on target.");
        ABILITY_DESCS.put("Spore Blast", "Releases toxic spore gas cloud.");
        ABILITY_DESCS.put("Iron Wall", "Reduces incoming damage by 80% for 3 seconds.");
        ABILITY_DESCS.put("Infernal Charge", "Burns and charges through target paths.");
        ABILITY_DESCS.put("Life Steal Bite", "Steals health from target on bite.");
        ABILITY_DESCS.put("Teleport Dash", "Teleports behind target instantly.");
        ABILITY_DESCS.put("Aqua Propulsion", "Boosts swimming velocity underwater.");
        ABILITY_DESCS.put("Trample", "Crushes crops and steps on targets.");
        ABILITY_DESCS.put("Feather Hover", "Allows hovering in air for a short duration.");
    }

    private static final String[] POSSIBLE_GOALS = {
        "MELEE", "MELEE_2", "MELEE_3", "MELEE_4", "MELEE_5", "MELEE_6", "RANGED", "WANDER", "TARGET_PLAYER", "TARGET_REVENGE", 
        "DELAY", "RETURN_TO_SPAWN", "HEAL_ALLIES", "LEAP_ATTACK", 
        "STALK", "SEARCH", "AVOID_LIGHT", "AVOID_PLAYER_WEARING", 
        "AVOID_MOB", "AVOID_GROUP", "ATTACK_OTHERS", "TARGET_GROUP", "USE_ABILITY",
        "EXPLODE_ON_DEATH", "TELEPORT_ON_HIT", "FLY_HOVER", "PANIC_ON_FIRE", 
        "SWIM_UNDERWATER", "LOOK_AT_PLAYER", "FLEE_SUN", "BURN_IN_SUN",
        "SUMMON_MINIONS", "TELEPORT_BEHIND_TARGET", "PULL_TARGET", "RAGE_MODE",
        "AMBUSH", "FIRE_TRAIL", "FROST_TOUCH", "DISARM_STRIKE", "LIGHTNING_STRIKE",
        "GIFT_GIVER", "CALL_HELP", "STEAL_ITEM", "BURROW", "IMITATE_SOUNDS",
        "SUMMON_GROUND_ATTACK", "AERIAL_RANGED_ATTACK", 
        "MELEE_AOE", "MELEE_AOE_2", "MELEE_AOE_3", "MELEE_AOE_4",
        "SUMMON_GROUND_ATTACK_AOE", "SUMMON_GROUND_ATTACK_AOE_2", "SUMMON_GROUND_ATTACK_AOE_3", "SUMMON_GROUND_ATTACK_AOE_4",
        "AERIAL_RANGED_AOE", "AERIAL_RANGED_AOE_2", "AERIAL_RANGED_AOE_3", "AERIAL_RANGED_AOE_4",
        "SHOTGUN_ATTACK", "ORBITING_SHIELD",
        "KNOCKBACK_ATTACK", "KNOCKBACK_ATTACK_2",
        "SUMMON_GROUND_LINE", "SUMMON_AERIAL_LINE", "SUMMON_GROUND_LINES", "SUMMON_AERIAL_LINES",
        "SUMMON_GROUND_LINE_LAYERED", "SUMMON_AERIAL_LINE_LAYERED", "SUMMON_GROUND_LINES_LAYERED", "SUMMON_AERIAL_LINES_LAYERED",
        "SUMMON_GROUND_SPIRAL", "SUMMON_AERIAL_SPIRAL", "SUMMON_GROUND_SPIRALS", "SUMMON_AERIAL_SPIRALS",
        "SUMMON_GROUND_SPIRAL_LAYERED", "SUMMON_AERIAL_SPIRAL_LAYERED", "SUMMON_GROUND_SPIRALS_LAYERED", "SUMMON_AERIAL_SPIRALS_LAYERED",
        "SUMMON_DOME",
        "SUMMON_GROUND_CROSS", "SUMMON_AERIAL_CROSS", "SUMMON_GROUND_CROSS_LAYERED", "SUMMON_AERIAL_CROSS_LAYERED",
        "SUMMON_GROUND_X", "SUMMON_AERIAL_X", "SUMMON_GROUND_X_LAYERED", "SUMMON_AERIAL_X_LAYERED",
        "SUMMON_GROUND_SHOCKWAVE", "SUMMON_AERIAL_SHOCKWAVE", "SUMMON_GROUND_SHOCKWAVE_LAYERED", "SUMMON_AERIAL_SHOCKWAVE_LAYERED",
        "SUMMON_GROUND_CRUNCH", "SUMMON_AERIAL_CRUNCH", "SUMMON_GROUND_CRUNCH_LAYERED", "SUMMON_AERIAL_CRUNCH_LAYERED",
        "SUMMON_TETHER_DRAIN", "SUMMON_CHASE_SNAKE", "SUMMON_GALE_VORTEX_PULL", "SUMMON_GALE_VORTEX_PUSH",
        "SUMMON_MINION_PORTAL", "SPAWN_MINIONS", "STAGGER"
    };

    private static final Map<String, String> GOAL_DESCS = new HashMap<>();
    static {
        java.util.Arrays.sort(POSSIBLE_GOALS);
        GOAL_DESCS.put("SUMMON_GROUND_LINE", "Summons ground projectiles in a line towards the target.");
        GOAL_DESCS.put("SUMMON_AERIAL_LINE", "Summons aerial projectiles in a line towards the target.");
        GOAL_DESCS.put("SUMMON_GROUND_LINES", "Summons ground projectiles in multiple diverging lines.");
        GOAL_DESCS.put("SUMMON_AERIAL_LINES", "Summons aerial projectiles in multiple diverging lines.");
        GOAL_DESCS.put("SUMMON_GROUND_LINE_LAYERED", "Summons ground projectiles sequentially in a line.");
        GOAL_DESCS.put("SUMMON_AERIAL_LINE_LAYERED", "Summons aerial projectiles sequentially in a line.");
        GOAL_DESCS.put("SUMMON_GROUND_LINES_LAYERED", "Summons ground projectiles sequentially in multiple lines.");
        GOAL_DESCS.put("SUMMON_AERIAL_LINES_LAYERED", "Summons aerial projectiles sequentially in multiple lines.");
        GOAL_DESCS.put("SUMMON_GROUND_SPIRAL", "Summons ground projectiles in a spiral pattern.");
        GOAL_DESCS.put("SUMMON_AERIAL_SPIRAL", "Summons aerial projectiles in a spiral pattern.");
        GOAL_DESCS.put("SUMMON_GROUND_SPIRALS", "Summons ground projectiles in multiple spiral lines.");
        GOAL_DESCS.put("SUMMON_AERIAL_SPIRALS", "Summons aerial projectiles in multiple spiral lines.");
        GOAL_DESCS.put("SUMMON_GROUND_SPIRAL_LAYERED", "Summons ground projectiles sequentially in a spiral pattern.");
        GOAL_DESCS.put("SUMMON_AERIAL_SPIRAL_LAYERED", "Summons aerial projectiles sequentially in a spiral pattern.");
        GOAL_DESCS.put("SUMMON_GROUND_SPIRALS_LAYERED", "Summons ground projectiles sequentially in multiple spirals.");
        GOAL_DESCS.put("SUMMON_AERIAL_SPIRALS_LAYERED", "Summons aerial projectiles sequentially in multiple spirals.");
        GOAL_DESCS.put("SUMMON_DOME", "Summons a defensive dome ring of projectiles around itself.");
        GOAL_DESCS.put("SUMMON_GROUND_CROSS", "Summons ground projectiles in a cross (+) around target.");
        GOAL_DESCS.put("SUMMON_AERIAL_CROSS", "Summons aerial projectiles in a cross (+) around target.");
        GOAL_DESCS.put("SUMMON_GROUND_CROSS_LAYERED", "Summons ground projectiles sequentially expanding in a cross (+).");
        GOAL_DESCS.put("SUMMON_AERIAL_CROSS_LAYERED", "Summons aerial projectiles sequentially expanding in a cross (+).");
        GOAL_DESCS.put("SUMMON_GROUND_X", "Summons ground projectiles in an X pattern around target.");
        GOAL_DESCS.put("SUMMON_AERIAL_X", "Summons aerial projectiles in an X pattern around target.");
        GOAL_DESCS.put("SUMMON_GROUND_X_LAYERED", "Summons ground projectiles sequentially expanding in an X pattern.");
        GOAL_DESCS.put("SUMMON_AERIAL_X_LAYERED", "Summons aerial projectiles sequentially expanding in an X pattern.");
        GOAL_DESCS.put("SUMMON_GROUND_SHOCKWAVE", "Summons ground projectiles expanding in a concentric shockwave ring.");
        GOAL_DESCS.put("SUMMON_AERIAL_SHOCKWAVE", "Summons aerial projectiles expanding in a concentric shockwave ring.");
        GOAL_DESCS.put("SUMMON_GROUND_SHOCKWAVE_LAYERED", "Summons ground projectiles sequentially in expanding concentric rings.");
        GOAL_DESCS.put("SUMMON_AERIAL_SHOCKWAVE_LAYERED", "Summons aerial projectiles sequentially in expanding concentric rings.");
        GOAL_DESCS.put("SUMMON_GROUND_CRUNCH", "Summons ground projectiles closing in on target's feet.");
        GOAL_DESCS.put("SUMMON_AERIAL_CRUNCH", "Summons aerial projectiles closing in on target.");
        GOAL_DESCS.put("SUMMON_GROUND_CRUNCH_LAYERED", "Summons ground projectiles sequentially closing in on target.");
        GOAL_DESCS.put("SUMMON_AERIAL_CRUNCH_LAYERED", "Summons aerial projectiles sequentially closing in on target.");
        GOAL_DESCS.put("SUMMON_TETHER_DRAIN", "Siphons health, slows players, and heals boss via a tether link.");
        GOAL_DESCS.put("SUMMON_CHASE_SNAKE", "Spawns homing trail of projectile explosions chasing target's feet.");
        GOAL_DESCS.put("SUMMON_GALE_VORTEX_PULL", "Summons vortex pull hazard that sucks entities in and damages them.");
        GOAL_DESCS.put("SUMMON_GALE_VORTEX_PUSH", "Summons vortex push hazard that repels entities and damages them.");
        GOAL_DESCS.put("SUMMON_MINION_PORTAL", "Summons a minion portal entity at a nearby location.");
        GOAL_DESCS.put("SPAWN_MINIONS", "Minion portal AI logic that spawns mini servants up to a maximum count.");
        GOAL_DESCS.put("STAGGER", "Vulnerability state triggered at HP threshold, freezing actions and multiplying taken damage.");

        GOAL_DESCS.put("KNOCKBACK_ATTACK", "Deals melee strikes with massive knockback distance.");
        GOAL_DESCS.put("KNOCKBACK_ATTACK_2", "Deals second melee strikes with massive knockback distance.");
        GOAL_DESCS.put("MELEE", "Attacks targets using melee strikes.");
        GOAL_DESCS.put("MELEE_2", "Attacks targets using second melee strikes (cooldowns apply).");
        GOAL_DESCS.put("MELEE_3", "Attacks targets using third melee strikes.");
        GOAL_DESCS.put("MELEE_4", "Attacks targets using fourth melee strikes.");
        GOAL_DESCS.put("MELEE_5", "Attacks targets using fifth melee strikes.");
        GOAL_DESCS.put("MELEE_6", "Attacks targets using sixth melee strikes.");
        GOAL_DESCS.put("RANGED", "Shoots custom projectiles from a distance.");
        GOAL_DESCS.put("SUMMON_GROUND_ATTACK", "Summons ground projectile directly at target.");
        GOAL_DESCS.put("AERIAL_RANGED_ATTACK", "Rains projectiles from above the target.");
        GOAL_DESCS.put("MELEE_AOE", "Sweeps in a frontal cone hitting multiple targets.");
        GOAL_DESCS.put("MELEE_AOE_2", "Sweeps in a frontal cone hitting multiple targets (cooldowns apply).");
        GOAL_DESCS.put("MELEE_AOE_3", "Sweeps in a frontal cone hitting multiple targets.");
        GOAL_DESCS.put("MELEE_AOE_4", "Sweeps in a frontal cone hitting multiple targets.");
        GOAL_DESCS.put("SUMMON_GROUND_ATTACK_AOE", "Summons ground projectiles at all entities inside a radius.");
        GOAL_DESCS.put("SUMMON_GROUND_ATTACK_AOE_2", "Summons ground projectiles at all entities inside a radius (cooldowns apply).");
        GOAL_DESCS.put("SUMMON_GROUND_ATTACK_AOE_3", "Summons ground projectiles at all entities inside a radius.");
        GOAL_DESCS.put("SUMMON_GROUND_ATTACK_AOE_4", "Summons ground projectiles at all entities inside a radius.");
        GOAL_DESCS.put("AERIAL_RANGED_AOE", "Rains projectiles across a circular zone centered on target.");
        GOAL_DESCS.put("AERIAL_RANGED_AOE_2", "Rains projectiles across a circular zone centered on target (cooldowns apply).");
        GOAL_DESCS.put("AERIAL_RANGED_AOE_3", "Rains projectiles across a circular zone centered on target.");
        GOAL_DESCS.put("AERIAL_RANGED_AOE_4", "Rains projectiles across a circular zone centered on target.");
        GOAL_DESCS.put("SHOTGUN_ATTACK", "Fires multiple projectiles in a spread fan.");
        GOAL_DESCS.put("ORBITING_SHIELD", "Summons orbiting projectiles around itself.");
        GOAL_DESCS.put("WANDER", "Wanders around randomly when idle.");
        GOAL_DESCS.put("TARGET_PLAYER", "Targets and attacks nearby players.");
        GOAL_DESCS.put("TARGET_REVENGE", "Targets entities that attack it first.");
        GOAL_DESCS.put("DELAY", "Pauses actions for 3 seconds.");
        GOAL_DESCS.put("RETURN_TO_SPAWN", "Returns back to spawn coordinates.");
        GOAL_DESCS.put("HEAL_ALLIES", "Heals nearby allies in range.");
        GOAL_DESCS.put("LEAP_ATTACK", "Leaps at targets from a distance.");
        GOAL_DESCS.put("STALK", "Stalks players from shadows before attacking.");
        GOAL_DESCS.put("SEARCH", "Searches last known location of players.");
        GOAL_DESCS.put("AVOID_LIGHT", "Flee from high light levels.");
        GOAL_DESCS.put("AVOID_PLAYER_WEARING", "Flees from players wearing specific items.");
        GOAL_DESCS.put("AVOID_MOB", "Flees from specified mob registry IDs.");
        GOAL_DESCS.put("AVOID_GROUP", "Avoids groups of surrounding entities.");
        GOAL_DESCS.put("ATTACK_OTHERS", "Attacks all other mobs except its group.");
        GOAL_DESCS.put("TARGET_GROUP", "Targets and attacks designated mob groups.");
        GOAL_DESCS.put("USE_ABILITY", "Uses mapped RPG Mounts abilities in combat.");
        GOAL_DESCS.put("EXPLODE_ON_DEATH", "Triggers explosion when the mob dies.");
        GOAL_DESCS.put("TELEPORT_ON_HIT", "Has a chance to teleport when hurt.");
        GOAL_DESCS.put("FLY_HOVER", "Enables hovering and flying behaviors.");
        GOAL_DESCS.put("PANIC_ON_FIRE", "Runs around frantically when on fire.");
        GOAL_DESCS.put("SWIM_UNDERWATER", "Allows swimming and breathing underwater.");
        GOAL_DESCS.put("LOOK_AT_PLAYER", "Tracks the nearest player with its head.");
        GOAL_DESCS.put("FLEE_SUN", "Searches for shade/cover during day.");
        GOAL_DESCS.put("BURN_IN_SUN", "Ignites under direct daylight.");
        GOAL_DESCS.put("SUMMON_MINIONS", "Periodically summons mini servants to help in combat.");
        GOAL_DESCS.put("TELEPORT_BEHIND_TARGET", "Periodically teleports directly behind its combat target.");
        GOAL_DESCS.put("PULL_TARGET", "Repeatedly pulls target towards it with vertical Y lift.");
        GOAL_DESCS.put("RAGE_MODE", "Gains damage and speed boosts when health drops below threshold.");
        GOAL_DESCS.put("AMBUSH", "Becomes invisible and stalks targets before launching attacks.");
        GOAL_DESCS.put("FIRE_TRAIL", "Leaves temporary fire blocks behind while moving in combat.");
        GOAL_DESCS.put("FROST_TOUCH", "Attacks freeze and slow down targets.");
        GOAL_DESCS.put("DISARM_STRIKE", "Attacks have a chance to disarm the player's weapon.");
        GOAL_DESCS.put("LIGHTNING_STRIKE", "Chance to strike target with lightning on hit.");
        GOAL_DESCS.put("GIFT_GIVER", "Periodically gifts a random drop pool item to players.");
        GOAL_DESCS.put("CALL_HELP", "Rallies all nearby allies in the same group to target attacker.");
        GOAL_DESCS.put("STEAL_ITEM", "Chance to steal an item from player inventory on attack.");
        GOAL_DESCS.put("BURROW", "Burrows into the ground and teleports away when hurt.");
        GOAL_DESCS.put("IMITATE_SOUNDS", "Periodically plays sounds of nearby mobs to confuse players.");
    }

    private CustomMobEntity previewEntity;

    public MobCreatorScreen() {
        super(Component.literal("Custom Mobs Creator"));
        BuiltInRegistries.ITEM.keySet().forEach(res -> {
            var item = BuiltInRegistries.ITEM.get(res);
            if (item != Items.AIR) selectorItems.add(new ItemStack(item));
        });
    }

    @Override
    protected void init() {
        this.lastModelType = "";
        this.lastModelId = "";
        this.lastTexturePath = "";
        this.lastAnimationPath = "";

        mobTemplates.clear();
        for (var m : MobRegistry.loadedMobs.values()) {
            if (!m.id.startsWith("__proj_preview_")) {
                mobTemplates.add(m);
            }
        }
        if (selectedMob != null) {
            MobData fresh = MobRegistry.loadedMobs.get(selectedMob.id);
            if (fresh != null) {
                selectedMob = fresh;
            } else if (selectedMob.id.startsWith("new_mob_")) {
                // Keep unsaved newly created template
            } else if (!mobTemplates.isEmpty()) {
                selectedMob = mobTemplates.get(0);
            } else {
                selectedMob = new MobData();
                selectedMob.id = "new_mob";
                selectedMob.name = "New Mob";
            }
        } else {
            if (!mobTemplates.isEmpty()) {
                selectedMob = mobTemplates.get(0);
            } else {
                selectedMob = new MobData();
                selectedMob.id = "new_mob";
                selectedMob.name = "New Mob";
            }
        }

        this.panelW = (int) (this.width * 0.9);
        this.panelH = (int) (this.height * 0.85);
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;

        int formX = left + 120;
        int formY = top + 42;
        int formW = (int) ((panelW - 130) * 0.6);
        int fieldW = formW - 130;
        int leftW = formW / 2 - 5;

        // General
        this.nameField = new EditBox(this.font, formX + 114, formY + 18, fieldW - 8, 10, Component.literal("Name"));
        this.nameField.setValue(selectedMob.name);
        this.nameField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.name")));

        this.mobGroupField = new EditBox(this.font, formX + 114, formY + 33, fieldW - 8, 10, Component.literal("Mob Group"));
        this.mobGroupField.setValue(selectedMob.mobGroup != null ? selectedMob.mobGroup : "");
        this.mobGroupField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.mob_group")));

        this.tamingChanceField = new EditBox(this.font, formX + 114, formY + 123, 50, 10, Component.literal("Taming Chance"));
        this.tamingChanceField.setValue(String.valueOf(selectedMob.tamingChance));
        this.tamingChanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.taming_chance")));

        this.loreField = new net.minecraft.client.gui.components.MultiLineEditBox(this.font, formX + 114, formY + 138, fieldW - 8, 30, Component.literal(""), Component.literal("Lore Description"));
        this.loreField.setValue(selectedMob.loreText != null ? selectedMob.loreText : "");
        this.loreField.setCharacterLimit(2048);
        this.loreField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.lore")));

        // Model fields
        this.modelIdField = new EditBox(this.font, formX + 114, formY + 39, fieldW - 8, 10, Component.literal("Model ID"));
        this.modelIdField.setValue(selectedMob.modelId);
        this.modelIdField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.model_id")));
        this.modelIdField.setResponder(val -> {
            if (selectedMob != null) {
                selectedMob.modelId = val;
                if (selectedMob.modelType.equals("geckolib") || selectedMob.modelType.equals("java")) {
                    this.textureField.setValue(val);
                }
                if (selectedMob.modelType.equals("geckolib")) {
                    this.animField.setValue(val + ".animation.json");
                }
                selectedMob.resetDimensions();
                selectedMob.hitboxWidth = selectedMob.getModelWidth();
                selectedMob.hitboxHeight = selectedMob.getModelHeight();
                this.hitboxWidthSlider = (float) ((selectedMob.hitboxWidth - 0.1f) / 7.9f);
                this.hitboxHeightSlider = (float) ((selectedMob.hitboxHeight - 0.1f) / 7.9f);
                rebuildPreviewEntity();
            }
        });

        this.textureField = new EditBox(this.font, formX + 114, formY + 57, fieldW - 8, 10, Component.literal("Texture Path"));
        this.textureField.setValue(selectedMob.texturePath);
        this.textureField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.texture_path")));

        this.animField = new EditBox(this.font, formX + 114, formY + 75, fieldW - 8, 10, Component.literal("Animation Path"));
        this.animField.setValue(selectedMob.animationPath);
        this.animField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.anim_path")));

        this.animSpeedSlider = (float) ((selectedMob.stats.animSpeed - 0.5) / 1.5);
        this.modelScaleSlider = (float) ((selectedMob.scale - 0.1f) / 4.9f);
        this.hitboxWidthSlider = (float) ((selectedMob.hitboxWidth - 0.1f) / 7.9f);
        this.hitboxHeightSlider = (float) ((selectedMob.hitboxHeight - 0.1f) / 7.9f);

        // Stats
        int statsY = formY + 8;
        int ySpacing = 13;
        int statsMaxW = 0;
        java.util.List<Component> statsLabels = java.util.List.of(
            Component.translatable("gui.custom_mobs.creator.label.health"),
            Component.translatable("gui.custom_mobs.creator.label.speed"),
            Component.translatable("gui.custom_mobs.creator.label.follow_range"),
            Component.translatable("gui.custom_mobs.creator.label.damage"),
            Component.translatable("gui.custom_mobs.creator.label.armor"),
            Component.translatable("gui.custom_mobs.creator.label.attack_speed"),
            Component.translatable("gui.custom_mobs.creator.label.attack_reach"),
            Component.translatable("gui.custom_mobs.creator.label.kb_resistance"),
            Component.translatable("gui.custom_mobs.creator.label.kb_inflicted"),
            Component.translatable("gui.custom_mobs.creator.label.regen_speed"),
            Component.translatable("gui.custom_mobs.creator.label.step_height"),
            Component.translatable("gui.custom_mobs.creator.label.fall_res"),
            Component.translatable("gui.custom_mobs.creator.label.reflection_chance")
        );
        for (Component lbl : statsLabels) {
            statsMaxW = Math.max(statsMaxW, this.font.width(lbl));
        }
        int statsLeftOffset = Math.max(124, 10 + statsMaxW + 8);
        int statsFieldW = formW - statsLeftOffset - 24;

        this.healthField = new EditBox(this.font, formX + statsLeftOffset, statsY + 3, statsFieldW, 9, Component.literal("Health"));
        this.healthField.setValue(String.valueOf(selectedMob.stats.maxHealth));
        this.healthField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.health")));

        this.speedField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing + 3, statsFieldW, 9, Component.literal("Speed"));
        this.speedField.setValue(String.valueOf(selectedMob.stats.movementSpeed));
        this.speedField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.speed")));

        this.followRangeField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 2 + 3, statsFieldW, 9, Component.literal("Follow Range"));
        this.followRangeField.setValue(String.valueOf(selectedMob.stats.followRange));
        this.followRangeField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.follow_range")));

        this.damageField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 3 + 3, statsFieldW, 9, Component.literal("Damage"));
        this.damageField.setValue(String.valueOf(selectedMob.stats.attackDamage));
        this.damageField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.damage")));

        this.armorField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 4 + 3, statsFieldW, 9, Component.literal("Armor"));
        this.armorField.setValue(String.valueOf(selectedMob.stats.armor));
        this.armorField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.armor")));

        this.attackSpeedField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 5 + 3, statsFieldW, 9, Component.literal("Attack Speed"));
        this.attackSpeedField.setValue(String.valueOf(selectedMob.stats.attackSpeed));
        this.attackSpeedField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.attack_speed")));

        this.attackReachField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 6 + 3, statsFieldW, 9, Component.literal("Reach"));
        this.attackReachField.setValue(String.valueOf(selectedMob.stats.attackReach));
        this.attackReachField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.attack_reach")));

        this.knockbackResistanceField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 7 + 3, statsFieldW, 9, Component.literal("KB Resistance"));
        this.knockbackResistanceField.setValue(String.valueOf(selectedMob.stats.knockbackResistance));
        this.knockbackResistanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.kb_resistance")));

        this.knockbackInflictedField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 8 + 3, statsFieldW, 9, Component.literal("KB Inflicted"));
        this.knockbackInflictedField.setValue(String.valueOf(selectedMob.stats.knockbackInflicted));
        this.knockbackInflictedField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.kb_inflicted")));

        this.regenSpeedField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 9 + 3, statsFieldW, 9, Component.literal("Regen Speed"));
        this.regenSpeedField.setValue(String.valueOf(selectedMob.stats.regenSpeed));
        this.regenSpeedField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.regen_speed")));

        this.stepHeightField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 10 + 3, statsFieldW, 9, Component.literal("Step Height"));
        this.stepHeightField.setValue(String.valueOf(selectedMob.stats.stepHeight));
        this.stepHeightField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.step_height")));

        this.fallResField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 11 + 3, statsFieldW, 9, Component.literal("Fall Res"));
        this.fallResField.setValue(String.valueOf(selectedMob.stats.fallDamageResistance));
        this.fallResField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.fall_res")));

        this.reflectionChanceField = new EditBox(this.font, formX + statsLeftOffset, statsY + ySpacing * 12 + 3, statsFieldW, 9, Component.literal("Reflection"));
        this.reflectionChanceField.setValue(String.valueOf(selectedMob.stats.projectileReflectionChance));
        this.reflectionChanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.reflection_chance")));

        // Sounds
        int soundsMaxW = 0;
        java.util.List<Component> soundsLabels = java.util.List.of(
            Component.translatable("gui.custom_mobs.creator.label.sound.ambient"),
            Component.translatable("gui.custom_mobs.creator.label.sound.step"),
            Component.translatable("gui.custom_mobs.creator.label.sound.hurt"),
            Component.translatable("gui.custom_mobs.creator.label.sound.death"),
            Component.translatable("gui.custom_mobs.creator.label.sound.attack")
        );
        for (Component lbl : soundsLabels) {
            soundsMaxW = Math.max(soundsMaxW, this.font.width(lbl));
        }
        int soundsLeftOffset = Math.max(114, 10 + soundsMaxW + 8);
        int soundsFieldW = formW - soundsLeftOffset - 26; // Leaves 26px for the play preview button

        this.ambientSoundField = new EditBox(this.font, formX + soundsLeftOffset, formY + 11, soundsFieldW, 10, Component.literal("Ambient"));
        this.ambientSoundField.setValue(selectedMob.sounds.ambient);
        this.ambientSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.ambient_sound")));

        this.stepSoundField = new EditBox(this.font, formX + soundsLeftOffset, formY + 36, soundsFieldW, 10, Component.literal("Step"));
        this.stepSoundField.setValue(selectedMob.sounds.step);
        this.stepSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.step_sound")));

        this.hurtSoundField = new EditBox(this.font, formX + soundsLeftOffset, formY + 61, soundsFieldW, 10, Component.literal("Hurt"));
        this.hurtSoundField.setValue(selectedMob.sounds.hurt);
        this.hurtSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.hurt_sound")));

        this.deathSoundField = new EditBox(this.font, formX + soundsLeftOffset, formY + 86, soundsFieldW, 10, Component.literal("Death"));
        this.deathSoundField.setValue(selectedMob.sounds.death);
        this.deathSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.death_sound")));

        this.attackSoundField = new EditBox(this.font, formX + soundsLeftOffset, formY + 111, soundsFieldW, 10, Component.literal("Attack"));
        this.attackSoundField.setValue(selectedMob.sounds.attack);
        this.attackSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.attack_sound")));

        // Loot
        int lootMaxW = this.font.width(Component.translatable("gui.custom_mobs.creator.label.xp_reward"));
        int lootLeftOffset = Math.max(114, 10 + lootMaxW + 8);
        int lootFieldW = formW - lootLeftOffset - 24;

        this.xpField = new EditBox(this.font, formX + lootLeftOffset, formY + 23, lootFieldW, 10, Component.literal("XP"));
        this.xpField.setValue(String.valueOf(selectedMob.loot.xpReward));
        this.xpField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.xp")));

        // Spawning rules
        int spawnLeftOffset = getSpawnLeftOffset();
        int spawnY = formY + 87;
        this.minHeightField = new EditBox(this.font, formX + spawnLeftOffset, spawnY, 40, 9, Component.literal("Min Height"));
        this.minHeightField.setValue(String.valueOf(selectedMob.spawnRules.minHeight));
        this.maxHeightField = new EditBox(this.font, formX + spawnLeftOffset + 55, spawnY, 40, 9, Component.literal("Max Height"));
        this.maxHeightField.setValue(String.valueOf(selectedMob.spawnRules.maxHeight));

        int spawnFieldW = fieldW - 8 - (spawnLeftOffset - 114);
        this.spawnBlockField = new EditBox(this.font, formX + spawnLeftOffset, formY + 102, spawnFieldW, 9, Component.literal("Block"));
        this.spawnBlockField.setValue(selectedMob.spawnRules.spawnBlock);
        this.spawnBlockField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.creator.spawn_block")));

        this.minLightField = new EditBox(this.font, formX + spawnLeftOffset, formY + 117, 40, 9, Component.literal("Min Light"));
        this.minLightField.setValue(String.valueOf(selectedMob.spawnRules.minLight));
        this.maxLightField = new EditBox(this.font, formX + spawnLeftOffset + 55, formY + 117, 40, 9, Component.literal("Max Light"));
        this.maxLightField.setValue(String.valueOf(selectedMob.spawnRules.maxLight));

        this.minGroupField = new EditBox(this.font, formX + spawnLeftOffset, formY + 132, 40, 9, Component.literal("Min Group"));
        this.minGroupField.setValue(String.valueOf(selectedMob.spawnRules.minGroup));
        this.maxGroupField = new EditBox(this.font, formX + spawnLeftOffset + 55, formY + 132, 40, 9, Component.literal("Max Group"));
        this.maxGroupField.setValue(String.valueOf(selectedMob.spawnRules.maxGroup));

        this.naturalWeightField = new EditBox(this.font, formX + spawnLeftOffset, formY + 147, 55, 9, Component.literal("Weight"));
        this.naturalWeightField.setValue(String.valueOf(selectedMob.spawnRules.weight));
        this.naturalWeightField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.natural_weight")));

        this.biomeSearchField = new EditBox(this.font, formX + spawnLeftOffset, formY + 162, spawnFieldW, 9, Component.literal("Search Biome"));
        this.biomeSearchField.setValue("");
        this.biomeSearchField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.biome_search")));

        this.structureField = new EditBox(this.font, formX + spawnLeftOffset, formY + 177, spawnFieldW, 9, Component.literal("Structure"));
        this.structureField.setValue(selectedMob.spawnRules.allowedStructure != null ? selectedMob.spawnRules.allowedStructure : "");
        this.structureField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.structure")));

        // Goal mapping (aligned in left column)
        this.goalAnimationField = new EditBox(this.font, formX + 9, formY + 148, leftW - 18, 10, Component.literal("Animation"));
        this.goalAnimationField.setValue("");
        this.goalAnimationField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.goal_animation")));

        this.goalGroupField = new EditBox(this.font, formX + 9, formY + 175, leftW - 18, 10, Component.literal("Group Value"));
        this.goalGroupField.setValue("");
        this.goalGroupField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.goal_group")));

        this.goalDelayField = new EditBox(this.font, formX + 9, formY + 202, leftW - 18, 10, Component.literal("Delay Ticks"));
        this.goalDelayField.setValue("");
        this.goalDelayField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.delay_ticks")));

        // Loot item configuration fields
        this.lootChanceField = new EditBox(this.font, formX + 114, formY + 138, 50, 10, Component.literal("Loot Chance"));
        this.lootChanceField.setValue("");
        this.lootChanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.loot_chance")));

        this.lootMinField = new EditBox(this.font, formX + 114, formY + 153, 40, 10, Component.literal("Min Qty"));
        this.lootMinField.setValue("");
        this.lootMinField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.loot_min")));

        this.lootMaxField = new EditBox(this.font, formX + 169, formY + 153, 40, 10, Component.literal("Max Qty"));
        this.lootMaxField.setValue(String.valueOf(selectedLootIndex >= 0 && selectedLootIndex < selectedMob.loot.items.size() ? selectedMob.loot.items.get(selectedLootIndex).maxCount : ""));
        this.lootMaxField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.loot_max")));

        this.lootLevelField = new EditBox(this.font, formX + 169, formY + 170, 40, 10, Component.literal("Looting Level"));
        this.lootLevelField.setValue("");
        this.lootLevelField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.loot_level")));

        // Animations tab initializations
        int animsMaxW = 0;
        java.util.List<Component> animsLabels = java.util.List.of(
            Component.translatable("gui.custom_mobs.creator.label.idle_anim"),
            Component.translatable("gui.custom_mobs.creator.label.walk_anim"),
            Component.translatable("gui.custom_mobs.creator.label.attack_anim"),
            Component.translatable("gui.custom_mobs.creator.label.death_anim"),
            Component.translatable("gui.custom_mobs.creator.label.swim_anim"),
            Component.translatable("gui.custom_mobs.creator.label.fly_anim")
        );
        for (Component lbl : animsLabels) {
            animsMaxW = Math.max(animsMaxW, this.font.width(lbl));
        }
        int animsLeftOffset = Math.max(114, 10 + animsMaxW + 8);
        int animsFieldW = formW - animsLeftOffset - 24;

        this.idleAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 23, animsFieldW, 10, Component.literal("Idle Anim"));
        this.idleAnimField.setValue(selectedMob.animations.getOrDefault("idle", ""));
        this.idleAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.idle_anim")));

        this.walkAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 41, animsFieldW, 10, Component.literal("Walk Anim"));
        this.walkAnimField.setValue(selectedMob.animations.getOrDefault("walk", ""));
        this.walkAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.walk_anim")));

        this.attackAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 59, animsFieldW, 10, Component.literal("Attack Anim"));
        this.attackAnimField.setValue(selectedMob.animations.getOrDefault("attack", ""));
        this.attackAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.attack_anim")));

        this.deathAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 77, animsFieldW, 10, Component.literal("Death Anim"));
        this.deathAnimField.setValue(selectedMob.animations.getOrDefault("death", ""));
        this.deathAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.death_anim")));

        this.swimAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 95, animsFieldW, 10, Component.literal("Swim Anim"));
        this.swimAnimField.setValue(selectedMob.animations.getOrDefault("swim", ""));
        this.swimAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.swim_anim")));

        this.flyAnimField = new EditBox(this.font, formX + animsLeftOffset, formY + 113, animsFieldW, 10, Component.literal("Fly Anim"));
        this.flyAnimField.setValue(selectedMob.animations.getOrDefault("fly", ""));
        this.flyAnimField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.fly_anim")));

        // AI Param Fields
        this.goalParam1Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 132, leftW - 25, 10, Component.literal("Param 1"));
        this.goalParam1Field.setValue("");
        this.goalParam2Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 157, leftW - 18, 10, Component.literal("Param 2"));
        this.goalParam2Field.setValue("");
        this.goalParam3Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 182, leftW - 18, 10, Component.literal("Param 3"));
        this.goalParam3Field.setValue("");
        this.goalParam4Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 207, leftW - 18, 10, Component.literal("Param 4"));
        this.goalParam4Field.setValue("");
        this.goalParam5Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 232, leftW - 18, 10, Component.literal("Param 5"));
        this.goalParam5Field.setValue("");
        this.goalParam6Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 257, leftW - 18, 10, Component.literal("Param 6"));
        this.goalParam6Field.setValue("");
        this.goalParam7Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 282, leftW - 18, 10, Component.literal("Param 7"));
        this.goalParam7Field.setValue("");
        this.goalParam8Field = new EditBox(this.font, formX + formW / 2 + 10, formY + 307, leftW - 18, 10, Component.literal("Param 8"));
        this.goalParam8Field.setValue("");

        int rightW = formW / 2 - 5;
        this.behaviorSearchField = new EditBox(this.font, formX + formW / 2 + 5, formY + 18, rightW, 12, Component.literal("Search Behavior"));
        this.behaviorSearchField.setValue("");
        this.behaviorSearchField.setResponder(s -> {
            this.availableBehaviorsScroll = 0;
            updateFilteredBehaviors();
        });
        applyBorderless(this.behaviorSearchField);
        updateFilteredBehaviors();

        // Loot item search field
        int popW = 220;
        int popX = (this.width - popW) / 2;
        int popY = (this.height - 190) / 2;
        this.itemSearchField = new EditBox(this.font, popX + 60, popY + 41, 140, 10, Component.literal("Search"));
        this.itemSearchField.setValue("");

        // Remove default white borders on EditBoxes to make them borderless and use customized styling
        applyBorderless(nameField);
        applyBorderless(mobGroupField);
        applyBorderless(tamingChanceField);
        applyBorderless(modelIdField);
        applyBorderless(textureField);
        applyBorderless(animField);
        applyBorderless(healthField);
        applyBorderless(speedField);
        applyBorderless(followRangeField);
        applyBorderless(damageField);
        applyBorderless(armorField);
        applyBorderless(attackSpeedField);
        applyBorderless(attackReachField);
        applyBorderless(knockbackResistanceField);
        applyBorderless(knockbackInflictedField);
        applyBorderless(regenSpeedField);
        applyBorderless(stepHeightField);
        applyBorderless(fallResField);
        applyBorderless(reflectionChanceField);
        applyBorderless(ambientSoundField);
        applyBorderless(stepSoundField);
        applyBorderless(hurtSoundField);
        applyBorderless(deathSoundField);
        applyBorderless(attackSoundField);
        applyBorderless(xpField);
        applyBorderless(minHeightField);
        applyBorderless(maxHeightField);
        applyBorderless(spawnBlockField);
        applyBorderless(minLightField);
        applyBorderless(maxLightField);
        applyBorderless(minGroupField);
        applyBorderless(maxGroupField);
        applyBorderless(naturalWeightField);
        applyBorderless(biomeSearchField);
        applyBorderless(structureField);
        applyBorderless(goalAnimationField);
        applyBorderless(goalGroupField);
        applyBorderless(goalDelayField);
        applyBorderless(lootChanceField);
        applyBorderless(lootMinField);
        applyBorderless(lootMaxField);
        applyBorderless(lootLevelField);
        applyBorderless(goalParam1Field);
        applyBorderless(goalParam2Field);
        applyBorderless(goalParam3Field);
        applyBorderless(goalParam4Field);
        applyBorderless(goalParam5Field);
        applyBorderless(goalParam6Field);
        applyBorderless(goalParam7Field);
        applyBorderless(goalParam8Field);

        this.addRenderableWidget(this.nameField);
        this.addRenderableWidget(this.mobGroupField);
        this.addRenderableWidget(this.tamingChanceField);
        this.addRenderableWidget(this.loreField);
        this.addRenderableWidget(this.modelIdField);
        this.addRenderableWidget(this.textureField);
        this.addRenderableWidget(this.animField);
        this.addRenderableWidget(this.healthField);
        this.addRenderableWidget(this.speedField);
        this.addRenderableWidget(this.followRangeField);
        this.addRenderableWidget(this.damageField);
        this.addRenderableWidget(this.armorField);
        this.addRenderableWidget(this.attackSpeedField);
        this.addRenderableWidget(this.attackReachField);
        this.addRenderableWidget(this.knockbackResistanceField);
        this.addRenderableWidget(this.knockbackInflictedField);
        this.addRenderableWidget(this.regenSpeedField);
        this.addRenderableWidget(this.stepHeightField);
        this.addRenderableWidget(this.fallResField);
        this.addRenderableWidget(this.reflectionChanceField);
        this.addRenderableWidget(this.ambientSoundField);
        this.addRenderableWidget(this.stepSoundField);
        this.addRenderableWidget(this.hurtSoundField);
        this.addRenderableWidget(this.deathSoundField);
        this.addRenderableWidget(this.attackSoundField);
        this.addRenderableWidget(this.xpField);
        this.addRenderableWidget(this.minHeightField);
        this.addRenderableWidget(this.maxHeightField);
        this.addRenderableWidget(this.spawnBlockField);
        this.addRenderableWidget(this.minLightField);
        this.addRenderableWidget(this.maxLightField);
        this.addRenderableWidget(this.minGroupField);
        this.addRenderableWidget(this.maxGroupField);
        this.addRenderableWidget(this.naturalWeightField);
        this.addRenderableWidget(this.biomeSearchField);
        this.addRenderableWidget(this.structureField);
        this.addRenderableWidget(this.goalAnimationField);
        this.addRenderableWidget(this.goalGroupField);
        this.addRenderableWidget(this.goalDelayField);
        this.addRenderableWidget(this.behaviorSearchField);
        this.addRenderableWidget(this.lootChanceField);
        this.addRenderableWidget(this.lootMinField);
        this.addRenderableWidget(this.lootMaxField);
        this.addRenderableWidget(this.lootLevelField);
        this.addRenderableWidget(this.idleAnimField);
        this.addRenderableWidget(this.walkAnimField);
        this.addRenderableWidget(this.attackAnimField);
        this.addRenderableWidget(this.deathAnimField);
        this.addRenderableWidget(this.swimAnimField);
        this.addRenderableWidget(this.flyAnimField);
        this.addRenderableWidget(this.goalParam1Field);
        this.addRenderableWidget(this.goalParam2Field);
        this.addRenderableWidget(this.goalParam3Field);
        this.addRenderableWidget(this.goalParam4Field);
        this.addRenderableWidget(this.goalParam5Field);
        this.addRenderableWidget(this.goalParam6Field);
        this.addRenderableWidget(this.goalParam7Field);
        this.addRenderableWidget(this.goalParam8Field);
        this.addRenderableWidget(this.itemSearchField);

        for (var child : this.children()) {
            if (child instanceof EditBox eb) {
                eb.setMaxLength(1024);
            }
        }

        hideAllFields();
        showFieldsForTab();

        rebuildPreviewEntity();
    }

    private void applyBorderless(EditBox field) {
        field.setBordered(false);
    }

    private void rebuildPreviewEntity() {
        if (selectedMob == null) return;
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            try {
                previewEntity = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(localPlayer.level());
                if (previewEntity != null) {
                    previewEntity.isPreview = true;
                    previewEntity.setTemplateId(selectedMob.id);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void tick() {
        if (showItemSelector) return;

        saveTextFieldsToActiveMob();
        MobRegistry.loadedMobs.put(selectedMob.id, selectedMob);

        String currentType = selectedMob.modelType;
        String currentModel = selectedMob.modelId;
        String currentTexture = selectedMob.texturePath;
        String currentAnim = selectedMob.animationPath;

        if (!currentType.equals(lastModelType) ||
            !currentModel.equals(lastModelId) ||
            !currentTexture.equals(lastTexturePath) ||
            !currentAnim.equals(lastAnimationPath)) {

            ddraig.net.custommobs.client.renderer.JavaModelLoader.clearCacheFor(selectedMob.id, currentModel);

            if (currentType.equalsIgnoreCase("java")) {
                ddraig.net.custommobs.client.renderer.JavaModelLoader.loadJavaModel(currentModel);
            }
            ddraig.net.custommobs.client.renderer.JavaModelLoader.loadTexture(selectedMob.id, currentModel, currentTexture);

            lastModelType = currentType;
            lastModelId = currentModel;
            lastTexturePath = currentTexture;
            lastAnimationPath = currentAnim;

            rebuildPreviewEntity();
        }

        if (previewEntity != null) {
            previewEntity.reapplyTemplate();
        }

        nameField.tick();
        mobGroupField.tick();
        tamingChanceField.tick();
        loreField.tick();
        modelIdField.tick();
        textureField.tick();
        animField.tick();
        healthField.tick();
        speedField.tick();
        followRangeField.tick();
        damageField.tick();
        armorField.tick();
        attackSpeedField.tick();
        attackReachField.tick();
        knockbackResistanceField.tick();
        knockbackInflictedField.tick();
        regenSpeedField.tick();
        stepHeightField.tick();
        fallResField.tick();
        reflectionChanceField.tick();
        ambientSoundField.tick();
        stepSoundField.tick();
        hurtSoundField.tick();
        deathSoundField.tick();
        attackSoundField.tick();
        xpField.tick();
        minHeightField.tick();
        maxHeightField.tick();
        spawnBlockField.tick();
        minLightField.tick();
        maxLightField.tick();
        minGroupField.tick();
        maxGroupField.tick();
        naturalWeightField.tick();
        biomeSearchField.tick();
        behaviorSearchField.tick();
        structureField.tick();
        goalAnimationField.tick();
        goalGroupField.tick();
        goalDelayField.tick();
        lootChanceField.tick();
        lootMinField.tick();
        lootMaxField.tick();
        lootLevelField.tick();
        idleAnimField.tick();
        walkAnimField.tick();
        attackAnimField.tick();
        deathAnimField.tick();
        swimAnimField.tick();
        flyAnimField.tick();
        goalParam1Field.tick();
        goalParam2Field.tick();
        goalParam3Field.tick();
        goalParam4Field.tick();
        goalParam5Field.tick();
        goalParam6Field.tick();
        goalParam7Field.tick();
        goalParam8Field.tick();
        itemSearchField.tick();

        try {
            EditBox focused = null;
            int yOffset = 0;
            List<String> cache = null;

            if (activeTab.equals("Model")) {
                if (modelIdField.isFocused()) {
                    if (selectedMob.modelType.equals("vanilla")) {
                        focused = modelIdField; yOffset = 39 + 10;
                        cache = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                    } else if (selectedMob.modelType.equals("geckolib") || selectedMob.modelType.equals("java")) {
                        focused = modelIdField; yOffset = 39 + 10;
                        cache = MobRegistry.cachedModels;
                    }
                } else if (textureField.isFocused()) {
                    focused = textureField; yOffset = 57 + 10;
                    cache = getTextureSuggestions();
                } else if (animField.isFocused()) {
                    focused = animField; yOffset = 75 + 10;
                    cache = getAnimationPathSuggestions();
                }
            } else if (activeTab.equals("Animations")) {
                if (idleAnimField.isFocused()) { focused = idleAnimField; yOffset = 23 + 10; cache = getAnimationNameSuggestions(); }
                else if (walkAnimField.isFocused()) { focused = walkAnimField; yOffset = 41 + 10; cache = getAnimationNameSuggestions(); }
                else if (attackAnimField.isFocused()) { focused = attackAnimField; yOffset = 59 + 10; cache = getAnimationNameSuggestions(); }
                else if (deathAnimField.isFocused()) { focused = deathAnimField; yOffset = 77 + 10; cache = getAnimationNameSuggestions(); }
                else if (swimAnimField.isFocused()) { focused = swimAnimField; yOffset = 95 + 10; cache = getAnimationNameSuggestions(); }
                else if (flyAnimField.isFocused()) { focused = flyAnimField; yOffset = 113 + 10; cache = getAnimationNameSuggestions(); }
            } else if (activeTab.equals("Sounds")) {
                if (ambientSoundField.isFocused()) { focused = ambientSoundField; yOffset = 11 + 10; }
                else if (stepSoundField.isFocused()) { focused = stepSoundField; yOffset = 36 + 10; }
                else if (hurtSoundField.isFocused()) { focused = hurtSoundField; yOffset = 61 + 10; }
                else if (deathSoundField.isFocused()) { focused = deathSoundField; yOffset = 86 + 10; }
                else if (attackSoundField.isFocused()) { focused = attackSoundField; yOffset = 111 + 10; }
                if (focused != null) cache = MobRegistry.cachedSounds;
            } else if (activeTab.equals("Spawning")) {
                if (biomeSearchField.isFocused()) {
                    focused = biomeSearchField; yOffset = 162 + 9;
                    cache = MobRegistry.cachedBiomes;
                } else if (spawnBlockField.isFocused()) {
                    focused = spawnBlockField; yOffset = 102 + 9;
                    cache = new ArrayList<>(BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::toString).toList());
                } else if (structureField.isFocused()) {
                    focused = structureField; yOffset = 177 + 9;
                    var registries = Minecraft.getInstance().level.registryAccess();
                    var structureRegistryOpt = registries.registry(net.minecraft.core.registries.Registries.STRUCTURE);
                    if (structureRegistryOpt.isPresent()) {
                        cache = new ArrayList<>(structureRegistryOpt.get().keySet().stream().map(ResourceLocation::toString).toList());
                    }
                }
            } else if (activeTab.equals("General")) {
                if (mobGroupField.isFocused()) {
                    focused = mobGroupField; yOffset = 33 + 10;
                    cache = new ArrayList<>(MobRegistry.loadedMobs.values().stream().filter(m -> !m.id.startsWith("__proj_preview_")).map(m -> m.mobGroup).filter(g -> g != null && !g.isEmpty()).distinct().toList());
                }
            } else if (activeTab.equals("AI") && selectedGoalIndex >= 0) {
                var goal = selectedMob.aiGoals.get(selectedGoalIndex);
                if (goalAnimationField.isFocused()) {
                    focused = goalAnimationField; yOffset = 148 + 10;
                    cache = getAnimationNameSuggestions();
                } else if (goalParam1Field.isFocused()) {
                    if (goal.type.equals("AVOID_PLAYER_WEARING")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        cache = new ArrayList<>(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).toList());
                    } else if (goal.type.equals("AVOID_MOB")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        List<String> list = new ArrayList<>(MobRegistry.loadedMobs.keySet().stream().filter(id -> !id.startsWith("__proj_preview_")).toList());
                        list.addAll(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                        cache = list;
                    } else if (goal.type.startsWith("MELEE") || goal.type.startsWith("KNOCKBACK") || goal.type.equals("RANGED")
                            || goal.type.startsWith("SUMMON_GROUND_ATTACK") || goal.type.startsWith("AERIAL_RANGED")
                            || goal.type.equals("SHOTGUN_ATTACK") || goal.type.equals("ORBITING_SHIELD")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        cache = MobRegistry.cachedSounds;
                    } else if (goal.type.equals("USE_ABILITY")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        List<String> list = new ArrayList<>();
                        for (var ab : selectedMob.abilities) {
                            if (!ab.isPassive) {
                                list.add(ab.name);
                            }
                        }
                        cache = list;
                    } else if (goal.type.equals("EFFECT_ON_CONTACT") || goal.type.equals("EFFECT_ON_ATTACK")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        cache = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet().stream().map(ResourceLocation::toString).toList());
                    } else if (goal.type.equals("SPLIT_ON_DEATH") || goal.type.equals("SCARE_MOB") || goal.type.equals("SUMMON_MINIONS")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        List<String> list = new ArrayList<>(MobRegistry.loadedMobs.keySet().stream().filter(id -> !id.startsWith("__proj_preview_")).toList());
                        list.addAll(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                        cache = list;
                    } else if (goal.type.equals("GIFT_GIVER")) {
                        focused = goalParam1Field; yOffset = 132 + 10;
                        List<String> list = new ArrayList<>();
                        list.add("loot");
                        list.addAll(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).toList());
                        cache = list;
                    }
                } else if (goalParam2Field.isFocused()) {
                    if (goal.type.equals("RANGED") || goal.type.startsWith("SUMMON_GROUND_ATTACK")
                            || goal.type.startsWith("AERIAL_RANGED") || goal.type.equals("SHOTGUN_ATTACK")
                            || goal.type.equals("ORBITING_SHIELD")) {
                        focused = goalParam2Field; yOffset = 157 + 10;
                        List<String> list = new ArrayList<>(MobRegistry.loadedProjectiles.keySet());
                        list.addAll(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                        cache = list;
                    } else if (goal.type.equals("EXPLODE_ON_DEATH")) {
                        focused = goalParam2Field; yOffset = 157 + 10;
                        cache = List.of("true", "false");
                    }
                } else if (goalParam3Field.isFocused()) {
                    if (goal.type.equals("EXPLODE_ON_DEATH")) {
                        focused = goalParam3Field; yOffset = 182 + 10;
                        cache = List.of("true", "false");
                    }
                } else if (goalParam4Field.isFocused()) {
                    focused = goalParam4Field; yOffset = 207 + 10;
                }
            }

            if (focused != null && cache != null) {
                String query = focused.getValue().toLowerCase();
                if (focused != lastFocusedField || !query.equals(lastQueryValue)) {
                    activeSuggestions.clear();
                    suggestionsScrollOffset = 0;
                    for (String val : cache) {
                        if (val.toLowerCase().contains(query)) activeSuggestions.add(val);
                    }
                    showSuggestions = true;
                    activeField = focused;
                    suggestionsYOffset = yOffset;

                    lastFocusedField = focused;
                    lastQueryValue = query;
                }
            } else {
                showSuggestions = false;
                activeField = null;
                lastFocusedField = null;
                lastQueryValue = "";
            }
        } catch (Exception e) {
            showSuggestions = false;
        }
    }

    private int getFilteredItemCount() {
        if (selectAllItems) {
            String query = itemSearchField.getValue().toLowerCase();
            return (int) selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).count();
        } else {
            var player = Minecraft.getInstance().player;
            int count = 0;
            if (player != null) {
                for (var stack : player.getInventory().items) {
                    if (!stack.isEmpty()) count++;
                }
            }
            return count;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (showItemSelector) {
            int totalItems = getFilteredItemCount();
            int totalRows = (int) Math.ceil((double) totalItems / 9.0);
            int maxScroll = Math.max(0, totalRows - 4);
            itemSelectorScroll = Math.max(0, Math.min(maxScroll, itemSelectorScroll - (int) amount));
            return true;
        }
        if (showSuggestions && !activeSuggestions.isEmpty()) {
            int maxScroll = Math.max(0, activeSuggestions.size() - 5);
            suggestionsScrollOffset = Math.max(0, Math.min(maxScroll, suggestionsScrollOffset - (int) amount));
            return true;
        }
        if (activeTab.equals("Abilities")) {
            String[] currentAbList = showPassiveAbilities ? PASSIVE_ABILITIES : ACTIVE_ABILITIES;
            int maxScroll = Math.max(0, currentAbList.length - 8);
            abilitiesScrollOffset = Math.max(0, Math.min(maxScroll, abilitiesScrollOffset - (int) amount));
            return true;
        }
        if (activeTab.equals("AI")) {
            int left = (this.width - this.panelW) / 2;
            int top = (this.height - this.panelH) / 2;
            int formX = left + 120;
            int formY = top + 42;
            int formW = (int) ((panelW - 130) * 0.6);
            int formH = panelH - 80;
            if (mouseY >= formY && mouseY < formY + 136) {
                if (mouseX >= formX && mouseX <= formX + formW / 2) {
                    int maxScroll = Math.max(0, selectedMob.aiGoals.size() - 5);
                    activeGoalsScroll = Math.max(0, Math.min(maxScroll, activeGoalsScroll - (int) amount));
                } else if (mouseX > formX + formW / 2 && mouseX <= formX + formW) {
                    int maxScroll = Math.max(0, filteredBehaviors.size() - 5);
                    availableBehaviorsScroll = Math.max(0, Math.min(maxScroll, availableBehaviorsScroll - (int) amount));
                }
            } else if (mouseY >= formY + 136 && mouseY <= formY + formH && selectedGoalIndex >= 0) {
                int visibleHeight = formH - 136;
                int contentHeight = getAiParamsContentHeight();
                if (visibleHeight < contentHeight) {
                    int maxScroll = contentHeight - visibleHeight;
                    aiParamsScroll = Math.max(0, Math.min(maxScroll, aiParamsScroll - (int) (amount * 12)));
                    updateAIFieldsY();
                }
            }
            return true;
        }
        if (activeTab.equals("Loot")) {
            int maxScroll = Math.max(0, selectedMob.loot.items.size() - 3);
            lootScroll = Math.max(0, Math.min(maxScroll, lootScroll - (int) amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void openItemSelector(java.util.function.Consumer<ItemStack> callback) {
        this.showItemSelector = true;
        this.itemSelectorScroll = 0;
        this.itemSelectionCallback = callback;
        showFieldsForTab();
        this.itemSearchField.setFocused(true);
    }

    private void closeItemSelector() {
        this.showItemSelector = false;
        showFieldsForTab();
    }

    private void showFieldsForTab() {
        if (showItemSelector) {
            hideAllFields();
            if (selectAllItems) {
                itemSearchField.visible = true;
                itemSearchField.active = true;
            }
            return;
        }

        boolean general = activeTab.equals("General");
        nameField.visible = general; nameField.active = general;
        mobGroupField.visible = general; mobGroupField.active = general;
        tamingChanceField.visible = general && selectedMob.tameable; tamingChanceField.active = general && selectedMob.tameable;
        loreField.visible = general; loreField.active = general;

        boolean model = activeTab.equals("Model");
        boolean modelHide = model && !selectedMob.modelType.equals("mcmodel");
        modelIdField.visible = modelHide; modelIdField.active = modelHide;
        boolean modelGeckoOrJava = model && !selectedMob.modelType.equals("mcmodel") && (selectedMob.modelType.equals("geckolib") || selectedMob.modelType.equals("java"));
        textureField.visible = modelGeckoOrJava; textureField.active = modelGeckoOrJava;
        boolean modelGecko = model && !selectedMob.modelType.equals("mcmodel") && selectedMob.modelType.equals("geckolib");
        animField.visible = modelGecko; animField.active = modelGecko;

        boolean anims = activeTab.equals("Animations");
        idleAnimField.visible = anims; idleAnimField.active = anims;
        walkAnimField.visible = anims; walkAnimField.active = anims;
        attackAnimField.visible = anims; attackAnimField.active = anims;
        deathAnimField.visible = anims; deathAnimField.active = anims;
        swimAnimField.visible = anims; swimAnimField.active = anims;
        flyAnimField.visible = anims; flyAnimField.active = anims;

        boolean stats = activeTab.equals("Stats");
        healthField.visible = stats; healthField.active = stats;
        speedField.visible = stats; speedField.active = stats;
        followRangeField.visible = stats; followRangeField.active = stats;
        damageField.visible = stats; damageField.active = stats;
        armorField.visible = stats; armorField.active = stats;
        attackSpeedField.visible = stats; attackSpeedField.active = stats;
        attackReachField.visible = stats; attackReachField.active = stats;
        knockbackResistanceField.visible = stats; knockbackResistanceField.active = stats;
        knockbackInflictedField.visible = stats; knockbackInflictedField.active = stats;
        regenSpeedField.visible = stats; regenSpeedField.active = stats;
        stepHeightField.visible = stats; stepHeightField.active = stats;
        fallResField.visible = stats; fallResField.active = stats;
        reflectionChanceField.visible = stats; reflectionChanceField.active = stats;

        boolean sounds = activeTab.equals("Sounds");
        ambientSoundField.visible = sounds; ambientSoundField.active = sounds;
        stepSoundField.visible = sounds; stepSoundField.active = sounds;
        hurtSoundField.visible = sounds; hurtSoundField.active = sounds;
        deathSoundField.visible = sounds; deathSoundField.active = sounds;
        attackSoundField.visible = sounds; attackSoundField.active = sounds;

        boolean loot = activeTab.equals("Loot");
        xpField.visible = loot; xpField.active = loot;
        boolean lootSelected = loot && selectedLootIndex >= 0 && selectedLootIndex < selectedMob.loot.items.size();
        lootChanceField.visible = lootSelected; lootChanceField.active = lootSelected;
        lootMinField.visible = lootSelected; lootMinField.active = lootSelected;
        lootMaxField.visible = lootSelected; lootMaxField.active = lootSelected;

        boolean lootLevelVisible = false;
        if (lootSelected) {
            var item = selectedMob.loot.items.get(selectedLootIndex);
            lootChanceField.setValue(String.valueOf(item.chance));
            lootMinField.setValue(String.valueOf(item.minCount));
            lootMaxField.setValue(String.valueOf(item.maxCount));
            lootLevelField.setValue(String.valueOf(item.lootingLevel));
            lootLevelVisible = item.lootingRequired;
        }
        lootLevelField.visible = lootLevelVisible; lootLevelField.active = lootLevelVisible;

        boolean spawn = activeTab.equals("Spawning");
        minHeightField.visible = spawn; minHeightField.active = spawn;
        maxHeightField.visible = spawn; maxHeightField.active = spawn;
        spawnBlockField.visible = spawn; spawnBlockField.active = spawn;
        minLightField.visible = spawn; minLightField.active = spawn;
        maxLightField.visible = spawn; maxLightField.active = spawn;
        minGroupField.visible = spawn; minGroupField.active = spawn;
        maxGroupField.visible = spawn; maxGroupField.active = spawn;
        naturalWeightField.visible = spawn; naturalWeightField.active = spawn;
        biomeSearchField.visible = spawn; biomeSearchField.active = spawn;
        structureField.visible = spawn; structureField.active = spawn;

        boolean aiGoalSelected = activeTab.equals("AI") && selectedGoalIndex >= 0;
        goalAnimationField.visible = aiGoalSelected; goalAnimationField.active = aiGoalSelected;
        goalDelayField.visible = aiGoalSelected; goalDelayField.active = aiGoalSelected;
        if (behaviorSearchField != null) {
            boolean ai = activeTab.equals("AI");
            behaviorSearchField.visible = ai;
            behaviorSearchField.active = ai;
        }
        
        boolean showGroupField = aiGoalSelected && isGroupGoal(selectedMob.aiGoals.get(selectedGoalIndex).type);
        goalGroupField.visible = showGroupField; goalGroupField.active = showGroupField;

        boolean p1Visible = false;
        boolean p2Visible = false;
        boolean p3Visible = false;
        boolean p4Visible = false;
        boolean p5Visible = false;
        boolean p6Visible = false;
        boolean p7Visible = false;
        boolean p8Visible = false;

        if (aiGoalSelected) {
            goalParam1Field.setTooltip(null);
            goalParam2Field.setTooltip(null);
            goalParam3Field.setTooltip(null);
            goalParam4Field.setTooltip(null);
            goalParam5Field.setTooltip(null);
            goalParam6Field.setTooltip(null);
            goalParam7Field.setTooltip(null);
            goalParam8Field.setTooltip(null);

            var goal = selectedMob.aiGoals.get(selectedGoalIndex);
            goalAnimationField.setValue(goal.animation != null ? goal.animation : "");
            goalGroupField.setValue(goal.params.getOrDefault("group", ""));

            String type = goal.type;
            String defDelay = "0";
            if (type.equals("WANDER") || type.equals("DELAY") || type.startsWith("AERIAL_RANGED")) defDelay = "60";
            else if (type.equals("HEAL_ALLIES") || type.equals("RANGED") || type.startsWith("SUMMON_GROUND_ATTACK") || type.equals("SHOTGUN_ATTACK") || type.equals("ORBITING_SHIELD")) defDelay = "40";
            else if (type.startsWith("MELEE") || type.startsWith("KNOCKBACK")) defDelay = "20";
            goalDelayField.setValue(goal.params.getOrDefault("delay", defDelay));

            if (type.equals("WANDER")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("radius", "10"));
            } else if (type.equals("DELAY")) {
                // No parameters on right side
            } else if (type.equals("HEAL_ALLIES")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("amount", "2"));
            } else if (type.equals("AVOID_LIGHT")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("light_level", "8"));
            } else if (type.equals("AVOID_MOB")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("mobs", ""));
            } else if (type.equals("AVOID_GROUP") || type.equals("TARGET_GROUP") || type.equals("ATTACK_OTHERS")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("group", ""));
            } else if (type.equals("AVOID_PLAYER_WEARING")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("item", ""));
            } else if (type.startsWith("MELEE_AOE")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("damageDelay", "0"));
                goalParam2Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.melee_damage_delay")));
                goalParam3Field.setValue(goal.params.getOrDefault("reach", "4.0"));
                goalParam3Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.reach")));
                goalParam4Field.setValue(goal.params.getOrDefault("width", "120.0"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.width")));
            } else if (type.startsWith("KNOCKBACK")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("damageDelay", "0"));
                goalParam2Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.melee_damage_delay")));
                goalParam3Field.setValue(goal.params.getOrDefault("distance", "8.0"));
                goalParam3Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.knockback_distance")));
            } else if (type.startsWith("MELEE")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("damageDelay", "0"));
                goalParam2Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.melee_damage_delay")));
            } else if (type.equals("RANGED")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
            } else if (type.equals("SUMMON_GROUND_ATTACK")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                p7Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("upwardKnockback", "0.0"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.upward_knockback")));
                goalParam5Field.setValue(goal.params.getOrDefault("upwardSpeed", "1.0"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.upward_speed")));
                goalParam6Field.setValue(goal.params.getOrDefault("maxHeight", "8.0"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.max_height")));
                goalParam7Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam7Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
            } else if (type.startsWith("SUMMON_GROUND_ATTACK_AOE")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                p7Visible = true;
                p8Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("upwardKnockback", "0.0"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.upward_knockback")));
                goalParam5Field.setValue(goal.params.getOrDefault("upwardSpeed", "1.0"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.upward_speed")));
                goalParam6Field.setValue(goal.params.getOrDefault("maxHeight", "8.0"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.max_height")));
                goalParam7Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam7Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
                goalParam8Field.setValue(goal.params.getOrDefault("radius", "12.0"));
                goalParam8Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.radius")));
            } else if (type.equals("SHOTGUN_ATTACK")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("quantity", "5"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_quantity")));
                goalParam5Field.setValue(goal.params.getOrDefault("spread", "30.0"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_spread")));
                goalParam6Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
            } else if (type.equals("ORBITING_SHIELD")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                p7Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("quantity", "3"));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_quantity")));
                goalParam5Field.setValue(goal.params.getOrDefault("radius", "1.5"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.orbit_radius")));
                goalParam6Field.setValue(goal.params.getOrDefault("speed", "4.0"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.speed")));
                goalParam7Field.setValue(goal.params.getOrDefault("duration", "200"));
                goalParam7Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.duration")));
            } else if (type.startsWith("AERIAL_RANGED_AOE")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                p7Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("gravity", goal.params.getOrDefault("drop_speed", "1.0")));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_gravity")));
                goalParam5Field.setValue(goal.params.getOrDefault("quantity", "3"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_quantity")));
                goalParam6Field.setValue(goal.params.getOrDefault("spread", "4.0"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_spread")));
                goalParam7Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam7Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
            } else if (type.equals("AERIAL_RANGED_ATTACK")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                p4Visible = true;
                p5Visible = true;
                p6Visible = true;
                p7Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("sound", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("projectileId", "fireball"));
                goalParam3Field.setValue(goal.params.getOrDefault("damage", "4.0"));
                goalParam4Field.setValue(goal.params.getOrDefault("gravity", goal.params.getOrDefault("drop_speed", "1.0")));
                goalParam4Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_gravity")));
                goalParam5Field.setValue(goal.params.getOrDefault("quantity", "3"));
                goalParam5Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_quantity")));
                goalParam6Field.setValue(goal.params.getOrDefault("spread", "4.0"));
                goalParam6Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.projectile_spread")));
                goalParam7Field.setValue(goal.params.getOrDefault("accuracy", "100"));
                goalParam7Field.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.goal.tooltip.accuracy")));
            } else if (type.equals("USE_ABILITY")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("ability", ""));
            } else if (type.equals("EXPLODE_ON_DEATH") || type.equals("EXPLODE_ON_CONTACT") || type.equals("EXPLODE_ON_LOW_HEALTH")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("power", "3.0"));
                goalParam2Field.setValue(goal.params.getOrDefault("break_blocks", "false"));
                goalParam3Field.setValue(goal.params.getOrDefault("set_fire", "false"));
            } else if (type.equals("TELEPORT_ON_HIT")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("chance", "0.25"));
            } else if (type.equals("DAMAGE_ON_CONTACT")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("amount", "1.0"));
            } else if (type.equals("EFFECT_ON_CONTACT") || type.equals("EFFECT_ON_ATTACK")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("effect", ""));
                goalParam2Field.setValue(goal.params.getOrDefault("duration", "100"));
                goalParam3Field.setValue(goal.params.getOrDefault("amplifier", "0"));
            } else if (type.equals("SPLIT_ON_DEATH")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("minion_id", "zombie"));
                goalParam2Field.setValue(goal.params.getOrDefault("count", "2"));
            } else if (type.equals("SCARE_MOB")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("target_mob", ""));
            } else if (type.equals("TELEPORT_ON_LOW_HEALTH")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("health_percent", "0.2"));
            } else if (type.equals("SUMMON_MINIONS")) {
                p1Visible = true;
                p2Visible = true;
                p3Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("minion_id", "zombie"));
                goalParam2Field.setValue(goal.params.getOrDefault("count", "3"));
                goalParam3Field.setValue(goal.params.getOrDefault("cooldown", "300"));
            } else if (type.equals("TELEPORT_BEHIND_TARGET")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("cooldown", "100"));
            } else if (type.equals("PULL_TARGET")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("cooldown", "120"));
                goalParam2Field.setValue(goal.params.getOrDefault("pull_strength", "1.2"));
            } else if (type.equals("RAGE_MODE")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("health_percent", "0.5"));
                goalParam2Field.setValue(goal.params.getOrDefault("amplifier", "1"));
            } else if (type.equals("AMBUSH")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("stalk_time", "60"));
            } else if (type.equals("FIRE_TRAIL")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("duration", "40"));
            } else if (type.equals("FROST_TOUCH")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("duration", "100"));
                goalParam2Field.setValue(goal.params.getOrDefault("amplifier", "1"));
            } else if (type.equals("DISARM_STRIKE")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("chance", "0.1"));
            } else if (type.equals("LIGHTNING_STRIKE")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("chance", "0.05"));
                goalParam2Field.setValue(goal.params.getOrDefault("cooldown", "200"));
            } else if (type.equals("GIFT_GIVER")) {
                p1Visible = true;
                p2Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("item_id", "loot"));
                goalParam2Field.setValue(goal.params.getOrDefault("cooldown", "600"));
            } else if (type.equals("CALL_HELP")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("range", "100.0"));
            } else if (type.equals("STEAL_ITEM")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("chance", "0.1"));
            } else if (type.equals("BURROW")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("cooldown", "300"));
            } else if (type.equals("IMITATE_SOUNDS")) {
                p1Visible = true;
                goalParam1Field.setValue(goal.params.getOrDefault("cooldown", "200"));
            }
        }
        
        aiParamsScroll = 0;
        updateAIFieldsY();
    }

    private boolean isGroupGoal(String type) {
        return type.equals("AVOID_PLAYER_WEARING") || type.equals("AVOID_MOB") || type.equals("AVOID_GROUP") || type.equals("TARGET_GROUP") || type.equals("ATTACK_OTHERS");
    }

    private void hideAllFields() {
        nameField.setVisible(false);
        mobGroupField.setVisible(false);
        tamingChanceField.setVisible(false);
        loreField.visible = false;
        modelIdField.setVisible(false);
        textureField.setVisible(false);
        animField.setVisible(false);
        healthField.setVisible(false);
        speedField.setVisible(false);
        followRangeField.setVisible(false);
        damageField.setVisible(false);
        armorField.setVisible(false);
        attackSpeedField.setVisible(false);
        attackReachField.setVisible(false);
        knockbackResistanceField.setVisible(false);
        knockbackInflictedField.setVisible(false);
        regenSpeedField.setVisible(false);
        stepHeightField.setVisible(false);
        fallResField.setVisible(false);
        reflectionChanceField.setVisible(false);
        ambientSoundField.setVisible(false);
        stepSoundField.setVisible(false);
        hurtSoundField.setVisible(false);
        deathSoundField.setVisible(false);
        attackSoundField.setVisible(false);
        xpField.setVisible(false);
        minHeightField.setVisible(false);
        maxHeightField.setVisible(false);
        spawnBlockField.setVisible(false);
        minLightField.setVisible(false);
        maxLightField.setVisible(false);
        minGroupField.setVisible(false);
        maxGroupField.setVisible(false);
        naturalWeightField.setVisible(false);
        biomeSearchField.setVisible(false);
        structureField.setVisible(false);
        goalAnimationField.setVisible(false);
        goalGroupField.setVisible(false);
        goalDelayField.setVisible(false);
        lootChanceField.setVisible(false);
        lootMinField.setVisible(false);
        lootMaxField.setVisible(false);
        lootLevelField.setVisible(false);
        idleAnimField.setVisible(false);
        walkAnimField.setVisible(false);
        attackAnimField.setVisible(false);
        deathAnimField.setVisible(false);
        swimAnimField.setVisible(false);
        flyAnimField.setVisible(false);
        goalParam1Field.setVisible(false);
        goalParam2Field.setVisible(false);
        goalParam3Field.setVisible(false);
        goalParam4Field.setVisible(false);
        goalParam5Field.setVisible(false);
        goalParam6Field.setVisible(false);
        goalParam7Field.setVisible(false);
        goalParam8Field.setVisible(false);
        itemSearchField.setVisible(false);
    }

    private void selectTab(String tab) {
        saveTextFieldsToActiveMob();
        this.activeTab = tab;
        this.selectedGoalIndex = -1;
        this.selectedLootIndex = -1;
        hideAllFields();
        showFieldsForTab();
    }

    private void selectMob(MobData data) {
        saveTextFieldsToActiveMob();
        this.selectedMob = data;
        this.selectedGoalIndex = -1;
        this.selectedLootIndex = -1;
        this.init(this.minecraft, this.width, this.height);
    }

    private void saveTextFieldsToActiveMob() {
        if (selectedMob == null || nameField == null) return;
        selectedMob.name = nameField.getValue();
        selectedMob.mobGroup = mobGroupField.getValue();
        selectedMob.loreText = loreField.getValue();
        try {
            selectedMob.tamingChance = Double.parseDouble(tamingChanceField.getValue());
        } catch (Exception ignored) {}

        String oldModel = selectedMob.modelId;
        String newModel = modelIdField.getValue();
        ddraig.net.custommobs.client.renderer.JavaModelLoader.clearCacheFor(selectedMob.id, oldModel);
        ddraig.net.custommobs.client.renderer.JavaModelLoader.clearCacheFor(selectedMob.id, newModel);

        selectedMob.modelId = newModel;
        selectedMob.texturePath = textureField.getValue();
        selectedMob.animationPath = animField.getValue();
        selectedMob.stats.animSpeed = 0.5 + animSpeedSlider * 1.5;
        selectedMob.scale = (float) (0.1 + modelScaleSlider * 4.9);
        selectedMob.hitboxWidth = (float) (0.1 + hitboxWidthSlider * 7.9);
        selectedMob.hitboxHeight = (float) (0.1 + hitboxHeightSlider * 7.9);
        if (previewEntity != null) {
            previewEntity.reapplyTemplate();
        }

        selectedMob.animations.put("idle", idleAnimField.getValue());
        selectedMob.animations.put("walk", walkAnimField.getValue());
        selectedMob.animations.put("attack", attackAnimField.getValue());
        selectedMob.animations.put("death", deathAnimField.getValue());
        selectedMob.animations.put("swim", swimAnimField.getValue());
        selectedMob.animations.put("fly", flyAnimField.getValue());

        selectedMob.spawnRules.spawnBlock = spawnBlockField.getValue();
        selectedMob.spawnRules.allowedStructure = structureField.getValue();

        if (this.biomeSearchField != null) {
            String biomeText = this.biomeSearchField.getValue().trim();
            if (!biomeText.isEmpty() && !selectedMob.spawnRules.biomes.contains(biomeText)) {
                selectedMob.spawnRules.biomes.add(biomeText);
            }
        }

        try { selectedMob.stats.maxHealth = Double.parseDouble(healthField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.movementSpeed = Double.parseDouble(speedField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.followRange = Double.parseDouble(followRangeField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.attackDamage = Double.parseDouble(damageField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.armor = Double.parseDouble(armorField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.attackSpeed = Double.parseDouble(attackSpeedField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.attackReach = Double.parseDouble(attackReachField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.knockbackResistance = Double.parseDouble(knockbackResistanceField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.knockbackInflicted = Double.parseDouble(knockbackInflictedField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.regenSpeed = Double.parseDouble(regenSpeedField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.stepHeight = Double.parseDouble(stepHeightField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.fallDamageResistance = Double.parseDouble(fallResField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.stats.projectileReflectionChance = Double.parseDouble(reflectionChanceField.getValue()); } catch (Exception ignored) {}

        try { selectedMob.loot.xpReward = Integer.parseInt(xpField.getValue()); } catch (Exception ignored) {}

        try { selectedMob.spawnRules.minHeight = Integer.parseInt(minHeightField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.maxHeight = Integer.parseInt(maxHeightField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.minLight = Integer.parseInt(minLightField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.maxLight = Integer.parseInt(maxLightField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.minGroup = Integer.parseInt(minGroupField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.maxGroup = Integer.parseInt(maxGroupField.getValue()); } catch (Exception ignored) {}
        try { selectedMob.spawnRules.weight = Integer.parseInt(naturalWeightField.getValue()); } catch (Exception ignored) {}

        selectedMob.sounds.ambient = ambientSoundField.getValue();
        selectedMob.sounds.step = stepSoundField.getValue();
        selectedMob.sounds.hurt = hurtSoundField.getValue();
        selectedMob.sounds.death = deathSoundField.getValue();
        selectedMob.sounds.attack = attackSoundField.getValue();

        if (activeTab.equals("AI") && selectedGoalIndex >= 0) {
            var goal = selectedMob.aiGoals.get(selectedGoalIndex);
            goal.animation = goalAnimationField.getValue();
            if (isGroupGoal(goal.type)) {
                goal.params.put("group", goalGroupField.getValue());
            }

            goal.params.put("delay", goalDelayField.getValue());

            String type = goal.type;
            if (type.equals("WANDER")) {
                goal.params.put("radius", goalParam1Field.getValue());
            } else if (type.equals("DELAY")) {
                // Delay saved globally
            } else if (type.equals("HEAL_ALLIES")) {
                goal.params.put("amount", goalParam1Field.getValue());
            } else if (type.equals("AVOID_LIGHT")) {
                goal.params.put("light_level", goalParam1Field.getValue());
            } else if (type.equals("AVOID_MOB")) {
                goal.params.put("mobs", goalParam1Field.getValue());
            } else if (type.equals("AVOID_GROUP") || type.equals("TARGET_GROUP") || type.equals("ATTACK_OTHERS")) {
                goal.params.put("group", goalParam1Field.getValue());
            } else if (type.equals("AVOID_PLAYER_WEARING")) {
                goal.params.put("item", goalParam1Field.getValue());
            } else if (type.startsWith("MELEE_AOE")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("damageDelay", goalParam2Field.getValue());
                goal.params.put("reach", goalParam3Field.getValue());
                goal.params.put("width", goalParam4Field.getValue());
            } else if (type.startsWith("KNOCKBACK")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("damageDelay", goalParam2Field.getValue());
                goal.params.put("distance", goalParam3Field.getValue());
            } else if (type.startsWith("MELEE")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("damageDelay", goalParam2Field.getValue());
            } else if (type.equals("RANGED")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("accuracy", goalParam4Field.getValue());
            } else if (type.equals("SUMMON_GROUND_ATTACK")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("upwardKnockback", goalParam4Field.getValue());
                goal.params.put("upwardSpeed", goalParam5Field.getValue());
                goal.params.put("maxHeight", goalParam6Field.getValue());
                goal.params.put("accuracy", goalParam7Field.getValue());
            } else if (type.startsWith("SUMMON_GROUND_ATTACK_AOE")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("upwardKnockback", goalParam4Field.getValue());
                goal.params.put("upwardSpeed", goalParam5Field.getValue());
                goal.params.put("maxHeight", goalParam6Field.getValue());
                goal.params.put("accuracy", goalParam7Field.getValue());
                goal.params.put("radius", goalParam8Field.getValue());
            } else if (type.equals("SHOTGUN_ATTACK")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("quantity", goalParam4Field.getValue());
                goal.params.put("spread", goalParam5Field.getValue());
                goal.params.put("accuracy", goalParam6Field.getValue());
            } else if (type.equals("ORBITING_SHIELD")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("quantity", goalParam4Field.getValue());
                goal.params.put("radius", goalParam5Field.getValue());
                goal.params.put("speed", goalParam6Field.getValue());
                goal.params.put("duration", goalParam7Field.getValue());
            } else if (type.startsWith("AERIAL_RANGED_AOE")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("gravity", goalParam4Field.getValue());
                goal.params.put("quantity", goalParam5Field.getValue());
                goal.params.put("spread", goalParam6Field.getValue());
                goal.params.put("accuracy", goalParam7Field.getValue());
            } else if (type.equals("AERIAL_RANGED_ATTACK")) {
                goal.params.put("sound", goalParam1Field.getValue());
                goal.params.put("projectileId", goalParam2Field.getValue());
                goal.params.put("damage", goalParam3Field.getValue());
                goal.params.put("gravity", goalParam4Field.getValue());
                goal.params.put("quantity", goalParam5Field.getValue());
                goal.params.put("spread", goalParam6Field.getValue());
                goal.params.put("accuracy", goalParam7Field.getValue());
            } else if (type.equals("USE_ABILITY")) {
                goal.params.put("ability", goalParam1Field.getValue());
            } else if (type.equals("EXPLODE_ON_DEATH") || type.equals("EXPLODE_ON_CONTACT") || type.equals("EXPLODE_ON_LOW_HEALTH")) {
                goal.params.put("power", goalParam1Field.getValue());
                goal.params.put("break_blocks", goalParam2Field.getValue());
                goal.params.put("set_fire", goalParam3Field.getValue());
            } else if (type.equals("TELEPORT_ON_HIT")) {
                goal.params.put("chance", goalParam1Field.getValue());
            } else if (type.equals("DAMAGE_ON_CONTACT")) {
                goal.params.put("amount", goalParam1Field.getValue());
            } else if (type.equals("EFFECT_ON_CONTACT") || type.equals("EFFECT_ON_ATTACK")) {
                goal.params.put("effect", goalParam1Field.getValue());
                goal.params.put("duration", goalParam2Field.getValue());
                goal.params.put("amplifier", goalParam3Field.getValue());
            } else if (type.equals("SPLIT_ON_DEATH")) {
                goal.params.put("minion_id", goalParam1Field.getValue());
                goal.params.put("count", goalParam2Field.getValue());
            } else if (type.equals("SCARE_MOB")) {
                goal.params.put("target_mob", goalParam1Field.getValue());
            } else if (type.equals("TELEPORT_ON_LOW_HEALTH")) {
                goal.params.put("health_percent", goalParam1Field.getValue());
            } else if (type.equals("SUMMON_MINIONS")) {
                goal.params.put("minion_id", goalParam1Field.getValue());
                goal.params.put("count", goalParam2Field.getValue());
                goal.params.put("cooldown", goalParam3Field.getValue());
            } else if (type.equals("TELEPORT_BEHIND_TARGET")) {
                goal.params.put("cooldown", goalParam1Field.getValue());
            } else if (type.equals("PULL_TARGET")) {
                goal.params.put("cooldown", goalParam1Field.getValue());
                goal.params.put("pull_strength", goalParam2Field.getValue());
            } else if (type.equals("RAGE_MODE")) {
                goal.params.put("health_percent", goalParam1Field.getValue());
                goal.params.put("amplifier", goalParam2Field.getValue());
            } else if (type.equals("AMBUSH")) {
                goal.params.put("stalk_time", goalParam1Field.getValue());
            } else if (type.equals("FIRE_TRAIL")) {
                goal.params.put("duration", goalParam1Field.getValue());
            } else if (type.equals("FROST_TOUCH")) {
                goal.params.put("duration", goalParam1Field.getValue());
                goal.params.put("amplifier", goalParam2Field.getValue());
            } else if (type.equals("DISARM_STRIKE")) {
                goal.params.put("chance", goalParam1Field.getValue());
            } else if (type.equals("LIGHTNING_STRIKE")) {
                goal.params.put("chance", goalParam1Field.getValue());
                goal.params.put("cooldown", goalParam2Field.getValue());
            } else if (type.equals("GIFT_GIVER")) {
                goal.params.put("item_id", goalParam1Field.getValue());
                goal.params.put("cooldown", goalParam2Field.getValue());
            } else if (type.equals("CALL_HELP")) {
                goal.params.put("range", goalParam1Field.getValue());
            } else if (type.equals("STEAL_ITEM")) {
                goal.params.put("chance", goalParam1Field.getValue());
            } else if (type.equals("BURROW")) {
                goal.params.put("cooldown", goalParam1Field.getValue());
            } else if (type.equals("IMITATE_SOUNDS")) {
                goal.params.put("cooldown", goalParam1Field.getValue());
            }
        }

        if (activeTab.equals("Loot") && selectedLootIndex >= 0 && selectedLootIndex < selectedMob.loot.items.size()) {
            var item = selectedMob.loot.items.get(selectedLootIndex);
            try { item.chance = Double.parseDouble(lootChanceField.getValue()); } catch (Exception ignored) {}
            try { item.minCount = Integer.parseInt(lootMinField.getValue()); } catch (Exception ignored) {}
            try { item.maxCount = Integer.parseInt(lootMaxField.getValue()); } catch (Exception ignored) {}
            try {
                if (!lootLevelField.getValue().isEmpty()) {
                    item.lootingLevel = Integer.parseInt(lootLevelField.getValue());
                } else {
                    item.lootingLevel = 0;
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveCurrentMob() {
        if (selectedMob == null) return;
        String oldId = selectedMob.id;

        saveTextFieldsToActiveMob();

        String baseId = selectedMob.name.toLowerCase().trim().replace(" ", "_").replaceAll("[^a-z0-9_-]", "");
        if (baseId.isEmpty()) baseId = "custom_mob";
        String newId = baseId;
        int counter = 1;
        while ((MobRegistry.loadedMobs.containsKey(newId) || MobRegistry.loadedProjectiles.containsKey(newId)) && !newId.equals(oldId)) {
            newId = baseId + "_" + counter;
            counter++;
        }

        if (!newId.equals(oldId)) {
            selectedMob.id = newId;
            MobRegistry.loadedMobs.remove(oldId);
            MobRegistry.loadedMobs.put(newId, selectedMob);
        }

        String json = new Gson().toJson(selectedMob);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(oldId);
        buf.writeUtf(json, 262144);
        NetworkManager.sendToServer(ModPackets.C2S_SAVE_MOB_TEMPLATE, buf);
    }

    private static class TabBounds {
        String id;
        Component label;
        int x, y, w, h;
        TabBounds(String id, Component label) {
            this.id = id;
            this.label = label;
        }
    }

    private List<TabBounds> calculateTabBounds(int left, int top) {
        List<TabBounds> bounds = new ArrayList<>();
        List<String> tabIds = new ArrayList<>(List.of("General", "Model"));
        if (selectedMob != null && (selectedMob.modelType.equals("geckolib") || selectedMob.modelType.equals("java"))) {
            tabIds.add("Animations");
        }
        tabIds.addAll(List.of("AI", "Stats", "Abilities", "Sounds", "Loot", "Spawning"));

        List<List<TabBounds>> rows = new ArrayList<>();
        List<TabBounds> currentRow = new ArrayList<>();
        int currentX = left + 120;
        int maxX = left + this.panelW - 10;

        for (int i = 0; i < tabIds.size(); i++) {
            String tabId = tabIds.get(i);
            Component labelComp = Component.literal(tabId);
            int w = this.font.width(labelComp) + 12;
            TabBounds tb = new TabBounds(tabId, labelComp);
            tb.w = w;
            tb.h = 15;

            if (currentX + w > maxX && !currentRow.isEmpty()) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentX = left + 120;
            }
            tb.x = currentX;
            currentRow.add(tb);
            currentX += w + 2;
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        int numRows = rows.size();
        for (int r = 0; r < numRows; r++) {
            int rowY = top + 25 - (numRows - 1 - r) * 15;
            for (TabBounds tb : rows.get(r)) {
                tb.y = rowY;
                bounds.add(tb);
            }
        }
        return bounds;
    }

    private boolean hasAbility(String name) {
        if (selectedMob == null) return false;
        for (var ab : selectedMob.abilities) {
            if (ab.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void toggleAbility(String name) {
        if (selectedMob == null) return;
        MobData.AbilityData found = null;
        for (var ab : selectedMob.abilities) {
            if (ab.name.equalsIgnoreCase(name)) {
                found = ab;
                break;
            }
        }
        if (found != null) {
            selectedMob.abilities.remove(found);
        } else {
            MobData.AbilityData ab = new MobData.AbilityData();
            ab.name = name;
            boolean isPassive = false;
            for (String p : PASSIVE_ABILITIES) {
                if (p.equalsIgnoreCase(name)) {
                    isPassive = true;
                    break;
                }
            }
            ab.isPassive = isPassive;

            if (name.contains("Flame") || name.contains("Fire") || name.contains("Infernal")) {
                ab.type = "BURNING";
            } else if (name.contains("Frost") || name.contains("Nova") || name.contains("Glacial")) {
                ab.type = "FREEZE";
            } else if (name.contains("Healing") || name.contains("Rejuvenation")) {
                ab.type = "HEALING";
            } else if (name.contains("Teleport")) {
                ab.type = "TELEPORT";
            } else if (name.contains("Dash") || name.contains("Charge")) {
                ab.type = "DASH";
            } else {
                ab.type = "POISON";
            }
            selectedMob.abilities.add(ab);
        }
    }

    private void drawEditBoxBackground(GuiGraphics graphics, net.minecraft.client.gui.components.AbstractWidget field, int borderC, int slotC) {
        if (field.visible) {
            int bx = field.getX() - 4;
            int by = field.getY() - 3;
            int bw = field.getWidth() + 8;
            int bh = field.getHeight() + 6;
            UIHelper.drawRecessedSlot(graphics, bx, by, bw, bh, borderC, slotC);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        this.hoveredTooltip = null;
        this.hoveredItemTooltip = ItemStack.EMPTY;

        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;

        int borderC = 0xFFDFD0A0; // Gold
        int bgC = 0xFF2D2D2D;
        int slotC = 0xFF1C1C1C;
        int textActiveC = 0xFFD4AF37;
        int textNormalC = 0xFFCCCCCC;

        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        graphics.drawString(this.font, this.title, left + 12, top + 10, textActiveC, false);

        int listX = left + 10;
        int listY = top + 25;
        int listW = 100;
        int listH = panelH - 55;

        UIHelper.drawRecessedSlot(graphics, listX, listY, listW, listH, borderC, slotC);

        int sidebarY = listY + 5;
        for (MobData m : mobTemplates) {
            int c = (m == selectedMob) ? textActiveC : textNormalC;
            graphics.drawString(this.font, truncate(m.name, 14), listX + 5, sidebarY, c, false);
            
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= sidebarY && mouseY <= sidebarY + 10) {
                this.hoveredTooltip = List.of(
                    Component.literal("Template ID: " + m.id),
                    Component.literal("Type: " + m.modelType.toUpperCase())
                );
            }
            sidebarY += 12;
        }

        int addY = listY + listH + 4;
        boolean hoverAdd = mouseX >= listX && mouseX <= listX + 31 && mouseY >= addY && mouseY <= addY + 14;
        UIHelper.drawShadedButton(graphics, listX, addY, 31, 14, hoverAdd, 0xFF005500);
        graphics.drawString(this.font, "New", listX + 6, addY + 3, 0xFFFFFFFF, false);

        boolean hoverCopy = mouseX >= listX + 34 && mouseX <= listX + 66 && mouseY >= addY && mouseY <= addY + 14;
        UIHelper.drawShadedButton(graphics, listX + 34, addY, 32, 14, hoverCopy, 0xFF003366);
        graphics.drawString(this.font, "Copy", listX + 39, addY + 3, 0xFFFFFFFF, false);

        boolean hoverDel = mouseX >= listX + 69 && mouseX <= listX + listW && mouseY >= addY && mouseY <= addY + 14;
        UIHelper.drawShadedButton(graphics, listX + 69, addY, 31, 14, hoverDel, 0xFF550000);
        graphics.drawString(this.font, "Del", listX + 76, addY + 3, 0xFFFFFFFF, false);

        if (selectedMob != null) {
            List<TabBounds> tabBoundsList = calculateTabBounds(left, top);
            for (TabBounds tb : tabBoundsList) {
                boolean active = tb.id.equals(activeTab);
                boolean hoverTab = mouseX >= tb.x && mouseX <= tb.x + tb.w && mouseY >= tb.y && mouseY <= tb.y + tb.h;
                int color = active ? slotC : UIHelper.adjustBrightness(bgC, -15);
                UIHelper.drawShadedButton(graphics, tb.x, tb.y, tb.w, tb.h, hoverTab, color);
                graphics.drawString(this.font, tb.label, tb.x + 6, tb.y + 4, active ? textActiveC : textNormalC, false);
            }

            int formX = left + 120;
            int formY = top + 42;
            int formW = (int) ((panelW - 130) * 0.6);
            int formH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, formX, formY, formW, formH, borderC, slotC);

            int viewportX = formX + formW + 10;
            int viewportY = top + 42;
            int viewportW = left + panelW - 10 - viewportX;
            int viewportH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, viewportX, viewportY, viewportW, viewportH, borderC, slotC);
            draw3DPreview(graphics, viewportX + viewportW / 2, viewportY + viewportH / 2 + 35);

            boolean hoverSave = mouseX >= viewportX + (viewportW - 110) / 2 && mouseX <= viewportX + (viewportW - 110) / 2 + 110 && mouseY >= viewportY + viewportH - 25 && mouseY <= viewportY + viewportH - 5;
            UIHelper.drawShadedButton(graphics, viewportX + (viewportW - 110) / 2, viewportY + viewportH - 25, 110, 20, hoverSave, 0xFF00AA00);
            graphics.drawString(this.font, "Save & Export", viewportX + (viewportW - 110) / 2 + 15, viewportY + viewportH - 19, 0xFFFFFFFF, false);
        }

        // Draw recessed dark slot backgrounds behind active EditBox fields (so they look like RPG Mounts slots)
        drawEditBoxBackground(graphics, nameField, borderC, slotC);
        drawEditBoxBackground(graphics, mobGroupField, borderC, slotC);
        drawEditBoxBackground(graphics, tamingChanceField, borderC, slotC);
        drawEditBoxBackground(graphics, loreField, borderC, slotC);
        drawEditBoxBackground(graphics, modelIdField, borderC, slotC);
        drawEditBoxBackground(graphics, textureField, borderC, slotC);
        drawEditBoxBackground(graphics, animField, borderC, slotC);
        drawEditBoxBackground(graphics, healthField, borderC, slotC);
        drawEditBoxBackground(graphics, speedField, borderC, slotC);
        drawEditBoxBackground(graphics, followRangeField, borderC, slotC);
        drawEditBoxBackground(graphics, damageField, borderC, slotC);
        drawEditBoxBackground(graphics, armorField, borderC, slotC);
        drawEditBoxBackground(graphics, attackSpeedField, borderC, slotC);
        drawEditBoxBackground(graphics, attackReachField, borderC, slotC);
        drawEditBoxBackground(graphics, knockbackResistanceField, borderC, slotC);
        drawEditBoxBackground(graphics, knockbackInflictedField, borderC, slotC);
        drawEditBoxBackground(graphics, regenSpeedField, borderC, slotC);
        drawEditBoxBackground(graphics, stepHeightField, borderC, slotC);
        drawEditBoxBackground(graphics, fallResField, borderC, slotC);
        drawEditBoxBackground(graphics, reflectionChanceField, borderC, slotC);
        drawEditBoxBackground(graphics, ambientSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, stepSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, hurtSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, deathSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, attackSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, xpField, borderC, slotC);
        drawEditBoxBackground(graphics, minHeightField, borderC, slotC);
        drawEditBoxBackground(graphics, maxHeightField, borderC, slotC);
        drawEditBoxBackground(graphics, spawnBlockField, borderC, slotC);
        drawEditBoxBackground(graphics, minLightField, borderC, slotC);
        drawEditBoxBackground(graphics, maxLightField, borderC, slotC);
        drawEditBoxBackground(graphics, minGroupField, borderC, slotC);
        drawEditBoxBackground(graphics, maxGroupField, borderC, slotC);
        drawEditBoxBackground(graphics, naturalWeightField, borderC, slotC);
        drawEditBoxBackground(graphics, biomeSearchField, borderC, slotC);
        drawEditBoxBackground(graphics, structureField, borderC, slotC);
        drawEditBoxBackground(graphics, goalAnimationField, borderC, slotC);
        drawEditBoxBackground(graphics, goalGroupField, borderC, slotC);
        drawEditBoxBackground(graphics, goalDelayField, borderC, slotC);
        drawEditBoxBackground(graphics, behaviorSearchField, borderC, slotC);
        drawEditBoxBackground(graphics, lootChanceField, borderC, slotC);
        drawEditBoxBackground(graphics, lootMinField, borderC, slotC);
        drawEditBoxBackground(graphics, lootMaxField, borderC, slotC);
        drawEditBoxBackground(graphics, lootLevelField, borderC, slotC);
        drawEditBoxBackground(graphics, idleAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, walkAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, attackAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, deathAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, swimAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, flyAnimField, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam1Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam2Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam3Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam4Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam5Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam6Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam7Field, borderC, slotC);
        drawEditBoxBackground(graphics, goalParam8Field, borderC, slotC);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (selectedMob != null) {
            int formX = left + 120;
            int formY = top + 42;
            int formW = (int) ((panelW - 130) * 0.6);
            renderTabFormText(graphics, formX, formY, formW, mouseX, mouseY);
        }

        // Draw suggestions autocomplete dropdown (elevating Z index to block bleed-through)
        if (showSuggestions && !activeSuggestions.isEmpty() && activeField != null) {
            int dropX = activeField.getX();
            int dropY = activeField.getY() + activeField.getHeight() + 2;
            int dropW = activeField.getWidth();
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 350.0F); // Elevate layer to prevent rendering overlap

            graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1C1C1C);
            UIHelper.drawOutline(graphics, dropX, dropY, dropW, dropH, 0xFFDFD0A0);

            for (int i = 0; i < visibleRows; i++) {
                int idx = i + suggestionsScrollOffset;
                if (idx >= activeSuggestions.size()) break;
                String suggestion = activeSuggestions.get(idx);
                int suggestionY = dropY + i * rowH;

                boolean hoverRow = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= suggestionY && mouseY <= suggestionY + rowH;
                if (hoverRow) {
                    graphics.fill(dropX + 1, suggestionY + 1, dropX + dropW - 1, suggestionY + rowH - 1, 0xFF444444);
                }
                String scrolledText = suggestion;
                int textW = this.font.width(suggestion);
                int maxW = dropW - 8;
                if (textW > maxW) {
                    int overflow = textW - maxW;
                    int speed = 30; // ms per pixel
                    long totalDuration = overflow * speed + 2000;
                    long t = System.currentTimeMillis() % totalDuration;
                    int shiftPixels = 0;
                    if (t > 1000 && t < 1000 + overflow * speed) {
                        shiftPixels = (int) ((t - 1000) / speed);
                    } else if (t >= 1000 + overflow * speed) {
                        shiftPixels = overflow;
                    }
                    int startIndex = 0;
                    while (startIndex < suggestion.length() && this.font.width(suggestion.substring(0, startIndex)) < shiftPixels) {
                        startIndex++;
                    }
                    String visiblePart = suggestion.substring(startIndex);
                    scrolledText = this.font.plainSubstrByWidth(visiblePart, maxW);
                }

                graphics.drawString(this.font, scrolledText, dropX + 4, suggestionY + 2, 0xFFFFFFFF, false);
            }
            
            graphics.pose().popPose();
        }

        // Draw generic Item selection popup overlay
        if (showItemSelector) {
            int popW = 220;
            int popH = 190;
            int popX = (this.width - popW) / 2;
            int popY = (this.height - popH) / 2;

            graphics.fill(0, 0, this.width, this.height, 0x88000000);
            UIHelper.drawBeveledPanel(graphics, popX, popY, popW, popH, borderC, bgC);
            graphics.drawString(this.font, "Select Item", popX + 10, popY + 10, textActiveC, false);

            // Sub-selection Tabs
            int tab1X = popX + 15;
            int tabY = popY + 25;
            boolean hoverAll = mouseX >= tab1X && mouseX <= tab1X + 90 && mouseY >= tabY && mouseY <= tabY + 12;
            UIHelper.drawShadedButton(graphics, tab1X, tabY, 90, 12, hoverAll, selectAllItems ? slotC : 0xFF3C3C3C);
            graphics.drawString(this.font, "All Items", tab1X + 18, tabY + 2, selectAllItems ? textActiveC : textNormalC, false);

            int tab2X = popX + 115;
            boolean hoverInv = mouseX >= tab2X && mouseX <= tab2X + 90 && mouseY >= tabY && mouseY <= tabY + 12;
            UIHelper.drawShadedButton(graphics, tab2X, tabY, 90, 12, hoverInv, !selectAllItems ? slotC : 0xFF3C3C3C);
            graphics.drawString(this.font, "Inventory", tab2X + 18, tabY + 2, !selectAllItems ? textActiveC : textNormalC, false);

            // Filter items
            List<ItemStack> currentList;
            int slotY;
            if (selectAllItems) {
                String query = itemSearchField.getValue().toLowerCase();
                currentList = selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).toList();
                
                int maxScroll = Math.max(0, (int) Math.ceil((double) currentList.size() / 9.0) - 4);
                if (itemSelectorScroll > maxScroll) {
                    itemSelectorScroll = maxScroll;
                }
                
                // Draw Search Box
                graphics.drawString(this.font, "Search:", popX + 15, popY + 43, 0xFFFFFFFF);
                itemSearchField.setX(popX + 60);
                itemSearchField.setY(popY + 41);
                itemSearchField.render(graphics, mouseX, mouseY, 0.0f);
                drawEditBoxBackground(graphics, itemSearchField, borderC, slotC);
                
                slotY = popY + 58;
            } else {
                var player = Minecraft.getInstance().player;
                currentList = new ArrayList<>();
                if (player != null) {
                    for (var stack : player.getInventory().items) {
                        if (!stack.isEmpty()) currentList.add(stack);
                    }
                }
                slotY = popY + 42;
            }

            int slotX = popX + 15;
            int itemIdx = itemSelectorScroll * 9;
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = itemIdx + row * 9 + col;
                    if (idx >= currentList.size()) break;
                    ItemStack stack = currentList.get(idx);
                    int sX = slotX + col * 21;
                    int sY = slotY + row * 21;

                    boolean hoverSlot = mouseX >= sX && mouseX <= sX + 20 && mouseY >= sY && mouseY <= sY + 20;
                    graphics.fill(sX, sY, sX + 20, sY + 20, hoverSlot ? 0xFF444444 : slotC);
                    UIHelper.drawOutline(graphics, sX, sY, 20, 20, borderC);
                    graphics.renderItem(stack, sX + 2, sY + 2);

                    if (hoverSlot) {
                        this.hoveredItemTooltip = stack;
                    }
                }
            }
            graphics.drawString(this.font, "Click backdrop to close.", popX + 15, popY + popH - 13, 0xFFAAAAAA, false);
        }

        // Draw Ghost/Preview of the dragged AI Behavior
        if (isDraggingGoal && draggedGoalIndex >= 0 && draggedGoalIndex < selectedMob.aiGoals.size()) {
            String type = selectedMob.aiGoals.get(draggedGoalIndex).type;
            graphics.fill(mouseX - 60, mouseY - 7, mouseX + 60, mouseY + 7, 0xAA666666);
            UIHelper.drawOutline(graphics, mouseX - 60, mouseY - 7, 120, 14, 0xFFDFD0A0);
            graphics.drawString(this.font, type, mouseX - 55, mouseY - 4, 0xFFFFFFFF, false);
        }

        if (hoveredTooltip != null) {
            graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }
        if (!hoveredItemTooltip.isEmpty()) {
            graphics.renderTooltip(this.font, hoveredItemTooltip, mouseX, mouseY);
        }
    }

    private void renderTabFormText(GuiGraphics graphics, int formX, int formY, int formW, int mouseX, int mouseY) {
        int labelC = 0xFFFFFF55;
        int textC = 0xFFFFFFFF;
        int borderC = 0xFFDFD0A0;
        int slotC = 0xFF1C1C1C;
        int bgC = 0xFF2D2D2D;
        int textActiveC = 0xFFD4AF37;
        int textNormalC = 0xFFCCCCCC;

        if (activeTab.equals("General")) {
            graphics.drawString(this.font, "Name:", formX + 10, formY + 19, labelC);
            graphics.drawString(this.font, "Mob Group:", formX + 10, formY + 34, labelC);

            graphics.drawString(this.font, "Billboard Name Tag:", formX + 10, formY + 49, textC);
            graphics.fill(formX + 120, formY + 48, formX + 130, formY + 58, selectedMob.billboardName ? 0xFF00FF00 : 0xFFFF0000);

            graphics.drawString(this.font, "Name Color:", formX + 180, formY + 49, labelC);
            boolean hoverColor = mouseX >= formX + 260 && mouseX <= formX + 340 && mouseY >= formY + 47 && mouseY <= formY + 61;
            UIHelper.drawShadedButton(graphics, formX + 260, formY + 47, 80, 14, hoverColor, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.nameColor.toUpperCase(), formX + 270, formY + 50, 0xFFFFFFFF, false);

            graphics.drawString(this.font, "Is Flying Mode:", formX + 10, formY + 63, textC);
            graphics.fill(formX + 120, formY + 62, formX + 130, formY + 72, selectedMob.isFlying ? 0xFF00FF00 : 0xFFFF0000);

            graphics.drawString(this.font, "Behavior Mode:", formX + 10, formY + 77, textC);
            boolean hoverMode = mouseX >= formX + 100 && mouseX <= formX + 180 && mouseY >= formY + 75 && mouseY <= formY + 89;
            UIHelper.drawShadedButton(graphics, formX + 100, formY + 75, 80, 14, hoverMode, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.behaviorMode.toUpperCase(), formX + 115, formY + 78, 0xFFFFFFFF, false);

            graphics.drawString(this.font, "Is Tameable:", formX + 10, formY + 95, textC);
            graphics.fill(formX + 120, formY + 94, formX + 130, formY + 104, selectedMob.tameable ? 0xFF00FF00 : 0xFFFF0000);

            if (selectedMob.tameable) {
                graphics.drawString(this.font, "Taming Food (Click to select):", formX + 10, formY + 109, labelC);
                boolean hoverItemSelect = mouseX >= formX + 100 && mouseX <= formX + 220 && mouseY >= formY + 107 && mouseY <= formY + 119;
                UIHelper.drawShadedButton(graphics, formX + 10, formY + 107, 120, 12, hoverItemSelect, 0xFF3C3C3C);
                graphics.drawString(this.font, truncate(selectedMob.tamingItem, 16), formX + 15, formY + 109, 0xFFFFFFFF, false);

                graphics.drawString(this.font, "Taming Chance:", formX + 10, formY + 123, labelC);
            }

            graphics.drawString(this.font, "Lore description:", formX + 10, formY + 138, labelC);

        } else if (activeTab.equals("Model")) {
            graphics.drawString(this.font, "Model Type:", formX + 10, formY + 23, textC);
            boolean hoverType = mouseX >= formX + 100 && mouseX <= formX + 180 && mouseY >= formY + 21 && mouseY <= formY + 35;
            UIHelper.drawShadedButton(graphics, formX + 100, formY + 21, 80, 14, hoverType, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.modelType.toUpperCase(), formX + 110, formY + 24, 0xFFFFFFFF, false);

            if (selectedMob.modelType.equals("mcmodel")) {
                int infoY = formY + 40;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.header"), formX + 10, infoY, 0xFFFFD700, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step1"), formX + 10, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step2"), formX + 10, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step3"), formX + 10, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step4"), formX + 10, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step5"), formX + 10, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.mcmodel.step6"), formX + 10, infoY, 0xFF55FFFF, false);

                // Hover tooltips for each step
                int hoverY = formY + 40 + 12;
                if (mouseX >= formX + 10 && mouseX <= formX + 220) {
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step1.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step1.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY),
                            Component.translatable("gui.custom_mobs.mcmodel.step1.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                    hoverY += 12;
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step2.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step2.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY),
                            Component.translatable("gui.custom_mobs.mcmodel.step2.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                    hoverY += 12;
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step3.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step3.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                    hoverY += 12;
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step4.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step4.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                    hoverY += 12;
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step5.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step5.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY),
                            Component.translatable("gui.custom_mobs.mcmodel.step5.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                    hoverY += 12;
                    if (mouseY >= hoverY && mouseY <= hoverY + 10) {
                        this.hoveredTooltip = List.of(
                            Component.translatable("gui.custom_mobs.mcmodel.step6.tooltip1"),
                            Component.translatable("gui.custom_mobs.mcmodel.step6.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY),
                            Component.translatable("gui.custom_mobs.mcmodel.step6.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    }
                }
            } else {
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.model_id"), formX + 10, formY + 40, labelC);
                if (selectedMob.modelType.equals("geckolib") || selectedMob.modelType.equals("java")) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.texture_path"), formX + 10, formY + 58, labelC);
                }
                if (selectedMob.modelType.equals("geckolib")) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.anim_path"), formX + 10, formY + 76, labelC);
                }
                
                int sliderX = formX + 110;
                int sliderY = formY + 100;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.anim_speed"), formX + 10, sliderY + 1, labelC);
                graphics.fill(sliderX, sliderY + 4, sliderX + 100, sliderY + 6, 0xFF1C1C1C);
                graphics.fill(sliderX + (int) (animSpeedSlider * 92), sliderY, sliderX + (int) (animSpeedSlider * 92) + 8, sliderY + 10, 0xFFDFD0A0);
                graphics.drawString(this.font, String.format("%.1fx", 0.5 + animSpeedSlider * 1.5), sliderX + 110, sliderY + 1, 0xFFFFFFFF);

                // Model Scale Slider
                int scaleY = formY + 115;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.model_scale"), formX + 10, scaleY + 1, labelC);
                graphics.fill(sliderX, scaleY + 4, sliderX + 100, scaleY + 6, 0xFF1C1C1C);
                graphics.fill(sliderX + (int) (modelScaleSlider * 92), scaleY, sliderX + (int) (modelScaleSlider * 92) + 8, scaleY + 10, 0xFFDFD0A0);
                graphics.drawString(this.font, String.format("%.2fx", 0.1 + modelScaleSlider * 4.9), sliderX + 110, scaleY + 1, 0xFFFFFFFF);

                // Hitbox Width Slider
                int hbWidthY = formY + 130;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.hitbox_width"), formX + 10, hbWidthY + 1, labelC);
                graphics.fill(sliderX, hbWidthY + 4, sliderX + 100, hbWidthY + 6, 0xFF1C1C1C);
                graphics.fill(sliderX + (int) (hitboxWidthSlider * 92), hbWidthY, sliderX + (int) (hitboxWidthSlider * 92) + 8, hbWidthY + 10, 0xFFDFD0A0);
                graphics.drawString(this.font, String.format("%.2f", 0.1 + hitboxWidthSlider * 7.9), sliderX + 110, hbWidthY + 1, 0xFFFFFFFF);

                // Hitbox Height Slider
                int hbHeightY = formY + 145;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.hitbox_height"), formX + 10, hbHeightY + 1, labelC);
                graphics.fill(sliderX, hbHeightY + 4, sliderX + 100, hbHeightY + 6, 0xFF1C1C1C);
                graphics.fill(sliderX + (int) (hitboxHeightSlider * 92), hbHeightY, sliderX + (int) (hitboxHeightSlider * 92) + 8, hbHeightY + 10, 0xFFDFD0A0);
                graphics.drawString(this.font, String.format("%.2f", 0.1 + hitboxHeightSlider * 7.9), sliderX + 110, hbHeightY + 1, 0xFFFFFFFF);

                // Show Hitbox Checkbox
                int hbShowY = formY + 160;
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.show_hitbox"), formX + 10, hbShowY + 1, labelC);
                graphics.fill(formX + 130, hbShowY, formX + 140, hbShowY + 10, ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug ? 0xFF00FF00 : 0xFFFF0000);
            }

        } else if (activeTab.equals("Animations")) {
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.idle_anim"), formX + 10, formY + 24, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.walk_anim"), formX + 10, formY + 42, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.attack_anim"), formX + 10, formY + 60, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.death_anim"), formX + 10, formY + 78, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.swim_anim"), formX + 10, formY + 96, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.fly_anim"), formX + 10, formY + 114, labelC);

        } else if (activeTab.equals("Stats")) {
            int statsY = formY + 8;
            int ySpacing = 13;
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.health").getString(), healthField, "maxHealth", formX + 10, statsY + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.speed").getString(), speedField, "movementSpeed", formX + 10, statsY + ySpacing + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.follow_range").getString(), followRangeField, "followRange", formX + 10, statsY + ySpacing * 2 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.damage").getString(), damageField, "attackDamage", formX + 10, statsY + ySpacing * 3 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.armor").getString(), armorField, "armor", formX + 10, statsY + ySpacing * 4 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.attack_speed").getString(), attackSpeedField, "attackSpeed", formX + 10, statsY + ySpacing * 5 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.attack_reach").getString(), attackReachField, "attackReach", formX + 10, statsY + ySpacing * 6 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.kb_resistance").getString(), knockbackResistanceField, "knockbackResistance", formX + 10, statsY + ySpacing * 7 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.kb_inflicted").getString(), knockbackInflictedField, "knockbackInflicted", formX + 10, statsY + ySpacing * 8 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.regen_speed").getString(), regenSpeedField, "regenSpeed", formX + 10, statsY + ySpacing * 9 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.step_height").getString(), stepHeightField, "stepHeight", formX + 10, statsY + ySpacing * 10 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.fall_res").getString(), fallResField, "fallDamageResistance", formX + 10, statsY + ySpacing * 11 + 3, mouseX, mouseY);
            renderStatField(graphics, Component.translatable("gui.custom_mobs.creator.label.reflection_chance").getString(), reflectionChanceField, "projectileReflectionChance", formX + 10, statsY + ySpacing * 12 + 3, mouseX, mouseY);

        } else if (activeTab.equals("Abilities")) {
            // Draw Sub-tabs: "Active" and "Passive"
            int activeTabY = formY + 5;
            int tabW = 60;
            int tabH = 12;

            // "Active" tab
            int activeBtnX = formX + 8;
            boolean hoverActiveTab = mouseX >= activeBtnX && mouseX <= activeBtnX + tabW && mouseY >= activeTabY && mouseY <= activeTabY + tabH;
            int activeColor = (!showPassiveAbilities) ? bgC : UIHelper.adjustBrightness(bgC, -15);
            UIHelper.drawShadedButton(graphics, activeBtnX, activeTabY, tabW, tabH, hoverActiveTab, activeColor);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.abilities.active").getString(), activeBtnX + 12, activeTabY + 2, (!showPassiveAbilities) ? textActiveC : textNormalC, false);

            // "Passive" tab
            int passiveBtnX = formX + 72;
            boolean hoverPassiveTab = mouseX >= passiveBtnX && mouseX <= passiveBtnX + tabW && mouseY >= activeTabY && mouseY <= activeTabY + tabH;
            int passiveColor = showPassiveAbilities ? bgC : UIHelper.adjustBrightness(bgC, -15);
            UIHelper.drawShadedButton(graphics, passiveBtnX, activeTabY, tabW, tabH, hoverPassiveTab, passiveColor);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.abilities.passive").getString(), passiveBtnX + 10, activeTabY + 2, showPassiveAbilities ? textActiveC : textNormalC, false);

            // Draw Recessed Slot below the sub-tabs
            int slotY = formY + 20;
            int slotH = 150;
            UIHelper.drawRecessedSlot(graphics, formX + 5, slotY, formW - 10, slotH, borderC, slotC);

            String[] currentAbList = showPassiveAbilities ? PASSIVE_ABILITIES : ACTIVE_ABILITIES;

            int abY = slotY + 4;
            for (int i = 0; i < 8; i++) {
                int idx = i + abilitiesScrollOffset;
                if (idx >= currentAbList.length) break;
                String abName = currentAbList[idx];
                boolean active = hasAbility(abName);

                int rowX = formX + 10;
                int rowY = abY;
                int rowW = formW - 30;
                int rowH = 16;

                graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, 0x11FFFFFF);
                UIHelper.drawOutline(graphics, rowX, rowY, rowW, rowH, borderC);

                graphics.drawString(this.font, abName, rowX + 22, rowY + 4, textNormalC, false);
                graphics.fill(rowX + 6, rowY + 3, rowX + 16, rowY + 13, active ? 0xFF00FF00 : 0xFFFF0000);

                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + rowH) {
                    graphics.fill(rowX + 1, rowY + 1, rowX + rowW - 1, rowY + rowH - 1, 0x22FFFFFF);
                    String desc = ABILITY_DESCS.getOrDefault(abName, "No description.");
                    this.hoveredTooltip = List.of(
                        Component.literal("§e" + abName),
                        Component.literal("§7" + desc)
                    );
                }
                abY += 18;
            }

            int barX = formX + formW - 14;
            int barY = slotY + 4;
            graphics.fill(barX, barY, barX + 4, barY + 142, 0xFF111111);
            int barH = Math.max(10, (int) (142 * (8.0 / currentAbList.length)));
            int barOffset = (int) (142 * ((double) abilitiesScrollOffset / currentAbList.length));
            graphics.fill(barX, barY + barOffset, barX + 4, barY + barOffset + barH, 0xFFDFD0A0);

        } else if (activeTab.equals("AI")) {
            int leftW = formW / 2 - 5;
            int rightW = formW / 2 - 5;

            // Render Active Goals List (decreased height to align perfectly with available behaviors)
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.active_ai"), formX + 5, formY + 6, labelC);
            UIHelper.drawRecessedSlot(graphics, formX + 5, formY + 18, leftW, 95, borderC, slotC);

            int goalY = formY + 22;
            for (int i = 0; i < 5; i++) {
                int idx = i + activeGoalsScroll;
                if (idx >= selectedMob.aiGoals.size()) break;
                MobData.AIGoalData goal = selectedMob.aiGoals.get(idx);
                boolean selected = (idx == selectedGoalIndex);
                int rowColor = selected ? 0xFF555555 : 0x15FFFFFF;
                int rowX = formX + 8;
                int rowW = leftW - 18;

                graphics.fill(rowX, goalY - 1, rowX + rowW, goalY + 11, rowColor);
                UIHelper.drawOutline(graphics, rowX, goalY - 1, rowW, 12, borderC);

                graphics.drawString(this.font, (idx + 1) + ". " + goal.type, rowX + 5, goalY + 2, textC);
                
                boolean hoverDelCross = mouseX >= rowX + rowW - 12 && mouseX <= rowX + rowW && mouseY >= goalY && mouseY <= goalY + 10;
                graphics.drawString(this.font, "§c[X]", rowX + rowW - 12, goalY + 2, hoverDelCross ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                goalY += 14;
            }

            int activeBarX = formX + leftW - 8;
            graphics.fill(activeBarX, formY + 20, activeBarX + 3, formY + 110, 0xFF111111);
            if (selectedMob.aiGoals.size() > 5) {
                int barH = Math.max(10, (int) (90 * (5.0 / selectedMob.aiGoals.size())));
                int barOffset = (int) ((90 - barH) * ((double) activeGoalsScroll / (selectedMob.aiGoals.size() - 5)));
                graphics.fill(activeBarX, formY + 20 + barOffset, activeBarX + 3, formY + 20 + barOffset + barH, 0xFFDFD0A0);
            }

            // Render Available Behaviors list (decreased height to 76 to fit search field)
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.add_ai"), formX + formW / 2 + 5, formY + 6, labelC);
            
            if (behaviorSearchField != null) {
                behaviorSearchField.setX(formX + formW / 2 + 5);
                behaviorSearchField.setY(formY + 18);
                behaviorSearchField.setWidth(rightW);
            }
            
            UIHelper.drawRecessedSlot(graphics, formX + formW / 2 + 5, formY + 34, rightW, 76, borderC, slotC);

            int availY = formY + 37;
            for (int i = 0; i < 5; i++) {
                int idx = i + availableBehaviorsScroll;
                if (idx >= filteredBehaviors.size()) break;
                String pGoal = filteredBehaviors.get(idx);

                int rowX = formX + formW / 2 + 8;
                int rowW = rightW - 16;
                boolean hoverAvail = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= availY - 1 && mouseY <= availY + 11;
                
                graphics.fill(rowX, availY - 1, rowX + rowW, availY + 11, hoverAvail ? 0xFF444444 : 0x15FFFFFF);
                UIHelper.drawOutline(graphics, rowX, availY - 1, rowW, 12, borderC);
                graphics.drawString(this.font, pGoal, rowX + 5, availY + 2, textC);

                if (hoverAvail) {
                    String key = "gui.custom_mobs.goal_desc." + pGoal.toLowerCase(java.util.Locale.ROOT);
                    String desc = Component.translatable(key).getString();
                    if (desc.equals(key)) {
                        desc = GOAL_DESCS.getOrDefault(pGoal, "No description.");
                    }
                    this.hoveredTooltip = List.of(
                        Component.translatable("gui.custom_mobs.add_behavior", pGoal),
                        Component.literal("§7" + desc)
                    );
                }
                availY += 14;
            }

            int availBarX = formX + formW - 6;
            graphics.fill(availBarX, formY + 36, availBarX + 3, formY + 108, 0xFF111111);
            int totalItems = filteredBehaviors.size();
            int barH2 = Math.max(10, (int) (72 * (5.0 / Math.max(1, totalItems))));
            int barOffset2 = (int) (72 * ((double) availableBehaviorsScroll / Math.max(1, totalItems)));
            graphics.fill(availBarX, formY + 36 + barOffset2, availBarX + 3, formY + 36 + barOffset2 + barH2, 0xFFDFD0A0);

            // Up / Down buttons
            boolean hoverUp = mouseX >= formX + 5 && mouseX <= formX + 35 && mouseY >= formY + 120 && mouseY <= formY + 132;
            UIHelper.drawShadedButton(graphics, formX + 5, formY + 120, 30, 12, hoverUp, 0xFF3C3C3C);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.up"), formX + 11, formY + 122, 0xFFFFFFFF, false);

            boolean hoverDown = mouseX >= formX + 38 && mouseX <= formX + 68 && mouseY >= formY + 120 && mouseY <= formY + 132;
            UIHelper.drawShadedButton(graphics, formX + 38, formY + 120, 30, 12, hoverDown, 0xFF3C3C3C);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.dn"), formX + 44, formY + 122, 0xFFFFFFFF, false);

            int checkboxX = formX + 75;
            graphics.fill(checkboxX, formY + 120, checkboxX + 10, formY + 130, selectedMob.loopCombo ? 0xFF00FF00 : 0xFFFF0000);
            UIHelper.drawOutline(graphics, checkboxX, formY + 120, 10, 10, borderC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.loop_combo"), checkboxX + 14, formY + 121, labelC);

            if (mouseX >= checkboxX && mouseX <= checkboxX + 10 && mouseY >= formY + 120 && mouseY <= formY + 130) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.creator.tooltip.loop_combo"));
            }

            if (selectedGoalIndex >= 0) {
                int formH = panelH - 80;
                int minY = formY + 136;
                int maxY = formY + formH - 10;

                var goal = selectedMob.aiGoals.get(selectedGoalIndex);
                String type = goal.type;
                boolean showGroupField = isGroupGoal(type);

                int currentY = formY + 136 - aiParamsScroll;
                int rowHeight = 26;
                int lblX = formX + 10;

                // goalAnimationField
                if (currentY >= minY && currentY <= maxY) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.animation_override"), lblX, currentY, labelC);
                }
                currentY += rowHeight;

                // goalGroupField
                if (showGroupField) {
                    if (currentY >= minY && currentY <= maxY) {
                        String label = Component.translatable("gui.custom_mobs.creator.label.mob_group_name").getString();
                        if (goal.type.equals("AVOID_PLAYER_WEARING")) label = Component.translatable("gui.custom_mobs.creator.label.armor_item_id").getString();
                        else if (goal.type.equals("AVOID_MOB")) label = Component.translatable("gui.custom_mobs.creator.label.mob_registry_id").getString();
                        graphics.drawString(this.font, label, lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalDelayField
                if (currentY >= minY && currentY <= maxY) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.goal.delay_ticks"), lblX, currentY, labelC);
                }
                currentY += rowHeight;

                // goalParam1Field
                if (isParamActive(type, 1)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 1), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam2Field
                if (isParamActive(type, 2)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 2), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam3Field
                if (isParamActive(type, 3)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 3), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam4Field
                if (isParamActive(type, 4)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 4), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam5Field
                if (isParamActive(type, 5)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 5), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam6Field
                if (isParamActive(type, 6)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 6), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam7Field
                if (isParamActive(type, 7)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 7), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // goalParam8Field
                if (isParamActive(type, 8)) {
                    if (currentY >= minY && currentY <= maxY) {
                        graphics.drawString(this.font, getParamLabel(type, 8), lblX, currentY, labelC);
                    }
                    currentY += rowHeight;
                }

                // Draw parameter list scrollbar
                int visibleHeight = formH - 136;
                int contentHeight = getAiParamsContentHeight();
                if (visibleHeight < contentHeight) {
                    int trackX = formX + formW - 6;
                    int trackY = formY + 136;
                    int trackH = formH - 136;
                    graphics.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF111111);
                    int barH = Math.max(10, (int) (trackH * ((double) trackH / contentHeight)));
                    int barOffset = (int) ((trackH - barH) * ((double) aiParamsScroll / (contentHeight - trackH)));
                    graphics.fill(trackX, trackY + barOffset, trackX + 3, trackY + barOffset + barH, 0xFFDFD0A0);
                }

                // Draw Sound play preview button next to sound override field
                if (goalParam1Field.visible && (type.startsWith("MELEE") || type.startsWith("KNOCKBACK") || type.equals("RANGED") || type.startsWith("SUMMON_GROUND_ATTACK") || type.startsWith("AERIAL_RANGED") || type.equals("SHOTGUN_ATTACK") || type.equals("ORBITING_SHIELD"))) {
                    int playX = goalParam1Field.getX() + goalParam1Field.getWidth() + 4;
                    int playY = goalParam1Field.getY();
                    boolean hoverPlay = mouseX >= playX && mouseX <= playX + 12 && mouseY >= playY && mouseY <= playY + 10;
                    UIHelper.drawShadedButton(graphics, playX, playY, 12, 10, hoverPlay, 0xFF3C3C3C);
                    graphics.drawString(this.font, ">", playX + 3, playY + 1, 0xFF55FF55, false);
                }
            }

        } else if (activeTab.equals("Sounds")) {
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.sound.ambient"), formX + 10, formY + 12, labelC);
            renderSoundPlayPreview(graphics, ambientSoundField, formX + formW - 20, formY + 10, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.sound.step"), formX + 10, formY + 37, labelC);
            renderSoundPlayPreview(graphics, stepSoundField, formX + formW - 20, formY + 35, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.sound.hurt"), formX + 10, formY + 62, labelC);
            renderSoundPlayPreview(graphics, hurtSoundField, formX + formW - 20, formY + 60, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.sound.death"), formX + 10, formY + 87, labelC);
            renderSoundPlayPreview(graphics, deathSoundField, formX + formW - 20, formY + 85, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.sound.attack"), formX + 10, formY + 112, labelC);
            renderSoundPlayPreview(graphics, attackSoundField, formX + formW - 20, formY + 110, mouseX, mouseY);

        } else if (activeTab.equals("Loot")) {
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.xp_reward"), formX + 10, formY + 10, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.drops_on_death"), formX + 10, formY + 36, labelC);
            UIHelper.drawRecessedSlot(graphics, formX + 5, formY + 48, formW - 10, 56, borderC, slotC);
            
            int dropY = formY + 52;
            int itemsCount = selectedMob.loot.items.size();
            for (int i = 0; i < 3; i++) {
                int idx = i + lootScroll;
                if (idx >= itemsCount) break;
                MobData.LootItemData item = selectedMob.loot.items.get(idx);
                boolean selected = (idx == selectedLootIndex);
                int rowColor = selected ? 0xFF555555 : 0x15FFFFFF;
                int rowX = formX + 8;
                int rowW = formW - 32;
                int rowH = 18;

                graphics.fill(rowX, dropY - 1, rowX + rowW, dropY + rowH - 1, rowColor);
                UIHelper.drawOutline(graphics, rowX, dropY - 1, rowW, rowH, borderC);

                // Draw item icon with parsed custom NBT!
                var itemObj = BuiltInRegistries.ITEM.get(new ResourceLocation(item.itemId));
                ItemStack stack = new ItemStack(itemObj);
                if (item.nbt != null && !item.nbt.isEmpty()) {
                    try {
                        stack.setTag(net.minecraft.nbt.TagParser.parseTag(item.nbt));
                    } catch (Exception ignored) {}
                }
                graphics.renderItem(stack, rowX + 5, dropY);

                // Draw description text shifted right
                graphics.drawString(this.font, (idx + 1) + ". (" + item.chance + "%, qty: " + item.minCount + "-" + item.maxCount + ")", rowX + 26, dropY + 4, textC);

                // Tooltip on hovering the row or item icon
                if (mouseX >= rowX && mouseX <= rowX + rowW - 14 && mouseY >= dropY && mouseY <= dropY + 16) {
                    this.hoveredItemTooltip = stack;
                }

                boolean hoverDelCross = mouseX >= rowX + rowW - 12 && mouseX <= rowX + rowW && mouseY >= dropY && mouseY <= dropY + 14;
                graphics.drawString(this.font, "§c[X]", rowX + rowW - 12, dropY + 4, hoverDelCross ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                dropY += 18;
            }

            // Draw vertical scrollbar for the loot pool if there are more than 3 items
            int activeBarX = formX + formW - 12;
            int trackY = formY + 50;
            int trackH = 52;
            graphics.fill(activeBarX, trackY, activeBarX + 3, trackY + trackH, 0xFF111111);
            if (itemsCount > 3) {
                int barH = Math.max(10, (int) (trackH * (3.0 / itemsCount)));
                int barOffset = (int) ((trackH - barH) * ((double) lootScroll / (itemsCount - 3)));
                graphics.fill(activeBarX, trackY + barOffset, activeBarX + 3, trackY + barOffset + barH, 0xFFDFD0A0);
            }

            boolean hoverItemAdd = mouseX >= formX + 10 && mouseX <= formX + 110 && mouseY >= formY + 107 && mouseY <= formY + 121;
            UIHelper.drawShadedButton(graphics, formX + 10, formY + 107, 100, 14, hoverItemAdd, 0xFF229922);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.add_drop"), formX + 15, formY + 110, 0xFFFFFFFF, false);

            if (selectedLootIndex >= 0 && selectedLootIndex < selectedMob.loot.items.size()) {
                var item = selectedMob.loot.items.get(selectedLootIndex);
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.loot_item"), formX + 10, formY + 125, labelC);
                boolean hoverItemSelect = mouseX >= formX + 100 && mouseX <= formX + 220 && mouseY >= formY + 123 && mouseY <= formY + 135;
                UIHelper.drawShadedButton(graphics, formX + 100, formY + 123, 120, 12, hoverItemSelect, 0xFF3C3C3C);
                graphics.drawString(this.font, truncate(item.itemId, 16), formX + 105, formY + 125, 0xFFFFFFFF, false);

                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.loot_chance"), formX + 10, formY + 140, labelC);
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.loot_qty"), formX + 10, formY + 155, labelC);

                // Draw Looting Required Checkbox and level box label
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.looting_required"), formX + 10, formY + 172, labelC);
                boolean hoverLootingBox = mouseX >= formX + 110 && mouseX <= formX + 120 && mouseY >= formY + 170 && mouseY <= formY + 180;
                graphics.fill(formX + 110, formY + 170, formX + 120, formY + 180, item.lootingRequired ? 0xFF00FF00 : 0xFFFF0000);
                if (item.lootingRequired) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.looting_level"), formX + 125, formY + 172, labelC);
                }
            }

        } else if (activeTab.equals("Spawning")) {
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.spawn_rules"), formX + 10, formY + 4, labelC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.raid_spawner_only"), formX + 190, formY + 21, textC);
            boolean hoverRaidOnly = mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 19 && mouseY <= formY + 29;
            graphics.fill(formX + 280, formY + 19, formX + 290, formY + 29, selectedMob.spawnRules.raidOnly ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverRaidOnly) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.creator.raid_spawner_only"));
            }

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.surface_only"), formX + 190, formY + 36, textC);
            boolean hoverSurface = mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 34 && mouseY <= formY + 44;
            graphics.fill(formX + 280, formY + 34, formX + 290, formY + 44, selectedMob.spawnRules.surfaceOnly ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverSurface) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.creator.surface_only"));
            }

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.caves_only"), formX + 190, formY + 51, textC);
            boolean hoverCaves = mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 49 && mouseY <= formY + 59;
            graphics.fill(formX + 280, formY + 49, formX + 290, formY + 59, selectedMob.spawnRules.cavesOnly ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverCaves) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.creator.caves_only"));
            }

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.aquatic"), formX + 190, formY + 66, textC);
            boolean hoverAquatic = mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 64 && mouseY <= formY + 74;
            graphics.fill(formX + 280, formY + 64, formX + 290, formY + 74, selectedMob.spawnRules.aquatic ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverAquatic) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.creator.aquatic"));
            }

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.lava"), formX + 190, formY + 81, textC);
            boolean hoverLava = mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 79 && mouseY <= formY + 89;
            graphics.fill(formX + 280, formY + 79, formX + 290, formY + 89, selectedMob.spawnRules.lava ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverLava) {
                this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.creator.lava"));
            }
            
            int spawnLeftOffset = getSpawnLeftOffset();
            int btnX = formX + spawnLeftOffset - 4;

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.moon_phase"), formX + 10, formY + 21, textC);
            boolean hoverMoon = mouseX >= btnX && mouseX <= btnX + 70 && mouseY >= formY + 18 && mouseY <= formY + 30;
            UIHelper.drawShadedButton(graphics, btnX, formY + 18, 70, 12, hoverMoon, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.spawnRules.moonPhase.toUpperCase(), btnX + 5, formY + 20, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.time_of_day"), formX + 10, formY + 36, textC);
            boolean hoverTime = mouseX >= btnX && mouseX <= btnX + 70 && mouseY >= formY + 33 && mouseY <= formY + 45;
            UIHelper.drawShadedButton(graphics, btnX, formY + 33, 70, 12, hoverTime, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.spawnRules.timeOfDay.toUpperCase(), btnX + 5, formY + 35, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.dimension"), formX + 10, formY + 51, textC);
            boolean hoverDim = mouseX >= btnX && mouseX <= btnX + 70 && mouseY >= formY + 48 && mouseY <= formY + 60;
            UIHelper.drawShadedButton(graphics, btnX, formY + 48, 70, 12, hoverDim, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.spawnRules.dimension.toUpperCase(), btnX + 5, formY + 50, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.weather"), formX + 10, formY + 66, textC);
            boolean hoverWeather = mouseX >= btnX && mouseX <= btnX + 70 && mouseY >= formY + 63 && mouseY <= formY + 75;
            UIHelper.drawShadedButton(graphics, btnX, formY + 63, 70, 12, hoverWeather, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedMob.spawnRules.weather.toUpperCase(), btnX + 5, formY + 65, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.height"), formX + 10, formY + 87, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.spawn_block"), formX + 10, formY + 102, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.light"), formX + 10, formY + 117, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.pack_qty"), formX + 10, formY + 132, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.spawn_weight"), formX + 10, formY + 147, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.biome_filter"), formX + 10, formY + 162, labelC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.creator.label.structure"), formX + 10, formY + 177, labelC);

            int biomeY = formY + 192;
            for (int i = 0; i < selectedMob.spawnRules.biomes.size(); i++) {
                String b = selectedMob.spawnRules.biomes.get(i);
                graphics.drawString(this.font, "- " + truncate(b, 16), formX + 110, biomeY, textC);
                
                int xBtnX = formX + 215;
                boolean hoverDel = mouseX >= xBtnX && mouseX <= xBtnX + 12 && mouseY >= biomeY && mouseY < biomeY + 10;
                graphics.drawString(this.font, "§c[X]", xBtnX, biomeY, hoverDel ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                if (hoverDel) {
                    this.hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.biome_remove"));
                }
                biomeY += 11;
            }
        }
    }

    private void renderStatField(GuiGraphics graphics, String label, EditBox field, String key, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(this.font, label, x, y + 1, 0xFFFFFF55);
        double val = 0.0;
        try {
            val = Double.parseDouble(field.getValue());
        } catch (Exception ignored) {}

        double min = ModConfig.getMin("attribute_limits", key);
        double max = ModConfig.getMax("attribute_limits", key);

        if (val < min || val > max) {
            UIHelper.drawOutline(graphics, field.getX() - 1, field.getY() - 1, field.getWidth() + 2, field.getHeight() + 2, 0xFFFF0000);
        }
    }

    private void renderSoundPlayPreview(GuiGraphics graphics, EditBox field, int x, int y, int mouseX, int mouseY) {
        boolean hoverPlay = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 12;
        UIHelper.drawShadedButton(graphics, x, y, 16, 12, hoverPlay, 0xFF3C3C3C);
        graphics.drawString(this.font, ">", x + 6, y + 2, 0xFF00FF00, false);
        if (hoverPlay) {
            this.hoveredTooltip = List.of(Component.literal("Play sound preview event."));
        }
    }

    private void draw3DPreview(GuiGraphics graphics, int x, int y) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && previewEntity != null) {
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics, x, y, 22, (float) (x - width/2), (float) (y - height/2), previewEntity
                );
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int formX = left + 120;
        int formY = top + 42;
        int formW = (int) ((panelW - 130) * 0.6);

        int listX = left + 10;
        int listY = top + 25;
        int listW = 100;
        int listH = panelH - 55;

        if (showItemSelector) {
            int popW = 220;
            int popH = 190;
            int popX = (this.width - popW) / 2;
            int popY = (this.height - popH) / 2;

            if (mouseX < popX || mouseX > popX + popW || mouseY < popY || mouseY > popY + popH) {
                closeItemSelector();
                return true;
            }

            int tab1X = popX + 15;
            int tabY = popY + 25;
            if (mouseX >= tab1X && mouseX <= tab1X + 90 && mouseY >= tabY && mouseY <= tabY + 12) {
                selectAllItems = true;
                itemSelectorScroll = 0;
                itemSearchField.setValue("");
                itemSearchField.visible = true; itemSearchField.active = true;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int tab2X = popX + 115;
            if (mouseX >= tab2X && mouseX <= tab2X + 90 && mouseY >= tabY && mouseY <= tabY + 12) {
                selectAllItems = false;
                itemSelectorScroll = 0;
                itemSearchField.visible = false; itemSearchField.active = false;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            if (selectAllItems && itemSearchField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            List<ItemStack> currentList;
            int slotY;
            if (selectAllItems) {
                String query = itemSearchField.getValue().toLowerCase();
                currentList = selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).toList();
                
                int maxScroll = Math.max(0, (int) Math.ceil((double) currentList.size() / 9.0) - 4);
                if (itemSelectorScroll > maxScroll) {
                    itemSelectorScroll = maxScroll;
                }
                slotY = popY + 58;
            } else {
                var player = Minecraft.getInstance().player;
                currentList = new ArrayList<>();
                if (player != null) {
                    for (var stack : player.getInventory().items) {
                        if (!stack.isEmpty()) currentList.add(stack);
                    }
                }
                slotY = popY + 42;
            }

            int slotX = popX + 15;
            int itemIdx = itemSelectorScroll * 9;
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = itemIdx + row * 9 + col;
                    if (idx >= currentList.size()) break;
                    int sX = slotX + col * 21;
                    int sY = slotY + row * 21;
                    if (mouseX >= sX && mouseX <= sX + 20 && mouseY >= sY && mouseY <= sY + 20) {
                        if (itemSelectionCallback != null) {
                            itemSelectionCallback.accept(currentList.get(idx));
                        }
                        closeItemSelector();
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
            return true;
        }

        if (showSuggestions && !activeSuggestions.isEmpty() && activeField != null) {
            int dropX = activeField.getX();
            int dropY = activeField.getY() + activeField.getHeight() + 2;
            int dropW = activeField.getWidth();
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                int clickedRow = (int) ((mouseY - dropY) / rowH);
                int idx = clickedRow + suggestionsScrollOffset;
                if (idx < activeSuggestions.size()) {
                    if (activeField == biomeSearchField) {
                        String b = activeSuggestions.get(idx);
                        if (!selectedMob.spawnRules.biomes.contains(b)) {
                            selectedMob.spawnRules.biomes.add(b);
                        }
                        biomeSearchField.setValue("");
                    } else {
                        activeField.setValue(activeSuggestions.get(idx));
                    }
                    showSuggestions = false;
                    activeField = null;
                }
                return true;
            } else {
                showSuggestions = false;
                activeField = null;
            }
        }

        int viewportX = formX + formW + 10;
        int viewportW = left + panelW - 10 - viewportX;
        int viewportY = top + 42;
        int viewportH = panelH - 80;
        int saveX = viewportX + (viewportW - 110) / 2;
        int saveY = viewportY + viewportH - 25;
        if (mouseX >= saveX && mouseX <= saveX + 110 && mouseY >= saveY && mouseY <= saveY + 20) {
            saveCurrentMob();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int clickedIdx = (int) ((mouseY - listY - 5) / 12);
            if (clickedIdx >= 0 && clickedIdx < mobTemplates.size()) {
                selectMob(mobTemplates.get(clickedIdx));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        int addYSidebar = listY + listH + 4;
        if (mouseY >= addYSidebar && mouseY <= addYSidebar + 14) {
            if (mouseX >= listX && mouseX <= listX + 31) {
                saveTextFieldsToActiveMob();
                selectedMob = new MobData();
                selectedMob.id = "new_mob_" + System.currentTimeMillis();
                selectedMob.name = "New Custom Mob";
                this.init(this.minecraft, this.width, this.height);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= listX + 34 && mouseX <= listX + 66) {
                if (selectedMob != null) {
                    saveTextFieldsToActiveMob();
                    String json = new Gson().toJson(selectedMob);
                    MobData duplicated = new Gson().fromJson(json, MobData.class);
                    duplicated.name = duplicated.name + " Copy";
                    String baseId = duplicated.name.toLowerCase().trim().replace(" ", "_").replaceAll("[^a-z0-9_-]", "");
                    if (baseId.isEmpty()) baseId = "custom_mob";
                    String newId = baseId;
                    int counter = 1;
                    while (MobRegistry.loadedMobs.containsKey(newId) || MobRegistry.loadedProjectiles.containsKey(newId)) {
                        newId = baseId + "_" + counter;
                        counter++;
                    }
                    duplicated.id = newId;

                    MobRegistry.loadedMobs.put(duplicated.id, duplicated);
                    String dupJson = new Gson().toJson(duplicated);
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUtf(duplicated.id);
                    buf.writeUtf(dupJson, 262144);
                    NetworkManager.sendToServer(ModPackets.C2S_SAVE_MOB_TEMPLATE, buf);

                    selectedMob = duplicated;
                    this.init(this.minecraft, this.width, this.height);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
            if (mouseX >= listX + 69 && mouseX <= listX + listW) {
                if (selectedMob != null) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUtf(selectedMob.id);
                    NetworkManager.sendToServer(ModPackets.C2S_DELETE_MOB_TEMPLATE, buf);

                    MobRegistry.loadedMobs.remove(selectedMob.id);
                    mobTemplates.remove(selectedMob);
                    selectedMob = mobTemplates.isEmpty() ? null : mobTemplates.get(0);
                    this.init(this.minecraft, this.width, this.height);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }

        List<TabBounds> tabBoundsList = calculateTabBounds(left, top);
        for (TabBounds tb : tabBoundsList) {
            if (mouseX >= tb.x && mouseX <= tb.x + tb.w && mouseY >= tb.y && mouseY <= tb.y + tb.h) {
                selectTab(tb.id);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (activeTab.equals("General")) {
            if (mouseX >= formX + 120 && mouseX <= formX + 130 && mouseY >= formY + 48 && mouseY <= formY + 58) {
                selectedMob.billboardName = !selectedMob.billboardName;
                return true;
            }
            if (mouseX >= formX + 260 && mouseX <= formX + 340 && mouseY >= formY + 47 && mouseY <= formY + 61) {
                java.util.List<String> colors = java.util.List.of(
                    "white", "red", "green", "blue", "yellow", "aqua", "gold", "gray",
                    "dark_red", "dark_green", "dark_blue", "dark_purple", "light_purple"
                );
                int idx = colors.indexOf(selectedMob.nameColor.toLowerCase());
                if (idx == -1) idx = 0;
                selectedMob.nameColor = colors.get((idx + 1) % colors.size());
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 120 && mouseX <= formX + 130 && mouseY >= formY + 62 && mouseY <= formY + 72) {
                selectedMob.isFlying = !selectedMob.isFlying;
                this.init(this.minecraft, this.width, this.height);
                return true;
            }
            if (mouseX >= formX + 100 && mouseX <= formX + 180 && mouseY >= formY + 75 && mouseY <= formY + 89) {
                String curr = selectedMob.behaviorMode;
                if (curr.equalsIgnoreCase("hostile")) selectedMob.behaviorMode = "neutral";
                else if (curr.equalsIgnoreCase("neutral")) selectedMob.behaviorMode = "passive";
                else selectedMob.behaviorMode = "hostile";
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 120 && mouseX <= formX + 130 && mouseY >= formY + 94 && mouseY <= formY + 104) {
                selectedMob.tameable = !selectedMob.tameable;
                this.init(this.minecraft, this.width, this.height);
                return true;
            }
            if (selectedMob.tameable && mouseX >= formX + 10 && mouseX <= formX + 130 && mouseY >= formY + 107 && mouseY <= formY + 119) {
                openItemSelector(stack -> {
                    selectedMob.tamingItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    this.init(this.minecraft, this.width, this.height);
                });
                return true;
            }

        } else if (activeTab.equals("Model")) {
            if (mouseX >= formX + 100 && mouseX <= formX + 180 && mouseY >= formY + 21 && mouseY <= formY + 35) {
                String type = selectedMob.modelType;
                if (type.equalsIgnoreCase("vanilla")) selectedMob.modelType = "geckolib";
                else if (type.equalsIgnoreCase("geckolib")) selectedMob.modelType = "mcmodel";
                else if (type.equalsIgnoreCase("mcmodel")) selectedMob.modelType = "java";
                else selectedMob.modelType = "vanilla";
                this.init(this.minecraft, this.width, this.height);
                return true;
            }
            int sliderX = formX + 110;
            int sliderY = formY + 100;
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= sliderY && mouseY <= sliderY + 10) {
                animSpeedSlider = (float) ((mouseX - sliderX) / 100.0);
                saveTextFieldsToActiveMob();
                return true;
            }
            int scaleY = formY + 115;
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= scaleY && mouseY <= scaleY + 10) {
                modelScaleSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveMob();
                return true;
            }
            int hbWidthY = formY + 130;
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= hbWidthY && mouseY <= hbWidthY + 10) {
                hitboxWidthSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveMob();
                return true;
            }
            int hbHeightY = formY + 145;
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= hbHeightY && mouseY <= hbHeightY + 10) {
                hitboxHeightSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveMob();
                return true;
            }
            int hbShowY = formY + 160;
            if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= hbShowY && mouseY <= hbShowY + 10) {
                ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug = !ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

        } else if (activeTab.equals("Abilities")) {
            int activeTabY = formY + 5;
            int tabW = 60;
            int tabH = 12;

            // Click Active tab
            int activeBtnX = formX + 8;
            if (mouseX >= activeBtnX && mouseX <= activeBtnX + tabW && mouseY >= activeTabY && mouseY <= activeTabY + tabH) {
                if (showPassiveAbilities) {
                    showPassiveAbilities = false;
                    abilitiesScrollOffset = 0;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }

            // Click Passive tab
            int passiveBtnX = formX + 72;
            if (mouseX >= passiveBtnX && mouseX <= passiveBtnX + tabW && mouseY >= activeTabY && mouseY <= activeTabY + tabH) {
                if (!showPassiveAbilities) {
                    showPassiveAbilities = true;
                    abilitiesScrollOffset = 0;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }

            int slotY = formY + 20;
            String[] currentAbList = showPassiveAbilities ? PASSIVE_ABILITIES : ACTIVE_ABILITIES;
            int abY = slotY + 4;
            for (int i = 0; i < 8; i++) {
                int idx = i + abilitiesScrollOffset;
                if (idx >= currentAbList.length) break;
                int rowX = formX + 10;
                int rowY = abY;
                int rowW = formW - 30;
                int rowH = 16;
                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + rowH) {
                    toggleAbility(currentAbList[idx]);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                abY += 18;
            }

        } else if (activeTab.equals("AI")) {
            saveTextFieldsToActiveMob();
            int leftW = formW / 2 - 5;
            int rightW = formW / 2 - 5;

            // Click check for AI parameters scrollbar
            if (selectedGoalIndex >= 0) {
                int formH = panelH - 80;
                int visibleHeight = formH - 136;
                int contentHeight = getAiParamsContentHeight();
                if (visibleHeight < contentHeight) {
                    int trackX = formX + formW - 6;
                    int trackY = formY + 136;
                    int trackH = formH - 136;
                    if (mouseX >= trackX && mouseX <= trackX + 3 && mouseY >= trackY && mouseY <= trackY + trackH) {
                        this.isDraggingAiConfigScroll = true;
                        double pct = (mouseY - trackY) / (double) trackH;
                        int maxScroll = contentHeight - visibleHeight;
                        this.aiParamsScroll = Math.max(0, Math.min(maxScroll, (int) (pct * maxScroll)));
                        updateAIFieldsY();
                        return true;
                    }
                }
            }

            if (selectedGoalIndex >= 0) {
                var goal = selectedMob.aiGoals.get(selectedGoalIndex);
                String type = goal.type;
                if (goalParam1Field.visible && (type.startsWith("MELEE") || type.startsWith("KNOCKBACK") || type.equals("RANGED") || type.startsWith("SUMMON_GROUND_ATTACK") || type.startsWith("AERIAL_RANGED") || type.equals("SHOTGUN_ATTACK") || type.equals("ORBITING_SHIELD"))) {
                    int playX = goalParam1Field.getX() + goalParam1Field.getWidth() + 4;
                    int playY = goalParam1Field.getY();
                    if (mouseX >= playX && mouseX <= playX + 12 && mouseY >= playY && mouseY <= playY + 10) {
                        String soundId = goalParam1Field.getValue().trim();
                        if (!soundId.isEmpty()) {
                            playSoundPreview(soundId);
                        }
                        return true;
                    }
                }
            }

            // Click Active Goals List
            int goalY = formY + 22;
            for (int i = 0; i < 5; i++) {
                int idx = i + activeGoalsScroll;
                if (idx >= selectedMob.aiGoals.size()) break;
                int rowX = formX + 8;
                int rowW = leftW - 18;

                if (mouseX >= rowX + rowW - 12 && mouseX <= rowX + rowW && mouseY >= goalY && mouseY <= goalY + 10) {
                    selectedMob.aiGoals.remove(idx);
                    selectedGoalIndex = -1;
                    recalculateGoalPriorities();
                    return true;
                }

                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= goalY - 1 && mouseY <= goalY + 11) {
                    selectedGoalIndex = idx;
                    isDraggingGoal = true;
                    draggedGoalIndex = idx;
                    this.init(this.minecraft, this.width, this.height);
                    return true;
                }
                goalY += 14;
            }

            // Up / Down buttons
            if (mouseX >= formX + 5 && mouseX <= formX + 35 && mouseY >= formY + 120 && mouseY <= formY + 132) {
                moveGoalUp();
                return true;
            }
            if (mouseX >= formX + 38 && mouseX <= formX + 68 && mouseY >= formY + 120 && mouseY <= formY + 132) {
                moveGoalDown();
                return true;
            }
            if (mouseX >= formX + 75 && mouseX <= formX + 85 && mouseY >= formY + 120 && mouseY <= formY + 130) {
                selectedMob.loopCombo = !selectedMob.loopCombo;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // Click Available Behaviors List
            int availY = formY + 37;
            for (int i = 0; i < 5; i++) {
                int idx = i + availableBehaviorsScroll;
                if (idx >= filteredBehaviors.size()) break;
                String pGoal = filteredBehaviors.get(idx);

                int rowX = formX + formW / 2 + 8;
                int rowW = rightW - 16;
                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= availY - 1 && mouseY <= availY + 11) {
                    MobData.AIGoalData goal = new MobData.AIGoalData();
                    goal.type = pGoal;
                    goal.priority = selectedMob.aiGoals.size() + 1;
                    selectedMob.aiGoals.add(goal);
                    this.init(this.minecraft, this.width, this.height);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                availY += 14;
            }

        } else if (activeTab.equals("Sounds")) {
            int playX = formX + formW - 20;
            if (mouseX >= playX && mouseX <= playX + 16) {
                if (mouseY >= formY + 10 && mouseY <= formY + 22) playSoundPreview(ambientSoundField.getValue());
                else if (mouseY >= formY + 35 && mouseY <= formY + 47) playSoundPreview(stepSoundField.getValue());
                else if (mouseY >= formY + 60 && mouseY <= formY + 72) playSoundPreview(hurtSoundField.getValue());
                else if (mouseY >= formY + 85 && mouseY <= formY + 97) playSoundPreview(deathSoundField.getValue());
                else if (mouseY >= formY + 110 && mouseY <= formY + 122) playSoundPreview(attackSoundField.getValue());
            }

        } else if (activeTab.equals("Loot")) {
            saveTextFieldsToActiveMob();
            int dropY = formY + 52;
            int itemsCount = selectedMob.loot.items.size();
            for (int i = 0; i < 3; i++) {
                int idx = i + lootScroll;
                if (idx >= itemsCount) break;
                int rowX = formX + 8;
                int rowW = formW - 32;
                int rowH = 18;

                // Delete drop
                if (mouseX >= rowX + rowW - 12 && mouseX <= rowX + rowW && mouseY >= dropY && mouseY <= dropY + rowH - 4) {
                    selectedMob.loot.items.remove(idx);
                    selectedLootIndex = -1;
                    this.init(this.minecraft, this.width, this.height);
                    return true;
                }

                // Select drop
                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= dropY - 1 && mouseY <= dropY + rowH - 1) {
                    selectedLootIndex = idx;
                    this.init(this.minecraft, this.width, this.height);
                    return true;
                }
                dropY += 18;
            }

            // Click + Add Drop Item
            if (mouseX >= formX + 10 && mouseX <= formX + 110 && mouseY >= formY + 107 && mouseY <= formY + 121) {
                openItemSelector(stack -> {
                    MobData.LootItemData loot = new MobData.LootItemData();
                    loot.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (stack.hasTag() && stack.getTag() != null) {
                        loot.nbt = stack.getTag().toString();
                    } else {
                        loot.nbt = "";
                    }
                    loot.chance = 50.0;
                    loot.minCount = 1;
                    loot.maxCount = 2;
                    selectedMob.loot.items.add(loot);
                    selectedLootIndex = selectedMob.loot.items.size() - 1;
                    lootScroll = Math.max(0, selectedMob.loot.items.size() - 3);
                    this.init(this.minecraft, this.width, this.height);
                });
                return true;
            }

            // Click Item selector button to change item type
            if (selectedLootIndex >= 0 && selectedLootIndex < selectedMob.loot.items.size()) {
                if (mouseX >= formX + 100 && mouseX <= formX + 220 && mouseY >= formY + 123 && mouseY <= formY + 135) {
                    openItemSelector(stack -> {
                        var item = selectedMob.loot.items.get(selectedLootIndex);
                        item.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (stack.hasTag() && stack.getTag() != null) {
                            item.nbt = stack.getTag().toString();
                        } else {
                            item.nbt = "";
                        }
                        this.init(this.minecraft, this.width, this.height);
                    });
                    return true;
                }

                // Click Looting Required checkbox
                var item = selectedMob.loot.items.get(selectedLootIndex);
                if (mouseX >= formX + 110 && mouseX <= formX + 120 && mouseY >= formY + 170 && mouseY <= formY + 180) {
                    item.lootingRequired = !item.lootingRequired;
                    if (!item.lootingRequired) {
                        item.lootingLevel = 0;
                        lootLevelField.setValue("");
                    }
                    showFieldsForTab();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }

        } else if (activeTab.equals("Spawning")) {
            if (mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 19 && mouseY <= formY + 29) {
                selectedMob.spawnRules.raidOnly = !selectedMob.spawnRules.raidOnly;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 34 && mouseY <= formY + 44) {
                selectedMob.spawnRules.surfaceOnly = !selectedMob.spawnRules.surfaceOnly;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 49 && mouseY <= formY + 59) {
                selectedMob.spawnRules.cavesOnly = !selectedMob.spawnRules.cavesOnly;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 64 && mouseY <= formY + 74) {
                selectedMob.spawnRules.aquatic = !selectedMob.spawnRules.aquatic;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= formX + 280 && mouseX <= formX + 290 && mouseY >= formY + 79 && mouseY <= formY + 89) {
                selectedMob.spawnRules.lava = !selectedMob.spawnRules.lava;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int spawnLeftOffset = getSpawnLeftOffset();
            int btnX = formX + spawnLeftOffset - 4;
            if (mouseX >= btnX && mouseX <= btnX + 70) {
                if (mouseY >= formY + 18 && mouseY <= formY + 30) {
                    String moon = selectedMob.spawnRules.moonPhase;
                    if (moon.equalsIgnoreCase("any")) selectedMob.spawnRules.moonPhase = "full";
                    else if (moon.equalsIgnoreCase("full")) selectedMob.spawnRules.moonPhase = "new";
                    else if (moon.equalsIgnoreCase("new")) selectedMob.spawnRules.moonPhase = "quarters";
                    else selectedMob.spawnRules.moonPhase = "any";
                    return true;
                }
                if (mouseY >= formY + 33 && mouseY <= formY + 45) {
                    String time = selectedMob.spawnRules.timeOfDay;
                    if (time.equalsIgnoreCase("any")) selectedMob.spawnRules.timeOfDay = "day";
                    else if (time.equalsIgnoreCase("day")) selectedMob.spawnRules.timeOfDay = "night";
                    else selectedMob.spawnRules.timeOfDay = "any";
                    return true;
                }
                if (mouseY >= formY + 48 && mouseY <= formY + 60) {
                    String dim = selectedMob.spawnRules.dimension;
                    if (dim.equalsIgnoreCase("any")) selectedMob.spawnRules.dimension = "overworld";
                    else if (dim.equalsIgnoreCase("overworld")) selectedMob.spawnRules.dimension = "nether";
                    else if (dim.equalsIgnoreCase("nether")) selectedMob.spawnRules.dimension = "end";
                    else selectedMob.spawnRules.dimension = "any";
                    return true;
                }
                if (mouseY >= formY + 63 && mouseY <= formY + 75) {
                    String weather = selectedMob.spawnRules.weather;
                    if (weather.equalsIgnoreCase("any")) selectedMob.spawnRules.weather = "clear";
                    else if (weather.equalsIgnoreCase("clear")) selectedMob.spawnRules.weather = "rain";
                    else if (weather.equalsIgnoreCase("rain")) selectedMob.spawnRules.weather = "thunder";
                    else selectedMob.spawnRules.weather = "any";
                    return true;
                }
            }

            int biomeY = formY + 192;
            for (int i = 0; i < selectedMob.spawnRules.biomes.size(); i++) {
                int xBtnX = formX + 215;
                if (mouseX >= xBtnX && mouseX <= xBtnX + 12 && mouseY >= biomeY && mouseY < biomeY + 10) {
                    selectedMob.spawnRules.biomes.remove(i);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                biomeY += 11;
            }

        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playSoundPreview(String soundId) {
        if (soundId == null || soundId.isEmpty()) return;
        try {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var event = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
                if (event != null) {
                    player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), event, SoundSource.MASTER, 1.0F, 1.0F, false);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDraggingAiConfigScroll) {
            this.isDraggingAiConfigScroll = false;
            return true;
        }
        if (isDraggingGoal && activeTab.equals("AI")) {
            saveTextFieldsToActiveMob();
            int left = (this.width - this.panelW) / 2;
            int top = (this.height - this.panelH) / 2;
            int formY = top + 42;

            int dropIdx = (int) ((mouseY - (formY + 22)) / 14) + activeGoalsScroll;
            if (dropIdx >= 0 && dropIdx < selectedMob.aiGoals.size() && dropIdx != draggedGoalIndex) {
                var goal = selectedMob.aiGoals.remove(draggedGoalIndex);
                selectedMob.aiGoals.add(dropIdx, goal);
                selectedGoalIndex = dropIdx;
                recalculateGoalPriorities();
            }
            isDraggingGoal = false;
            draggedGoalIndex = -1;
            this.init(this.minecraft, this.width, this.height);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingAiConfigScroll && activeTab.equals("AI") && selectedGoalIndex >= 0) {
            int left = (this.width - this.panelW) / 2;
            int top = (this.height - this.panelH) / 2;
            int formY = top + 42;
            int formH = panelH - 80;
            int visibleHeight = formH - 136;
            int contentHeight = getAiParamsContentHeight();
            if (visibleHeight < contentHeight) {
                int trackY = formY + 136;
                int trackH = formH - 136;
                double pct = (mouseY - trackY) / (double) trackH;
                int maxScroll = contentHeight - visibleHeight;
                this.aiParamsScroll = Math.max(0, Math.min(maxScroll, (int) (pct * maxScroll)));
                updateAIFieldsY();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (var widget : this.children()) {
            if (widget instanceof EditBox editBox && editBox.isFocused()) {
                if (editBox.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER || GLFW_KEY_KP_ENTER
            if (this.biomeSearchField != null && this.biomeSearchField.isFocused()) {
                String val = this.biomeSearchField.getValue().trim();
                if (!val.isEmpty()) {
                    String finalVal = val;
                    if (showSuggestions && !activeSuggestions.isEmpty()) {
                        finalVal = activeSuggestions.get(0);
                    }
                    if (!selectedMob.spawnRules.biomes.contains(finalVal)) {
                        selectedMob.spawnRules.biomes.add(finalVal);
                    }
                    this.biomeSearchField.setValue("");
                }
                return true;
            }
        }
        // Also route keyPressed directly to focused edit boxes to handle things like Backspace/Arrow keys
        for (var widget : this.children()) {
            if (widget instanceof EditBox editBox && editBox.isFocused()) {
                if (editBox.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveGoalUp() {
        saveTextFieldsToActiveMob();
        if (selectedGoalIndex > 0 && selectedGoalIndex < selectedMob.aiGoals.size()) {
            var goal = selectedMob.aiGoals.remove(selectedGoalIndex);
            selectedMob.aiGoals.add(selectedGoalIndex - 1, goal);
            selectedGoalIndex--;
            recalculateGoalPriorities();
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void moveGoalDown() {
        saveTextFieldsToActiveMob();
        if (selectedGoalIndex >= 0 && selectedGoalIndex < selectedMob.aiGoals.size() - 1) {
            var goal = selectedMob.aiGoals.remove(selectedGoalIndex);
            selectedMob.aiGoals.add(selectedGoalIndex + 1, goal);
            selectedGoalIndex++;
            recalculateGoalPriorities();
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void recalculateGoalPriorities() {
        for (int i = 0; i < selectedMob.aiGoals.size(); i++) {
            selectedMob.aiGoals.get(i).priority = i + 1;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 2) + "..";
    }

    private String truncateByWidth(String text, int width) {
        if (text == null) return "";
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, width - 8) + "..";
    }

    public static class SpawnerEditScreen extends Screen {
        private final BlockPos pos;
        private int rate;
        private int radius;
        private int maxAlive;
        private int dayNight;
        private int playerDist;
        private String selectedTemplateId;
        private int eliteChance;
        private boolean redstonePulseOnly;
        private int spawnerCooldown;

        private EditBox rateField;
        private EditBox radiusField;
        private EditBox maxAliveField;
        private EditBox playerDistField;
        private EditBox eliteChanceField;
        private EditBox spawnerCooldownField;

        private List<Component> hoveredTooltip = null;

        private final List<String> availableTemplates = new ArrayList<>();
        private int templatesScroll = 0;
        private boolean isDraggingScrollbar = false;

        public SpawnerEditScreen(BlockPos pos, int rate, int radius, int maxAlive, int dayNight, int playerDist, String templateId, int eliteChance, boolean redstonePulseOnly, int spawnerCooldown) {
            super(Component.translatable("gui.custom_mobs.spawner_edit.title"));
            this.pos = pos;
            this.rate = rate;
            this.radius = radius;
            this.maxAlive = maxAlive;
            this.dayNight = dayNight;
            this.playerDist = playerDist;
            this.selectedTemplateId = templateId;
            this.eliteChance = eliteChance;
            this.redstonePulseOnly = redstonePulseOnly;
            this.spawnerCooldown = spawnerCooldown;
            for (String key : MobRegistry.loadedMobs.keySet()) {
                if (!key.startsWith("__proj_preview_")) {
                    this.availableTemplates.add(key);
                }
            }
            this.availableTemplates.addAll(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(net.minecraft.resources.ResourceLocation::toString).toList());
        }

        private void updateScrollFromMouse(double mouseY) {
            int left = (this.width - 320) / 2;
            int top = (this.height - 220) / 2;
            int listY = top + 25;
            int listH = 160;
            int barH = Math.max(10, (int) ((listH - 10) * (10.0 / availableTemplates.size())));
            int trackH = listH - 10 - barH;
            if (trackH > 0) {
                double relativeY = mouseY - (listY + 5) - (barH / 2.0);
                double pct = relativeY / (double) trackH;
                int maxScroll = availableTemplates.size() - 10;
                templatesScroll = Math.max(0, Math.min(maxScroll, (int) Math.round(pct * maxScroll)));
            }
        }

        @Override
        protected void init() {
            int left = (this.width - 320) / 2;
            int top = (this.height - 220) / 2;

            this.rateField = new EditBox(this.font, left + 90, top + 30, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.rate"));
            this.rateField.setValue(String.valueOf(rate));
            this.radiusField = new EditBox(this.font, left + 90, top + 50, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.radius"));
            this.radiusField.setValue(String.valueOf(radius));
            this.maxAliveField = new EditBox(this.font, left + 90, top + 70, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.max_mobs"));
            this.maxAliveField.setValue(String.valueOf(maxAlive));
            this.playerDistField = new EditBox(this.font, left + 90, top + 90, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.player_dist"));
            this.playerDistField.setValue(String.valueOf(playerDist));
            this.eliteChanceField = new EditBox(this.font, left + 90, top + 110, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.elite_chance"));
            this.eliteChanceField.setValue(String.valueOf(eliteChance));
            this.spawnerCooldownField = new EditBox(this.font, left + 90, top + 130, 50, 12, Component.translatable("gui.custom_mobs.spawner_edit.cooldown"));
            this.spawnerCooldownField.setValue(String.valueOf(spawnerCooldown));

            this.rateField.setBordered(false);
            this.radiusField.setBordered(false);
            this.maxAliveField.setBordered(false);
            this.playerDistField.setBordered(false);
            this.eliteChanceField.setBordered(false);
            this.spawnerCooldownField.setBordered(false);

            this.addRenderableWidget(this.rateField);
            this.addRenderableWidget(this.radiusField);
            this.addRenderableWidget(this.maxAliveField);
            this.addRenderableWidget(this.playerDistField);
            this.addRenderableWidget(this.eliteChanceField);
            this.addRenderableWidget(this.spawnerCooldownField);

            this.addRenderableWidget(Button.builder(Component.translatable("gui.custom_mobs.spawner_edit.save"), btn -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(pos);
                buf.writeInt(parseIntSafe(rateField.getValue(), rate));
                buf.writeInt(parseIntSafe(radiusField.getValue(), radius));
                buf.writeInt(parseIntSafe(maxAliveField.getValue(), maxAlive));
                buf.writeInt(dayNight);
                buf.writeInt(parseIntSafe(playerDistField.getValue(), playerDist));
                buf.writeUtf(selectedTemplateId != null ? selectedTemplateId : "");
                buf.writeInt(parseIntSafe(eliteChanceField.getValue(), eliteChance));
                buf.writeBoolean(redstonePulseOnly);
                buf.writeInt(parseIntSafe(spawnerCooldownField.getValue(), spawnerCooldown));
                NetworkManager.sendToServer(ModPackets.C2S_SAVE_SPAWNER_SETTINGS, buf);
                Minecraft.getInstance().setScreen(null);
            }).bounds(left + 20, top + 190, 110, 20).build());
        }

        private static int parseIntSafe(String val, int def) {
            try {
                return Integer.parseInt(val.trim());
            } catch (Exception e) {
                return def;
            }
        }

        @Override
        public void tick() {
            rateField.tick();
            radiusField.tick();
            maxAliveField.tick();
            playerDistField.tick();
            eliteChanceField.tick();
            spawnerCooldownField.tick();
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            int maxScroll = Math.max(0, availableTemplates.size() - 10);
            templatesScroll = Math.max(0, Math.min(maxScroll, templatesScroll - (int) amount));
            return true;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = (this.width - 320) / 2;
            int top = (this.height - 220) / 2;

            int listX = left + 160;
            int listY = top + 25;
            int listW = 140;
            int listH = 160;
            int barX = listX + listW - 10;

            if (button == 0 && availableTemplates.size() > 10) {
                if (mouseX >= barX - 4 && mouseX <= barX + 8 && mouseY >= listY + 5 && mouseY <= listY + listH - 5) {
                    this.isDraggingScrollbar = true;
                    updateScrollFromMouse(mouseY);
                    return true;
                }
            }

            if (mouseX >= left + 90 && mouseX <= left + 140 && mouseY >= top + 150 && mouseY <= top + 162) {
                dayNight = (dayNight + 1) % 3;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            if (mouseX >= left + 90 && mouseX <= left + 100 && mouseY >= top + 170 && mouseY <= top + 180) {
                redstonePulseOnly = !redstonePulseOnly;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + 160) {
                int clickedRow = (int) ((mouseY - listY - 5) / 14);
                int idx = clickedRow + templatesScroll;
                if (idx >= 0 && idx < availableTemplates.size()) {
                    selectedTemplateId = availableTemplates.get(idx);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.isDraggingScrollbar) {
                updateScrollFromMouse(mouseY);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                this.isDraggingScrollbar = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(graphics);
            int left = (this.width - 320) / 2;
            int top = (this.height - 220) / 2;

            int borderC = 0xFFDFD0A0; // Gold
            int bgC = 0xFF2D2D2D;
            int slotC = 0xFF1C1C1C;
            int textActiveC = 0xFFD4AF37;
            int textNormalC = 0xFFCCCCCC;

            UIHelper.drawBeveledPanel(graphics, left, top, 320, 220, borderC, bgC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.header").getString(), left + 15, top + 10, textActiveC, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.rate").getString(), left + 15, top + 32, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.radius").getString(), left + 15, top + 52, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.max_mobs").getString(), left + 15, top + 72, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.player_dist").getString(), left + 15, top + 92, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.elite_chance").getString(), left + 15, top + 112, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.cooldown").getString(), left + 15, top + 132, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.day_night").getString(), left + 15, top + 152, 0xFFFFFFFF, false);
            String cycleText = Component.translatable("gui.custom_mobs.spawner_edit.both").getString();
            if (dayNight == 1) cycleText = Component.translatable("gui.custom_mobs.spawner_edit.day_only").getString();
            else if (dayNight == 2) cycleText = Component.translatable("gui.custom_mobs.spawner_edit.night_only").getString();
            boolean hoverCycle = mouseX >= left + 90 && mouseX <= left + 140 && mouseY >= top + 150 && mouseY <= top + 162;
            UIHelper.drawShadedButton(graphics, left + 90, top + 150, 50, 12, hoverCycle, 0xFF3C3C3C);
            graphics.drawString(this.font, cycleText, left + 93, top + 152, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.redstone_pulse").getString(), left + 15, top + 172, 0xFFFFFFFF, false);
            int checkboxX = left + 90;
            int checkboxY = top + 170;
            graphics.fill(checkboxX, checkboxY, checkboxX + 10, checkboxY + 10, redstonePulseOnly ? 0xFF00FF00 : 0xFFFF0000);
            UIHelper.drawOutline(graphics, checkboxX, checkboxY, 10, 10, borderC);

            int listX = left + 160;
            int listY = top + 25;
            int listW = 140;
            int listH = 160;

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.spawner_edit.select_template").getString(), listX, top + 12, textActiveC, false);
            UIHelper.drawRecessedSlot(graphics, listX, listY, listW, listH, borderC, slotC);

            int rowY = listY + 5;
            for (int i = 0; i < 10; i++) {
                int idx = i + templatesScroll;
                if (idx >= availableTemplates.size()) break;
                String tId = availableTemplates.get(idx);
                boolean selected = tId.equals(selectedTemplateId);

                int rowColor = selected ? 0xFF555555 : 0x15FFFFFF;
                graphics.fill(listX + 5, rowY - 1, listX + listW - 15, rowY + 11, rowColor);
                UIHelper.drawOutline(graphics, listX + 5, rowY - 1, listW - 20, 12, borderC);

                MobData m = MobRegistry.loadedMobs.get(tId);
                String displayName = (m != null) ? m.name : tId;
                graphics.drawString(this.font, truncate(displayName, 14), listX + 9, rowY + 1, selected ? textActiveC : textNormalC, false);

                rowY += 14;
            }

            int barX = listX + listW - 10;
            graphics.fill(barX, listY + 5, barX + 3, listY + listH - 5, 0xFF111111);
            if (availableTemplates.size() > 10) {
                int barH = Math.max(10, (int) ((listH - 10) * (10.0 / availableTemplates.size())));
                int barOffset = (int) ((listH - 10 - barH) * ((double) templatesScroll / (availableTemplates.size() - 10)));
                graphics.fill(barX, listY + 5 + barOffset, barX + 3, listY + 5 + barOffset + barH, 0xFFDFD0A0);
            }

            // Draw custom recessed slots behind active EditBox fields inside SpawnerEditScreen too
            if (rateField.visible) {
                UIHelper.drawRecessedSlot(graphics, rateField.getX() - 4, rateField.getY() - 3, rateField.getWidth() + 8, rateField.getHeight() + 6, borderC, slotC);
            }
            if (radiusField.visible) {
                UIHelper.drawRecessedSlot(graphics, radiusField.getX() - 4, radiusField.getY() - 3, radiusField.getWidth() + 8, radiusField.getHeight() + 6, borderC, slotC);
            }
            if (maxAliveField.visible) {
                UIHelper.drawRecessedSlot(graphics, maxAliveField.getX() - 4, maxAliveField.getY() - 3, maxAliveField.getWidth() + 8, maxAliveField.getHeight() + 6, borderC, slotC);
            }
            if (playerDistField.visible) {
                UIHelper.drawRecessedSlot(graphics, playerDistField.getX() - 4, playerDistField.getY() - 3, playerDistField.getWidth() + 8, playerDistField.getHeight() + 6, borderC, slotC);
            }
            if (eliteChanceField.visible) {
                UIHelper.drawRecessedSlot(graphics, eliteChanceField.getX() - 4, eliteChanceField.getY() - 3, eliteChanceField.getWidth() + 8, eliteChanceField.getHeight() + 6, borderC, slotC);
            }
            if (spawnerCooldownField.visible) {
                UIHelper.drawRecessedSlot(graphics, spawnerCooldownField.getX() - 4, spawnerCooldownField.getY() - 3, spawnerCooldownField.getWidth() + 8, spawnerCooldownField.getHeight() + 6, borderC, slotC);
            }

            hoveredTooltip = null;
            if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 30 && mouseY <= top + 42) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.rate"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 50 && mouseY <= top + 62) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.radius"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 70 && mouseY <= top + 82) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.max_mobs"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 90 && mouseY <= top + 102) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.player_dist"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 110 && mouseY <= top + 122) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.elite_chance"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 130 && mouseY <= top + 142) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.cooldown"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 150 && mouseY <= top + 162) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.day_night"));
            } else if (mouseX >= left + 15 && mouseX <= left + 140 && mouseY >= top + 170 && mouseY <= top + 182) {
                hoveredTooltip = List.of(Component.translatable("gui.custom_mobs.spawner_edit.tooltip.redstone_pulse"));
            }

            super.render(graphics, mouseX, mouseY, partialTicks);

            if (hoveredTooltip != null) {
                graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
            }
        }
    }

    private List<String> getAnimationPathSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            String modelId = selectedMob.modelId;
            if (modelId == null || modelId.isEmpty()) {
                modelId = selectedMob.id;
            }
            java.io.File configFolder = MobRegistry.getMobsFolder();
            java.io.File unpackedFolder = new java.io.File(configFolder, modelId);
            if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
                List<java.io.File> animFiles = new ArrayList<>();
                findFilesRecursivelyBySuffix(unpackedFolder, ".animation.json", animFiles);
                for (java.io.File f : animFiles) {
                    list.add(f.getName());
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<String> getTextureSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            String modelId = selectedMob.modelId;
            if (modelId == null || modelId.isEmpty()) {
                modelId = selectedMob.id;
            }
            java.io.File configFolder = MobRegistry.getMobsFolder();
            java.io.File unpackedFolder = new java.io.File(configFolder, modelId);
            if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
                List<java.io.File> pngFiles = new ArrayList<>();
                findFilesRecursivelyBySuffix(unpackedFolder, ".png", pngFiles);
                for (java.io.File f : pngFiles) {
                    list.add(f.getName());
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<String> getAnimationNameSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            String modelId = selectedMob.modelId;
            if (modelId == null || modelId.isEmpty()) {
                modelId = selectedMob.id;
            }
            java.io.File configFolder = MobRegistry.getMobsFolder();
            java.io.File unpackedFolder = new java.io.File(configFolder, modelId);
            if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
                String animPath = selectedMob.animationPath;
                java.io.File animFile = null;
                if (animPath != null && !animPath.isEmpty()) {
                    String filename = animPath;
                    if (filename.startsWith("animations/")) {
                        filename = filename.substring(11);
                    }
                    animFile = findFileRecursivelyByName(unpackedFolder, filename);
                    if (animFile == null) {
                        String nameWithJson = filename.toLowerCase().endsWith(".animation.json") ? filename : filename + ".animation.json";
                        animFile = findFileRecursivelyByName(unpackedFolder, nameWithJson);
                    }
                }
                if (animFile == null || !animFile.exists()) {
                    animFile = findFileRecursively(unpackedFolder, ".animation.json");
                }
                if (animFile != null && animFile.exists()) {
                    String content = java.nio.file.Files.readString(animFile.toPath());
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    if (json != null && json.has("animations")) {
                        com.google.gson.JsonObject animsJson = json.getAsJsonObject("animations");
                        if (animsJson != null) {
                            for (String key : animsJson.keySet()) {
                                list.add(key);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        if (list.isEmpty()) {
            list.addAll(List.of("idle", "walk", "attack", "death", "swim", "fly"));
        }
        return list;
    }

    private static void findFilesRecursivelyBySuffix(java.io.File dir, String suffix, List<java.io.File> list) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix.toLowerCase())) {
                list.add(f);
            } else if (f.isDirectory()) {
                findFilesRecursivelyBySuffix(f, suffix, list);
            }
        }
    }

    private static java.io.File findFileRecursively(java.io.File dir, String suffix) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix.toLowerCase())) {
                return f;
            }
        }
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                java.io.File found = findFileRecursively(f, suffix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static java.io.File findFileRecursivelyByName(java.io.File dir, String filename) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase(filename)) {
                return f;
            }
        }
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                java.io.File found = findFileRecursivelyByName(f, filename);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean isParamActive(String type, int paramNum) {
        if (paramNum == 1) {
            return true;
        }
        if (type.startsWith("SUMMON_")) {
            return paramNum <= 8;
        }
        if (type.equals("SPAWN_MINIONS")) {
            return paramNum <= 4;
        }
        if (type.equals("STAGGER")) {
            return paramNum <= 3;
        }
        if (type.startsWith("SUMMON_GROUND_ATTACK_AOE")) {
            return paramNum <= 8;
        }
        if (type.startsWith("SUMMON_GROUND_ATTACK") || type.startsWith("AERIAL_RANGED") || type.equals("ORBITING_SHIELD")) {
            return paramNum <= 7;
        }
        if (type.equals("SHOTGUN_ATTACK")) {
            return paramNum <= 6;
        }
        if (type.startsWith("MELEE_AOE") || type.equals("RANGED")) {
            return paramNum <= 4;
        }
        if (type.startsWith("KNOCKBACK") || type.startsWith("EXPLODE_ON_") || type.startsWith("EFFECT_ON_") || type.equals("SPLIT_ON_DEATH") || type.equals("SUMMON_MINIONS")) {
            return paramNum <= 3;
        }
        if (type.startsWith("MELEE") || type.equals("PULL_TARGET") || type.equals("RAGE_MODE") || type.equals("FIRE_TRAIL") || type.equals("FROST_TOUCH") || type.equals("LIGHTNING_STRIKE") || type.equals("GIFT_GIVER")) {
            return paramNum <= 2;
        }
        return false;
    }

    private int getSpawnLeftOffset() {
        int spawnMaxW = 0;
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.height")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.spawn_block")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.light")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.pack_qty")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.spawn_weight")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.biome_filter")));
        spawnMaxW = Math.max(spawnMaxW, this.font.width(Component.translatable("gui.custom_mobs.creator.label.structure")));
        return Math.max(114, 10 + spawnMaxW + 8);
    }

    private int getAiParamsContentHeight() {
        if (selectedGoalIndex < 0) return 205;
        var goal = selectedMob.aiGoals.get(selectedGoalIndex);
        int activeCount = 2; // Animation and Delay are always active
        if (isGroupGoal(goal.type)) activeCount++;
        for (int i = 1; i <= 8; i++) {
            if (isParamActive(goal.type, i)) {
                activeCount++;
            }
        }
        return activeCount * 26 + 10;
    }

    private void updateAIFieldsY() {
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int formX = left + 120;
        int formY = top + 42;
        int formH = panelH - 80;
        int formW = (int) ((panelW - 130) * 0.6);

        // Calculate limits
        int minY = formY + 136;
        int maxY = formY + formH - 4; // leave 4px padding at the bottom

        // Check if tab is AI and a goal is selected
        if (!activeTab.equals("AI") || selectedGoalIndex < 0) {
            return;
        }

        var goal = selectedMob.aiGoals.get(selectedGoalIndex);
        String type = goal.type;
        boolean showGroupField = isGroupGoal(type);

        int currentY = formY + 136 - aiParamsScroll;
        int rowHeight = 26; // 10px label + 10px edit box + 6px vertical gap

        // goalAnimationField
        int animY = currentY + 11;
        goalAnimationField.setX(formX + 10);
        goalAnimationField.setY(animY);
        goalAnimationField.setWidth(formW - 20);
        goalAnimationField.visible = (animY >= minY && animY + 10 <= maxY);
        goalAnimationField.active = goalAnimationField.visible;
        currentY += rowHeight;

        // goalGroupField
        if (showGroupField) {
            int grpY = currentY + 11;
            goalGroupField.setX(formX + 10);
            goalGroupField.setY(grpY);
            goalGroupField.setWidth(formW - 20);
            goalGroupField.visible = (grpY >= minY && grpY + 10 <= maxY);
            goalGroupField.active = goalGroupField.visible;
            currentY += rowHeight;
        } else {
            goalGroupField.visible = false;
            goalGroupField.active = false;
        }

        // goalDelayField
        int dlyY = currentY + 11;
        goalDelayField.setX(formX + 10);
        goalDelayField.setY(dlyY);
        goalDelayField.setWidth(formW - 20);
        goalDelayField.visible = (dlyY >= minY && dlyY + 10 <= maxY);
        goalDelayField.active = goalDelayField.visible;
        currentY += rowHeight;

        // goalParam1Field
        if (isParamActive(type, 1)) {
            int pY = currentY + 11;
            goalParam1Field.setX(formX + 10);
            goalParam1Field.setY(pY);
            boolean needsPlayBtn = (type.startsWith("MELEE") || type.startsWith("KNOCKBACK") || type.equals("RANGED") || type.startsWith("SUMMON_GROUND_ATTACK") || type.startsWith("AERIAL_RANGED") || type.equals("SHOTGUN_ATTACK") || type.equals("ORBITING_SHIELD"));
            goalParam1Field.setWidth(needsPlayBtn ? formW - 35 : formW - 20);
            goalParam1Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam1Field.active = goalParam1Field.visible;
            currentY += rowHeight;
        } else {
            goalParam1Field.visible = false;
            goalParam1Field.active = false;
        }

        // goalParam2Field
        if (isParamActive(type, 2)) {
            int pY = currentY + 11;
            goalParam2Field.setX(formX + 10);
            goalParam2Field.setY(pY);
            goalParam2Field.setWidth(formW - 20);
            goalParam2Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam2Field.active = goalParam2Field.visible;
            currentY += rowHeight;
        } else {
            goalParam2Field.visible = false;
            goalParam2Field.active = false;
        }

        // goalParam3Field
        if (isParamActive(type, 3)) {
            int pY = currentY + 11;
            goalParam3Field.setX(formX + 10);
            goalParam3Field.setY(pY);
            goalParam3Field.setWidth(formW - 20);
            goalParam3Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam3Field.active = goalParam3Field.visible;
            currentY += rowHeight;
        } else {
            goalParam3Field.visible = false;
            goalParam3Field.active = false;
        }

        // goalParam4Field
        if (isParamActive(type, 4)) {
            int pY = currentY + 11;
            goalParam4Field.setX(formX + 10);
            goalParam4Field.setY(pY);
            goalParam4Field.setWidth(formW - 20);
            goalParam4Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam4Field.active = goalParam4Field.visible;
            currentY += rowHeight;
        } else {
            goalParam4Field.visible = false;
            goalParam4Field.active = false;
        }

        // goalParam5Field
        if (isParamActive(type, 5)) {
            int pY = currentY + 11;
            goalParam5Field.setX(formX + 10);
            goalParam5Field.setY(pY);
            goalParam5Field.setWidth(formW - 20);
            goalParam5Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam5Field.active = goalParam5Field.visible;
            currentY += rowHeight;
        } else {
            goalParam5Field.visible = false;
            goalParam5Field.active = false;
        }

        // goalParam6Field
        if (isParamActive(type, 6)) {
            int pY = currentY + 11;
            goalParam6Field.setX(formX + 10);
            goalParam6Field.setY(pY);
            goalParam6Field.setWidth(formW - 20);
            goalParam6Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam6Field.active = goalParam6Field.visible;
            currentY += rowHeight;
        } else {
            goalParam6Field.visible = false;
            goalParam6Field.active = false;
        }

        // goalParam7Field
        if (isParamActive(type, 7)) {
            int pY = currentY + 11;
            goalParam7Field.setX(formX + 10);
            goalParam7Field.setY(pY);
            goalParam7Field.setWidth(formW - 20);
            goalParam7Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam7Field.active = goalParam7Field.visible;
            currentY += rowHeight;
        } else {
            goalParam7Field.visible = false;
            goalParam7Field.active = false;
        }

        // goalParam8Field
        if (isParamActive(type, 8)) {
            int pY = currentY + 11;
            goalParam8Field.setX(formX + 10);
            goalParam8Field.setY(pY);
            goalParam8Field.setWidth(formW - 20);
            goalParam8Field.visible = (pY >= minY && pY + 10 <= maxY);
            goalParam8Field.active = goalParam8Field.visible;
        } else {
            goalParam8Field.visible = false;
            goalParam8Field.active = false;
        }
    }

    private void updateFilteredBehaviors() {
        this.filteredBehaviors.clear();
        String query = this.behaviorSearchField != null ? this.behaviorSearchField.getValue().trim().toLowerCase() : "";
        for (String goal : POSSIBLE_GOALS) {
            if (query.isEmpty()) {
                this.filteredBehaviors.add(goal);
            } else {
                String descKey = "gui.custom_mobs.goal_desc." + goal.toLowerCase();
                String desc = Component.translatable(descKey).getString().toLowerCase();
                if (desc.equals(descKey.toLowerCase())) {
                    desc = GOAL_DESCS.getOrDefault(goal, "").toLowerCase();
                }
                if (goal.toLowerCase().contains(query) || desc.contains(query)) {
                    this.filteredBehaviors.add(goal);
                }
            }
        }
    }

    private String getParamLabel(String type, int paramNum) {
        String key = "gui.custom_mobs.creator.goal.param" + paramNum;
        String fallback = "Param " + paramNum;
        if (paramNum == 1) {
            key = "gui.custom_mobs.creator.goal.param1";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.portal_mob_id"; fallback = "Portal Mob ID"; }
            else if (type.equals("SPAWN_MINIONS")) { key = "gui.custom_mobs.creator.goal.minion_mob_id"; fallback = "Minion Mob ID"; }
            else if (type.equals("STAGGER")) { key = "gui.custom_mobs.creator.goal.hp_threshold_percent"; fallback = "HP Threshold (%)"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.sound_event"; fallback = "Sound Event"; }
        } else if (paramNum == 2) {
            key = "gui.custom_mobs.creator.goal.param2";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.minion_mob_id"; fallback = "Minion Mob ID"; }
            else if (type.equals("SPAWN_MINIONS")) { key = "gui.custom_mobs.creator.goal.spawn_interval"; fallback = "Spawn Interval"; }
            else if (type.equals("STAGGER")) { key = "gui.custom_mobs.creator.goal.stagger_duration_sec"; fallback = "Stagger Duration (Sec)"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.projectile_id"; fallback = "Projectile/Particle ID"; }
        } else if (paramNum == 3) {
            key = "gui.custom_mobs.creator.goal.param3";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.portal_duration_ticks"; fallback = "Portal Duration (Ticks)"; }
            else if (type.equals("SPAWN_MINIONS")) { key = "gui.custom_mobs.creator.goal.max_minions"; fallback = "Max Minions"; }
            else if (type.equals("STAGGER")) { key = "gui.custom_mobs.creator.goal.damage_multiplier"; fallback = "Damage Multiplier"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.damage"; fallback = "Damage"; }
        } else if (paramNum == 4) {
            key = "gui.custom_mobs.creator.goal.param4";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.spawn_interval"; fallback = "Spawn Interval"; }
            else if (type.equals("SPAWN_MINIONS")) { key = "gui.custom_mobs.creator.goal.spawn_radius"; fallback = "Spawn Radius"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.speed"; fallback = "Speed"; }
        } else if (paramNum == 5) {
            key = "gui.custom_mobs.creator.goal.param5";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.max_minions"; fallback = "Max Minions"; }
            else if (type.equals("SUMMON_TETHER_DRAIN")) { key = "gui.custom_mobs.creator.goal.max_distance"; fallback = "Max Distance"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.count"; fallback = "Count"; }
        } else if (paramNum == 6) {
            key = "gui.custom_mobs.creator.goal.param6";
            if (type.equals("SUMMON_MINION_PORTAL")) { key = "gui.custom_mobs.creator.goal.spawn_radius"; fallback = "Spawn Radius"; }
            else if (type.equals("SUMMON_TETHER_DRAIN")) { key = "gui.custom_mobs.creator.goal.slowness_level"; fallback = "Slowness Level"; }
            else if (type.equals("SUMMON_GALE_VORTEX_PULL") || type.equals("SUMMON_GALE_VORTEX_PUSH")) { key = "gui.custom_mobs.creator.goal.radius"; fallback = "Radius"; }
            else if (type.equals("SUMMON_CHASE_SNAKE")) { key = "gui.custom_mobs.creator.goal.spawn_interval"; fallback = "Spawn Interval"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.lines_count_or_radius"; fallback = "Lines Count / Radius"; }
        } else if (paramNum == 7) {
            key = "gui.custom_mobs.creator.goal.param7";
            if (type.equals("SUMMON_TETHER_DRAIN")) { key = "gui.custom_mobs.creator.goal.heal_amount"; fallback = "Heal Amount"; }
            else if (type.equals("SUMMON_CHASE_SNAKE")) { key = "gui.custom_mobs.creator.goal.warning_particle_type"; fallback = "Warning Particle Type"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.angle_or_spiral_factor"; fallback = "Angle / Spiral Factor"; }
        } else if (paramNum == 8) {
            key = "gui.custom_mobs.creator.goal.param8";
            if (type.contains("LAYERED")) { key = "gui.custom_mobs.creator.goal.delay_ticks"; fallback = "Delay Ticks"; }
            else if (type.startsWith("SUMMON_")) { key = "gui.custom_mobs.creator.goal.upward_knockback"; fallback = "Upward Knockback"; }
        }
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? fallback : translated;
    }
}
