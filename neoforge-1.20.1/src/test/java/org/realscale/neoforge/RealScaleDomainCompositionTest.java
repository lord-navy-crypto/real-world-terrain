package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RealScaleDomainCompositionTest {
    @Test void protectedSurfaceAndSeaFloorRemainBitExact() {
        double surface=0.24;
        double actual=RealScaleDomainComposition.compose(surface,-0.9,1.0);
        assertEquals(Double.doubleToRawLongBits(surface),
                Double.doubleToRawLongBits(actual));
    }

    @Test void undergroundSmallCaveCarvesOnlyBelowTheSurfaceRoofGuard() {
        assertEquals(-0.18,
                RealScaleDomainComposition.compose(0.42,-0.18,0.0));
        assertEquals(0.20,
                RealScaleDomainComposition.compose(0.20,-0.18,0.0));
    }

    @Test void giantUndergroundPassageWinsWhenItsDensityIsStronger() {
        assertEquals(-1.2,
                RealScaleDomainComposition.compose(0.42,-0.18,0.5));
    }

    @Test void noUndergroundContributionLeavesTheSurfaceDomainUnchanged() {
        double surface=0.613;
        assertEquals(Double.doubleToRawLongBits(surface),
                Double.doubleToRawLongBits(
                        RealScaleDomainComposition.compose(surface,0.0,0.0)));
    }
}
