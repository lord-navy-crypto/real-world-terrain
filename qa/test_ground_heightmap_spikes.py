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
from unittest.mock import patch
from check_realism_continuity import (
    isolated_height_impulses, terrain_quality_gates, underlying_ground_below_stem
)


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

    def test_actual_saved_tree_trunks_no_longer_count_as_ground_cliffs(self):
        # Persisted jungle columns contain trunk blocks for 10+ layers,
        # even in MOTION_BLOCKING_NO_LEAVES (it ignores leaves, not logs).
        blocks={y:"minecraft:jungle_log" for y in range(72,94)}
        blocks.update({71:"minecraft:grass_block",70:"minecraft:dirt"})
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:blocks.get(y,"minecraft:air")):
            h,block,corrected=underlying_ground_below_stem({},0,0,93,-64)
        self.assertEqual((h,block,corrected),
                         (71,"minecraft:grass_block",True))
        points=[(0,0),(-1,0),(1,0),(0,-1),(0,1)]
        ground={(x,z):71 for x,z in points}
        ground[(0,0)]=h
        materials={p:"minecraft:grass_block" for p in points}
        self.assertEqual(isolated_height_impulses(
            ground,materials,{})["count"],0)

    def test_bamboo_with_air_between_stalk_segments(self):
        blocks={y:"minecraft:bamboo" for y in range(82,96)}
        blocks[77]="minecraft:grass_block"
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:blocks.get(y,"minecraft:air")):
            self.assertEqual(underlying_ground_below_stem({},0,0,95,-64),
                             (77,"minecraft:grass_block",True))

    def test_stone_spike_never_removed_by_vegetation_normalization(self):
        blocks={92:"minecraft:stone",91:"minecraft:stone"}
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:blocks.get(y,"minecraft:air")):
            self.assertEqual(underlying_ground_below_stem({},0,0,92,-64),
                             (92,"minecraft:stone",False))
        heights={(0,0):92,(-1,0):72,(1,0):72,(0,-1):72,(0,1):72}
        surface={p:"minecraft:stone" for p in heights}
        self.assertEqual(isolated_height_impulses(heights,surface,{})["count"],1)

    def test_unknown_block_and_missing_support_cannot_hide_cliffs(self):
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:(
                       "minecraft:oak_log" if y==93 else
                       "INVALID_PALETTE_INDEX" if y==92 else "minecraft:air")):
            self.assertEqual(underlying_ground_below_stem({},0,0,93,-64),
                             (93,"minecraft:oak_log",False))
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:(
                       "minecraft:oak_log" if y==200 else "minecraft:air")):
            self.assertEqual(underlying_ground_below_stem({},0,0,200,-64),
                             (200,"minecraft:oak_log",False))

    def test_original_terrain_impulse_threshold_is_preserved(self):
        # The existing acceptance threshold must not be relaxed to get green CI.
        results=terrain_quality_gates([],[],[],[],19,43264,0)
        self.assertFalse(results["pass"])
        self.assertEqual(results["isolated_spikes"],19)
        self.assertIn("Excess isolated 4-neighbor spikes or one-block pits",
                      results["issues"])


if __name__=="__main__":
    unittest.main()
