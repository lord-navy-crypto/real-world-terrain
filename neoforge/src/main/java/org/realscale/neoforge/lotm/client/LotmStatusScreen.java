package org.realscale.neoforge.lotm.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.realscale.neoforge.lotm.LotmActing;
import org.realscale.neoforge.lotm.LotmAlchemyContent;
import org.realscale.neoforge.lotm.LotmAlchemyRecipe;
import org.realscale.neoforge.lotm.LotmBalance;
import org.realscale.neoforge.lotm.LotmContent;
import org.realscale.neoforge.lotm.LotmCreatureKind;
import org.realscale.neoforge.lotm.LotmDropBalance;
import org.realscale.neoforge.lotm.LotmPanelNetwork;
import org.realscale.neoforge.lotm.LotmPath;
import org.realscale.neoforge.lotm.LotmStatusMenu;

/** Occult console: readable overview, branching skills, live research log and settings. */
public final class LotmStatusScreen extends AbstractContainerScreen<LotmStatusMenu> {
    private static final int JOURNAL_PAGE_SIZE = 7;
    private int selectedTab;
    private int creatureIndex;
    private int journalPage;
    private Button hudToggle;
    private Button auraToggle;
    private Button openBestiary;
    private Button previousCreature;
    private Button nextCreature;
    private Button backToMaterials;
    private Button previousRecipe;
    private Button nextRecipe;
    private Button previousJournalPage;
    private Button nextJournalPage;
    private List<RecipeHolder<LotmAlchemyRecipe>> alchemyRecipes = List.of();
    private int recipeIndex;

    public LotmStatusScreen(LotmStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 232;
    }

