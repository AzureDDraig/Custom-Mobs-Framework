# Changelog

All notable changes to the Custom Mobs Framework project are documented in this file.

---

## [Build 140] - Marquee Scrolling Text for Spawning Tab UI Labels
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   **Marquee Text Scissor Renderer (`drawScrollingText`):** Implemented a smooth horizontal marquee text scrolling helper using `graphics.enableScissor()` and time-based trigonometric ping-pong interpolation (`Math.cos`).
    *   **Automatic Overflow Handling:** Whenever a label's translated string width (`font.width(text)`) exceeds column width limits (95px for left column, 85px for right column), `drawScrollingText` smoothly scrolls the text back and forth, keeping it 100% clipped within its column boundary with zero text overlap.
### Layman's Explanation
*   **Marquee Scrolling Labels:** Added automatic marquee scrolling for Spawning tab text labels when running long translations (like Portuguese or Spanish). Labels that are too wide now smoothly scroll horizontally within their column box instead of spilling into buttons or adjacent fields.

---

## [Build 139] - Cave/Any Natural Spawning Engine Fix (Issue #109) & Spawning Tab 2-Column UI Overhaul
### Technical Changes (By Class)
*   **`ModEntities.java`**:
    *   **Natural Spawn Candidate Sampling:** Changed `SpawnPlacements` type in `SpawnPlacementsRegistry.register` for `CUSTOM_MOB` from `NO_RESTRICTIONS` to `ON_GROUND`. This enables Minecraft's `NaturalSpawner` engine to sample random candidate `Y` coordinates across subterranean cave depths, aquatic water bodies, and lava lakes instead of locking all candidates to the top surface.
*   **`MobCreatorScreen.java`**:
    *   **Spawning Tab 2-Column Layout:** Restructured the Spawning tab into two clean, non-overlapping columns.
        *   **Left Column (`formX + 10` labels, `formX + 110` controls):** Moon Phase, Time of Day, Dimension, Weather, Environment (ANY / SURFACE / CAVES), Raid/Spawner Only, Aquatic Only, Lava Only, World Limit.
        *   **Right Column (`formX + 180` labels, `formX + 270` controls):** Height (min/max), Light (min/max), Pack Qty (min/max), Spawn Weight, Spawn Block ID, Structure, Biome Filter.
    *   **Hitbox & Render Alignment:** Updated `init()`, `renderTabFormText()`, and `mouseClicked()` coordinates to ensure 0% text/control overlap regardless of translation string length (e.g. English, Portuguese, Spanish).
