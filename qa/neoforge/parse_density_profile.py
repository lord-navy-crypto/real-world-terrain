#!/usr/bin/env python3
"""Parse sampled density-stage timings from a real graceful server shutdown.

Times are inclusive sampled compute() latencies. Nested density stages
overlap, so these values must NOT be summed into precise CPU percentages.
"""
import json
import pathlib
import re
import sys

PATTERN=re.compile(
    r"\[RealScale density profile\]\s+([A-Z_]+)\s+samples=(\d+)"
    r"\s+stride=(\d+)\s+mean_ns=(\d+)\s+mean_us=(\d+)"
    r"\s+p95_bucket_upper_us=(\d+)\s+max_us=(\d+)")

def extract(text):
    stages={}
    for name,samples,stride,mean_ns,mean,p95,maximum in PATTERN.findall(text):
        num=int(samples)
        total_ns=int(mean_ns)*num
        stages[name]={
            "samples":num,"sampling_stride":int(stride),
            "mean_nanoseconds":int(mean_ns),
            "mean_microseconds_rounded":int(mean),
            "p95_bucket_upper_microseconds":int(p95),
            "max_microseconds":int(maximum),
            "approx_sampled_inclusive_nanoseconds":total_ns,
            "approx_sampled_inclusive_microseconds":round(total_ns/1000.0,3)
        }
    if "OVERWORLD_CAVE" not in stages or "MOUNTAIN_RELIEF" not in stages:
        raise ValueError("Missing expected Overworld cave or relief profile: enable JVM property on actual server and stop gracefully")
    if any(x["samples"]==0 for x in stages.values()):
        raise ValueError("Profiler reported an empty stage")
    return {"sampled_inclusive_stages":dict(sorted(stages.items(),
            key=lambda kv:kv[1]["approx_sampled_inclusive_nanoseconds"],reverse=True)),
            "interpretation":"Per-call samples, nested and inclusive; NOT wall-clock chunk CPU share.",
            "source":"Actual NeoForge server shutdown log, opt-in 1/1024 sampling"}

if __name__=="__main__":
    if len(sys.argv)!=3:raise SystemExit("usage: parse_density_profile.py server.log output.json")
    report=extract(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    destination=pathlib.Path(sys.argv[2])
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("REALSCALE_DENSITY_PROFILE_REPORT:",json.dumps(report),flush=True)
