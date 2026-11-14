package org.realscale.neoforge.lotm.client;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Ingredient;
import org.realscale.neoforge.lotm.LotmAlchemyContent;
import org.realscale.neoforge.lotm.LotmAlchemyRecipe;
import org.realscale.neoforge.lotm.LotmContent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.realscale.neoforge.lotm.LotmBalance;
import org.realscale.neoforge.lotm.LotmCreatureKind;
import org.realscale.neoforge.lotm.LotmPath;
import org.realscale.neoforge.lotm.LotmDropBalance;
import org.realscale.neoforge.lotm.LotmStatusMenu;

/** Compact occult status console with Overview, Skills, Materials and Settings tabs. */
public final class LotmStatusScreen extends AbstractContainerScreen<LotmStatusMenu> {
    private int selectedTab;
    private int creatureIndex;
    private Button hudToggle;
    private Button auraToggle;
    private Button openBestiary;
    private Button previousCreature;
    private Button nextCreature;
    private Button backToMaterials;
    private Button previousRecipe;
    private Button nextRecipe;
    private List<RecipeHolder<LotmAlchemyRecipe>> alchemyRecipes = List.of();
    private int recipeIndex;

    public LotmStatusScreen(LotmStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 194;
    }

    @Override protected void init() {
        super.init();
        refreshRecipes();
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.overview"),
                button -> selectedTab = 0).bounds(leftPos + 8, topPos + 24, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.skills"),
                button -> selectedTab = 1).bounds(leftPos + 49, topPos + 24, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.materials"),
                button -> selectedTab = 2).bounds(leftPos + 90, topPos + 24, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.alchemy"),
                button -> selectedTab = 3).bounds(leftPos + 131, topPos + 24, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.journal"),
                button -> selectedTab = 4).bounds(leftPos + 172, topPos + 24, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.settings"),
                button -> selectedTab = 5).bounds(leftPos + 213, topPos + 24, 35, 18).build());
        hudToggle = addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.toggle_hud"),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_HUD))
                .bounds(leftPos + 10, topPos + 166, 112, 18).build());
        auraToggle = addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.toggle_appearance"),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_APPEARANCE))
                .bounds(leftPos + 132, topPos + 166, 114, 18).build());

        openBestiary = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.open"),
                button -> { selectedTab = 6; creatureIndex = 0; })
                .bounds(leftPos + 70, topPos + 166, 116, 18).build());
        previousCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.previous"),
                button -> creatureIndex = Math.floorMod(creatureIndex - 1, LotmCreatureKind.values().length))
                .bounds(leftPos + 10, topPos + 166, 65, 18).build());
        nextCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.next"),
                button -> creatureIndex = (creatureIndex + 1) % LotmCreatureKind.values().length)
                .bounds(leftPos + 80, topPos + 166, 65, 18).build());
        backToMaterials = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.back"),
                button -> selectedTab = 2)
                .bounds(leftPos + 150, topPos + 166, 96, 18).build());
        previousRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.previous"),
                button -> recipeIndex = Math.floorMod(recipeIndex - 1, alchemyRecipes.size()))
                .bounds(leftPos + 12, topPos + 166, 88, 18).build());
        nextRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.next"),
                button -> recipeIndex = (recipeIndex + 1) % alchemyRecipes.size())
                .bounds(leftPos + 156, topPos + 166, 88, 18).build());
    }

    private void refreshRecipes() {
        if (minecraft == null || minecraft.level == null) {
            alchemyRecipes = List.of();
            recipeIndex = 0;
            return;
        }
        List<RecipeHolder<LotmAlchemyRecipe>> synced = new ArrayList<>(
                minecraft.level.getRecipeManager().getAllRecipesFor(
                        LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get()));
        synced.sort(Comparator.comparing(holder -> holder.id().toString()));
        alchemyRecipes = List.copyOf(synced);
        recipeIndex = alchemyRecipes.isEmpty() ? 0 : recipeIndex % alchemyRecipes.size();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Material navigation and player preferences only appear in their own pages.
        if (hudToggle != null) hudToggle.visible = selectedTab == 5;
        if (auraToggle != null) auraToggle.visible = selectedTab == 5;
        if (openBestiary != null) openBestiary.visible = selectedTab == 2;
        if (previousCreature != null) previousCreature.visible = selectedTab == 6;
        if (nextCreature != null) nextCreature.visible = selectedTab == 6;
        if (backToMaterials != null) backToMaterials.visible = selectedTab == 6;
        if (previousRecipe != null) previousRecipe.visible = selectedTab == 3 && alchemyRecipes.size() > 1;
        if (nextRecipe != null) nextRecipe.visible = selectedTab == 3 && alchemyRecipes.size() > 1;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
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
        } else if (selectedTab == 3 && !alchemyRecipes.isEmpty()) {
            for (int index = 0; index < 4; index++) {
                int itemX = x + 22 + (index % 2) * 23;
                int itemY = y + 86 + (index / 2) * 23;
                graphics.fill(itemX - 2, itemY - 2, itemX + 18, itemY + 18, 0xFF403650);
            }
            graphics.fill(x + 118, y + 98, x + 140, y + 120, 0xFF403650);
            graphics.fill(x + 193, y + 98, x + 215, y + 120, 0xFF403650);
        } else if (selectedTab == 4) {
            final int[] caps = {12, 8, 9, 6};
            for (int index = 0; index < caps.length; index++) {
                bar(graphics, x + 14, y + 93 + index * 19, 224, 4,
                        menu.dailyActing(index), caps[index], 0xFF79B7C7);
            }
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
        boolean hasPath = menu.pathIndex() > 0 && menu.pathIndex() <= LotmPath.values().length;
        if (!hasPath && selectedTab != 3 && selectedTab != 4 && selectedTab != 5 && selectedTab != 6) {
            graphics.drawString(font, Component.translatable("message.realscale.lotm.no_path"), 14, 59, 0xFFE0D6E8, false);
            int hintY = 78;
            for (var line : font.split(Component.translatable("gui.realscale.lotm_panel.start_hint"), 222)) {
                graphics.drawString(font, line, 14, hintY, 0xFFB8ADC9, false);
                hintY += 12;
            }
            return;
        }
        LotmPath path = hasPath ? LotmPath.values()[menu.pathIndex() - 1] : null;
        Component pathName = path == null
                ? Component.translatable("message.realscale.lotm.no_path")
                : Component.translatable("lotm.realscale.path." + path.id());
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
        } else if (selectedTab == 3) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy_header"), 14, 51, 0xFFEDE4F6, false);
            if (alchemyRecipes.isEmpty() || this.minecraft.level == null) {
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy.no_recipes"),
                        14, 78, 0xFFB8ADC9, false);
            } else {
                RecipeHolder<LotmAlchemyRecipe> holder = alchemyRecipes.get(recipeIndex);
                LotmAlchemyRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess());
                graphics.drawString(font,
                        Component.translatable("gui.realscale.lotm_panel.alchemy.page",
                                recipeIndex + 1, alchemyRecipes.size()), 14, 66, 0xFFB8ADC9, false);
                graphics.drawString(font,
                        Component.translatable("gui.realscale.lotm_panel.alchemy.inputs"),
                        14, 77, 0xFFD7CDE4, false);
                int index = 0;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] choices = ingredient.getItems();
                    if (choices.length > 0 && index < 4) {
                        graphics.renderItem(choices[0], 22 + (index % 2) * 23, 86 + (index / 2) * 23);
                    }
                    index++;
                }
                graphics.drawString(font,
                        Component.translatable("gui.realscale.lotm_panel.alchemy.fuel"),
                        115, 77, 0xFFD7CDE4, false);
                graphics.renderItem(new ItemStack(LotmContent.SPIRIT_EMBER.get()), 120, 100);
                graphics.drawString(font,
                        Component.translatable("gui.realscale.lotm_panel.alchemy.result"),
                        185, 77, 0xFFD7CDE4, false);
                graphics.renderItem(result, 195, 100);
                graphics.drawString(font, font.plainSubstrByWidth(result.getHoverName().getString(), 222),
                        14, 132, 0xFFE6DCF3, false);
                for (var line : font.split(
                        Component.translatable("gui.realscale.lotm_panel.alchemy.fuel_note"), 222)) {
                    graphics.drawString(font, line, 14, 147, 0xFFB8ADC9, false);
                    break;
                }
            }
        } else if (selectedTab == 4) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal_header"), 14, 51, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal_discoveries",
                    menu.researchEntries()), 14, 66, 0xFFD7CDE4, false);
            final String[] categories = {"exploration", "skill", "research", "fieldwork"};
            final int[] caps = {12, 8, 9, 6};
            for (int index = 0; index < categories.length; index++) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.journal_" + categories[index],
                        menu.dailyActing(index), caps[index]),
                        14, 82 + 19 * index, 0xFFB8ADC9, false);
            }
        } else if (selectedTab == 6) {
            LotmCreatureKind kind = LotmCreatureKind.values()[creatureIndex];
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.header", creatureIndex + 1,
                    LotmCreatureKind.values().length), 14, 51, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable(
                    "entity.realscale.lotm_" + kind.id()), 14, 67, 0xFFE0D6E8, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.sequence", kind.sequence()), 14, 81, 0xFFB8ADC9, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.disposition", Component.translatable(
                            kind.isHostile() ? "gui.realscale.lotm_panel.bestiary.hostile"
                                    : "gui.realscale.lotm_panel.bestiary.passive")), 14, 94, 0xFFB8ADC9, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.health", kind.maxHealth()), 14, 107, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.attack", kind.attackDamage()), 14, 120, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.armor", kind.armor()), 14, 133, 0xFFD7CDE4, false);
            Component drop = Component.translatable("gui.realscale.lotm_panel.bestiary.drop",
                    Component.translatable("item.realscale.lotm_" + kind.dropId()),
                    LotmDropBalance.effectiveChance(menu.materialDropPercent(), kind.sequence()));
            int y = 146;
            for (var line : font.split(drop, 222)) {
                if (y > 158) break;
                graphics.drawString(font, line, 14, y, 0xFFE5CB9F, false);
                y += 12;
            }
        } else {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.settings_header"), 14, 51, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.skills", onOff(menu.skillsEnabled())), 14, 68, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.manuals", onOff(menu.skillBooksEnabled())), 14, 83, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.appearance", onOff(menu.appearanceEffectsEnabled())), 14, 98, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.creatures", onOff(menu.creaturesEnabled())), 14, 113, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.skill_cost", menu.skillCostPercent()), 14, 128, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.setting.drop_rate",
                    menu.materialDropPercent(), LotmDropBalance.effectiveChance(menu.materialDropPercent(), 8)), 14, 143, 0xFFD7CDE4, false);
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
