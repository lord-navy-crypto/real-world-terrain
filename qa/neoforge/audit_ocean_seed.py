#!/usr/bin/env python3
"""Audit saved REAL TVA Natural ocean-seed chunks; no fabricated ocean biomes.

Only generated Anvil chunks count. A seed with no real ocean observations
FAILS rather than reporting water success. Report both seabed and biomes.
"""
import argparse
import json
from collections import Counter
from pathlib import Path
from realscale_headless_qa import iter_region_chunks, get_block, unpack_palette_index

WATER = frozenset((
    "minecraft:water", "minecraft:kelp", "minecraft:kelp_plant",
    "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:bubble_column",
))
OCEAN_STRUCTURE = ("shipwreck", "ruin", "monument")

def saved_biome(chunk, bx, y, bz):
    section = next((s for s in chunk.get("sections", [])
                    if s.get("Y") == y // 16), None)
    if not section:
        return None
    data = section.get("biomes", {})
    palette = data.get("palette", [])
    if not palette:
        return None
    if len(palette) == 1:
        return palette[0]
    index = (y % 16 // 4) * 16 + (bz // 4) * 4 + bx // 4
    bits = max(1, (len(palette) - 1).bit_length())
    decoded = unpack_palette_index(data.get("data", []), index, bits)
    return palette[decoded] if decoded < len(palette) else None

def audit(world, seed, profile):
    regions = sorted((world / "region").glob("r.*.*.mca"))
    if not regions:
        raise AssertionError("NO_SAVED_ANVIL: the ocean seed world was never persisted")
    out = {
        "seed": seed, "profile": profile, "world": str(world),
        "full_chunks": 0, "ocean_chunks": 0, "ocean_columns": 0,
        "deep_ocean_columns": 0, "wet_ocean_columns": 0, "dry_ocean_columns": 0,
        "deep_y48_water_columns": 0, "structure_starts": 0,
        "biome_counts": {}, "examples_of_dry_ocean": [],
    }
    names = Counter()
    for region in regions:
        for chunk in iter_region_chunks(region):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            if not isinstance(cx, int) or not isinstance(cz, int):
                continue
            out["full_chunks"] += 1
            structures = chunk.get("structures", {}).get("starts", {})
            out["structure_starts"] += sum(
                any(word in str(k).lower() for word in OCEAN_STRUCTURE)
                and isinstance(v, dict)
                and v.get("id") not in ("INVALID", "minecraft:empty")
                for k, v in structures.items()
            )
            has_ocean = False
            for dx in (0, 4, 8, 12):
                for dz in (0, 4, 8, 12):
                    biome = saved_biome(chunk, dx, 62, dz)
                    if not isinstance(biome, str):
                        continue
                    if not (biome.startswith("minecraft:") and biome.endswith("_ocean")
                            or biome == "minecraft:ocean"):
                        continue
                    has_ocean = True
                    names[biome] += 1
                    out["ocean_columns"] += 1
                    deep = biome.startswith("minecraft:deep_")
                    out["deep_ocean_columns"] += int(deep)
                    block = get_block(chunk, dx, 62, dz)
                    wet = block in WATER
                    out["wet_ocean_columns"] += int(wet)
                    out["dry_ocean_columns"] += int(not wet)
                    if deep and get_block(chunk, dx, 48, dz) in WATER:
                        out["deep_y48_water_columns"] += 1
                    if not wet and len(out["examples_of_dry_ocean"]) < 24:
                        out["examples_of_dry_ocean"].append({
                            "x": cx * 16 + dx, "z": cz * 16 + dz,
                            "y": 62, "biome": biome, "block": block
                        })
            out["ocean_chunks"] += int(has_ocean)
    out["biome_counts"] = dict(sorted(names.items()))
    if out["ocean_columns"] > 0:
        out["ocean_wet_fraction"] = round(
            out["wet_ocean_columns"] / out["ocean_columns"], 5)
    else:
        out["ocean_wet_fraction"] = None
    if out["deep_ocean_columns"] > 0:
        out["deep_y48_water_fraction"] = round(
            out["deep_y48_water_columns"] / out["deep_ocean_columns"], 5)
    else:
        out["deep_y48_water_fraction"] = None
    errors = []
    if out["full_chunks"] < 8:
        errors.append("FEWER_THAN_EIGHT_FULL_CHUNKS")
    if out["ocean_columns"] < 64:
        errors.append("NO_VERIFIED_OCEAN_BIOME_IN_SAVED_SEED_WORLD")
    if out["ocean_chunks"] < 4:
        errors.append("INSUFFICIENT_SAVED_OCEAN_CHUNK_COVERAGE")
    if out["ocean_columns"] and out["ocean_wet_fraction"] < 0.85:
        errors.append("DRY_OCEAN_BIOME_SEAFLOOR_MISMATCH")
    if profile == "deep":
        if out["deep_ocean_columns"] < 16:
            errors.append("NO_VERIFIED_DEEP_OCEAN_IN_SAVED_SEED_WORLD")
        if out["deep_ocean_columns"] and out["deep_y48_water_fraction"] < 0.10:
            errors.append("DEEP_OCEAN_NO_WATER_AT_Y48")
    out["issues"] = errors
    out["pass"] = not errors
    return out

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=Path)
    parser.add_argument("--seed", required=True)
    parser.add_argument("--profile", choices=("deep", "ocean"), required=True)
    parser.add_argument("--output", type=Path,
                        default=Path("build/tva-ocean-seed-audit.json"))
    args = parser.parse_args()
    report = audit(args.world, args.seed, args.profile)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print("TVA_REAL_OCEAN_SEED_AUDIT", json.dumps(report, sort_keys=True), flush=True)
    if report["issues"]:
        raise AssertionError("OCEAN_SEED_QUALITY_FAILED: " + ", ".join(report["issues"]))
    print("OCEAN_SEED_PASS: real vanilla oceans, seawater and saved full chunks verified")
if __name__ == "__main__":
    main()
