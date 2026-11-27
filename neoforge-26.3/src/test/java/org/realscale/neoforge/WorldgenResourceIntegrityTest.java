package org.realscale.neoforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class WorldgenResourceIntegrityTest {
    private static Path resourceDirectory(String name) throws IOException, URISyntaxException {
        var url = WorldgenResourceIntegrityTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "Missing packaged resource directory: " + name);
        return Path.of(url.toURI());
    }

    private static JsonObject readObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Path> jsonFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void legacyConfiguredFeatureRegistryIsNotPackaged() {
        assertNull(
                WorldgenResourceIntegrityTest.class.getClassLoader()
                        .getResource("data/realscale/worldgen/configured_feature"),
                "26.3 must package the new worldgen/feature registry, not the legacy configured_feature directory"
        );
    }

    @Test
    void everyCustomPlacedFeatureResolvesToAFeatureDefinition() throws Exception {
        Path placedDirectory = resourceDirectory("data/realscale/worldgen/placed_feature");
        Path featureDirectory = resourceDirectory("data/realscale/worldgen/feature");

        for (Path path : jsonFiles(placedDirectory)) {
            JsonObject placed = readObject(path);
            JsonElement featureElement = placed.get("feature");
            assertNotNull(featureElement, path + " has no feature reference");
            assertTrue(featureElement.isJsonPrimitive(),
                    path + " must reference a feature identifier string");
            String featureId = featureElement.getAsString();
            if (featureId.startsWith("realscale:")) {
                Path definition = featureDirectory.resolve(
                        featureId.substring("realscale:".length()) + ".json");
                assertTrue(Files.isRegularFile(definition),
                        path + " references missing feature definition " + featureId);
            }
        }
    }

    @Test
    void everyCustomBiomeModifierResolvesToAPlacedFeature() throws Exception {
        Path modifierDirectory = resourceDirectory("data/realscale/neoforge/biome_modifier");
        Path placedDirectory = resourceDirectory("data/realscale/worldgen/placed_feature");

        for (Path path : jsonFiles(modifierDirectory)) {
            JsonObject modifier = readObject(path);
            JsonElement features = modifier.get("features");
            assertNotNull(features, path + " has no features field");
            List<String> identifiers;
            if (features.isJsonPrimitive()) {
                identifiers = List.of(features.getAsString());
            } else {
                identifiers = features.getAsJsonArray().asList().stream()
                        .map(JsonElement::getAsString)
                        .toList();
            }
            for (String identifier : identifiers) {
                assertFalse(identifier.startsWith("#"),
                        path + " uses an unsupported placed-feature tag " + identifier);
                if (identifier.startsWith("realscale:")) {
                    Path placed = placedDirectory.resolve(
                            identifier.substring("realscale:".length()) + ".json");
                    assertTrue(Files.isRegularFile(placed),
                            path + " references missing placed feature " + identifier);
                }
            }
        }
    }

    @Test
    void allFeatureDefinitionsUseTheFlat26_3CodecShape() throws Exception {
        Path featureDirectory = resourceDirectory("data/realscale/worldgen/feature");
        for (Path path : jsonFiles(featureDirectory)) {
            JsonObject feature = readObject(path);
            assertTrue(feature.has("type"), path + " has no type");
            assertFalse(feature.has("config"),
                    path + " still uses the legacy nested config object");
        }
    }

    @Test
    void everyGeneratedStructurePackUses26_3Metadata() throws Exception {
        Path packRoot = resourceDirectory("resourcepacks");
        List<Path> packDirectories;
        try (Stream<Path> directories = Files.list(packRoot)) {
            packDirectories = directories.filter(Files::isDirectory).sorted().toList();
        }
        assertFalse(packDirectories.isEmpty(), "No generated structure resource packs were packaged");
        for (Path directory : packDirectories) {
            Path metadataPath = directory.resolve("pack.mcmeta");
            assertTrue(Files.isRegularFile(metadataPath), directory + " has no pack.mcmeta");
            JsonObject metadata = readObject(metadataPath).getAsJsonObject("pack");
            assertNotNull(metadata, directory + " has no pack metadata object");
            assertFalse(metadata.has("pack_format"),
                    directory + " still uses the legacy pack_format field");
            assertEquals(121, metadata.getAsJsonArray("min_format").get(0).getAsInt(),
                    directory + " has the wrong minimum data-pack major format");
            assertEquals(0, metadata.getAsJsonArray("min_format").get(1).getAsInt(),
                    directory + " has the wrong minimum data-pack minor format");
            assertEquals(121, metadata.getAsJsonArray("max_format").get(0).getAsInt(),
                    directory + " has the wrong maximum data-pack major format");
            assertEquals(0, metadata.getAsJsonArray("max_format").get(1).getAsInt(),
                    directory + " has the wrong maximum data-pack minor format");
        }
    }

}
