package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RealScaleDomainManagerTest {
    @Test void oneOverworldExposureClassifierMapsToTwoInternalDomains() {
        assertEquals(RealScaleDomainManager.Domain.SURFACE,
                RealScaleDomainManager.fromVisibilityMode(
                        AdaptiveVisibilityBudget.Mode.OPEN_SURFACE));
        assertEquals(RealScaleDomainManager.Domain.UNDERGROUND,
                RealScaleDomainManager.fromVisibilityMode(
                        AdaptiveVisibilityBudget.Mode.SEALED_CAVE));
        assertEquals(RealScaleDomainManager.Domain.TRANSITION,
                RealScaleDomainManager.fromVisibilityMode(
                        AdaptiveVisibilityBudget.Mode.TRANSITION));
        assertEquals(RealScaleDomainManager.Domain.TRANSITION,
                RealScaleDomainManager.fromVisibilityMode(null));
    }

    @Test void domainPrioritiesNeverDemotePlayerVisibleWork() {
        for(RealScaleDomainManager.Domain domain:RealScaleDomainManager.Domain.values())
            assertEquals(100,RealScaleDomainManager.priority(domain,
                    RealScaleDomainManager.Work.PLAYER_VISIBLE));
    }

    @Test void surfaceAndUndergroundPreferTheirOwnNearbyGeometry() {
        assertTrue(RealScaleDomainManager.priority(
                RealScaleDomainManager.Domain.SURFACE,
                RealScaleDomainManager.Work.NEAR_SURFACE_MESH)
                > RealScaleDomainManager.priority(
                RealScaleDomainManager.Domain.SURFACE,
                RealScaleDomainManager.Work.NEAR_UNDERGROUND_MESH));
        assertTrue(RealScaleDomainManager.priority(
                RealScaleDomainManager.Domain.UNDERGROUND,
                RealScaleDomainManager.Work.NEAR_UNDERGROUND_MESH)
                > RealScaleDomainManager.priority(
                RealScaleDomainManager.Domain.UNDERGROUND,
                RealScaleDomainManager.Work.NEAR_SURFACE_MESH));
    }

    @Test void uncertainTransitionsNeverCullCaveLodsOrThrottleUploads() {
        assertFalse(RealScaleDomainManager.shouldCullDistantHorizonCaves(
                RealScaleDomainManager.Domain.TRANSITION));
        assertFalse(RealScaleDomainManager.shouldCullDistantHorizonCaves(
                RealScaleDomainManager.Domain.UNDERGROUND));
        assertEquals(0,RealScaleDomainManager.uploadMultiplier(
                RealScaleDomainManager.Domain.TRANSITION,100,false));
    }

    @Test void undergroundGetsBoundedUploadBoostAndFramePressureRemovesIt() {
        assertEquals(1,RealScaleDomainManager.uploadMultiplier(
                RealScaleDomainManager.Domain.SURFACE,0,false));
        assertEquals(2,RealScaleDomainManager.uploadMultiplier(
                RealScaleDomainManager.Domain.UNDERGROUND,0,false));
        assertEquals(2,RealScaleDomainManager.uploadMultiplier(
                RealScaleDomainManager.Domain.SURFACE,64,false));
        assertEquals(1,RealScaleDomainManager.uploadMultiplier(
                RealScaleDomainManager.Domain.UNDERGROUND,64,true));
    }
}
