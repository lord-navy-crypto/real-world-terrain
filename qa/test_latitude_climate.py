#!/usr/bin/env python3
"""RealScale periodic latitude and optional basin climate control integration."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("terrain_latitude",ROOT/"scripts/terrain_lab.py")
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)

class LatitudeClimateTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root=Path(tmp.name)
        source=ROOT/"neoforge/src/main/resources"
        for path in ("data/realscale/worldgen",
                     "data/minecraft/tags/worldgen/world_preset",
                     "assets/minecraft/lang"):
            shutil.copytree(source/path,self.root/path)

    def make(self,**opts):
        return lab.files_for(self.root,"landscape",1.0,0,50,6,**opts)

    def get_temperature(self,outputs,name):
        return outputs[lab.GEO_ROOT/"density_function/terrain_lab"/name/"biome_temperature.json"]

    def test_earth_scale_is_true_one_block_per_meter(self):
        name,options,out=self.make(latitude="earth",latitude_strength=70)
        temp=self.get_temperature(out,name)
        self.assertEqual(temp["type"],"realscale:latitude_temperature")
        self.assertEqual(temp["equator_z"],0)
        self.assertEqual(temp["pole_distance"],10_001_966)
        self.assertEqual(temp["strength"],.7)
        noise=out[lab.GEO_ROOT/"noise_settings"/(name+".json")]
        self.assertEqual(noise["noise_router"]["temperature"],
                         f"realscale:terrain_lab/{name}/biome_temperature")
        self.assertEqual(noise["sea_level"],63)
        self.assertEqual(options["latitude"],"earth")

    def test_preview_shortens_pole_distance_without_affecting_blocks_per_meter(self):
        name,_,out=self.make(latitude="preview",latitude_strength=45,equator_z=-512)
        temp=self.get_temperature(out,name)
        self.assertEqual(temp["pole_distance"],16384)
        self.assertEqual(temp["equator_z"],-512)
        self.assertEqual(temp["strength"],.45)

    def test_basin_climate_is_true_nested_noise_router_node(self):
        name,options,out=self.make(latitude="preview",climate=65,
                                    valley=25,basin=30,latitude_strength=80)
        temp=self.get_temperature(out,name)
        self.assertEqual(temp["type"],"realscale:basin_temperature")
        self.assertEqual(temp["warmth"],.105)
        self.assertEqual(temp["radius"],192)
        self.assertEqual(temp["height_signal"],f"realscale:terrain_lab/{name}/height_signal")
        lat=temp["base"]["base"]
        self.assertEqual(lat["type"],"realscale:latitude_temperature")
        self.assertEqual(lat["strength"],.8)
        self.assertEqual(lat["base"]["type"],"minecraft:add")
        self.assertEqual(options["basin"],30)

    def test_zero_or_off_preserves_prior_ids_and_files(self):
        original,opts,files=self.make(climate=65,valley=35)
        same,opts2,files2=self.make(climate=65,valley=35,latitude="off",basin=0)
        self.assertEqual(original,same)
        self.assertEqual(opts,opts2)
        self.assertEqual(files,files2)
        disabled,disabled_opts,disabled_files=self.make(latitude="preview",latitude_strength=0)
        old,old_opts,old_files=self.make()
        self.assertEqual(disabled,old)
        self.assertEqual(disabled_opts,old_opts)
        self.assertEqual(disabled_files,old_files)

    def test_old_biomes_and_dem_unchanged(self):
        for old in lab.PRESETS:
            with self.subTest(preset=old):
                with self.assertRaises(ValueError):
                    lab.files_for(self.root,old,1.0,0,50,6,latitude="earth")
        with self.assertRaises(ValueError):
            lab.files_for(self.root,"usgs_big_sur",1.0,0,50,6)

    def test_invalid_settings_rejected_before_writing(self):
        bad=({"latitude":"planet"},{"latitude_strength":101},
             {"latitude_strength":-5},{"equator_z":30_000_000},
             {"equator_z":0.25},{"basin":-1},{"basin":101},
             {"latitude":"earth","latitude_strength":"70"})
        for item in bad:
            with self.subTest(item=item),self.assertRaises(ValueError):
                self.make(**item)

    def test_saved_yacl_draft_exposes_cycle_and_basin(self):
        draft=self.root/"latitude-draft.json"
        draft.write_text(json.dumps({
            "preset":"alpine","blendPercent":25,"reliefPercent":100,
            "elevation":0,"caves":50,"riverGrade":6,"inland":True,
            "ridgePercent":0,"erosionPercent":0,"strataPercent":0,
            "climatePercent":65,"valleyPercent":0,"latitudeMode":"earth",
            "latitudeStrength":70,"equatorZ":0,"basinPercent":20
        }))
        baseline=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_alpine_*.json"))
        self.assertEqual(lab.main(["--root",str(self.root),"--from-yacl",str(draft)]),0)
        after=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_alpine_*.json"))
        self.assertEqual(len(after-baseline),1)
        name=(after-baseline).pop().stem
        t=lab.jread(self.root,lab.GEO_ROOT/"density_function/terrain_lab"/name/"biome_temperature.json")
        self.assertEqual(t["type"],"realscale:basin_temperature")
        self.assertEqual(t["base"]["type"],"realscale:latitude_temperature")
        self.assertEqual(t["base"]["pole_distance"],10_001_966)

    def test_source_registration_and_yacl_controls(self):
        src=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
        mod=(src/"RealScaleMod.java").read_text()
        screen=(src/"RealScaleTerrainLabClient.java").read_text()
        for name in ("latitude_temperature","basin_temperature"):
            self.assertIn('"'+name+'"',mod)
        self.assertIn('Latitude Climate Cycle',screen)
        self.assertIn('Enclosed Basin Warming (Optional)',screen)
        self.assertIn('equatorZ',screen)

if __name__=="__main__":
    unittest.main()
