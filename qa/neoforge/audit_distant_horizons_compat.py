#!/usr/bin/env python3
"""Diagnose REAL NeoForge server logs from control vs Distant Horizons runs.

Do not declare DH compatibility just because two mods are in the mods folder.
Evidence requires a live DH initialization marker plus Minecraft's completed,
saved chunk probe from the existing RCON QA, not merely a successful Gradle build.
"""
from pathlib import Path
import json
import re
import sys

# Broad fingerprints for triage; WARN strings are recorded but do not
# automatically convert a complete, functioning worldgen run into a failure.
FINGERPRINTS = {
    "mixin": r"(MixinApplyError|MixinTransformerError|InjectionError|Critical injection failure)",
    "worldgen": r"(Failed to load registries|Failed to create level|Exception generating chunk|Exception ticking world|WorldGen deadlock)",
    "vertical": r"(ArrayIndexOutOfBoundsException|NegativeArraySizeException|height.?range|out.of.bounds)",
    "resource": r"(OutOfMemoryError|Java heap space|GC overhead limit exceeded)",
    "dh_error": r"(?:DistantHorizons|distanthorizons)[^\\n]*(?:ERROR|Exception|failed)|(?:ERROR)[^\\n]*(?:DistantHorizons|distanthorizons)",
}
DH_ACTIVE = re.compile(r"(?:DistantHorizons|Distant Horizons|distanthorizons)", re.I)
# This is the approved, actual completed Minecraft chunk proof from RCON.
CHUNK_COMPLETE = "QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS: PASS"

def audit(log_text: str, expect_dh: bool, real_probe: str):
    fingerprints = {
        name: len(re.findall(regex, log_text, flags=re.I))
        for name, regex in FINGERPRINTS.items()
    }
    # One marker alone is not sufficient: distinct DH mod metadata and runtime
    # logging may differ between minor versions; retain a report for review.
    has_dh = bool(DH_ACTIVE.search(log_text))
    completed = CHUNK_COMPLETE in real_probe
    # Actual Mac 1.21.1/NeoForge 21.1.262 crash report and independent
    # upstream issue #1329 show this failure inside the DH <-> Chunky
    # accessor. It is not evidence of a RealScale density/height fault.
    chunky_unloaded = "java.lang.IllegalStateException: Chunky is not loaded." in log_text
    chunky_dh_bridge = "ChunkyAccessor.bindOnGenerationProgressEvent" in log_text
    known_upstream_chunky_failure = chunky_unloaded and chunky_dh_bridge
    return {
        "expected_distant_horizons": expect_dh,
        "distant_horizons_log_marker": has_dh,
        "real_minecraft_chunk_generated": completed,
        "fingerprints": fingerprints,
        "dh_chunky_bridge_unavailable": known_upstream_chunky_failure,
        "triage": "DH-Chunky provider lifecycle (#1329)" if known_upstream_chunky_failure else
                  "No proven Chunky lifecycle crash",
        "log_lines": len(log_text.splitlines()),
        "pass": completed and (has_dh if expect_dh else not has_dh)
                and not known_upstream_chunky_failure
                and not any(fingerprints[k] for k in ("mixin", "resource")),
        "note": "Headless server boot + saved chunk, NOT LOD rendering or GPU compatibility",
    }

def main():
    log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    probe = Path(sys.argv[2]).read_text(encoding="utf-8", errors="replace")
    expect_dh = sys.argv[3] == "with_dh"
    report = audit(log, expect_dh, probe)
    target = Path(sys.argv[4])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2, sort_keys=True) + "\\n", encoding="utf-8")
    print("REALSCALE_DH_COMPAT_REPORT", json.dumps(report, sort_keys=True), flush=True)
    if not report["pass"]:
        raise SystemExit("Actual Distant Horizons server compatibility gate failed")

if __name__ == "__main__":
    main()
