package org.realscale.neoforge.lotm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmCreatureRegistryTest {
    @Test
    void everyActiveCreatureHasOneStableRegistryId() {
        assertEquals(EnumSet.allOf(LotmCreatureKind.class), LotmCreatureRegistryIds.activeKinds());

        Set<ResourceLocation> ids = new HashSet<>();
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            ResourceLocation id = LotmCreatureRegistryIds.idFor(kind);
            assertEquals("realscale:lotm_" + kind.id(), id.toString());
            assertTrue(ids.add(id), "Duplicate entity registry ID: " + id);
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
    void everyBiomeModifierMatchesTheCreatureRegistryContract() {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            JsonObject definition = loadSpawnDefinition(kind.id());
            assertEquals("neoforge:add_spawns", definition.get("type").getAsString(),
                    kind.id() + " must use the add_spawns modifier");

            JsonArray biomeEntries = definition.getAsJsonArray("biomes");
            Set<String> declaredBiomes = new HashSet<>();
            for (JsonElement biomeEntry : biomeEntries) {
                String biome = biomeEntry.getAsString();
                assertTrue(declaredBiomes.add(biome),
                        kind.id() + " has a duplicate biome entry: " + biome);
            }
            assertEquals(biomeEntries.size(), declaredBiomes.size(),
                    kind.id() + " biome entries must be unique");
            assertEquals(kind.biomes(), declaredBiomes,
                    kind.id() + " JSON habitats must match LotmCreatureKind");

            JsonArray spawners = definition.getAsJsonArray("spawners");
            assertEquals(1, spawners.size(),
                    kind.id() + " must have exactly one active spawn entry");
            JsonObject spawner = spawners.get(0).getAsJsonObject();
            assertEquals("realscale:lotm_" + kind.id(), spawner.get("type").getAsString());
            assertEquals(kind.spawnWeight(), spawner.get("weight").getAsInt(),
                    kind.id() + " spawn weight drifted from the registry");
            assertEquals(kind.minGroupSize(), spawner.get("minCount").getAsInt(),
                    kind.id() + " minimum group size drifted from the registry");
            assertEquals(kind.maxGroupSize(), spawner.get("maxCount").getAsInt(),
                    kind.id() + " maximum group size drifted from the registry");
        }
    }

    @Test
    void eachCreatureDropMatchesTheStableEcologyMaterialMapping() {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            assertEquals(kind.dropId(), LotmEcologyRules.DROPS.get(kind.id()),
                    "Drop mapping mismatch for " + kind.id());
        }
    }

    @Test
    void independentCreaturesAreEnabledByDefaultAndLegacySystemsAreDisabled() {
        assertTrue(LotmConfig.DEFAULT_CREATURE_ENTITIES);
        assertFalse(LotmConfig.DEFAULT_CREATURE_VARIANTS);
        assertFalse(LotmConfig.DEFAULT_RELIC_SITES);
    }

    private static JsonObject loadSpawnDefinition(String creatureId) {
        String resource = "/data/realscale/neoforge/biome_modifier/lotm_"
                + creatureId + "_spawns.json";
        try (InputStream stream = LotmCreatureRegistryTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing biome modifier resource: " + resource);
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("Unable to read biome modifier resource: " + resource, exception);
        }
    }
}
