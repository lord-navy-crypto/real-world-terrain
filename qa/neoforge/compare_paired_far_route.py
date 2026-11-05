#!/usr/bin/env python3
"""Paired Minecraft server complete-load timings; NEVER substitute ACK times."""
import json
import pathlib
import statistics
import sys

def compare(before,after):
    old=json.loads(pathlib.Path(before).read_text())
    new=json.loads(pathlib.Path(after).read_text())
    b=old["waypoints"];a=new["waypoints"]
    if old["preset"]!=new["preset"]:raise ValueError("different presets")
    if len(b)!=3 or len(a)!=3:raise ValueError("partial route")
    if [(x["x"],x["z"]) for x in b]!=[(x["x"],x["z"]) for x in a]:
        raise ValueError("mismatched route")
    if old["strict_seconds_per_waypoint"]!=180 or new["strict_seconds_per_waypoint"]!=180:
        raise ValueError("not strict 180s")
    for v in b+a:
        if not v["saved"] or not v["unloaded_ticket"] or not 0<v["loaded_seconds"]<=180:
            raise ValueError("not fully loaded and saved")
    rows=[{"x":p["x"],"z":p["z"],"baseline_seconds":p["loaded_seconds"],
           "candidate_seconds":q["loaded_seconds"],
           "speedup_percent":round((1-q["loaded_seconds"]/p["loaded_seconds"])*100,2)}
          for p,q in zip(b,a)]
    oldTotal=sum(x["loaded_seconds"] for x in b)
    newTotal=sum(x["loaded_seconds"] for x in a)
    return {"preset":old["preset"],"waypoints":rows,
            "baseline_total_seconds":round(oldTotal,3),
            "candidate_total_seconds":round(newTotal,3),
            "total_speedup_percent":round((1-newTotal/oldTotal)*100,2),
            "baseline_median_seconds":statistics.median(x["loaded_seconds"] for x in b),
            "candidate_median_seconds":statistics.median(x["loaded_seconds"] for x in a),
            "caveat":"One paired baseline-then-candidate sample; not a stable benchmark."}

if __name__=="__main__":
    if len(sys.argv)!=4:raise SystemExit("usage: compare_paired_far_route.py baseline candidate output.json")
    outcome=compare(sys.argv[1],sys.argv[2])
    pathlib.Path(sys.argv[3]).write_text(json.dumps(outcome,indent=2)+"\n")
    print("REALSCALE_PAIRED_FAR_ROUTE_COMPARISON:",json.dumps(outcome),flush=True)
