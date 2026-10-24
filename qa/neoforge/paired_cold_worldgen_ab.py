#!/usr/bin/env python3
"""Run real NeoForge virgin-chunk A/B on ONE machine, never a fake speed claim.

This intentionally compares two separately checked-out revisions through the
EXISTING strict RCON distant_route test and its persisted timing JSON.
It also checks canonical saved Anvil block-state/biome/heightmap payloads at
all three actually generated distant target chunks. Other chunks can be
different because spawn ticks and asynchronous neighbors may vary.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from realscale_headless_qa import iter_region_chunks
from rcon_worldgen_smoke import FAR_ROUTE_SITES


def prepare(root: Path, seed: int, preset: str) -> None:
    if not (root / "build.gradle").is_file():
        raise RuntimeError(f"Not a NeoForge checkout: {root}")
    for location in ("run", "run/server", "run/gametestserver"):
        p = root / location
        p.mkdir(parents=True, exist_ok=True)
        world = p / "world"
        if world.exists():
            raise RuntimeError(f"Refusing non-virgin world: {world}")
        (p / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (p / "server.properties").write_text(
            "level-name=world\n"
            f"level-type=realscale:{preset}\n"
            f"level-seed={seed}\n"
            "generate-structures=true\n"
            "spawn-protection=0\n"
            "view-distance=3\n"
            "simulation-distance=3\n"
            "max-players=1\n"
            "max-tick-time=-1\n"
            "online-mode=true\n"
            "server-ip=127.0.0.1\n"
            "enable-rcon=true\n"
            "rcon.port=25575\n"
            "rcon.password=realscale_ci_probe_only\n",
            encoding="utf-8",
        )


def server_run(root: Path, preset: str, label: str) -> list[dict]:
    command = [
        sys.executable, "../qa/neoforge/rcon_worldgen_smoke.py",
        ".", f"realscale:{preset}", "distant_route",
    ]
    log = root / "build" / "paired-worldgen-console.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w", encoding="utf-8") as handle:
        proc = subprocess.Popen(
            command, cwd=root, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1,
            env={**os.environ, "REALSCALE_JFR_DIAGNOSTIC": "0"},
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            handle.write(line)
            print(f"[{label}] {line}", end="", flush=True)
        code = proc.wait()
    if code:
        raise RuntimeError(f"{label} full distant-route server QA failed: {code}")
    path = root / "build" / "rcon_distant_route_timing.json"
    info = json.loads(path.read_text(encoding="utf-8"))
    waypoints = info["waypoints"]
    expected = list(FAR_ROUTE_SITES)
    if len(waypoints) != len(expected):
        raise AssertionError(f"{label} missing full distant route")
    for item, (x,z) in zip(waypoints, expected):
        if (item["x"],item["z"]) != (x,z):
            raise AssertionError(f"{label} different measured coordinates")
        if not item["saved"] or not item["unloaded_ticket"]:
            raise AssertionError(f"{label} unverified saved/chunk ticket")
        if not 0 < item["loaded_seconds"] <= 180:
            raise AssertionError(f"{label} timed out or invalid new chunk")
    return waypoints


def block_payload_digest(chunk: dict) -> str:
    sections = chunk.get("sections", [])
    if not sections:
        raise AssertionError("Target chunk has no Anvil section data")
    # Only actual saved block, biome and heightmap data: not timestamps,
    # entity positions, block tick queues, random heightmap metadata, etc.
    canonical = {
        "sections": [
            {"Y": sec["Y"],
             "block_states": sec.get("block_states", {}),
             "biomes": sec.get("biomes", {})}
            for sec in sorted(sections, key=lambda s:s["Y"])
        ],
        "heightmaps": chunk.get("Heightmaps", chunk.get("heightmaps",{})),
    }
    encoded = json.dumps(canonical, sort_keys=True, separators=(",",":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def target_chunk_digests(root: Path) -> dict[str,str]:
    targets={(x//16,z//16) for x,z in FAR_ROUTE_SITES}
    found={}
    for region in root.glob("run/**/region/*.mca"):
        if {"DIM-1","DIM1"}.intersection(region.parts):
            continue
        for chunk in iter_region_chunks(region):
            pos=(chunk.get("xPos"),chunk.get("zPos"))
            if pos not in targets:
                continue
            key=f"{pos[0]},{pos[1]}"
            fingerprint=block_payload_digest(chunk)
            if key in found and found[key]!=fingerprint:
                raise AssertionError(f"Conflicting Overworld region copies: {key}")
            found[key]=fingerprint
    if set(found)!={f"{x},{z}" for x,z in targets}:
        raise AssertionError(
            f"Missing actual persisted distant target chunks: "
            f"found={sorted(found)} expected={sorted(targets)}")
    return found


def main() -> None:
    p=argparse.ArgumentParser()
    p.add_argument("baseline", type=Path)
    p.add_argument("candidate", type=Path)
    p.add_argument("--seed", type=int, required=True)
    p.add_argument("--preset",default="tva_measured_three_realms")
    p.add_argument("--order",choices=("baseline-first","candidate-first"),
                   required=True)
    args=p.parse_args()
    roots={"baseline":args.baseline.resolve()/"neoforge",
           "candidate":args.candidate.resolve()/"neoforge"}
    results={}
    for name in ("baseline","candidate"):
        prepare(roots[name],args.seed,args.preset)
    order=("baseline","candidate") if args.order=="baseline-first" else (
            "candidate","baseline")
    report=Path(f"paired-worldgen-summary-seed{args.seed}.json")
    for name in order:
        results[name]={"route":server_run(roots[name],args.preset,name),
                       "blocks":target_chunk_digests(roots[name])}
        report.write_text(json.dumps({"seed":args.seed,"preset":args.preset,
                "order":order,"partial":results},indent=2)+"\n",encoding="utf-8")
    original=results["baseline"]["blocks"]
    current=results["candidate"]["blocks"]
    parity=original==current
    before=results["baseline"]["route"]
    after=results["candidate"]["route"]
    comparisons=[{
        "chunk_x":a["x"]//16, "chunk_z":a["z"]//16,
        "baseline_seconds":a["loaded_seconds"],
        "candidate_seconds":b["loaded_seconds"],
        "relative_delta_percent":round(
            100*(b["loaded_seconds"]-a["loaded_seconds"])/a["loaded_seconds"],2),
    } for a,b in zip(before,after)]
    summary={"seed":args.seed,"preset":args.preset,"order":order,
             "pairs":comparisons,"target_anvil_parity":parity,
             "saved_target_sha256":results["candidate"]["blocks"],
             "note":"Single matched same-runner seed, order-sensitive and noisy;"
                    " not a reliable percentage acceleration claim."}
    report.write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print("REALSCALE_MATCHED_COLD_WORLDGEN_AB_RESULT:",
          json.dumps(summary,sort_keys=True),flush=True)
    if not parity:
        raise AssertionError(
            "The old and optimized versions generated DIFFERENT saved "
            "block/biome/heightmap contents for the same distant chunks.")


if __name__=="__main__":
    main()
