#!/usr/bin/env python3
"""Cross-check every shipped RealScale world preset and its density graph.

Checks all original 8 terrain presets and the separate USGS DEM v2 trial.
Does not alter sharp natural ridges or turn static references into a claim
that distant generated chunks are proven continuous.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources"
WORLD=ROOT/"data/realscale/worldgen"
DENSITY=WORLD/"density_function"
NOISE=WORLD/"noise_settings"
TYPES=ROOT/"data/realscale/dimension_type"
PRESETS=WORLD/"world_preset"
NAMES=("realism_max","georeal_ultimate","himalayan","grand_canyon",
       "usgs_big_sur","usgs_big_sur_v2","continental","realistic_mountains","ultimate")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def refs(node):
    if isinstance(node,str):
        if node.startswith("realscale:"):
            yield node
    elif isinstance(node,list):
        for child in node:
            yield from refs(child)
    elif isinstance(node,dict):
        for key,value in node.items():
            if key!="type":
                yield from refs(value)


class WorldgenGraphIntegrity(unittest.TestCase):
    def test_each_preset_has_actual_settings_for_all_dimensions(self):
        for name in NAMES:
            with self.subTest(preset=name):
                data=load(PRESETS/(name+".json"))
                dim=data["dimensions"]
                self.assertEqual(set(dim),{"minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
                surface=dim["minecraft:overworld"]
                self.assertEqual(surface["generator"]["type"],"minecraft:noise")
                self.assertEqual(surface["generator"]["settings"],"realscale:"+name)
                self.assertTrue((NOISE/(name+".json")).is_file())
                dtype=surface["type"]
                if dtype.startswith("realscale:"):
                    self.assertTrue((TYPES/(dtype.split(":",1)[1]+".json")).is_file(),
                                    "Missing custom dimension type "+dtype)
                for nether_end,vanilla in (("minecraft:the_nether","minecraft:nether"),
                                          ("minecraft:the_end","minecraft:end")):
                    target=dim[nether_end]["generator"]["settings"]
                    self.assertEqual(target,vanilla,"Legacy/optional overworld preset leaked dimensions")

    def test_all_realscale_density_references_have_real_files(self):
        for name in NAMES:
            with self.subTest(preset=name):
                router=load(NOISE/(name+".json"))["noise_router"]
                todo=list(refs(router))
                visited=set()
                while todo:
                    identifier=todo.pop()
                    if identifier in visited:continue
                    visited.add(identifier)
                    file=DENSITY/(identifier.split(":",1)[1]+".json")
                    self.assertTrue(file.is_file(),name+": unresolved density "+identifier)
                    if file.is_file():
                        child=load(file)
                        todo.extend(refs(child))
                self.assertGreater(len(visited),1)

    def test_all_profiles_have_valid_vertical_bounds_and_expected_oceans(self):
        for name in NAMES:
            with self.subTest(preset=name):
                settings=load(NOISE/(name+".json"))
                vertical=settings["noise"]
                self.assertEqual(vertical["min_y"]%16,0)
                self.assertEqual(vertical["height"]%16,0)
                self.assertGreaterEqual(vertical["height"],128)
                self.assertLessEqual(vertical["height"],4096)
                self.assertEqual(settings["sea_level"],63)
                self.assertEqual(settings["default_fluid"]["Name"],"minecraft:water")
                self.assertIn(vertical["size_horizontal"],(1,2,4))
                self.assertIn(vertical["size_vertical"],(1,2,4))

    def test_old_and_new_usgs_profiles_reference_separate_density(self):
        old=load(NOISE/"usgs_big_sur.json")["noise_router"]
        new=load(NOISE/"usgs_big_sur_v2.json")["noise_router"]
        # TVA v0.6 rebuilt noise graphs refer directly to a registry density
        # ID; historical v1.3 had a range_choice wrapper with an input key.
        # Require the SAME independent source-vs-v2 distinction either way.
        source_id=lambda node:node if isinstance(node,str) else node["input"]
        self.assertEqual(source_id(old["final_density"]),"realscale:usgs_big_sur/terrain_density")
        wrapped=new["final_density"]
        self.assertEqual(wrapped["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(wrapped["base"]["input"],"realscale:usgs_big_sur_v2/terrain_density")
        self.assertEqual(wrapped["small_caves"],"realscale:realism_max/cave_core")
        self.assertEqual(load(DENSITY/"usgs_big_sur/terrain_density.json")["type"],
                         "realscale:dem_density")
        self.assertEqual(load(DENSITY/"usgs_big_sur_v2/terrain_density.json")["type"],
                         "realscale:dem_density_seamless")

if __name__=="__main__":
    unittest.main()
