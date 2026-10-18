#!/usr/bin/env python3
"""CI guard for real client FPS A/B requirements, never fabricated measurements."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT))
from assess_real_client_render_ab import assess, REQUIRED_ENV, SCENES

def fake_log(scene,mode,fps,p95,slow=5):
    return "\n".join(
        f"[RealScale fps profile] interval={i} scene={scene} mode={mode} "
        f"frames=1800 mean_fps={fps} p50_interval_ms=16 "
        f"p95_interval_ms={p95} p99_interval_ms=48 "
        f"slow_over_50ms={slow} max_interval_ms=81 "
        "method=post_to_post_including_frame_pacing_not_gpu"
        for i in range(3)
    )

class RealClientGateTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        environment={
            "machine":"test-machine","gpu":"test-gpu",
            "gpu_driver":"test-driver","os":"test-os",
            "minecraft":"1.21.1","neoforge":"21.1.249",
            "java":"21","renderer":"sodium-neo",
            "shader":"off","resolution":"1920x1080",
            "render_distance":16,"simulation_distance":8,
            "fov":70,"vsync":False,"fps_cap":0,
            "world_seed":2,"world_preset":"realscale:tva_sacred_timeline_three_realms",
            "world_save_hash":"sha256-immutable-generated-world",
        }
        self.manifest={
            "baseline":{"variant":"parent","environment":dict(environment),"scenes":{}},
            "candidate":{"variant":"patch","environment":dict(environment),"scenes":{}},
        }
        for scene,mode in SCENES.items():
            for run in ("baseline","candidate"):
                name=run+"_"+scene+".log"
                (self.root/name).write_text(fake_log(scene,mode,60,23))
                self.manifest[run]["scenes"][scene]={"mode":mode,"log":name}

    def test_exact_three_scene_real_windows_controlled_environment(self):
        output=assess(self.manifest,self.root)
        self.assertEqual(output["status"],"NO_OBSERVED_REGRESSION_NO_PROVEN_GAIN")
        self.assertEqual(set(output["scene_results"]),set(SCENES))
        self.assertFalse(output["regression_scenes"])
        self.assertIn("NOT GPU timings",output["caveat"])
        self.assertIn("world_save_hash",REQUIRED_ENV)

    def test_gpu_mismatch_fails_closed(self):
        self.manifest["candidate"]["environment"]["gpu"]="different-gpu"
        with self.assertRaisesRegex(ValueError,"gpu"):
            assess(self.manifest,self.root)

    def test_world_seed_and_save_mismatch_fails_closed(self):
        self.manifest["candidate"]["environment"]["world_seed"]=42
        with self.assertRaisesRegex(ValueError,"world_seed"):
            assess(self.manifest,self.root)
        self.manifest["candidate"]["environment"]["world_seed"]=2
        self.manifest["candidate"]["environment"]["world_save_hash"]="other"
        with self.assertRaisesRegex(ValueError,"world_save_hash"):
            assess(self.manifest,self.root)

    def test_vsync_or_fps_cap_fails_closed(self):
        self.manifest["baseline"]["environment"]["vsync"]=True
        with self.assertRaisesRegex(ValueError,"VSync"):
            assess(self.manifest,self.root)
        self.manifest["baseline"]["environment"]["vsync"]=False
        self.manifest["baseline"]["environment"]["fps_cap"]=120
        with self.assertRaisesRegex(ValueError,"FPS limit"):
            assess(self.manifest,self.root)

    def test_missing_cave_exit_fails_closed(self):
        del self.manifest["candidate"]["scenes"]["cave_exit"]
        with self.assertRaisesRegex(ValueError,"cave_exit"):
            assess(self.manifest,self.root)

    def test_insufficient_real_windows_cannot_pass(self):
        path=self.root/"candidate_deep_cave.log"
        path.write_text(fake_log("deep_cave","SEALED_CAVE",60,23).splitlines()[0])
        with self.assertRaisesRegex(ValueError,"fewer than three"):
            assess(self.manifest,self.root)

    def test_higher_fps_cannot_mask_worse_cave_exit_stutters(self):
        (self.root/"candidate_cave_exit.log").write_text(
            fake_log("cave_exit","TRANSITION",90,17,slow=30))
        output=assess(self.manifest,self.root)
        self.assertEqual(output["status"],"REVIEW_REGRESSION")
        self.assertIn("cave_exit",output["regression_scenes"])

    def test_descriptive_gain_is_not_claimed_proven(self):
        (self.root/"candidate_deep_cave.log").write_text(
            fake_log("deep_cave","SEALED_CAVE",70,19))
        output=assess(self.manifest,self.root)
        self.assertEqual(output["status"],"DESCRIPTIVE_GAIN_NOT_PROVEN")
        self.assertIn("deep_cave",output["improved_scenes_descriptive_only"])

if __name__=="__main__":
    unittest.main()
