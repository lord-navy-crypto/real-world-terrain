package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpawnAltitudeMathTest {
    @Test
    void targetAltitudeIsDeterministicAndSeedVaried() {
        int first=SpawnAltitudeMath.targetAltitude("tva_natural_smoothed",63,42L);
        int repeat=SpawnAltitudeMath.targetAltitude("tva_natural_smoothed",63,42L);
        int other=SpawnAltitudeMath.targetAltitude("tva_natural_smoothed",63,43L);
        assertEquals(first,repeat);
        assertTrue(first>=127 && first<=159);
        assertTrue(other>=127 && other<=159);
    }

    @Test
    void presetProfilesUseDifferentMidLowTargets() {
        int natural=SpawnAltitudeMath.targetAltitude("tva_natural_smoothed",63,7L);
        int alpine=SpawnAltitudeMath.targetAltitude("tva_alpine_smoothed",63,7L);
        int canyon=SpawnAltitudeMath.targetAltitude("tva_canyon_smoothed",63,7L);
        int measured=SpawnAltitudeMath.targetAltitude("usgs_big_sur_v2",63,7L);
        assertTrue(alpine>natural);
        assertTrue(natural>measured);
        assertTrue(measured>canyon);
        for(int target:new int[]{natural,alpine,canyon,measured})
            assertTrue(target>=111 && target<=167);
    }

    @Test
    void scorePrefersTargetAltitudeAndPenalizesHighMountainsAndWaterline() {
        int target=145,sea=63;
        double near=SpawnAltitudeMath.candidateScore(148,target,sea,64,-64,2032);
        double far=SpawnAltitudeMath.candidateScore(350,target,sea,32,-64,2032);
        double submerged=SpawnAltitudeMath.candidateScore(65,target,sea,0,-64,2032);
        assertTrue(near<far);
        assertTrue(near<submerged);
    }

    @Test
    void distanceBreaksEqualAltitudeTiesWithoutDominatingAltitude() {
        double near=SpawnAltitudeMath.candidateScore(145,145,63,32,-64,2032);
        double far=SpawnAltitudeMath.candidateScore(145,145,63,256,-64,2032);
        assertTrue(near<far);
        double lowNear=SpawnAltitudeMath.candidateScore(155,145,63,32,-64,2032);
        assertTrue(lowNear<far);
    }
}
