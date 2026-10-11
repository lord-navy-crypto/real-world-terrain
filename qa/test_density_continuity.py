#!/usr/bin/env python3
"""Regression guards for ALL eight worldgen presets.

JSON structural checks are not a substitute for loaded Minecraft chunks or visual tests.
"""
import json
import math
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data/realscale/worldgen"
MODES=("continental","georeal_ultimate","grand_canyon","himalayan",
       "realistic_mountains","ultimate","realism_max","usgs_big_sur")
PROCEDURAL=tuple(x for x in MODES if x!="usgs_big_sur")

def load(path):
    return json.loads((ROOT/path).read_text())

def all_nodes(x):
    if isinstance(x,dict):
        yield x
        for v in x.values():yield from all_nodes(v)
    elif isinstance(x,list):
        for v in x:yield from all_nodes(v)

class WorldgenRegression(unittest.TestCase):
    def test_all_seven_procedural_heights_are_interpolated(self):
        for p in PROCEDURAL:
            with self.subTest(preset=p):
                height=load(f"density_function/{p}/height_signal.json")
                self.assertEqual(height["type"],"minecraft:interpolated")
                # Do not directly export flat_cache's four-block stair-step as the terrain height.
                self.assertNotEqual(height["argument"].get("type"),"minecraft:cache_2d")
                if p=="realism_max":
                    self.assertEqual(load("density_function/realism_max/height_signal_raw.json")["type"],"minecraft:flat_cache")
                else:
                    self.assertTrue(any(n.get("type")=="minecraft:flat_cache" for n in all_nodes(height)))
                settings=load(f"noise_settings/{p}.json")
                self.assertEqual(settings["noise"]["size_horizontal"],4)
                self.assertEqual(settings["noise"]["size_vertical"],1)

    def test_realism_smooth_monotone_summit(self):
        h=load("density_function/realism_max/height_signal.json")
        self.assertEqual(h["type"],"minecraft:interpolated")
        spline=h["argument"]["spline"]
        self.assertEqual(spline["coordinate"],"realscale:realism_max/height_signal_raw")
        pts=spline["points"]
        for a,b in zip(pts,pts[1:]):
            self.assertLess(a["location"],b["location"])
            self.assertLess(a["value"],b["value"])
        self.assertGreater(pts[-1]["derivative"],0)
        raw=load("density_function/realism_max/height_signal_raw.json")
        self.assertEqual(raw["type"],"minecraft:flat_cache")
        # Historical costly workaround was cache_2d at every mountain/noise node.
        for f in (ROOT/"density_function/realism_max").glob("*field.json"):
            self.assertEqual(load(f.relative_to(ROOT))["type"],"minecraft:flat_cache")

    def test_all_eight_preset_caves_are_3d_and_clipped(self):
        core=load("density_function/realism_max/cave_core.json")
        noise=[n for n in all_nodes(core) if n.get("type")=="minecraft:noise"]
        self.assertGreaterEqual(len(noise),3)
        self.assertTrue(all(n.get("y_scale",0)>0 for n in noise))
        for p in MODES:
            with self.subTest(preset=p):
                router=load(f"noise_settings/{p}.json")["noise_router"]
                terrain=f"realscale:{p}/terrain_density"
                final=router["final_density"]
                self.assertEqual(final["type"],"minecraft:range_choice")
                self.assertEqual(final["input"],terrain)
                self.assertEqual(final["when_out_of_range"],terrain)
                self.assertEqual(final["when_in_range"]["type"],"minecraft:min")
                self.assertIn(terrain,final["when_in_range"].values())
                self.assertEqual(final["when_in_range"]["argument2"],f"realscale:{p}/cave_density")
                self.assertGreater(final["min_inclusive"],0)
                self.assertLess(final["min_inclusive"],final["max_exclusive"])
                cave=load(f"density_function/{p}/cave_density.json")
                self.assertIn(terrain,json.dumps(cave))
                self.assertTrue(any(n.get("type")=="minecraft:y_clamped_gradient" and
                    n.get("from_y")==-64 for n in all_nodes(cave)))
                self.assertEqual(router["initial_density_without_jaggedness"],
                    f"realscale:{p}/preliminary_density")

    def test_cave_network_is_deliberately_wider_but_still_shielded(self):
        core=load("density_function/realism_max/cave_core.json")
        tunnel=[n for n in all_nodes(core)
                if n.get("type")=="minecraft:add"
                and isinstance(n.get("argument1"),dict)
                and n["argument1"].get("type")=="minecraft:abs"]
        self.assertEqual(len(tunnel),2)
        self.assertTrue(all(n["argument2"]==-0.19 for n in tunnel))
        cheese=[n for n in all_nodes(core)
                if n.get("type")=="minecraft:add"
                and isinstance(n.get("argument1"),dict)
                and n["argument1"].get("noise")=="minecraft:cave_cheese"]
        self.assertEqual(len(cheese),1)
        self.assertAlmostEqual(cheese[0]["argument2"],0.37)
        # The cave router's solid-depth gate is intentionally unchanged.
        for p in MODES:
            f=load(f"noise_settings/{p}.json")["noise_router"]["final_density"]
            self.assertEqual((f["min_inclusive"],f["max_exclusive"]),(0.25,4))

    def test_continental_cave_overcarving_is_locally_limited(self):
        # Continental exposed >26% underground voids in real saved chunks:
        # preserve widened spaghetti tunnels but suppress oversized cheese chambers.
        core=load("density_function/continental/cave_core.json")
        cheese=[n for n in all_nodes(core)
                if n.get("type")=="minecraft:add"
                and isinstance(n.get("argument1"),dict)
                and n["argument1"].get("noise")=="minecraft:cave_cheese"]
        self.assertEqual(len(cheese),1)
        self.assertEqual(cheese[0]["argument2"],0.62)
        c=load("density_function/continental/cave_density.json")
        self.assertIn("realscale:continental/cave_core",json.dumps(c))

    def test_no_broken_density_references(self):
        base=ROOT/"density_function"
        for f in base.rglob("*.json"):
            obj=load(f.relative_to(ROOT))
            def refs(o):
                if isinstance(o,str) and o.startswith("realscale:"):yield o
                elif isinstance(o,list):
                    for v in o:yield from refs(v)
                elif isinstance(o,dict):
                    for k,v in o.items():
                        # type may be a registered density codec, e.g. realscale:dem_density
                        if k!="type":yield from refs(v)
            for reference in refs(obj):
                self.assertTrue((base/(reference.split(":",1)[1]+".json")).is_file(),
                                f"{f}: {reference}")

    def test_all_presets_have_valid_preset_and_terrain(self):
        for p in MODES:
            with self.subTest(preset=p):
                preset=load(f"world_preset/{p}.json")
                dim=preset["dimensions"]["minecraft:overworld"]
                self.assertEqual(dim["generator"]["type"],"minecraft:noise")
                self.assertEqual(dim["generator"]["settings"],f"realscale:{p}")
                s=load(f"noise_settings/{p}.json")
                self.assertEqual(s["default_block"]["Name"],"minecraft:stone")
                self.assertEqual(s["sea_level"],63)
                self.assertTrue(s["aquifers_enabled"])
                self.assertLessEqual(s["noise"]["min_y"]+s["noise"]["height"],2032)

    def test_original_planar_noise_source_preserved(self):
        """39 original planar source frequencies are preserved; spatial smoothing occurs at interpolation cells."""
        archived=Path(__file__).resolve().parents[1]/"archive/v0.6/datapack/data/realscale/worldgen/density_function"
        revised=ROOT/"density_function"
        count=0
        def planar(x):
            return [(n["type"],n["noise"],n["xz_scale"],n.get("y_scale",0))
                    for n in all_nodes(x)
                    if n.get("type") in ("minecraft:noise","minecraft:shifted_noise")
                    and n.get("y_scale")==0 and isinstance(n.get("xz_scale"),(int,float))]
        for preset in PROCEDURAL:
            for old in (archived/preset).glob("*.json"):
                new=revised/preset/old.name
                if not new.exists():continue
                aa=planar(json.loads(old.read_text()))
                bb=planar(json.loads(new.read_text()))
                self.assertEqual(len(aa),len(bb),(preset,old.name))
                for a,b in zip(aa,bb):
                    self.assertEqual(a[:2],b[:2])
                    self.assertAlmostEqual(a[2],b[2],places=7,
                                           msg=f"{preset}/{old.name}")
                    self.assertEqual(a[3],b[3])
                    count+=1
        self.assertEqual(count,39)

    def test_interpolation_is_not_piecewise_constant_at_quart_boundaries(self):
        # Mathematical property of bilinear interpolation, not actual in-game measurement.
        corners={(0,0):100,(4,0):104,(0,4):108,(4,4):112,
                 (8,0):108,(8,4):116}
        def interp(x,z):
            ix=int(math.floor(x/4))*4;iz=int(math.floor(z/4))*4
            a=(x-ix)/4;b=(z-iz)/4
            return ((1-a)*(1-b)*corners[(ix,iz)]+a*(1-b)*corners[(ix+4,iz)]
                   +(1-a)*b*corners[(ix,iz+4)]+a*b*corners[(ix+4,iz+4)])
        self.assertLess(abs(interp(3.999,2)-interp(4.001,2)),.01)
        self.assertNotEqual(interp(1,1),interp(2,1))

if __name__=="__main__":unittest.main()
