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

    private static double number(JsonObject object, String field) {
        assertNotNull(object.get(field), "Missing fitted coefficient: " + field);
        return object.get(field).getAsDouble();
    }
}
