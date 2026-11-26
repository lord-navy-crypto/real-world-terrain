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
 * Persistent client-side controls for the automatic Geometry Clipmap renderer.
 *
 * <p>The default is OFF. The far range is selected independently
 * from Minecraft's vanilla chunk distance, using a compact 64/128/256 grid.
 * <p>128 is the balanced default; 256 remains available as a higher-cost option.
 */
public final class GeometryClipmapSettings {
    private static final Path CONFIG_FILE =
            Path.of("config", "realscale-geometry-clipmap.properties");
    private static final int DEFAULT_GRID_CELLS = 128;

    private static volatile boolean enabled = loadEnabled(CONFIG_FILE);
    private static volatile int gridCells = loadGridCells(CONFIG_FILE);

    private GeometryClipmapSettings() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int gridCells() {
        return gridCells;
    }

    public static synchronized void setEnabled(boolean next) {
        if (enabled == next) {
            return;
        }
        try {
            saveSettings(CONFIG_FILE, next, gridCells);
            enabled = next;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot save RealScale Geometry Clipmap setting", failure);
        }
    }

    public static synchronized void setGridCells(int next) {
        if (!isSupportedGridCells(next)) {
            throw new IllegalArgumentException(
                    "Geometry Clipmap range grid must be 64, 128, or 256");
        }
        if (gridCells == next) {
            return;
        }
        try {
            saveSettings(CONFIG_FILE, enabled, next);
            gridCells = next;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot save RealScale Geometry Clipmap range", failure);
        }
    }

    public static boolean isSupportedGridCells(int value) {
        return value == 64 || value == 128 || value == 256;
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
            return false;
        }
    }

    static int loadGridCells(Path path) {
        if (!Files.isRegularFile(path)) {
            return DEFAULT_GRID_CELLS;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            int value = Integer.parseInt(
                    properties.getProperty("gridCells", Integer.toString(DEFAULT_GRID_CELLS)).trim());
            return isSupportedGridCells(value) ? value : DEFAULT_GRID_CELLS;
        } catch (IOException | IllegalArgumentException failure) {
            return DEFAULT_GRID_CELLS;
        }
    }

    static void saveEnabled(Path path, boolean value) throws IOException {
        saveSettings(path, value, loadGridCells(path));
    }

    static void saveGridCells(Path path, int value) throws IOException {
        if (!isSupportedGridCells(value)) {
            throw new IllegalArgumentException(
                    "Geometry Clipmap range grid must be 64, 128, or 256");
        }
        saveSettings(path, loadEnabled(path), value);
    }

    private static void saveSettings(Path path, boolean enabledValue,
            int gridCellsValue) throws IOException {
        if (!isSupportedGridCells(gridCellsValue)) {
            throw new IllegalArgumentException(
                    "Geometry Clipmap range grid must be 64, 128, or 256");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabledValue));
        properties.setProperty("gridCells", Integer.toString(gridCellsValue));
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "RealScale Geometry Clipmap client settings");
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
