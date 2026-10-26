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


def audit(world: Path, slug: str, target_chunk_min: int, target_chunk_max: int) -> dict:
    regions = sorted((world / "region").glob("r.*.*.mca"))
    if not regions:
        raise RuntimeError(f"No Anvil region files under {world / 'region'}")

    heightmaps: dict[str, list[int | None]] = {}
    world_surface_maps: dict[str, list[int | None]] = {}
    decoded_chunks = 0
    for region in regions:
        if region.stat().st_size <= 8192:
            continue
        for chunk in iter_region_chunks(region):
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            if not isinstance(cx, int) or not isinstance(cz, int):
                continue
            # Only count the explicit forceload sample area. Spawn chunks can
            # differ between presets, so they must never satisfy this gate.
            if not (target_chunk_min <= cx <= target_chunk_max
                    and target_chunk_min <= cz <= target_chunk_max):
                continue
            # WORLD_SURFACE is capped by the water surface in oceans. Compare
            # OCEAN_FLOOR as the primary terrain signal so submerged landforms
            # are not invisible to the acceptance test.
            floor_heights = [
                surface_height(chunk, x, z, min_y=-64, height=2096, heightmap="OCEAN_FLOOR")
                for z in range(16)
                for x in range(16)
            ]
            surface_heights = [
                surface_height(chunk, x, z, min_y=-64, height=2096, heightmap="WORLD_SURFACE")
                for z in range(16)
                for x in range(16)
            ]
            if not any(value is not None for value in floor_heights):
                continue
            key = f"{cx},{cz}"
            if key in heightmaps:
                raise RuntimeError(f"Duplicate saved chunk coordinates {key}")
            heightmaps[key] = floor_heights
            world_surface_maps[key] = surface_heights
            decoded_chunks += 1

    flat = sorted(value for values in heightmaps.values() for value in values if value is not None)
    surface_flat = sorted(
        value for values in world_surface_maps.values() for value in values if value is not None
    )
    if decoded_chunks < 40 or len(flat) < 40 * 200:
        raise RuntimeError(
            f"Insufficient persisted OCEAN_FLOOR samples in the forced sample area for {slug}: "
            f"chunks={decoded_chunks}, columns={len(flat)}, "
            f"target_chunks={target_chunk_min}..{target_chunk_max}"
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
        "heightmap": "OCEAN_FLOOR",
        "surface_heightmap": "WORLD_SURFACE",
        "world_min_y": -64,
        "world_height": 2096,
        "region_files": len(regions),
        "target_chunk_min": target_chunk_min,
        "target_chunk_max": target_chunk_max,
        "decoded_chunks": decoded_chunks,
        "sampled_columns": len(flat),
        "terrain_floor_y_min": min(flat),
        "terrain_floor_y_max": max(flat),
        "terrain_floor_y_p05": percentile(flat, 0.05),
        "terrain_floor_y_median": percentile(flat, 0.50),
        "terrain_floor_y_p95": percentile(flat, 0.95),
        "world_surface_y_min": min(surface_flat) if surface_flat else None,
        "world_surface_y_max": max(surface_flat) if surface_flat else None,
        "world_surface_y_median": percentile(surface_flat, 0.50) if surface_flat else None,
        "heightmap_sha256": digest,
        # Per-chunk arrays let the aggregate gate compare identical X/Z columns
        # across modes rather than comparing unrelated summary statistics.
        "heightmap_by_chunk": {key: heightmaps[key] for key in sorted(heightmaps)},
        "world_surface_by_chunk": {
            key: world_surface_maps[key] for key in sorted(world_surface_maps)
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-root", type=Path, required=True)
    parser.add_argument("--world-name", required=True)
    parser.add_argument("--slug", required=True)
    parser.add_argument("--target-chunk-min", type=int, default=60)
    parser.add_argument("--target-chunk-max", type=int, default=68)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    report = audit(
        find_world(args.run_root, args.world_name),
        args.slug,
        args.target_chunk_min,
        args.target_chunk_max,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    compact = {key: value for key, value in report.items() if key != "heightmap_by_chunk"}
    print(json.dumps(compact, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
