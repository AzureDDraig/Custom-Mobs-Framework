# Changelog

All notable changes to the Custom Mobs Framework project are documented in this file.

---

## [Build 114] - AI Goal Param Saving, Suggestions, and Minion Portal Sync (Issue 2)
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Added a fallback branch for `type.startsWith("SUMMON_")` in `saveTextFieldsToActiveMob()` to correctly serialize all 8 parameters for custom summon goals on save/export (fixing tether drain, minion portals, spirals, lines, shockwaves, domes, vortexes, etc.).
    *   Updated the focused field checks in `tick()` to suggest Sound Events for all summon goals, suggest Projectile, Entity, and Particle IDs for all projectile parameters, and added focused checks for parameter input fields 5 through 8 with Particle Type autocompletion support.
    *   Modified `getParamLabel()` to display `"Max Height"` for non-layered ground summons, and `"Max Duration (Ticks)"` for tether drain.
*   **`CustomMobEntity.java`**:
    *   Restructured `CustomSummonAttackGoal.tick()` to enforce the `castDelay` check before initiating chase snake, vortex, or minion portal summons, preventing them from spawning every tick.
    *   Parsed the `maxHeight` parameter from AI goal parameters in `executeSummonStep()` and `executeChaseSnake()` and passed it to the spawned ground/dome projectiles.
    *   Updated `SpawnMinionsGoal.tick()` to synchronize `spawnInterval`, `maxMinions`, and `spawnRadius` from the summoner boss's `SUMMON_MINION_PORTAL` parameters if the portal itself does not have overrides.
*   **`CustomProjectileEntity.java`**:
    *   Implemented a default 10-second (200-tick) lifespan safety cleanup inside the server-side `tick()` routine for flying projectiles to prevent orphaned/stuck projectiles from persisting indefinitely.
### Layman's Explanation
*   **AI Goal Param Saving:** Fixed a critical bug where the parameters (like Portal Mob ID, Minion Mob ID, Spawn Intervals, Radii, etc.) for custom summon skills were completely discarded when saving or exporting.
*   **Search and Autocomplete:** Added fully functional suggestion overlays for all summon goals in the editor. The editor now correctly suggests sounds, projectiles, entity templates, and particle types when editing these fields.
*   **Minion Portal Sync:** Enforced cast cooldowns on minion portals, vortexes, and chase snakes so they no longer spam hundreds of objects at once. Portal minion limits, spawn rates, and radii are now correctly synchronized from the boss mob configurations.
*   **Ground Projectile Height and Cleanup:** Ground projectiles now support playtester-defined maximum heights. A safety cleanup was added to automatically delete any lost or stuck projectiles after 10 seconds to prevent server lag.

---

## [Build 113] - Custom Death Animations Support
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Updated the Geckolib controller registrar in `registerControllers()` to check if the mob is dead or dying (`isDeadOrDying()`), playing the custom death animation via `thenPlay()` if defined.
    *   Overrode `tickDeath()` to dynamically extend the death tick timeline from the default 20 ticks to match the exact duration of the custom death animation.
*   **`CustomMobRenderer.java`**:
    *   Overrode `setupRotations()` to temporarily set `deathTime = 0` during rendering calls if a custom death animation is defined. This bypasses the default vanilla sideways 90-degree Z-axis tilt rotation.
    *   Updated Java model rendering in `render()` to detect if the entity is dying, playing the death animation with timeline positioning computed from `deathTime + partialTicks`, clamped to the maximum length of the animation.
### Layman's Explanation
*   **Custom Death Animations:** Mobs now correctly play their custom playtester-defined death animations instead of using the generic Minecraft behavior (where they tilt 90 degrees sideways and quickly disappear). The mob remains visible until the custom death animation has fully completed playing.

---

