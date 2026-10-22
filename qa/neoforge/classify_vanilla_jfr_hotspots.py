#!/usr/bin/env python3
"""Exclusive JFR CPU leaf-family counts for real 1.21.1 worldgen.

Never add overlapping inclusive stack counts. Report sample counts rather
than claiming nanos or a provable share of isolated chunk-worldgen walltime.
"""
import collections
import json
import pathlib
import subprocess
import sys

from summarize_worldgen_jfr import method_name

def family(method):
    if not method:return "non_java_or_missing"
    if (".synth.PerlinNoise." in method or
            ".synth.ImprovedNoise." in method or
            ".synth.NormalNoise." in method):
        return "perlin_improved_noise"
    if ".levelgen.Beardifier." in method:
        return "beardifier"
    if ".levelgen.Aquifer$NoiseBasedAquifer." in method:
        return "aquifer"
    if method.startswith("net.minecraft.world.level.levelgen."):
        return "other_vanilla_worldgen"
    if method.startswith("org.realscale."):
        return "realscale_custom"
    if method.startswith("net.minecraft.") or method.startswith("net.neoforged."):
        return "other_minecraft_neoforge"
    return "other_java_native"

def analyze(events):
    counts=collections.Counter()
    inclusive=collections.Counter()
    hot=collections.Counter()
    total=0
    for evt in events:
        if evt.get("type")!="jdk.ExecutionSample":continue
        total+=1
        frames=(((evt.get("values") or {}).get("stackTrace") or {}).get("frames") or [])
        names=[method_name(f) for f in frames]
        closest=next((n for n in names if n),"")
        group=family(closest)
        counts[group]+=1
        if closest:hot[closest]+=1
        # Inclusive bucket shares may overlap, e.g. ImprovedNoise beneath
        # PerlinNoise and vanilla calls under RealScale wrappers.
        for bucket in {family(n) for n in names if n}:
            inclusive[bucket]+=1
    if total<10:raise ValueError("Too few JFR ExecutionSample events")
    return {
        "execution_samples":total,
        "exclusive_leaf_families":{key:{
            "samples":counts[key],
            "pct_of_recording":round(100*counts[key]/total,2)
        }for key in sorted(counts)},
        "inclusive_stack_presence":dict(sorted(inclusive.items())),
        "top_25_leaf_methods":[{"method":m,"samples":n} for m,n in hot.most_common(25)],
        "notes":[
            "Exclusive means the first actual JFR Java frame and family buckets partition ALL ExecutionSamples.",
            "Inclusive method-family presence can overlap and must never be summed.",
            "These are sampled server CPU stacks, not exact CPU milliseconds, not a 1:1 worldgen-only time window.",
            "Perlin+ImprovedNoise, Beardifier and Aquifer are measured separately without changing their algorithms."
        ]
    }

def from_jfr(path):
    task=subprocess.run(["jfr","print","--json","--events",
        "jdk.ExecutionSample",str(path)],check=True,capture_output=True,
        text=True,timeout=150)
    return analyze((json.loads(task.stdout).get("recording") or {}).get("events") or [])

if __name__=="__main__":
    if len(sys.argv)!=3:
        raise SystemExit("usage: classify_vanilla_jfr_hotspots.py raw.jfr out.json")
    result=from_jfr(pathlib.Path(sys.argv[1]))
    out=pathlib.Path(sys.argv[2])
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print("REALSCALE_VANILLA_PERLIN_AQUIFER_BEARDIFIER_JFR:",json.dumps(result),flush=True)
