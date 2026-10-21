#!/usr/bin/env python3
"""Parse sampled density-stage timings from a real graceful server shutdown.

Times are inclusive sampled compute() latencies. Nested density stages
overlap, so these values must NOT be summed into precise CPU percentages.
"""
import json
import pathlib
import re
import sys

CACHE_PATTERN=re.compile(
    r"\[RealScale ecotone cache profile\]\s+resolution_calls=(\d+)"
    r"\s+base_hits=(\d+)\s+base_misses=(\d+)"
)

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
    cache_details=None
    matches=CACHE_PATTERN.findall(text)
    if matches:
        requests,bh,bm=(int(v) for v in matches[-1])
        cache_details={
            "resolution_calls":requests,
            "underlying_biome_hits":bh,"underlying_biome_misses":bm,
            "underlying_climate_calls":bm,
            "underlying_hit_pct":round(100*bh/(bh+bm),2) if bh+bm else 0,
            "full_output_cache":"disabled_due_to_0.03pct_real_hit_rate"
        }
    return {"ecotone_cache":cache_details,
            "sampled_inclusive_stages":dict(sorted(stages.items(),
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
