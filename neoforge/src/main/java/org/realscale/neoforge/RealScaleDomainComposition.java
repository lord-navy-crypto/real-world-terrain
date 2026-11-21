package org.realscale.neoforge;

/**
 * The single-Overworld composition boundary between two internal world domains.
 *
 * <p>The surface density graph remains authoritative for terrain and the
 * underground graph contributes subtractive cave space. They are evaluated
 * independently, then composed into the one density field Minecraft stores
 * in the shared Overworld chunks. The surface roof guard is deliberately
 * conservative and preserves the existing RealScale cave contract.
 */
public final class RealScaleDomainComposition {
    static final double SURFACE_ROOF_PROTECTION_DENSITY = 0.24;
    static final double GIANT_CAVE_DENSITY_SCALE = 2.4;

    private RealScaleDomainComposition() {}

    /**
     * Compose the surface domain with small-cave and giant-underground-domain
     * contributions. A protected surface/sea-floor density is returned bit
     * for bit unchanged.
     */
    public static double compose(double surfaceDensity,
            double smallCaveDensity, double undergroundCaveStrength) {
        if (surfaceDensity <= SURFACE_ROOF_PROTECTION_DENSITY)
            return surfaceDensity;
        double result = surfaceDensity;
        if (smallCaveDensity < 0.0)
            result = Math.min(result, smallCaveDensity);
        if (undergroundCaveStrength > 0.0)
            result = Math.min(result,
                    -GIANT_CAVE_DENSITY_SCALE * undergroundCaveStrength);
        return result;
    }
}
