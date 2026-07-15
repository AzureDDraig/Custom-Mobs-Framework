# Changelog

All notable changes to the Custom Mobs Framework project are documented in this file.

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
