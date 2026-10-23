#!/usr/bin/env python3
"""Safety/registration regressions for the opt-in merged Natural Wonders + invasion.

These assertions certify source wiring and canonical-world isolation only.
The dedicated NeoForge workflow must prove saved Anvil/codec runtime behavior.
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESETS = ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
TAGS = ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
JAVA = ROOT/"neoforge/src/main/java/org/realscale/neoforge"
CANONICAL = "tva_sacred_timeline_three_realms"
COMBINED = "tva_wonders_invasion_three_realms"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


class WovenOptionalFeaturesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.canonical = load(PRESETS/(CANONICAL+".json"))
        cls.combined = load(PRESETS/(COMBINED+".json"))

    def test_combined_reuses_sacred_terrain_and_all_three_dimensions(self):
        self.assertEqual(set(self.combined["dimensions"]),
                         {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"})
        for realm in self.canonical["dimensions"]:
            with self.subTest(realm=realm):
                old = self.canonical["dimensions"][realm]
                new = self.combined["dimensions"][realm]
                self.assertEqual(old["type"], new["type"])
                self.assertEqual(old["generator"]["type"], new["generator"]["type"])
                self.assertEqual(old["generator"]["settings"], new["generator"]["settings"])

    def test_opt_in_wrapper_order_preserves_sky_reefs_climate_and_invasions(self):
        o = self.combined["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        self.assertEqual(o["type"], "realscale:sky_ocean")
        wonder = o["source"]
        self.assertEqual(wonder["type"], "realscale:geo_wonders_opt_in")
        invasion = wonder["source"]
        self.assertEqual(invasion["type"], "realscale:cross_realm_invasion")
        self.assertEqual(invasion["target"], "overworld")
        ecotone = invasion["source"]
        self.assertEqual(ecotone["type"], "realscale:natural_ecotones")
        self.assertEqual(ecotone["source"]["type"], "minecraft:multi_noise")
        for realm in ("minecraft:the_nether", "minecraft:the_end"):
            self.assertEqual(self.combined["dimensions"][realm]["generator"]
                             ["biome_source"]["type"], "realscale:cross_realm_invasion")
        self.assertNotIn("usgs_big_sur", json.dumps(self.combined))

    def test_no_experimental_features_leak_into_original_sacred(self):
        original = json.dumps(self.canonical)
        for opt_in in ("geo_wonders_opt_in", "cross_realm_invasion",
                       "cross_realm_showcase"):
            self.assertNotIn(opt_in, original)

    def test_new_opt_in_is_selectable_without_replacing_existing_presets(self):
        for tag_name in ("normal", "extended"):
            with self.subTest(tag=tag_name):
                entry = load(TAGS/(tag_name+".json"))
                self.assertFalse(entry["replace"])
                self.assertEqual(entry["values"][0], "realscale:natural_three_realms")
                self.assertEqual(entry["values"].count("realscale:wonders_invasion_three_realms"), 1)
                for required in ("alpine_three_realms", "canyon_three_realms",
                                 "measured_three_realms"):
                    self.assertIn("realscale:"+required,entry["values"])

    def test_nested_java_guards_activate_both_feature_families(self):
        sky=(JAVA/"SkyOceanBiomeSource.java").read_text()
        wonders=(JAVA/"GeoWondersBiomeSource.java").read_text()
        guard=(JAVA/"TVAWorldGuard.java").read_text()
        patch=(JAVA/"CrossRealmTerrainPatchFeature.java").read_text()
        self.assertIn("source instanceof GeoWondersBiomeSource wonders", sky)
        self.assertIn("wonders.containsCrossRealm()", sky)
        self.assertIn("source instanceof CrossRealmBiomeSource", wonders)
        self.assertIn("sky.containsCrossRealm()", guard)
        self.assertIn("sky.containsGeoWonders()", guard)
        self.assertIn("sky.containsCrossRealm()", patch)
        self.assertIn("Math.floorDiv(x,16)!=homeX", patch)
        self.assertIn("Math.floorDiv(z,16)!=homeZ", patch)

    def test_performance_systems_still_live_in_sacred_feature_path(self):
        ecotone=(JAVA/"NaturalEcotoneBiomeSource.java").read_text()
        cache=(JAVA/"QuartBiomeResultCache.java").read_text()
        profiler=(JAVA/"WorldgenProfiler.java").read_text()
        for token in ("QuartBiomeResultCache", "cache.resolved.get",
                      "cache.resolved.put", "cache.prepare(sampler)"):
            self.assertIn(token, ecotone)
        self.assertIn("SLOTS = 2048", cache)
        self.assertIn("realscale.worldgen.profile",profiler)
        self.assertIn("static final boolean ENABLED",profiler)


if __name__ == "__main__":
    unittest.main()
