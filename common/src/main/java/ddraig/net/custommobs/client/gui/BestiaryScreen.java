package ddraig.net.custommobs.client.gui;

import ddraig.net.custommobs.data.MobData;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.client.CustomMobsClient;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Mobs Bestiary Screen
 * Premium obsidian dark-themed UI displaying all custom mobs,
 * with category headers, dynamic scrollbars, safe model auto-scaling,
 * stats displays, and drag-to-rotate entity previews.
 */
public class BestiaryScreen extends Screen {
    private final List<MobData> passives = new ArrayList<>();
    private final List<MobData> neutrals = new ArrayList<>();
    private final List<MobData> hostiles = new ArrayList<>();
    private final List<SidebarItem> sidebarItems = new ArrayList<>();

    private MobData selectedMob;
    private RaidSystem.RaidDefinition selectedRaid;
    private float previewRotation = 0.0f;
    private CustomMobEntity previewEntity;

    private boolean isDragging = false;
    private double lastMouseX = 0.0;
    private float previewZoom = 1.0f;
    private int scrollOffset = 0;
    private int descScrollOffset = 0;

    private static class SidebarItem {
        final boolean isHeader;
        final String category;
        final MobData mob;
        final RaidSystem.RaidDefinition raid;

        SidebarItem(String category) {
            this.isHeader = true;
            this.category = category;
            this.mob = null;
            this.raid = null;
        }

        SidebarItem(MobData mob) {
            this.isHeader = false;
            this.category = null;
            this.mob = mob;
            this.raid = null;
        }

        SidebarItem(RaidSystem.RaidDefinition raid) {
            this.isHeader = false;
            this.category = null;
            this.mob = null;
            this.raid = raid;
        }
    }

    public BestiaryScreen() {
        super(Component.translatable("gui.custom_mobs.bestiary.title"));
    }

    private boolean isMobDiscovered(String mobId) {
        return CustomMobsClient.clientDiscoveries.contains(mobId);
    }

    private boolean isRaidDiscovered(String raidId) {
        return CustomMobsClient.clientDiscoveries.contains(raidId);
    }

    @Override
    protected void init() {
        passives.clear();
        neutrals.clear();
        hostiles.clear();

        for (MobData m : MobRegistry.loadedMobs.values()) {
            if (m.id.startsWith("__proj_preview_")) continue;
            if (m.behaviorMode.equalsIgnoreCase("passive")) passives.add(m);
            else if (m.behaviorMode.equalsIgnoreCase("neutral")) neutrals.add(m);
            else hostiles.add(m);
        }

        selectedMob = null;
        selectedRaid = null;
        for (MobData m : MobRegistry.loadedMobs.values()) {
            if (m.id.startsWith("__proj_preview_")) continue;
            if (isMobDiscovered(m.id)) {
                selectedMob = m;
                break;
            }
        }
        if (selectedMob == null) {
            for (MobData m : MobRegistry.loadedMobs.values()) {
                if (!m.id.startsWith("__proj_preview_")) {
                    selectedMob = m;
                    break;
                }
            }
        }
        if (selectedMob == null) {
            List<RaidSystem.RaidDefinition> loadedRaids = new ArrayList<>(RaidSystem.getRaids());
            if (!loadedRaids.isEmpty()) {
                selectedRaid = loadedRaids.get(0);
            }
        }

        sidebarItems.clear();
        if (!passives.isEmpty()) {
            sidebarItems.add(new SidebarItem("PASSIVE"));
            for (MobData m : passives) sidebarItems.add(new SidebarItem(m));
        }
        if (!neutrals.isEmpty()) {
            sidebarItems.add(new SidebarItem("NEUTRAL"));
            for (MobData m : neutrals) sidebarItems.add(new SidebarItem(m));
        }
        if (!hostiles.isEmpty()) {
            sidebarItems.add(new SidebarItem("HOSTILE"));
            for (MobData m : hostiles) sidebarItems.add(new SidebarItem(m));
        }

        List<RaidSystem.RaidDefinition> loadedRaids = new ArrayList<>(RaidSystem.getRaids());
        if (!loadedRaids.isEmpty()) {
            sidebarItems.add(new SidebarItem("RAIDS"));
            for (RaidSystem.RaidDefinition rd : loadedRaids) {
                sidebarItems.add(new SidebarItem(rd));
            }
        }
    }

