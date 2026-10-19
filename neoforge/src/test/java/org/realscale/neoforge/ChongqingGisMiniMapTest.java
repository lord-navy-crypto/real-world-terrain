package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Preview must only reproduce pinned authentic OSM GIS rows. */
class ChongqingGisMiniMapTest {
    @Test void mapsAllRealSourceCategoriesWithBounded72x72Pixels() throws Exception {
        ChongqingGisPlan plan=ChongqingGisPlan.readBundled();
        ChongqingGisMiniMap map=ChongqingGisMiniMap.of(plan);
        assertTrue(map.occupied()>100,"True OSM source yields a non-empty map");
        assertTrue(map.occupied()<=ChongqingGisMiniMap.SIZE*ChongqingGisMiniMap.SIZE);
        boolean hasBuilding=false,hasRoad=false,hasRail=false,hasWater=false;
        for(int z=0;z<ChongqingGisMiniMap.SIZE;z++) {
            for(int x=0;x<ChongqingGisMiniMap.SIZE;x++) {
                int pixel=map.at(x,z);
                hasBuilding|=(pixel&ChongqingGisMiniMap.BUILDINGS)!=0;
                hasRoad|=(pixel&ChongqingGisMiniMap.ROADS)!=0;
                hasRail|=(pixel&ChongqingGisMiniMap.RAILWAYS)!=0;
                hasWater|=(pixel&ChongqingGisMiniMap.WATER)!=0;
            }
        }
        assertTrue(hasBuilding,"OSM real building layer must appear");
        assertTrue(hasRoad,"OSM real road centerlines must appear");
        assertTrue(hasWater,"OSM real water-reference outlines must appear");
        // An OSM railway can be outside 500m or deferred due to layer tags;
        // do not invent an unverified railway layer to make a test pass.
        assertEquals(hasRail,plan.rows.stream().anyMatch(
                r -> "railway".equals(r.kind())));
        assertThrows(IndexOutOfBoundsException.class,()->map.at(-1,0));
        assertThrows(IndexOutOfBoundsException.class,()->map.at(72,1));
    }
}
