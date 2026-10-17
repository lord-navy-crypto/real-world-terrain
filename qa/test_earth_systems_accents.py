"""Regression gate for the 12 optional geological surface accents."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
KINDS=("cold_desert","rocky_plateau","salt_playa","cloud_forest",
       "alluvial_fan","volcanic_highland","badlands","yardang",
       "karst","glacial","wetland","ash_field")

class EarthSystemsAccentsTest(unittest.TestCase):
    def test_each_accent_has_registration_and_complete_datapack_wiring(self):
        java=(SRC/"EarthSystemsAccentFeature.java").read_text()
        registry=(SRC/"RealScaleMod.java").read_text()
        for kind in KINDS:
            with self.subTest(kind=kind):
                name="earth_"+kind
                configured=json.loads((DATA/"worldgen/configured_feature"/(name+".json")).read_text())
                placed=json.loads((DATA/"worldgen/placed_feature"/(name+".json")).read_text())
                modifier=json.loads((DATA/"neoforge/biome_modifier"/(name+".json")).read_text())
                self.assertEqual(configured["type"],"realscale:"+name)
                self.assertEqual(placed["feature"],"realscale:"+name)
                self.assertEqual(modifier["features"],"realscale:"+name)
                self.assertEqual(modifier["step"],"vegetal_decoration")
                self.assertIn("minecraft:biome",[p["type"] for p in placed["placement"]])
                self.assertIn('"'+name+'"',registry)
                self.assertIn("Kind."+kind.upper(),registry)
                self.assertIn(kind.upper(),java)

    def test_never_carves_water_or_surface_and_is_guarded_to_realscale(self):
        java=(SRC/"EarthSystemsAccentFeature.java").read_text()
        self.assertIn("TVAWorldGuard.hasRealScaleGenerator(context)",java)
        self.assertIn("world.getBlockState(position).isAir()",java)
        self.assertIn("world.getFluidState(position).isEmpty()",java)
        self.assertIn("world.getFluidState(floorPos).isEmpty()",java)
        self.assertIn("world.setBlock(position, accent, 2)",java)
        self.assertNotIn("world.setBlock(floorPos",java)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",java)

if __name__=="__main__":
    unittest.main()