    private CustomMobEntity getPreviewEntity(String templateId) {
        if (previewEntity == null && this.minecraft != null && this.minecraft.level != null) {
            previewEntity = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(this.minecraft.level);
        }
        if (previewEntity != null) {
            previewEntity.setTemplateId(templateId);
            previewEntity.setSilhouette(!isMobDiscovered(templateId));
        }
        return previewEntity;
    }

    private int getCategoryColor(String category) {
        if (category == null) return 0xFFDFD0A0; // Gold
        switch (category.toUpperCase()) {
            case "PASSIVE": return 0xFF55FF55;
            case "NEUTRAL": return 0xFFFFAA00;
            case "HOSTILE": return 0xFFFF5555;
            case "RAIDS": return 0xFFDFD0A0;
            default: return 0xFFDFD0A0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // Dynamic 85% Panel Scaling
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int borderC = 0xFFDFD0A0; // Gold
        int bgC = 0xFF2D2D2D;
        int slotC = 0xFF1C1C1C;
        int textActiveC = 0xFFD4AF37;
        int textNormalC = 0xFFCCCCCC;

        // Main beveled obsidian panel
        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        // Sidebar dimensions
        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        // Viewport dimensions
        int viewX = listX + listW + 8;
        int viewY = top + 8;
        int viewW = (int)(panelW * 0.38);
        int viewH = (int)(panelH * 0.45);

        // Stats column dimensions
        int statsX = viewX + viewW + 8;
        int statsW = panelW - (statsX - left) - 8;
        int statsY = top + 8;
        int statsH = panelH - 16;

        // Draw Left sidebar recessed slot
        UIHelper.drawRecessedSlot(graphics, listX, listY, listW, listH, borderC, slotC);

        // Draw Left Sidebar list rows
        int rowH = 12;
        int visibleRows = (listH - 4) / rowH;
        int itemY = listY + 3;
        int maxVisible = Math.min(scrollOffset + visibleRows, sidebarItems.size());

        double scaleVal = this.minecraft.getWindow().getGuiScale();
        int scissorX = (int) (listX * scaleVal);
        int scissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (listY + listH - 1)) * scaleVal);
        int scissorW = (int) (listW * scaleVal);
        int scissorH = (int) ((listH - 2) * scaleVal);

        com.mojang.blaze3d.systems.RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        for (int i = scrollOffset; i < maxVisible; i++) {
            SidebarItem item = sidebarItems.get(i);
            int drawY = itemY + (i - scrollOffset) * rowH;

            if (item.isHeader) {
                int col = getCategoryColor(item.category);
                String labelStr = item.category.equalsIgnoreCase("RAIDS") ? "RAIDS" : Component.translatable("gui.custom_mobs.category." + item.category.toLowerCase()).getString();
                graphics.drawString(this.font, labelStr, listX + 4, drawY + 2, col, false);
            } else if (item.mob != null) {
                MobData m = item.mob;
                boolean isUnlocked = isMobDiscovered(m.id);
                String prefix = isUnlocked ? "§2✔ " : "§8❌ ";
                String displayName = prefix + (isUnlocked ? m.name : "???");
                int col = isUnlocked ? ((m == selectedMob) ? 0xFFFFFFFF : 0xFFCCCCCC) : ((m == selectedMob) ? 0xFF888888 : 0xFF555555);

                if (m == selectedMob) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x40FFFFFF);
                }

