#!/usr/bin/env python3
"""Complete-load A / B / A-control analysis: all three real NeoForge servers.

The candidate is compared against BOTH vanilla passes, not just the first
pass. Results are descriptive measurements, never claims of guaranteed speed.
"""
import json
import pathlib
import sys

from compare_paired_far_route import compare

def three_run(before,candidate,control):
    ab=compare(before,candidate)
    cb=compare(control,candidate)
    ac=compare(before,control)
    if ab["preset"]!=cb["preset"] or ab["preset"]!=ac["preset"]:
        raise ValueError("Mixed world presets in three-run comparison")
    vanilla_one=ab["baseline_total_seconds"]
    variant=ab["candidate_total_seconds"]
    vanilla_two=cb["baseline_total_seconds"]
    lower=min(vanilla_one,vanilla_two)
    upper=max(vanilla_one,vanilla_two)
    conservative=round((1-variant/lower)*100,2)
    drift=round((upper/lower-1)*100,2)
    return {
        "preset":ab["preset"],
        "order":"vanilla first -> experimental -> vanilla control",
        "vanilla_first_total_seconds":vanilla_one,
        "experimental_total_seconds":variant,
        "vanilla_control_total_seconds":vanilla_two,
        "speedup_vs_first_vanilla_pct":ab["total_speedup_percent"],
        "speedup_vs_control_vanilla_pct":cb["total_speedup_percent"],
        "conservative_speedup_vs_faster_vanilla_pct":conservative,
        "vanilla_run_to_run_spread_pct":drift,
        "candidate_faster_than_both_controls":variant<lower,
        "caveat":"Single same-runner three-server sequence; sampling replay adds experimental CPU work. Repeated randomized orders needed before promotion."
    }

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("usage: compare_beardifier_three_runs.py vanilla.json experimental.json vanilla_control.json report.json")
    report=three_run(*sys.argv[1:4])
    output=pathlib.Path(sys.argv[4])
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+"\n")
    print("REALSCALE_BEARDIFIER_THREE_RUN_REPORT:",json.dumps(report),flush=True)