    @Override
    protected void init() {
        super.init();
        refreshRecipes();
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.overview"),
                button -> selectedTab = 0).bounds(leftPos + 8, topPos + 24, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.skills"),
                button -> selectedTab = 1).bounds(leftPos + 59, topPos + 24, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.materials"),
                button -> selectedTab = 2).bounds(leftPos + 110, topPos + 24, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.alchemy"),
                button -> selectedTab = 3).bounds(leftPos + 161, topPos + 24, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.journal"),
                button -> { selectedTab = 4; journalPage = 0; })
                .bounds(leftPos + 212, topPos + 24, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realscale.lotm_panel.tab.settings"),
                button -> selectedTab = 5).bounds(leftPos + 263, topPos + 24, 49, 18).build());

        hudToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_HUD))
                .bounds(leftPos + 10, topPos + 204, 144, 18).build());
        auraToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_APPEARANCE))
                .bounds(leftPos + 166, topPos + 204, 144, 18).build());

        openBestiary = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.open"),
                button -> { selectedTab = 6; creatureIndex = 0; })
                .bounds(leftPos + 92, topPos + 204, 136, 18).build());
        previousCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.previous"),
                button -> creatureIndex = Math.floorMod(creatureIndex - 1, LotmCreatureKind.values().length))
                .bounds(leftPos + 10, topPos + 204, 76, 18).build());
        nextCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.next"),
                button -> creatureIndex = (creatureIndex + 1) % LotmCreatureKind.values().length)
                .bounds(leftPos + 92, topPos + 204, 76, 18).build());
        backToMaterials = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.back"),
                button -> selectedTab = 2)
                .bounds(leftPos + 174, topPos + 204, 136, 18).build());

        previousRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.previous"),
                button -> recipeIndex = Math.floorMod(recipeIndex - 1, Math.max(1, alchemyRecipes.size())))
                .bounds(leftPos + 12, topPos + 204, 100, 18).build());
        nextRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.next"),
                button -> recipeIndex = (recipeIndex + 1) % Math.max(1, alchemyRecipes.size()))
                .bounds(leftPos + 208, topPos + 204, 100, 18).build());

        previousJournalPage = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.journal.previous"),
                button -> journalPage = Math.max(0, journalPage - 1))
                .bounds(leftPos + 12, topPos + 204, 100, 18).build());
        nextJournalPage = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.journal.next"),
                button -> journalPage = Math.min(journalPageCount() - 1, journalPage + 1))
                .bounds(leftPos + 208, topPos + 204, 100, 18).build());
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

    private int journalPageCount() {
        int entries = LotmPanelNetwork.clientResearchEntries().size();
        return Math.max(1, (entries + JOURNAL_PAGE_SIZE - 1) / JOURNAL_PAGE_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        journalPage = Math.min(journalPage, journalPageCount() - 1);
        if (hudToggle != null) {
            hudToggle.visible = selectedTab == 5;
            hudToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.hud_toggle",
                    onOff(menu.hudEnabled())));
        }
        if (auraToggle != null) {
            auraToggle.visible = selectedTab == 5;
            auraToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.aura_toggle",
                    onOff(menu.appearanceEnabled())));
        }
        if (openBestiary != null) openBestiary.visible = selectedTab == 2;
        if (previousCreature != null) previousCreature.visible = selectedTab == 6;
        if (nextCreature != null) nextCreature.visible = selectedTab == 6;
        if (backToMaterials != null) backToMaterials.visible = selectedTab == 6;
        if (previousRecipe != null) previousRecipe.visible = selectedTab == 3 && alchemyRecipes.size() > 1;
        if (nextRecipe != null) nextRecipe.visible = selectedTab == 3 && alchemyRecipes.size() > 1;
        if (previousJournalPage != null) previousJournalPage.visible = selectedTab == 4 && journalPage > 0;
        if (nextJournalPage != null) nextJournalPage.visible = selectedTab == 4
                && journalPage + 1 < journalPageCount();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderRecipeItemTooltip(graphics, mouseX, mouseY);
        renderJournalEntryTooltip(graphics, mouseX, mouseY);
    }

    /** Full localized clue name is available on hover even when the row is shortened. */
    private void renderJournalEntryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedTab != 4 || mouseX < leftPos + 166 || mouseX >= leftPos + 300) return;
        int relativeY = mouseY - (topPos + 96);
        if (relativeY < 0) return;
        int row = relativeY / 14;
        if (row >= JOURNAL_PAGE_SIZE || relativeY % 14 >= 12) return;
        List<String> entries = LotmPanelNetwork.clientResearchEntries();
        int index = journalPage * JOURNAL_PAGE_SIZE + row;
        if (index < 0 || index >= entries.size()) return;
        graphics.renderTooltip(font, researchName(entries.get(index)), mouseX, mouseY);
    }

    /** Tooltips identify the actual ingredients and output in the recipe browser. */
    private void renderRecipeItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedTab != 3 || alchemyRecipes.isEmpty()
                || minecraft == null || minecraft.level == null) return;
        LotmAlchemyRecipe recipe = alchemyRecipes.get(recipeIndex).value();
        int ingredientIndex = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredientIndex >= 4) break;
            int x = leftPos + 30 + (ingredientIndex % 2) * 25;
            int y = topPos + 93 + (ingredientIndex / 2) * 25;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                ItemStack[] choices = ingredient.getItems();
                if (choices.length > 0) graphics.renderTooltip(font, choices[0], mouseX, mouseY);
                return;
            }
            ingredientIndex++;
        }
        int fuelX = leftPos + 175, fuelY = topPos + 107;
        if (mouseX >= fuelX && mouseX < fuelX + 16 && mouseY >= fuelY && mouseY < fuelY + 16) {
            graphics.renderTooltip(font, new ItemStack(LotmContent.SPIRIT_EMBER.get()), mouseX, mouseY);
            return;
        }
        int outputX = leftPos + 255, outputY = topPos + 107;
        if (mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16) {
            ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
            if (!result.isEmpty()) graphics.renderTooltip(font, result, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF10111A);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + 21, 0xFF29243A);
        graphics.fill(x + 6, y + 46, x + imageWidth - 6, y + 196, 0xFF1D1C2B);
        graphics.fill(x + 6, y + 200, x + imageWidth - 6, y + 226, 0xFF171722);

        if (selectedTab == 0) {
            bar(graphics, x + 14, y + 99, 292, 7, menu.spirit(), 100, 0xFF69B9D0);
            bar(graphics, x + 14, y + 136, 292, 7, menu.digestion(), 100, 0xFFB28AD8);
            bar(graphics, x + 14, y + 173, 292, 7, menu.instability(), 100, 0xFFD98A9A);
        } else if (selectedTab == 1) {
            for (int slot = 1; slot <= 2; slot++) {
                int cardX = x + (slot == 1 ? 12 : 164);
                graphics.fill(cardX, y + 66, cardX + 144, y + 196, 0xFF282438);
                boolean treeActive = menu.skillsEnabled()
                        && (menu.sequence() == 9 || menu.sequence() == 8);
                int firstNode = treeActive ? 0xFF86C9B3 : 0xFF625A70;
                int secondNode = treeActive && menu.sequence() == 8
                        ? 0xFF86C9B3 : treeActive ? 0xFFB28AD8 : 0xFF625A70;
                graphics.fill(cardX + 19, y + 128, cardX + 22, y + 158,
                        treeActive ? 0xFF665A7A : 0xFF494454);
                graphics.fill(cardX + 14, y + 119, cardX + 27, y + 132, firstNode);
                graphics.fill(cardX + 14, y + 151, cardX + 27, y + 164, secondNode);
                int masteryCap = menu.sequence() == 9 ? 1 : menu.skillMasteryCap();
                bar(graphics, cardX + 12, y + 188, 120, 4,
                        menu.skillMastery(slot), masteryCap, slot == 1 ? 0xFFB28AD8 : 0xFF79B7C7);
            }
        } else if (selectedTab == 2) {
            graphics.fill(x + 12, y + 66, x + 308, y + 196, 0xFF282438);
        } else if (selectedTab == 3 && !alchemyRecipes.isEmpty()) {
            for (int index = 0; index < 4; index++) {
                int itemX = x + 30 + (index % 2) * 25;
                int itemY = y + 93 + (index / 2) * 25;
                graphics.fill(itemX - 2, itemY - 2, itemX + 18, itemY + 18, 0xFF403650);
            }
            graphics.fill(x + 173, y + 105, x + 195, y + 127, 0xFF403650);
            graphics.fill(x + 253, y + 105, x + 275, y + 127, 0xFF403650);
        } else if (selectedTab == 4) {
            graphics.fill(x + 12, y + 66, x + 152, y + 196, 0xFF282438);
            graphics.fill(x + 160, y + 66, x + 308, y + 196, 0xFF282438);
            final String[] categories = {"exploration", "skill", "research", "fieldwork"};
            for (int index = 0; index < categories.length; index++) {
                bar(graphics, x + 22, y + 101 + index * 24, 120, 4,
                        menu.dailyActing(index), LotmActing.dailyCap(categories[index]), 0xFF79B7C7);
            }
        } else if (selectedTab == 5) {
            graphics.fill(x + 12, y + 66, x + 152, y + 196, 0xFF282438);
            graphics.fill(x + 160, y + 66, x + 308, y + 196, 0xFF282438);
        } else if (selectedTab == 6) {
            graphics.fill(x + 12, y + 66, x + 308, y + 196, 0xFF282438);
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, int height,
            int value, int maximum, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF0C0D14);
        int safeMaximum = Math.max(1, maximum);
        int filled = Math.max(0, Math.min(width, width * Math.max(0, value) / safeMaximum));
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.title"),
                9, 7, 0xFFF0E8FF, false);
        boolean hasPath = menu.pathIndex() > 0 && menu.pathIndex() <= LotmPath.values().length;
        if (!hasPath && selectedTab != 3 && selectedTab != 4 && selectedTab != 5 && selectedTab != 6) {
            graphics.drawString(font, Component.translatable("message.realscale.lotm.no_path"),
                    14, 59, 0xFFE0D6E8, false);
            int hintY = 82;
            for (var line : font.split(Component.translatable("gui.realscale.lotm_panel.start_hint"), 286)) {
                graphics.drawString(font, line, 14, hintY, 0xFFB8ADC9, false);
                hintY += 13;
            }
            return;
        }

        LotmPath path = hasPath ? LotmPath.values()[menu.pathIndex() - 1] : null;
        Component pathName = path == null
                ? Component.translatable("message.realscale.lotm.no_path")
                : Component.translatable("lotm.realscale.path." + path.id());

        if (selectedTab == 0) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.path", pathName),
                    14, 52, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.sequence", menu.sequence()),
                    14, 69, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.spirit", menu.spirit(), 100),
                    14, 87, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.digestion", menu.digestion(), 100),
                    14, 124, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.instability", menu.instability()),
                    14, 161, 0xFFE0B8C8, false);
            Component accessory = menu.hasSpiritFocus()
                    ? Component.translatable("item.realscale.lotm_spirit_focus")
                    : menu.hasDivinationPendulum()
                            ? Component.translatable("item.realscale.lotm_divination_pendulum")
                            : Component.translatable("message.realscale.lotm.status.no_accessory");
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.accessory", accessory),
                    14, 186, 0xFFB8ADC9, false);
        } else if (selectedTab == 1 && path != null) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.skill_tree.header"),
                    14, 52, 0xFFEDE4F6, false);
            int sequence = menu.sequence();
            boolean activeSequence = sequence == 9 || sequence == 8;
            for (int slot = 1; slot <= 2; slot++) {
                int cardX = slot == 1 ? 14 : 166;
                int mastery = menu.skillMastery(slot);
                int masteryCap = sequence == 9 ? 1 : menu.skillMasteryCap();
                String skillKey = "gui.realscale.lotm_panel.skill." + path.id() + "." + slot;
                graphics.drawString(font, Component.translatable(
                        slot == 1 ? "gui.realscale.lotm_panel.skill_tree.primary"
                                : "gui.realscale.lotm_panel.skill_tree.secondary"),
                        cardX, 73, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(skillKey + ".name"),
                        cardX, 87, 0xFFEDE4F6, false);
                int descriptionY = 101;
                for (var line : font.split(Component.translatable(skillKey + ".description"), 128)) {
                    if (descriptionY > 116) break;
                    graphics.drawString(font, line, cardX, descriptionY, 0xFFB8ADC9, false);
                    descriptionY += 12;
                }

                Component sequence9 = !menu.skillsEnabled()
                        ? Component.translatable("gui.realscale.lotm_panel.skill_tree.disabled")
                        : activeSequence
                                ? Component.translatable("gui.realscale.lotm_panel.skill_tree.node.s9")
                                : Component.translatable("gui.realscale.lotm_panel.skill_tree.archived");
                Component sequence8 = !menu.skillsEnabled()
                        ? Component.translatable("gui.realscale.lotm_panel.skill_tree.disabled")
                        : sequence == 8
                                ? Component.translatable("gui.realscale.lotm_panel.skill_tree.node.s8")
                                : sequence == 9
                                        ? Component.translatable("gui.realscale.lotm_panel.skill_tree.node.locked")
                                        : Component.translatable("gui.realscale.lotm_panel.skill_tree.archived");
                graphics.drawString(font, sequence9, cardX + 34, 128, 0xFF9ED8C0, false);
                graphics.drawString(font, sequence8, cardX + 34, 155,
                        sequence == 8 ? 0xFF9ED8C0 : 0xFFB8ADC9, false);
                if (activeSequence && menu.skillsEnabled()) {
                    int base = LotmBalance.skillCost(path.id(), slot, sequence,
                            menu.hasDivinationPendulum(), menu.instability());
                    int cost = LotmBalance.skillCostWithMastery(base, mastery, menu.skillCostPercent());
                    graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.skill_tree.cost",
                            cost, LotmBalance.skillCooldownTicks(slot, sequence)),
                            cardX, 174, menu.spirit() < cost ? 0xFFEAA0A0 : 0xFFB8ADC9, false);
                    graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.mastery",
                            mastery, masteryCap), cardX, 184, 0xFFD7CDE4, false);
                } else {
                    Component unavailable = !menu.skillsEnabled()
                            ? Component.translatable("gui.realscale.lotm_panel.skill_tree.disabled")
                            : Component.translatable("gui.realscale.lotm_panel.skill_tree.unavailable");
                    graphics.drawString(font, unavailable, cardX, 174, 0xFFB8ADC9, false);
                }
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
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.materials_header"),
                    14, 52, 0xFFEDE4F6, false);
            int lineY = 73;
            for (Component section : new Component[] {materials, manualOne, manualTwo}) {
                for (var line : font.split(section, 286)) {
                    if (lineY > 190) break;
                    graphics.drawString(font, line, 14, lineY, 0xFFD7CDE4, false);
                    lineY += 13;
                }
                lineY += 4;
                if (lineY > 190) break;
            }
        } else if (selectedTab == 3) {
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy_header"),
                    14, 52, 0xFFEDE4F6, false);
            if (alchemyRecipes.isEmpty() || this.minecraft.level == null) {
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy.no_recipes"),
                        14, 84, 0xFFB8ADC9, false);
            } else {
                RecipeHolder<LotmAlchemyRecipe> holder = alchemyRecipes.get(recipeIndex);
                LotmAlchemyRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess());
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.alchemy.page", recipeIndex + 1, alchemyRecipes.size()),
                        14, 69, 0xFFB8ADC9, false);
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy.inputs"),
                        14, 84, 0xFFD7CDE4, false);
                int index = 0;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] choices = ingredient.getItems();
                    if (choices.length > 0 && index < 4) {
                        graphics.renderItem(choices[0], 30 + (index % 2) * 25,
                                93 + (index / 2) * 25);
                    }
                    index++;
                }
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy.fuel"),
                        166, 84, 0xFFD7CDE4, false);
                graphics.renderItem(new ItemStack(LotmContent.SPIRIT_EMBER.get()), 175, 107);
                graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.alchemy.result"),
                        246, 84, 0xFFD7CDE4, false);
                graphics.renderItem(result, 255, 107);
                graphics.drawString(font, font.plainSubstrByWidth(result.getHoverName().getString(), 286),
                        14, 145, 0xFFE6DCF3, false);
                int noteY = 164;
                for (var line : font.split(
                        Component.translatable("gui.realscale.lotm_panel.alchemy.fuel_note"), 286)) {
                    if (noteY > 190) break;
                    graphics.drawString(font, line, 14, noteY, 0xFFB8ADC9, false);
                    noteY += 12;
                }
            }
        } else if (selectedTab == 4) {
            List<String> entries = LotmPanelNetwork.clientResearchEntries();
            int pages = journalPageCount();
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal_header"),
                    14, 52, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal.daily_progress"),
                    22, 79, 0xFFE5CB9F, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal.recent",
                    menu.researchEntries()), 166, 79, 0xFFE5CB9F, false);
            final String[] categories = {"exploration", "skill", "research", "fieldwork"};
            for (int index = 0; index < categories.length; index++) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.journal." + categories[index],
                        menu.dailyActing(index), LotmActing.dailyCap(categories[index])),
                        22, 92 + index * 24, 0xFFD7CDE4, false);
            }
            if (entries.isEmpty()) {
                int emptyY = 96;
                for (var line : font.split(
                        Component.translatable("gui.realscale.lotm_panel.journal.empty"), 128)) {
                    graphics.drawString(font, line, 166, emptyY, 0xFFB8ADC9, false);
                    emptyY += 13;
                }
            } else {
                int first = journalPage * JOURNAL_PAGE_SIZE;
                int last = Math.min(entries.size(), first + JOURNAL_PAGE_SIZE);
                int lineY = 96;
                for (int index = first; index < last; index++) {
                    Component name = researchName(entries.get(index));
                    String text = Component.translatable(
                            "gui.realscale.lotm_panel.journal.entry", name).getString();
                    graphics.drawString(font, font.plainSubstrByWidth(text, 132),
                            166, lineY, 0xFFD7CDE4, false);
                    lineY += 14;
                }
            }
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.journal.page", journalPage + 1, pages),
                    133, 211, 0xFFB8ADC9, false);
        } else if (selectedTab == 5) {
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.personal_header"),
                    22, 79, 0xFFE5CB9F, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.hud", onOff(menu.hudEnabled())),
                    22, 98, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.aura", onOff(menu.appearanceEnabled())),
                    22, 115, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.server_header"),
                    22, 139, 0xFFE5CB9F, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.skills", onOff(menu.skillsEnabled())),
                    22, 155, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.manuals", onOff(menu.skillBooksEnabled())),
                    22, 169, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.appearance", onOff(menu.appearanceEffectsEnabled())),
                    22, 183, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.creatures", onOff(menu.creaturesEnabled())),
                    166, 98, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.setting.skill_cost", menu.skillCostPercent()),
                    166, 115, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.drop_rate", menu.materialDropPercent(),
                    LotmDropBalance.effectiveChance(menu.materialDropPercent(), 8)),
                    166, 132, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.hud_refresh", menu.hudRefreshTicks()),
                    166, 149, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.aura_interval", menu.appearanceIntervalTicks()),
                    166, 166, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.manual_alchemy", menu.manualAlchemyTicks()),
                    166, 183, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.settings.readonly_note"),
                    166, 193, 0xFFB8ADC9, false);
        } else if (selectedTab == 6) {
            LotmCreatureKind kind = LotmCreatureKind.values()[creatureIndex];
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.header", creatureIndex + 1,
                    LotmCreatureKind.values().length), 14, 52, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable(
                    "entity.realscale.lotm_" + kind.id()), 14, 72, 0xFFE0D6E8, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.sequence", kind.sequence()),
                    14, 89, 0xFFB8ADC9, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.disposition", Component.translatable(
                            kind.isHostile() ? "gui.realscale.lotm_panel.bestiary.hostile"
                                    : "gui.realscale.lotm_panel.bestiary.passive")),
                    14, 105, 0xFFB8ADC9, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.health", kind.maxHealth()),
                    14, 122, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.attack", kind.attackDamage()),
                    14, 139, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.bestiary.armor", kind.armor()),
                    14, 156, 0xFFD7CDE4, false);
            Component drop = Component.translatable("gui.realscale.lotm_panel.bestiary.drop",
                    Component.translatable("item.realscale.lotm_" + kind.dropId()),
                    LotmDropBalance.effectiveChance(menu.materialDropPercent(), kind.sequence()));
            int dropY = 174;
            for (var line : font.split(drop, 286)) {
                if (dropY > 192) break;
                graphics.drawString(font, line, 14, dropY, 0xFFE5CB9F, false);
                dropY += 12;
            }
        }
    }

    private static Component researchName(String source) {
        int first = source.indexOf(':');
        int second = first < 0 ? -1 : source.indexOf(':', first + 1);
        if (first <= 0 || second <= first + 1 || second == source.length() - 1) {
            return Component.literal(source);
        }
        String kind = source.substring(0, first);
        if (!"entity".equals(kind) && !"block".equals(kind)) return Component.literal(source);
        String namespace = source.substring(first + 1, second);
        String id = source.substring(second + 1);
        return Component.translatable(kind + "." + namespace + "." + id);
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
}
