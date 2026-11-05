#!/usr/bin/env python3
"""Acquire and crop a real Copernicus GLO-90 COG tile for a named archetype target.

Raw source tiles and cropped DEMs are research inputs and are not committed to
the repository. Only provenance hashes and fitted coefficients should be shared.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import numpy as np
import rasterio
from rasterio.windows import from_bounds

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "data" / "terrain_dem_targets.json"
BASE_URL = "https://copernicus-dem-90m.s3.eu-central-1.amazonaws.com"
TILE_PATTERN = re.compile(r"^[NS]\d{2}_00_[EW]\d{3}_00$")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def copernicus_tile_url(tile: str) -> str:
    if not TILE_PATTERN.fullmatch(tile):
        raise ValueError(f"Invalid Copernicus one-degree tile identifier: {tile!r}")
    name = f"Copernicus_DSM_COG_30_{tile}_DEM"
    return f"{BASE_URL}/{name}/{name}.tif"


def read_manifest(path: Path = MANIFEST) -> dict:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("schema") != "realscale:terrain_dem_targets/v2":
        raise ValueError("Unexpected terrain DEM target manifest schema")
    targets = manifest.get("targets")
    if not isinstance(targets, list) or not targets:
        raise ValueError("DEM target manifest must contain targets")
    ids = [target.get("id") for target in targets]
    if len(ids) != len(set(ids)) or any(not isinstance(value, str) for value in ids):
        raise ValueError("DEM target IDs must be unique non-empty strings")
    return manifest


def _download(url: str, destination: Path, force: bool = False) -> None:
    if destination.is_file() and destination.stat().st_size > 1024 and not force:
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    part = destination.with_suffix(destination.suffix + ".part")
    request = Request(url, headers={"User-Agent": "RealScale-TerrainFit/0.1 (open-data research)"})
    last_error = None
    for attempt in range(3):
        try:
            with urlopen(request, timeout=120) as response, part.open("wb") as output:
                if response.status != 200:
                    raise RuntimeError(f"Unexpected HTTP status {response.status} for {url}")
                shutil.copyfileobj(response, output)
            if part.stat().st_size <= 1024:
                raise RuntimeError(f"Downloaded object is unexpectedly small: {url}")
            with rasterio.open(part) as dataset:
                if dataset.count < 1 or dataset.crs is None:
                    raise RuntimeError(f"Downloaded object is not a georeferenced DEM: {url}")
                if not np.isfinite(dataset.read(1, out_dtype="float32", masked=True).compressed()).all():
                    raise RuntimeError(f"Downloaded DEM contains non-finite valid pixels: {url}")
            part.replace(destination)
            return
        except (HTTPError, URLError, TimeoutError, RuntimeError, rasterio.errors.RasterioError) as exc:
            last_error = exc
            part.unlink(missing_ok=True)
            if attempt < 2:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"Failed to acquire DEM tile after 3 attempts: {url}: {last_error}")


def acquire_target(target_id: str, output_dir: Path, manifest_path: Path = MANIFEST, force: bool = False):
    manifest = read_manifest(manifest_path)
    matches = [target for target in manifest["targets"] if target["id"] == target_id]
    if not matches:
        raise ValueError(f"Unknown target {target_id!r}; expected one of: " + ", ".join(t["id"] for t in manifest["targets"]))
    target = matches[0]
    tile = target["tile"]
    url = copernicus_tile_url(tile)
    raw_dir = output_dir / "source_tiles"
    dem_dir = output_dir / "dems"
    raw_path = raw_dir / f"{target_id}_glo90.tif"
    crop_path = dem_dir / f"{target_id}.tif"
    sidecar_path = dem_dir / f"{target_id}.source.json"

    _download(url, raw_path, force=force)
    bbox = target["study_window_degrees"]
    west, south, east, north = (float(bbox[key]) for key in ("west", "south", "east", "north"))
    if not (west < east and south < north):
        raise ValueError(f"Invalid study window for {target_id}")
    with rasterio.open(raw_path) as src:
        bounds = src.bounds
        tolerance = 0.01
        if west < bounds.left - tolerance or east > bounds.right + tolerance or south < bounds.bottom - tolerance or north > bounds.top + tolerance:
            raise ValueError(
                f"Study window for {target_id} falls outside tile {tile}: "
                f"window={(west, south, east, north)}, tile={(bounds.left, bounds.bottom, bounds.right, bounds.top)}"
            )
        window = from_bounds(west, south, east, north, transform=src.transform)
        window = window.round_offsets().round_lengths()
        data = src.read(1, window=window, masked=True)
        if data.shape[0] < 32 or data.shape[1] < 32:
            raise ValueError(f"Cropped DEM for {target_id} is too small: {data.shape}")
        nodata = src.nodata if src.nodata is not None else -32767.0
        filled = np.asarray(data.filled(nodata))
        if not np.isfinite(filled).all():
            raise ValueError(f"Cropped DEM for {target_id} contains NaN or infinite values")
        profile = src.profile.copy()
        profile.update(
            driver="GTiff",
            height=int(data.shape[0]),
            width=int(data.shape[1]),
            count=1,
            transform=src.window_transform(window),
            nodata=nodata,
            compress="DEFLATE",
            predictor=3,
        )
        dem_dir.mkdir(parents=True, exist_ok=True)
        with rasterio.open(crop_path, "w", **profile) as dst:
            dst.write(filled, 1)

    sidecar = {
        "schema": "realscale:dem_acquisition_provenance/v1",
        "target_id": target_id,
        "target_display_name": target["display_name"],
        "landscape": target["landscape"],
        "provider": "Copernicus DEM GLO-90 public AWS COG",
        "tile": tile,
        "tile_url": url,
        "source_tile_sha256": sha256_file(raw_path),
        "cropped_dem_file": crop_path.name,
        "cropped_dem_sha256": sha256_file(crop_path),
        "study_window_degrees": bbox,
        "horizontal_crs": "WGS84 / EPSG:4326",
        "vertical_datum": "EGM2008",
        "vertical_units": "meters",
        "surface_type": "digital surface model (DSM)",
        "nominal_spacing_m": 90,
        "license_and_attribution": manifest["data_policy"]["license_url"],
        "documentation_url": manifest["data_policy"]["documentation_url"],
        "acquired_at_utc": datetime.now(timezone.utc).isoformat(),
        "raw_source_tile_committed": False,
    }
    sidecar_path.write_text(json.dumps(sidecar, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return crop_path, sidecar


def main(argv=None):
    manifest = read_manifest()
    parser = argparse.ArgumentParser(description="Download/crop real Copernicus GLO-90 DEM data for RealScale archetype fitting")
    parser.add_argument("--target", choices=[t["id"] for t in manifest["targets"]], action="append", help="Target ID; repeat to select several")
    parser.add_argument("--all", action="store_true", help="Acquire all six target regions")
    parser.add_argument("--out-dir", type=Path, default=Path("build/terrain-dem-inputs"))
    parser.add_argument("--force", action="store_true", help="Redownload source tiles")
    args = parser.parse_args(argv)
    targets = [t["id"] for t in manifest["targets"]] if args.all or not args.target else args.target
    results = []
    for target_id in targets:
        path, sidecar = acquire_target(target_id, args.out_dir, force=args.force)
        results.append({"target_id": target_id, "dem": str(path), "sha256": sidecar["cropped_dem_sha256"], "tile_sha256": sidecar["source_tile_sha256"]})
    print(json.dumps({"acquired": results, "raw_source_tiles_committed": False}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, rasterio.errors.RasterioError) as exc:
        print(f"Terrain DEM acquisition error: {exc}", file=sys.stderr)
        raise SystemExit(1)
