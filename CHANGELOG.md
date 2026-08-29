# Changelog

All notable changes to the Custom Mobs Framework project are documented in this file in plain, simple terms.

---

## [Build 151] - Custom Boss Bars & Dedicated Boss Tab
* **In-Game Boss Bars:** Any custom mob can now have an on-screen boss health bar with customizable colors, styles, titles, and activation ranges.
* **Dedicated Boss Tab:** Added a "Boss" tab in the Mob Creator menu with a live visual preview, custom title box, color switcher, style switcher, range setting, and atmosphere toggles (dark sky, fog, boss music).
* **Automatic Health & Range Tracking:** Boss bars update mob health smoothly in real time, appear when players walk near, and disappear when players leave or the boss is defeated.

---

## [Build 150] - Despawning System Overhaul & Spawning Tab Menu Fix
* **Reliable Mob Despawning:** Fixed custom mobs not despawning when players move far away (over 128 blocks) or when dungeon chunks unload and reload.
* **Spawning Tab Layout Fix:** Fixed an issue where despawn and lifetime settings were hidden or cut off at the bottom of the Mob Creator menu. All 12 spawning settings are now neatly arranged and fully visible.

---

## [Build 149] - Configurable Mob Lifetime & Performance Despawning
* **Mob Lifetime Timer:** Added an optional lifetime timer in seconds for custom mobs. When the timer ends, the mob despawns automatically (pauses while fighting players).
* **Chunk Unload Despawning:** Added a toggle to automatically despawn mobs when their chunk unloads, preventing lag from mobs accumulating in unloaded dungeon rooms.
* **Far Away Despawning:** Added a toggle to allow mobs to despawn naturally when players are far away.

---

## [Build 148] - Cave & Subterranean Raid Spawning Fix
* **Subterranean Raid Bounds:** Fixed an issue where cave raid monsters (like flying bees or tall chamber mobs) would accidentally spawn on the world surface above. Raid mobs are now strictly kept inside the vertical area of the Raid Block.

---

## [Build 147] - Dual Raid Radii, Crocodile Swimming, Boss Bars & Better Tooltips
* **Dual Raid Radii:** Separated mob spawn distance from player escape distance in Raid Blocks so monsters spawn close while players have room to move.
* **Amphibious Movement:** Mobs with the Amphibious behavior now seamlessly switch between walking on land and swimming in water.
* **Elite & Boss Icons:** Star (⭐) icons now appear on Elite mob names, and skull (☠️) icons appear on Boss mob names.
* **Raid Reward Tooltips:** Rewards in the Bestiary and Raid Editor now show readable item names and hover tooltips with drop rates and command info.

---

## [Build 145] - Epic Fight & GeckoLib Startup Crash Fix
* **Loading Screen Crash Fix:** Fixed a game crash during startup when Epic Fight or GeckoLib scanned resource pack folders with uppercase letters or spaces.

---

## [Build 144] - Raid Area Warning Message & Live Countdown
* **Raid Warning UI:** When players step outside an active raid zone, a bold red warning title and live countdown appear on screen prompting them to return before the raid fails.

---

## [Build 143] - Redstone Raid Triggers & Despawn Protection
* **Redstone Activations:** Raid Blocks can now be triggered using redstone signals (levers, buttons, pressure plates, clocks).
* **Raid Mob Despawn Protection:** Raid and Spawner mobs will no longer despawn when players run away to heal or kite during combat.
* **Cooldown Timer Fix:** Saved 0-second cooldowns no longer reset to default timers after reloading the world.

---

## [Build 142] - Raid Cooldown Fix & Passive Mob Raids
* **Wave Progression Fix:** Fixed a bug where Raid Blocks skipped cooldowns and spawned endless loops of waves.
* **Spawn Rule Bypass for Raids:** Mobs in raids now ignore natural habitat restrictions (like cave-only rules), allowing any mob to spawn in raid encounters.
* **Passive & Modded Mobs in Raids:** Added full support for passive animals, villagers, and modded mobs in raid waves.

---

## [Build 141] - Ranged & Shotgun Projectile Aiming Fix
* **Accurate Aiming:** Fixed monsters with Ranged or Shotgun attacks aiming too low at the player's feet or into the floor. Projectiles now aim accurately at the torso.

