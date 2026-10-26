#!/usr/bin/env python3
"""Acquire six public DEM windows and fit reproducible terrain archetype profiles."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import acquire_terrain_dem as acquisition
import fit_terrain_archetype as fitter


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(target_ids, out_dir: Path, seed: int = 42, force_download: bool = False):
    manifest = acquisition.read_manifest()
    target_by_id = {target["id"]: target for target in manifest["targets"]}
    out_dir.mkdir(parents=True, exist_ok=True)
    profiles_dir = out_dir / "profiles"
    previews_dir = out_dir / "previews"
    profiles_dir.mkdir(parents=True, exist_ok=True)
    previews_dir.mkdir(parents=True, exist_ok=True)
    summary = {
        "schema": "realscale:terrain_archetype_calibration_run/v1",
        "data_manifest_schema": manifest["schema"],
        "profile_count": 0,
        "profiles": [],
        "raw_dem_inputs_committed": False,
        "note": "Profiles are fitted from real Copernicus GLO-90 DSM crops and compiled by this branch into NeoForge terrain_archetype density functions; they are not raw-heightmap playback. Vertical relief remains normalized to the existing world-height budget and does not reproduce absolute DEM altitude.",
    }
    for target_id in target_ids:
        target = target_by_id[target_id]
        dem_path, source_meta = acquisition.acquire_target(
            target_id, out_dir / "inputs", force=force_download
        )
        elevation, cell_x, cell_z, input_meta = fitter.read_dem(dem_path, "meter")
        profile = fitter.fit_profile(
            elevation=elevation,
            cell_x=cell_x,
            cell_z=cell_z,
            family=target["family"],
            source_name="Copernicus DEM GLO-90 public AWS COG",
            source_url=source_meta["tile_url"],
            vertical_datum="EGM2008",
            source_sha256=sha256_file(dem_path),
            source_file=dem_path.name,
            input_meta=input_meta,
        )
        profile["target"] = {
            "id": target["id"],
            "display_name": target["display_name"],
            "landscape": target["landscape"],
            "fit_goal": target["fit_goal"],
        }
        profile["source"]["source_tile_sha256"] = source_meta["source_tile_sha256"]
        profile["source"]["source_dataset"] = "Copernicus DEM GLO-90"
        profile["source"]["surface_type"] = "DSM"
        profile["source"]["nominal_spacing_m"] = 90
        profile["limitations"].append(
            "This calibration uses the global 90 m DSM baseline; it cannot resolve fine cliffs, small sinkholes, or individual dune ripples."
        )
        profile["limitations"].append(
            "The profile is a real-data-derived morphology fit, but game runtime integration still requires explicit density-function compilation and worldgen QA."
        )
        profile_path = profiles_dir / f"{target_id}.json"
        profile_path.write_text(
            json.dumps(profile, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        cell_m = math.sqrt(cell_x * cell_z)
        preview = fitter.synthesize_heightfield(
            profile, 256, 256, cell_m, cell_m, seed=seed
        )
        preview_path = previews_dir / f"{target_id}_seed{seed}.npz"
        import numpy as np
        np.savez_compressed(
            preview_path,
            height_m=preview.astype(np.float32),
            cell_x_m=float(cell_m),
            cell_z_m=float(cell_m),
            seed=int(seed),
            family=target["family"],
            target_id=target_id,
        )
        summary["profiles"].append({
            "target_id": target_id,
            "family": target["family"],
            "profile": str(profile_path.relative_to(out_dir)),
            "profile_sha256": sha256_file(profile_path),
            "preview": str(preview_path.relative_to(out_dir)),
            "preview_sha256": sha256_file(preview_path),
            "source_tile_sha256": source_meta["source_tile_sha256"],
            "cropped_dem_sha256": source_meta["cropped_dem_sha256"],
            "relief_p95_minus_p05_m": profile["observed_statistics"]["relief_p95_minus_p05_m"],
            "fit_parameters": profile["fit_parameters"],
        })
    summary["profile_count"] = len(summary["profiles"])
    summary_path = out_dir / "calibration_summary.json"
    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return summary


def main(argv=None):
    manifest = acquisition.read_manifest()
    valid_ids = [target["id"] for target in manifest["targets"]]
    parser = argparse.ArgumentParser(
        description="Acquire real terrain DEMs and fit six parameter-only RealScale archetype profiles"
    )
    parser.add_argument("--target", choices=valid_ids, action="append", help="Select one or more target IDs")
    parser.add_argument("--all", action="store_true", help="Fit all six reference landscapes")
    parser.add_argument("--out-dir", type=Path, default=Path("build/terrain-archetype-calibration"))
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--force-download", action="store_true")
    args = parser.parse_args(argv)
    targets = valid_ids if args.all or not args.target else args.target
    summary = build(targets, args.out_dir, args.seed, args.force_download)
    print(json.dumps({
        "summary": str(args.out_dir / "calibration_summary.json"),
        "profile_count": summary["profile_count"],
        "profiles": [
            {
                "target_id": item["target_id"],
                "family": item["family"],
                "relief_p95_minus_p05_m": item["relief_p95_minus_p05_m"],
                "profile_sha256": item["profile_sha256"],
            }
            for item in summary["profiles"]
        ],
        "raw_dem_inputs_committed": False,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as exc:
        print(f"Terrain archetype calibration error: {exc}", file=sys.stderr)
        raise SystemExit(1)
