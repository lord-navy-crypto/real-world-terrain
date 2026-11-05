#!/usr/bin/env python3
"""Compare *real client* RealScale FPS log windows without fake GPU results.

Run identical, already-pregenerated RealScale worlds on the SAME computer,
with the same view distance, mode, scene and shader settings. Collect >=3
30-second steady-state windows per candidate (the first 10 seconds warm up).

    python3 qa/compare_client_fps.py baseline.log experiment.log \
      --scene enclosed_cave --mode SEALED_CAVE

This tool uses medians of per-window metrics, NOT an invented aggregate p95,
and returns descriptive differences only. It cannot confirm GPU speedups,
statistical significance, or worldgen optimizations by itself.
"""
import argparse
import json
import math
import re
import statistics
from pathlib import Path

PREFIX = "[RealScale fps profile]"
DATA = re.compile(r"\b([a-z][a-z0-9_]+)=([^\s]+)")


def parse_windows(text, *, scene, mode):
    windows = []
    for line in text.splitlines():
        if PREFIX not in line:
            continue
        entry = dict(DATA.findall(line.split(PREFIX,1)[1]))
        if entry.get("scene") != scene or entry.get("mode") != mode:
            continue
        if entry.get("method") != "post_to_post_including_frame_pacing_not_gpu":
            raise ValueError("Frame timing method mismatch")
        try:
            n = int(entry["frames"])
            fps = float(entry["mean_fps"])
            p95 = entry["p95_interval_ms"]
            slow = int(entry["slow_over_50ms"])
            if p95.endswith("+"):
                raise ValueError("p95 overflow; scene unsuitable for percentiles")
            p95 = float(p95)
        except (ValueError, KeyError) as exc:
            raise ValueError("Malformed or overflowing FPS log line") from exc
        if n < 30 or fps <= 0 or p95 <= 0 or slow < 0 or slow > n:
            raise ValueError("Invalid FPS sample counts/values")
        windows.append({"frames": n, "fps": fps, "p95_ms": p95, "slow": slow})
    return windows


def summarize(windows):
    if len(windows) < 3:
        raise ValueError("Need >=3 comparable 30s frame windows per run")
    total_frames = sum(w["frames"] for w in windows)
    return {
        "windows": len(windows),
        "frames": total_frames,
        "median_window_mean_fps": round(statistics.median(
            w["fps"] for w in windows), 3),
        "median_window_p95_ms": round(statistics.median(
            w["p95_ms"] for w in windows), 3),
        "stutters_over_50ms_per_1000_frames": round(
            sum(w["slow"] for w in windows)*1000/total_frames, 3),
    }


def compare(baseline, candidate, *, scene, mode):
    a, b = summarize(baseline), summarize(candidate)
    def pct(after,before):
        return round(100.0*(after-before)/before,2) if before > 0 else None
    return {
        "scene": scene,
        "mode": mode,
        "baseline": a,
        "candidate": b,
        "descriptive_change": {
            "median_fps_percent": pct(
                b["median_window_mean_fps"],a["median_window_mean_fps"]),
            "median_p95_frame_ms_percent": pct(
                b["median_window_p95_ms"],a["median_window_p95_ms"]),
            "stutters_per_1000_frames_delta": round(
                b["stutters_over_50ms_per_1000_frames"]
                -a["stutters_over_50ms_per_1000_frames"],3),
        },
        "caveat": (
            "Differences are descriptive, not statistically significant proof. "
            "Post-to-Post frame intervals include VSync/frame pacing and are "
            "not GPU timings. Use identical fixed routes in pregenerated worlds; "
            "do not interpret these values as chunk generation throughput."
        ),
    }


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("baseline", type=Path)
    p.add_argument("candidate", type=Path)
    p.add_argument("--scene", required=True,
                   help="Same -Drealscale.client.fps.scene in both runs")
    p.add_argument("--mode", choices=("OPEN_SURFACE","SEALED_CAVE","TRANSITION"),
                   required=True, help="Compare windows from exactly one mode")
    p.add_argument("--output", type=Path)
    args = p.parse_args(argv)
    try:
        a = parse_windows(args.baseline.read_text(encoding="utf-8",errors="replace"),
                          scene=args.scene,mode=args.mode)
        b = parse_windows(args.candidate.read_text(encoding="utf-8",errors="replace"),
                          scene=args.scene,mode=args.mode)
        result = compare(a,b,scene=args.scene,mode=args.mode)
    except (ValueError,OSError) as exc:
        p.error(str(exc))
    output=json.dumps(result,indent=2)+"\n"
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(output,encoding="utf-8")
    print(output,end="")


if __name__ == "__main__":
    main()
