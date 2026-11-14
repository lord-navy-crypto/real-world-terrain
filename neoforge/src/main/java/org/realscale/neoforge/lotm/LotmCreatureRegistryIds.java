package org.realscale.neoforge.lotm;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure creature registry metadata. Kept separate from DeferredRegister so
 * unit tests can validate stable IDs without bootstrapping the NeoForge mod bus.
 */
public final class LotmCreatureRegistryIds {
    private LotmCreatureRegistryIds() {}

    public static ResourceLocation idFor(LotmCreatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        return ResourceLocation.fromNamespaceAndPath("realscale", "lotm_" + kind.id());
    }

    public static Set<LotmCreatureKind> activeKinds() {
        return Collections.unmodifiableSet(EnumSet.allOf(LotmCreatureKind.class));
    }
}
