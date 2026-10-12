#!/usr/bin/env python3
"""RealScale Terrain Lab profiles must create new worldgen IDs, never edit old IDs."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/"scripts/terrain_lab.py"
spec=importlib.util.spec_from_file_location("terrain_lab",SCRIPT)
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)

class TerrainLabTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        src=ROOT/"neoforge/src/main/resources"
        for folder in ("data/realscale/worldgen","data/minecraft/tags/worldgen/world_preset","assets/minecraft/lang"):
            shutil.copytree(src/folder,self.root/folder)
        self.originals={
            name:hashlib.sha256((self.root/name).read_bytes()).hexdigest()
            for name in (
                "data/realscale/worldgen/noise_settings/realism_max.json",
                "data/realscale/worldgen/density_function/realism_max/terrain_density.json",
                "data/realscale/worldgen/world_preset/realism_max.json",
                "data/realscale/worldgen/noise_settings/usgs_big_sur.json"
            )
        }

    def test_all_seven_procedural_profiles_build_independently(self):
        for preset in lab.LEGACY_EDITABLE:
            with self.subTest(mode=preset):
                name,_,paths=lab.apply(self.root,preset,1.12,16,60,9)
                settings=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name+".json"))
                prefix="realscale:terrain_lab/"+name
                self.assertEqual(settings["sea_level"],63)
                self.assertTrue(settings["aquifers_enabled"])
                self.assertEqual(settings["noise_router"]["depth"],prefix+"/terrain_density")
                self.assertEqual(settings["noise_router"]["initial_density_without_jaggedness"],prefix+"/preliminary_density")
                final=settings["noise_router"]["final_density"]
                self.assertEqual(final["input"],prefix+"/terrain_density")
                self.assertEqual(final["when_in_range"]["argument2"],prefix+"/cave_density")
                height=lab.jread(self.root,lab.GEO_ROOT/"density_function/terrain_lab"/name/"height_signal.json")
                self.assertEqual(height["argument1"]["argument2"],"realscale:"+preset+"/height_signal")
                self.assertEqual(height["argument2"],16/128)
                caves=lab.jread(self.root,lab.GEO_ROOT/"density_function/terrain_lab"/name/"cave_density.json")
                self.assertEqual(caves["argument2"],-0.04)
                self.assertIn(prefix+"/terrain_density",json.dumps(caves))
                world=lab.jread(self.root,lab.GEO_ROOT/"world_preset"/(name+".json"))
                self.assertEqual(world["dimensions"]["minecraft:overworld"]["generator"]["settings"],"realscale:"+name)
                self.assertTrue(name.endswith("_g9"))
                self.assertTrue((self.root/"assets/minecraft/lang/zh_cn.json").exists())
                self.assertEqual(len(paths),10)


    def test_ui_really_exposes_only_three_merged_families(self):
        self.assertEqual(lab.EDITABLE,("landscape","alpine","canyon"))
        native=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleTerrainLabClient.java").read_text()
        self.assertIn('"landscape", "alpine", "canyon"',native)
        self.assertIn('Secondary Landform Mix (%)',native)
        self.assertIn('blendPercent',native)
        self.assertNotIn('"realism_max", "georeal_ultimate", "himalayan", "grand_canyon",',native)
        original=lab.jread(self.root,lab.TAG_ROOT/"extended.json")
        self.assertEqual(len([x for x in original["values"] if not x.startswith("realscale:lab_")]),8)
        self.assertEqual(len([x for x in lab.jread(self.root,lab.TAG_ROOT/"normal.json")["values"] if not x.startswith("realscale:lab_")]),4)

    def test_merged_families_blend_actual_height_density_functions(self):
        for family,(base,secondary) in lab.FAMILIES.items():
            with self.subTest(family=family):
                name,opts,_=lab.apply(self.root,family,1.0,0,50,6,blend_percent=35)
                h=lab.jread(self.root,lab.GEO_ROOT/"density_function/terrain_lab"/name/"height_signal.json")
                mix=h["argument1"]["argument2"]
                self.assertEqual(mix["type"],"minecraft:add")
                self.assertEqual(mix["argument1"]["argument1"],0.65)
                self.assertEqual(mix["argument1"]["argument2"],f"realscale:{base}/height_signal")
                self.assertEqual(mix["argument2"]["argument1"],0.35)
                self.assertEqual(mix["argument2"]["argument2"],f"realscale:{secondary}/height_signal")
                self.assertEqual(opts["blend_percent"],35)

    def test_new_high_mountain_caves_are_thin_and_protected(self):
        name,opts,_=lab.apply(self.root,"alpine",1.0,0,65,6,blend_percent=25)
        self.assertEqual(opts["mountain_caves_revision"],2)
        settings=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name+".json"))
        final=settings["noise_router"]["final_density"]
        self.assertEqual(final["type"],"minecraft:min")
        extra=final["argument2"]
        self.assertEqual(extra["type"],"minecraft:max")
        guarded=extra["argument2"]
        self.assertEqual(guarded["type"],"minecraft:add")
        # Ensure the mountain tunnel uses smooth, bounded density penalties,
        # rather than hard density window edges that cause abrupt seams.
        self.assertEqual(guarded["argument1"]["argument2"]["type"],"minecraft:mul")
        self.assertEqual(guarded["argument2"]["type"],"minecraft:mul")
        self.assertEqual(extra["argument1"]["type"],"minecraft:mul")
        self.assertEqual(settings["sea_level"],63)
        self.assertTrue(settings["aquifers_enabled"])

    def test_low_cave_activity_does_not_inject_mountain_tunnels(self):
        name,opts,_=lab.apply(self.root,"alpine",1.0,0,50,6)
        self.assertNotIn("mountain_caves_revision",opts)
        settings=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name+".json"))
        self.assertEqual(settings["noise_router"]["final_density"]["type"],
                         "minecraft:range_choice")

    def test_legacy_hash_remains_stable(self):
        name,opts,_=lab.files_for(self.root,"realism_max",1.0,0,50,6,True)
        self.assertNotIn("blend_percent",opts)
        self.assertTrue(name.endswith("_g6"))

    def test_yacl_biome_cooling_persists_into_new_world_noise_settings(self):
        draft=self.root/"biomes-yacl.json"
        draft.write_text(json.dumps({
            "preset":"landscape","blendPercent":30,"reliefPercent":100,
            "elevation":0,"caves":50,"riverGrade":6,"inland":True,
            "ridgePercent":0,"erosionPercent":0,"strataPercent":0,
            "climatePercent":75
        }))
        before=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_landscape_*.json"))
        self.assertEqual(lab.main(["--root",str(self.root),"--from-yacl",str(draft)]),0)
        created=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_landscape_*.json"))-before
        self.assertEqual(len(created),1)
        data=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/created.pop().name)
        self.assertIn("biome_temperature",data["noise_router"]["temperature"])

    def test_saved_yacl_valley_warming_becomes_new_temperature_function(self):
        draft=self.root/"valley-yacl.json"
        draft.write_text(json.dumps({
            "preset":"alpine","blendPercent":25,"reliefPercent":100,
            "elevation":0,"caves":50,"riverGrade":6,"inland":True,
            "ridgePercent":0,"erosionPercent":0,"strataPercent":0,
            "climatePercent":65,"valleyPercent":35
        }))
        before=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_alpine_*.json"))
        self.assertEqual(lab.main(["--root",str(self.root),"--from-yacl",str(draft)]),0)
        new=set((self.root/lab.GEO_ROOT/"noise_settings").glob("lab_alpine_*.json"))-before
        self.assertEqual(len(new),1)
        name=new.pop().stem
        output=lab.jread(self.root,lab.GEO_ROOT/"density_function/terrain_lab"/name/"biome_temperature.json")
        self.assertEqual(output["type"],"realscale:valley_temperature")
        self.assertEqual(output["warmth"],.1225)
        client=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleTerrainLabClient.java").read_text()
        self.assertIn('Valley Warming (Optional)',client)
        self.assertIn("valleyPercent = 0",client)
        entry=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
        self.assertIn('"valley_temperature"',entry)

    def test_new_profile_cannot_change_historical_modes(self):
        lab.apply(self.root,"realism_max",1.2,30,70,6)
        for name,digest in self.originals.items():
            self.assertEqual(hashlib.sha256((self.root/name).read_bytes()).hexdigest(),digest)

    def test_more_than_one_lab_mode_without_overwriting(self):
        a,*_=lab.apply(self.root,"realism_max",1.0,0,50,6)
        b,*_=lab.apply(self.root,"realism_max",1.1,0,50,6)
        self.assertNotEqual(a,b)
        normal=lab.jread(self.root,lab.TAG_ROOT/"normal.json")
        expected=["realscale:"+x for x in lab.NORMAL]
        self.assertEqual(normal["values"][:len(expected)],expected)
        self.assertIn("realscale:"+a,normal["values"])
        self.assertIn("realscale:"+b,normal["values"])
        self.assertEqual(len(normal["values"]),len(set(normal["values"])))

    def test_same_profile_is_idempotent(self):
        name,*_=lab.apply(self.root,"grand_canyon",0.9,-12,35,3)
        path=self.root/lab.GEO_ROOT/"noise_settings"/(name+".json")
        old=path.read_bytes()
        name2,*_=lab.apply(self.root,"grand_canyon",0.9,-12,35,3)
        self.assertEqual(name,name2)
        self.assertEqual(path.read_bytes(),old)

    def test_optional_river_off_is_immutable_world_id(self):
        name,_,_=lab.apply(self.root,"himalayan",1.0,0,50,0)
        self.assertTrue(name.endswith("_g0"))

    def test_dem_pilot_rejects_modification(self):
        with self.assertRaisesRegex(ValueError,"measured DEM"):
            lab.apply(self.root,"usgs_big_sur",1.1,8,60,3)

    def test_rejects_out_of_bound_values(self):
        for vals in ((1.5,0,50,6),(1.0,90,50,6),(1.0,0,100,6),(1.0,0,50,5)):
            with self.subTest(values=vals), self.assertRaises(ValueError):
                lab.apply(self.root,"realism_max",*vals)


    def test_yacl_saved_draft_is_compiled_before_next_build(self):
        draft=self.root/"realscale-terrain-lab.json"
        draft.write_text(json.dumps({
            "preset":"realism_max","reliefPercent":112,
            "elevation":12,"caves":55,"riverGrade":3,"inland":True}))
        folder=self.root/lab.GEO_ROOT/"noise_settings"
        baseline=set(folder.glob("lab_realism_max_*.json"))
        self.assertEqual(lab.main(["--root",str(self.root),"--from-yacl",str(draft)]),0)
        new_files=set(folder.glob("lab_realism_max_*.json"))-baseline
        self.assertEqual(len(new_files),1)
        saved=new_files.pop()
        self.assertIn("_g3",saved.name)
        self.assertEqual(json.loads(saved.read_text())["sea_level"],63)
        self.assertIn("generator.realscale.lab_realism_max_",(
            self.root/lab.LABEL_ROOT/"en_us.json").read_text())

    def test_invalid_yacl_draft_does_not_generate(self):
        draft=self.root/"draft.json"
        draft.write_text(json.dumps({
            "preset":"usgs_big_sur","reliefPercent":112,
            "elevation":12,"caves":55,"riverGrade":9,"inland":True}))
        folder=self.root/lab.GEO_ROOT/"noise_settings"
        baseline=set(folder.glob("lab_*.json"))
        with self.assertRaises(ValueError):
            lab.main(["--root",str(self.root),"--from-yacl",str(draft)])
        self.assertEqual(set(folder.glob("lab_*.json")),baseline)

    def test_yacl_integration_does_not_require_client_mod_on_server(self):
        main=ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java"
        client=ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleTerrainLabClient.java"
        metadata=(ROOT/"neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text()
        build=(ROOT/"neoforge/build.gradle").read_text()
        self.assertNotIn("yacl3",main.read_text())
        self.assertIn("dist = Dist.CLIENT",client.read_text())
        self.assertIn("IConfigScreenFactory",client.read_text())
        self.assertIn('side="CLIENT"',metadata)
        self.assertIn("maven.modrinth:1eAoo2KR:h8Px82QV",build)
        self.assertIn("prepareTerrainLabDraft",build)
        self.assertIn("dependsOn tasks.named('prepareTerrainLabDraft')",build)

    def test_merged_blend_bounds_are_enforced(self):
        for mix in (-1,51):
            with self.assertRaises(ValueError):
                lab.apply(self.root,"landscape",1.0,0,50,6,blend_percent=mix)

    def test_dry_run_does_not_create_new_preset(self):
        name,_,_=lab.apply(self.root,"realism_max",0.85,0,20,3,dry_run=True)
        self.assertFalse((self.root/lab.GEO_ROOT/"noise_settings"/(name+".json")).exists())

    def test_spawn_selector_has_explicit_inland_control(self):
        name,_,_=lab.apply(self.root,"realism_max",1.0,0,50,6,inland=True)
        result=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name+".json"))
        self.assertTrue(all(s["continentalness"][0]==0.12 for s in result["spawn_target"]))
        name2,_,_=lab.apply(self.root,"realism_max",1.0,0,50,6,inland=False)
        result2=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name2+".json"))
        self.assertEqual(result2["spawn_target"][0]["continentalness"][0],-0.11)

if __name__=="__main__":unittest.main()
