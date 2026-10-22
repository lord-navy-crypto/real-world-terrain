#!/usr/bin/env python3
"""Actual complete-loaded-chunk A/B/A timing, requiring both vanilla controls."""
import json
import pathlib
import sys
from compare_paired_far_route import compare

def compare_three(first,candidate,control):
    ab=compare(first,candidate)
    cb=compare(control,candidate)
    ac=compare(first,control)
    if ab["preset"]!=cb["preset"] or ab["preset"]!=ac["preset"]:
        raise ValueError("different preset in Perlin memo A/B/A")
    a,b,c=(ab["baseline_total_seconds"],ab["candidate_total_seconds"],
           cb["baseline_total_seconds"])
    faster=min(a,c)
    return {"preset":ab["preset"],"order":"vanilla -> Perlin exact memo -> vanilla",
            "vanilla_first_seconds":a,"memo_seconds":b,
            "vanilla_control_seconds":c,
            "gain_vs_first_pct":round((1-b/a)*100,2),
            "gain_vs_control_pct":round((1-b/c)*100,2),
            "conservative_gain_pct":round((1-b/faster)*100,2),
            "baseline_drift_pct":round((max(a,c)/faster-1)*100,2),
            "candidate_faster_than_both":b<faster,
            "caveat":"One exact same-runner A/B/A, no cache verification overhead in timing. Separate verifier not used to score speed."}

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("usage: compare_perlin_three_runs.py vanilla.json memo.json vanilla_control.json output.json")
    result=compare_three(*sys.argv[1:4])
    output=pathlib.Path(sys.argv[4])
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,indent=2)+"\n")
    print("REALSCALE_PERLIN_EXACT_ABA_REPORT:",json.dumps(result),flush=True)
