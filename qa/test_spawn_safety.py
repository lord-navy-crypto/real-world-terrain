#!/usr/bin/env python3
"""Regression checks for actual saved-world spawn surfacing rules."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import spawn_water_audit

class WorldSpawnWaterTests(unittest.TestCase):
    def sample(self,block="minecraft:grass_block",biome="minecraft:plains"):
        h={(x,z):90 for x in range(-15,16) for z in range(-15,16)}
        return h,{p:block for p in h},{p:biome for p in h}

    def test_safe_inland_spawn(self):
        h,b,biomes=self.sample()
        r=spawn_water_audit((0,91,0),h,b,biomes)
        self.assertTrue(r["spawn_sampled"])
        self.assertFalse(r["unsafe_ocean_spawn"])
        self.assertEqual(r["nearby_surface_liquid_fraction"],0)

    def test_water_spawn_is_failure(self):
        h,b,biomes=self.sample("minecraft:water","minecraft:ocean")
        r=spawn_water_audit((0,63,0),h,b,biomes)
        self.assertTrue(r["unsafe_ocean_spawn"])
        self.assertTrue(r["spawn_in_liquid"])
        self.assertTrue(r["spawn_in_ocean_biome"])

    def test_beached_on_tiny_island_surrounded_by_water(self):
        h,b,biomes=self.sample("minecraft:water","minecraft:ocean")
        b[(0,0)]="minecraft:grass_block"
        biomes[(0,0)]="minecraft:plains"
        r=spawn_water_audit((0,91,0),h,b,biomes)
        self.assertTrue(r["nearby_majority_water"])
        self.assertTrue(r["unsafe_ocean_spawn"])

    def test_spawn_missing_is_unknown_not_pass(self):
        h,b,biomes=self.sample()
        self.assertTrue(spawn_water_audit(None,h,b,biomes)["spawn_unverified"])
        self.assertTrue(spawn_water_audit((400,80,400),h,b,biomes)["spawn_unverified"])

if __name__=="__main__":unittest.main()