---

## [Build 140] - Marquee Scrolling Text for Menu Labels
* **Smooth Scrolling Labels:** Labels in the Spawning tab that are too long for their column (e.g. in other languages) now smoothly scroll horizontally instead of overlapping buttons or textboxes.

---

## [Build 139] - Cave Spawning Fix & 2-Column Spawning Menu
* **Underground Spawning Fix:** Fixed mobs configured for Caves or Any environment failing to spawn underground.
* **Clean 2-Column Layout:** Redesigned the Spawning tab into two organized columns so options and text boxes never overlap.

---

## [Build 138] - Item Search Input & Spawner HUD Tooltip Fixes
* **Item Search Typing:** Fixed the item search box in the Mob Creator and Raid Editor so typing, backspacing, and deleting work smoothly.
* **Spawner Block Names:** Mob names in the Spawner block menu are no longer cut off too short, and tooltips show the full template name and ID.

---

## [Build 137] - Worldwide Mob Spawn Limit
* **Global Spawn Limit:** Added a configurable "World Limit" setting in the Spawning tab to prevent custom mobs from overpopulating the world. Raid and Spawner mobs bypass this limit.

---

## [Build 136] - Mob Search Bar, Alphabetical List & Projectile Cleanup
* **Searchable Mob List:** Added a search box to the Mob Creator sidebar and sorted all mobs alphabetically.
* **Projectile Mob Leak Fix:** Prevented custom projectiles from accidentally appearing as spawnable mobs in lists and in the world.
* **Sound & Particle Search:** Fixed sound and particle search suggestion dropdowns in the Projectile Creator.

---

## [Build 135] - Optional Spawn Coordinates Command
* **Summon Coordinates:** Added optional `X Y Z` coordinates to `/custom_mobs spawn-mob` so mobs can be spawned at specific coordinates.

---

## [Build 134] - Avoid Mob & Avoid Armor AI Fixes
* **Fleeing Behaviors Fix:** Fixed a settings mismatch that caused "Avoid Mob" and "Avoid Player Wearing" behaviors to be ignored. Mobs now correctly run away from specified mobs or armor items.

---

## [Build 133] - Avoid Goal Autocomplete Suggestions
* **Entity & Armor Suggestions:** Added autocomplete suggestions when choosing which mobs or armor items a custom mob should run away from.

---

## [Build 132] - Summon Goal Editing Fix
* **Summon Settings Visible:** Fixed a bug where settings for special summon attacks (like Tether Drain and Chase Snake) were hidden and could not be edited in the menu.

---

## [Build 131] - Biome Group Tag Spawning
* **Biome Groups:** Added support for biome group tags (like `#minecraft:is_forest`) so mobs can spawn across entire categories of biomes.

---

## [Build 130] - Projectile Creator Search Bar
* **Searchable Projectiles:** Added a search box at the top of the Projectile Creator sidebar to quickly filter and find projectiles.

---

## [Build 129] - Minion Portal Settings Loading Fix
* **Portal Settings Loading:** Fixed a bug where Minion Portal parameters (mob type, spawn rate, limits) failed to load when editing existing mobs.

---

## [Build 128] - Bestiary Search Bar & Hover Tooltips
* **Searchable Bestiary:** Added a search box to the Bestiary screen, along with detailed hover tooltips showing full mob and raid names.

---

## [Build 127] - Raid Loot Search Fix
* **Loot Grid Fix:** Fixed the raid loot item selector showing a blank screen if you typed a search after scrolling down.

---

## [Build 126] - Raid Editor Mob Search Filter
* **Searchable Raid Mobs:** Added a search box to the "Add Mob" list in the Raid Editor to quickly find and add mobs to waves.

---

## [Build 125] - Customizable Flee Radius
* **Configurable Avoid Distance:** Added a customizable radius setting for all avoidance and flee behaviors in the Mob Creator AI menu.

---

## [Build 124] - Vanilla Spawner Limits & Clean Raid Names
* **Spawner Mob Limits:** Spawners set to spawn vanilla mobs (like zombies or skeletons) now properly respect max mob count limits.
* **Readable Raid Names:** Raid mob lists now display clean, formatted mob names with detailed hover tooltips.

---

