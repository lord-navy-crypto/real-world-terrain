package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts the complete, authentic 500m source-backed V2 GIS plan is bundled
 * in the player JAR, DEM-compatible and safe to load OFFLINE. No user shell,
 * command scripts, server or external plan download is required.
 */
class ChongqingGisPlanTest {
    @Test void loadsPinnedRealOSMPlanFromClasspath() throws Exception {
        ChongqingGisPlan plan=ChongqingGisPlan.readBundled();
        assertEquals(34_063,plan.rows.size());
        assertEquals(494_988L,plan.blockWrites);
        assertEquals("04a380b144d3f002c0157d841edefb68d5a5bfacf6ca07149c43e8f03a858980",
                     plan.planHash);
        assertEquals("9816d76beb4579e8c7289cce52fdd3f216e323c2e2a320562654005717494657",
                     plan.sourceHash);
        assertEquals(500,plan.radius);
        assertTrue(plan.attribution.contains("OpenStreetMap"));
        assertTrue(plan.rows.stream().anyMatch(r -> "facade".equals(r.shell())));
        assertTrue(plan.rows.stream().anyMatch(r -> "roof".equals(r.shell())));
        assertTrue(plan.rows.stream().anyMatch(r -> "water_reference".equals(r.kind())));
        assertTrue(plan.rows.stream().allMatch(r -> r.writes() > 0));
        assertEquals(plan.blockWrites, plan.rows.stream().mapToLong(
                ChongqingGisPlan.Row::writes).sum());
    }

    @Test void absentUserOverrideDoesNotReplacePinnedBuiltInSource() throws Exception {
        assertThrows(IOException.class,() -> ChongqingGisPlan.read(
                Path.of("nonexistent-realscale-gis-plan-file.json")));
        assertEquals(494_988L,ChongqingGisPlan.readBundled().blockWrites);
    }

    @Test void roofInteriorSkipsEmptyLevelsAndFacadeWritesAllBoundaryLevels() {
        ChongqingGisPlan.Row interior=new ChongqingGisPlan.Row(
                0,10,500,0,"building_footprint",250,"roof");
        ChongqingGisPlan.Row facade=new ChongqingGisPlan.Row(
                0,10,500,0,"building_footprint",250,"facade");
        assertEquals(22,interior.writes());
        assertEquals(2761,facade.writes());
        assertEquals(250,interior.nextOffsetAfter(0));
        assertEquals(251,interior.nextOffsetAfter(250));
        assertEquals(1,facade.nextOffsetAfter(0));
        assertEquals(250,facade.nextOffsetAfter(249));
    }
}
