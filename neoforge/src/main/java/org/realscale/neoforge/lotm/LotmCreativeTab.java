package org.realscale.neoforge.lotm;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The entire playable S9/S8 LOTM toolkit is available without manual /give commands. */
public final class LotmCreativeTab {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "realscale");

    private static final Supplier<CreativeModeTab> OCCULT = TABS.register("lotm",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.realscale.lotm"))
                    .icon(() -> new ItemStack(LotmContent.SEER_POTION.get()))
                    .displayItems((parameters, output) -> populate(output))
                    .build());

    private LotmCreativeTab() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private static void populate(CreativeModeTab.Output output) {
        output.accept(LotmContent.ALCHEMY_APPARATUS_ITEM.get());
        output.accept(LotmContent.STAR_ORE_ITEM.get());
        output.accept(LotmContent.CURSED_ORE_ITEM.get());
        output.accept(LotmContent.GRAVE_ORE_ITEM.get());
        output.accept(LotmContent.LUNAR_MICA_ORE_ITEM.get());
        output.accept(LotmContent.ECHO_QUARTZ_ORE_ITEM.get());
        output.accept(LotmContent.ABYSSAL_SALT_ORE_ITEM.get());
        output.accept(LotmContent.BLOOM_ITEM.get());
        output.accept(LotmContent.FACE_ROSE_ITEM.get());
        output.accept(LotmContent.SOUL_BELLFLOWER_ITEM.get());
        output.accept(LotmContent.BLOOD_ROSE_ITEM.get());
        output.accept(LotmContent.SPIRIT_QUARTZ_ORE_ITEM.get());
        output.accept(LotmContent.RELIC_CACHE_ITEM.get());

        output.accept(LotmContent.STAR_CRYSTAL.get());
        output.accept(LotmContent.CURSED_CRYSTAL.get());
        output.accept(LotmContent.GRAVE_CRYSTAL.get());
        output.accept(LotmContent.LUNAR_MICA.get());
        output.accept(LotmContent.ECHO_QUARTZ.get());
        output.accept(LotmContent.ABYSSAL_SALT.get());
        output.accept(LotmContent.EMPTY_VIAL.get());
        output.accept(LotmContent.SPIRIT_EMBER.get());
        output.accept(LotmContent.SPIRIT_FOCUS.get());
        output.accept(LotmContent.DIVINATION_PENDULUM.get());
        output.accept(LotmContent.STABILITY_SIGIL.get());

        // The registry is the source of truth: newly added active materials
        // automatically appear in the creative catalogue without a second ID list.
        LotmContent.registeredMaterials().values().forEach(material -> output.accept(material.get()));

        for (LotmPath path : LotmPath.values()) {
            for (int sequence = 9; sequence >= 8; sequence--) {
                output.accept(LotmContent.potion(path, sequence).get());
                output.accept(LotmContent.characteristic(path, sequence).get());
            }
            for (int slot = 1; slot <= 2; slot++) {
                output.accept(LotmContent.skillBook(path, slot).get());
            }
        }
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            output.accept(LotmSpawnEggs.egg(kind).get());
        }
        // Archived S7 IDs stay registered for saves but are not displayed.
    }
}
