#!/usr/bin/env python3
"""Regression contract: dimensional invasion is absent, while climate and ecology remain."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
PRESETS=DATA/"worldgen/world_preset"
TAGS=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
class NoDimensionalInvasionTests(unittest.TestCase):
    def test_invasion_java_modules_and_registrations_are_absent(self):
        for name in ("CrossRealmBiomeSource.java","CrossRealmShowcaseBiomeSource.java","CrossRealmTerrainPatchFeature.java","DimensionalInvasionFeature.java"):
            self.assertFalse((JAVA/name).exists(),name)
        mod=(JAVA/"RealScaleMod.java").read_text()
        guard=(JAVA/"TVAWorldGuard.java").read_text()
        for token in ("cross_realm_invasion","cross_realm_showcase","invasion_patch_","invasion_overworld","invasion_nether","invasion_end"):
            self.assertNotIn(token,mod)
            self.assertNotIn(token,guard)
    def test_invasion_worldgen_assets_and_presets_are_absent(self):
        for folder in (DATA/"neoforge/biome_modifier",DATA/"worldgen/configured_feature",DATA/"worldgen/placed_feature"):
            for path in folder.glob("invasion_*.json"):
                self.fail(f"obsolete invasion asset remains: {path.relative_to(ROOT)}")
        for name in ("tva_dimensional_invasion_showcase.json","tva_dimensional_invasion_three_realms.json","tva_wonders_invasion_three_realms.json","wonders_invasion_three_realms.json"):
            self.assertFalse((PRESETS/name).exists(),name)
    def test_geo_wonders_and_sky_ocean_remain_independent(self):
        wonder=json.loads((PRESETS/"tva_natural_wonders_experimental_three_realms.json").read_text())
        source=wonder["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        self.assertEqual(source["type"],"realscale:sky_ocean")
        self.assertEqual(source["source"]["type"],"realscale:geo_wonders_opt_in")
        self.assertNotIn("cross_realm",json.dumps(wonder))
        self.assertTrue((JAVA/"GeoWondersBiomeSource.java").exists())
        self.assertTrue((JAVA/"SkyOceanBiomeSource.java").exists())
    def test_latitude_climate_and_three_dimensions_are_preserved(self):
        mod=(JAVA/"RealScaleMod.java").read_text()
        self.assertTrue((JAVA/"LatitudeTemperatureDensity.java").exists())
        self.assertTrue((JAVA/"RadialLatitudeTemperatureDensity.java").exists())
        self.assertIn('"latitude_temperature"',mod)
        self.assertIn('"radial_latitude_temperature"',mod)
        sacred=json.loads((PRESETS/"tva_sacred_timeline_three_realms.json").read_text())
        self.assertEqual(set(sacred["dimensions"]),{"minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
        self.assertNotIn("invasion",json.dumps(sacred))
        for menu in ("normal","extended"):
            values=json.loads((TAGS/(menu+".json")).read_text())["values"]
            self.assertEqual(len(values),7)
            self.assertFalse(any("invasion" in value for value in values))
if __name__=="__main__": unittest.main()
