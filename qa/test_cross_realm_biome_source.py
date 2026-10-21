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
        self.assertEqual(new["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         base["dimensions"]["minecraft:overworld"]["generator"]["settings"])
        self.assertEqual(new["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["type"],
                         "realscale:sky_ocean")
        wrapper=new["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["source"]
        self.assertEqual(wrapper["type"],"realscale:cross_realm_invasion")
        self.assertEqual(wrapper["target"],"overworld")
        self.assertEqual(wrapper["source"],
                         base["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["source"])

    def test_real_biomes_and_dimension_guards(self):
        source=(MAIN/"CrossRealmBiomeSource.java").read_text()
        registered=(MAIN/"RealScaleMod.java").read_text()
        self.assertIn("cross_realm_invasion",registered)
        for name in ("forest","plains","meadow","cherry_grove","taiga"):
            self.assertIn('get(registry,"'+name+'")',source)
        for name in ("nether_wastes","basalt_deltas","the_end","end_highlands"):
            self.assertIn("minecraft:"+name,source)
        self.assertIn("if(!nether&&!end)return original;",source)
        self.assertIn('if("overworld".equals(target))',source)
        self.assertIn('biome.endsWith("ocean")',source)
        self.assertIn('biome.endsWith("river")',source)
        self.assertIn("if(Math.floorMod(hash,24L)!=0)return original;",source)
        # Fast-path exactly matches the old overworld altitude window. Legacy
        # forced-spawn Y columns must never be short-circuited.
        self.assertIn("if(!forceOriginInvasion && originZone)return original;",source)
        self.assertIn("if((quartY<14||quartY>49)",source)
        self.assertIn("&& !(forceOriginInvasion && originZone))return original;",source)
        for forced in (False,True):
            for at_origin in (False,True):
                for quart_y in range(-16,508):
                    fast_skip=(not forced and at_origin) or (
                        (quart_y<14 or quart_y>49)
                        and not (forced and at_origin))
                    # The original behavior delegates in the free origin
                    # or outside the surface band except forced legacy spawn.
                    expected=(not forced and at_origin) or (
                        (quart_y*4<56 or quart_y*4>196)
                        and not (forced and at_origin))
                    self.assertEqual(fast_skip,expected)


if __name__=="__main__": unittest.main()
