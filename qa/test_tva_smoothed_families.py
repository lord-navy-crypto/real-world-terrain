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
    final=settings["noise_router"]["final_density"]
    self.assertEqual(final["type"],"realscale:grand_overworld_caverns")
    self.assertEqual(final["base"],"realscale:"+smoothed+"/terrain_density")
    self.assertEqual(final["small_caves"],"realscale:realism_max/cave_core")
    for tag,suffix in [("depth","terrain_density"),("initial_density_without_jaggedness","preliminary_density")]:
     self.assertEqual(settings["noise_router"][tag],"realscale:"+smoothed+"/"+suffix)
    self.assertEqual(read(R/"density_function"/smoothed/"original_height_signal.json"),
      {"type":"minecraft:interpolated","argument":"realscale:"+legacy+"/height_signal"})
    restored=read(R/"density_function"/smoothed/"height_signal.json")
    if smoothed=="tva_natural_smoothed":
     self.assertEqual(restored["type"],"minecraft:cache_2d")
     restored=restored["argument"]
    self.assertEqual(restored["type"],"realscale:mountain_relief")
    self.assertEqual(restored["base"],"realscale:"+smoothed+"/original_height_signal")
    self.assertGreater(restored["basin_depth"],0)
    self.assertGreater(restored["plateau_rise"],0)
    self.assertEqual(read(R/"density_function"/smoothed/"terrain_density.json")["argument1"],
      "realscale:"+smoothed+"/height_signal")
    self.assertEqual(read(R/"density_function"/smoothed/"preliminary_density.json")["argument1"],
      "realscale:"+smoothed+"/terrain_density")
    self.assertTrue((R/"noise_settings"/(legacy+".json")).is_file())
    self.assertTrue((R/"density_function"/legacy/"height_signal.json").is_file())
 def test_four_curated_families_and_separate_combined_opt_in(self):
  stable=["realscale:"+x for x,_,_ in FAMILIES]+["realscale:tva_measured_three_realms"]
  optional="realscale:tva_wonders_invasion_three_realms"
  biodiversity=["realscale:tva_sacred_biodiversity_end3_spawn3_v2",
                "realscale:tva_measured_biodiversity_end3_spawn3_v2"]
  for tag in ("normal","extended"):
   path=ROOT/"minecraft/tags/worldgen/world_preset"/(tag+".json")
   expected=stable+[optional]+(biodiversity if tag=="normal" else [])
   self.assertEqual(read(path)["values"],expected)
   self.assertEqual(len(read(path)["values"]),len(set(read(path)["values"])))
if __name__=="__main__": unittest.main()
