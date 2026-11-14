package org.realscale.neoforge.lotm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Server-safe helpers for the single occult_focus Curios slot.
 * The slot intentionally accepts either the Spirit Focus or Divination Pendulum,
 * so players choose a specialization rather than stacking both bonuses.
 */
public final class LotmCurios {
    private LotmCurios() {}

    public static boolean hasSpiritFocus(LivingEntity entity) {
        return hasEquipped(entity, LotmContent.SPIRIT_FOCUS.get());
    }

    public static boolean hasDivinationPendulum(LivingEntity entity) {
        return hasEquipped(entity, LotmContent.DIVINATION_PENDULUM.get());
    }

    private static boolean hasEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(inventory -> {
            IItemHandlerModifiable equipped = inventory.getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                if (equipped.getStackInSlot(slot).is(item)) return true;
            }
            return false;
        }).orElse(false);
    }
}
