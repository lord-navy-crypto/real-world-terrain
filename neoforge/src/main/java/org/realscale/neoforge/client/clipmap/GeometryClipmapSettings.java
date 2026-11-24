package org.realscale.neoforge.client.clipmap;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Persistent client-side switch for the automatic Geometry Clipmap renderer.
 *
 * <p>The default is OFF so distant terrain rendering starts only after the
 * player explicitly enables it from the in-game pause menu. Explicit saved
 * preferences are preserved across launches; disabling also releases the
 * active render resources.
 */
public final class GeometryClipmapSettings {
    private static final Path CONFIG_FILE =
            Path.of("config", "realscale-geometry-clipmap.properties");

    private static volatile boolean enabled = loadEnabled(CONFIG_FILE);

    private GeometryClipmapSettings() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean next) {
        if (enabled == next) {
            return;
        }
        try {
            saveEnabled(CONFIG_FILE, next);
            enabled = next;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot save RealScale Geometry Clipmap setting", failure);
        }
    }

    static boolean loadEnabled(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            String value = properties.getProperty("enabled", "false").trim();
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            return false;
        } catch (IOException | IllegalArgumentException failure) {
            // A missing or damaged preference must never enable the renderer at boot.
            return false;
        }
    }

    static void saveEnabled(Path path, boolean value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value));
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "RealScale Geometry Clipmap runtime setting");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
