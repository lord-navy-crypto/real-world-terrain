#!/usr/bin/env python3
"""Opt-in ecology pack is vanilla-material-only and cannot rewrite preset geology."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("ecology_builder", ROOT/"scripts/make_vanilla_ecology_pack.py")
ecology=importlib.util.module_from_spec(spec)
spec.loader.exec_module(ecology)

class EcologyPackTests(unittest.TestCase):
    def test_resources_are_compatible_and_namespaced(self):
        data=ecology.resources()
        ecology.validate(data)
        self.assertEqual(data["pack.mcmeta"]["pack"]["pack_format"],48)
        self.assertEqual(len(data),10)
        self.assertEqual(len(ecology.PROFILES),3)
        self.assertTrue(all(path.startswith(("pack.mcmeta",f"data/{ecology.NAMESPACE}/"))
                            for path in data))
        self.assertFalse(any("/worldgen/biome/" in path or
                             "/worldgen/noise_settings/" in path or
                             "/worldgen/density_function/" in path
                             for path in data))

    def test_features_only_decorate_air_above_allowed_vanilla_ground(self):
        for name,profile in ecology.PROFILES.items():
            p=f"data/{ecology.NAMESPACE}"
            feature=ecology.resources()[f"{p}/worldgen/configured_feature/{name}.json"]
            cfg=feature["config"]
            self.assertGreater(cfg["tries"],0)
            self.assertLessEqual(cfg["tries"],20)
            inner=cfg["feature"]
            self.assertEqual(inner["feature"]["type"],"minecraft:simple_block")
            preds=inner["placement"][0]["predicate"]["predicates"]
            self.assertEqual(preds[0]["blocks"],"minecraft:air")
            self.assertEqual(preds[1]["offset"],[0,-1,0])
            self.assertEqual(preds[1]["blocks"],profile["supports"])
            self.assertTrue(profile["block"].startswith("minecraft:"))

    def test_zip_contains_only_opt_in_data_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            destination=Path(tmp)/"ecology.zip"
            ecology.build(destination)
            with ZipFile(destination) as z:
                self.assertIsNone(z.testzip())
                contents=set(z.namelist())
                self.assertEqual(contents,set(ecology.resources()))
                for name in contents:
                    self.assertEqual(json.loads(z.read(name)),ecology.resources()[name])
                self.assertNotIn("data/minecraft/worldgen/world_preset.json",contents)

    def test_no_vanilla_placed_feature_id_reuse(self):
        resources=ecology.resources()
        mods=[v for k,v in resources.items() if "/neoforge/biome_modifier/" in k]
        feature_ids=[v["features"] for v in mods]
        self.assertEqual(len(feature_ids),len(set(feature_ids)))
        self.assertTrue(all(id.startswith(ecology.NAMESPACE+":") for id in feature_ids))

if __name__=="__main__":
    unittest.main()
