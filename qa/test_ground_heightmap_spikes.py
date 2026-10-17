#!/usr/bin/env python3
"""Ground-surface quality regressions: canopy is not a terrain pit.

Synthetic persisted heightmap fixtures are deliberately separate from the
real NeoForge Anvil gate, which must still pass before Sacred Timeline merge.
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from realscale_headless_qa import surface_height
from check_realism_continuity import isolated_height_impulses,terrain_quality_gates


class SavedGroundHeightmapTest(unittest.TestCase):
    def test_read_explicit_heightmap_without_canopy_fallback(self):
        # y = min_y + packed_height - 1; 2096-high terrain encodes 12 bits.
        chunk={"Heightmaps":{
            "WORLD_SURFACE":[100-(-64)+1],
            "MOTION_BLOCKING_NO_LEAVES":[71-(-64)+1],
        }}
        self.assertEqual(surface_height(chunk,0,0,-64,2096),100)
        self.assertEqual(surface_height(chunk,0,0,-64,2096,
                         "MOTION_BLOCKING_NO_LEAVES"),71)
        self.assertIsNone(surface_height(chunk,0,0,-64,2096,"OCEAN_FLOOR"))

    def test_leaf_canopy_gap_does_not_fail_ground_spike_gate(self):
        points=[(0,0),(-1,0),(1,0),(0,-1),(0,1)]
        visible={(x,z):(70 if (x,z)==(0,0) else 101) for x,z in points}
        ground={(x,z):(70 if (x,z)==(0,0) else 71) for x,z in points}
        blocks={p:"minecraft:grass_block" for p in points}
        biomes={p:"minecraft:jungle" for p in points}
        self.assertEqual(isolated_height_impulses(
            visible,blocks,biomes)["count"],1)
        self.assertEqual(isolated_height_impulses(
            ground,blocks,biomes)["count"],0)

    def test_true_ground_pit_is_still_rejected(self):
        points=[(0,0),(-1,0),(1,0),(0,-1),(0,1)]
        ground={(x,z):(70 if (x,z)==(0,0) else 95) for x,z in points}
        blocks={p:"minecraft:stone" for p in points}
        biomes={p:"minecraft:stony_peaks" for p in points}
        observed=isolated_height_impulses(ground,blocks,biomes)
        self.assertEqual(observed["count"],1)
        self.assertEqual(observed["blocks"],{"minecraft:stone":1})
        self.assertEqual(observed["examples"][0]["height"],70)

    def test_original_terrain_impulse_threshold_is_preserved(self):
        # The existing acceptance threshold must not be relaxed to get green CI.
        results=terrain_quality_gates([],[],[],[],19,43264,0)
        self.assertFalse(results["pass"])
        self.assertEqual(results["isolated_spikes"],19)
        self.assertIn("Excess isolated 4-neighbor spikes or one-block pits",
                      results["issues"])


if __name__=="__main__":
    unittest.main()
