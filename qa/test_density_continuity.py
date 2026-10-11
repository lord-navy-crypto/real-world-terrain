#!/usr/bin/env python3
"""Source topology regression tests; does NOT substitute for real Minecraft world QA."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent/"neoforge/src/main/resources/data/realscale/worldgen"
def load(path):return json.loads((ROOT/path).read_text())
def nodes(x):
    if isinstance(x,dict):
        yield x
        for v in x.values():yield from nodes(v)
    elif isinstance(x,list):
        for v in x:yield from nodes(v)
class TerrainRegression(unittest.TestCase):
    def test_realism_has_no_4x4_flat_cache(self):
        for p in sorted((ROOT/"density_function/realism_max").glob("*.json")):
            self.assertNotIn("minecraft:flat_cache",[n.get("type") for n in nodes(load(p.relative_to(ROOT)))],p)
    def test_monotonic_soft_summit(self):
        h=load("density_function/realism_max/height_signal.json")
        self.assertEqual(h["type"],"minecraft:cache_2d")
        s=h["argument"]["spline"];self.assertEqual(s["coordinate"],"realscale:realism_max/height_signal_raw")
        for a,b in zip(s["points"],s["points"][1:]):
            self.assertLess(a["location"],b["location"])
            self.assertLess(a["value"],b["value"])
        self.assertGreater(s["points"][-1]["derivative"],0)
    def test_cave_has_3d_noise_and_protected_roof(self):
        core=load("density_function/realism_max/cave_core.json")
        noises=[n for n in nodes(core) if n.get("type")=="minecraft:noise"]
        self.assertGreaterEqual(len(noises),3)
        self.assertTrue(all(n["y_scale"]>0 for n in noises))
        for p in ("realism_max","usgs_big_sur"):
            router=load(f"noise_settings/{p}.json")["noise_router"]
            self.assertEqual(router["final_density"],dict(type="minecraft:min",argument1=f"realscale:{p}/terrain_density",argument2=f"realscale:{p}/cave_density"))
            cav=load(f"density_function/{p}/cave_density.json")
            self.assertIn(f"realscale:{p}/terrain_density",json.dumps(cav))
            self.assertTrue(any(n.get("type")=="minecraft:y_clamped_gradient" and n.get("from_y")==-64 for n in nodes(cav)))
    def test_no_broken_realscale_density_refs(self):
        base=ROOT/"density_function"
        for f in base.rglob("*.json"):
            ob=load(f.relative_to(ROOT))
            strings=[]
            def collect(o):
                if isinstance(o,str) and o.startswith("realscale:"):strings.append(o)
                elif isinstance(o,list):
                    for v in o:collect(v)
                elif isinstance(o,dict):
                    for k,v in o.items():
                        if k!="type":collect(v)
            collect(ob)
            for s in strings:self.assertTrue((base/(s.split(":",1)[1]+".json")).is_file(),(f,s))
if __name__=="__main__":unittest.main()
