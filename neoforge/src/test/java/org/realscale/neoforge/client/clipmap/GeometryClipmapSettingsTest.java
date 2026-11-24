package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryClipmapSettingsTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsToEnabledWhenPreferenceDoesNotExist() {
        assertTrue(GeometryClipmapSettings.loadEnabled(
                tempDir.resolve("missing.properties")));
    }

    @Test
    void readsAnExplicitDisabledPreference() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "enabled=false\n");

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
    void invalidPreferenceSafelyFallsBackToEnabled() throws Exception {
        Path file = tempDir.resolve("clipmap.properties");
        Files.writeString(file, "enabled=maybe\n");

        assertTrue(GeometryClipmapSettings.loadEnabled(file));
    }
}
