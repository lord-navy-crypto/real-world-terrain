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
PATCH_DEFERRED = "REALSCALE_DH_CHUNKY_GUARD_DEFERRED"
PATCH_READY = "REALSCALE_DH_CHUNKY_GUARD_READY"
PATCH_BOUND = "REALSCALE_DH_CHUNKY_GUARD_BOUND"
DH_WORLDGEN_QUEUE = "Created world gen queue"

def evaluate(game_log, runner, mode, rc):
    original_failure = EXACT_CRASH in game_log and EXACT_PATH in game_log
    complete = FULL_CHUNK in runner and rc == 0
    patch_loaded = PATCH_LOADED in game_log
    hook_executed = PATCH_HOOK in game_log
    deferred = PATCH_DEFERRED in game_log
    ready_after_defer = PATCH_READY in game_log
    listener_not_stuck = (not deferred or ready_after_defer)
    native_listener_bound = PATCH_BOUND in game_log
    dh_generation_queue_initialized = DH_WORLDGEN_QUEUE in game_log
    if mode == "patched":
        passed = (complete and patch_loaded and hook_executed and
                  listener_not_stuck and native_listener_bound and dh_generation_queue_initialized
                  and not original_failure)
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
                deferred=deferred,ready_after_defer=ready_after_defer,
                listener_not_stuck=listener_not_stuck,
                native_listener_bound=native_listener_bound,
                dh_generation_queue_initialized=dh_generation_queue_initialized,
                verdict=verdict,pass_gate=passed,
                note="Real headless NeoForge; not a macOS client GPU test")

def verify_lod_storage(storage):
    """Validate independent persisted-DH evidence, NOT GPU rendering.

    Only require at least one likely LOD table in any dimension; overworld
    often contains rows while the untouched Nether and End only have metadata.
    Any WAL snapshot, truncated scan, or unhealthy database invalidates the
    persistence gate. Do not open raw user data here.
    """
    if not isinstance(storage, dict):
        return False, "missing_or_invalid_report"
    state=storage.get("state")
    files=storage.get("files")
    if state!="candidate_lod_records_present":
        return False, state if isinstance(state,str) and state in (
            "no_lod_database_found", "incomplete_due_to_sqlite_wal",
            "database_read_or_integrity_problem",
            "sqlite_records_found_lod_payload_not_verified",
            "sqlite_opened_no_table_records") else "invalid_state"
    if not isinstance(files,list) or not files:
        return False,"missing_file_records"
    discovered=storage.get("dh_sqlite_files_discovered")
    scanned=storage.get("dh_sqlite_files_scanned")
    if (type(discovered) is not int or type(scanned) is not int
            or scanned<1 or discovered!=scanned
            or storage.get("scan_truncated") is not False):
        return False,"database_scan_incomplete"
    if (type(storage.get("healthy_sqlite_files")) is not int or
            storage["healthy_sqlite_files"]!=scanned or
            len(files)!=scanned):
        return False,"unhealthy_sqlite"
    for item in files:
        if not isinstance(item,dict) or (
                item.get("sqlite_open_ok") is not True
                or item.get("integrity_check")!="ok"
                or item.get("incomplete_snapshot") is not False):
            return False,"sqlite_integrity_or_wal_uncertain"
    if (type(storage.get("candidate_lod_data_tables_with_records")) is not int
            or storage["candidate_lod_data_tables_with_records"]<1):
        return False,"no_candidate_lod_data"
    if (storage.get("lod_payload_verified") is not False
            or storage.get("gpu_lod_visible")!="unverified"):
        return False,"misleading_proof_claim"
    return True,"candidate_lod_storage_verified_metadata_only"


def main():
    game=Path(sys.argv[1])
    runner=Path(sys.argv[2])
    mode=sys.argv[3]
    rc=int(sys.argv[4])
    out=Path(sys.argv[5])
    result=evaluate(game.read_text(errors="replace") if game.exists() else "",
                    runner.read_text(errors="replace") if runner.exists() else "",
                    mode,rc)
    if mode=="patched":
        # This is a second, independent gate in addition to full chunks,
        # DH's native listener and its generation queue.
        source=Path(sys.argv[6]) if len(sys.argv)>6 else None
        try:
            evidence=json.loads(source.read_text(encoding="utf-8")) if source else None
        except (OSError,ValueError):
            evidence=None
        lod_ok,lod_reason=verify_lod_storage(evidence)
        result["lod_storage_gate"]=lod_ok
        result["lod_storage_reason"]=lod_reason
        result["lod_storage_level"]="candidate_table_presence_only"
        result["gpu_lod_visible"]="unverified"
        result["pass_gate"]=bool(result["pass_gate"] and lod_ok)
        if not result["pass_gate"]:
            result["verdict"]="patch_or_lod_storage_not_proven"
    else:
        # The unpatched arm is for reproducing the original crash only.
        # Requiring LOD storage when the game intentionally crashes would
        # make the comparator invalid.
        result["lod_storage_gate"]="not_required_for_unpatched_baseline"
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    print("REALSCALE_REAL_DH_CHUNKY_PATCH_RESULT",json.dumps(result,sort_keys=True),flush=True)
    if not result["pass_gate"]:
        raise SystemExit("Actual combined DH Chunky runtime check did not pass")

if __name__=="__main__":
    main()
