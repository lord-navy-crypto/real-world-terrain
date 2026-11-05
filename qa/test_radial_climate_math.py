#!/usr/bin/env python3
"""Independent analytic checks of Minecraft radial-latitude geometry."""
import math
import unittest

EARTH_Q=10_001_966

def radial_latitude(x,z,cx,cz,q):
    r=math.hypot(x-cx,z-cz)
    phase=(r/q)%2.0
    return 90.0*(phase if phase<=1 else 2-phase)

def balanced_latitude(radius,q=8192):
    phase=(radius/q)%2
    return 90*(min(phase,2-phase)**1.15)

def balanced_temp_delta(lat,y,cooling=.585,strength=.7):
    def smooth(a,b,x):
        t=max(0,min(1,(x-a)/(b-a)))
        return t*t*(3-2*t)
    tropical=1-smooth(12,37,lat)
    temperate=smooth(18,38,lat)*(1-smooth(47,67,lat))
    return (temp_delta(lat,strength)+strength*(.06*tropical+.13*temperate)
            -cooling*smooth(96,320,y)*(1-.75*tropical-.40*temperate))

def temp_delta(lat,strength=.7):
    polar=math.sin(math.radians(abs(lat)))
    return strength*(.22-.9*(max(polar,0)**1.35))

def cap_delta(d,cold=.60,hot=.45):
    return max(-cold,min(hot,d))

class RadialClimateMathTests(unittest.TestCase):
    def test_balanced_ring_distance_and_symmetry(self):
        self.assertEqual(balanced_latitude(0),0)
        self.assertEqual(balanced_latitude(8192),90)
        self.assertEqual(balanced_latitude(16384),0)
        self.assertAlmostEqual(balanced_latitude(4096),balanced_latitude(12288))
        self.assertLess(balanced_latitude(4096),45)

    def test_tropical_plateau_cools_less_than_polar_mountain(self):
        low=balanced_temp_delta(0,320)
        polar=balanced_temp_delta(90,320)
        self.assertGreater(low,polar)
        self.assertGreater(low,-.10)
        self.assertAlmostEqual(balanced_temp_delta(0,96),temp_delta(0)+.7*.06)

    def test_temperate_buffer_reduces_cooling_without_heating_poles(self):
        self.assertGreater(balanced_temp_delta(40,320),temp_delta(40)-.585)
        self.assertAlmostEqual(balanced_temp_delta(75,320),temp_delta(75)-.585)
        self.assertLess(balanced_temp_delta(60,320),balanced_temp_delta(30,320))

    def test_center_pole_equator_and_repeat_at_earth_scale(self):
        cases=((0,0),(EARTH_Q,90),(2*EARTH_Q,0),(3*EARTH_Q,90),
               (4*EARTH_Q,0),(-EARTH_Q,90),(-2*EARTH_Q,0))
        for x,lat in cases:
            with self.subTest(x=x):
                self.assertAlmostEqual(radial_latitude(x,0,0,0,EARTH_Q),lat)
                self.assertAlmostEqual(radial_latitude(0,x,0,0,EARTH_Q),lat)

    def test_poles_are_circular_rings_and_edges_continuous(self):
        for radius in (EARTH_Q-1,EARTH_Q,EARTH_Q+1):
            self.assertLess(abs(radial_latitude(radius,0,0,0,EARTH_Q)-90),.00002)
        self.assertAlmostEqual(radial_latitude(600,800,0,0,1000),90.0)
        self.assertAlmostEqual(radial_latitude(-600,-800,0,0,1000),90.0)

    def test_radial_climate_warms_equator_and_cools_pole(self):
        self.assertGreater(temp_delta(0),0)
        self.assertLess(temp_delta(90),0)
        self.assertGreater(temp_delta(20),temp_delta(70))

    def test_combined_cold_correction_is_capped(self):
        self.assertAlmostEqual(cap_delta(temp_delta(90)-.585),-.60)
        self.assertAlmostEqual(cap_delta(temp_delta(0)),temp_delta(0))
        self.assertAlmostEqual(cap_delta(.76),.45)

if __name__=="__main__":
    unittest.main()
