"""Six-direction origin-area biome invasion gallery, independent of default presets."""
import json
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
P=R/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
J=R/"neoforge/src/main/java/org/realscale/neoforge"
class CrossRealmShowcaseTest(unittest.TestCase):
    def test_six_direction_preset(self):
        original=json.loads((P/"tva_dimensional_invasion_three_realms.json").read_text())
        gallery=json.loads((P/"tva_dimensional_invasion_showcase.json").read_text())
        for key in ("minecraft:overworld","minecraft:the_nether","minecraft:the_end"):
            source=gallery["dimensions"][key]["generator"]["biome_source"]
            self.assertEqual(source["type"],"realscale:cross_realm_showcase")
            self.assertEqual(source["source"],original["dimensions"][key]["generator"]["biome_source"])
            self.assertEqual(gallery["dimensions"][key]["generator"]["settings"],
                             original["dimensions"][key]["generator"]["settings"])
    def test_true_vanilla_biomes_and_no_default_mutation(self):
        src=(J/"CrossRealmShowcaseBiomeSource.java").read_text()
        registration=(J/"RealScaleMod.java").read_text()
        for name in ("nether_wastes","end_highlands","forest"):
            self.assertIn(name,src)
        for direction in ("Overworld -> Nether","End -> Nether","Overworld -> End",
                          "Nether -> End","Nether -> Overworld","End -> Overworld"):
            self.assertIn(direction,src)
        self.assertIn("cross_realm_showcase",registration)
        self.assertIn("return normal;",src)
if __name__=="__main__": unittest.main()