                if (mouseX >= listX + 2 && mouseX <= listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + rowH - 1) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x20FFFFFF);
                }

                graphics.drawString(this.font, truncate(displayName, (listW - 14) / 6), listX + 8, drawY + 2, col, false);
            } else if (item.raid != null) {
                RaidSystem.RaidDefinition r = item.raid;
                boolean isUnlocked = isRaidDiscovered(r.raidId);
                String prefix = isUnlocked ? "§6⚔ " : "§8⚔ ";
                String displayName = prefix + (isUnlocked ? r.raidId : "???");
                int col = isUnlocked ? ((r == selectedRaid) ? 0xFFFFFFFF : 0xFFCCCCCC) : ((r == selectedRaid) ? 0xFF888888 : 0xFF555555);

                if (r == selectedRaid) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x40FFFFFF);
                }

                if (mouseX >= listX + 2 && mouseX <= listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + rowH - 1) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x20FFFFFF);
                }

                graphics.drawString(this.font, truncate(displayName, (listW - 14) / 6), listX + 8, drawY + 2, col, false);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        // Draw Left sidebar scrollbar
        if (sidebarItems.size() > visibleRows) {
            int scrollbarX = listX + listW - 6;
            int scrollbarY = listY + 2;
            int scrollbarW = 4;
            int scrollbarH = listH - 4;
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x40000000);

            int thumbH = Math.max(10, scrollbarH * visibleRows / sidebarItems.size());
            int thumbY = scrollbarY + (scrollbarH - thumbH) * scrollOffset / (sidebarItems.size() - visibleRows);
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, borderC);
        }

        // Draw 3D Viewport
        int viewportBorderColor = selectedMob != null ? getCategoryColor(selectedMob.behaviorMode) : borderC;
        UIHelper.drawRecessedSlot(graphics, viewX, viewY, viewW, viewH, viewportBorderColor, 0xFF12131A);

        if (selectedMob != null) {
            boolean isUnlocked = isMobDiscovered(selectedMob.id);

            // Left Drag Rotation logic
            boolean isLeftClickPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (isLeftClickPressed) {
                if (!this.isDragging) {
                    if (mouseX >= viewX && mouseX <= viewX + viewW && mouseY >= viewY && mouseY <= viewY + viewH) {
                        this.isDragging = true;
                        this.lastMouseX = mouseX;
                    }
                } else {
                    double deltaX = mouseX - this.lastMouseX;
                    this.previewRotation -= deltaX * 1.5f;
                    this.lastMouseX = mouseX;
                }
            } else {
                this.isDragging = false;
                this.previewRotation += 0.5f;
            }

            CustomMobEntity dummy = getPreviewEntity(selectedMob.id);
            if (dummy != null) {
                dummy.setSilhouette(!isUnlocked);
                dummy.setYRot(previewRotation);
                dummy.setYHeadRot(previewRotation);
                dummy.setXRot(0.0f);
                dummy.refreshDimensions();

                float baseScale = Math.min((viewH * 0.70f) / dummy.getBbHeight(), (viewW * 0.70f) / dummy.getBbWidth());
                if (baseScale < 0.2F) {
                    baseScale = 0.2F;
                }
                int scaleFactor = (int) (baseScale * previewZoom);
                int centeredY = (viewY + viewH / 2) + (int) ((dummy.getBbHeight() * scaleFactor) / 2);

                int viewScissorX = (int) (viewX * scaleVal);
                int viewScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (viewY + viewH - 1)) * scaleVal);
                int viewScissorW = (int) (viewW * scaleVal);
                int viewScissorH = (int) ((viewH - 2) * scaleVal);

                com.mojang.blaze3d.systems.RenderSystem.enableScissor(viewScissorX, viewScissorY, viewScissorW, viewScissorH);
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics,
                        viewX + viewW / 2,
                        centeredY,
                        scaleFactor,
                        -30.0f,
                        -20.0f,
                        dummy
                );
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();

                // Viewport Zoom overlay buttons
                int btnY = viewY + viewH - 14;
                int btnMinusX = viewX + viewW - 27;
                int btnPlusX = viewX + viewW - 14;

                boolean hoverMinus = mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnMinusX, btnY, 12, 12, hoverMinus, 0xFF3D2C1E);
                graphics.drawString(this.font, "-", btnMinusX + 4, btnY + 2, 0xFFFFFFFF, false);

                boolean hoverPlus = mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnPlusX, btnY, 12, 12, hoverPlus, 0xFF3D2C1E);
                graphics.drawString(this.font, "+", btnPlusX + 3, btnY + 2, 0xFFFFFFFF, false);
            }

            // Draw Name & Category labels
            int nameY = viewY + viewH + 6;
            String nameText = isUnlocked ? selectedMob.name : "???";
            int nameColor = getCategoryColor(selectedMob.behaviorMode);
            graphics.drawString(this.font, nameText, viewX + 4, nameY, nameColor, false);

            int behaviorLabelY = nameY + 11;
            String catName = isUnlocked ? Component.translatable("gui.custom_mobs.category." + selectedMob.behaviorMode.toLowerCase()).getString() : "???";
            String behaviorText = Component.translatable("gui.custom_mobs.bestiary.category_label").getString() + catName;
            graphics.drawString(this.font, behaviorText, viewX + 4, behaviorLabelY, 0xFFAAAAAA, false);

            // Description wrapping below viewport
            int descY = behaviorLabelY + 11;
            int descMaxH = panelH - (descY - top) - 10;

            Component descComp;
            if (isUnlocked) {
                String desc = selectedMob.loreText;
                if (desc == null || desc.isEmpty()) {
                    descComp = Component.translatable("gui.custom_mobs.bestiary.no_lore").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
                } else {
                    descComp = Component.literal(desc).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
                }
            } else {
                descComp = Component.translatable("gui.custom_mobs.bestiary.undiscovered").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
            }

            int descScissorX = (int) (viewX * scaleVal);
            int descScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (descY + descMaxH)) * scaleVal);
            int descScissorW = (int) (viewW * scaleVal);
            int descScissorH = (int) (descMaxH * scaleVal);

            com.mojang.blaze3d.systems.RenderSystem.enableScissor(descScissorX, descScissorY, descScissorW, descScissorH);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(descComp, viewW - 8);
            int visibleDescLines = descMaxH / 10;
            if (lines.size() > visibleDescLines) {
                lines = this.font.split(descComp, viewW - 18);
            }
            int startLine = descScrollOffset;
            int endLine = Math.min(lines.size(), startLine + visibleDescLines);
            int lineY = descY;
            for (int j = startLine; j < endLine; j++) {
                graphics.drawString(this.font, lines.get(j), viewX + 4, lineY, 0xFFCCCCCC, false);
                lineY += 10;
            }
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();

            if (lines.size() > visibleDescLines) {
                int scrollbarX = viewX + viewW - 6;
                int scrollbarY = descY;
                int scrollbarW = 4;
                int scrollbarH = descMaxH;
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x40000000);
                int thumbH = Math.max(10, scrollbarH * visibleDescLines / lines.size());
                int thumbY = scrollbarY + (scrollbarH - thumbH) * descScrollOffset / (lines.size() - visibleDescLines);
                graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, borderC);
            }

            // Render stats column on the right side
            UIHelper.drawRecessedSlot(graphics, statsX, statsY, statsW, statsH, borderC, slotC);

            int labelColor = 0xFFCCCCCC;
            int valueColor = 0xFFD4AF37;
            int drawY = statsY + 6;

            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.stats_header").getString(), statsX + 6, drawY, 0xFFFFFFFF, false);
            drawY += 12;

            String hpText = isUnlocked ? String.valueOf((int)selectedMob.stats.maxHealth) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.health").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, hpText, statsX + 65, drawY, valueColor, false);
            drawY += 11;

            String dmgText = isUnlocked ? String.valueOf((int)selectedMob.stats.attackDamage) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.damage").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, dmgText, statsX + 65, drawY, valueColor, false);
            drawY += 11;

            String armText = isUnlocked ? String.valueOf((int)selectedMob.stats.armor) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.armor").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, armText, statsX + 65, drawY, valueColor, false);
            drawY += 11;

            String spdText = isUnlocked ? String.format("%.2f", selectedMob.stats.movementSpeed) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.speed").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, spdText, statsX + 65, drawY, valueColor, false);
            drawY += 11;

            String reachText = isUnlocked ? String.format("%.1f", selectedMob.stats.attackReach) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.reach").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, reachText, statsX + 65, drawY, valueColor, false);
            drawY += 11;

            String groupText = isUnlocked ? (selectedMob.mobGroup.isEmpty() ? Component.translatable("gui.custom_mobs.bestiary.none").getString() : selectedMob.mobGroup) : "???";
            graphics.drawString(this.font, Component.translatable("gui.custom_mobs.bestiary.group").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, groupText, statsX + 65, drawY, valueColor, false);
            drawY += 11;
        } else if (selectedRaid != null) {
            boolean isUnlocked = isRaidDiscovered(selectedRaid.raidId);
            
            // Render first mob of first wave in 3D viewport if unlocked
            String previewMobId = "";
            if (isUnlocked && !selectedRaid.waves.isEmpty()) {
                RaidSystem.RaidWave wave = selectedRaid.waves.get(0);
                if (!wave.mobCounts.isEmpty()) {
                    previewMobId = wave.mobCounts.keySet().iterator().next();
                }
            }

            // Drag rotation
            boolean isLeftClickPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (isLeftClickPressed) {
                if (!this.isDragging) {
                    if (mouseX >= viewX && mouseX <= viewX + viewW && mouseY >= viewY && mouseY <= viewY + viewH) {
                        this.isDragging = true;
                        this.lastMouseX = mouseX;
                    }
                } else {
                    double deltaX = mouseX - this.lastMouseX;
                    this.previewRotation -= deltaX * 1.5f;
                    this.lastMouseX = mouseX;
                }
            } else {
                this.isDragging = false;
                this.previewRotation += 0.5f;
            }

            if (isUnlocked && !previewMobId.isEmpty()) {
                CustomMobEntity dummy = getPreviewEntity(previewMobId);
                if (dummy != null) {
                    dummy.setSilhouette(false);
                    dummy.setYRot(previewRotation);
                    dummy.setYHeadRot(previewRotation);
                    dummy.setXRot(0.0f);
                    dummy.refreshDimensions();

                    float baseScale = Math.min((viewH * 0.70f) / dummy.getBbHeight(), (viewW * 0.70f) / dummy.getBbWidth());
                    if (baseScale < 0.2F) baseScale = 0.2F;
                    int scaleFactor = (int) (baseScale * previewZoom);
                    int centeredY = (viewY + viewH / 2) + (int) ((dummy.getBbHeight() * scaleFactor) / 2);

                    int viewScissorX = (int) (viewX * scaleVal);
                    int viewScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (viewY + viewH - 1)) * scaleVal);
                    int viewScissorW = (int) (viewW * scaleVal);
                    int viewScissorH = (int) ((viewH - 2) * scaleVal);

                    com.mojang.blaze3d.systems.RenderSystem.enableScissor(viewScissorX, viewScissorY, viewScissorW, viewScissorH);
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics,
                            viewX + viewW / 2,
                            centeredY,
                            scaleFactor,
                            -30.0f,
                            -20.0f,
                            dummy
                    );
                    com.mojang.blaze3d.systems.RenderSystem.disableScissor();
                }
            }

            // Draw Raid Title and lore
            int nameY = viewY + viewH + 6;
            String raidNameText = isUnlocked ? selectedRaid.raidId : "???";
            graphics.drawString(this.font, "§6" + raidNameText, viewX + 4, nameY, isUnlocked ? 0xFFDFD0A0 : 0xFF888888, false);

            int subtitleY = nameY + 11;
            graphics.drawString(this.font, "Active Raid Center Location", viewX + 4, subtitleY, 0xFFAAAAAA, false);

            int descY = subtitleY + 11;
            int descMaxH = panelH - (descY - top) - 10;
            Component descComp;
            if (isUnlocked) {
                String desc = selectedRaid.description;
                descComp = (desc == null || desc.isEmpty()) ?
                        Component.literal("No raid lore configured.").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC) :
                        Component.literal(desc).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
            } else {
                descComp = Component.literal("Undiscovered Raid. Complete this raid to unlock its logs.").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
            }

            int descScissorX = (int) (viewX * scaleVal);
            int descScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (descY + descMaxH)) * scaleVal);
            int descScissorW = (int) (viewW * scaleVal);
            int descScissorH = (int) (descMaxH * scaleVal);

            com.mojang.blaze3d.systems.RenderSystem.enableScissor(descScissorX, descScissorY, descScissorW, descScissorH);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(descComp, viewW - 8);
            int visibleDescLines = descMaxH / 10;
            if (lines.size() > visibleDescLines) {
                lines = this.font.split(descComp, viewW - 18);
            }
            int startLine = descScrollOffset;
            int endLine = Math.min(lines.size(), startLine + visibleDescLines);
            int lineY = descY;
            for (int j = startLine; j < endLine; j++) {
                graphics.drawString(this.font, lines.get(j), viewX + 4, lineY, 0xFFCCCCCC, false);
                lineY += 10;
            }
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();

            if (lines.size() > visibleDescLines) {
                int scrollbarX = viewX + viewW - 6;
                int scrollbarY = descY;
                int scrollbarW = 4;
                int scrollbarH = descMaxH;
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x40000000);
                int thumbH = Math.max(10, scrollbarH * visibleDescLines / lines.size());
                int thumbY = scrollbarY + (scrollbarH - thumbH) * descScrollOffset / (lines.size() - visibleDescLines);
                graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, borderC);
            }

            // Right statistics column: Raid details & Loot
            UIHelper.drawRecessedSlot(graphics, statsX, statsY, statsW, statsH, borderC, slotC);

            int labelColor = 0xFFCCCCCC;
            int valueColor = 0xFFD4AF37;
            int drawY = statsY + 6;

            graphics.drawString(this.font, "RAID DETAILS", statsX + 6, drawY, 0xFFFFFFFF, false);
            drawY += 12;

            graphics.drawString(this.font, "Total Waves:", statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, isUnlocked ? String.valueOf(selectedRaid.waves.size()) : "???", statsX + 70, drawY, valueColor, false);
            drawY += 11;

            // Draw Rewards
            drawY += 6;
            graphics.drawString(this.font, "RAID REWARDS:", statsX + 6, drawY, 0xFFFFFFFF, false);
            drawY += 12;

            if (isUnlocked) {
                int rewardCount = 0;
                for (RaidSystem.RaidReward reward : selectedRaid.rewards) {
                    if (rewardCount >= 6) break;
                    String val = reward.value;
                    String display = val.startsWith("/") ? "[CMD] " + val.substring(1) : val;
                    display += " (" + (int)(reward.chance * 100) + "%)";
                    graphics.drawString(this.font, truncate(display, (statsW - 12) / 6), statsX + 6, drawY, 0xFFCCCCCC, false);
                    drawY += 10;
                    rewardCount++;
                }
            } else {
                graphics.drawString(this.font, "???", statsX + 6, drawY, 0xFF888888, false);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowH = 12;
            int visibleRows = (listH - 4) / rowH;
            int maxOffset = Math.max(0, sidebarItems.size() - visibleRows);
            if (amount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (amount < 0) {
                scrollOffset = Math.min(maxOffset, scrollOffset + 1);
            }
            return true;
        }

        int viewX = listX + listW + 8;
        int viewW = (int)(panelW * 0.38);
        int viewY = top + 8;
        int viewH = (int)(panelH * 0.45);
        int descY = viewY + viewH + 6 + 11 + 11;
        int descMaxH = panelH - (descY - top) - 10;

        if (mouseX >= viewX && mouseX <= viewX + viewW && mouseY >= descY && mouseY <= descY + descMaxH) {
            Component descComp = Component.empty();
            if (selectedMob != null) {
                boolean isUnlocked = isMobDiscovered(selectedMob.id);
                if (isUnlocked) {
                    String desc = selectedMob.loreText;
                    if (desc != null && !desc.isEmpty()) {
                        descComp = Component.literal(desc);
                    }
                } else {
                    descComp = Component.translatable("gui.custom_mobs.bestiary.undiscovered");
                }
            } else if (selectedRaid != null) {
                boolean isUnlocked = isRaidDiscovered(selectedRaid.raidId);
                if (isUnlocked) {
                    String desc = selectedRaid.description;
                    if (desc != null && !desc.isEmpty()) {
                        descComp = Component.literal(desc);
                    }
                } else {
                    descComp = Component.literal("Undiscovered Raid. Complete this raid to unlock its logs.");
                }
            }

            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(descComp, viewW - 18);
            int visibleDescLines = descMaxH / 10;
            int maxScroll = Math.max(0, lines.size() - visibleDescLines);
            if (maxScroll > 0) {
                if (amount > 0) {
                    descScrollOffset = Math.max(0, descScrollOffset - 1);
                } else if (amount < 0) {
                    descScrollOffset = Math.min(maxScroll, descScrollOffset + 1);
                }
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        int viewX = listX + listW + 8;
        int viewY = top + 8;
        int viewW = (int)(panelW * 0.38);
        int viewH = (int)(panelH * 0.45);
        int btnY = viewY + viewH - 14;
        int btnMinusX = viewX + viewW - 27;
        int btnPlusX = viewX + viewW - 14;

        if (selectedMob != null) {
            if (button == 0) {
                if (mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.max(0.2f, previewZoom - 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.min(4.0f, previewZoom + 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        // Left sidebar selection click
        int rowH = 12;
        int visibleRows = (listH - 4) / rowH;
        int itemY = listY + 3;
        int maxVisible = Math.min(scrollOffset + visibleRows, sidebarItems.size());

        if (button == 0) {
            for (int i = scrollOffset; i < maxVisible; i++) {
                SidebarItem item = sidebarItems.get(i);
                int drawY = itemY + (i - scrollOffset) * rowH;
                if (!item.isHeader) {
                    if (mouseX >= listX + 2 && mouseX <= listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + rowH - 1) {
                        if (item.mob != null) {
                            selectedMob = item.mob;
                            selectedRaid = null;
                        } else if (item.raid != null) {
                            selectedRaid = item.raid;
                            selectedMob = null;
                        }
                        previewZoom = 1.0f;
                        descScrollOffset = 0;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.previewEntity != null) {
            this.previewEntity.tickCount++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 2) + "..";
    }
}
