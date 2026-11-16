package org.realscale.neoforge.lotm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.realscale.neoforge.lotm.LotmBalance;
import org.realscale.neoforge.lotm.LotmPath;
import org.realscale.neoforge.lotm.LotmStatusMenu;

/** Compact occult status console with Overview, Skills, Materials and Settings tabs. */
public final class LotmStatusScreen extends AbstractContainerScreen<LotmStatusMenu> {
    private int selectedTab;

    public LotmStatusScreen(LotmStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 194;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.overview"),
                button -> selectedTab = 0).bounds(leftPos + 8, topPos + 24, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.skills"),
                button -> selectedTab = 1).bounds(leftPos + 68, topPos + 24, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.materials"),
                button -> selectedTab = 2).bounds(leftPos + 128, topPos + 24, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.settings"),
                button -> selectedTab = 3).bounds(leftPos + 188, topPos + 24, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.toggle_hud"),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_HUD))
                .bounds(leftPos + 10, topPos + 166, 112, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.toggle_appearance"),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_APPEARANCE))
                .bounds(leftPos + 132, topPos + 166, 114, 18).build());
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF11121B);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + 21, 0xFF29243A);
        graphics.fill(x + 6, y + 46, x + imageWidth - 6, y + 160, 0xFF1D1C2B);
        graphics.fill(x + 10, y + 166, x + 122, y + 184, 0xFF34304A);
        graphics.fill(x + 132, y + 166, x + 246, y + 184, 0xFF34304A);
        if (selectedTab == 0) {
            bar(graphics, x + 14, y + 93, 224, 7, menu.spirit(), 100, 0xFF69B9D0);
            bar(graphics, x + 14, y + 127, 224, 7, menu.digestion(), 100, 0xFFB28AD8);
        } else if (selectedTab == 1) {
            bar(graphics, x + 14, y + 91, 224, 5, menu.skillMastery(1), menu.skillMasteryCap(), 0xFFB28AD8);
            bar(graphics, x + 14, y + 145, 224, 5, menu.skillMastery(2), menu.skillMasteryCap(), 0xFF79B7C7);
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, int height,
            int value, int maximum, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF0C0D14);
        int safeMaximum = Math.max(1, maximum);
        int filled = Math.max(0, Math.min(width, width * Math.max(0, value) / safeMaximum));
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, color);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.title"), 9, 7, 0xFFF0E8FF, false);
        if (menu.pathIndex() == 0 || menu.pathIndex() > LotmPath.values().length) {
            graphics.drawString(font, Component.translatable("message.realscale.lotm.no_path"), 14, 59, 0xFFE0D6E8, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.start_hint"), 14, 78, 0xFFB8ADC9, false);
            return;
        }
        LotmPath path = LotmPath.values()[menu.pathIndex() - 1];
        Component pathName = Component.translatable("lotm.realscale.path." + path.id());
        if (selectedTab == 0) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.path", pathName), 14, 52, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.sequence", menu.sequence()), 14, 69, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.spirit", menu.spirit(), 100), 14, 83, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.digestion", menu.digestion(), 100), 14, 117, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.instability", menu.instability()), 14, 143, 0xFFE0B8C8, false);
            Component accessory = menu.hasSpiritFocus()
                    ? Component.translatable("item.realscale.lotm_spirit_focus")
                    : menu.hasDivinationPendulum()
                            ? Component.translatable("item.realscale.lotm_divination_pendulum")
                            : Component.translatable("message.realscale.lotm.status.no_accessory");
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.accessory", accessory),
                    14, 153, 0xFFB8ADC9, false);
        } else if (selectedTab == 1) {
            int sequence = menu.sequence();
            for (int slot = 1; slot <= 2; slot++) {
                int y = slot == 1 ? 51 : 105;
                int mastery = menu.skillMastery(slot);
                int base = LotmBalance.skillCost(path.id(), slot, sequence,
                        menu.hasDivinationPendulum(), menu.instability());
                int cost = LotmBalance.skillCostWithMastery(base, mastery, menu.skillCostPercent());
                String skillKey = "gui.realscale.lotm_panel.skill." + path.id() + "." + slot;
                graphics.drawString(font, Component.translatable(skillKey + ".name"), 14, y, 0xFFEDE4F6, false);
                graphics.drawString(font, Component.translatable(skillKey + ".description"), 14, y + 12, 0xFFB8ADC9, false);
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.mastery", mastery,
                        sequence == 9 ? 1 : menu.skillMasteryCap()), 14, y + 25, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.skill_cost", cost,
                        LotmBalance.skillCooldownTicks(slot, sequence)), 14, y + 36, 0xFFB8ADC9, false);
            }
        } else if (selectedTab == 2) {
            int nextSequence = menu.sequence() - 1;
            Component materials = nextSequence >= 8
                    ? Component.translatable("lotm.realscale.materials." + path.id() + "." + nextSequence)
                    : Component.translatable("message.realscale.lotm.max_rank");
            Component manualOne = Component.translatable(
                    "lotm.realscale.skill_manuals." + path.id() + "." + menu.sequence() + ".1");
            Component manualTwo = Component.translatable(
                    "lotm.realscale.skill_manuals." + path.id() + "." + menu.sequence() + ".2");
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.materials_header"), 14, 51, 0xFFEDE4F6, false);
            int lineY = 67;
            for (Component section : new Component[] {materials, manualOne, manualTwo}) {
                for (var line : font.split(section, 224)) {
                    if (lineY > 151) break;
                    graphics.drawString(font, line, 14, lineY, 0xFFD7CDE4, false);
                    lineY += 13;
                }
                lineY += 3;
                if (lineY > 151) break;
            }
        } else {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.settings_header"), 14, 51, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.skills", onOff(menu.skillsEnabled())), 14, 68, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.manuals", onOff(menu.skillBooksEnabled())), 14, 83, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.appearance", onOff(menu.appearanceEffectsEnabled())), 14, 98, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.creatures", onOff(menu.creaturesEnabled())), 14, 113, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.skill_cost", menu.skillCostPercent()), 14, 128, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.drop_rate", menu.materialDropPercent()), 14, 143, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.hud_refresh", menu.hudRefreshTicks()), 135, 68, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.aura_interval", menu.appearanceIntervalTicks()), 135, 83, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.manual_alchemy", menu.manualAlchemyTicks()), 135, 98, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.mastery_cap", menu.skillMasteryCap()), 135, 113, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.hud", onOff(menu.hudEnabled())), 135, 128, 0xFFB8ADC9, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.aura", onOff(menu.appearanceEnabled())), 135, 143, 0xFFB8ADC9, false);
        }
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
}
