#!/usr/bin/env python3
"""Summarize real persisted Minecraft WORLD_SURFACE heightmaps for a fitted preset.

This reads Anvil chunk NBT produced by the NeoForge server smoke test. It does
not estimate heights from the density function or infer terrain from a preset ID.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from realscale_headless_qa import iter_region_chunks, surface_height  # noqa: E402


def percentile(sorted_values: list[int], fraction: float) -> int:
    if not sorted_values:
        raise ValueError("no persisted heightmap samples")
    return sorted_values[round((len(sorted_values) - 1) * fraction)]


def find_world(run_root: Path, world_name: str) -> Path:
    candidates = sorted(run_root.glob(f"**/{world_name}/level.dat"))
    for candidate in candidates:
        if (candidate.parent / "region").is_dir():
            return candidate.parent
    raise FileNotFoundError(f"Could not find saved world {world_name!r} under {run_root}")


def audit(world: Path, slug: str) -> dict:
    regions = sorted((world / "region").glob("r.*.*.mca"))
    if not regions:
        raise RuntimeError(f"No Anvil region files under {world / 'region'}")

    heightmaps: dict[str, list[int | None]] = {}
    decoded_chunks = 0
    for region in regions:
        if region.stat().st_size <= 8192:
            continue
        for chunk in iter_region_chunks(region):
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            if not isinstance(cx, int) or not isinstance(cz, int):
                continue
            heights = [
                surface_height(chunk, x, z, min_y=-64, height=2096, heightmap="WORLD_SURFACE")
                for z in range(16)
                for x in range(16)
            ]
            if not any(value is not None for value in heights):
                continue
            key = f"{cx},{cz}"
            if key in heightmaps:
                raise RuntimeError(f"Duplicate saved chunk coordinates {key}")
            heightmaps[key] = heights
            decoded_chunks += 1

    flat = sorted(value for values in heightmaps.values() for value in values if value is not None)
    if decoded_chunks == 0 or len(flat) < 100:
        raise RuntimeError(
            f"Insufficient persisted WORLD_SURFACE samples for {slug}: "
            f"chunks={decoded_chunks}, columns={len(flat)}"
        )

    canonical = json.dumps(
        {key: heightmaps[key] for key in sorted(heightmaps)},
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    digest = hashlib.sha256(canonical).hexdigest()
    return {
        "schema": "realscale:fitted_archetype_heightmap_audit/v1",
        "slug": slug,
        "world_name": world.name,
        "seed": 42,
        "heightmap": "WORLD_SURFACE",
        "world_min_y": -64,
        "world_height": 2096,
        "region_files": len(regions),
        "decoded_chunks": decoded_chunks,
        "sampled_columns": len(flat),
        "surface_y_min": min(flat),
        "surface_y_max": max(flat),
        "surface_y_p05": percentile(flat, 0.05),
        "surface_y_median": percentile(flat, 0.50),
        "surface_y_p95": percentile(flat, 0.95),
        "heightmap_sha256": digest,
        # Per-chunk arrays let the aggregate gate compare identical X/Z columns
        # across modes rather than comparing unrelated summary statistics.
        "heightmap_by_chunk": {key: heightmaps[key] for key in sorted(heightmaps)},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-root", type=Path, required=True)
    parser.add_argument("--world-name", required=True)
    parser.add_argument("--slug", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    report = audit(find_world(args.run_root, args.world_name), args.slug)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    compact = {key: value for key, value in report.items() if key != "heightmap_by_chunk"}
    print(json.dumps(compact, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
