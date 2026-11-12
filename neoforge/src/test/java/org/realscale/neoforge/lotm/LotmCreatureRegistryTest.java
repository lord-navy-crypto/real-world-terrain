package org.realscale.neoforge.lotm;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmCreatureRegistryTest {
    @Test
    void everyActiveCreatureHasOneStableRegisteredEntityIdAndSupplier() {
        assertEquals(EnumSet.allOf(LotmCreatureKind.class), LotmCreatureEntities.registeredKinds());

        Set<ResourceLocation> ids = new HashSet<>();
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            ResourceLocation id = LotmCreatureEntities.registryId(kind);
            assertEquals("realscale:lotm_" + kind.id(), id.toString());
            assertTrue(ids.add(id), "Duplicate entity registry ID: " + id);
            assertNotNull(LotmCreatureEntities.type(kind), "Missing entity supplier for " + kind.id());
        }
        assertEquals(10, ids.size());
    }

    @Test
    void eachCreatureHasValidSpawnAndAttributeMetadata() {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            assertNotNull(kind.spawnCategory(), kind.id());
            assertTrue(kind.width() > 0.0F, kind.id());
            assertTrue(kind.height() > 0.0F, kind.id());
            assertTrue(kind.maxHealth() > 0.0D, kind.id());
            assertTrue(kind.movementSpeed() > 0.0D, kind.id());
            assertTrue(kind.spawnWeight() > 0, kind.id());
            assertTrue(kind.minGroupSize() > 0, kind.id());
            assertTrue(kind.maxGroupSize() >= kind.minGroupSize(), kind.id());
        }
    }

    @Test
    void eachCreatureDropResolvesToAnExistingRegisteredMaterial() {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            assertNotNull(LotmContent.materialItem(kind.dropId()),
                    "Missing material registration for " + kind.id() + ": " + kind.dropId());
        }
    }

    @Test
    void legacyVanillaVariantsDefaultOffWhileIndependentCreaturesDefaultOn() {
        assertFalse(LotmConfig.CREATURE_VARIANTS.get());
        assertTrue(LotmConfig.CREATURE_ENTITIES.get());
    }
}
