package org.realscale.neoforge;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One control plane for RealScale-owned cache sizing and guarded client rendering experiments.
 *
 * Profiles are opt-in. "balanced" preserves the existing cache capacities and client defaults.
 * Cache eviction only causes the original calculation to run again; it never changes terrain data.
 */
public final class RealScalePerformanceProfile {
    public enum Profile {
        BALANCED,
        LOW_MEMORY,
        SMOOTH_RENDER,
        LOW_MEMORY_SMOOTH_RENDER
    }

    private static final Profile ACTIVE = parseProfile(
            System.getProperty("realscale.performance.profile", "balanced"));
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private RealScalePerformanceProfile() {}

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

    /**
     * Select a bounded power-of-two capacity. The compact profile uses one quarter
     * of the existing slots (minimum 16); all other profiles preserve current defaults.
     */
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

    /** Approximate primitive/reference-array bytes for one instance of each major cache type. */
    static long estimatedCacheArrayBytesOneSet(Profile profile) {
        long demSlots = cacheSlots(profile, "real_dem_column", 4096);
        long measuredSlots = cacheSlots(profile, "measured_horizontal", 8192);
        long biomeBaseSlots = cacheSlots(profile, "biome_base", 8192);
        long biomeResultSlots = cacheSlots(profile, "biome_result", 2048);
        long reliefSlots = cacheSlots(profile, "mountain_relief", 64);
        long skySlots = cacheSlots(profile, "sky_island", 256);

        long dem = demSlots * (Long.BYTES + Integer.BYTES + Long.BYTES);
        long measured = measuredSlots * (Long.BYTES + Double.BYTES + 1L)
                + measuredSlots / 2L;
        long biomeBase = biomeBaseSlots * (Long.BYTES + Long.BYTES)
                + biomeBaseSlots / 2L;
        long biomeResult = biomeResultSlots * (Long.BYTES + Long.BYTES)
                + biomeResultSlots / 2L;
        long relief = reliefSlots * Long.BYTES + reliefSlots / 2L;
        long sky = skySlots * Long.BYTES + skySlots / 2L;
        return dem + measured + biomeBase + biomeResult + relief + sky;
    }

    public static boolean clientUploadBudgetEnabled() {
        return Boolean.getBoolean("realscale.experimental.client.uploadbudget")
                || usesSmoothRender(ACTIVE);
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

    /** Predictive FIFO pacing remains a separate explicit experiment. */
    public static boolean predictiveUploadEnabled() {
        return modeAwareUploadEnabled()
                && Boolean.getBoolean("realscale.experimental.client.uploadbudget.predictive");
    }

    public static boolean undergroundLodEnabled() {
        return Boolean.getBoolean("realscale.experimental.client.underground_lod_culling")
                || usesSmoothRender(ACTIVE);
    }

    private static void reportIfRequested() {
        if (!Boolean.getBoolean("realscale.performance.report")
                || !REPORTED.compareAndSet(false, true)) return;
        long bytes = estimatedCacheArrayBytesOneSet(ACTIVE);
        System.err.println("[RealScale performance] profile=" + ACTIVE.name().toLowerCase(Locale.ROOT)
                + " cache_array_bytes_one_set=" + bytes
                + " cache_array_mib_one_set="
                + String.format(Locale.ROOT, "%.3f", bytes / (1024.0 * 1024.0))
                + " note=approximate_arrays_only_excludes_value_objects_and_additional_density_instances");
    }
}
