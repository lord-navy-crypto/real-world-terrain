#!/usr/bin/env python3
"""Proof gate: sky offshore ocean/deep-ocean probes may bypass only ecotones.

This checks the codec contracts and every transition target's namespace.
Actual behavior and saved biomes are independently tested by NeoForge CI.
"""
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
PRESETS=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"

class OffshoreClassificationTest(unittest.TestCase):
    def test_bypass_only_for_ecotone_source_and_final_delegation_unchanged(self):
        sky=(SOURCES/"SkyOceanBiomeSource.java").read_text()
        eco=(SOURCES/"NaturalEcotoneBiomeSource.java").read_text()
        self.assertIn("source instanceof NaturalEcotoneBiomeSource eco",sky)
        self.assertIn("eco.offshoreOceanClassificationSource() : source",sky)
        self.assertIn("oceanClassificationSource.getNoiseBiome(",sky)
        self.assertIn("return source.getNoiseBiome(quartX,quartY,quartZ,sampler);",sky)
        self.assertRegex(eco,r"BiomeSource offshoreOceanClassificationSource\(\)\s*\{\s*return source;")
        # The protected ocean branch precedes all ecotone candidate/ray work.
        begin=eco.index("Holder<Biome> original = sampleBase(")
        # The ecotone scan may use either a for-each direction loop or
        # an indexed heading loop with a precomputed geometric lookup.
        # Validate that water protection precedes whichever real loop exists,
        # rather than requiring one obsolete spelling of that loop.
        ray_heads=(
            "for (int[] direction : DIRECTIONS)",
            "for (int heading = 0; heading < DIRECTIONS.length; heading++)",
        )
        found=[eco.index(head,begin) for head in ray_heads if head in eco[begin:]]
        self.assertEqual(len(found),1,"Expected exactly one active ecotone ray scan")
        boundary=found[0]
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
        # The source hierarchy that we want to optimize actually exists.
        for preset in ("tva_sacred_timeline_three_realms","tva_measured_three_realms",
                       "realism_max_ecotones_skylands"):
            dims=json.loads((PRESETS/(preset+".json")).read_text())["dimensions"]
            source=dims["minecraft:overworld"]["generator"]["biome_source"]
            self.assertEqual(source["type"],"realscale:sky_ocean")
            self.assertEqual(source["source"]["type"],"realscale:natural_ecotones")

if __name__=="__main__":unittest.main()
