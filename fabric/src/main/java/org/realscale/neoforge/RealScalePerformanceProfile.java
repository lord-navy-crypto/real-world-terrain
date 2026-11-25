package org.realscale.neoforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single control plane for RealScale-owned bounded caches and guarded client
 * upload experiments. Settings are written by RealScale Studio and read once
 * at startup; changing them requires a restart because cache arrays are sized
 * when their worldgen objects are constructed.
 *
 * Cache eviction changes reuse only. It never changes terrain values.
 */
public final class RealScalePerformanceProfile {
    public enum Profile {
        BALANCED,
        LOW_MEMORY,
        SMOOTH_RENDER,
        LOW_MEMORY_SMOOTH_RENDER
    }

    private record Settings(Profile profile, boolean diagnostics,
                            boolean predictiveUpload, boolean worldgenDiagnostics) {}

    private static final Settings SETTINGS = loadSettings();
    private static final Profile ACTIVE = SETTINGS.profile();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    // SkyIslandLayout.SLOTS; kept local so this settings class stays dependency-free.
    private static final int SKY_ISLAND_SITE_SLOTS = 5;

    private RealScalePerformanceProfile() {}

    private static Settings loadSettings() {
        Profile profile = Profile.BALANCED;
        boolean diagnostics = false;
        boolean predictive = false;
        boolean worldgen = false;
        Path path = Path.of("config", "realscale-performance.json");
        try {
            if (Files.isRegularFile(path)) {
                String json=Files.readString(path);
                profile=parseProfile(stringSetting(json,"profile","balanced"));
                diagnostics=booleanSetting(json,"diagnostics",false);
                predictive=booleanSetting(json,"predictiveUpload",false);
                worldgen=booleanSetting(json,"worldgenDiagnostics",false);
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("[RealScale performance] Could not read config/realscale-performance.json; using balanced defaults: "
                    + ex.getClass().getSimpleName());
        }

        String property = System.getProperty("realscale.performance.profile");
        if (property != null) profile = parseProfile(property);
        diagnostics = booleanOverride("realscale.performance.diagnostics", diagnostics);
        predictive = booleanOverride("realscale.experimental.client.uploadbudget.predictive", predictive);
        worldgen = booleanOverride("realscale.performance.worldgen_diagnostics", worldgen);
        return new Settings(profile, diagnostics, predictive, worldgen);
    }

    private static String stringSetting(String json,String key,String fallback) {
        String quotedKey=Pattern.quote("\"" + key + "\"");
        Matcher matcher=Pattern.compile(quotedKey + "\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return matcher.find()?matcher.group(1):fallback;
    }

    private static boolean booleanSetting(String json,String key,boolean fallback) {
        String quotedKey=Pattern.quote("\"" + key + "\"");
        Matcher matcher=Pattern.compile(quotedKey + "\\s*:\\s*(true|false)",
                Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find()?Boolean.parseBoolean(matcher.group(1)):fallback;
    }

    private static boolean booleanOverride(String property, boolean fallback) {
        String value = System.getProperty(property);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public static Profile activeProfile() {
        return ACTIVE;
    }

    static Profile parseProfile(String raw) {
        String normalized = raw == null ? "balanced" :
                raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "", "balanced", "default" -> Profile.BALANCED;
            case "low_memory" -> Profile.LOW_MEMORY;
            case "smooth_render", "render_smooth" -> Profile.SMOOTH_RENDER;
            case "low_memory_smooth_render", "compact_smooth_render" ->
                    Profile.LOW_MEMORY_SMOOTH_RENDER;
            default -> {
                System.err.println("[RealScale performance] Unknown profile '" + raw
                        + "'; using balanced defaults");
                yield Profile.BALANCED;
            }
        };
    }

    static boolean usesLowMemory(Profile profile) {
        return profile == Profile.LOW_MEMORY
                || profile == Profile.LOW_MEMORY_SMOOTH_RENDER;
    }

    static boolean usesSmoothRender(Profile profile) {
        return profile == Profile.SMOOTH_RENDER
                || profile == Profile.LOW_MEMORY_SMOOTH_RENDER;
    }

    /** Compact mode uses one quarter of each power-of-two cache, minimum 16. */
    static int cacheSlots(Profile profile, String cacheName, int defaultSlots) {
        if (defaultSlots < 16 || (defaultSlots & (defaultSlots - 1)) != 0) {
            throw new IllegalArgumentException("Cache capacity must be a power of two >= 16: "
                    + cacheName);
        }
        return usesLowMemory(profile) ? Math.max(16, defaultSlots >>> 2) : defaultSlots;
    }

    public static int cacheSlots(String cacheName, int defaultSlots) {
        int selected = cacheSlots(ACTIVE, cacheName, defaultSlots);
        reportIfRequested();
        return selected;
    }

    /**
     * Approximate array-payload bytes for one representative RealScale cache set.
     * It includes one instance of each counted cache family, including nested
     * sky-island satellite reference arrays, but not stored objects/object headers,
     * every density instance, total JVM memory, GPU memory or other mods.
     */
    static long estimatedCacheArrayBytesOneSet(Profile profile) {
        long demSlots = cacheSlots(profile, "real_dem_column", 4096);
        long measuredSlots = cacheSlots(profile, "measured_horizontal", 8192);
        long biomeBaseSlots = cacheSlots(profile, "biome_base", 8192);
        long biomeResultSlots = cacheSlots(profile, "biome_result", 2048);
        long reliefSlots = cacheSlots(profile, "mountain_relief", 64);
        long skySlots = cacheSlots(profile, "sky_island", 256);
        long horizontalSlots = cacheSlots(profile, "archetype_horizontal", 1024);
        long volcanicSlots = cacheSlots(profile, "archetype_volcanic", 1024);
        long karstSlots = cacheSlots(profile, "archetype_karst", 1024);

        long dem = demSlots * (Long.BYTES + Integer.BYTES + Long.BYTES);
        long measured = measuredSlots * (Long.BYTES + Double.BYTES + 1L)
                + measuredSlots / 2L;
        long biomeBase = biomeBaseSlots * (Long.BYTES + Long.BYTES)
                + biomeBaseSlots / 2L;
        long biomeResult = biomeResultSlots * (Long.BYTES + Long.BYTES)
                + biomeResultSlots / 2L;
        long relief = reliefSlots * Long.BYTES + reliefSlots / 2L;
        long sky = skySlots * (Long.BYTES * (1L + SKY_ISLAND_SITE_SLOTS))
                + skySlots / 2L;
        long archetypeHorizontal = 3L * horizontalSlots
                * (Long.BYTES * 2L + Double.BYTES * 2L + 1L);
        long archetypeVolcanic = volcanicSlots
                * (Long.BYTES + Double.BYTES + 1L);
        long archetypeKarst = karstSlots
                * (Long.BYTES + Double.BYTES * 2L + 1L);
        return dem + measured + biomeBase + biomeResult + relief + sky
                + archetypeHorizontal + archetypeVolcanic + archetypeKarst + 96L;
    }

    public static long estimatedCacheArrayBytesOneSet() {
        return estimatedCacheArrayBytesOneSet(ACTIVE);
    }

    public static boolean diagnosticsEnabled() {
        return SETTINGS.diagnostics();
    }

    public static boolean worldgenDiagnosticsEnabled() {
        return SETTINGS.worldgenDiagnostics();
    }

    public static boolean clientUploadBudgetEnabled() {
        // No Forge 1.20.1 upload interception mixin is installed yet.
        // Never report an inactive renderer hook as enabled.
        return false;
    }

    public static boolean modeAwareUploadEnabled() {
        return clientUploadBudgetEnabled()
                && (Boolean.getBoolean("realscale.experimental.client.uploadbudget.modeaware")
                    || usesSmoothRender(ACTIVE));
    }

    public static boolean adaptiveUploadEnabled() {
        return modeAwareUploadEnabled()
                && (Boolean.getBoolean("realscale.experimental.client.uploadbudget.adaptive")
                    || usesSmoothRender(ACTIVE));
    }

    public static boolean predictiveUploadEnabled() {
        return modeAwareUploadEnabled() && (SETTINGS.predictiveUpload()
                || Boolean.getBoolean("realscale.experimental.client.uploadbudget.predictive"));
    }

    public static boolean undergroundLodEnabled() {
        return Boolean.getBoolean("realscale.experimental.client.underground_lod_culling")
                || usesSmoothRender(ACTIVE);
    }

    private static void reportIfRequested() {
        if (!(Boolean.getBoolean("realscale.performance.report") || SETTINGS.diagnostics())
                || !REPORTED.compareAndSet(false, true)) return;
        long bytes = estimatedCacheArrayBytesOneSet(ACTIVE);
        System.err.println("[RealScale performance] profile="
                + ACTIVE.name().toLowerCase(Locale.ROOT)
                + " natural_automate_cache_array_bytes_one_worker=" + bytes
                + " natural_automate_cache_array_mib_one_worker="
                + String.format(Locale.ROOT, "%.3f", bytes / (1024.0 * 1024.0))
                + " note=approximate_arrays_only_excludes_jvm_objects_density_instances_and_other_mods");
    }
}
