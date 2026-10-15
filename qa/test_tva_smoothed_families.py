#!/usr/bin/env python3
"""TVA core families must use independent interpolated density graphs, while
every legacy generator registry remains available unchanged.
"""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data"
R=ROOT/"realscale/worldgen"
FAMILIES=[
  ("tva_sacred_timeline_three_realms","tva_natural_smoothed","realism_max"),
  ("tva_alpine_three_realms","tva_alpine_smoothed","himalayan"),
  ("tva_canyon_three_realms","tva_canyon_smoothed","grand_canyon"),
]
def read(path):return json.loads(path.read_text())
class CoreSmoothing(unittest.TestCase):
 def test_registry_graph_contract(self):
  for world,smoothed,legacy in FAMILIES:
   with self.subTest(world=world):
    preset=read(R/"world_preset"/(world+".json"))
    dimensions=preset["dimensions"]
    self.assertEqual(dimensions["minecraft:overworld"]["generator"]["settings"],"realscale:"+smoothed)
    self.assertEqual(dimensions["minecraft:the_nether"]["generator"]["settings"],"realscale:grand_nether")
    self.assertEqual(dimensions["minecraft:the_end"]["generator"]["settings"],"realscale:grand_end")
    settings=read(R/"noise_settings"/(smoothed+".json"))
    self.assertEqual(settings["noise"]["size_horizontal"],4)
    for tag,suffix in [("final_density","terrain_density"),("depth","terrain_density"),("initial_density_without_jaggedness","preliminary_density")]:
     self.assertEqual(settings["noise_router"][tag],"realscale:"+smoothed+"/"+suffix)
    self.assertEqual(read(R/"density_function"/smoothed/"height_signal.json"),
      {"type":"minecraft:interpolated","argument":"realscale:"+legacy+"/height_signal"})
    self.assertEqual(read(R/"density_function"/smoothed/"terrain_density.json")["argument1"],
      "realscale:"+smoothed+"/height_signal")
    self.assertEqual(read(R/"density_function"/smoothed/"preliminary_density.json")["argument1"],
      "realscale:"+smoothed+"/terrain_density")
    self.assertTrue((R/"noise_settings"/(legacy+".json")).is_file())
    self.assertTrue((R/"density_function"/legacy/"height_signal.json").is_file())
 def test_only_four_public_families(self):
  expected=["realscale:"+x for x,_,_ in FAMILIES]+["realscale:tva_measured_three_realms"]
  for tag in ("normal","extended"):
   path=ROOT/"minecraft/tags/worldgen/world_preset"/(tag+".json")
   self.assertEqual(read(path)["values"],expected)
if __name__=="__main__": unittest.main()
