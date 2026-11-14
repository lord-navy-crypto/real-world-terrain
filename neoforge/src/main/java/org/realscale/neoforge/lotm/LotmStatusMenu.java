package org.realscale.neoforge.lotm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Read-only synchronized player panel with two server-authoritative preference buttons. */
public final class LotmStatusMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 18;
    public static final int BUTTON_TOGGLE_HUD = 0;
    public static final int BUTTON_TOGGLE_APPEARANCE = 1;
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
    public int materialDropPercent() { return data.get(14); }
    public int hudRefreshTicks() { return data.get(15); }
    public int manualAlchemyTicks() { return data.get(16); }
    public int skillMasteryCap() { return data.get(17); }

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
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player.isAlive(); }
}
