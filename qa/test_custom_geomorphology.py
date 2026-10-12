#!/usr/bin/env python3
"""Validate actual resource graphs for codec-registered erosion, ridges and strata."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("labgeo",ROOT/"scripts/terrain_lab.py")
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)

class CustomGeoTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root=Path(tmp.name)
        real=ROOT/"neoforge/src/main/resources"
        for folder in ("data/realscale/worldgen","data/minecraft/tags/worldgen/world_preset","assets/minecraft/lang"):
            shutil.copytree(real/folder,self.root/folder)

    def test_opt_in_codec_graph_used_by_actual_world(self):
        for family,(_,secondary) in lab.FAMILIES.items():
            with self.subTest(family=family):
                name,opts,_=lab.apply(self.root,family,1.0,0,50,6,
                                       ridge=18,erosion=12,strata=8)
                src=lab.GEO_ROOT/"density_function/terrain_lab"/name
                h=lab.jread(self.root,src/"height_signal.json")
                geom=h["argument1"]["argument2"]
                self.assertEqual(geom["type"],"realscale:ridge_erosion")
                self.assertEqual(geom["ridge_signal"],f"realscale:{secondary}/ridges")
                self.assertEqual(geom["erosion_signal"],f"realscale:{secondary}/erosion")
                self.assertEqual(geom["ridge_gain"],.072)
                self.assertEqual(geom["erosion_gain"],.036)
                terrain=lab.jread(self.root,src/"terrain_density.json")
                self.assertEqual(terrain["type"],"realscale:strata")
                self.assertEqual(terrain["strength"],.016)
                self.assertEqual(terrain["period"],64)
                self.assertEqual(terrain["warp"],f"realscale:{secondary}/erosion")
                self.assertIn(src.name,f"terrain_lab/{name}")
                noise=lab.jread(self.root,lab.GEO_ROOT/"noise_settings"/(name+".json"))
                self.assertEqual(noise["sea_level"],63)
                self.assertEqual(noise["noise_router"]["final_density"]["input"],"realscale:terrain_lab/"+name+"/terrain_density")
                self.assertEqual(opts["ridge"],18)

    def test_zero_values_preserve_v15_profile_hash_and_files(self):
        one,options,files=lab.files_for(self.root,"landscape",1.0,0,50,6)
        two,options2,files2=lab.files_for(self.root,"landscape",1.0,0,50,6,ridge=0,erosion=0,strata=0)
        self.assertEqual(one,two)
        self.assertEqual(files,files2)
        self.assertEqual(options,options2)
        h=next(x for k,x in files.items() if str(k).endswith("height_signal.json"))
        self.assertNotIn("ridge_erosion",json.dumps(h))

    def test_invalid_amplitudes_rejected(self):
        for kwargs in (dict(ridge=41),dict(erosion=31),dict(strata=21),dict(ridge=-1)):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                lab.files_for(self.root,"landscape",1.0,0,50,6,**kwargs)
        with self.assertRaises(ValueError):
            lab.files_for(self.root,"realism_max",1.0,0,50,6,ridge=10)

    def test_mod_dependency_and_codec_registration(self):
        base=ROOT/"neoforge/src/main"
        gradle=(ROOT/"neoforge/build.gradle").read_text()
        meta=(base/"resources/META-INF/neoforge.mods.toml").read_text()
        entry=(base/"java/org/realscale/neoforge/RealScaleMod.java").read_text()
        self.assertIn("maven.modrinth:XaDC71GB:u0UUKAE1",gradle)
        self.assertIn('modId="lithostitched"',meta)
        self.assertIn('side="BOTH"',meta)
        for function in ("ridge_erosion","strata"):
            self.assertIn('"'+function+'"',entry)
        for cls in ("RidgeErosionDensity","StrataDensity"):
            body=(base/"java/org/realscale/neoforge"/(cls+".java")).read_text()
            self.assertIn("HOLDER_HELPER_CODEC",body)
            self.assertIn("mapAll(DensityFunction.Visitor visitor)",body)
            self.assertIn("KeyDispatchDataCodec",body)

if __name__=="__main__":unittest.main()
