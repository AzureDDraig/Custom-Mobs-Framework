package ddraig.net.custommobs.client.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RaidEditorScreen extends Screen {
    private static final Gson GSON = new Gson();

    private final BlockPos pos;
    private String raidId;
    private int radius;
    private int waveCooldown;
    private int raidCooldown;
    private String description;
    private List<RaidSystem.RaidWave> waves = new ArrayList<>();
    private List<RaidSystem.RaidReward> rewards = new ArrayList<>();

    // Tabs: "Raid Info", "Waves", "Loot"
    private String activeTab = "Raid Info";

    // Textfields
    private EditBox raidIdField;
    private EditBox radiusField;
    private EditBox waveCooldownField;
    private EditBox raidCooldownField;
    private EditBox wavesCountField;
    private EditBox descriptionField;

    // Waves Tab variables
    private int selectedWaveIndex = 0;
    private String selectedWaveMobTemplate = "";
    private EditBox mobCountField;
    private EditBox eliteChanceField;
    private final List<String> availableAddMobs = new ArrayList<>();
    private final List<String> filteredAddMobs = new ArrayList<>();
    private EditBox addMobSearchField;
    private String addMobSearchQuery = "";
    private int addMobScroll = 0;
    private int waveMobsScroll = 0;
    private int wavesScroll = 0;

    // Loot Tab variables
    private int selectedRewardIndex = -1;
    private EditBox commandInputField;
    private EditBox itemRewardField; // Edits current item reward quantity
    private EditBox chanceField;
    private EditBox rewardCountField;
    private Button perPlayerToggleBtn;
    private int rewardsScroll = 0;

    // Loot tab Buttons
    private Button addItemBtn;
    private Button addCommandBtn;
    private Button removeRewardBtn;
    private Button saveSettingsBtn;

    // Item selection modal variables
    private boolean showItemSelector = false;
    private int itemSelectorScroll = 0;
    private boolean selectAllItems = true;
    private final List<ItemStack> selectorItems = new ArrayList<>();
    private EditBox itemSearchField;
    private java.util.function.Consumer<ItemStack> itemSelectionCallback;

    public RaidEditorScreen(BlockPos pos, String raidId, int radius, int waveCooldown, int raidCooldown, String description, String wavesJson, String rewardsJson) {
        super(Component.translatable("gui.custom_mobs.raid_editor.title"));
        this.pos = pos;
        this.raidId = raidId;
        this.radius = radius;
        this.waveCooldown = waveCooldown;
        this.raidCooldown = raidCooldown;
        this.description = description;

        try {
            List<RaidSystem.RaidWave> parsed = GSON.fromJson(wavesJson, new TypeToken<List<RaidSystem.RaidWave>>(){}.getType());
            if (parsed != null) this.waves.addAll(parsed);
        } catch (Exception ignored) {}

        try {
            List<RaidSystem.RaidReward> parsed = GSON.fromJson(rewardsJson, new TypeToken<List<RaidSystem.RaidReward>>(){}.getType());
            if (parsed != null) {
                this.rewards.addAll(parsed);
            }
        } catch (Exception e) {
            try {
                List<String> oldRewards = GSON.fromJson(rewardsJson, new TypeToken<List<String>>(){}.getType());
                if (oldRewards != null) {
                    for (String s : oldRewards) {
                        this.rewards.add(new RaidSystem.RaidReward(s));
                    }
                }
            } catch (Exception ignored) {}
        }

        for (String id : MobRegistry.loadedMobs.keySet()) {
            if (!id.startsWith("__proj_")) {
                availableAddMobs.add(id);
            }
        }
        
        // Dynamically add all vanilla and modded mob types (monsters, passive animals, water creatures, ambient mobs)
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
            ResourceLocation loc = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (loc != null && !loc.getNamespace().equals("custommobs")) {
                MobCategory category = type.getCategory();
                if (category != MobCategory.MISC) {
                    String id = loc.toString();
                    if (!availableAddMobs.contains(id)) {
                        availableAddMobs.add(id);
                    }
                }
            }
        });
        this.updateFilteredAddMobs();

        // Populate item selector list
        BuiltInRegistries.ITEM.keySet().forEach(res -> {
            var item = BuiltInRegistries.ITEM.get(res);
            if (item != Items.AIR) selectorItems.add(new ItemStack(item));
        });
    }

    @Override
    protected void init() {
        int panelW = 380;
        int panelH = 240;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        // Raid Info fields
        this.raidIdField = new EditBox(this.font, left + 145, top + 45, 120, 12, Component.translatable("gui.custom_mobs.raid_editor.id"));
        this.raidIdField.setValue(raidId);
        this.raidIdField.setBordered(false);
        this.raidIdField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.raid_id")));
        this.addRenderableWidget(this.raidIdField);

        this.radiusField = new EditBox(this.font, left + 145, top + 65, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.radius"));
        this.radiusField.setValue(String.valueOf(radius));
        this.radiusField.setBordered(false);
        this.radiusField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.radius")));
        this.addRenderableWidget(this.radiusField);

        this.waveCooldownField = new EditBox(this.font, left + 145, top + 85, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.wave_cooldown"));
        this.waveCooldownField.setValue(String.valueOf(waveCooldown));
        this.waveCooldownField.setBordered(false);
        this.waveCooldownField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.wave_cooldown")));
        this.addRenderableWidget(this.waveCooldownField);

        this.raidCooldownField = new EditBox(this.font, left + 145, top + 105, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.raid_cooldown"));
        this.raidCooldownField.setValue(String.valueOf(raidCooldown));
        this.raidCooldownField.setBordered(false);
        this.raidCooldownField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.raid_cooldown")));
        this.addRenderableWidget(this.raidCooldownField);

        this.wavesCountField = new EditBox(this.font, left + 145, top + 125, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.waves"));
        this.wavesCountField.setValue(String.valueOf(Math.max(1, waves.size())));
        this.wavesCountField.setBordered(false);
        this.wavesCountField.setResponder(this::updateWavesCountFromField);
        this.wavesCountField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.waves_count")));
        this.addRenderableWidget(this.wavesCountField);

        this.descriptionField = new EditBox(this.font, left + 145, top + 145, 200, 12, Component.translatable("gui.custom_mobs.raid_editor.description"));
        this.descriptionField.setValue(description);
        this.descriptionField.setBordered(false);
        this.descriptionField.setMaxLength(2048);
        this.descriptionField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.description")));
        this.addRenderableWidget(this.descriptionField);

        // Ensure waves matches default size
        updateWavesCountFromField(this.wavesCountField.getValue());

        // Waves Tab fields
        this.mobCountField = new EditBox(this.font, left + 300, top + 160, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.count"));
        this.mobCountField.setValue("1");
        this.mobCountField.setBordered(false);
        this.mobCountField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.mob_count")));
        this.addRenderableWidget(this.mobCountField);

        this.eliteChanceField = new EditBox(this.font, left + 300, top + 180, 50, 12, Component.translatable("gui.custom_mobs.raid_editor.elite_chance"));
        this.eliteChanceField.setValue("0");
        this.eliteChanceField.setBordered(false);
        this.eliteChanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.elite_chance")));
        this.addRenderableWidget(this.eliteChanceField);

        // Loot Tab fields
        this.commandInputField = new EditBox(this.font, left + 20, top + 205, 300, 12, Component.translatable("gui.custom_mobs.raid_editor.command_to_run"));
        this.commandInputField.setMaxLength(2048);
        this.commandInputField.setBordered(false);
        this.commandInputField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.command_input")));
        this.addRenderableWidget(this.commandInputField);

        this.itemRewardField = new EditBox(this.font, left + 20, top + 205, 300, 12, Component.translatable("gui.custom_mobs.raid_editor.edit_item_reward"));
        this.itemRewardField.setMaxLength(2048);
        this.itemRewardField.setBordered(false);
        this.itemRewardField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.item_reward_edit")));
        this.addRenderableWidget(this.itemRewardField);

        this.chanceField = new EditBox(this.font, left + 77, top + 173, 26, 12, Component.translatable("gui.custom_mobs.raid_editor.chance"));
        this.chanceField.setValue("1.0");
        this.chanceField.setBordered(false);
        this.chanceField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.chance")));
        this.addRenderableWidget(this.chanceField);

        this.rewardCountField = new EditBox(this.font, left + 142, top + 173, 21, 12, Component.translatable("gui.custom_mobs.raid_editor.label.reward_qty"));
        this.rewardCountField.setValue("1");
        this.rewardCountField.setBordered(false);
        this.rewardCountField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.reward_qty")));
        this.addRenderableWidget(this.rewardCountField);

        // Loot tab Buttons
        this.addItemBtn = Button.builder(Component.translatable("gui.custom_mobs.raid_editor.add_item"), btn -> {
            openItemSelector(stack -> {
                String rewardVal;
                if (stack.hasTag()) {
                    net.minecraft.nbt.CompoundTag compound = stack.save(new net.minecraft.nbt.CompoundTag());
                    rewardVal = "nbt: " + compound.toString();
                } else {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    rewardVal = itemId + " " + stack.getCount();
                }
                rewards.add(new RaidSystem.RaidReward(rewardVal, 1.0, false));
                selectedRewardIndex = rewards.size() - 1;
                itemRewardField.setValue(rewardVal);
                chanceField.setValue("1.0");
                rewardCountField.setValue(String.valueOf(stack.getCount()));
                perPlayerToggleBtn.setMessage(Component.translatable("gui.custom_mobs.raid_editor.per_raid"));
            });
        }).bounds(left + 20, top + 150, 90, 20).build();
        this.addItemBtn.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.add_item")));
        this.addRenderableWidget(this.addItemBtn);

        this.addCommandBtn = Button.builder(Component.translatable("gui.custom_mobs.raid_editor.add_command"), btn -> {
            rewards.add(new RaidSystem.RaidReward("/give <player> minecraft:diamond 5", 1.0, true));
            selectedRewardIndex = rewards.size() - 1;
            commandInputField.setValue("give <player> minecraft:diamond 5");
            chanceField.setValue("1.0");
            perPlayerToggleBtn.setMessage(Component.translatable("gui.custom_mobs.raid_editor.per_player"));
        }).bounds(left + 120, top + 150, 95, 20).build();
        this.addCommandBtn.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.add_command")));
        this.addRenderableWidget(this.addCommandBtn);

        this.removeRewardBtn = Button.builder(Component.translatable("gui.custom_mobs.raid_editor.remove_reward"), btn -> {
            if (selectedRewardIndex >= 0 && selectedRewardIndex < rewards.size()) {
                rewards.remove(selectedRewardIndex);
                selectedRewardIndex = -1;
            }
        }).bounds(left + 225, top + 150, 100, 20).build();
        this.removeRewardBtn.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.remove_reward")));
        this.addRenderableWidget(this.removeRewardBtn);

        this.perPlayerToggleBtn = Button.builder(Component.translatable("gui.custom_mobs.raid_editor.per_raid"), btn -> {
            if (selectedRewardIndex >= 0 && selectedRewardIndex < rewards.size()) {
                var r = rewards.get(selectedRewardIndex);
                r.perPlayer = !r.perPlayer;
                btn.setMessage(Component.translatable(r.perPlayer ? "gui.custom_mobs.raid_editor.per_player" : "gui.custom_mobs.raid_editor.per_raid"));
            }
        }).bounds(left + 250, top + 171, 110, 16).build();
        this.perPlayerToggleBtn.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.per_player")));
        this.addRenderableWidget(this.perPlayerToggleBtn);

        // Item search modal field
        int popW = 220;
        int popH = 190;
        int popX = (this.width - popW) / 2;
        int popY = (this.height - popH) / 2;
        this.itemSearchField = new EditBox(this.font, popX + 60, popY + 41, 140, 10, Component.translatable("gui.custom_mobs.raid_editor.search"));
        this.itemSearchField.setValue("");
        this.itemSearchField.setBordered(false);
        this.itemSearchField.visible = false;
        this.itemSearchField.setResponder(val -> {
            this.itemSelectorScroll = 0;
        });
        this.addRenderableWidget(this.itemSearchField);

        // Global save button
        this.saveSettingsBtn = Button.builder(Component.translatable("gui.custom_mobs.raid_editor.save_settings"), btn -> {
            if (showItemSelector) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(pos);
            buf.writeUtf(raidIdField.getValue().trim());
            buf.writeInt(parseIntSafe(radiusField.getValue(), radius));
            buf.writeInt(parseIntSafe(waveCooldownField.getValue(), waveCooldown));
            buf.writeInt(parseIntSafe(raidCooldownField.getValue(), raidCooldown));
            buf.writeUtf(descriptionField.getValue().trim());
            buf.writeUtf(GSON.toJson(waves));
            buf.writeUtf(GSON.toJson(rewards));

            NetworkManager.sendToServer(ModPackets.C2S_SAVE_RAID_SETTINGS, buf);
            Minecraft.getInstance().setScreen(null);
        }).bounds(left + 260, top + 10, 100, 16).build();
        this.saveSettingsBtn.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.tooltip.save")));
        this.addRenderableWidget(this.saveSettingsBtn);

        this.addMobSearchField = new EditBox(this.font, left + 95, top + 164, 100, 10, Component.literal("Search"));
        this.addMobSearchField.setValue(this.addMobSearchQuery);
        this.addMobSearchField.setBordered(false);
        this.addMobSearchField.visible = false;
        this.addMobSearchField.setResponder(val -> {
            this.addMobSearchQuery = val.trim().toLowerCase();
            this.updateFilteredAddMobs();
        });
        this.addRenderableWidget(this.addMobSearchField);
    }

    private void updateWavesCountFromField(String text) {
        int count = parseIntSafe(text, 1);
        if (count < 1) count = 1;
        if (count > 25) count = 25; // Limit wave size to prevent issues

        while (waves.size() < count) {
            waves.add(new RaidSystem.RaidWave());
        }
        while (waves.size() > count) {
            waves.remove(waves.size() - 1);
        }

        if (selectedWaveIndex >= waves.size()) {
            selectedWaveIndex = Math.max(0, waves.size() - 1);
            selectedWaveMobTemplate = "";
        }
    }

    private static int parseIntSafe(String val, int def) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDoubleSafe(String val, double def) {
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void openItemSelector(java.util.function.Consumer<ItemStack> callback) {
        this.showItemSelector = true;
        this.itemSelectorScroll = 0;
        this.itemSelectionCallback = callback;
        this.itemSearchField.setValue("");
        this.itemSearchField.visible = selectAllItems;
        this.itemSearchField.setFocused(selectAllItems);
    }

    private void closeItemSelector() {
        this.showItemSelector = false;
        this.itemSearchField.visible = false;
        this.itemSearchField.setFocused(false);
    }

    @Override
    public void tick() {
        this.raidIdField.tick();
        this.radiusField.tick();
        this.waveCooldownField.tick();
        this.raidCooldownField.tick();
        this.wavesCountField.tick();
        this.descriptionField.tick();
        this.mobCountField.tick();
        this.eliteChanceField.tick();
        this.commandInputField.tick();
        this.itemRewardField.tick();
        this.chanceField.tick();
        this.rewardCountField.tick();
        this.itemSearchField.tick();
        if (this.addMobSearchField != null) {
            this.addMobSearchField.tick();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (showItemSelector) {
            List<ItemStack> currentList;
            if (selectAllItems) {
                String query = itemSearchField.getValue().toLowerCase();
                currentList = selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).toList();
            } else {
                var player = Minecraft.getInstance().player;
                currentList = new ArrayList<>();
                if (player != null) {
                    for (var stack : player.getInventory().items) {
                        if (!stack.isEmpty()) currentList.add(stack);
                    }
                }
            }
            int totalRows = (currentList.size() + 8) / 9;
            int maxScroll = Math.max(0, totalRows - 4);
            itemSelectorScroll = Math.max(0, Math.min(maxScroll, itemSelectorScroll - (int) amount));
            return true;
        }

        if (activeTab.equals("Waves")) {
            int left = (this.width - 380) / 2;
            int top = (this.height - 240) / 2;
            // Left list: Waves index
            if (mouseX >= left + 15 && mouseX <= left + 95 && mouseY >= top + 50 && mouseY <= top + 165) {
                int maxScroll = Math.max(0, waves.size() - 8);
                wavesScroll = Math.max(0, Math.min(maxScroll, wavesScroll - (int) amount));
            }
            // Center list: Wave Mobs
            else if (mouseX >= left + 115 && mouseX <= left + 215 && mouseY >= top + 50 && mouseY <= top + 165) {
                int count = getSelectedWaveMobCount();
                int maxScroll = Math.max(0, count - 8);
                waveMobsScroll = Math.max(0, Math.min(maxScroll, waveMobsScroll - (int) amount));
            }
            // Bottom left list: Add Mob list
            else if (mouseX >= left + 15 && mouseX <= left + 195 && mouseY >= top + 175 && mouseY <= top + 230) {
                int maxScroll = Math.max(0, filteredAddMobs.size() - 5);
                addMobScroll = Math.max(0, Math.min(maxScroll, addMobScroll - (int) amount));
            }
        } else if (activeTab.equals("Loot")) {
            int maxScroll = Math.max(0, rewards.size() - 8);
            rewardsScroll = Math.max(0, Math.min(maxScroll, rewardsScroll - (int) amount));
        }
        return true;
    }

    private int getSelectedWaveMobCount() {
        if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size()) {
            return waves.get(selectedWaveIndex).mobCounts.size();
        }
        return 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = 380;
        int panelH = 240;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        if (showItemSelector) {
            int popW = 220;
            int popH = 190;
            int popX = (this.width - popW) / 2;
            int popY = (this.height - popH) / 2;

            if (mouseX < popX || mouseX > popX + popW || mouseY < popY || mouseY > popY + popH) {
                closeItemSelector();
                playClickSound();
                return true;
            }

            int tab1X = popX + 15;
            int tabY = popY + 25;
            if (mouseX >= tab1X && mouseX <= tab1X + 90 && mouseY >= tabY && mouseY <= tabY + 12) {
                selectAllItems = true;
                itemSelectorScroll = 0;
                itemSearchField.setValue("");
                itemSearchField.visible = true;
                itemSearchField.setFocused(true);
                playClickSound();
                return true;
            }
            int tab2X = popX + 115;
            if (mouseX >= tab2X && mouseX <= tab2X + 90 && mouseY >= tabY && mouseY <= tabY + 12) {
                selectAllItems = false;
                itemSelectorScroll = 0;
                itemSearchField.visible = false;
                itemSearchField.setFocused(false);
                playClickSound();
                return true;
            }

            if (selectAllItems && itemSearchField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            List<ItemStack> currentList;
            int slotYOff;
            if (selectAllItems) {
                String query = itemSearchField.getValue().toLowerCase();
                currentList = selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).toList();
                slotYOff = popY + 58;
            } else {
                var player = Minecraft.getInstance().player;
                currentList = new ArrayList<>();
                if (player != null) {
                    for (var stack : player.getInventory().items) {
                        if (!stack.isEmpty()) currentList.add(stack);
                    }
                }
                slotYOff = popY + 42;
            }

            int slotX = popX + 15;
            int itemIdx = itemSelectorScroll * 9;
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = itemIdx + row * 9 + col;
                    if (idx >= currentList.size()) break;
                    int sX = slotX + col * 21;
                    int sY = slotYOff + row * 21;
                    if (mouseX >= sX && mouseX <= sX + 20 && mouseY >= sY && mouseY <= sY + 20) {
                        if (itemSelectionCallback != null) {
                            itemSelectionCallback.accept(currentList.get(idx));
                        }
                        closeItemSelector();
                        playClickSound();
                        return true;
                    }
                }
            }
            return true;
        }

        // Clicks on Tabs
        if (mouseY >= top + 10 && mouseY <= top + 26) {
            if (mouseX >= left + 15 && mouseX <= left + 90) {
                activeTab = "Raid Info";
                playClickSound();
                return true;
            } else if (mouseX >= left + 95 && mouseX <= left + 160) {
                activeTab = "Waves";
                playClickSound();
                return true;
            } else if (mouseX >= left + 165 && mouseX <= left + 230) {
                activeTab = "Loot";
                playClickSound();
                return true;
            }
        }

        if (activeTab.equals("Waves")) {
            // Wave list selection
            if (mouseX >= left + 15 && mouseX <= left + 95 && mouseY >= top + 50 && mouseY <= top + 165) {
                int clicked = (int) ((mouseY - (top + 53)) / 14) + wavesScroll;
                if (clicked >= 0 && clicked < waves.size()) {
                    selectedWaveIndex = clicked;
                    selectedWaveMobTemplate = "";
                    playClickSound();
                    return true;
                }
            }

            // Wave Mobs list selection
            if (mouseX >= left + 115 && mouseX <= left + 215 && mouseY >= top + 50 && mouseY <= top + 165) {
                if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size()) {
                    List<String> mobIds = new ArrayList<>(waves.get(selectedWaveIndex).mobCounts.keySet());
                    int clicked = (int) ((mouseY - (top + 53)) / 14) + waveMobsScroll;
                    if (clicked >= 0 && clicked < mobIds.size()) {
                        selectedWaveMobTemplate = mobIds.get(clicked);
                        mobCountField.setValue(String.valueOf(waves.get(selectedWaveIndex).mobCounts.get(selectedWaveMobTemplate)));
                        eliteChanceField.setValue(String.valueOf(waves.get(selectedWaveIndex).mobEliteChances.getOrDefault(selectedWaveMobTemplate, 0)));
                        playClickSound();
                        return true;
                    }
                }
            }

            // Add Mob list click
            if (mouseX >= left + 15 && mouseX <= left + 195 && mouseY >= top + 175 && mouseY <= top + 230) {
                int clicked = (int) ((mouseY - (top + 178)) / 10) + addMobScroll;
                if (clicked >= 0 && clicked < filteredAddMobs.size()) {
                    String clickedMob = filteredAddMobs.get(clicked);
                    addMobToSelectedWave(clickedMob);
                    playClickSound();
                    return true;
                }
            }

            // Remove mob from wave button '-'
            if (mouseX >= left + 185 && mouseX <= left + 215 && mouseY >= top + 32 && mouseY <= top + 42) {
                if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size() && !selectedWaveMobTemplate.isEmpty()) {
                    waves.get(selectedWaveIndex).mobCounts.remove(selectedWaveMobTemplate);
                    waves.get(selectedWaveIndex).mobEliteChances.remove(selectedWaveMobTemplate);
                    selectedWaveMobTemplate = "";
                    playClickSound();
                }
                return true;
            }
        } else if (activeTab.equals("Loot")) {
            // Rewards list selection
            if (mouseX >= left + 20 && mouseX <= left + 350 && mouseY >= top + 45 && mouseY <= top + 145) {
                int clicked = (int) ((mouseY - (top + 48)) / 12) + rewardsScroll;
                if (clicked >= 0 && clicked < rewards.size()) {
                    selectedRewardIndex = clicked;
                    var r = rewards.get(clicked);
                    if (r.value.startsWith("/")) {
                        commandInputField.setValue(r.value.substring(1));
                        rewardCountField.setValue("1");
                    } else {
                        itemRewardField.setValue(r.value);
                        rewardCountField.setValue(String.valueOf(getRewardCount(r.value)));
                    }
                    chanceField.setValue(String.valueOf(r.chance));
                    perPlayerToggleBtn.setMessage(Component.translatable(r.perPlayer ? "gui.custom_mobs.raid_editor.per_player" : "gui.custom_mobs.raid_editor.per_raid"));
                    playClickSound();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addMobToSelectedWave(String mobId) {
        if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size()) {
            RaidSystem.RaidWave wave = waves.get(selectedWaveIndex);
            if (!wave.mobCounts.containsKey(mobId)) {
                wave.mobCounts.put(mobId, 1);
                wave.mobEliteChances.put(mobId, 0);
            }
            selectedWaveMobTemplate = mobId;
            mobCountField.setValue("1");
            eliteChanceField.setValue("0");
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int panelW = 380;
        int panelH = 240;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int borderC = 0xFFDFD0A0; // Gold border
        int bgC = 0xFF2D2D2D;
        int slotC = 0xFF1C1C1C;

        // Draw main editor panel
        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        // Hide/show components dynamically based on modal status & tabs
        boolean modalOpen = showItemSelector;
        saveSettingsBtn.active = !modalOpen;

        raidIdField.visible = !modalOpen && activeTab.equals("Raid Info");
        radiusField.visible = !modalOpen && activeTab.equals("Raid Info");
        waveCooldownField.visible = !modalOpen && activeTab.equals("Raid Info");
        raidCooldownField.visible = !modalOpen && activeTab.equals("Raid Info");
        wavesCountField.visible = !modalOpen && activeTab.equals("Raid Info");
        descriptionField.visible = !modalOpen && activeTab.equals("Raid Info");

        boolean modifiersVisible = !modalOpen && activeTab.equals("Waves") && selectedWaveIndex >= 0 && selectedWaveIndex < waves.size() && !selectedWaveMobTemplate.isEmpty();
        mobCountField.visible = modifiersVisible;
        eliteChanceField.visible = modifiersVisible;
        if (this.addMobSearchField != null) {
            this.addMobSearchField.visible = !modalOpen && activeTab.equals("Waves");
        }

        boolean isLootTab = !modalOpen && activeTab.equals("Loot");
        addItemBtn.visible = isLootTab;
        addCommandBtn.visible = isLootTab;
        removeRewardBtn.visible = isLootTab;

        boolean hasSelectedReward = isLootTab && selectedRewardIndex >= 0 && selectedRewardIndex < rewards.size();
        chanceField.visible = hasSelectedReward;
        perPlayerToggleBtn.visible = hasSelectedReward;

        boolean isCmdSelected = hasSelectedReward && rewards.get(selectedRewardIndex).value.startsWith("/");
        commandInputField.visible = isCmdSelected;

        boolean isItemRewardSelected = hasSelectedReward && !rewards.get(selectedRewardIndex).value.startsWith("/");
        itemRewardField.visible = isItemRewardSelected;
        rewardCountField.visible = isItemRewardSelected;

        // Apply edits in real time
        if (modifiersVisible) {
            int cnt = parseIntSafe(mobCountField.getValue(), 1);
            int elite = parseIntSafe(eliteChanceField.getValue(), 0);
            waves.get(selectedWaveIndex).mobCounts.put(selectedWaveMobTemplate, cnt);
            waves.get(selectedWaveIndex).mobEliteChances.put(selectedWaveMobTemplate, elite);
        }

        if (hasSelectedReward) {
            double c = parseDoubleSafe(chanceField.getValue(), 1.0);
            if (c < 0.0) c = 0.0;
            if (c > 1.0) c = 1.0;
            rewards.get(selectedRewardIndex).chance = c;

            if (isCmdSelected) {
                rewards.get(selectedRewardIndex).value = "/" + commandInputField.getValue().trim();
            } else {
                String rawItemVal = itemRewardField.getValue().trim();
                int qty = parseIntSafe(rewardCountField.getValue(), 1);
                if (qty < 1) qty = 1;
                if (qty > 64) qty = 64;
                rewards.get(selectedRewardIndex).value = setRewardCount(rawItemVal, qty);
            }
        }

        // Draw Tab selectors
        drawTab(graphics, Component.translatable("gui.custom_mobs.raid_editor.tab.raid_info").getString(), left + 15, top + 10, activeTab.equals("Raid Info"));
        drawTab(graphics, Component.translatable("gui.custom_mobs.raid_editor.tab.waves").getString(), left + 95, top + 10, activeTab.equals("Waves"));
        drawTab(graphics, Component.translatable("gui.custom_mobs.raid_editor.tab.loot").getString(), left + 165, top + 10, activeTab.equals("Loot"));

        if (activeTab.equals("Raid Info")) {
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.raid_name_id"), left + 20, top + 47, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 42, 130, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.raid_radius"), left + 20, top + 67, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 62, 60, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.wave_cooldown"), left + 20, top + 87, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 82, 60, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.raid_cooldown"), left + 20, top + 107, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 102, 60, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.number_of_waves"), left + 20, top + 127, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 122, 60, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.lore_description"), left + 20, top + 147, 0xFFCCCCCC, false);
            UIHelper.drawRecessedSlot(graphics, left + 140, top + 142, 215, 16, borderC, slotC);

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.info1"), left + 20, top + 175, 0xFF888888, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.info2"), left + 20, top + 190, 0xFF888888, false);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.info3"), left + 20, top + 205, 0xFF888888, false);

        } else if (activeTab.equals("Waves")) {
            // Draw Waves list (left column)
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.waves_header"), left + 15, top + 38, 0xFFFFFFFF, false);
            UIHelper.drawRecessedSlot(graphics, left + 15, top + 50, 80, 115, borderC, slotC);

            int rowH = 14;
            for (int i = 0; i < Math.min(8, waves.size() - wavesScroll); i++) {
                int idx = i + wavesScroll;
                int rowY = top + 53 + i * rowH;
                int color = (idx == selectedWaveIndex) ? 0xFFFFFFFF : 0xFF888888;
                if (idx == selectedWaveIndex) {
                    graphics.fill(left + 17, rowY - 1, left + 93, rowY + rowH - 1, 0x40FFFFFF);
                }
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.wave_prefix").append(" " + (idx + 1)), left + 20, rowY, color, false);
            }

            // Draw Wave Mobs list (middle column)
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.wave_mobs_header"), left + 115, top + 38, 0xFFFFFFFF, false);
            graphics.drawString(this.font, "-", left + 188, top + 38, 0xFFFF5555, false);
            UIHelper.drawRecessedSlot(graphics, left + 115, top + 50, 100, 115, borderC, slotC);

            if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size()) {
                RaidSystem.RaidWave wave = waves.get(selectedWaveIndex);
                List<String> mobIds = new ArrayList<>(wave.mobCounts.keySet());
                for (int i = 0; i < Math.min(8, mobIds.size() - waveMobsScroll); i++) {
                    int idx = i + waveMobsScroll;
                    String mobId = mobIds.get(idx);
                    int count = wave.mobCounts.get(mobId);
                    int rowY = top + 53 + i * rowH;
                    int color = mobId.equals(selectedWaveMobTemplate) ? 0xFFFFFFFF : 0xFF888888;

                    if (mobId.equals(selectedWaveMobTemplate)) {
                        graphics.fill(left + 117, rowY - 1, left + 213, rowY + rowH - 1, 0x40FFFFFF);
                    }

                    ddraig.net.custommobs.data.MobData m = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(mobId);
                    String displayName = (m != null) ? m.name : (mobId.contains(":") ? mobId.substring(mobId.indexOf(":") + 1) : mobId);
                    graphics.drawString(this.font, truncate(displayName, 14) + ": " + count, left + 120, rowY, color, false);
                }
            }

            // Draw Add Mob list (bottom left)
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.add_mob_header"), left + 15, top + 168, 0xFFFFFFFF, false);
            if (this.addMobSearchField != null && this.addMobSearchField.visible) {
                UIHelper.drawRecessedSlot(graphics, this.addMobSearchField.getX() - 4, this.addMobSearchField.getY() - 3, this.addMobSearchField.getWidth() + 8, this.addMobSearchField.getHeight() + 6, borderC, slotC);
            }
            UIHelper.drawRecessedSlot(graphics, left + 15, top + 175, 180, 56, borderC, slotC);
            for (int i = 0; i < Math.min(5, filteredAddMobs.size() - addMobScroll); i++) {
                int idx = i + addMobScroll;
                String mobId = filteredAddMobs.get(idx);
                int rowY = top + 178 + i * 10;
                ddraig.net.custommobs.data.MobData m = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(mobId);
                String displayName = (m != null) ? m.name : mobId;
                graphics.drawString(this.font, truncate(displayName, 26), left + 20, rowY, 0xFFCCCCCC, false);
            }

            // Draw modifiers (bottom right)
            if (modifiersVisible) {
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.mob_count"), left + 225, top + 162, 0xFFCCCCCC, false);
                UIHelper.drawRecessedSlot(graphics, left + 295, top + 158, 60, 16, borderC, slotC);

                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.elite_chance"), left + 225, top + 182, 0xFFCCCCCC, false);
                UIHelper.drawRecessedSlot(graphics, left + 295, top + 178, 60, 16, borderC, slotC);
            }
        } else if (activeTab.equals("Loot")) {
            // Draw Rewards list
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.rewards_header"), left + 20, top + 30, 0xFFFFFFFF, false);
            UIHelper.drawRecessedSlot(graphics, left + 20, top + 45, 340, 100, borderC, slotC);

            int rowH = 12;
            for (int i = 0; i < Math.min(8, rewards.size() - rewardsScroll); i++) {
                int idx = i + rewardsScroll;
                var reward = rewards.get(idx);
                int rowY = top + 48 + i * rowH;
                int color = (idx == selectedRewardIndex) ? 0xFFFFFFFF : 0xFFCCCCCC;

                if (idx == selectedRewardIndex) {
                    graphics.fill(left + 22, rowY - 1, left + 358, rowY + rowH - 1, 0x40FFFFFF);
                }

                String displayTxt = getRewardDisplayName(reward.value) + " (" + (int)(reward.chance * 100) + "%) " + (reward.perPlayer ? Component.translatable("gui.custom_mobs.raid_editor.per_player").getString() : Component.translatable("gui.custom_mobs.raid_editor.per_raid").getString());
                graphics.drawString(this.font, displayTxt, left + 25, rowY, color, false);
            }

            if (hasSelectedReward) {
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.chance"), left + 20, top + 175, 0xFFFFFFFF, false);
                UIHelper.drawRecessedSlot(graphics, left + 75, top + 171, 30, 16, borderC, slotC);

                if (rewardCountField.visible) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.reward_qty"), left + 115, top + 175, 0xFFFFFFFF, false);
                    UIHelper.drawRecessedSlot(graphics, left + 140, top + 171, 25, 16, borderC, slotC);
                }

                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.distribution"), left + 175, top + 175, 0xFFFFFFFF, false);

                if (commandInputField.visible) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.command_reward_info"), left + 20, top + 192, 0xFFFFFFFF, false);
                    UIHelper.drawRecessedSlot(graphics, left + 15, top + 202, 310, 16, borderC, slotC);
                }

                if (itemRewardField.visible) {
                    graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.item_reward_info"), left + 20, top + 192, 0xFFFFFFFF, false);
                    UIHelper.drawRecessedSlot(graphics, left + 15, top + 202, 310, 16, borderC, slotC);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        // Render manual tooltips for lists and labels
        List<Component> customTooltip = null;
        if (!showItemSelector) {
            if (activeTab.equals("Waves")) {
                // Hover over Waves list (left column)
                if (mouseX >= left + 15 && mouseX <= left + 95 && mouseY >= top + 50 && mouseY <= top + 165) {
                    customTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.waves_list"));
                }
                // Hover over Wave Mobs list (middle column)
                else if (mouseX >= left + 115 && mouseX <= left + 215 && mouseY >= top + 50 && mouseY <= top + 165) {
                    if (selectedWaveIndex >= 0 && selectedWaveIndex < waves.size()) {
                        RaidSystem.RaidWave wave = waves.get(selectedWaveIndex);
                        List<String> mobIds = new ArrayList<>(wave.mobCounts.keySet());
                        int clickedRow = (int) ((mouseY - (top + 53)) / 14);
                        int idx = clickedRow + waveMobsScroll;
                        if (idx >= 0 && idx < mobIds.size()) {
                            String mobId = mobIds.get(idx);
                            int count = wave.mobCounts.getOrDefault(mobId, 0);
                            int elite = wave.mobEliteChances.getOrDefault(mobId, 0);
                            ddraig.net.custommobs.data.MobData m = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(mobId);
                            String displayName = (m != null) ? m.name : mobId;
                            customTooltip = List.of(
                                Component.literal(displayName),
                                Component.literal("ID: " + mobId).withStyle(net.minecraft.ChatFormatting.GRAY),
                                Component.literal("Count: " + count).withStyle(net.minecraft.ChatFormatting.GREEN),
                                Component.literal("Elite Chance: " + elite + "%").withStyle(net.minecraft.ChatFormatting.GOLD)
                            );
                        } else {
                            customTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.wave_mobs_list"));
                        }
                    } else {
                        customTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.wave_mobs_list"));
                    }
                }
                // Hover over Add Mob list
                else if (mouseX >= left + 15 && mouseX <= left + 195 && mouseY >= top + 175 && mouseY <= top + 230) {
                    int clickedRow = (int) ((mouseY - (top + 178)) / 10);
                    int idx = clickedRow + addMobScroll;
                    if (idx >= 0 && idx < filteredAddMobs.size()) {
                        String mobId = filteredAddMobs.get(idx);
                        ddraig.net.custommobs.data.MobData m = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(mobId);
                        String displayName = (m != null) ? m.name : mobId;
                        customTooltip = List.of(
                            Component.literal(displayName),
                            Component.literal("ID: " + mobId).withStyle(net.minecraft.ChatFormatting.GRAY)
                        );
                    } else {
                        customTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.add_mob_list"));
                    }
                }
                // Hover over minus button '-'
                else if (mouseX >= left + 185 && mouseX <= left + 215 && mouseY >= top + 32 && mouseY <= top + 42) {
                    customTooltip = List.of(Component.translatable("gui.custom_mobs.tooltip.remove_mob"));
                }
            }
        }

        if (customTooltip != null) {
            graphics.renderComponentTooltip(this.font, customTooltip, mouseX, mouseY);
        }

        // Render Item Selector Modal
        if (showItemSelector) {
            int popW = 220;
            int popH = 190;
            int popX = (this.width - popW) / 2;
            int popY = (this.height - popH) / 2;

            graphics.fill(0, 0, this.width, this.height, 0x88000000);
            UIHelper.drawBeveledPanel(graphics, popX, popY, popW, popH, borderC, bgC);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.select_item_header"), popX + 10, popY + 10, 0xFFD4AF37, false);

            // Selector Tabs
            int tab1X = popX + 15;
            int tabY = popY + 25;
            boolean hoverAll = mouseX >= tab1X && mouseX <= tab1X + 90 && mouseY >= tabY && mouseY <= tabY + 12;
            UIHelper.drawShadedButton(graphics, tab1X, tabY, 90, 12, hoverAll, selectAllItems ? slotC : 0xFF3C3C3C);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.all_items_tab"), tab1X + 18, tabY + 2, selectAllItems ? 0xFFD4AF37 : 0xFFCCCCCC, false);

            int tab2X = popX + 115;
            boolean hoverInv = mouseX >= tab2X && mouseX <= tab2X + 90 && mouseY >= tabY && mouseY <= tabY + 12;
            UIHelper.drawShadedButton(graphics, tab2X, tabY, 90, 12, hoverInv, !selectAllItems ? slotC : 0xFF3C3C3C);
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.inventory_tab"), tab2X + 18, tabY + 2, !selectAllItems ? 0xFFD4AF37 : 0xFFCCCCCC, false);

            // Filter items list
            List<ItemStack> currentList;
            int slotYOff;
            if (selectAllItems) {
                String query = itemSearchField.getValue().toLowerCase();
                currentList = selectorItems.stream().filter(stack -> stack.getHoverName().getString().toLowerCase().contains(query)).toList();

                // Draw Search Box
                graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.search"), popX + 15, popY + 43, 0xFFFFFFFF, false);
                itemSearchField.setX(popX + 60);
                itemSearchField.setY(popY + 41);
                itemSearchField.render(graphics, mouseX, mouseY, 0.0f);
                UIHelper.drawOutline(graphics, itemSearchField.getX() - 2, itemSearchField.getY() - 2, itemSearchField.getWidth() + 4, itemSearchField.getHeight() + 4, borderC);

                slotYOff = popY + 58;
            } else {
                var player = Minecraft.getInstance().player;
                currentList = new ArrayList<>();
                if (player != null) {
                    for (var stack : player.getInventory().items) {
                        if (!stack.isEmpty()) currentList.add(stack);
                    }
                }
                slotYOff = popY + 42;
            }

            int slotX = popX + 15;
            int itemIdx = itemSelectorScroll * 9;
            List<Component> hoveredTooltip = null;
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = itemIdx + row * 9 + col;
                    if (idx >= currentList.size()) break;
                    ItemStack stack = currentList.get(idx);
                    int sX = slotX + col * 21;
                    int sY = slotYOff + row * 21;

                    boolean hoverSlot = mouseX >= sX && mouseX <= sX + 20 && mouseY >= sY && mouseY <= sY + 20;
                    graphics.fill(sX, sY, sX + 20, sY + 20, hoverSlot ? 0xFF444444 : slotC);
                    UIHelper.drawOutline(graphics, sX, sY, 20, 20, borderC);
                    graphics.renderItem(stack, sX + 2, sY + 2);

                    if (hoverSlot) {
                        hoveredTooltip = List.of(stack.getHoverName());
                    }
                }
            }
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.raid_editor.label.close_info"), popX + 15, popY + popH - 13, 0xFFAAAAAA, false);

            if (hoveredTooltip != null) {
                graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
            }
        }
    }

    private void drawTab(GuiGraphics graphics, String label, int x, int y, boolean active) {
        int baseC = active ? 0xFFDFD0A0 : 0xFF3D3D3D;
        int textC = active ? 0xFFFFFFFF : 0xFF888888;
        UIHelper.drawShadedButton(graphics, x, y, 75, 15, false, baseC);
        graphics.drawCenteredString(this.font, label, x + 37, y + 4, textC);
    }

    private String getRewardDisplayName(String val) {
        if (val.startsWith("nbt:")) {
            try {
                CompoundTag tag = TagParser.parseTag(val.substring(4).trim());
                ItemStack stack = ItemStack.of(tag);
                if (!stack.isEmpty()) {
                    return stack.getCount() + "x " + stack.getHoverName().getString();
                }
            } catch (Exception ignored) {}
        }
        return val;
    }

    private static int getRewardCount(String val) {
        val = val.trim();
        if (val.startsWith("nbt:")) {
            String nbtStr = val.substring(4).trim();
            try {
                net.minecraft.nbt.CompoundTag compound = net.minecraft.nbt.TagParser.parseTag(nbtStr);
                return compound.contains("Count") ? compound.getByte("Count") : 1;
            } catch (Exception e) {
                return 1;
            }
        } else {
            String[] split = val.split(" ");
            if (split.length > 1) {
                try {
                    return Integer.parseInt(split[1]);
                } catch (Exception ignored) {}
            }
            return 1;
        }
    }

    private static String setRewardCount(String val, int count) {
        val = val.trim();
        if (val.startsWith("nbt:")) {
            String nbtStr = val.substring(4).trim();
            try {
                net.minecraft.nbt.CompoundTag compound = net.minecraft.nbt.TagParser.parseTag(nbtStr);
                compound.putByte("Count", (byte) count);
                return "nbt: " + compound.toString();
            } catch (Exception e) {
                return val;
            }
        } else {
            String[] split = val.split(" ");
            String itemId = split[0];
            return itemId + " " + count;
        }
    }

    private void updateFilteredAddMobs() {
        this.filteredAddMobs.clear();
        for (String mobId : this.availableAddMobs) {
            ddraig.net.custommobs.data.MobData m = ddraig.net.custommobs.data.MobRegistry.loadedMobs.get(mobId);
            String displayName = (m != null) ? m.name : mobId;
            if (this.addMobSearchQuery.isEmpty() || mobId.toLowerCase().contains(this.addMobSearchQuery) || displayName.toLowerCase().contains(this.addMobSearchQuery)) {
                this.filteredAddMobs.add(mobId);
            }
        }
        int maxScroll = Math.max(0, this.filteredAddMobs.size() - 5);
        if (this.addMobScroll > maxScroll) {
            this.addMobScroll = maxScroll;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (showItemSelector && selectAllItems && itemSearchField != null && itemSearchField.visible) {
            if (itemSearchField.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
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
        if (showItemSelector && selectAllItems && itemSearchField != null && itemSearchField.visible) {
            if (itemSearchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        for (var widget : this.children()) {
            if (widget instanceof EditBox editBox && editBox.isFocused()) {
                if (editBox.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
