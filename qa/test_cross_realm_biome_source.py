"""Verify opt-in world preset uses authentic vanilla biome IDs in both alien realms."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
PRESETS=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"

class CrossRealmBiomeSourceTests(unittest.TestCase):
    def test_only_new_preset_wraps_nether_and_end(self):
        new=json.loads((PRESETS/"tva_dimensional_invasion_three_realms.json").read_text())
        base=json.loads((PRESETS/"tva_sacred_timeline_three_realms.json").read_text())
        for name in ("minecraft:the_nether","minecraft:the_end"):
            newgen=new["dimensions"][name]["generator"]
            original=base["dimensions"][name]["generator"]
            self.assertEqual(newgen["biome_source"]["type"],"realscale:cross_realm_invasion")
            self.assertEqual(newgen["biome_source"]["source"],original["biome_source"])
            self.assertEqual(newgen["settings"],original["settings"])
        self.assertEqual(new["dimensions"]["minecraft:overworld"],
                         base["dimensions"]["minecraft:overworld"])

    def test_real_biomes_and_dimension_guards(self):
        source=(MAIN/"CrossRealmBiomeSource.java").read_text()
        registered=(MAIN/"RealScaleMod.java").read_text()
        self.assertIn("cross_realm_invasion",registered)
        for name in ("forest","plains","meadow","cherry_grove","taiga"):
            self.assertIn('get(registry,"'+name+'")',source)
        for name in ("nether_wastes","basalt_deltas","the_end","end_highlands"):
            self.assertIn("minecraft:"+name,source)
        self.assertIn("if(!nether&&!end)return original;",source)
        self.assertIn("if(Math.floorMod(hash,24L)!=0)return original;",source)

if __name__=="__main__": unittest.main()
