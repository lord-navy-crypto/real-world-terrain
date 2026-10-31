"""Sacred timeline certification: prevent experimental features from leaking."""
import json,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
D=R/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
S=R/"neoforge/src/main/java/org/realscale/neoforge"
class SacredPromotionTest(unittest.TestCase):
 def test_stable_preset_keeps_three_dimensional_topology(self):
  j=json.loads((D/"tva_sacred_timeline_three_realms.json").read_text())
  self.assertEqual(set(j["dimensions"]),{"minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
  g=j["dimensions"]["minecraft:overworld"]["generator"]
  self.assertEqual(g["settings"],"realscale:tva_natural_smoothed")
  self.assertEqual(g["biome_source"]["type"],"realscale:sky_ocean")
  self.assertEqual(g["biome_source"]["source"]["type"],"realscale:natural_ecotones")
  self.assertEqual(j["dimensions"]["minecraft:the_nether"]["generator"]["settings"],"realscale:grand_nether")
  self.assertEqual(j["dimensions"]["minecraft:the_end"]["generator"]["settings"],"realscale:grand_end")
  self.assertNotIn("geo_wonders_opt_in",json.dumps(j))
  self.assertNotIn("cross_realm_invasion",json.dumps(j))
 def test_optional_wonders_preset_wraps_but_never_replaces_sacred(self):
  a=json.loads((D/"tva_sacred_timeline_three_realms.json").read_text())
  b=json.loads((D/"tva_natural_wonders_experimental_three_realms.json").read_text())
  self.assertEqual(a["dimensions"]["minecraft:the_end"],b["dimensions"]["minecraft:the_end"])
  self.assertEqual(a["dimensions"]["minecraft:the_nether"],b["dimensions"]["minecraft:the_nether"])
  x=a["dimensions"]["minecraft:overworld"]["generator"]
  y=b["dimensions"]["minecraft:overworld"]["generator"]
  self.assertEqual(x["settings"],y["settings"])
  self.assertEqual(y["biome_source"]["type"],"realscale:sky_ocean")
  self.assertEqual(y["biome_source"]["source"]["type"],"realscale:geo_wonders_opt_in")
  self.assertEqual(y["biome_source"]["source"]["source"],x["biome_source"]["source"])
 def test_no_unverified_features_can_run_without_explicit_opt_in(self):
  guard=(S/"TVAWorldGuard.java").read_text()
  self.assertIn("hasExperimentalWonders",guard)
  self.assertNotIn("hasCrossRealmInvasion",guard)
  self.assertIn("source instanceof GeoWondersBiomeSource",guard)
  for name in ("EarthWonderFeature","GeoWonderRegionFeature","CaveWonderNetworkFeature"):
   self.assertIn("TVAWorldGuard.hasExperimentalWonders(context)",(S/(name+".java")).read_text())
  self.assertIn("TVAWorldGuard.hasExperimentalWonders(context)",
                (S/"EarthSystemsAccentFeature.java").read_text())
  self.assertFalse((S/"DimensionalInvasionFeature.java").exists())
if __name__=="__main__":unittest.main()
