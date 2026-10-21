#!/usr/bin/env python3
"""Y+XZ union vs optional sparse-cell-grid benchmark on identical full 2096 worlds."""
import json
import pathlib
import sys
from compare_beardifier_three_runs import three_run

def compare_three(base, candidate, repeat):
    result=three_run(base,candidate,repeat)
    return {
        "preset":result["preset"],
        "order":"union XZ first -> union XZ + sparse grid -> union XZ repeat",
        "xz_union_first_seconds":result["vanilla_first_total_seconds"],
        "xz_sparse_seconds":result["experimental_total_seconds"],
        "xz_union_control_seconds":result["vanilla_control_total_seconds"],
        "gain_vs_first_union_pct":result["speedup_vs_first_vanilla_pct"],
        "gain_vs_control_union_pct":result["speedup_vs_control_vanilla_pct"],
        "conservative_gain_vs_faster_union_pct":result["conservative_speedup_vs_faster_vanilla_pct"],
        "union_control_spread_pct":result["vanilla_run_to_run_spread_pct"],
        "candidate_faster_than_both_union_controls":result["candidate_faster_than_both_controls"],
        "caveat":"Single real-server A/B/A; opt-in sampled parity adds CPU work. This is chunk generation, not GPU render acceleration."
    }

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("usage: compare_beardifier_sparse_grid_three_runs.py xz_first.json sparse.json xz_control.json output.json")
    result=compare_three(*sys.argv[1:4])
    path=pathlib.Path(sys.argv[4])
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(result,indent=2)+"\n")
    print("REALSCALE_BEARDIFIER_SPARSE_THREE_RUN_REPORT:",json.dumps(result),flush=True)
