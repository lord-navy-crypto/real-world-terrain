#!/usr/bin/env python3
"""Incremental Beardifier Y-only / Y+XZ / Y-only complete-load comparison."""
import json
import pathlib
import sys
from compare_beardifier_three_runs import three_run

def compare_three_y_xz(y_first, y_plus_xz, y_control):
    old=three_run(y_first,y_plus_xz,y_control)
    return {
        "preset":old["preset"],
        "order":"Y-only -> Y+XZ -> Y-only",
        "y_only_first_seconds":old["vanilla_first_total_seconds"],
        "y_plus_xz_seconds":old["experimental_total_seconds"],
        "y_only_control_seconds":old["vanilla_control_total_seconds"],
        "speedup_vs_y_first_pct":old["speedup_vs_first_vanilla_pct"],
        "speedup_vs_y_control_pct":old["speedup_vs_control_vanilla_pct"],
        "speedup_vs_faster_y_only_pct":old["conservative_speedup_vs_faster_vanilla_pct"],
        "y_only_run_spread_pct":old["vanilla_run_to_run_spread_pct"],
        "candidate_faster_than_both_y_only_controls":old["candidate_faster_than_both_controls"],
        "caveat":"One A/B/A sequence with parity replay CPU overhead; not client FPS. Repeat orders and save parity before promotion."
    }

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("usage: compare_beardifier_xz_three_runs.py y_first.json y_plus_xz.json y_control.json output.json")
    report=compare_three_y_xz(*sys.argv[1:4])
    output=pathlib.Path(sys.argv[4])
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+"\n")
    print("REALSCALE_BEARDIFIER_XZ_THREE_RUN_REPORT:",json.dumps(report),flush=True)
