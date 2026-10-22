#!/usr/bin/env python3
"""Compare original 256-block Y support vs tighter 64-block support."""
import json
import pathlib
import sys
from compare_beardifier_three_runs import three_run

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("usage: compare_beardifier_tight_y_three_runs.py y256.json y64.json y256again.json report.json")
    a=three_run(*sys.argv[1:4])
    report={
        "preset":a["preset"],
        "order":"Y256 -> Y64 -> Y256",
        "y256_first_seconds":a["vanilla_first_total_seconds"],
        "y64_seconds":a["experimental_total_seconds"],
        "y256_control_seconds":a["vanilla_control_total_seconds"],
        "speedup_vs_first_y256_pct":a["speedup_vs_first_vanilla_pct"],
        "speedup_vs_control_y256_pct":a["speedup_vs_control_vanilla_pct"],
        "gain_vs_faster_y256_pct":a["conservative_speedup_vs_faster_vanilla_pct"],
        "y256_drift_pct":a["vanilla_run_to_run_spread_pct"],
        "candidate_faster_than_both_y256_controls":a["candidate_faster_than_both_controls"],
        "caveat":"Uninstrumented Y256/Y64/Y256 A-B-A; separate Y64 verifier runs same context with sampled replay. Repeat shuffled orders and review Anvil parity before promotion."
    }
    dest=pathlib.Path(sys.argv[4])
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(report,indent=2)+"\n")
    print("REALSCALE_BEARDIFIER_TIGHT_Y_THREE_RUN_REPORT:",json.dumps(report),flush=True)
