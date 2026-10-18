package org.realscale.neoforge;

/**
 * Pure Java classification + advisory work-priority policy for the 2096-high
 * RealScale client. This is NOT a renderer or a request to load any chunks.
 *
 * No Y-only clipping: exposure rather than altitude decides the mode.
 * UNKNOWN/ambiguous observations immediately return to conservative mode,
 * and a different confident mode requires repeated consistent observations.
 * Priorities are hints for a possible FUTURE supported renderer integration.
 * RealScale does not currently modify Sodium/vanilla render queues.
 */
public final class AdaptiveVisibilityBudget {
    private AdaptiveVisibilityBudget() {}

    public enum Mode { TRANSITION, OPEN_SURFACE, SEALED_CAVE }
    public enum Task {
        VISIBLE_OR_PLAYER_CHANGED,
        NEAR_CAVE_MESH,
        DISTANT_SURFACE_MESH,
        OPTIONAL_HORIZON_LOD,
        BACKGROUND_MAINTENANCE
    }

    /** All fields are from ALREADY LOADED local client positions only. */
    public record Observation(
            boolean known,
            boolean skyAtCamera,
            boolean nearbySkyAccess,
            boolean solidRoofEvidence,
            boolean openingOrRavine,
            boolean rapidlyMoving,
            boolean exteriorSightline) {
        /** Preserve Stage-1 source compatibility for existing callers. */
        public Observation(boolean known, boolean skyAtCamera,
                boolean nearbySkyAccess, boolean solidRoofEvidence,
                boolean openingOrRavine, boolean rapidlyMoving) {
            this(known, skyAtCamera, nearbySkyAccess, solidRoofEvidence,
                    openingOrRavine, rapidlyMoving, false);
        }
    }

    /** Observational interpretation; no vision ray may be skipped. */
    public static Mode classify(Observation o) {
        if (o == null || !o.known() || o.openingOrRavine()
                || o.rapidlyMoving() || o.exteriorSightline())
            return Mode.TRANSITION;
        if (o.skyAtCamera() && o.nearbySkyAccess() && !o.solidRoofEvidence())
            return Mode.OPEN_SURFACE;
        if (!o.skyAtCamera() && !o.nearbySkyAccess() && o.solidRoofEvidence())
            return Mode.SEALED_CAVE;
        return Mode.TRANSITION;
    }

    /**
     * Larger scores are more urgent. All tasks remain eligible; no result
     * implies that a section is invisible or may be omitted from rendering.
     * This is advisory only until an explicitly supported renderer interface
     * can honor it without delaying player-visible rebuilds.
     */
    public static int priority(Mode mode, Task task) {
        if (task == Task.VISIBLE_OR_PLAYER_CHANGED) return 100;
        return switch (mode) {
            case OPEN_SURFACE -> switch (task) {
                case DISTANT_SURFACE_MESH, OPTIONAL_HORIZON_LOD -> 75;
                case NEAR_CAVE_MESH -> 35;
                case BACKGROUND_MAINTENANCE -> 20;
                default -> 100;
            };
            case SEALED_CAVE -> switch (task) {
                case NEAR_CAVE_MESH -> 75;
                case DISTANT_SURFACE_MESH, OPTIONAL_HORIZON_LOD -> 35;
                case BACKGROUND_MAINTENANCE -> 20;
                default -> 100;
            };
            case TRANSITION -> switch (task) {
                case NEAR_CAVE_MESH, DISTANT_SURFACE_MESH -> 65;
                case OPTIONAL_HORIZON_LOD -> 40;
                case BACKGROUND_MAINTENANCE -> 20;
                default -> 100;
            };
        };
    }

    /**
     * Low-CPU adaptive cadence. The output is only an observation interval:
     * it never controls what Minecraft draws, sends, meshes, or unloads.
     * Stable stationary scenes can sample less often; a moving player or
     * an exposed/uncertain exit is resampled at least once per half-second.
     *
     * squaredBlockDisplacement is measured from the LAST known observation,
     * not camera Y. Teleports are separately fail-open invalidated by client.
     */
    public static int samplingIntervalTicks(Mode mode,
            double squaredBlockDisplacement) {
        if (mode == Mode.TRANSITION || squaredBlockDisplacement > 16.0)
            return 10;
        return 40; // at 20 TPS, 2s when stable and stationary
    }

    /** Three consecutive confident observations; usually sampled once/sec. */
    public static final int REQUIRED_CONFIDENT_SAMPLES = 3;

    public static final class Tracker {
        private Mode mode = Mode.TRANSITION;
        private Mode candidate = Mode.TRANSITION;
        private int consecutive;
        private long samples;
        private long transitions;

        public Mode mode() { return mode; }
        public long samples() { return samples; }
        public long transitions() { return transitions; }

        public Mode observe(Observation observation) {
            samples++;
            Mode next = classify(observation);
            if (next == Mode.TRANSITION) {
                // Fail open: an exit, skylight or unknown data cannot wait
                // for hysteresis; never deprioritize a possibly visible side.
                if (mode != Mode.TRANSITION) transitions++;
                mode = Mode.TRANSITION;
                candidate = Mode.TRANSITION;
                consecutive = 0;
                return mode;
            }
            if (next != candidate) {
                candidate = next;
                consecutive = 1;
                if (mode != next && mode != Mode.TRANSITION) {
                    mode = Mode.TRANSITION;
                    transitions++;
                }
            } else if (consecutive < REQUIRED_CONFIDENT_SAMPLES) {
                consecutive++;
            }
            if (consecutive >= REQUIRED_CONFIDENT_SAMPLES && mode != next) {
                mode = next;
                transitions++;
            }
            return mode;
        }

        public void reset() {
            mode = Mode.TRANSITION;
            candidate = Mode.TRANSITION;
            consecutive = 0;
            samples = 0;
            transitions = 0;
        }
    }
}