### Layman's Explanation
*   **Cave & Subterranean Spawning Fix (Issue #109):** Fixed a bug where mobs set to spawn in Caves or Any environment were failing to spawn naturally underground. Spawning now works correctly in caves, water, lava, and on the surface.
*   **Spawning Tab UI Layout Overhaul:** Redesigned the Spawning tab layout in the Mob Creator UI into two clean columns, preventing long translated labels from overlapping into text boxes and buttons.

---

## [Build 138] - Item Search Input Fix & Spawner HUD Name/Tooltip Fix (Issues #108 & #106)
### Technical Changes (By Class)
*   **`RaidEditorScreen.java`**:
    *   **Keyboard Input Routing:** Added `charTyped` and `keyPressed` overrides. Keyboard events are now properly routed to `itemSearchField` when searching items in the Raid Loot popup.
*   **`MobCreatorScreen.java`**:
    *   **Item Search Keyboard Input:** Updated `charTyped` and `keyPressed` to pass inputs (including Backspace, Arrow keys, Delete) to `itemSearchField` when the Item Selector modal is open.
    *   **Spawner HUD Truncation & Tooltips:** Updated `SpawnerEditScreen` to use dynamic font-width truncation (`font.width(displayName)`) for template entry names instead of hardcoded 14-character cutoff. Fixed `hoveredTooltip` initialization order so hovering over template entries correctly displays full mob display names and template IDs.
### Layman's Explanation
*   **Item Search Box Fix (Issue #108):** Fixed typing in item search boxes in both the Mob Creator and Raid Editor menus so players can type item names directly to filter items.
*   **Spawner HUD Names & Tooltips (Issue #106):** Fixed mob template names getting cut off too short in the Spawner block menu, and fixed hover tooltips showing full mob display names and IDs.

---

## [Build 137] - Worldwide Mob Spawn Limit
### Technical Changes (By Class)
*   **`MobData.java`**:
    *   **Spawn Rules Field:** Added `public int worldwideLimit = 100;` to `SpawnRulesData`. Defaulting to 100 for newly constructed or unconfigured mob templates.
*   **`CustomMobEntity.java`**:
    *   **World Entity Counter:** Added `countActiveWorldwideLimitMobs(ServerLevelAccessor level, String templateId)` iterating across all loaded server levels to count active instances of a mob template.
    *   **Exclusion Logic:** Excluded raid mobs (`activeRaidId != null`), spawner block mobs (`spawnerPos != null`), and spawner-spawned mobs (`isSpawnerMob == true`) from counting towards or being restricted by the worldwide limit.
    *   **Spawn Rule Check:** Updated `isValidSpawnTemplate()` to check if active non-raid, non-spawner mobs reach or exceed `worldwideLimit`, blocking further natural/chunk generation spawning.
    *   **NBT Serialization:** Added `IsSpawnerMob` NBT tag saving and loading in `addAdditionalSaveData` and `readAdditionalSaveData`.
*   **`MobCreatorScreen.java` & `en_us.json`**:
    *   **Spawning Tab UI:** Added `World Limit:` input box (`worldwideLimitField`) to the Spawning tab layout.
    *   **Tooltips & Language Keys:** Added tooltips explaining that `0` means unlimited, and raid/spawner mobs are excluded from the limit.
### Layman's Explanation
*   **Worldwide Mob Spawn Limit:** Added a configurable "World Limit" setting (default: 100) under the Spawning tab in the Mob Creator UI. Naturally spawning mobs respect this cap, while raid mobs and spawner block mobs bypass the limit and do not count towards it.

---

## [Build 136] - Mob Creator Search & Sorting, Projectile Leak Fix, Sound/Particle Autocomplete Fix
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   **Sidebar Search & Alphabetical Sorting:** Added an `EditBox` search bar to the Mob Creator sidebar, filtering mob templates dynamically in real-time. Sorted mob templates alphabetically by display name. Updated viewport, mouse clicks, wheel scroll bounds, and scrollbar drag calculations.
    *   **Preview Filter Standardization:** Updated template filters to exclude any template ID starting with `__proj_`.
*   **`ProjectileCreatorScreen.java`**:
    *   **Sound & Particle Autocomplete Fix:** Added fallback to `BuiltInRegistries.SOUND_EVENT` and `BuiltInRegistries.PARTICLE_TYPE` when sound caches are empty. Added missing `drawEditBoxBackground` call for `particleTypeField`.
*   **`MobRegistry.java`**:
    *   **Save Safeguard:** Added guard check in `saveMob()` to reject saving any projectile preview template starting with `__proj_`.
*   **`BestiaryScreen.java`, `RaidEditorScreen.java`, `CustomMobEntity.java`, `CustomMobsCommands.java`**:
    *   **Framework Preview Filter Standardization:** Standardized all template checks to filter `__proj_` prefixes, preventing projectile preview models from appearing in bestiary/raid lists or spawning as natural mobs.
### Layman's Explanation
*   **Mob Creator Search & Alphabetical List:** Added a search box to the Mob Creator Screen sidebar and sorted all custom mobs alphabetically.
*   **Projectile Mob Leak Fixed:** Fixed a bug where custom projectiles would leak into the Mob Creator UI list and spawn naturally in the world like mobs.
*   **Projectile Sound & Particle Search Fixed:** Fixed sound and particle search suggestion dropdowns in the Projectile Creator Screen so they pop up and register selections reliably.

---

## [Build 135] - Optional Coordinates for custom_mobs spawn-mob Command
### Technical Changes (By Class)
*   **`CustomMobsCommands.java`**:
    *   **Coordinates Argument:** Updated the `/custom_mobs spawn-mob` command to accept an optional `x y z` coordinate block argument (as `pos`) before the optional `is_elite` boolean parameter.
    *   **Helper Signature:** Adjusted `spawnMobCustom()` helper to accept and use the custom target coordinate `Vec3` position instead of defaulting to the player's position.
### Layman's Explanation
*   **Optional Summon Coordinates:** You can now specify custom `x y z` coordinates when running `/custom_mobs spawn-mob` to spawn custom mobs at specific locations instead of only at the player's current location.

---

## [Build 134] - Fix Avoid Mob & Avoid Player Wearing Goal Parameter Mismatches
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   **Avoid Goals Parameter Mismatch:** Fixed parameter key mismatches for `AvoidMobGoal` and `AvoidPlayerWearingGoal`. The goals now read `"mobs"` and `"item"` respectively (as saved by the Mob Creator UI), falling back to `"mobId"` and `"armorItem"` for legacy configurations.
    *   **Custom Mob Fleeing:** Enhanced `AvoidMobGoal` to support custom mob template IDs, allowing mobs to successfully detect and flee from other configured custom mobs.
    *   **Namespace Fallbacks:** Added namespace-stripped fallback matching for avoid goals, letting them match registry items/entities like `diamond_chestplate` or `zombie` without explicit namespaces.
### Layman's Explanation
*   **Avoid Behaviors Fix:** Fixed bugs where the "Avoid Mob" and "Avoid Player Wearing" AI goals would be completely ignored when saving/editing them via the Mob Creator UI due to a settings key mismatch. Custom mobs will now correctly flee from the specified items or entities (including other custom mobs).

---

## [Build 133] - Mob Creator Avoid Mob & Avoid Player Wearing Suggestions
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   **Goal Autocomplete Suggestions:** Added autocomplete suggestions dropdown support for the `AVOID_MOB` goal's parameter 1 (suggesting custom mobs and entity registry types) and the `AVOID_PLAYER_WEARING` goal's parameter 1 (suggesting item registry types).
### Layman's Explanation
*   **Autocomplete Suggestions Coverage:** Adding missing autocomplete suggestions to the "Avoid Mob" and "Avoid Player Wearing" AI goals inside the Mob Creator Screen, making it easy to select the entities to avoid or armor items to flee from.

---

## [Build 132] - Mob Creator General Summon Goal Loading Support
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   **Goal Loading Parameters:** Added a general fallback loading case in `loadActiveMobGoalDetails()` for all goals starting with `SUMMON_` (e.g. `SUMMON_TETHER_DRAIN`, `SUMMON_CHASE_SNAKE`, etc.). This fixes a bug where their parameters were hidden and could not be loaded/edited from the list of AI goals.
### Layman's Explanation
*   **Summon AI Editing Fixes:** Fixed a bug where parameters of various Summon AI behaviors (like Tether Drain, Chase Snake, and Vortexes) would not load or display inside the Mob Creator Screen.

---

## [Build 131] - Biome Tag Support for Spawn Rules
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   **Biome Tag Spawning:** Added support for biome tags inside custom mob spawn rules. Biome specifications starting with `#` (e.g., `#minecraft:is_forest`) will be validated against the biome holder's registry tags via `Holder.is(TagKey)`.
### Layman's Explanation
*   **Biome Tag Spawning:** Spawning rules now support biome tags, allowing custom mobs to spawn across groups of biomes (such as all forests or oceans) using `#namespace:tag_id` syntax.

---

## [Build 130] - Projectile Creator Sidebar Search Filter
### Technical Changes (By Class)
*   **`ProjectileCreatorScreen.java`**:
    *   **Sidebar Search Filter:** Integrated an `EditBox` search field at the top of the sidebar. Shuffled list viewport and scrollbar math downwards by 18 pixels, allowing players to filter custom projectiles.
### Layman's Explanation
*   **Projectile Creator Quality of Life:** Added a search box to the Projectile Creator Screen sidebar to quickly filter and find custom projectiles.

---

## [Build 129] - Mob Creator Summon Minion Portal Load Parameters Fix
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   **Goal Loading Parameters:** Fixed a bug where parameters for the `SUMMON_MINION_PORTAL` AI goal were not being loaded into the configuration textboxes when selecting or loading the goal in the Mob Creator UI. Added parameter mapping overrides inside `loadActiveMobGoalDetails()` to populate portalMobId, minionMobId, portalDuration, spawnInterval, maxMinions, and spawnRadius parameters properly.
### Layman's Explanation
*   **Minion Portal Goal Editing Fix:** Fixed an issue where the parameters of the "SUMMON_MINION_PORTAL" AI behavior would not load or display inside the editing boxes in the Mob Creator Screen, preventing quick edits of active minion portals.

---

## [Build 128] - Bestiary Screen Search Filter & Sidebar Hover Tooltips
### Technical Changes (By Class)
*   **`BestiaryScreen.java`**:
    *   **Bestiary Search Filter:** Integrated an `EditBox` search field at the top of the sidebar. Shuffled list viewport and scrollbar math downwards by 20 pixels, allowing players to filter discoverable custom mobs and raids.
    *   **Sidebar Tooltips:** Added premium hover tooltips displaying the full display name and internal resource/template ID of hovered sidebar items, making it easy to read long truncated names.
### Layman's Explanation
*   **Bestiary Quality of Life:** Added a search box to the Bestiary Screen to quickly find mobs or raids. Hovering over a list entry now shows a tooltip with the full name and internal ID.

---

## [Build 127] - Raid Editor Item Selector Scroll Reset Fix
### Technical Changes (By Class)
*   **`RaidEditorScreen.java`**:
    *   **Item Search Responder:** Added a text change responder (`setResponder`) to the loot tab item selector modal's `itemSearchField` that resets `itemSelectorScroll` to `0` whenever typing/filtering. This fixes a GUI bug where the item selector grid rendered completely blank if a user typed a query after having scrolled down the list.
### Layman's Explanation
*   **Raid Loot Selector Quality of Life:** Fixed a common item selector screen bug. When adding a loot drop to a raid wave and typing in the search box, the search results will no longer render blank if you had scrolled down the list prior to typing.

---

## [Build 126] - Raid Editor Mobs List Autocomplete Search Filter
### Technical Changes (By Class)
*   **`RaidEditorScreen.java`**:
    *   **Add Mob Search Filter:** Integrated an `addMobSearchField` text box to filter the addable mobs list, matching the search functionality of `SpawnerEditScreen`. Toggles visible only inside the Waves tab.
    *   **Ticking & Scrolling Fixes:** Updated `tick()` to tick `rewardCountField` and the new search field. Modified `mouseClicked()`, `mouseScrolled()`, and rendering to use the filtered list `filteredAddMobs` instead of `availableAddMobs`.
### Layman's Explanation
*   **Raid Editor Mob Template Search:** You can now filter/search templates in the Raid Editor's "Add Mob" list using a new search input box, making it significantly faster to configure complex raid waves.

---

## [Build 125] - Customizable Avoid Range & Goal Parameter Mismatch Fixes
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   **Avoid Goals Range:** Replaced hardcoded `12.0` detection radius values in `AvoidPlayerWearingGoal`, `AvoidMobGoal`, and `AvoidGroupGoal` with dynamic `"range"` parameter parsing, falling back to `12.0` if not set.
    *   **Group Parameter Mismatch:** Fixed `AvoidGroupGoal` looking up `"mobGroup"` instead of `"group"`. It now checks both keys to maintain backwards compatibility while aligning with the GUI.
*   **`MobCreatorScreen.java`**:
    *   Exposed Parameter 2 (Range/Radius) in the Mob Creator Screen goals configuration page for `HEAL_ALLIES`, `AVOID_MOB`, `AVOID_GROUP`, `TARGET_GROUP`, and `AVOID_PLAYER_WEARING`, resolving a UI disconnect where these values could not be loaded or saved.
    *   Remapped Parameter 2's textbox label for avoid/target goals to `"gui.custom_mobs.creator.goal.radius"` (displaying "Radius" instead of "Call Range").
### Layman's Explanation
*   **Customizable Avoid Radius:** You can now configure the radius at which mobs will flee from specific items, entities, or groups in the Mob Creator Screen AI goals settings.
*   **Avoid Group Goal Fix:** Fixed a parameter mapping mismatch that broke the `Avoid Group` behavior when saving custom mobs via the Mob Creator screen.

---

## [Build 124] - Vanilla Spawner Limits & Raid Editor Display Names (Issue #4)
### Technical Changes (By Class)
*   **`RPGMobSpawnerBlockEntity.java`**:
    *   Fixed a bug where spawners configured to spawn vanilla Minecraft entities (like creepers or zombies) bypassed the `maxAlive` limit. Spawner blocks now check `Mob.class` instead of `CustomMobEntity.class`, evaluating both custom template IDs and vanilla registry ResourceLocations to correctly enforce max alive limits and prevent infinite spawning lag.
*   **`RaidEditorScreen.java`**:
    *   **Wave Mobs and Add Mobs list:** Replaced raw registry/namespace ID rendering with corresponding human-readable template name lookups, using a new private `truncate` helper to prevent text overflows.
    *   **Detailed list hovers:** Overhauled manual hover tooltips on list rows. Hovering over a wave mob now displays the template display name, internal ID resource key, configured spawn quantity, and elite chance. Hovering over an addable mob displays its name and ID.
### Layman's Explanation
*   **Vanilla Spawner Limit Safeguard:** Setting a spawner block to spawn vanilla Minecraft mobs now respects the `Max Mobs` settings limit, preventing infinite mob spawning issues.
*   **Raid Editor UI Overhaul:** Custom mob names are now cleanly formatted and truncated in the wave mobs and add mobs lists. Hovering over a mob row displays details like the entity ID, spawn count, and elite chance in a premium popup tooltip.

---

## [Build 123] - Translatable Mobs Abilities & Custom Damage per Melee Attack Slot (Issue #4)
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Added a `doHurtTarget(Entity target, double customDamage)` overload. This method temporarily sets the mob's base attack damage attribute to `customDamage` during the damage transaction call and restores the original value afterwards, ensuring vanilla mechanics are fully respected.
    *   Updated `CustomMeleeAttackGoal`, `CustomMeleeAOEAttackGoal`, and `CustomKnockbackAttackGoal` to check if a `"damage"` string parameter is specified inside the active goal's JSON parameters, parsing and passing it to the custom `doHurtTarget` overload.
*   **`MobCreatorScreen.java`**:
    *   **Abilities list:** Replaced hardcoded ability display names and descriptions with `Component.translatable` lookups (`ability.custom_mobs.<name>` and `ability_desc.custom_mobs.<name>`), falling back to English defaults if translations are missing.
    *   **Melee Goals parameters:** Registered custom `"damage"` parameter mappings (including fields, tooltips, and labels) for `MELEE`, `MELEE_AOE`, and `KNOCKBACK` attacks under `isParamActive`, `loadActiveMobGoalDetails`, `saveActiveMobGoalDetails`, and `getParamLabel`.
*   **Language JSON files (`de_de`, `es_es`, `fr_fr`, `pt_br`, `zh_cn`, `ja_jp`, `ko_kr`, `ru_ru`, `en_us`)**:
    *   Registered `"gui.custom_mobs.creator.goal.tooltip.melee_damage"` along with localized ability name and description translation keys across all language JSON files.
### Layman's Explanation
*   **Custom Damage per Melee Attack:** You can now configure different damage amounts for each individual melee attack goal (like a weak sweep attack vs. a heavy smash attack) inside the Mob Creator Screen under the AI goals parameter settings.
*   **Fully Translatable Abilities:** Custom passive and active abilities (and their hover descriptions) are now fully localized and translatable into other languages using standard Minecraft language resource files.

---

## [Build 122] - RPG Spawner Pulse, Cooldowns, Name Hovers & Spawning Env UI (Issue #4)
### Technical Changes (By Class)
*   **`RPGMobSpawnerBlockEntity.java`**:
    *   Fixed redstone pulse lockups: Attempting to spawn now returns a boolean status, and `cooldownTimer` is only set when the spawn successfully completes. Failed pulses due to day/night checks, lack of nearby players, or max alive limits will no longer block future redstone signals.
    *   Corrected double-cooldown logic: Removed the line where `spawnerCooldown` overrode `spawnRate`. Spawner blocks now tick normally every `spawnRate` ticks and enforce `spawnerCooldown` as a delay between successful spawns, letting both settings work together properly.
*   **`MobCreatorScreen.java`**:
    *   **Spawner Settings UI (`SpawnerEditScreen`):** Implemented hover tooltips displaying the full display name and resource ID for items in the template selection list.
    *   **Mob Spawning tab:** Replaced the separate, contradictory `Surface Only` and `Caves spawn Only` checkboxes with a single `Spawn Environment` cycle button (`ANY`, `SURFACE`, `CAVES`) to make configuring surface-and-cave spawning intuitive and error-proof.
*   **Language JSON files (`de_de`, `es_es`, `fr_fr`, `pt_br`, `zh_cn`, `ja_jp`, `ko_kr`, `ru_ru`, `en_us`)**:
    *   Registered `"gui.custom_mobs.creator.label.spawn_env"` and `"gui.custom_mobs.tooltip.creator.spawn_env"` translations using a python scratch script.
### Layman's Explanation
*   **Spawner Redstone Pulse & Cooldowns Fixed:** Spawner blocks set to redstone-pulse mode now trigger spawning reliably every time they receive a pulse (if not on cooldown). If a spawn attempt fails (e.g. no player is near), the pulse is no longer ignored, and the block doesn't lock up. Both Spawn Rate (frequency of checks) and Cooldown (delay after success) now work together in normal mode.
*   **Full Template Names in Spawner UI:** Hovering over a truncated mob name in the spawner list now displays the full name and internal ID in a tooltip.
*   **Spawning Environment UI simplified:** The confusing surface/cave checkboxes are merged into a single cycle button: `ANY` (spawns both underground and under sky), `SURFACE` (sky only), and `CAVES` (underground only).

---

## [Build 121] - Scrollable Sidebar for Projectile Creator Menu (Issue #3)
### Technical Changes (By Class)
*   **`ProjectileCreatorScreen.java`**:
    *   Added a `sidebarScrollOffset` variable to track scroll states of the sidebar template list.
    *   Updated `init()` to bound the scroll offset to the current template list count to prevent out-of-bounds rendering.
    *   Overrode `mouseScrolled()` to intercept mouse wheel actions on the sidebar region, adjusting `sidebarScrollOffset` accordingly.
    *   Updated `render()` to only draw the visible subset of projectiles based on the scroll offset and list viewport.
    *   Modified `mouseClicked()` to offset selection coordinates by the scroll offset, making selection click-to-select fully functional while scrolled.
### Layman's Explanation
*   **Scrollable Projectile List:** When playtesters have created enough custom projectiles, the sidebar list will now smoothly transition into a scrollable list, allowing them to scroll through all templates using the mouse wheel (fully matching the behavior of the Mob Creator Screen sidebar).

---

## [Build 120] - Fix Spawning and Limits for Vanilla Minions (Issue #2)
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Updated `SpawnMinionsGoal.tick()` to properly check and spawn vanilla entities (like zombies or skeletons) if configured as the `minionMobId` parameter of the portal, instead of trying to instantiate them as custom mobs.
    *   Modified the active minion query to scan for `Mob.class` instead of `CustomMobEntity.class`, correctly counting both custom and vanilla minion entities against the `maxMinions` limit to prevent infinite spawning bugs.
### Layman's Explanation
*   **Vanilla Portal Minions Fixed:** Fixed a bug where configuring a minion portal to spawn vanilla mobs (like Minecraft zombies or skeletons) would result in broken/white-box entities and cause them to spawn infinitely without respecting the configured maximum minion limits. The portal now spawns vanilla minion types correctly and enforces spawn caps properly.

---

## [Build 119] - SCARE_GROUP AI Goal & Group Autocomplete Suggestions (Issue #2)
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Implemented ticking logic for the new `SCARE_GROUP` AI goal type. When active, it scans for surrounding `PathfinderMob` entities within the configured scare radius (defaulting to `8.0` blocks) and check if they belong to custom mob templates matching the specified `target_group`. Any matching mobs are scared and navigate away.
*   **`MobCreatorScreen.java`**:
    *   Added `"SCARE_GROUP"` to the `POSSIBLE_GOALS` array.
    *   Added parameter load, save, parameter label translation mappings, and field visibility checks for `SCARE_GROUP` (parameter 1 is `"target_group"`, parameter 2 is `"range"`).
    *   Implemented dynamic autocomplete suggestions for group-based goals (`AVOID_GROUP`, `TARGET_GROUP`, and `SCARE_GROUP`), suggesting all unique group names currently defined on loaded custom mobs.
*   **Language JSON files (`de_de`, `es_es`, `fr_fr`, `pt_br`, `zh_cn`, `ja_jp`, `ko_kr`, `ru_ru`, `en_us`)**:
    *   Registered `"gui.custom_mobs.goal_desc.scare_group"` across all language files using an automated scratch script.
### Layman's Explanation
*   **Scare Group AI Behavior:** Added a new AI goal type called `SCARE_GROUP`. Mobs with this behavior will scare away other custom mobs belonging to a specific group (e.g. scaring away all "undead" group mobs) within a configurable radius.
*   **Group Name Autocomplete:** Group-related configuration text fields (for scaring, avoiding, or targeting specific groups) now provide autocomplete suggestion dropdowns listing all existing custom mob group names.

---

## [Build 118] - Customizable Scare Radius for SCARE_MOB (Issue #2)
### Technical Changes (By Class)
*   **`CustomMobEntity.java`**:
    *   Replaced the hardcoded double range value of `8.0` blocks under `SCARE_MOB` goal ticking with dynamic parameter parsing from the `"range"` parameter value, falling back safely to `8.0` if not set or invalid.
*   **`MobCreatorScreen.java`**:
    *   Exposed parameter 2 (`p2Visible = true`) in `loadActiveMobGoalDetails()` for the `SCARE_MOB` goal type, mapping the textbox load/save behavior to `"range"` with a default value of `8.0`.
    *   Mapped parameter 2 of the `SCARE_MOB` goal to `"gui.custom_mobs.creator.goal.radius"` (fallback `"Radius"`) inside `getParamLabel()`.
### Layman's Explanation
*   **Customizable Scare Radius:** The `SCARE_MOB` behavior now supports a customizable "Radius" parameter instead of using a hardcoded value of 8.0 blocks. Playtesters can configure exactly how far away target mobs will be scared by this custom mob.

---

## [Build 117] - Parameter Mappings for All AI Goals (Issue #2)
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Expanded `getParamLabel()` to fully support parameter translations and descriptive fallback labels for all remaining AI goals in the framework, including: Heal Allies, Avoid Light/Mob/Group, Target Group, Scare Mob, Avoid Player Wearing, Explode on Death/Contact/Low Health, Damage/Effect on Contact/Attack, Split on Death, Summon Minions, Teleport on Low Health/Hit, Teleport Behind Target, Pull Target, Rage Mode, Ambush, Fire Trail, Frost Touch, Disarm Strike, Lightning Strike, Gift Giver, Call Help, Steal Item, Burrow, and Imitate Sounds.
### Layman's Explanation
*   **Descriptive AI Parameter Labels (Full Coverage):** Mapped and verified descriptive parameter labels for every remaining AI goal behavior in the custom mob creator interface. Now, rather than displaying generic labels like "Param 1" or "Param 2", all parameters describe exactly what configuration value they represent (such as "Light Level", "Heal Amount", "Explosion Power", "Stalk Ticks", "Cooldown Ticks", etc.) in all languages.

---

## [Build 116] - Projectile UI & Gameplay Fixes (Issue #3)
### Technical Changes (By Class)
*   **`ProjectileCreatorScreen.java`**:
    *   Aligned the suggestions autocomplete coordinates, width, and scroll values between `render()` and `mouseClicked()` by driving coordinates dynamically off the active edit box. This resolves the bug where click detection for the status effect search box was offset and failed to register clicks.
    *   Replaced the basic `truncate(suggestion, 22)` string truncation with dynamic horizontal scrolling text formatting, matching the behavior of the Mob Creator Screen. This allows playtesters to view long sound and particle names in full.
    *   Removed redundant `suggestionsYOffset` and related calculations.
*   **`CustomProjectileRenderer.java`**:
    *   Centered custom models (GeckoLib or Java) vertically inside the projectile's bounding box by translating the render `poseStack` upwards on the Y-axis by half of the bounding box height (`entity.getBbHeight() * 0.5D`).
    *   Fixed a bug where the renderer's dummy Mob template didn't inherit the custom projectile's configured hitbox dimensions, by assigning `fakeMob.hitboxWidth = data.hitboxWidth` and `fakeMob.hitboxHeight = data.hitboxHeight`.
*   **`CustomProjectileEntity.java`**:
    *   Enabled entity hit logic during the landed phase for ground summons (`isGroundSummon == true`) by allowing `onHitEntity()` to bypass the `ticksAfterLanding > -1` early return check.
    *   Added entity collision scanning to the `ticksAfterLanding > 0` tick updates on the server level, executing `onHitEntity()` once per newly-collided entity so potion effects and damage are applied successfully when targets step on landed projectiles.
### Layman's Explanation
*   **Autocomplete Click Detection & Scrolling:** Fixed a bug where playtesters could not select search suggestions for potion effect IDs because click detection was offset. Autocomplete suggestions now register clicks correctly, and long sound names now scroll horizontally when hovered so they are no longer cut off.
*   **Centered Projectile Models:** Centered custom model renderings vertically inside their collision box. This aligns the visual projectile with its actual hitbox in-game.
*   **Landed Projectile Hits:** Fixed a gameplay bug where ground-summoned projectile attacks (like circles, spikes, or trails) did not apply damage or potion status effects to entities that stepped on them after they had spawned on the ground.

---

## [Build 115] - Descriptive AI Goal Parameter Labels
### Technical Changes (By Class)
*   **`MobCreatorScreen.java`**:
    *   Updated the `getParamLabel()` helper method to fully map all parameters for common combat/behavior goals (Melee, Melee AOE, Knockback, Ranged, Shotgun Attack, Orbiting Shield, and Aerial Ranged attacks) to their corresponding descriptive translation keys (e.g., `sound_event`, `melee_damage_delay`, `reach`, `width`, `knockback_distance`, `projectile_id`, `damage`, `accuracy`, `quantity`, `spread`, `orbit_radius`, `speed`, and `duration`) and fallbacks. This fixes the issue where they were generically labeled as "Param X".
*   **Language Files (`en_us.json`, `es_es.json`, etc.)**:
    *   Added the missing `"gui.custom_mobs.creator.goal.gravity": "Gravity:"` translation key (and translated equivalents) to all JSON language files (`de_de`, `es_es`, `fr_fr`, `pt_br`, `zh_cn`, `ja_jp`, `ko_kr`, `ru_ru`, `en_us`).
### Layman's Explanation
*   **Descriptive AI Parameter Labels:** Fixed a major UI bug where parameter names for standard AI goals (like Melee, Knockback, Ranged, Shotgun, Orbiting Shield, and Aerial attacks) showed up generically as "Param 1", "Param 2", etc. They now display their actual descriptive labels (e.g., "Damage Delay", "Orbit Radius", "Rotation Speed", "Gravity") regardless of the language configured.

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
