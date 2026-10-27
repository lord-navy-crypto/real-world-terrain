package org.realscale.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the exact production morphology kernel without initializing Minecraft's
 * registry-backed density codecs. This verifies the compiled DEM coefficients
 * change the density signal, rather than only changing preset/menu labels.
 */
final class TerrainArchetypeDensityTest {
    private static final String[] FAMILIES = {
            "alpine", "canyon", "glacial", "volcanic", "karst", "dune"
    };

    @Test
    void compiledFittedCoefficientsChangeRuntimeDensity() throws Exception {
        for (String family : FAMILIES) {
            String resourcePath = "/data/realscale/worldgen/density_function/tva_"
                    + family + "_fitted/height_signal.json";
            JsonObject resource;
            try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
                assertNotNull(stream, "Missing compiled fitted density resource: " + resourcePath);
                resource = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }

            assertEquals("realscale:terrain_archetype", resource.get("type").getAsString(),
                    family + " must use the registered runtime density codec");
            assertEquals(family, resource.get("family").getAsString(),
                    family + " resource must select its own morphology kernel");

            TerrainArchetypeMath fitted = new TerrainArchetypeMath(
                    family,
                    number(resource, "macro_gain"),
                    number(resource, "meso_gain"),
                    number(resource, "micro_gain"),
                    number(resource, "relief_gain"),
                    number(resource, "ridge_sharpness"),
                    number(resource, "cliff_index"),
                    number(resource, "valley_index"),
                    number(resource, "ridge_index"),
                    number(resource, "anisotropy_gain"),
                    number(resource, "dune_axis_radians"),
                    number(resource, "dominant_wavelength_m"));

            // A same-family neutral control keeps all base input fields fixed
            // while removing the measured fit's distinguishing coefficients.
            TerrainArchetypeMath neutral = new TerrainArchetypeMath(
                    family, 0.0, 0.0, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0,
                    0.5, 0.0, 1.0);

            int changedSamples = 0;
            double maximumDelta = 0.0;
            for (int x = -4096; x <= 4096; x += 128) {
                for (int z = -4096; z <= 4096; z += 128) {
                    double fittedValue = fitted.compute(1.0, 0.5, 0.1, x, z);
                    double neutralValue = neutral.compute(1.0, 0.5, 0.1, x, z);
                    double delta = Math.abs(fittedValue - neutralValue);
                    maximumDelta = Math.max(maximumDelta, delta);
                    if (delta > 1.0e-6) changedSamples++;
                }
            }

            assertTrue(changedSamples >= 4,
                    family + " fitted coefficients did not measurably change enough density samples; "
                            + "changed=" + changedSamples + ", maxDelta=" + maximumDelta);
        }
    }

    @Test
    void hawaiiVolcanicColumnMemoMatchesUncachedReference() throws Exception {
        String resourcePath =
                "/data/realscale/worldgen/density_function/tva_volcanic_fitted/height_signal.json";
        JsonObject resource;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing Hawaii volcanic fit resource");
            resource = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        TerrainArchetypeMath fitted = new TerrainArchetypeMath(
                "volcanic",
                number(resource, "macro_gain"),
                number(resource, "meso_gain"),
                number(resource, "micro_gain"),
                number(resource, "relief_gain"),
                number(resource, "ridge_sharpness"),
                number(resource, "cliff_index"),
                number(resource, "valley_index"),
                number(resource, "ridge_index"),
                number(resource, "anisotropy_gain"),
                number(resource, "dune_axis_radians"),
                number(resource, "dominant_wavelength_m"));

        double[] continents = {-0.15, -0.05, 0.02, 0.14, 0.34};
        for (int x = -4096; x <= 4096; x += 64) {
            for (int z = -4096; z <= 4096; z += 64) {
                // Revisit each X/Z with different Y-dependent base density and
                // continent values. The cached term must contain only X/Z shape.
                for (int level = 0; level < continents.length; level++) {
                    double original = -0.8 + level * 0.45;
                    double continent = continents[level];
                    double expected = uncachedVolcanicReference(
                            original, continent, x, z,
                            number(resource, "macro_gain"),
                            number(resource, "relief_gain"),
                            number(resource, "dominant_wavelength_m"));
                    double actual = fitted.compute(original, continent, 0.37, x, z);
                    assertEquals(expected, actual, 1.0e-12,
                            "Cached volcanic morphology changed at X/Z " + x + "/" + z);
                }
            }
        }
    }

    private static double uncachedVolcanicReference(
            double original, double continent, int x, int z,
            double macroGain, double reliefGain, double wavelength) {
        double t = smoothstepReference(-0.10, 0.22, continent);
        if (t <= 0.0) return original;

        int cellX = Math.floorDiv(x, 2048);
        int cellZ = Math.floorDiv(z, 2048);
        long site = MountainReliefMath.siteHash(cellX, cellZ);
        double centerX = cellX * 2048.0 + 1024.0
                + MountainReliefMath.centerOffsetX(site);
        double centerZ = cellZ * 2048.0 + 1024.0
                + MountainReliefMath.centerOffsetZ(site);
        double radius = Math.max(250.0, Math.min(800.0, wavelength * 0.025));
        double radial = Math.hypot(x - centerX, z - centerZ) / radius;
        if (radial >= 1.0) return original;

        double shield = Math.pow(Math.max(0.0, 1.0 - radial), 1.35);
        double caldera = 0.16 * Math.exp(-Math.pow(radial / 0.12, 2.0));
        return original + t * reliefGain * 0.55 * Math.max(macroGain, 0.12)
                * (shield - caldera);
    }

    private static double smoothstepReference(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double number(JsonObject object, String field) {
        assertNotNull(object.get(field), "Missing fitted coefficient: " + field);
        return object.get(field).getAsDouble();
    }
}
