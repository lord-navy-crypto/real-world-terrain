#!/usr/bin/env python3
"""Tests for reproducible FPS comparisons based on actual client log windows."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent))
from compare_client_fps import compare, parse_windows, summarize


def sample(fps, p95, *, scene="enclosed_cave", mode="SEALED_CAVE",
           n=1800, slow=3, count=3):
    return "\n".join(
        f"[RealScale fps profile] interval={i} scene={scene} mode={mode} "
        f"frames={n} mean_fps={fps} p50_interval_ms=16.5 "
        f"p95_interval_ms={p95} p99_interval_ms=32.5 "
        f"low1pct_fps_lower=30.76 slow_over_50ms={slow} "
        f"max_interval_ms=99.5 "
        f"method=post_to_post_including_frame_pacing_not_gpu"
        for i in range(1,count+1)
    )


class CompareClientFPSTest(unittest.TestCase):
    def test_fixed_scene_and_mode_excluded(self):
        body=sample(60,20) + "\n" + sample(
            100,10,scene="mountain",mode="OPEN_SURFACE")
        values=parse_windows(body,scene="enclosed_cave",mode="SEALED_CAVE")
        self.assertEqual(len(values),3)
        self.assertEqual(values[0]["fps"],60)
        self.assertEqual(values[0]["p95_ms"],20)

    def test_credible_percentages_are_descriptive(self):
        baseline=parse_windows(sample(50,25,slow=36),
                               scene="enclosed_cave",mode="SEALED_CAVE")
        upgraded=parse_windows(sample(55,20,slow=18),
                               scene="enclosed_cave",mode="SEALED_CAVE")
        report=compare(baseline,upgraded,scene="enclosed_cave",
                       mode="SEALED_CAVE")
        self.assertEqual(report["descriptive_change"]["median_fps_percent"],10)
        self.assertEqual(
            report["descriptive_change"]["median_p95_frame_ms_percent"],-20)
        self.assertEqual(
            report["descriptive_change"]["stutters_per_1000_frames_delta"],-10)
        self.assertIn("descriptive",report["caveat"])
        self.assertEqual(report["baseline"]["windows"],3)
        self.assertEqual(report["candidate"]["frames"],5400)

    def test_do_not_aggregate_p95_as_exact_global_quantile(self):
        windows=parse_windows(
            sample(50,18,count=1)+"\n"+
            sample(60,25,count=1)+"\n"+
            sample(70,35,count=1),
            scene="enclosed_cave",mode="SEALED_CAVE")
        result=summarize(windows)
        self.assertEqual(result["median_window_p95_ms"],25)
        self.assertEqual(result["median_window_mean_fps"],60)

    def test_insufficient_windows_rejected(self):
        windows=parse_windows(sample(50,20,count=2),
                               scene="enclosed_cave",mode="SEALED_CAVE")
        with self.assertRaisesRegex(ValueError,"Need >=3"):
            summarize(windows)

    def test_stutter_overflow_and_malformed_metrics_rejected(self):
        with self.assertRaisesRegex(ValueError,"overflow"):
            parse_windows(sample(40,"256+"),
                          scene="enclosed_cave",mode="SEALED_CAVE")
        with self.assertRaisesRegex(ValueError,"Invalid FPS"):
            parse_windows(sample(60,20,n=10),
                          scene="enclosed_cave",mode="SEALED_CAVE")

    def test_empty_or_other_scene_logs_fail_closed(self):
        self.assertEqual(parse_windows("some text",scene="x",mode="TRANSITION"),[])
        with self.assertRaises(ValueError):
            summarize([])


if __name__=="__main__":
    unittest.main()
