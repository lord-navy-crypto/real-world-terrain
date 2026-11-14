package org.realscale.neoforge.lotm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Ten active creatures can be spawned from the LOTM creative tab with no commands. */
public final class LotmSpawnEggs {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realscale");
    private static final Map<LotmCreatureKind, Supplier<? extends Item>> EGGS =
            new EnumMap<>(LotmCreatureKind.class);

    static {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            EGGS.put(kind, ITEMS.registerItem("lotm_" + kind.id() + "_spawn_egg",
                    properties -> new DeferredSpawnEggItem(
                            LotmCreatureEntities.type(kind), baseColor(kind), highlightColor(kind),
                            properties)));
        }
    }

    private LotmSpawnEggs() {}

    private static int baseColor(LotmCreatureKind kind) {
        return switch (kind) {
            case STARLIT_SQUID -> 0x255872;
            case NIGHTMARE_EYE -> 0x9C4370;
            case NIGHT_OWL -> 0x3D4255;
            case BLACK_SPOTTED_FROG -> 0x3B724B;
            case SHROUDMAN -> 0x646874;
            case HORNACIS_GOAT -> 0xB3A795;
            case IRONARM_BEAST -> 0x5B4E46;
            case FOUR_EYED_TURTLE -> 0x3A7E77;
            case CRIMSON_HOWLER -> 0x85384A;
            case DEATH_CROW -> 0x2C2D3E;
        };
    }

    private static int highlightColor(LotmCreatureKind kind) {
        return switch (kind) {
            case STARLIT_SQUID -> 0xB4EDE8;
            case NIGHTMARE_EYE -> 0xF0BDDD;
            case NIGHT_OWL -> 0xC1BED4;
            case BLACK_SPOTTED_FROG -> 0xDAEF9D;
            case SHROUDMAN -> 0xD4D3CE;
            case HORNACIS_GOAT -> 0x54473E;
            case IRONARM_BEAST -> 0xBE805A;
            case FOUR_EYED_TURTLE -> 0xB7EAD7;
            case CRIMSON_HOWLER -> 0xF2AB8F;
            case DEATH_CROW -> 0xAB8BDA;
        };
    }

    public static Supplier<? extends Item> egg(LotmCreatureKind kind) {
        return Objects.requireNonNull(EGGS.get(Objects.requireNonNull(kind)), "Missing LOTM spawn egg");
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
