"""Exact-contract checks for cave/marine feature hot path.

No benchmark speedup claims. The generated structures, RNG draw order,
worldgen feature registrations and existing save files must remain unchanged.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/CaveMarineHabitatFeature.java").read_text()

class HabitatHotPathTests(unittest.TestCase):
    def test_signed_chunk_shift_matches_floor_div_for_full_int_range_edges(self):
        edge=[-(2**31),-(2**31)+1,-65537,-65536,-33,-32,-31,-17,-16,
              -15,-1,0,1,15,16,17,31,32,33,65535,65536,2**31-2,2**31-1]
        for x in edge:
            self.assertEqual(x>>4,x//16,x)
            for y in edge:
                self.assertEqual((x>>4)==(y>>4),
                                 (x//16)==(y//16),(x,y))
        self.assertIn('(origin.getX()>>4)==(candidate.getX()>>4)',SOURCE)
        self.assertIn('(origin.getZ()>>4)==(candidate.getZ()>>4)',SOURCE)

    def test_cave_probe_reuses_mutable_position_without_changing_scan(self):
        self.assertIn('final BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();',SOURCE)
        self.assertIn('HabitatFloorSearch.nearest(origin.getY(),min,max,36,',SOURCE)
        self.assertIn('v->cavePocket(w,probe.set(x,v,z))',SOURCE)
        self.assertIn('return y==HabitatFloorSearch.NOT_FOUND ? null',SOURCE)
        self.assertNotIn('v->cavePocket(w,new BlockPos(',SOURCE)

    def test_reporting_is_opt_in_and_has_no_default_increment(self):
        self.assertIn('Boolean.getBoolean("realscale.habitat.report")',SOURCE)
        self.assertIn('if(REPORT_HABITATS)ATTEMPTS[kind.ordinal()].increment();',SOURCE)
        self.assertIn('if(REPORT_HABITATS && changed)PLACEMENTS[kind.ordinal()].increment();',SOURCE)
        self.assertIn('if(REPORT_HABITATS) Runtime.getRuntime().addShutdownHook(',SOURCE)

    def test_original_worldgen_rules_and_rng_paths_remain(self):
        for token in (
            'TVAWorldGuard.hasRealScaleGenerator(context)',
            'w.getLevel().dimension().equals(Level.OVERWORLD)',
            'sameChunk(origin,p)', 'sameChunk(origin,column)',
            'surface-ROOF_DEPTH',
            'GrandOverworldCaveNetwork.MAX_Y',
            'Heightmap.Types.OCEAN_FLOOR_WG',
            'w.getFluidState(bottom).isEmpty()',
            'if(!cavePocket(w,p))continue;',
            'random.nextInt(MAX_RADIUS*2+1)',
            'random.nextInt(11)-5',
            'if(w.setBlock(water,plant,2))changed++;',
        ):
            self.assertIn(token,SOURCE,token)

if __name__ == '__main__':
    unittest.main()
