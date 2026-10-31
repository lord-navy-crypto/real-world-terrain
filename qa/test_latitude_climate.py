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

    def get_temperature_wrapper(self,outputs,name):
        return outputs[lab.GEO_ROOT/"density_function/terrain_lab"/name/"biome_temperature.json"]

    def get_temperature(self,outputs,name):
        wrapper=self.get_temperature_wrapper(outputs,name)
        self.assertEqual(wrapper["type"],"realscale:horizontal_scale")
        self.assertEqual(wrapper["scale"],20)
        return wrapper["base"]

    def test_earth_scale_uses_periodic_one_to_twenty_horizontal_mapping(self):
        name,options,out=self.make(latitude="earth",latitude_strength=70)
        temp=self.get_temperature(out,name)
        self.assertEqual(temp["type"],"realscale:latitude_temperature")
        self.assertEqual(temp["equator_z"],0)
        self.assertEqual(temp["pole_distance"],10_001_966)
        self.assertEqual(temp["strength"],.7)
        self.assertFalse(temp["clamp_at_poles"])
        self.assertEqual(options["horizontal_scale"],20)
        noise=out[lab.GEO_ROOT/"noise_settings"/(name+".json")]
        self.assertEqual(noise["noise_router"]["temperature"],
                         f"realscale:terrain_lab/{name}/biome_temperature")
        self.assertEqual(noise["sea_level"],63)
        self.assertEqual(options["latitude"],"earth")

    def test_preview_keeps_sixteen_thousand_block_pole_distance_and_periodicity(self):
        name,_,out=self.make(latitude="preview",latitude_strength=45,equator_z=-512)
        temp=self.get_temperature(out,name)
        self.assertEqual(temp["pole_distance"],327_680)
        self.assertEqual(temp["equator_z"],-10_240)
        self.assertEqual(temp["strength"],.45)
        self.assertFalse(temp["clamp_at_poles"])

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
        self.assertEqual(t["type"],"realscale:horizontal_scale")
        self.assertEqual(t["scale"],20)
        t=t["base"]
        self.assertEqual(t["type"],"realscale:basin_temperature")
        self.assertEqual(t["base"]["type"],"realscale:latitude_temperature")
        self.assertEqual(t["base"]["pole_distance"],10_001_966)
        self.assertFalse(t["base"]["clamp_at_poles"])

    def test_concentric_earth_radius_and_climate_budget(self):
        name,opts,outputs=self.make(
            latitude="radial_earth",latitude_strength=70,
            center_x=1024,equator_z=-512,cold_limit=60,
            climate=65,valley=25,basin=20)
        temp=self.get_temperature(outputs,name)
        self.assertEqual(temp["type"],"realscale:climate_budget")
        self.assertEqual(temp["max_cooling"],.6)
        self.assertEqual(temp["max_warming"],.45)
        self.assertEqual(temp["original"]["type"],"minecraft:shifted_noise")
        r=temp["adjusted"]["base"]["base"]
        self.assertEqual(r["type"],"realscale:radial_latitude_temperature")
        self.assertEqual(r["center_x"],20_480)
        self.assertEqual(r["center_z"],-10_240)
        self.assertEqual(r["pole_distance"],10_001_966)
        self.assertEqual(r["strength"],.7)
        self.assertEqual(opts["latitude"],"radial_earth")
        self.assertEqual(opts["cold_limit"],60)
        self.assertEqual(opts["center_x"],1024)
        n=outputs[lab.GEO_ROOT/"noise_settings"/(name+".json")]
        self.assertEqual(n["sea_level"],63)
        self.assertEqual(n["noise_router"]["temperature"],
                         f"realscale:terrain_lab/{name}/biome_temperature")

    def test_concentric_preview_cold_limit_can_be_disabled(self):
        a,o,files=self.make(latitude="radial_preview",cold_limit=0)
        r=self.get_temperature(files,a)
        self.assertEqual(r["type"],"realscale:radial_latitude_temperature")
        self.assertEqual(r["pole_distance"],327_680)
        self.assertNotIn("cold_limit",o)
        b,oo,ff=self.make(latitude="radial_preview",cold_limit=60)
        self.assertNotEqual(a,b)
        self.assertEqual(self.get_temperature(ff,b)["type"],"realscale:climate_budget")

    def test_existing_meridional_latitude_and_all_disabled_stay_identical(self):
        for mode in ("earth","preview","off"):
            x,a,b=self.make(latitude=mode,latitude_strength=70)
            y,c,d=self.make(latitude=mode,latitude_strength=70,center_x=0,cold_limit=60)
            self.assertEqual(x,y)
            self.assertEqual(a,c)
            self.assertEqual(b,d)
        normal,_,normal_files=self.make()
        ignored,_,ignored_files=self.make(latitude="off",cold_limit=0,center_x=1000)
        self.assertEqual(normal,ignored)
        self.assertEqual(normal_files,ignored_files)

    def test_concentric_controls_validate_ranges(self):
        for invalid in (
            {"latitude":"radial_time"},
            {"center_x":29_000_001},
            {"center_x":.5},
            {"cold_limit":-1},
            {"cold_limit":101},
            {"cold_limit":"60"},
        ):
            with self.subTest(invalid=invalid),self.assertRaises(ValueError):
                self.make(**invalid)

    def test_source_registration_and_yacl_controls(self):
        src=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
        mod=(src/"RealScaleMod.java").read_text()
        screen=(src/"RealScaleTerrainLabClient.java").read_text()
        for name in ("latitude_temperature","basin_temperature","radial_latitude_temperature","climate_budget"):
            self.assertIn('"'+name+'"',mod)
        self.assertIn('Latitude Climate Cycle',screen)
        self.assertIn('Enclosed Basin Warming (Optional)',screen)
        self.assertIn('equatorZ',screen)
        self.assertIn('radial_preview',screen)
        self.assertIn('Z-axis zonal latitude',screen)
        self.assertIn('Radial Cold Climate Limit',screen)


    def test_pr25_equal_area_custom_lab_mode_and_legacy_stability(self):
        name,options,outputs=self.make(
            latitude="radial_equal_area",latitude_strength=70,
            climate=65,valley=25,basin=20,cold_limit=60)
        temperature=self.get_temperature(outputs,name)
        radial=temperature["adjusted"]["base"]["base"]
        self.assertEqual(radial["type"],"realscale:radial_latitude_temperature")
        self.assertTrue(radial["balanced"])
        self.assertTrue(radial["area_balanced"])
        self.assertEqual(radial["pole_distance"],8192)
        self.assertEqual(radial["altitude_cooling"],.585)
        self.assertEqual(options["latitude"],"radial_equal_area")
        old_name,old_options,old_files=self.make(
            latitude="radial_balanced",latitude_strength=70,
            climate=65,valley=25,basin=20,cold_limit=60)
        old=self.get_temperature(old_files,old_name)["adjusted"]["base"]["base"]
        self.assertNotIn("area_balanced",old)
        self.assertTrue(old["balanced"])
        java=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RadialLatitudeTemperatureDensity.java").read_text()
        self.assertIn("return (balanced || areaBalanced || singlePoleArea)",java)


    def test_pr25_single_pole_annulus_mode_is_opt_in(self):
        name,opts,out=self.make(latitude="radial_equal_area_single_pole",
                                latitude_strength=70)
        profile=self.get_temperature(out,name)
        # Terrain Lab may nest the radial node under its existing climate
        # budget/valley/basin decorators. Follow the actual exported graph.
        while profile["type"]!="realscale:radial_latitude_temperature":
            self.assertIn(profile["type"],(
                "realscale:climate_budget","realscale:valley_temperature",
                "realscale:basin_temperature","realscale:mountain_basin_temperature"))
            profile=profile["adjusted"] if "adjusted" in profile else profile["base"]
        self.assertTrue(profile["single_pole_area"])
        self.assertTrue(profile["area_balanced"])
        self.assertTrue(profile["balanced"])
        self.assertEqual(profile["pole_distance"],8192)
        self.assertEqual(opts["latitude"],"radial_equal_area_single_pole")
        old_name,_,old_out=self.make(latitude="radial_equal_area",
                                    latitude_strength=70,cold_limit=0)
        old=self.get_temperature(old_out,old_name)
        while old["type"]!="realscale:radial_latitude_temperature":
            old=old["adjusted"] if "adjusted" in old else old["base"]
        self.assertTrue(old["area_balanced"])
        self.assertNotIn("single_pole_area",old)
        from pathlib import Path
        java=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RadialLatitudeTemperatureDensity.java").read_text()
        self.assertIn('optionalFieldOf("single_pole_area",false)',java)


if __name__=="__main__":
    unittest.main()
