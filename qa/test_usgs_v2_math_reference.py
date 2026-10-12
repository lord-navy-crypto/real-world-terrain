#!/usr/bin/env python3
"""Cross-check measured USGS interpolation without Minecraft classpath."""
import math
import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"qa/neoforge"))
from usgs_v2_math_reference import MonotoneDem
from check_actual_chunk_heights import read_dem

class MonotoneUSGSMathTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source=next((ROOT/"neoforge/src/main/resources/data/realscale/dem").glob("*.rgt.gz"))
        cls.dem=MonotoneDem(source)
        cls.legacy,cls.bounds=read_dem(source)

    def test_exactly_preserves_every_measured_grid_point(self):
        dem=self.dem
        self.assertEqual(self.bounds,(dem.x0,dem.z0,dem.maxx,dem.maxz))
        for i in range(16):
            for j in range(16):
                x=dem.x0+32*j;z=dem.z0+32*i
                self.assertAlmostEqual(dem.measured_height(x,z),dem.grid[i][j],places=6)
                self.assertAlmostEqual(dem.measured_height(x,z),self.legacy(x,z),places=6)

    def test_monotone_cubic_never_overshoots_four_cell_corners(self):
        dem=self.dem
        for row in range(15):
            for col in range(15):
                vals=[dem.grid[row][col],dem.grid[row][col+1],
                      dem.grid[row+1][col],dem.grid[row+1][col+1]]
                for dz in (0,8,16,24,32):
                    for dx in (0,8,16,24,32):
                        actual=dem.measured_height(dem.x0+col*32+dx,
                                                   dem.z0+row*32+dz)
                        self.assertGreaterEqual(actual,min(vals)-1e-6)
                        self.assertLessEqual(actual,max(vals)+1e-6)

    def test_extended_slopes_are_continuous_at_all_four_sides(self):
        d=self.dem
        for z in (d.z0,(d.z0+d.maxz)/2,d.maxz):
            for x in (d.x0,d.maxx):
                v=d.extended_height(x,z)
                delta=(-0.01 if x==d.x0 else 0.01)
                self.assertLess(abs(v-d.extended_height(x+delta,z)),0.1)
        for x in (d.x0,(d.x0+d.maxx)/2,d.maxx):
            for z in (d.z0,d.maxz):
                v=d.extended_height(x,z)
                delta=(-0.01 if z==d.z0 else 0.01)
                self.assertLess(abs(v-d.extended_height(x,z+delta)),0.1)

    def test_corner_slopes_are_independent_on_both_axes(self):
        # The corner extrapolation must use the SAME measured base for both
        # axis gradients. Using the X-updated height to compute Z is wrong.
        d=self.dem
        for cx in (d.x0,d.maxx):
            for cz in (d.z0,d.maxz):
                dx=-64 if cx==d.x0 else 64
                dz=-64 if cz==d.z0 else 64
                direction_x=32 if dx<0 else -32
                direction_z=32 if dz<0 else -32
                base=d.measured_height(cx,cz)
                sx=d.clip((d.measured_height(cx+direction_x,cz)-base)/
                          direction_x,-1.25,1.25)
                sz=d.clip((d.measured_height(cx,cz+direction_z)-base)/
                          direction_z,-1.25,1.25)
                expected=d.clip(base+sx*d.taper(dx)+sz*d.taper(dz),-64,2031)
                with self.subTest(cx=cx,cz=cz):
                    self.assertAlmostEqual(d.extended_height(cx+dx,cz+dz),
                                           expected,places=8)

    def test_four_sides_exterior_transition_reaches_procedural_region(self):
        d=self.dem
        cx=(d.x0+d.maxx)/2;cz=(d.z0+d.maxz)/2
        probes=((d.x0,cz,-1,0),(d.maxx,cz,1,0),
                (cx,d.z0,0,-1),(cx,d.maxz,0,1))
        for x,z,sx,sz in probes:
            self.assertEqual(d.weight(x,z),1.0)
            midpoint=d.weight(x+sx*1024,z+sz*1024)
            self.assertAlmostEqual(midpoint,0.5)
            self.assertEqual(d.weight(x+sx*2048,z+sz*2048),0.0)
            for t in range(0,2049,8):
                e=d.extended_height(x+sx*t,z+sz*t)
                self.assertTrue(math.isfinite(e) and -64<=e<=2031)

if __name__=="__main__":
    unittest.main()
