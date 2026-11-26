package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryClipmapSettingsTest {
    @TempDir
    Path tempDir;

    // A fresh install must not start distant rendering until the player opts in.
    @Test
    void defaultsToDisabledWhenPreferenceDoesNotExist() {
        assertFalse(GeometryClipmapSettings.loadEnabled(
                tempDir.resolve("missing.properties")));
    }

    @Test
    void readsAnExplicitEnabledPreference() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "enabled=true\n");

        assertTrue(GeometryClipmapSettings.loadEnabled(file));
    }

    @Test
    void readsAnExplicitDisabledPreference() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "enabled=false\n");

        assertFalse(GeometryClipmapSettings.loadEnabled(file));
    }

    @Test
    void missingEnabledPropertyDefaultsToDisabled() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "# empty saved preference\n");

        assertFalse(GeometryClipmapSettings.loadEnabled(file));
    }

    @Test
    void persistsBothStatesAndCanReadThemBack() throws Exception {
        Path file = tempDir.resolve("nested").resolve("clipmap.properties");

        GeometryClipmapSettings.saveEnabled(file, false);
        assertFalse(GeometryClipmapSettings.loadEnabled(file));

        GeometryClipmapSettings.saveEnabled(file, true);
        assertTrue(GeometryClipmapSettings.loadEnabled(file));
    }


    @Test
    void defaultsToBalancedRangeWhenPreferenceDoesNotExist() {
        assertEquals(128, GeometryClipmapSettings.loadGridCells(
                tempDir.resolve("missing.properties")));
    }

    @Test
    void readsAndPersistsSupportedRangePresets() throws Exception {
        Path file = tempDir.resolve("nested").resolve("clipmap.properties");
        GeometryClipmapSettings.saveGridCells(file, 64);
        assertEquals(64, GeometryClipmapSettings.loadGridCells(file));
        GeometryClipmapSettings.saveGridCells(file, 128);
        assertEquals(128, GeometryClipmapSettings.loadGridCells(file));
        GeometryClipmapSettings.saveGridCells(file, 256);
        assertEquals(128, GeometryClipmapSettings.loadGridCells(file));
    }

    @Test
    void invalidRangePreferenceFallsBackToBalancedDefault() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "gridCells=512\\n");
        assertEquals(256, GeometryClipmapSettings.loadGridCells(file));
    }

    @Test
    void rejectsUnsupportedRangePreset() {
        assertThrows(IllegalArgumentException.class,
                () -> GeometryClipmapSettings.saveGridCells(
                        tempDir.resolve("clipmap.properties"), 32));
    }

    @Test
    void invalidPreferenceSafelyFallsBackToDisabled() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "enabled=maybe\n");

        assertFalse(GeometryClipmapSettings.loadEnabled(file));
    }
}