## [Build 112] - Loot Tab Textbox Clickability Fix
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Restructured `init()` to conditionally register only the widgets that belong to the currently active tab or item selector. This solves a critical GUI bug where text fields on the Loot tab (`lootChanceField`, `lootMinField`, `lootMaxField`) were blocked and unclickable due to overlapping hidden widgets (such as the General tab's multi-line `loreField`) remaining active in the screen's children list.
    *   Updated `selectTab()`, `openItemSelector()`, `closeItemSelector()`, and the `Looting Required Checkbox` click handler to call `this.init(...)` to cleanly rebuild the screen with the correct active widgets.
### Layman's Explanation
*   **Loot Tab Click Fix:** Fixed a major bug where playtesters could not click on or edit the textboxes in the Loot tab (Loot Chance, Min Qty, Max Qty). The editor screen now dynamically loads only the input boxes for the tab you are currently viewing, preventing hidden text fields from other tabs from blocking your clicks.

---

## [Build 111] - Sidebar Scrollbar, Spawner Search, and Loot Tab Textbox Editing
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Added a scrollbar and scroll wheel support to the mobs template list in the creator screen sidebar using `sidebarScroll`.
    *   Added direct text field responders to `lootChanceField`, `lootMinField`, `lootMaxField`, and `lootLevelField` inside `init()` to synchronize edits instantly.
    *   Avoided calling `setValue()` in `showFieldsForTab()` if the respective loot text field is already focused (`isFocused()`), preventing cursor reset issues.
    *   Refactored `saveTextFieldsToActiveMob()` to save the raw textbox values into the new `params` map of the loot item, and safely parse them into their double/int representations.
    *   Updated the "+ Add Drop Item" click callback inside `mouseClicked()` to initialize the default `params` map keys.
    *   Implemented `SpawnerEditScreen` nested class updates: added a `searchField` and `searchQuery` to filter templates via a text responder. Shifted the spawner template list viewport down by 10 pixels to fit the search box, and updated coordinate click/scroll calculations accordingly.
*   **`MobData.java`**: Added a `public Map<String, String> params = new HashMap<>();` map to `LootItemData` to mirror the string-based parameters of `AIGoalData`, enabling raw text field value persistence.
### Layman's Explanation
*   **Sidebar Scrollbar:** Added a scrollbar to the custom mobs list in the creator sidebar. You can now scroll through long lists of mobs using either the mouse wheel or by clicking the scrollbar track.
*   **Spawner Search Field:** Added a search box to the RPG Mob Spawner block configuration screen, letting you search and filter the list of custom mobs instantly.
*   **Loot Tab Textboxes Fix:** Replaced double/integer field overwrites with a temporary string parameter map (similar to how AI goal parameters work). You can now edit the drop chance and quantities textboxes reliably without them freezing, resetting, or locking up when you clear them or type.

---

## [Build 110] - Animation Textbox Character Limit Update
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Set explicit maximum character length limits (`setMaxLength(512)`) on all animation textboxes (`idleAnimField`, `walkAnimField`, `attackAnimField`, `deathAnimField`, `swimAnimField`, `flyAnimField`, and `goalAnimationField`). This overrides the default Minecraft `EditBox` limit of 32 characters.
### Layman's Explanation
*   **Animation name Length:** Increased the character limit for animation name fields in the mob editor screen from 32 to 512, allowing for longer namesspaced Geckolib animation identifiers.

---

## [Build 109] - Loot Drop Parameter Editing Fix
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Separated the parsing of `lootChanceField`, `lootMinField`, `lootMaxField`, and `lootLevelField` inside `saveTextFieldsToActiveMob()` into individual `try-catch` blocks. This prevents an empty or temporarily invalid text field from blocking other fields from saving.
    *   Invoked `saveTextFieldsToActiveMob()` at the start of all Loot tab click handling routines in `mouseClicked()`, preserving unsaved text field edits before the screen gets reinitialized or updated.
    *   Added the missing background rendering call for `lootLevelField` inside `render()`.
### Layman's Explanation
*   **Loot Tab Edits Fix:** Fixed a bug where editing drop chance, minimum quantity, or maximum quantity in the Loot tab was non-functional or would constantly get reset when clicking other settings (like changing the item type or toggling Looting Required).

---

## [Build 108] - AOE Melee Sweep Damage Fix
### Technical Changes (By Class)
*   **`CustomMobEntity.java` (`CustomMeleeAOEAttackGoal` inner class)**: Refactored the `performAOESweep()` method to calculate target angle differences using the mob's actual body yaw (`getYRot()`) instead of its look vector. This resolves an issue on the server where stale/un-synced look vector direction data caused targets within the sweep range to be missed.
### Layman's Explanation
*   **AOE Melee Attack Fix:** Fixed a bug where frontal sweep (AOE Melee) attacks were not dealing damage. They now correctly detect and hit all targets in front of the mob.

---

## [Build 107] - RPG Mob Spawner Redstone & Cooldown Options
### Technical Changes (By Class)
*   **`RPGMobSpawnerBlockEntity.java`**: Added fields `redstonePulseOnly`, `spawnerCooldown`, `cooldownTimer`, and `wasPowered`. Redesigned spawner ticking logic to support redstone-pulsed activation alongside normal periodic spawning, and implemented block neighbor power check helpers.
*   **`MobCreatorScreen.java` (`SpawnerEditScreen` inner class)**: Expanded editor GUI canvas size to $320 \times 220$, added green/red checkbox rendering for the redstone pulse toggle, added an input textbox for cooldown settings, and updated position mappings.
*   **`ModPackets.java` & `CustomMobsClient.java`**: Serialized and deserialized `redstonePulseOnly` and `spawnerCooldown` in client-to-server save packets and server-to-client UI packets.
*   **Language Files (`en_us.json`, etc.)**: Added descriptions and tooltip localized strings for all 8 spawner attributes.
### Layman's Explanation
*   **Redstone Pulse Activation:** You can now configure custom mob spawners to only spawn creatures when triggered by redstone inputs (like levers, buttons, or tripwires).
*   **Cooldown in Seconds:** Added a simple textbox to specify how many seconds the spawner must wait before attempting to spawn again.
*   **Interactive Tooltips:** Hovering your mouse over any spawner setting will display a helper description explaining exactly what it does.

---

## [Build 106] - RPG Mod (Mine and Slash) Compatibility & Tamed targeting Bypass
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Implemented the marker interface `net.minecraft.world.entity.monster.Enemy` to classify the entity as a monster/hostile.
    *   Added `!isTame()` filters to target selector goals (`TARGET_PLAYER`, `TARGET_VILLAGER`, `TARGET_ANIMALS`).
    *   Added `!mob.isTame()` checks inside `canUse()` of `AttackOthersGoal` and `TargetGroupGoal` to prevent wild combat loops when tamed.
### Layman's Explanation
*   **RPG Mod Compatibility:** Custom mobs are now fully recognized as monsters by RPG mods like *Mine and Slash*, meaning they will now scale levels, spawn correctly in combat systems, and drop correct loot.
*   **Friendly Tamed Mobs:** Once tamed, custom mobs will immediately stop attacking players, villagers, passive animals, or friendly groups.

---

## [Build 105] - Localized AI Parameter Names and Behavior Descriptions
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Refactored the UI parameter drawing logic inside `render()` to call a centralized translatable helper `getParamLabel(type, paramNum)`.
*   **`CustomProjectileEntity.java`**: Replaced direct client class imports inside vortex tick particle updates with reflection-safe helper methods to prevent dedicated server crashes.
*   **Language Files (`en_us.json`, etc.)**: Fully mapped and translated the parameter labels, descriptions, and tooltips for all 39 combat/utility behaviors.
### Layman's Explanation
*   **Full GUI Translation:** All parameter inputs and combat behavior configurations in the creator screen are now fully translated across all supported languages (German, French, Spanish, Japanese, Russian, etc.).
*   **Dedicated Server Fix:** Fixed a server-crashing bug related to client particle color extractions.

---

## [Build 104] - Combat Combo Loops, Template Separation, and UI Parameter Alignment
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Fixed a registration typo in the sequential combo executor which called the wrong class for delay steps, causing combos to stall.
*   **`MobRegistry.java`**: Implemented strict config file validation inside template reloading routines to automatically skip projectile configs placed in the mobs folder, and vice versa.
*   **`MobCreatorScreen.java`**: Shifted the starting coordinate of the AI parameters panel viewport down by 16 pixels to prevent overlapping.
### Layman's Explanation
*   **Smooth Combo Loops:** Fixed a bug where boss combo sequences would get stuck on a delay step, allowing combat animations to loop properly.
*   **Cleaner Templates:** Projectile configs and Mob configs are now strictly separated, preventing them from cluttering the wrong list in the GUI.

---

## [Build 103] - Self-Damage Immunity & Orbiting Shield Parameter Clarification
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Added self-damage checks to `hurt()`, granting mobs immunity to damage sources where the attacker is themselves or their owned projectiles.
*   **`MobCreatorScreen.java` & Language Files**: Renamed generic "Radius" parameter label to "Orbit Radius" for the Orbiting Shield behavior.
### Layman's Explanation
*   **Self-Injury Protection:** Custom mobs and bosses will no longer accidentally damage themselves with their own explosive projectiles or spinning shield spells.

---

## [Build 102] - Damage Delay Preservation & Translated Tooltips
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Modified `tick()` in melee attack goals to run independently until both the swing cooldown and the user-configured damage delay timer have ended.
*   **`ProjectileCreatorScreen.java` & Language Files**: Replaced hardcoded tooltip components with translatable keys.
### Layman's Explanation
*   **Consistent Hit Registration:** Custom mobs will no longer skip dealing damage when their attack animations end faster than their damage delay timers.

---

## [Build 101] - Dynamic Animation Length Detection & delay Override
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Implemented `getAnimationLengthInTicks()` and `findFileRecursively()` to safely locate and parse the local `.animation.json` config files.
### Layman's Explanation
*   **Dynamic Animation Syncing:** The mod now reads geometry animation files to dynamically set the duration of melee goals to match the exact length of the animation.

---

## [Build 100] - Melee Attack Goal Animation Flickering & Interruption Fix
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Added an `isAttacking` state flag to prevent attack goals from terminating prematurely, allowing animations to play fully.
### Layman's Explanation
*   **No Animation Flickering:** Fixed an issue where attack animations would cancel or flicker immediately upon hitting a target.

---

## [Build 99] - Multi-Line Word Wrapping Lore Field
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Upgraded `loreField` to a native `MultiLineEditBox` and adapted custom render methods to support multi-line widgets.
### Layman's Explanation
*   **Better Bestiary Editing:** The lore text box inside the creator screen now supports typing on multiple lines with automatic word wrapping.

---

## [Build 98] - Melee Attack Goal Overlapping & Navigation Stalling Fix
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Overrode `canContinueToUse()` in melee goals to return true if target is in close proximity, overriding vanilla pathing cancellations.
### Layman's Explanation
*   **Proximity Attack Fix:** Monsters will no longer stop attacking if they stand directly on top of the player.

---

## [Build 97] - Lore Character Limit Fix
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Excluded the `loreField` from a UI layout iteration loop that was resetting character limits to 1024, preserving the 2048 character cap.
### Layman's Explanation
*   **Full Lore Descriptions:** Restored the ability to write description texts up to 2048 characters.

---

## [Build 96] - Unified Combat Combo Sequence System
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Integrated all offensive goals (`RANGED`, `SUMMON_GROUND_ATTACK`, `SUMMON_GROUND_ATTACK_AOE` (1-4), `AERIAL_RANGED_ATTACK`, `AERIAL_RANGED_AOE` (1-4), `SHOTGUN_ATTACK`, and `ORBITING_SHIELD`) into the sequential combo system alongside melee and delay steps.
### Layman's Explanation
*   **Combo Sequences:** Projectiles, spells, and ranged attacks can now be sequenced in order alongside melee attacks to form complex bosses.

---

## [Build 95] - Autocomplete Suggestions for New Behaviors
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Mapped registry autocomplete arrays to goal input fields when active behaviors are focused.
### Layman's Explanation
*   **Autocomplete Helpers:** Added autocomplete drop-down lists for sounds and projectiles when configuring special attacks.

---

## [Build 94] - Proximity Melee Attack Trigger & Pathing Bypass
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Allowed melee goals to trigger when within target proximity even if standard pathfinding returns empty.
### Layman's Explanation
*   **Responsive Attacks:** Monsters will now initiate attacks when right next to the target, bypassing navigation stalls inside tight spaces.

---

## [Build 93] - Scrollable Parameters Panel & Goal Descriptions
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Implemented custom scroll boundaries and drag handlers to scroll parameter inputs dynamically when the panel height is exceeded.
### Layman's Explanation
*   **Scrollable settings Panel:** You can now scroll through AI goal settings smoothly when configuring attacks with many parameters.

---

## [Build 92] - Knockback Attack Behaviors
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Added `CustomKnockbackAttackGoal` with horizontal drag equations to throw players a precise distance away.
### Layman's Explanation
*   **Custom Knockback:** Added a knockback attack behavior that launches targets a configurable distance away.

---

## [Build 91] - AOE Attack Variants & Custom Projectiles
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Implemented `CustomMeleeAOEAttackGoal` to check targets in a frontal cone and registered multiple variant slots.
### Layman's Explanation
*   **Area-of-Effect Attacks:** Mobs can now perform sweep attacks that hit all entities within a cone in front of them.

---

## [Build 90] - Accuracy Parameter for Ranged Attacks
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Integrated divergence calculations into ranged attack goals.
### Layman's Explanation
*   **Ranged Accuracy:** Added an accuracy slider to ranged attacks, allowing mobs to shoot projectiles with varying spreads.

---

## [Build 89] - Natural Spawning Passable Block Logic
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Updated the natural spawn predicate to check passable blocks and half-slabs.
### Layman's Explanation
*   **Spawn Block Improvements:** Mobs can now spawn in grass, flowers, snow, and on top of stairs or slabs.

---

## [Build 88] - Item Selection Submenu Search & Spawner Passable Block Fixes
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Added item list search and scroll boundary clamping to the item selector overlay.
*   **`RPGMobSpawnerBlockEntity.java`**: Allowed spawners to place mobs on stairs, slabs, and inside tall grass.
### Layman's Explanation
*   **Searchable Items:** Added a search bar when choosing items for mob drops or equipment, and fixed mob spawners refusing to spawn mobs in grass.

---

## [Build 86] - Sequential Combo and Mini-Combo Combat Behaviors
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Introduced combat sequence lists and handlers to route melee and delay AI steps sequentially.
### Layman's Explanation
*   **Boss Mini-Combos:** Mobs can now follow specific attack sequences in order rather than randomly choosing attacks.

---

## [Build 85] - Autocomplete Biome Selection
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Implemented autocomplete logic for the biome search field.
*   **`CustomMobEntity.java`**: Added un-namespaced fallback matching to validate spawning biomes.
### Layman's Explanation
*   **Autocomplete Biomes:** Biome input boxes now suggest matching Minecraft biomes while typing.

---

## [Build 83] - Flying Physics, Billboard Name Height, and Biome Saving Fixes
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Fixed flying physics to utilize gravity descent when inactive, and corrected culling bounds checks.
*   **`MobCreatorScreen.java`**: Auto-committed biome edits when saving.
### Layman's Explanation
*   **Better Flying & Nametags:** Flying mobs will now land naturally, and name tag labels will float right above their heads instead of far above them.

---

## [Build 82] - Multi-Melee Behavior & Animation Overrides
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Decoupled melee goals into dynamic instances (`MELEE_1` to `MELEE_6`).
### Layman's Explanation
*   **Multiple Melee Attacks:** Mobs can now be configured with up to 6 distinct melee attacks, each with unique animations and delays.

---

## [Build 80] - Lore Truncation & Duplicate Template Actions
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**: Added a "Copy" button to duplicate mob templates.
*   **`ModPackets.java`**: Increased network packet character limits to 262,144 to prevent truncation.
### Layman's Explanation
*   **Duplicate Mob & Long Descriptions:** Added a copy button to easily duplicate mobs, and fixed a bug where long lore descriptions were cut off.

---

## [Build 79] - Fluid Spawning & 3D Swimming Mechanics
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**: Overrode navigation and travel mechanics to support 3D fluid movement.
*   **`CustomMobs.java`**: Registered mobs across water categories to support fluid spawning.
### Layman's Explanation
*   **Water and Lava Monsters:** Mobs can now spawn in water or lava, swim naturally in 3D, and are immune to fire or drowning damage.
