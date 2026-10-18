#!/usr/bin/env python3
"""Fail-closed REAL-client render scene A/B gate, not synthetic FPS claims.

Input: one JSON manifest with baseline and candidate, whose environments
must be identical. Require >=3 real 30s windows for EACH scene, collected
by RealScaleClientFrameProfiler on a running Minecraft client. Never guess
that a profiler, priority classifier or static CI proves an FPS speedup.

Usage:
    python3 qa/assess_real_client_render_ab.py run_manifest.json \
        --output build/client_render_ab.json
"""
import argparse
import json
import sys
from pathlib import Path
from compare_client_fps import compare, parse_windows

REQUIRED_ENV = (
    "machine", "gpu", "gpu_driver", "os", "minecraft", "neoforge",
    "java", "renderer", "shader", "resolution", "render_distance",
    "simulation_distance", "fov", "vsync", "fps_cap",
    "world_seed", "world_preset", "world_save_hash",
)
SCENES = {
    "open_surface": "OPEN_SURFACE",
    "deep_cave": "SEALED_CAVE",
    "cave_exit": "TRANSITION",
}

def _validate_env(baseline,candidate):
    for label,part in (("baseline",baseline),("candidate",candidate)):
        env=part.get("environment")
        if not isinstance(env,dict):
            raise ValueError(label+" needs a complete environment object")
        missing=[k for k in REQUIRED_ENV
                 if k not in env or env[k] is None or env[k]==""]
        if missing:
            raise ValueError(label+" is missing A/B controls: "+", ".join(missing))
        if env["vsync"] is not False or env["fps_cap"] not in (0,"uncapped"):
            raise ValueError("Both runs must disable VSync and FPS limit")
    a,b=baseline["environment"],candidate["environment"]
    changed=[k for k in REQUIRED_ENV if a[k]!=b[k]]
    if changed:
        raise ValueError("A/B hardware, world or view controls differ: "+
                         ", ".join(changed))

def assess(manifest, root):
    if not isinstance(manifest,dict):
        raise ValueError("Expected JSON object")
    base=manifest.get("baseline",{})
    cand=manifest.get("candidate",{})
    if not isinstance(base,dict) or not isinstance(cand,dict):
        raise ValueError("Invalid baseline/candidate")
    _validate_env(base,cand)
    for key,part in (("baseline",base),("candidate",cand)):
        if not part.get("variant"):
            raise ValueError(key+" requires an explicit build/variant label")
        if not isinstance(part.get("scenes"),dict):
            raise ValueError(key+" missing scene logs")

    results={}
    for scene,mode in SCENES.items():
        pair=[]
        for label,part in (("baseline",base),("candidate",cand)):
            entry=part["scenes"].get(scene)
            if not isinstance(entry,dict) or entry.get("mode")!=mode:
                raise ValueError(label+" missing scene "+scene+" / "+mode)
            raw=entry.get("log")
            if not isinstance(raw,str) or not raw:
                raise ValueError(label+" has no "+scene+" log path")
            path=Path(raw)
            if not path.is_absolute():
                path=root/path
            text=path.read_text(encoding="utf-8",errors="replace")
            samples=parse_windows(text,scene=scene,mode=mode)
            if len(samples)<3:
                raise ValueError(label+" "+scene+
                                 " has fewer than three real client windows")
            pair.append(samples)
        results[scene]=compare(pair[0],pair[1],scene=scene,mode=mode)

    # Explicit *review thresholds* only. A non-regression verdict DOES NOT
    # establish significance or increased rendered FPS, and can never be
    # produced without valid observed client logs.
    regressions=[]
    improvements=[]
    for scene,report in results.items():
        delta=report["descriptive_change"]
        if (delta["median_fps_percent"] < -5.0 or
                delta["median_p95_frame_ms_percent"] > 5.0 or
                delta["stutters_per_1000_frames_delta"] > 2.0):
            regressions.append(scene)
        if (delta["median_fps_percent"] >= 5.0 or
                delta["median_p95_frame_ms_percent"] <= -5.0):
            improvements.append(scene)
    verdict=("REVIEW_REGRESSION" if regressions else
             "DESCRIPTIVE_GAIN_NOT_PROVEN" if improvements else
             "NO_OBSERVED_REGRESSION_NO_PROVEN_GAIN")
    return {
        "status":verdict,
        "baseline_variant":base["variant"],
        "candidate_variant":cand["variant"],
        "world_save_hash":base["environment"]["world_save_hash"],
        "scene_results":results,
        "regression_scenes":regressions,
        "improved_scenes_descriptive_only":improvements,
        "caveat":("Require manual visual parity review and repeated hardware A/B "
                  "before approving renderer changes. Post-to-post frame "
                  "intervals are NOT GPU timings; no claim of statistical "
                  "significance or GPU FPS boost is made."),
    }

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("manifest",type=Path)
    p.add_argument("--output",type=Path)
    args=p.parse_args(argv)
    try:
        manifest=json.loads(args.manifest.read_text(encoding="utf-8"))
        result=assess(manifest,args.manifest.resolve().parent)
    except (ValueError,OSError,KeyError,json.JSONDecodeError) as exc:
        p.error(str(exc))
    output=json.dumps(result,indent=2,sort_keys=True)+"\n"
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(output,encoding="utf-8")
    print(output,end="")
    if result["regression_scenes"]:
        return 2
    return 0

if __name__=="__main__":
    sys.exit(main())
