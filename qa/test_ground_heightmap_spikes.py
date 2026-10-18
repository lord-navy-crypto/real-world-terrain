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
    adjacent_height_deltas, isolated_height_impulses, terrain_quality_gates,
    underlying_ground_below_stem
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

    def test_hanging_vines_are_not_mistaken_for_ground_spikes(self):
        # Vanilla vines can enter MOTION_BLOCKING_NO_LEAVES. Descend through
        # persisted vegetation and air, stopping at the first true support.
        blocks={y:"minecraft:vine" for y in range(80,94)}
        blocks.update({79:"minecraft:air",78:"minecraft:jungle_leaves",
                       71:"minecraft:grass_block",70:"minecraft:dirt"})
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:blocks.get(y,"minecraft:air")):
            self.assertEqual(underlying_ground_below_stem({},0,0,93,-64),
                             (71,"minecraft:grass_block",True))

    def test_vine_over_real_stone_spike_remains_a_real_cliff(self):
        # Fix the measurement, not the threshold: a vine over a genuine
        # isolated stone tower must still be counted as a geological spike.
        blocks={100:"minecraft:vine",99:"minecraft:air",98:"minecraft:stone",
                97:"minecraft:stone"}
        with patch("check_realism_continuity.get_block",
                   side_effect=lambda chunk,dx,y,dz:blocks.get(y,"minecraft:air")):
            h,material,corrected=underlying_ground_below_stem({},0,0,100,-64)
        self.assertEqual((h,material,corrected),(98,"minecraft:stone",True))
        heights={(0,0):h,(-1,0):72,(1,0):72,(0,-1):72,(0,1):72}
        surfaces={p:"minecraft:grass_block" for p in heights}
        surfaces[(0,0)]=material
        self.assertEqual(isolated_height_impulses(heights,surfaces,{})["count"],1)

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

    def test_distant_canopy_must_not_trigger_chunk_seam(self):
        # Negative coordinates are deliberate. A 16-block seam at -8192
        # is exactly as real as the same seam near spawn.
        ground={(x,z):72 for x in range(-8209,-8175)
                          for z in range(1024,1058)}
        visible=dict(ground)
        for z in range(1024,1058):
            visible[(-8192,z)]=117  # canopy/log/long hanging vine
        raw=adjacent_height_deltas(visible)
        actual=adjacent_height_deltas(ground)
        self.assertTrue(raw[3] and max(raw[3])==45)
        self.assertTrue(actual[3] and max(actual[3])==0)
        self.assertTrue(terrain_quality_gates(
            actual[3],actual[4],actual[1],actual[2],0,len(ground),0)["pass"])

    def test_actual_distant_chunk_wall_still_fails_without_weakening_gate(self):
        # At x=8192, height discontinuity crosses a real chunk boundary
        # in every z row. A high mean seam ratio must remain a failure.
        heights={(x,z):(240 if x>=8192 else 75)
                 for x in range(8180,8205) for z in range(-350,351)}
        delta,grid_edge,grid_inner,edge,inner=adjacent_height_deltas(heights)
        quality=terrain_quality_gates(edge,inner,grid_edge,grid_inner,
                                      0,len(heights),0)
        self.assertTrue(edge)
        self.assertGreater(max(edge),160)
        self.assertFalse(quality["pass"])
        self.assertIn("Systematic 16-block chunk-border height seam",
                      quality["issues"])

    def test_unloaded_far_chunks_are_not_fabricated_into_seams(self):
        # A sparse Anvil snapshot may contain independent islands of chunks.
        # Do not compare unrelated x/z columns across empty space.
        values={(0,0):70,(1,0):72,(32768,-32768):151,
                (32769,-32768):153,(-32768,32768):90}
        delta,grid_edge,grid_inner,chunk_edge,chunk_inner=(
            adjacent_height_deltas(values))
        self.assertEqual(sorted(delta),[2,2])
        self.assertEqual(sum(len(v) for v in (chunk_edge,chunk_inner)),2)

    def test_original_terrain_impulse_threshold_is_preserved(self):
        # The existing acceptance threshold must not be relaxed to get green CI.
        results=terrain_quality_gates([],[],[],[],19,43264,0)
        self.assertFalse(results["pass"])
        self.assertEqual(results["isolated_spikes"],19)
        self.assertIn("Excess isolated 4-neighbor spikes or one-block pits",
                      results["issues"])


if __name__=="__main__":
    unittest.main()
