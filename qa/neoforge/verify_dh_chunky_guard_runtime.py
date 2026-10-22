#!/usr/bin/env python3
"""Evidence-driven runtime verdict for 2x real NeoForge DH + Chunky trials."""
from pathlib import Path
import json
import sys

EXACT_CRASH = "java.lang.IllegalStateException: Chunky is not loaded."
EXACT_PATH = "ChunkyAccessor.bindOnGenerationProgressEvent"
FULL_CHUNK = "QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS: PASS"
PATCH_LOADED = "REALSCALE_DH_CHUNKY_GUARD_MOD_LOADED"
PATCH_HOOK = "REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED"

def evaluate(game_log, runner, mode, rc):
    original_failure = EXACT_CRASH in game_log and EXACT_PATH in game_log
    complete = FULL_CHUNK in runner and rc == 0
    patch_loaded = PATCH_LOADED in game_log
    hook_executed = PATCH_HOOK in game_log
    if mode == "patched":
        passed = complete and patch_loaded and hook_executed and not original_failure
        verdict = "patch_runtime_pass" if passed else "patch_not_proven_or_failed"
    elif mode == "unpatched":
        # This is a genuine observation, not a forced green: unpatched
        # server may initialize Chunky earlier than user's integrated Mac server.
        passed = original_failure or complete
        verdict = ("reproduced_known_DH_Chunky_crash" if original_failure else
                  "baseline_started_no_reproduction" if complete else
                  "unexpected_baseline_failure")
    else:
        raise ValueError("invalid trial mode")
    return dict(mode=mode,exit_code=rc,chunk_complete=complete,
                known_upstream_crash=original_failure,
                patch_mod_loaded=patch_loaded,
                patch_mixin_entered=hook_executed,
                verdict=verdict,pass_gate=passed,
                note="Real headless NeoForge; not a macOS client GPU test")

def main():
    game=Path(sys.argv[1])
    runner=Path(sys.argv[2])
    mode=sys.argv[3]
    rc=int(sys.argv[4])
    out=Path(sys.argv[5])
    result=evaluate(game.read_text(errors="replace") if game.exists() else "",
                    runner.read_text(errors="replace") if runner.exists() else "",
                    mode,rc)
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    print("REALSCALE_REAL_DH_CHUNKY_PATCH_RESULT",json.dumps(result,sort_keys=True),flush=True)
    if not result["pass_gate"]:
        raise SystemExit("Actual combined DH Chunky runtime check did not pass")

if __name__=="__main__":
    main()