## [Build 123] - Custom Damage per Melee Attack & Translatable Abilities
* **Unique Damage per Attack:** You can now configure different damage amounts for each individual melee attack (e.g. light attack vs. heavy smash).
* **Translatable Abilities:** All mob abilities and hover descriptions are now fully translatable into different languages.

---

## [Build 122] - Spawner Redstone Pulses, Cooldowns & Spawning Environment
* **Reliable Spawner Pulses:** Fixed spawners locking up when receiving redstone pulses while no players were nearby.
* **Combined Rate and Cooldown:** Spawn rate and cooldown delays now work together smoothly.
* **Simplified Spawn Environment:** Merged confusing surface/cave checkboxes into a single cycle button (`ANY`, `SURFACE`, `CAVES`).

---

## [Build 121] - Scrollable Projectile List
* **Mouse Wheel Scrolling:** Added mouse wheel scrolling to the Projectile Creator sidebar list.

---

## [Build 120] - Vanilla Portal Minions & Limits Fix
* **Vanilla Minion Spawning:** Minion portals can now spawn vanilla mobs without graphical glitches or infinite spawning bugs.

---

## [Build 119] - Scare Group Behavior & Group Autocomplete
* **Scare Group Behavior:** Added a new AI goal allowing mobs to scare away specific groups of custom mobs (e.g. scaring away all undead).
* **Group Autocomplete:** Added autocomplete suggestions for mob group names across all group-related behaviors.

---

## [Build 118] - Customizable Scare Radius
* **Scare Range:** Made the scare radius customizable for the Scare Mob behavior instead of fixed at 8 blocks.

---

## [Build 117] - Clear AI Parameter Names
* **Descriptive Labels:** Replaced generic "Param 1" and "Param 2" labels across all AI behaviors with clear, descriptive names (like "Radius", "Damage", "Cooldown").

---

## [Build 116] - Projectile UI & Landed Hit Fixes
* **Clickable Potion Suggestions:** Fixed suggestion dropdown clicks not registering in the Projectile Creator.
* **Centered Hitboxes:** Aligned projectile models with their collision hitboxes.
* **Landed Ground Damage:** Ground-spawned projectile attacks (spikes, circles, trails) now properly deal damage when stepped on.

---

## [Build 115] - Descriptive Combat Attack Labels
* **Clear Attack Settings:** Replaced generic parameter names on Melee, Ranged, Shotgun, and Shield attacks with clear descriptions.

---

## [Build 114] - AI Settings Saving & Minion Portal Synchronization
* **Settings Persistence:** Fixed summon parameters being lost when saving or exporting custom mobs.
* **Portal Cooldowns:** Enforced cast cooldowns on minion portals and vortexes to prevent spam.
* **Auto-Cleanup:** Stuck or lost projectiles are now automatically cleaned up after 10 seconds to prevent lag.

---

## [Build 113] - Custom Death Animations
* **Full Death Animations:** Mobs now play their full custom death animations instead of instantly tilting sideways and disappearing.

---

## [Build 112] - Loot Menu Click Fix
* **Clickable Loot Boxes:** Fixed a menu bug where drop chance and quantity boxes in the Loot tab were unclickable.

---

## [Build 111] - Sidebar Scrollbar & Spawner Search Bar
* **Mob List Scrollbar:** Added a scrollbar to the Mob Creator sidebar.
* **Spawner Search:** Added a search box to the RPG Spawner block menu.
* **Reliable Loot Editing:** Fixed loot drop percentages and quantities resetting while typing.

---

## [Build 110] - Longer Animation Names
* **Increased Text Limit:** Increased the character limit for animation name fields to support long animation names.

---

## [Build 109] - Loot Drop Editing Fix
* **Saved Drop Rates:** Fixed drop chance and item count settings resetting when changing items.

---

## [Build 108] - Area Melee Sweep Attack Fix
* **Reliable Sweep Hits:** Fixed frontal sweep (AOE Melee) attacks occasionally missing targets.

---

## [Build 107] - Spawner Redstone Pulse & Cooldowns
* **Redstone Spawner Mode:** Added a toggle to make spawners activate only on redstone signals.
* **Configurable Cooldown:** Added a cooldown timer in seconds between spawns.

---

