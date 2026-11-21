package org.realscale.neoforge;

/**
 * Internal Surface / Underground domain manager for one registered Overworld.
 *
 * <p>It does not create another DimensionType, chunk store, coordinate space,
 * or renderer queue. It maps the conservative exposure classifier to
 * domain-specific work policy; uncertain boundaries always remain TRANSITION.
 */
public final class RealScaleDomainManager {
    public enum Domain { TRANSITION, SURFACE, UNDERGROUND }

    public enum Work {
        PLAYER_VISIBLE,
        NEAR_SURFACE_MESH,
        NEAR_UNDERGROUND_MESH,
        DISTANT_SURFACE_MESH,
        OPTIONAL_HORIZON_LOD,
        BACKGROUND_MAINTENANCE
    }

    private RealScaleDomainManager() {}

    public static Domain fromVisibilityMode(AdaptiveVisibilityBudget.Mode mode) {
        if (mode == null) return Domain.TRANSITION;
        return switch (mode) {
            case OPEN_SURFACE -> Domain.SURFACE;
            case SEALED_CAVE -> Domain.UNDERGROUND;
            case TRANSITION -> Domain.TRANSITION;
        };
    }

    /** Larger values are advisory urgency scores, not a command to cull work. */
    public static int priority(Domain domain, Work work) {
        if (work == Work.PLAYER_VISIBLE) return 100;
        if (domain == null) domain = Domain.TRANSITION;
        return switch (domain) {
            case SURFACE -> switch (work) {
                case NEAR_SURFACE_MESH, DISTANT_SURFACE_MESH,
                        OPTIONAL_HORIZON_LOD -> 75;
                case NEAR_UNDERGROUND_MESH -> 35;
                case BACKGROUND_MAINTENANCE -> 20;
                case PLAYER_VISIBLE -> 100;
            };
            case UNDERGROUND -> switch (work) {
                case NEAR_UNDERGROUND_MESH -> 75;
                case NEAR_SURFACE_MESH, DISTANT_SURFACE_MESH,
                        OPTIONAL_HORIZON_LOD -> 35;
                case BACKGROUND_MAINTENANCE -> 20;
                case PLAYER_VISIBLE -> 100;
            };
            case TRANSITION -> switch (work) {
                case NEAR_SURFACE_MESH, NEAR_UNDERGROUND_MESH,
                        DISTANT_SURFACE_MESH -> 65;
                case OPTIONAL_HORIZON_LOD -> 40;
                case BACKGROUND_MAINTENANCE -> 20;
                case PLAYER_VISIBLE -> 100;
            };
        };
    }

    /** Only confidently open surface may hide DH's distant cave LOD geometry. */
    public static boolean shouldCullDistantHorizonCaves(Domain domain) {
        return domain == Domain.SURFACE;
    }

    /**
     * Per-frame throughput multiplier for the existing FIFO upload queue.
     * Zero means TRANSITION bypasses the optional limiter and uses vanilla.
     */
    public static int uploadMultiplier(Domain domain, int pendingUploads,
            boolean framePressure) {
        if (domain == null || domain == Domain.TRANSITION) return 0;
        int multiplier = domain == Domain.UNDERGROUND ? 2 : 1;
        if (pendingUploads >= 64) multiplier = Math.max(2, multiplier);
        if (framePressure) multiplier = 1;
        return multiplier;
    }
}
