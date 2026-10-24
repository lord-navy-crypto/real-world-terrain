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


# Minecraft's PalettedContainer may serialize EQUAL world blocks with a
# different palette insertion order (or a different palette index packing).
# Test.39's first cold A/B failures were raw-palette hash mismatches; they
# did NOT distinguish that representation difference from real terrain drift.
# Expand the actual 4096 block states and 64 biomes PER SECTION and hash
# the canonical voxel content instead. Do not ignore actual block differences.
LONG_MASK=(1<<64)-1


def _palette_indices(palette, words, count: int, min_bits: int):
    if not palette:
        raise AssertionError("Saved paletted container has no palette")
    if len(palette)==1:
        # Minecraft may omit packed data for a single-entry palette.
        return [0]*count
    bits=max(min_bits,(len(palette)-1).bit_length())
    per_long=64//bits
    if per_long==0 or not isinstance(words,list):
        raise AssertionError("Unsupported saved PalettedContainer bit packing")
    required=(count+per_long-1)//per_long
    if len(words)<required:
        raise AssertionError(
            f"Truncated saved palette packing: bits={bits} "
            f"need={required} actual={len(words)}")
    indices=[]
    for i in range(count):
        word=int(words[i//per_long])&LONG_MASK
        index=(word>>((i%per_long)*bits))&((1<<bits)-1)
        if index>=len(palette):
            raise AssertionError(
                f"Illegal palette index {index} out of {len(palette)}")
        indices.append(index)
    return indices


def _normalized_block(state):
    if not isinstance(state,dict) or not isinstance(state.get("Name"),str):
        raise AssertionError(f"Malformed Minecraft block state: {state!r}")
    return json.dumps({
        "Name":state["Name"],
        "Properties":state.get("Properties",{}),
    },sort_keys=True,separators=(",",":"),ensure_ascii=True).encode("ascii")


def _digest_expanded_palette(container,count,min_bits,default):
    if not container or not container.get("palette"):
        palette=[default]
        packed=[]
    else:
        palette=container["palette"]
        packed=container.get("data",[])
    entries=([_normalized_block(x) for x in palette] if min_bits==4 else [
        str(x).encode("utf-8") for x in palette
    ])
    indices=_palette_indices(palette,packed,count,min_bits)
    h=hashlib.sha256()
    for index in indices:
        h.update(entries[index])
        h.update(b"\n")
    return h.hexdigest()


def _digest_heightmaps(maps):
    normalized={}
    # Decode numeric 2096-height 1.21.1 Overworld values, not storage
    # layout bytes. Heightmap names are compared independently.
    bits=(2096+1).bit_length()
    per_long=64//bits
    padded=(256+per_long-1)//per_long
    compact=(256*bits+63)//64
    for name,values in sorted(maps.items()):
        if not isinstance(values,list):
            raise AssertionError(f"Malformed heightmap {name}")
        # Heightmaps commonly use Minecraft SimpleBitStorage's
        # contiguous packing (values may cross a 64-bit boundary).
        # Some custom/legacy generators emit padded storage like
        # PalettedContainer. Accept either known length, never infer
        # a mixed or truncated layout. Equal lengths have same layout
        # for the common divisors of 64.
        if len(values)==compact and compact!=padded:
            mode="compact"
        elif len(values)==padded:
            mode="padded"
        else:
            raise AssertionError(
                f"Unexpected saved heightmap {name} longs={len(values)} "
                f"expectedCompact={compact} expectedPadded={padded}")
        unpacked=[]
        for i in range(256):
            if mode=="padded":
                word=int(values[i//per_long])&LONG_MASK
                value=word>>((i%per_long)*bits)
            else:
                offset=i*bits
                index=offset//64
                shift=offset%64
                word=int(values[index])&LONG_MASK
                value=word>>shift
                if shift+bits>64:
                    extra=int(values[index+1])&LONG_MASK
                    value|=extra<<(64-shift)
            unpacked.append(value&((1<<bits)-1))
        normalized[name]=unpacked
    return hashlib.sha256(json.dumps(
        normalized,sort_keys=True,separators=(",",":")
    ).encode("utf-8")).hexdigest()


def block_payload_digest(chunk: dict) -> dict:
    sections=chunk.get("sections",[])
    if not sections:
        raise AssertionError("Target chunk has no Anvil section data")
    section_details={}
    for sec in sections:
        y=int(sec["Y"])
        if y in section_details:
            raise AssertionError(f"Duplicate saved chunk section Y={y}")
        section_details[y]={
            "blocks":_digest_expanded_palette(
                sec.get("block_states",{}),4096,4,{"Name":"minecraft:air"}),
            "biomes":_digest_expanded_palette(
                sec.get("biomes",{}),64,1,"minecraft:plains"),
        }
    def combine(category):
        h=hashlib.sha256()
        for y,values in sorted(section_details.items()):
            h.update(f"{y}:".encode("ascii"))
            h.update(values[category].encode("ascii"))
            h.update(b"\n")
        return h.hexdigest()
    # Retain the old storage-layout hash only as a DIAGNOSTIC:
    # never fail on palette ordering when physical voxels are equal.
    old_storage={
        "sections":[{
            "Y":sec["Y"],
            "block_states":sec.get("block_states",{}),
            "biomes":sec.get("biomes",{}),
        } for sec in sorted(sections,key=lambda v:v["Y"])],
        "heightmaps":chunk.get("Heightmaps",chunk.get("heightmaps",{})),
    }
    return {
        "blocks":combine("blocks"),
        "biomes":combine("biomes"),
        "heightmaps":_digest_heightmaps(old_storage["heightmaps"]),
        "raw_storage_diagnostic":hashlib.sha256(json.dumps(
            old_storage,sort_keys=True,separators=(",",":")
        ).encode("utf-8")).hexdigest(),
        "sections":{str(y):x for y,x in sorted(section_details.items())},
    }


def target_chunk_digests(root: Path) -> dict[str,dict]:
    targets={(x//16,z//16) for x,z in FAR_ROUTE_SITES}
    target_region_names={
        f"r.{cx//32}.{cz//32}.mca" for cx,cz in targets
    }
    found={}
    for region in root.glob("run/**/region/*.mca"):
        if {"DIM-1","DIM1"}.intersection(region.parts):
            continue
        # Full 2096-high worldgen can leave transient header-only,
        # empty or partially flushed MCA files unrelated to the three
        # far probe targets. Scanning EVERY region (old path) falsely
        # failed both seed 2 and 42 at a different non-target region.
        # Read ONLY the specific persisted target region files; missing
        # or corrupt actual target chunks still fail below.
        if region.name not in target_region_names:
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


def saved_world_differences(baseline: dict, candidate: dict) -> list[dict]:
    errors=[]
    for chunk_id in sorted(set(baseline)|set(candidate)):
        a=baseline.get(chunk_id)
        b=candidate.get(chunk_id)
        if a is None or b is None:
            errors.append({"chunk":chunk_id,"category":"missing"})
            continue
        for category in ("blocks","biomes","heightmaps"):
            if a[category]!=b[category]:
                problem={"chunk":chunk_id,"category":category}
                if category in ("blocks","biomes"):
                    ys=sorted(set(a["sections"])|set(b["sections"]),
                              key=lambda y:int(y))
                    problem["changed_section_Y"]=[
                        y for y in ys if
                        a["sections"].get(y,{}).get(category)!=
                        b["sections"].get(y,{}).get(category)
                    ]
                errors.append(problem)
    return errors


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
    differences=saved_world_differences(original,current)
    parity=not differences
    storage_only=[
        key for key in original
        if key in current and original[key]["raw_storage_diagnostic"]
            !=current[key]["raw_storage_diagnostic"] and
           all(original[key][category]==current[key][category]
               for category in ("blocks","biomes","heightmaps"))
    ]
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
             "semantic_mismatches":differences,
             "storage_only_differences":storage_only,
             "saved_target_sha256":{
                 key:{c:v[c] for c in ("blocks","biomes","heightmaps")}
                 for key,v in results["candidate"]["blocks"].items()},
             "note":"Single matched same-runner seed, order-sensitive and noisy;"
                    " not a reliable percentage acceleration claim."}
    report.write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print("REALSCALE_MATCHED_COLD_WORLDGEN_AB_RESULT:",
          json.dumps(summary,sort_keys=True),flush=True)
    if not parity:
        raise AssertionError(
            "Real saved voxel/biome/heightmap difference by category "
            +json.dumps(differences,sort_keys=True))


if __name__=="__main__":
    main()
