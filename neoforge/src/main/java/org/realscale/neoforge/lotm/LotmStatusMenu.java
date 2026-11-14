package org.realscale.neoforge.lotm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Read-only synchronized player panel with server-authoritative preference buttons. */
public final class LotmStatusMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 33;
    public static final int BUTTON_TOGGLE_HUD = 0;
    public static final int BUTTON_TOGGLE_APPEARANCE = 1;
    public static final int BUTTON_TOGGLE_SOUND = 2;
    public static final int BUTTON_TOGGLE_SKILL_FEEDBACK = 3;
    public static final int BUTTON_TOGGLE_JOURNAL_NOTIFICATIONS = 4;
    public static final int BUTTON_TOGGLE_PARTICLES = 5;
    private final ContainerData data;

    public LotmStatusMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    public LotmStatusMenu(int id, Inventory inventory, ContainerData data) {
        super(LotmAlchemyContent.STATUS_MENU.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        addDataSlots(data);
    }

    public static ContainerData dataFor(ServerPlayer player) {
        return new ContainerData() {
            @Override public int get(int index) {
                LotmPath path = LotmPath.of(player);
                return switch (index) {
                    case 0 -> path == null ? 0 : path.ordinal() + 1;
                    case 1 -> LotmState.sequence(player);
                    case 2 -> LotmState.spirit(player);
                    case 3 -> LotmState.digestion(player);
                    case 4 -> LotmState.instability(player);
                    case 5 -> path == null ? 0 : LotmState.skillMastery(player, path, 1);
                    case 6 -> path == null ? 0 : LotmState.skillMastery(player, path, 2);
                    case 7 -> LotmHud.enabled(player) ? 1 : 0;
                    case 8 -> LotmAppearance.enabled(player) ? 1 : 0;
                    case 9 -> LotmConfig.SKILLS_ENABLED.get() ? 1 : 0;
                    case 10 -> LotmConfig.SKILL_BOOKS_ENABLED.get() ? 1 : 0;
                    case 11 -> LotmConfig.APPEARANCE_EFFECTS.get() ? 1 : 0;
                    case 12 -> LotmConfig.CREATURE_ENTITIES.get() ? 1 : 0;
                    case 13 -> LotmConfig.SKILL_COST_PERCENT.get();
                    case 14 -> LotmConfig.MATERIAL_DROP_CHANCE_PERCENT.get();
                    case 15 -> LotmConfig.HUD_REFRESH_TICKS.get();
                    case 16 -> LotmConfig.ALCHEMY_SKILL_MANUAL_TICKS.get();
                    case 17 -> LotmConfig.SKILL_MASTERY_CAP.get();
                    case 18 -> LotmConfig.APPEARANCE_INTERVAL_TICKS.get();
                    case 19 -> LotmCurios.hasDivinationPendulum(player) ? 1 : 0;
                    case 20 -> LotmCurios.hasSpiritFocus(player) ? 1 : 0;
                    case 21 -> LotmActing.journalEntries(player).size();
                    case 22 -> LotmActing.dailyUsed(player, "exploration");
                    case 23 -> LotmActing.dailyUsed(player, "skill");
                    case 24 -> LotmActing.dailyUsed(player, "research");
                    case 25 -> LotmActing.dailyUsed(player, "fieldwork");
                    case 26 -> LotmPreferences.soundEnabled(player) ? 1 : 0;
                    case 27 -> LotmPreferences.skillFeedbackEnabled(player) ? 1 : 0;
                    case 28 -> LotmPreferences.journalNotificationsEnabled(player) ? 1 : 0;
                    case 29 -> LotmContent.countMaterials(player);
                    case 30 -> LotmConfig.POTION_PRACTICE.get();
                    case 31 -> LotmConfig.CHARACTERISTIC_PRACTICE.get();
                    case 32 -> LotmPreferences.particlesEnabled(player) ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { /* server owns all values */ }
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    public int pathIndex() { return data.get(0); }
    public int sequence() { return data.get(1); }
    public int spirit() { return data.get(2); }
    public int digestion() { return data.get(3); }
    public int instability() { return data.get(4); }
    public int skillMastery(int slot) { return slot == 1 ? data.get(5) : slot == 2 ? data.get(6) : 0; }
    public boolean hudEnabled() { return data.get(7) != 0; }
    public boolean appearanceEnabled() { return data.get(8) != 0; }
    public boolean skillsEnabled() { return data.get(9) != 0; }
    public boolean skillBooksEnabled() { return data.get(10) != 0; }
    public boolean appearanceEffectsEnabled() { return data.get(11) != 0; }
    public boolean creaturesEnabled() { return data.get(12) != 0; }
    public int skillCostPercent() { return data.get(13); }
    /** Same pathway, Curios, instability, mastery and config formula used by the ability validator. */
    public int skillCost(int slot) {
        if (pathIndex() <= 0 || pathIndex() > LotmPath.values().length
                || (sequence() != 9 && sequence() != 8) || slot < 1 || slot > 2) {
            return LotmBalance.MAX_SPIRIT;
        }
        LotmPath path = LotmPath.values()[pathIndex() - 1];
        int base = LotmBalance.skillCost(path.id(), slot, sequence(),
                hasDivinationPendulum(), instability());
        return LotmBalance.skillCostWithMastery(base, skillMastery(slot), skillCostPercent());
    }
    public int materialDropPercent() { return data.get(14); }
    public int hudRefreshTicks() { return data.get(15); }
    public int manualAlchemyTicks() { return data.get(16); }
    public int skillMasteryCap() { return data.get(17); }
    public int appearanceIntervalTicks() { return data.get(18); }
    public boolean hasDivinationPendulum() { return data.get(19) != 0; }
    public boolean hasSpiritFocus() { return data.get(20) != 0; }
    public int researchEntries() { return data.get(21); }
    public int dailyActing(int index) { return index >= 0 && index < 4 ? data.get(22 + index) : 0; }
    public boolean soundEnabled() { return data.get(26) != 0; }
    public boolean skillFeedbackEnabled() { return data.get(27) != 0; }
    public boolean journalNotificationsEnabled() { return data.get(28) != 0; }
    public int materialCount() { return data.get(29); }
    public int preparedPotionRequirement() { return data.get(30); }
    public int characteristicRequirement() { return data.get(31); }
    public boolean particlesEnabled() { return data.get(32) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !LotmConfig.ENABLED.get()) return false;
        if (id == BUTTON_TOGGLE_HUD) {
            LotmHud.toggle(serverPlayer);
            return true;
        }
        if (id == BUTTON_TOGGLE_APPEARANCE) {
            LotmAppearance.toggle(serverPlayer);
            return true;
        }
        if (id == BUTTON_TOGGLE_SOUND) {
            LotmPreferences.toggleSound(serverPlayer);
            return true;
        }
        if (id == BUTTON_TOGGLE_SKILL_FEEDBACK) {
            LotmPreferences.toggleSkillFeedback(serverPlayer);
            return true;
        }
        if (id == BUTTON_TOGGLE_JOURNAL_NOTIFICATIONS) {
            LotmPreferences.toggleJournalNotifications(serverPlayer);
            return true;
        }
        if (id == BUTTON_TOGGLE_PARTICLES) {
            LotmPreferences.toggleParticles(serverPlayer);
            return true;
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player.isAlive(); }
}