## [Build 106] - RPG Mod Compatibility & Friendly Pets
* **RPG Level Scaling:** Custom mobs are now properly recognized as monsters by RPG mods like *Mine and Slash*.
* **Tamed Pet Safety:** Tamed custom mobs immediately stop attacking players, villagers, and friendly pets.

---

## [Build 105] - Full Multi-Language Translations
* **Localized Editor:** All combat behaviors, parameters, and tooltips are now translated into all supported languages.
* **Server Crash Fix:** Fixed a server crash related to particle colors.

---

## [Build 104] - Smooth Attack Combos & Clean Templates
* **Looping Combos:** Fixed attack combo sequences getting stuck on delay steps.
* **Organized Templates:** Projectile files and mob files are now kept strictly separated.

---

## [Build 103] - Self-Damage Protection
* **No Friendly Fire on Self:** Mobs and bosses no longer damage themselves with their own projectiles or spinning shields.

---

## [Build 102] - Consistent Attack Hit Registration
* **Attack Timing:** Fixed mobs skipping damage when attack animations finished before damage delay timers.

---

## [Build 101] - Dynamic Animation Length Detection
* **Automatic Attack Timing:** The game automatically detects animation lengths so attack delays match animations perfectly.

---

## [Build 100] - Smooth Attack Animations
* **No Animation Cancelling:** Fixed attack animations flickering or cancelling prematurely upon hitting a target.

---

## [Build 99] - Multi-Line Lore Description Box
* **Word-Wrapped Lore:** The mob lore description box now supports multiple lines of text with automatic word wrapping.

---

## [Build 98] - Close-Range Melee Attack Fix
* **Point-Blank Attacks:** Mobs will continue attacking even when standing right on top of the player.

---

## [Build 97] - Expanded Lore Character Cap
* **Long Descriptions:** Restored the lore character limit up to 2048 characters.

---

## [Build 96] - Unified Combat Combo System
* **Boss Combos:** Projectiles, spells, and ranged attacks can now be chained together with melee strikes to create multi-phase boss fights.

---

## [Build 95] - Sound & Projectile Suggestions
* **Autocomplete Dropdowns:** Added search suggestions for sounds and projectiles when setting up special attacks.

---

## [Build 94] - Responsive Melee Attacks
* **Tight Space Attacks:** Monsters now initiate attacks when next to targets even inside tight hallways.

---

## [Build 93] - Scrollable AI Settings Panel
* **Scrollable AI Panel:** Added scrolling to the AI goal settings panel so all parameters are easy to access.

---

## [Build 92] - Knockback Attacks
* **Custom Knockback:** Added a knockback attack behavior that flings targets a configurable distance away.

---

## [Build 91] - Area-of-Effect Attacks
* **Cone Sweep Attacks:** Mobs can perform sweep attacks that hit all enemies in a cone in front of them.

---

## [Build 90] - Ranged Accuracy Setting
* **Accuracy Slider:** Added an accuracy slider to ranged attacks for custom projectile spread.

---

## [Build 89] - Natural Spawning on Slabs & Vegetation
* **Better Spawn Locations:** Mobs can now spawn in grass, flowers, snow, and on stairs or slabs.

---

## [Build 88] - Searchable Item Drop Selector
* **Item Search Menu:** Added a search box when choosing item drops or equipment.

---

## [Build 86] - Ordered Boss Attack Sequences
* **Sequential Combos:** Mobs can execute attacks in a specific order instead of picking attacks randomly.

---

## [Build 85] - Autocomplete Biome Names
* **Biome Suggestions:** The biome input box now suggests matching Minecraft biomes while typing.

---

## [Build 83] - Natural Flying Physics & Head Nametags
* **Flying Landing:** Flying mobs land naturally when idle, and nametags sit directly above their heads.

---

## [Build 82] - Multiple Melee Attack Slots
* **6 Melee Attacks:** Mobs can now have up to 6 distinct melee attacks, each with unique animations, delays, and damage.

---

## [Build 80] - Duplicate Mob Button & Long Lore Text
* **Copy Mob:** Added a "Copy" button to duplicate existing mob templates easily.

---

## [Build 79] - Water & Lava Swimming Monsters
* **Fluid Spawning & 3D Swimming:** Mobs can spawn in water or lava, swim in 3D, and are immune to drowning and fire.
