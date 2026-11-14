package org.realscale.neoforge.lotm.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int JOURNAL_DEFAULT_PAGE_SIZE = 7;
    private int selectedTab;
    private int creatureIndex;
    private int journalPage;
    private int journalFilter;
    private int settingsPage;
    private Button journalFilterButton;
    private Button journalSortButton;
    private Button settingsPageButton;
    private EditBox journalSearch;
    private Button hudToggle;
    private Button auraToggle;
    private Button soundToggle;
    private Button skillFeedbackToggle;
    private Button journalNotificationsToggle;
    private Button panelScaleButton;
    private Button panelOpacityButton;
    private Button compactLayoutButton;
    private Button journalTimestampButton;
    private Button journalSortSettingButton;
    private Button skillDetailsButton;
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
        imageHeight = 276;
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
                .bounds(leftPos + 10, topPos + 238, 96, 18).build());
        auraToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_APPEARANCE))
                .bounds(leftPos + 112, topPos + 238, 96, 18).build());

        openBestiary = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.open"),
                button -> { selectedTab = 6; creatureIndex = 0; })
                .bounds(leftPos + 92, topPos + 238, 136, 18).build());
        previousCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.previous"),
                button -> creatureIndex = Math.floorMod(creatureIndex - 1, LotmCreatureKind.values().length))
                .bounds(leftPos + 10, topPos + 238, 76, 18).build());
        nextCreature = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.next"),
                button -> creatureIndex = (creatureIndex + 1) % LotmCreatureKind.values().length)
                .bounds(leftPos + 92, topPos + 238, 76, 18).build());
        backToMaterials = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.bestiary.back"),
                button -> selectedTab = 2)
                .bounds(leftPos + 174, topPos + 238, 136, 18).build());

        previousRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.previous"),
                button -> recipeIndex = Math.floorMod(recipeIndex - 1, Math.max(1, alchemyRecipes.size())))
                .bounds(leftPos + 12, topPos + 238, 100, 18).build());
        nextRecipe = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.alchemy.next"),
                button -> recipeIndex = (recipeIndex + 1) % Math.max(1, alchemyRecipes.size()))
                .bounds(leftPos + 208, topPos + 238, 100, 18).build());

        previousJournalPage = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.journal.previous"),
                button -> journalPage = Math.max(0, journalPage - 1))
                .bounds(leftPos + 12, topPos + 238, 100, 18).build());
        nextJournalPage = addRenderableWidget(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.journal.next"),
                button -> journalPage = Math.min(journalPageCount() - 1, journalPage + 1))
                .bounds(leftPos + 208, topPos + 238, 100, 18).build());

        journalSearch = addRenderableWidget(new EditBox(font, leftPos + 12, topPos + 52,
                180, 18, Component.translatable("gui.realscale.lotm_panel.journal.search")));
        journalSearch.setMaxLength(48);
        journalSearch.setHint(Component.translatable("gui.realscale.lotm_panel.journal.search_hint"));
        journalSearch.setResponder(value -> journalPage = 0);

        journalFilterButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> { journalFilter = (journalFilter + 1) % 11; journalPage = 0; })
                .bounds(leftPos + 204, topPos + 52, 104, 18).build());
        journalSortButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> {
                    LotmClientConfig.JOURNAL_NEWEST_FIRST.set(!LotmClientConfig.JOURNAL_NEWEST_FIRST.get());
                    LotmClientConfig.save();
                    journalPage = 0;
                }).bounds(leftPos + 204, topPos + 74, 104, 18).build());

        settingsPageButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> settingsPage = (settingsPage + 1) % 2)
                .bounds(leftPos + 204, topPos + 52, 104, 18).build());

        soundToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_SOUND))
                .bounds(leftPos + 214, topPos + 238, 96, 18).build());
        skillFeedbackToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_SKILL_FEEDBACK))
                .bounds(leftPos + 10, topPos + 258, 144, 16).build());
        journalNotificationsToggle = addRenderableWidget(Button.builder(Component.empty(),
                button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        LotmStatusMenu.BUTTON_TOGGLE_JOURNAL_NOTIFICATIONS))
                .bounds(leftPos + 166, topPos + 258, 144, 16).build());

        panelScaleButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            int current = LotmClientConfig.PANEL_SCALE_PERCENT.get();
            int next = current >= 125 ? 75 : current >= 115 ? 125 : current >= 100 ? 115
                    : current >= 85 ? 100 : 85;
            LotmClientConfig.PANEL_SCALE_PERCENT.set(next);
            LotmClientConfig.save();
        }).bounds(leftPos + 14, topPos + 92, 90, 18).build());
        panelOpacityButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            int current = LotmClientConfig.PANEL_OPACITY_PERCENT.get();
            int next = current >= 100 ? 45 : current >= 90 ? 100 : current >= 75 ? 90
                    : current >= 60 ? 75 : 60;
            LotmClientConfig.PANEL_OPACITY_PERCENT.set(next);
            LotmClientConfig.save();
        }).bounds(leftPos + 112, topPos + 92, 90, 18).build());
        compactLayoutButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            LotmClientConfig.COMPACT_LAYOUT.set(!LotmClientConfig.COMPACT_LAYOUT.get());
            LotmClientConfig.save();
            journalPage = 0;
        }).bounds(leftPos + 210, topPos + 92, 98, 18).build());
        journalTimestampButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            LotmClientConfig.SHOW_JOURNAL_TIMESTAMPS.set(!LotmClientConfig.SHOW_JOURNAL_TIMESTAMPS.get());
            LotmClientConfig.save();
        }).bounds(leftPos + 14, topPos + 122, 90, 18).build());
        journalSortSettingButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            LotmClientConfig.JOURNAL_NEWEST_FIRST.set(!LotmClientConfig.JOURNAL_NEWEST_FIRST.get());
            LotmClientConfig.save();
            journalPage = 0;
        }).bounds(leftPos + 112, topPos + 122, 90, 18).build());
        skillDetailsButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            LotmClientConfig.SHOW_SKILL_DETAILS.set(!LotmClientConfig.SHOW_SKILL_DETAILS.get());
            LotmClientConfig.save();
        }).bounds(leftPos + 210, topPos + 122, 98, 18).build());
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

    private List<String> filteredJournalEntries() {
        List<String> all = LotmPanelNetwork.clientResearchEntries();
        String selectedKind = journalFilterKey();
        String query = journalSearch == null ? "" : journalSearch.getValue().trim().toLowerCase(Locale.ROOT);
        java.util.stream.Stream<String> stream = all.stream()
                .filter(source -> "all".equals(selectedKind) || selectedKind.equals(journalCategory(source)));
        if (!query.isEmpty()) {
            stream = stream.filter(source -> source.toLowerCase(Locale.ROOT).contains(query)
                    || researchName(source).getString().toLowerCase(Locale.ROOT).contains(query));
        }
        Comparator<String> order = Comparator.comparingLong(LotmStatusScreen::journalTimestamp);
        if (LotmClientConfig.JOURNAL_NEWEST_FIRST.get()) order = order.reversed();
        return stream.sorted(order).toList();
    }

    private String journalFilterKey() {
        return switch (journalFilter) {
            case 1 -> "potion";
            case 2 -> "material";
            case 3 -> "alchemy";
            case 4 -> "skill";
            case 5 -> "pathway";
            case 6 -> "entity";
            case 7 -> "block";
            case 8 -> "item";
            case 9 -> "biome";
            case 10 -> "other";
            default -> "all";
        };
    }

    private static String[] journalParts(String source) {
        if (source == null) return null;
        String[] parts = source.split("\\|", 5);
        return parts.length == 5 && "event".equals(parts[0]) ? parts : null;
    }

    private static String journalCategory(String source) {
        String[] parts = journalParts(source);
        return parts == null ? researchKind(source) : parts[2];
    }

    private static String journalStatus(String source) {
        String[] parts = journalParts(source);
        return parts == null ? "discovered" : parts[3];
    }

    private static String journalId(String source) {
        String[] parts = journalParts(source);
        return parts == null ? source : parts[4];
    }

    private static long journalTimestamp(String source) {
        String[] parts = journalParts(source);
        if (parts == null) return 0L;
        try { return Math.max(0L, Long.parseLong(parts[1])); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private Component journalTimeLabel(String source) {
        long time = journalTimestamp(source);
        long day = time / 24000L + 1L;
        long dayTicks = (time + 6000L) % 24000L;
        int hour = (int) (dayTicks / 1000L);
        int minute = (int) ((dayTicks % 1000L) * 60L / 1000L);
        return Component.translatable("gui.realscale.lotm_panel.journal.timestamp", day, hour, minute);
    }

    private int journalPageSize() {
        return LotmClientConfig.COMPACT_LAYOUT.get() ? 9 : JOURNAL_DEFAULT_PAGE_SIZE;
    }

    private int journalRowHeight() {
        return LotmClientConfig.COMPACT_LAYOUT.get() ? 12 : 14;
    }

    private int journalPageCount() {
        int entries = filteredJournalEntries().size();
        int pageSize = journalPageSize();
        return Math.max(1, (entries + pageSize - 1) / pageSize);
    }

    private double panelMouseX(double mouseX) {
        double scale = LotmClientConfig.PANEL_SCALE_PERCENT.get() / 100.0D;
        return width / 2.0D + (mouseX - width / 2.0D) / scale;
    }

    private double panelMouseY(double mouseY) {
        double scale = LotmClientConfig.PANEL_SCALE_PERCENT.get() / 100.0D;
        return height / 2.0D + (mouseY - height / 2.0D) / scale;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(panelMouseX(mouseX), panelMouseY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(panelMouseX(mouseX), panelMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scale = LotmClientConfig.PANEL_SCALE_PERCENT.get() / 100.0D;
        return super.mouseDragged(panelMouseX(mouseX), panelMouseY(mouseY), button,
                dragX / scale, dragY / scale);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        journalPage = Math.min(journalPage, journalPageCount() - 1);
        boolean settings = selectedTab == 5;
        boolean personalSettings = settings && settingsPage == 0;
        boolean interfaceSettings = settings && settingsPage == 1;
        if (hudToggle != null) {
            hudToggle.visible = personalSettings;
            hudToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.hud_toggle",
                    onOff(menu.hudEnabled())));
        }
        if (auraToggle != null) {
            auraToggle.visible = personalSettings;
            auraToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.aura_toggle",
                    onOff(menu.appearanceEnabled())));
        }
        if (soundToggle != null) {
            soundToggle.visible = personalSettings;
            soundToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.sound_toggle",
                    onOff(menu.soundEnabled())));
        }
        if (skillFeedbackToggle != null) {
            skillFeedbackToggle.visible = personalSettings;
            skillFeedbackToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.skill_feedback_toggle",
                    onOff(menu.skillFeedbackEnabled())));
        }
        if (journalNotificationsToggle != null) {
            journalNotificationsToggle.visible = personalSettings;
            journalNotificationsToggle.setMessage(Component.translatable("gui.realscale.lotm_panel.setting.journal_notifications_toggle",
                    onOff(menu.journalNotificationsEnabled())));
        }
        if (settingsPageButton != null) {
            settingsPageButton.visible = settings;
            settingsPageButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.page." + (settingsPage == 0 ? "personal" : "interface")));
        }
        if (panelScaleButton != null) {
            panelScaleButton.visible = interfaceSettings;
            panelScaleButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.scale", LotmClientConfig.PANEL_SCALE_PERCENT.get()));
        }
        if (panelOpacityButton != null) {
            panelOpacityButton.visible = interfaceSettings;
            panelOpacityButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.opacity", LotmClientConfig.PANEL_OPACITY_PERCENT.get()));
        }
        if (compactLayoutButton != null) {
            compactLayoutButton.visible = interfaceSettings;
            compactLayoutButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.layout",
                    onOff(LotmClientConfig.COMPACT_LAYOUT.get())));
        }
        if (journalTimestampButton != null) {
            journalTimestampButton.visible = interfaceSettings;
            journalTimestampButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.timestamps",
                    onOff(LotmClientConfig.SHOW_JOURNAL_TIMESTAMPS.get())));
        }
        if (journalSortSettingButton != null) {
            journalSortSettingButton.visible = interfaceSettings;
            journalSortSettingButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.sort",
                    onOff(LotmClientConfig.JOURNAL_NEWEST_FIRST.get())));
        }
        if (skillDetailsButton != null) {
            skillDetailsButton.visible = interfaceSettings;
            skillDetailsButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.settings.skill_details",
                    onOff(LotmClientConfig.SHOW_SKILL_DETAILS.get())));
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
        if (journalSearch != null) journalSearch.visible = selectedTab == 4;
        if (journalFilterButton != null) {
            journalFilterButton.visible = selectedTab == 4;
            journalFilterButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.journal.filter." + journalFilterKey()));
        }
        if (journalSortButton != null) {
            journalSortButton.visible = selectedTab == 4;
            journalSortButton.setMessage(Component.translatable(
                    "gui.realscale.lotm_panel.journal.sort." +
                            (LotmClientConfig.JOURNAL_NEWEST_FIRST.get() ? "newest" : "oldest")));
        }

        double scale = LotmClientConfig.PANEL_SCALE_PERCENT.get() / 100.0D;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0D * (1.0D - scale),
                height / 2.0D * (1.0D - scale), 0.0D);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);
        super.render(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY), partialTick);
        renderTooltip(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY));
        renderRecipeItemTooltip(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY));
        renderJournalEntryTooltip(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY));
        renderBestiaryTooltip(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY));
        renderSkillTooltip(graphics, (int) panelMouseX(mouseX), (int) panelMouseY(mouseY));
        graphics.pose().popPose();
    }

    /** Full clue, verification state and in-game timestamp are available on hover. */
    private void renderJournalEntryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedTab != 4 || mouseX < leftPos + 166 || mouseX >= leftPos + 300) return;
        int relativeY = mouseY - (topPos + 102);
        if (relativeY < 0) return;
        int row = relativeY / journalRowHeight();
        if (row >= journalPageSize() || relativeY % journalRowHeight() >= journalRowHeight() - 2) return;
        List<String> entries = filteredJournalEntries();
        int index = journalPage * journalPageSize() + row;
        if (index < 0 || index >= entries.size()) return;
        String entry = entries.get(index);
        List<Component> lines = new ArrayList<>();
        lines.add(researchName(entry));
        lines.add(Component.translatable("gui.realscale.lotm_panel.journal.status." + journalStatus(entry)));
        lines.add(Component.translatable("gui.realscale.lotm_panel.journal.category." + journalCategory(entry)));
        if (LotmClientConfig.SHOW_JOURNAL_TIMESTAMPS.get() && journalTimestamp(entry) > 0L) {
            lines.add(journalTimeLabel(entry));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** The field guide reveals ecology and signature behavior without crowding its compact card. */
    private void renderBestiaryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedTab != 6 || mouseX < leftPos + 12 || mouseX >= leftPos + 308
                || mouseY < topPos + 66 || mouseY >= topPos + 196) return;
        LotmCreatureKind kind = LotmCreatureKind.values()[creatureIndex];
        String movement = kind.isAquatic() ? "aquatic"
                : kind.isAmphibious() ? "amphibious"
                : kind.isFlying() ? "flying" : "terrestrial";
        String activity = kind.isNocturnal() ? "nocturnal" : "anytime";
        List<Component> lines = List.of(
                Component.translatable("gui.realscale.lotm_panel.bestiary.tooltip.behavior." + kind.id()),
                Component.translatable("gui.realscale.lotm_panel.bestiary.tooltip.habitat." + kind.id()),
                Component.translatable("gui.realscale.lotm_panel.bestiary.tooltip.movement." + movement),
                Component.translatable("gui.realscale.lotm_panel.bestiary.tooltip.activity." + activity),
                Component.translatable("gui.realscale.lotm_panel.bestiary.tooltip.group_size",
                        kind.minGroupSize(), kind.maxGroupSize()));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** Skill details are derived from the same balance functions used by server ability checks. */
    private void renderSkillTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedTab != 1 || menu.pathIndex() <= 0 || menu.pathIndex() > LotmPath.values().length
                || mouseX < leftPos + 12 || mouseX >= leftPos + 308
                || mouseY < topPos + 66 || mouseY >= topPos + 196) return;
        LotmPath path = LotmPath.values()[menu.pathIndex() - 1];
        int slot = mouseX < leftPos + 160 ? 1 : 2;
        String skillKey = "gui.realscale.lotm_panel.skill." + path.id() + "." + slot;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skillKey + ".name"));
        if (LotmClientConfig.SHOW_SKILL_DETAILS.get()) {
            lines.add(Component.translatable(skillKey + ".description"));
        }
        int sequence = menu.sequence();
        boolean unlocked = menu.skillsEnabled() && (sequence == 9 || sequence == 8);
        int cost = menu.skillCost(slot);
        String state = !menu.skillsEnabled() ? "disabled"
                : sequence == 9 && unlocked ? "sequence9"
                : sequence == 8 && unlocked ? "sequence8"
                : "locked";
        lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.state." + state));
        if (unlocked) {
            lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.cost",
                    cost, LotmBalance.MAX_SPIRIT));
            lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.cooldown",
                    LotmBalance.skillCooldownTicks(slot, sequence) / 20.0F));
            lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.mastery",
                    menu.skillMastery(slot), sequence == 9 ? 1 : menu.skillMasteryCap()));
            if (menu.spirit() < cost) {
                lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.low_spirit",
                        cost - menu.spirit()));
            }
        } else if (menu.skillsEnabled()) {
            lines.add(Component.translatable("gui.realscale.lotm_panel.skill.tooltip.requirement"));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
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
                if (choices.length > 0) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable(
                            "gui.realscale.lotm_panel.alchemy.ingredient_options", choices.length));
                    int shown = Math.min(choices.length, 8);
                    for (int choice = 0; choice < shown; choice++) {
                        lines.add(choices[choice].getHoverName());
                    }
                    if (choices.length > shown) {
                        lines.add(Component.translatable(
                                "gui.realscale.lotm_panel.alchemy.ingredient_more",
                                choices.length - shown));
                    }
                    graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
                }
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
        graphics.fill(x, y, x + imageWidth, y + imageHeight, panelColor(0xFF10111A));
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + 21, panelColor(0xFF29243A));
        graphics.fill(x + 6, y + 46, x + imageWidth - 6, y + 228, panelColor(0xFF1D1C2B));
        graphics.fill(x + 6, y + 232, x + imageWidth - 6, y + 270, panelColor(0xFF171722));

        if (selectedTab == 0) {
            bar(graphics, x + 14, y + 99, 292, 6, menu.spirit(), 100, 0xFF69B9D0);
            bar(graphics, x + 14, y + 123, 292, 6, menu.digestion(), 100, 0xFFB28AD8);
            bar(graphics, x + 14, y + 147, 292, 6, menu.instability(), 100, 0xFFD98A9A);
        } else if (selectedTab == 1) {
            for (int slot = 1; slot <= 2; slot++) {
                int cardX = x + (slot == 1 ? 12 : 164);
                graphics.fill(cardX, y + 66, cardX + 144, y + 196, panelColor(0xFF282438));
                boolean sequence9Unlocked = menu.skillsEnabled()
                        && (menu.sequence() == 9 || menu.sequence() == 8);
                boolean sequence8Unlocked = menu.skillsEnabled() && menu.sequence() == 8;
                int firstNode = sequence9Unlocked ? 0xFF86C9B3 : 0xFF625A70;
                int secondNode = sequence8Unlocked ? 0xFF86C9B3
                        : menu.skillsEnabled() && menu.sequence() == 9 ? 0xFFB28AD8 : 0xFF625A70;
                graphics.fill(cardX + 19, y + 128, cardX + 22, y + 158,
                        sequence9Unlocked ? 0xFF665A7A : 0xFF494454);
                graphics.fill(cardX + 14, y + 119, cardX + 27, y + 132, firstNode);
                graphics.fill(cardX + 14, y + 151, cardX + 27, y + 164, secondNode);
                int masteryCap = menu.sequence() == 9 ? 1 : menu.skillMasteryCap();
                bar(graphics, cardX + 12, y + 188, 120, 4,
                        menu.skillMastery(slot), masteryCap, slot == 1 ? 0xFFB28AD8 : 0xFF79B7C7);
            }
        } else if (selectedTab == 2) {
            graphics.fill(x + 12, y + 66, x + 308, y + 226, panelColor(0xFF282438));
        } else if (selectedTab == 3 && !alchemyRecipes.isEmpty()) {
            for (int index = 0; index < 4; index++) {
                int itemX = x + 30 + (index % 2) * 25;
                int itemY = y + 93 + (index / 2) * 25;
                graphics.fill(itemX - 2, itemY - 2, itemX + 18, itemY + 18, panelColor(0xFF403650));
            }
            graphics.fill(x + 173, y + 105, x + 195, y + 127, panelColor(0xFF403650));
            graphics.fill(x + 253, y + 105, x + 275, y + 127, panelColor(0xFF403650));
        } else if (selectedTab == 4) {
            graphics.fill(x + 12, y + 66, x + 152, y + 226, panelColor(0xFF282438));
            graphics.fill(x + 160, y + 66, x + 308, y + 226, panelColor(0xFF282438));
            final String[] categories = {"exploration", "skill", "research", "fieldwork"};
            for (int index = 0; index < categories.length; index++) {
                bar(graphics, x + 22, y + 104 + index * 24, 120, 4,
                        menu.dailyActing(index), LotmActing.dailyCap(categories[index]), 0xFF79B7C7);
            }
        } else if (selectedTab == 5) {
            graphics.fill(x + 12, y + 66, x + 152, y + 226, panelColor(0xFF282438));
            graphics.fill(x + 160, y + 66, x + 308, y + 226, panelColor(0xFF282438));
        } else if (selectedTab == 6) {
            graphics.fill(x + 12, y + 66, x + 308, y + 226, panelColor(0xFF282438));
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, int height,
            int value, int maximum, int color) {
        graphics.fill(x, y, x + width, y + height, panelColor(0xFF0C0D14));
        int safeMaximum = Math.max(1, maximum);
        int filled = Math.max(0, Math.min(width, width * Math.max(0, value) / safeMaximum));
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, panelColor(color));
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
                    166, 52, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.spirit", menu.spirit(), 100),
                    14, 88, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.digestion", menu.digestion(), 100),
                    14, 112, 0xFFD7CDE4, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.instability", menu.instability()),
                    14, 136, 0xFFE0B8C8, false);
            Component accessory = menu.hasSpiritFocus()
                    ? Component.translatable("item.realscale.lotm_spirit_focus")
                    : menu.hasDivinationPendulum()
                            ? Component.translatable("item.realscale.lotm_divination_pendulum")
                            : Component.translatable("message.realscale.lotm.status.no_accessory");
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.accessory", accessory),
                    14, 160, 0xFFB8ADC9, false);
            if (menu.sequence() == 9) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.dashboard.next_prepared",
                        menu.preparedPotionRequirement()), 14, 177, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.dashboard.next_characteristic",
                        menu.characteristicRequirement()), 166, 177, 0xFFE5CB9F, false);
            } else {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.dashboard.archived"), 14, 177, 0xFFB8ADC9, false);
            }
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.dashboard.materials", menu.materialCount()),
                    14, 194, 0xFFD7CDE4, false);
            for (int slot = 1; slot <= 2; slot++) {
                int cost = menu.skillCost(slot);
                boolean unlocked = menu.skillsEnabled() && (menu.sequence() == 9 || menu.sequence() == 8);
                Component skillState = !menu.skillsEnabled()
                        ? Component.translatable("gui.realscale.lotm_panel.skill.tooltip.state.disabled")
                        : !unlocked
                                ? Component.translatable("gui.realscale.lotm_panel.skill.tooltip.state.locked")
                                : menu.spirit() >= cost
                                        ? Component.translatable("gui.realscale.lotm_panel.skill.tooltip.state.sequence" + menu.sequence())
                                        : Component.translatable("gui.realscale.lotm_panel.skill.tooltip.state.low_spirit");
                Component skill = Component.translatable(
                        "gui.realscale.lotm_panel.dashboard.skill",
                        Component.translatable("gui.realscale.lotm_panel.skill." + path.id() + "." + slot + ".name"),
                        skillState, cost);
                graphics.drawString(font, skill, 14, 208 + (slot - 1) * 13, 0xFFD7CDE4, false);
            }
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
                        : sequence == 9
                                ? Component.translatable("gui.realscale.lotm_panel.skill_tree.node.s9")
                                : sequence == 8
                                        ? Component.translatable("gui.realscale.lotm_panel.skill_tree.node.complete")
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
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.dashboard.materials", menu.materialCount()),
                    14, 68, 0xFFE5CB9F, false);
            if (menu.sequence() == 9) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.dashboard.requirements",
                        menu.preparedPotionRequirement(), menu.characteristicRequirement()),
                        14, 82, 0xFFB8ADC9, false);
            }
            int lineY = 99;
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
            List<String> entries = filteredJournalEntries();
            int pages = journalPageCount();
            int rowHeight = journalRowHeight();
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal_header"),
                    14, 52, 0xFFEDE4F6, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal.daily_progress"),
                    22, 79, 0xFFE5CB9F, false);
            graphics.drawString(font, Component.translatable("gui.realscale.lotm_panel.journal.filtered_count",
                    entries.size(), menu.researchEntries()), 166, 92, 0xFFE5CB9F, false);
            final String[] categories = {"exploration", "skill", "research", "fieldwork"};
            for (int index = 0; index < categories.length; index++) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.journal." + categories[index],
                        menu.dailyActing(index), LotmActing.dailyCap(categories[index])),
                        22, 104 + index * 24, 0xFFD7CDE4, false);
            }
            if (entries.isEmpty()) {
                int emptyY = 108;
                Component emptyMessage = menu.researchEntries() > 0 && journalFilter != 0
                        ? Component.translatable("gui.realscale.lotm_panel.journal.empty_filter")
                        : Component.translatable("gui.realscale.lotm_panel.journal.empty");
                for (var line : font.split(emptyMessage, 128)) {
                    graphics.drawString(font, line, 166, emptyY, 0xFFB8ADC9, false);
                    emptyY += 13;
                }
            } else {
                int first = journalPage * journalPageSize();
                int last = Math.min(entries.size(), first + journalPageSize());
                int lineY = 104;
                for (int index = first; index < last; index++) {
                    String entry = entries.get(index);
                    Component status = Component.translatable(
                            "gui.realscale.lotm_panel.journal.status.short." + journalStatus(entry));
                    Component name = researchName(entry);
                    String text = Component.translatable(
                            "gui.realscale.lotm_panel.journal.entry_status", status, name).getString();
                    graphics.drawString(font, font.plainSubstrByWidth(text, 138),
                            166, lineY, 0xFFD7CDE4, false);
                    lineY += rowHeight;
                }
            }
            graphics.drawString(font, Component.translatable(
                    "gui.realscale.lotm_panel.journal.page", journalPage + 1, pages),
                    133, 250, 0xFFB8ADC9, false);
        } else if (selectedTab == 5) {
            if (settingsPage == 0) {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.personal_header"),
                        22, 79, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.hud", onOff(menu.hudEnabled())),
                        22, 98, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.aura", onOff(menu.appearanceEnabled())),
                        22, 112, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.sound", onOff(menu.soundEnabled())),
                        22, 126, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.skill_feedback", onOff(menu.skillFeedbackEnabled())),
                        22, 140, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.journal_notifications",
                        onOff(menu.journalNotificationsEnabled())),
                        22, 154, 0xFFD7CDE4, false);

                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.server_header"),
                        166, 79, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.skills", onOff(menu.skillsEnabled())),
                        166, 98, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.manuals", onOff(menu.skillBooksEnabled())),
                        166, 112, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.appearance", onOff(menu.appearanceEffectsEnabled())),
                        166, 126, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.creatures", onOff(menu.creaturesEnabled())),
                        166, 140, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.setting.skill_cost", menu.skillCostPercent()),
                        166, 154, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.drop_rate", menu.materialDropPercent(),
                        LotmDropBalance.effectiveChance(menu.materialDropPercent(), 8)),
                        166, 168, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.server_timing",
                        menu.hudRefreshTicks(), menu.appearanceIntervalTicks(), menu.manualAlchemyTicks()),
                        166, 182, 0xFFB8ADC9, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.readonly_note"),
                        166, 198, 0xFFB8ADC9, false);
            } else {
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.interface_header"),
                        22, 79, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.scale_description"),
                        22, 116, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.opacity_description"),
                        22, 146, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.layout_description"),
                        22, 176, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.journal_header"),
                        166, 79, 0xFFE5CB9F, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.timestamps_description"),
                        166, 116, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.sort_description"),
                        166, 146, 0xFFD7CDE4, false);
                graphics.drawString(font, Component.translatable(
                        "gui.realscale.lotm_panel.settings.skill_details_description"),
                        166, 176, 0xFFD7CDE4, false);
            }
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

    private static String researchKind(String source) {
        int separator = source.indexOf(':');
        if (separator <= 0) return "other";
        String kind = source.substring(0, separator);
        return switch (kind) {
            case "entity", "block", "item", "biome" -> kind;
            default -> "other";
        };
    }

    private static Component researchName(String source) {
        String category = journalCategory(source);
        String id = journalId(source);
        if ("potion".equals(category) || "pathway".equals(category)) {
            String[] parts = id.split(":");
            if (parts.length >= 2) return Component.translatable(
                    "lotm.realscale.rank." + parts[0] + "." + parts[1]);
        }
        if ("skill".equals(category)) {
            String[] parts = id.split(":");
            if (parts.length >= 3) {
                int slot = "manual".equals(parts[1]) ? parsePositive(parts[2]) : parsePositive(parts[2]);
                if (slot >= 1 && slot <= 2) return Component.translatable(
                        "gui.realscale.lotm_panel.skill." + parts[0] + "." + slot + ".name");
            }
        }
        if ("alchemy".equals(category) || "material".equals(category)) {
            int separator = id.indexOf(':');
            if (separator > 0 && separator < id.length() - 1) {
                String namespace = id.substring(0, separator);
                String path = id.substring(separator + 1);
                return Component.translatable(("material".equals(category) ? "block." : "item.")
                        + namespace + "." + path);
            }
        }
        int first = id.indexOf(':');
        int second = first < 0 ? -1 : id.indexOf(':', first + 1);
        if (first <= 0 || second <= first + 1 || second == id.length() - 1) {
            return Component.literal(id);
        }
        String kind = id.substring(0, first);
        if (!"entity".equals(kind) && !"block".equals(kind)
                && !"item".equals(kind) && !"biome".equals(kind)) return Component.literal(id);
        String namespace = id.substring(first + 1, second);
        String path = id.substring(second + 1);
        return Component.translatable(kind + "." + namespace + "." + path);
    }

    private static int parsePositive(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static int panelColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int adjusted = alpha * LotmClientConfig.PANEL_OPACITY_PERCENT.get() / 100;
        return (color & 0x00FFFFFF) | (adjusted << 24);
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
}
