#!/usr/bin/env python3
"""Regression gates for the independent opt-in Natural Wonders wrapper.

The former combined invasion preset is intentionally gone. Natural Wonders
must remain independently selectable without changing climate or dimensions.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PRESETS=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
TAGS=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
CANONICAL="tva_sacred_timeline_three_realms"
WONDERS="tva_natural_wonders_experimental_three_realms"

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

class GeoWondersOptInTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.canonical=load(PRESETS/(CANONICAL+".json"))
        cls.wonders=load(PRESETS/(WONDERS+".json"))

    def test_wonders_reuses_sacred_terrain_and_three_dimension_topology(self):
        self.assertEqual(set(self.wonders["dimensions"]),
                         {"minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
        for realm in self.canonical["dimensions"]:
            with self.subTest(realm=realm):
                old=self.canonical["dimensions"][realm]
                new=self.wonders["dimensions"][realm]
                self.assertEqual(old["type"],new["type"])
                self.assertEqual(old["generator"]["type"],new["generator"]["type"])
                self.assertEqual(old["generator"]["settings"],new["generator"]["settings"])
        self.assertEqual(self.canonical["dimensions"]["minecraft:the_nether"],
                         self.wonders["dimensions"]["minecraft:the_nether"])
        self.assertEqual(self.canonical["dimensions"]["minecraft:the_end"],
                         self.wonders["dimensions"]["minecraft:the_end"])

    def test_wrapper_order_preserves_sky_ocean_wonders_and_ecotones(self):
        source=self.wonders["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        self.assertEqual(source["type"],"realscale:sky_ocean")
        wonders=source["source"]
        self.assertEqual(wonders["type"],"realscale:geo_wonders_opt_in")
        ecotones=wonders["source"]
        self.assertEqual(ecotones["type"],"realscale:natural_ecotones")
        self.assertEqual(ecotones["source"]["type"],"minecraft:multi_noise")
        self.assertNotIn("cross_realm",json.dumps(self.wonders))

    def test_wonders_remain_opt_in_and_public_menu_is_unchanged(self):
        canonical=json.dumps(self.canonical)
        self.assertNotIn("geo_wonders_opt_in",canonical)
        self.assertNotIn("cross_realm",canonical)
        self.assertTrue((JAVA/"GeoWondersBiomeSource.java").is_file())
        self.assertTrue((JAVA/"SkyOceanBiomeSource.java").is_file())
        expected=[
            "realscale:natural_three_realms",
            "realscale:alpine_three_realms",
            "realscale:canyon_three_realms",
            "realscale:glacial_three_realms",
            "realscale:volcanic_three_realms",
            "realscale:karst_three_realms",
            "realscale:dune_three_realms",
        ]
        for tag_name in ("normal","extended"):
            with self.subTest(tag=tag_name):
                entry=load(TAGS/(tag_name+".json"))
                self.assertFalse(entry["replace"])
                self.assertEqual(entry["values"],expected)

    def test_performance_systems_remain_on_sacred_feature_path(self):
        ecotone=(JAVA/"NaturalEcotoneBiomeSource.java").read_text()
        cache=(JAVA/"QuartBiomeResultCache.java").read_text()
        profiler=(JAVA/"WorldgenProfiler.java").read_text()
        for token in ("QuartBiomeResultCache","cache.resolved.get",
                      "cache.resolved.put","cache.prepare(sampler)"):
            self.assertIn(token,ecotone)
        self.assertIn("SLOTS = 2048",cache)
        self.assertIn("realscale.worldgen.profile",profiler)
        self.assertIn("static final boolean ENABLED",profiler)

if __name__=="__main__": unittest.main()
