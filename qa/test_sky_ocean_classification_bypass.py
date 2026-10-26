#!/usr/bin/env python3
"""Proof gate: offshore ocean probes unwrap shared identity/decorator layers.

The original decorated source still decides final biome results. This checks
the codec contracts and transition targets; saved-world behavior is covered by
the independent NeoForge CI jobs.
"""
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
PRESETS=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"

class OffshoreClassificationTest(unittest.TestCase):
    def test_bypass_unwraps_shared_layers_and_final_delegation_is_unchanged(self):
        sky=(SOURCES/"SkyOceanBiomeSource.java").read_text()
        eco=(SOURCES/"NaturalEcotoneBiomeSource.java").read_text()
        self.assertIn("unwrapOceanClassificationSource(BiomeSource candidate)",sky)
        self.assertIn("return unwrapOceanClassificationSource(eco.offshoreOceanClassificationSource());",sky)
        self.assertIn("return unwrapOceanClassificationSource(wonders.wrappedSource());",sky)
        self.assertIn("return unwrapOceanClassificationSource(cave.wrappedSource());",sky)
        self.assertIn("this.oceanClassificationSource=unwrapOceanClassificationSource(source);",sky)
        self.assertIn("oceanClassificationSource.getNoiseBiome(",sky)
        self.assertIn("return source.getNoiseBiome(quartX,quartY,quartZ,sampler);",sky)
        self.assertRegex(eco,r"BiomeSource offshoreOceanClassificationSource\(\)\s*\{\s*return source;")
        # The protected ocean branch precedes all ecotone candidate/ray work.
        begin=eco.index("Holder<Biome> original = sampleBase(")
        boundary=eco.index("for (int[] direction : DIRECTIONS)",begin)
        early=eco.index("protectedWater(current)",begin)
        self.assertLess(early,boundary)

    def test_all_ecotone_targets_are_non_ocean_and_non_vanilla(self):
        eco=(SOURCES/"NaturalEcotoneBiomeSource.java").read_text()
        targets=re.findall(r'rule\("([^"]+)"\s*,\s*\d+',eco)
        self.assertGreater(len(targets),50)
        self.assertFalse(any(t.endswith("ocean") or t.startswith("deep_")
                             for t in targets))
        self.assertIn('ResourceLocation.fromNamespaceAndPath("realscale", rule.target())',eco)
        self.assertIn('return "minecraft".equals(location.getNamespace())',eco)
        self.assertIn('id.contains("ocean")',eco)
        # Historical source graphs remain unchanged for existing worlds.
        for preset in ("tva_sacred_timeline_three_realms","tva_measured_three_realms",
                       "realism_max_ecotones_skylands"):
            dims=json.loads((PRESETS/(preset+".json")).read_text())["dimensions"]
            source=dims["minecraft:overworld"]["generator"]["biome_source"]
            self.assertEqual(source["type"],"realscale:sky_ocean")
            self.assertEqual(source["source"]["type"],"realscale:natural_ecotones")
        # New canonical aliases compose the same layers in a stable order.
        for preset in ("natural_three_realms","alpine_three_realms",
                       "canyon_three_realms","measured_three_realms"):
            dims=json.loads((PRESETS/(preset+".json")).read_text())["dimensions"]
            source=dims["minecraft:overworld"]["generator"]["biome_source"]
            self.assertEqual(source["type"],"realscale:sky_ocean")
            self.assertEqual(source["source"]["type"],"realscale:geo_wonders_opt_in")
            source=source["source"]["source"]
            self.assertEqual(source["type"],"realscale:natural_ecotones")
            self.assertEqual(source["source"]["type"],"realscale:cave_depth_habitats")

if __name__=="__main__":unittest.main()
