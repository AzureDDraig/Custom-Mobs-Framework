package ddraig.net.custommobs.client.gui;

import com.google.gson.Gson;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.ProjectileData;
import ddraig.net.custommobs.entity.CustomProjectileEntity;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ProjectileCreatorScreen extends Screen {
    private final List<ProjectileData> templatesList = new ArrayList<>();
    private ProjectileData selectedProj;
    private String activeTab = "Basics"; // Basics, Sounds, Effects
    private boolean applyEffect = false;

    private int panelW = 440;
    private int panelH = 260;

    // Fields
    private EditBox nameField;
    private EditBox modelIdField;
    private EditBox textureField;
    private float projScaleSlider = 0.1837f; // maps 0.1 to 5.0 (default 1.0)
    private float hitboxWidthSlider = 0.01898f; // maps 0.1 to 8.0 (default 0.25)
    private float hitboxHeightSlider = 0.01898f; // maps 0.1 to 8.0 (default 0.25)
    
    // Sounds & Particles
    private EditBox landSoundField;
    private EditBox particleTypeField;

    // Effects
    private EditBox effectIdField;
    private EditBox effectDurField;
    private EditBox effectAmpField;
    private EditBox explosionRadiusField;

    private boolean showSuggestions = false;
    private List<String> activeSuggestions = new ArrayList<>();
    private EditBox activeField = null;
    private int suggestionsScrollOffset = 0;
    private int sidebarScrollOffset = 0;
    private final List<ProjectileData> filteredTemplates = new ArrayList<>();
    private EditBox searchField;
    private String searchQuery = "";

    // Tooltip overlay
    private List<Component> hoveredTooltip = null;
    private CustomMobEntity previewEntity;

    public ProjectileCreatorScreen() {
        super(Component.literal("Custom Projectiles Creator"));
    }

    @Override
    protected void init() {
        templatesList.clear();
        templatesList.addAll(MobRegistry.loadedProjectiles.values());

        if (selectedProj == null && !templatesList.isEmpty()) {
            selectedProj = templatesList.get(0);
        } else if (selectedProj == null) {
            selectedProj = new ProjectileData();
            selectedProj.id = "new_projectile";
            selectedProj.name = "New Projectile";
        }
        this.applyEffect = selectedProj != null && !selectedProj.effects.statusEffects.isEmpty();

        this.panelW = (int) (this.width * 0.9);
        this.panelH = (int) (this.height * 0.85);
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;

        int listH = this.panelH - 55;
        int listW = 100;
        int listX = left + 10;
        int listY = top + 25;

        this.searchField = new EditBox(this.font, listX + 6, listY + 5, listW - 12, 10, Component.translatable("gui.custom_mobs.raid_editor.search"));
        this.searchField.setValue(this.searchQuery);
        this.searchField.setBordered(false);
        this.searchField.setResponder(val -> {
            this.searchQuery = val.trim().toLowerCase();
            this.updateFilteredTemplates();
        });
        this.addRenderableWidget(this.searchField);

        this.updateFilteredTemplates();

        int formX = left + 120;
        int formY = top + 45;
        int formW = (int) ((this.panelW - 130) * 0.6);
        int fieldW = formW - 130;

        this.projScaleSlider = (float) ((selectedProj.scale - 0.1f) / 4.9f);
        this.hitboxWidthSlider = (float) ((selectedProj.hitboxWidth - 0.1f) / 7.9f);
        this.hitboxHeightSlider = (float) ((selectedProj.hitboxHeight - 0.1f) / 7.9f);
        rebuildPreviewEntity();

        // Basics Fields
        this.nameField = new EditBox(this.font, formX + 114, formY + 15, fieldW - 8, 10, Component.literal("Name"));
        this.nameField.setValue(selectedProj.name);
        this.nameField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.name")));

        this.modelIdField = new EditBox(this.font, formX + 114, formY + 65, fieldW - 8, 10, Component.literal("Model ID"));
        this.modelIdField.setValue(selectedProj.modelId);
        this.modelIdField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.model_id")));
        this.modelIdField.setResponder(val -> {
            if (selectedProj != null) {
                selectedProj.modelId = val;
                rebuildPreviewEntity();
            }
        });

        this.textureField = new EditBox(this.font, formX + 114, formY + 90, fieldW - 8, 10, Component.literal("Texture Path"));
        this.textureField.setValue(selectedProj.texturePath != null ? selectedProj.texturePath : "");
        this.textureField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.texture")));
        this.textureField.setResponder(val -> {
            if (selectedProj != null) {
                selectedProj.texturePath = val;
                rebuildPreviewEntity();
            }
        });

        // Sounds & Particles Fields
        this.landSoundField = new EditBox(this.font, formX + 114, formY + 15, fieldW - 8, 10, Component.literal("Land/Impact Sound"));
        this.landSoundField.setValue(selectedProj.sounds.land != null ? selectedProj.sounds.land : "");
        this.landSoundField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.land_sound")));

        this.particleTypeField = new EditBox(this.font, formX + 114, formY + 40, fieldW - 8, 10, Component.literal("Particle Trail"));
        this.particleTypeField.setValue(selectedProj.particleType != null ? selectedProj.particleType : "minecraft:small_flame");
        this.particleTypeField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.particle_trail")));

        // Effects Fields
        this.effectIdField = new EditBox(this.font, formX + 114, formY + 15, fieldW - 8, 10, Component.literal("Status Effect (ResourceLocation)"));
        this.effectDurField = new EditBox(this.font, formX + 114, formY + 40, 56, 10, Component.literal("Duration (ticks)"));
        this.effectAmpField = new EditBox(this.font, formX + 114, formY + 65, 56, 10, Component.literal("Amplifier (0-4)"));

        if (selectedProj != null && !selectedProj.effects.statusEffects.isEmpty()) {
            var first = selectedProj.effects.statusEffects.get(0);
            this.effectIdField.setValue(first.effectId);
            this.effectDurField.setValue(String.valueOf(first.durationTicks));
            this.effectAmpField.setValue(String.valueOf(first.amplifier));
        } else {
            this.effectIdField.setValue("minecraft:poison");
            this.effectDurField.setValue("100");
            this.effectAmpField.setValue("0");
        }

        this.effectIdField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.effect_id")));
        this.effectDurField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.effect_dur")));
        this.effectAmpField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.effect_amp")));

        this.explosionRadiusField = new EditBox(this.font, formX + 114, formY + 115, 56, 10, Component.literal("Explosion Radius"));
        this.explosionRadiusField.setValue(String.valueOf(selectedProj.effects.explosionRadius));
        this.explosionRadiusField.setTooltip(Tooltip.create(Component.translatable("gui.custom_mobs.creator.projectile.tooltip.explosion_radius")));

        // Make all EditBoxes borderless
        this.nameField.setBordered(false);
        this.modelIdField.setBordered(false);
        this.textureField.setBordered(false);
        this.landSoundField.setBordered(false);
        this.particleTypeField.setBordered(false);
        this.effectIdField.setBordered(false);
        this.effectDurField.setBordered(false);
        this.effectAmpField.setBordered(false);
        this.explosionRadiusField.setBordered(false);

        // Character limit to 1024
        this.nameField.setMaxLength(1024);
        this.modelIdField.setMaxLength(1024);
        this.textureField.setMaxLength(1024);
        this.landSoundField.setMaxLength(1024);
        this.particleTypeField.setMaxLength(1024);
        this.effectIdField.setMaxLength(1024);
        this.effectDurField.setMaxLength(1024);
        this.effectAmpField.setMaxLength(1024);
        this.explosionRadiusField.setMaxLength(1024);

        // Use addRenderableWidget to register and draw edit box fields correctly
        this.addRenderableWidget(this.nameField);
        this.addRenderableWidget(this.modelIdField);
        this.addRenderableWidget(this.textureField);
        this.addRenderableWidget(this.landSoundField);
        this.addRenderableWidget(this.particleTypeField);
        this.addRenderableWidget(this.effectIdField);
        this.addRenderableWidget(this.effectDurField);
        this.addRenderableWidget(this.effectAmpField);
        this.addRenderableWidget(this.explosionRadiusField);

        hideAllFields();
        showFieldsForTab();
    }

    @Override
    public void tick() {
        // Real-time update in-memory template mapping so the viewport updates dynamically
        saveTextFieldsToActiveProj();
        MobRegistry.loadedProjectiles.put(selectedProj.id, selectedProj);

        nameField.tick();
        modelIdField.tick();
        textureField.tick();
        landSoundField.tick();
        particleTypeField.tick();
        effectIdField.tick();
        effectDurField.tick();
        effectAmpField.tick();
        explosionRadiusField.tick();
        if (this.searchField != null) {
            this.searchField.tick();
        }

        try {
            EditBox focused = null;
            List<String> cache = null;

            if (activeTab.equals("Basics")) {
                if (modelIdField.isFocused()) {
                    focused = modelIdField;
                    if (selectedProj.modelType.equals("vanilla")) {
                        cache = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                    } else {
                        cache = MobRegistry.cachedModels;
                    }
                } else if (textureField.isFocused()) {
                    focused = textureField;
                    cache = getTextureSuggestions();
                }
            } else if (activeTab.equals("Sounds")) {
                if (landSoundField.isFocused()) {
                    focused = landSoundField;
                    cache = MobRegistry.cachedSounds.isEmpty() 
                            ? new ArrayList<>(BuiltInRegistries.SOUND_EVENT.keySet().stream().map(ResourceLocation::toString).toList()) 
                            : MobRegistry.cachedSounds;
                } else if (particleTypeField.isFocused()) {
                    focused = particleTypeField;
                    cache = new ArrayList<>(BuiltInRegistries.PARTICLE_TYPE.keySet().stream().map(ResourceLocation::toString).toList());
                }
            } else if (activeTab.equals("Effects")) {
                if (effectIdField.isFocused() && effectIdField.visible) {
                    focused = effectIdField;
                    cache = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet().stream().map(ResourceLocation::toString).toList());
                }
            }

            if (focused != null && cache != null) {
                String query = focused.getValue().toLowerCase();
                activeSuggestions.clear();
                for (String val : cache) {
                    if (val.toLowerCase().contains(query)) {
                        activeSuggestions.add(val);
                    }
                }
                showSuggestions = true;
                activeField = focused;
            } else {
                showSuggestions = false;
                activeField = null;
            }
        } catch (Exception e) {
            showSuggestions = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int listX = left + 10;
        int listY = top + 25;
        int listW = 100;
        int listH = this.panelH - 55;
        int listContentY = listY + 18;
        int listContentH = listH - 18;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listContentY && mouseY <= listContentY + listContentH) {
            int visibleCount = (listContentH - 10) / 12;
            int maxScroll = Math.max(0, filteredTemplates.size() - visibleCount);
            sidebarScrollOffset = Math.max(0, Math.min(maxScroll, sidebarScrollOffset - (int) amount));
            return true;
        }

        if (showSuggestions && !activeSuggestions.isEmpty()) {
            int maxScroll = Math.max(0, activeSuggestions.size() - 5);
            suggestionsScrollOffset = Math.max(0, Math.min(maxScroll, suggestionsScrollOffset - (int) amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void updateEffectsLayout() {
        if (selectedProj == null) return;
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int formY = top + 42;

        boolean effects = activeTab.equals("Effects");
        
        if (effects) {
            if (applyEffect) {
                effectIdField.visible = true; effectIdField.active = true;
                effectDurField.visible = true; effectDurField.active = true;
                effectAmpField.visible = true; effectAmpField.active = true;

                effectIdField.setY(formY + 35);
                effectDurField.setY(formY + 60);
                effectAmpField.setY(formY + 85);
                explosionRadiusField.setY(formY + 135);
            } else {
                effectIdField.visible = false; effectIdField.active = false;
                effectDurField.visible = false; effectDurField.active = false;
                effectAmpField.visible = false; effectAmpField.active = false;

                explosionRadiusField.setY(formY + 45);
            }
            explosionRadiusField.visible = true; explosionRadiusField.active = true;
        } else {
            hideAllFields();
            showFieldsForTab();
        }
    }

    private void showFieldsForTab() {
        boolean basics = activeTab.equals("Basics");
        nameField.visible = basics; nameField.active = basics;
        modelIdField.visible = basics; modelIdField.active = basics;
        
        boolean showTexture = basics && !selectedProj.modelType.equals("vanilla");
        textureField.visible = showTexture; textureField.active = showTexture;

        boolean sounds = activeTab.equals("Sounds");
        landSoundField.visible = sounds; landSoundField.active = sounds;
        particleTypeField.visible = sounds; particleTypeField.active = sounds;

        boolean effects = activeTab.equals("Effects");
        if (effects) {
            updateEffectsLayout();
        } else {
            effectIdField.visible = false; effectIdField.active = false;
            effectDurField.visible = false; effectDurField.active = false;
            effectAmpField.visible = false; effectAmpField.active = false;
            explosionRadiusField.visible = false; explosionRadiusField.active = false;
        }
    }

    private void hideAllFields() {
        nameField.setVisible(false);
        modelIdField.setVisible(false);
        textureField.setVisible(false);
        landSoundField.setVisible(false);
        particleTypeField.setVisible(false);
        effectIdField.setVisible(false);
        effectDurField.setVisible(false);
        effectAmpField.setVisible(false);
        explosionRadiusField.setVisible(false);
    }

    private void selectTab(String tab) {
        saveTextFieldsToActiveProj();
        this.activeTab = tab;
        hideAllFields();
        showFieldsForTab();
    }

    private void selectProj(ProjectileData data) {
        saveTextFieldsToActiveProj();
        this.selectedProj = data;
        this.init(this.minecraft, this.width, this.height);
    }

    private void saveTextFieldsToActiveProj() {
        if (selectedProj == null) return;
        selectedProj.name = nameField.getValue();
        selectedProj.modelId = modelIdField.getValue();
        selectedProj.texturePath = textureField.getValue();
        selectedProj.sounds.land = landSoundField.getValue();
        selectedProj.particleType = particleTypeField.getValue();
        selectedProj.scale = (float) (0.1 + projScaleSlider * 4.9);
        selectedProj.hitboxWidth = (float) (0.1 + hitboxWidthSlider * 7.9);
        selectedProj.hitboxHeight = (float) (0.1 + hitboxHeightSlider * 7.9);
        if (previewEntity != null) {
            String fakeTemplateId = "__proj_preview_" + selectedProj.id;
            ddraig.net.custommobs.data.MobData fakeMob = MobRegistry.loadedMobs.get(fakeTemplateId);
            if (fakeMob != null) {
                fakeMob.name = selectedProj.name;
                fakeMob.modelType = selectedProj.modelType;
                fakeMob.modelId = selectedProj.modelId;
                fakeMob.texturePath = selectedProj.texturePath;
                fakeMob.scale = selectedProj.scale;
                fakeMob.hitboxWidth = selectedProj.hitboxWidth;
                fakeMob.hitboxHeight = selectedProj.hitboxHeight;
            }
            previewEntity.reapplyTemplate();
        }
        
        try {
            selectedProj.effects.explosionRadius = Float.parseFloat(explosionRadiusField.getValue());
        } catch (Exception ignored) {}

        if (applyEffect) {
            String effectId = effectIdField.getValue().trim();
            if (!effectId.isEmpty()) {
                int duration = 100;
                int amplifier = 0;
                try {
                    duration = Integer.parseInt(effectDurField.getValue());
                } catch (Exception ignored) {}
                try {
                    amplifier = Integer.parseInt(effectAmpField.getValue());
                } catch (Exception ignored) {}

                if (selectedProj.effects.statusEffects.isEmpty()) {
                    ProjectileData.StatusEffectData effect = new ProjectileData.StatusEffectData();
                    effect.effectId = effectId;
                    effect.durationTicks = duration;
                    effect.amplifier = amplifier;
                    selectedProj.effects.statusEffects.add(effect);
                } else {
                    ProjectileData.StatusEffectData first = selectedProj.effects.statusEffects.get(0);
                    first.effectId = effectId;
                    first.durationTicks = duration;
                    first.amplifier = amplifier;
                }
            }
        } else {
            selectedProj.effects.statusEffects.clear();
        }
    }

    private void saveCurrentProj() {
        saveTextFieldsToActiveProj();

        String oldId = selectedProj.id;
        String baseId = selectedProj.name.toLowerCase(java.util.Locale.ROOT).trim().replace(" ", "_").replaceAll("[^a-z0-9_-]", "");
        if (baseId.isEmpty()) baseId = "custom_projectile";
        String newId = baseId;

        if (!newId.equalsIgnoreCase(oldId)) {
            int counter = 1;
            while (MobRegistry.loadedProjectiles.containsKey(newId) || MobRegistry.loadedMobs.containsKey(newId)) {
                newId = baseId + "_" + counter;
                counter++;
            }
            selectedProj.id = newId;
        }

        String json = new Gson().toJson(selectedProj);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(oldId);
        buf.writeUtf(json, 262144);
        NetworkManager.sendToServer(ModPackets.C2S_SAVE_PROJECTILE_TEMPLATE, buf);
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
        String[] tabIds = {"Basics", "Sounds", "Effects"};
        String[] tabLabels = {"Basics", "Sounds", "Effects"};

        int currentX = left + 120; // Aligns tab buttons above the form box
        for (int i = 0; i < tabIds.length; i++) {
            Component labelComp = Component.literal(tabLabels[i]);
            int w = this.font.width(labelComp) + 12;
            TabBounds tb = new TabBounds(tabIds[i], labelComp);
            tb.x = currentX;
            tb.y = top + 26; // Align tab buttons below title
            tb.w = w;
            tb.h = 15;
            bounds.add(tb);
            currentX += w + 2;
        }
        return bounds;
    }

    private void drawEditBoxBackground(GuiGraphics graphics, EditBox field, int borderC, int slotC) {
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

        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;

        int borderC = 0xFFDFD0A0; // Gold
        int bgC = 0xFF2D2D2D;
        int slotC = 0xFF1C1C1C;
        int textActiveC = 0xFFD4AF37;
        int textNormalC = 0xFFCCCCCC;

        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        graphics.drawString(this.font, this.title, left + 12, top + 10, textActiveC, false);

        // Sidebar List (placed on the LEFT side, matching Mob Creator screen layout)
        int listX = left + 10;
        int listY = top + 25;
        int listW = 100;
        int listH = panelH - 55;

        if (this.searchField != null) {
            this.searchField.setX(listX + 6);
            this.searchField.setY(listY + 5);
            this.searchField.setWidth(listW - 12);
        }

        // Draw search box and sidebar recessed slots
        UIHelper.drawRecessedSlot(graphics, listX + 2, listY + 2, listW - 4, 16, borderC, slotC);

        int listContentY = listY + 18;
        int listContentH = listH - 18;
        UIHelper.drawRecessedSlot(graphics, listX, listContentY, listW, listContentH, borderC, slotC);

        int visibleCount = (listContentH - 10) / 12;
        int sidebarY = listContentY + 5;
        for (int i = 0; i < visibleCount; i++) {
            int index = i + sidebarScrollOffset;
            if (index >= filteredTemplates.size()) break;
            ProjectileData p = filteredTemplates.get(index);
            int c = (p == selectedProj) ? textActiveC : textNormalC;
            graphics.drawString(this.font, truncate(p.name, 14), listX + 5, sidebarY, c, false);
            
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= sidebarY && mouseY <= sidebarY + 10) {
                List<Component> t = new ArrayList<>();
                t.add(Component.literal("Projectile ID: " + p.id));
                this.hoveredTooltip = t;
            }
            sidebarY += 12;
        }

        int addY = listY + listH + 4;
        boolean hoverAdd = mouseX >= listX && mouseX <= listX + 48 && mouseY >= addY && mouseY <= addY + 14;
        UIHelper.drawShadedButton(graphics, listX, addY, 48, 14, hoverAdd, 0xFF005500);
        graphics.drawString(this.font, "+ New", listX + 8, addY + 3, 0xFFFFFFFF, false);

        boolean hoverDel = mouseX >= listX + 52 && mouseX <= listX + listW && mouseY >= addY && mouseY <= addY + 14;
        UIHelper.drawShadedButton(graphics, listX + 52, addY, 48, 14, hoverDel, 0xFF550000);
        graphics.drawString(this.font, "Delete", listX + 60, addY + 3, 0xFFFFFFFF, false);

        if (selectedProj != null) {
            List<TabBounds> tabBoundsList = calculateTabBounds(left, top);
            for (TabBounds tb : tabBoundsList) {
                boolean active = tb.id.equals(activeTab);
                boolean hoverTab = mouseX >= tb.x && mouseX <= tb.x + tb.w && mouseY >= tb.y && mouseY <= tb.y + tb.h;
                int color = active ? slotC : UIHelper.adjustBrightness(bgC, -15);
                UIHelper.drawShadedButton(graphics, tb.x, tb.y, tb.w, tb.h, hoverTab, color);
                graphics.drawString(this.font, tb.label, tb.x + 6, tb.y + 4, active ? textActiveC : textNormalC, false);
            }

            // Options Form Box
            int formX = left + 120;
            int formY = top + 42;
            int formW = (int) ((panelW - 130) * 0.6);
            int formH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, formX, formY, formW, formH, borderC, slotC);

            // Viewport Box (placed on the RIGHT side of the form)
            int viewportX = formX + formW + 10;
            int viewportY = top + 42;
            int viewportW = left + panelW - 10 - viewportX;
            int viewportH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, viewportX, viewportY, viewportW, viewportH, borderC, slotC);
            draw3DPreview(graphics, viewportX + viewportW / 2, viewportY + viewportH / 2 + 30);

            boolean hoverSave = mouseX >= viewportX + (viewportW - 110) / 2 && mouseX <= viewportX + (viewportW - 110) / 2 + 110 && mouseY >= viewportY + viewportH - 25 && mouseY <= viewportY + viewportH - 5;
            UIHelper.drawShadedButton(graphics, viewportX + (viewportW - 110) / 2, viewportY + viewportH - 25, 110, 20, hoverSave, 0xFF00AA00);
            graphics.drawString(this.font, "Save & Export", viewportX + (viewportW - 110) / 2 + 15, viewportY + viewportH - 19, 0xFFFFFFFF, false);
        }

        // Render recessed dark backgrounds for active EditBoxes
        drawEditBoxBackground(graphics, nameField, borderC, slotC);
        drawEditBoxBackground(graphics, modelIdField, borderC, slotC);
        drawEditBoxBackground(graphics, textureField, borderC, slotC);
        drawEditBoxBackground(graphics, landSoundField, borderC, slotC);
        drawEditBoxBackground(graphics, particleTypeField, borderC, slotC);
        drawEditBoxBackground(graphics, effectIdField, borderC, slotC);
        drawEditBoxBackground(graphics, effectDurField, borderC, slotC);
        drawEditBoxBackground(graphics, effectAmpField, borderC, slotC);
        drawEditBoxBackground(graphics, explosionRadiusField, borderC, slotC);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (selectedProj != null) {
            int formX = left + 120;
            int formY = top + 42;
            renderTabFormText(graphics, formX, formY, mouseX, mouseY);
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
            graphics.pose().translate(0, 0, 350.0F);

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

        if (hoveredTooltip != null) {
            graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private void renderTabFormText(GuiGraphics graphics, int formX, int formY, int mouseX, int mouseY) {
        int labelC = 0xFFFFFF55;
        int textC = 0xFFFFFFFF;

        if (activeTab.equals("Basics")) {
            graphics.drawString(this.font, "Name:", formX + 10, formY + 16, labelC);
            
            graphics.drawString(this.font, "Model Type:", formX + 10, formY + 41, labelC);
            boolean hoverType = mouseX >= formX + 114 && mouseX <= formX + 184 && mouseY >= formY + 39 && mouseY <= formY + 51;
            UIHelper.drawShadedButton(graphics, formX + 114, formY + 39, 70, 12, hoverType, 0xFF3C3C3C);
            graphics.drawString(this.font, selectedProj.modelType.toUpperCase(), formX + 120, formY + 41, 0xFFFFFFFF, false);

            graphics.drawString(this.font, "Model ID:", formX + 10, formY + 66, labelC);
            
            if (!selectedProj.modelType.equals("vanilla")) {
                graphics.drawString(this.font, "Texture Path:", formX + 10, formY + 91, labelC);
            }

            int currentY = selectedProj.modelType.equals("vanilla") ? formY + 90 : formY + 115;
            int sliderX = formX + 114;

            // Model Scale
            graphics.drawString(this.font, "Model Scale:", formX + 10, currentY + 1, labelC);
            graphics.fill(sliderX, currentY + 4, sliderX + 100, currentY + 6, 0xFF1C1C1C);
            graphics.fill(sliderX + (int) (projScaleSlider * 92), currentY, sliderX + (int) (projScaleSlider * 92) + 8, currentY + 10, 0xFFDFD0A0);
            graphics.drawString(this.font, String.format("%.2fx", 0.1 + projScaleSlider * 4.9), sliderX + 110, currentY + 1, 0xFFFFFFFF);
            currentY += 15;

            // Hitbox Width
            graphics.drawString(this.font, "Hitbox Width:", formX + 10, currentY + 1, labelC);
            graphics.fill(sliderX, currentY + 4, sliderX + 100, currentY + 6, 0xFF1C1C1C);
            graphics.fill(sliderX + (int) (hitboxWidthSlider * 92), currentY, sliderX + (int) (hitboxWidthSlider * 92) + 8, currentY + 10, 0xFFDFD0A0);
            graphics.drawString(this.font, String.format("%.2f", 0.1 + hitboxWidthSlider * 7.9), sliderX + 110, currentY + 1, 0xFFFFFFFF);
            currentY += 15;

            // Hitbox Height
            graphics.drawString(this.font, "Hitbox Height:", formX + 10, currentY + 1, labelC);
            graphics.fill(sliderX, currentY + 4, sliderX + 100, currentY + 6, 0xFF1C1C1C);
            graphics.fill(sliderX + (int) (hitboxHeightSlider * 92), currentY, sliderX + (int) (hitboxHeightSlider * 92) + 8, currentY + 10, 0xFFDFD0A0);
            graphics.drawString(this.font, String.format("%.2f", 0.1 + hitboxHeightSlider * 7.9), sliderX + 110, currentY + 1, 0xFFFFFFFF);
            currentY += 15;

            // Show Hitbox Bounds Checkbox
            graphics.drawString(this.font, "Show Hitbox Bounds:", formX + 10, currentY + 1, labelC);
            graphics.fill(formX + 130, currentY, formX + 140, currentY + 10, ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug ? 0xFF00FF00 : 0xFFFF0000);
            currentY += 15;

            // Affected by gravity Checkbox
            graphics.drawString(this.font, "Affected by gravity: ", formX + 10, currentY + 1, textC);
            boolean hoverGravity = mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= currentY && mouseY <= currentY + 10;
            graphics.fill(formX + 130, currentY, formX + 140, currentY + 10, selectedProj.gravity ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverGravity) {
                this.hoveredTooltip = List.of(Component.literal("If enabled, the projectile will fall due to gravity over time."));
            }
            currentY += 15;

            // Sticky projectile Checkbox
            graphics.drawString(this.font, "Sticky projectile: ", formX + 10, currentY + 1, textC);
            boolean hoverSticky = mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= currentY && mouseY <= currentY + 10;
            graphics.fill(formX + 130, currentY, formX + 140, currentY + 10, selectedProj.sticky ? 0xFF00FF00 : 0xFFFF0000);
            if (hoverSticky) {
                this.hoveredTooltip = List.of(Component.literal("If enabled, the projectile will stick physically onto the players/entities it hits."));
            }
            currentY += 20;

            graphics.drawString(this.font, "Projectile ID: " + selectedProj.id, formX + 10, currentY, 0xFFAAAAAA);

        } else if (activeTab.equals("Sounds")) {
            graphics.drawString(this.font, "Land/Impact Sound:", formX + 10, formY + 16, labelC);
            graphics.drawString(this.font, "Particle Trail:", formX + 10, formY + 41, labelC);

        } else if (activeTab.equals("Effects")) {
            graphics.drawString(this.font, "Apply Effect on hit:", formX + 10, formY + 16, textC);
            graphics.fill(formX + 130, formY + 15, formX + 140, formY + 25, applyEffect ? 0xFF00FF00 : 0xFFFF0000);

            if (applyEffect) {
                graphics.drawString(this.font, "Status Effect ID:", formX + 10, formY + 36, labelC);
                graphics.drawString(this.font, "Duration (ticks):", formX + 10, formY + 61, labelC);
                graphics.drawString(this.font, "Amplifier (0-4):", formX + 10, formY + 86, labelC);

                boolean hoverAdd = mouseX >= formX + 10 && mouseX <= formX + 120 && mouseY >= formY + 110 && mouseY <= formY + 122;
                UIHelper.drawShadedButton(graphics, formX + 10, formY + 110, 110, 12, hoverAdd, 0xFF229922);
                graphics.drawString(this.font, " + Inject Effect", formX + 15, formY + 113, textC, false);

                int base = formY + 125;
                graphics.drawString(this.font, "Explosion Options:", formX + 10, base, labelC);
                
                graphics.drawString(this.font, "Explode on hit:", formX + 10, base + 27, textC);
                graphics.fill(formX + 130, base + 26, formX + 140, base + 36, selectedProj.effects.explosion ? 0xFF00FF00 : 0xFFFF0000);

                graphics.drawString(this.font, "Destroy blocks:", formX + 10, base + 47, textC);
                graphics.fill(formX + 130, base + 46, formX + 140, base + 56, selectedProj.effects.destroyBlocks ? 0xFF00FF00 : 0xFFFF0000);

                graphics.drawString(this.font, "Active effects on hit:", formX + 10, base + 67, labelC);
                int effY = base + 80;
                for (ProjectileData.StatusEffectData effect : selectedProj.effects.statusEffects) {
                    graphics.drawString(this.font, "- " + effect.effectId + " (lvl " + (effect.amplifier + 1) + ", " + (effect.durationTicks/20) + "s)", formX + 15, effY, textC);
                    effY += 12;
                }
            } else {
                int base = formY + 35;
                graphics.drawString(this.font, "Explosion Options:", formX + 10, base, labelC);
                
                graphics.drawString(this.font, "Explode on hit:", formX + 10, base + 27, textC);
                graphics.fill(formX + 130, base + 26, formX + 140, base + 36, selectedProj.effects.explosion ? 0xFF00FF00 : 0xFFFF0000);

                graphics.drawString(this.font, "Destroy blocks:", formX + 10, base + 47, textC);
                graphics.fill(formX + 130, base + 46, formX + 140, base + 56, selectedProj.effects.destroyBlocks ? 0xFF00FF00 : 0xFFFF0000);
            }
        }
    }

    private void rebuildPreviewEntity() {
        if (selectedProj == null) return;
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            try {
                previewEntity = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(localPlayer.level());
                if (previewEntity != null) {
                    previewEntity.isPreview = true;
                    String fakeTemplateId = "__proj_preview_" + selectedProj.id;
                    ddraig.net.custommobs.client.renderer.JavaModelLoader.clearCacheFor(fakeTemplateId, selectedProj.modelId);
                    ddraig.net.custommobs.data.MobData fakeMob = new ddraig.net.custommobs.data.MobData();
                    fakeMob.id = fakeTemplateId;
                    fakeMob.name = selectedProj.name;
                    fakeMob.modelType = selectedProj.modelType;
                    fakeMob.modelId = selectedProj.modelId;
                    fakeMob.texturePath = selectedProj.texturePath;
                    fakeMob.scale = selectedProj.scale;
                    fakeMob.hitboxWidth = selectedProj.hitboxWidth;
                    fakeMob.hitboxHeight = selectedProj.hitboxHeight;
                    fakeMob.billboardName = false;
                    MobRegistry.loadedMobs.put(fakeTemplateId, fakeMob);

                    previewEntity.setTemplateId(fakeTemplateId);
                    previewEntity.reapplyTemplate();
                }
            } catch (Exception ignored) {}
        }
    }

    private void draw3DPreview(GuiGraphics graphics, int x, int y) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && previewEntity != null) {
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics, x, y, 25, (float) (x - width/2), (float) (y - height/2), previewEntity
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
        int listContentY = listY + 18;
        int listContentH = listH - 18;

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
                    activeField.setValue(activeSuggestions.get(idx));
                    showSuggestions = false;
                    activeField = null;
                }
                return true;
            }
        }

        int viewportX = formX + formW + 10;
        int viewportW = left + panelW - 10 - viewportX;
        int viewportY = top + 42;
        int viewportH = panelH - 80;
        int saveX = viewportX + (viewportW - 110) / 2;
        int saveY = viewportY + viewportH - 25;
        if (mouseX >= saveX && mouseX <= saveX + 110 && mouseY >= saveY && mouseY <= saveY + 20) {
            saveCurrentProj();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (button == 0 && this.searchField != null && this.searchField.visible) {
            if (this.searchField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listContentY && mouseY <= listContentY + listContentH) {
            int clickedIdx = (int) ((mouseY - listContentY - 5) / 12) + sidebarScrollOffset;
            int visibleCount = (listContentH - 10) / 12;
            int screenIdx = (int) ((mouseY - listContentY - 5) / 12);
            if (screenIdx >= 0 && screenIdx < visibleCount && clickedIdx >= 0 && clickedIdx < filteredTemplates.size()) {
                selectProj(filteredTemplates.get(clickedIdx));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        int addYSidebar = listY + listH + 4;
        if (mouseY >= addYSidebar && mouseY <= addYSidebar + 14) {
            if (mouseX >= listX && mouseX <= listX + 48) {
                saveTextFieldsToActiveProj();
                selectedProj = new ProjectileData();
                selectedProj.id = "new_proj_" + System.currentTimeMillis();
                selectedProj.name = "New Custom Projectile";
                this.init(this.minecraft, this.width, this.height);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= listX + 52 && mouseX <= listX + listW) {
                if (selectedProj != null) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUtf(selectedProj.id);
                    NetworkManager.sendToServer(ModPackets.C2S_DELETE_PROJECTILE_TEMPLATE, buf);

                    MobRegistry.loadedProjectiles.remove(selectedProj.id);
                    templatesList.remove(selectedProj);
                    selectedProj = templatesList.isEmpty() ? null : templatesList.get(0);
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

        if (activeTab.equals("Basics")) {
            // Model Type Cycle
            if (mouseX >= formX + 114 && mouseX <= formX + 184 && mouseY >= formY + 39 && mouseY <= formY + 51) {
                if (selectedProj.modelType.equals("vanilla")) {
                    selectedProj.modelType = "geckolib";
                } else if (selectedProj.modelType.equals("geckolib")) {
                    selectedProj.modelType = "java";
                } else {
                    selectedProj.modelType = "vanilla";
                }
                this.init(this.minecraft, this.width, this.height);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int currentY = selectedProj.modelType.equals("vanilla") ? formY + 90 : formY + 115;
            int sliderX = formX + 114;

            // Model Scale Slider
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= currentY && mouseY <= currentY + 10) {
                projScaleSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveProj();
                return true;
            }
            currentY += 15;

            // Hitbox Width Slider
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= currentY && mouseY <= currentY + 10) {
                hitboxWidthSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveProj();
                return true;
            }
            currentY += 15;

            // Hitbox Height Slider
            if (mouseX >= sliderX && mouseX <= sliderX + 100 && mouseY >= currentY && mouseY <= currentY + 10) {
                hitboxHeightSlider = (float) ((mouseX - sliderX) / 100.0f);
                saveTextFieldsToActiveProj();
                return true;
            }
            currentY += 15;

            // Show Hitbox Bounds Checkbox
            if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= currentY && mouseY <= currentY + 10) {
                ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug = !ddraig.net.custommobs.client.renderer.CustomMobRenderer.showHitboxDebug;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            currentY += 15;

            // Affected by Gravity Checkbox
            if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= currentY && mouseY <= currentY + 10) {
                selectedProj.gravity = !selectedProj.gravity;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            currentY += 15;

            // Sticky Projectile Checkbox
            if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= currentY && mouseY <= currentY + 10) {
                selectedProj.sticky = !selectedProj.sticky;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        } else if (activeTab.equals("Effects")) {
            // Toggle applyEffect checkbox
            if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= formY + 15 && mouseY <= formY + 25) {
                applyEffect = !applyEffect;
                if (!applyEffect) {
                    selectedProj.effects.statusEffects.clear();
                }
                updateEffectsLayout();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            if (applyEffect) {
                int base = formY + 125;
                if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= base + 26 && mouseY <= base + 36) {
                    selectedProj.effects.explosion = !selectedProj.effects.explosion;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= base + 46 && mouseY <= base + 56) {
                    selectedProj.effects.destroyBlocks = !selectedProj.effects.destroyBlocks;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                // Add Hit Status Effect
                if (mouseX >= formX + 10 && mouseX <= formX + 120 && mouseY >= formY + 110 && mouseY <= formY + 122) {
                    try {
                        ProjectileData.StatusEffectData effect = new ProjectileData.StatusEffectData();
                        effect.effectId = effectIdField.getValue();
                        effect.durationTicks = Integer.parseInt(effectDurField.getValue());
                        effect.amplifier = Integer.parseInt(effectAmpField.getValue());
                        selectedProj.effects.statusEffects.add(effect);
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    } catch (Exception ignored) {}
                    return true;
                }
            } else {
                int base = formY + 35;
                if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= base + 26 && mouseY <= base + 36) {
                    selectedProj.effects.explosion = !selectedProj.effects.explosion;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (mouseX >= formX + 130 && mouseX <= formX + 140 && mouseY >= base + 46 && mouseY <= base + 56) {
                    selectedProj.effects.destroyBlocks = !selectedProj.effects.destroyBlocks;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<String> getTextureSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            String modelId = selectedProj.modelId;
            if (modelId == null || modelId.isEmpty()) {
                modelId = selectedProj.id;
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

    private void findFilesRecursivelyBySuffix(java.io.File dir, String suffix, List<java.io.File> results) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    findFilesRecursivelyBySuffix(f, suffix, results);
                } else if (f.getName().toLowerCase().endsWith(suffix)) {
                    results.add(f);
                }
            }
        }
    }

    private String truncateByWidth(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, width - 8) + "..";
    }

    private void updateFilteredTemplates() {
        this.filteredTemplates.clear();
        for (ProjectileData p : this.templatesList) {
            if (this.searchQuery.isEmpty() || p.id.toLowerCase().contains(this.searchQuery) || p.name.toLowerCase().contains(this.searchQuery)) {
                this.filteredTemplates.add(p);
            }
        }
        int listH = this.panelH - 55;
        int listContentH = listH - 18;
        int visibleCount = (listContentH - 10) / 12;
        int maxScroll = Math.max(0, this.filteredTemplates.size() - visibleCount);
        if (this.sidebarScrollOffset > maxScroll) {
            this.sidebarScrollOffset = maxScroll;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 2) + "..";
    }
}
