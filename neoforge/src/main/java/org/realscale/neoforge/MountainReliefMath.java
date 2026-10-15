package org.realscale.neoforge;

/**
 * Bounded regional landforms for tall procedural RealScale continents.
 *
 * <p>All site origins are world-absolute, so a single plateau or basin is
 * identical no matter which Minecraft chunk requests it first. Candidate
 * footprints (including rims) remain entirely within their 2048-block site
 * cell; no order-dependent world reads, chunk accesses or block writes.
 *
 * <p>Heights/deltas here are in RealScale's vertical density units (about
 * 128 blocks per unit for the smoothed families), not raw block heights.
 */
final class MountainReliefMath {
    static final int CELL = 2048;
    static final int NONE = 0, BASIN = 1, PLATEAU = 2;
    private MountainReliefMath() {}

    static long mix(long h) {
        h ^= h >>> 30; h *= 0xbf58476d1ce4e5b9L;
        h ^= h >>> 27; h *= 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }

    static long siteHash(int cx, int cz) {
        return mix((long)cx * 0x9e3779b97f4a7c15L
                ^ (long)cz * 0xc2b2ae3d27d4eb4fL
                ^ 0x4d3cdbe3f81907a5L);
    }

    static int centerOffsetX(long h) {
        return (int)Math.floorMod(h >>> 11, 301L) - 150;
    }
    static int centerOffsetZ(long h) {
        return (int)Math.floorMod(h >>> 32, 301L) - 150;
    }
    static double radius(long h) {
        return 470 + Math.floorMod(h >>> 43, 231L);
    }

    static int kind(long h, double seedResolvedSignal) {
        // The hashed geography is stable across chunks, but the existing
        // world-seeded selector shifts site classification among world seeds.
        int perturb = (int)Math.round(GeoMath.clamp(seedResolvedSignal, -1, 1) * 21);
        int chance = Math.floorMod((int)Math.floorMod(h, 100L) + perturb, 100);
        return chance < 32 ? BASIN : chance < 64 ? PLATEAU : NONE;
    }

    static double plateauLevel(long h, double seededSignal) {
        // Fixed for an entire site, from world-absolute hash and world-seeded
        // erosion noise. Never peek at the height interpolator in distant
        // chunks (that is not safe during NoiseChunk interpolation).
        return 2.2 + Math.floorMod(h >>> 19, 800L) * 0.0125
                + GeoMath.clamp(seededSignal, -1, 1) * 0.5;
    }

    static double basinCore(double radiusFraction) {
        return 1.0 - GeoMath.smoothstep(0.25, 0.78, radiusFraction);
    }
    static double raisedRim(double radiusFraction) {
        // Subtle elevated rim; its outer edge fades out well before the
        // site's cell boundary, so it cannot create a seam at x=2048*k.
        return GeoMath.smoothstep(0.62, 0.82, radiusFraction)
                * (1.0 - GeoMath.smoothstep(0.91, 1.10, radiusFraction));
    }
    static double plateauCrest(double radiusFraction) {
        // Broad flat interior with a smooth escarpment/foothill transition.
        return 1.0 - GeoMath.smoothstep(0.52, 1.0, radiusFraction);
    }

    static double inlandUplandGate(double continentalness, double baselineHeight) {
        return GeoMath.smoothstep(-0.10, 0.22, continentalness)
                * GeoMath.smoothstep(0.65, 1.7, baselineHeight);
    }

    static double relief(double original, double anchorHeight, double continent,
                         int kind, double radiusFraction, double basinDepth,
                         double plateauRise) {
        double gate = inlandUplandGate(continent, original);
        if (gate <= 0.0 || radiusFraction >= 1.10 || kind == NONE) return original;
        if (kind == BASIN) {
            double depression = basinDepth * basinCore(radiusFraction);
            double rim = basinDepth * 0.15 * raisedRim(radiusFraction);
            return original + gate * (rim - depression);
        }
        // Plateau's central shelf approaches one height referenced to a
        // site anchor, not a quantized per-voxel value (which causes steps).
        // Existing mountain heights and caves remain intact elsewhere.
        double target = Math.max(anchorHeight, 0.9) + plateauRise;
        // Don't level a 1600m summit down to a 300m shelf just because it
        // happened to occupy the same 2048m cell as a plateau site.
        double heightFit = 1.0 - GeoMath.smoothstep(1.0, 2.4,
                Math.abs(original - target));
        return original + gate * heightFit * plateauCrest(radiusFraction)
                * (target - original);
    }
}
