package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * A rare Night Church variant of the vanilla wandering trader.
 * It offers only non-combat utility tools and never bypasses pathway progression.
 */
public final class LotmMerchantTrades {
    private static final String INITIALIZED = "realscale_lotm_trader_checked";
    private static final String NIGHT_CHURCH = "realscale_lotm_night_church_trader";
    private static final int VARIANT_DENOMINATOR = 4;

    private LotmMerchantTrades() {}

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !LotmConfig.ENABLED.get()
                || !LotmConfig.TRADERS_ENABLED.get()) return;
        if (!(event.getEntity() instanceof WanderingTrader trader)) return;

        var data = trader.getPersistentData();
        if (data.getBoolean(INITIALIZED)) return;
        data.putBoolean(INITIALIZED, true);

        // One in four wandering traders is a Night Church envoy; the rest stay vanilla.
        if (trader.getRandom().nextInt(VARIANT_DENOMINATOR) != 0) return;
        data.putBoolean(NIGHT_CHURCH, true);
        trader.setCustomName(Component.translatable("entity.realscale.lotm.night_church_trader"));
        trader.setCustomNameVisible(true);

        addEmeraldTrade(trader, LotmContent.DIVINATION_PENDULUM.get(), 22, 2, 3);
        addEmeraldTrade(trader, LotmContent.SPIRIT_FOCUS.get(), 26, 2, 3);
        addEmeraldTrade(trader, LotmContent.STABILITY_SIGIL.get(), 18, 3, 2);
    }

    private static void addEmeraldTrade(WanderingTrader trader, Item item,
            int emeraldCost, int maxUses, int experience) {
        var listing = new VillagerTrades.ItemsForEmeralds(
                item, emeraldCost, 1, maxUses, experience);
        var offer = listing.getOffer(trader, trader.getRandom());
        if (offer != null) trader.getOffers().add(offer);
    }
}
