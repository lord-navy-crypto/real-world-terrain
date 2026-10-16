#!/usr/bin/env python3
"""Import authoritative USGS 3DEP bare-earth DEM into RealScale's RGT1 format.

This tool runs BEFORE Minecraft world generation; the mod NEVER downloads
DEM data while generating chunks. Requires: python -m pip install rasterio.

USGS dynamic elevation service: https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer
Pixel values are expected to be FLOAT32 METRES, never RGB hillshade, and
the raster must contain real coverage for EVERY selected grid cell.
The resulting provenance JSON and SHA-256 checksum are mandatory.
"""
from __future__ import annotations

import argparse
import datetime
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import tempfile
import urllib.parse
import urllib.request

SERVICE = "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/exportImage"
SOURCE = "USGS 3DEP bare-earth elevation dynamic image service"
EPSG = 26910  # NAD83 / UTM zone 10N for Big Sur, California
GRID_CHOICES = (65, 129, 257, 513)
CELL_METRES = 32
MAX_TIFF_BYTES = 32 * 1024 * 1024
GAME_SEA_LEVEL = 63
MIN_WORLD_Y, MAX_WORLD_Y = -64, 2031
CENTER_LAT = 36.24
CENTER_LON = -121.81


def build_request(latitude=CENTER_LAT, longitude=CENTER_LON, size=257,
                  spacing=CELL_METRES):
    """Construct the exact raster geometry, 1 game block = 1 DEM metre."""
    if size not in GRID_CHOICES or spacing != CELL_METRES:
        raise ValueError("Supported odd grid sizes 65/129/257/513 at 32m spacing")
    if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
        raise ValueError("Invalid coordinates")
    if not (34.0 <= latitude <= 39.0 and -124.0 <= longitude <= -117.0):
        raise ValueError("Big Sur experiment uses NAD83 UTM zone 10N; "
                         "choose a California location in the supported region")
    try:
        from rasterio.warp import transform
    except ImportError as e:
        raise RuntimeError("rasterio is required: python -m pip install rasterio") from e
    east, north = transform("EPSG:4326", f"EPSG:{EPSG}",
                            [longitude], [latitude])
    e, n = east[0], north[0]
    half = size * spacing / 2
    bounds = (e - half, n - half, e + half, n + half)
    params = dict(bbox=",".join(f"{c:.8f}" for c in bounds),
                  bboxSR=str(EPSG), imageSR=str(EPSG),
                  size=f"{size},{size}", format="tiff",
                  pixelType="F32", interpolation="RSP_BilinearInterpolation",
                  f="image")
    url = SERVICE + "?" + urllib.parse.urlencode(params)
    return dict(url=url, bbox=bounds, grid_size=size, spacing=spacing,
                epsg=EPSG, center_utm=(e, n), latitude=latitude,
                longitude=longitude)


def fetch_raw_tiff(url: str) -> bytes:
    if not url.startswith(SERVICE + "?"):
        raise ValueError("Only the official USGS 3DEP image service is supported")
    request = urllib.request.Request(
        url, headers={"User-Agent": "RealScale-3DEP-import/1.0"})
    with urllib.request.urlopen(request, timeout=120) as resp:
        if resp.status != 200:
            raise RuntimeError(f"USGS returned HTTP {resp.status}")
        raw = resp.read(MAX_TIFF_BYTES + 1)
    if len(raw) > MAX_TIFF_BYTES or len(raw) < 64:
        raise RuntimeError("Unexpected source raster size")
    if raw[:4] not in (bytes((0x49,0x49,0x2a,0)), bytes((0x4d,0x4d,0,0x2a))):
        raise RuntimeError("USGS response was not TIFF; refusing preview/HTML/JSON")
    return raw


def unpack_real_raster(data: bytes, request: dict):
    """Reject rendered imagery, no-data pixels, and incorrect geodesy."""
    try:
        import numpy as np
        import rasterio
        from rasterio.io import MemoryFile
    except ImportError as e:
        raise RuntimeError("Requires rasterio and numpy") from e
    with MemoryFile(data) as mem:
        with mem.open() as dataset:
            n = request["grid_size"]
            if dataset.count != 1 or dataset.width != n or dataset.height != n:
                raise ValueError("Not a one-band measured elevation grid")
            if dataset.crs is None or dataset.crs.to_epsg() != request["epsg"]:
                raise ValueError("DEM coordinate system differs from requested NAD83 UTM")
            if dataset.dtypes[0] not in ("float32", "float64"):
                raise ValueError("USGS returned a rendered image, not floating-point heights")
            geo = dataset.transform
            west, south, east, north = request["bbox"]
            tolerance = 0.05
            if (abs(geo.a - request["spacing"]) > tolerance or
                abs(geo.e + request["spacing"]) > tolerance or
                abs(geo.b) > 1e-9 or abs(geo.d) > 1e-9 or
                abs(geo.c - west) > tolerance or abs(geo.f - north) > tolerance):
                raise ValueError("DEM pixel alignment differs from world grid")
            raster = dataset.read(1, masked=True)
            if np.ma.getmaskarray(raster).any():
                raise ValueError("3DEP raster contains NoData; choose an entirely covered region")
            arr = np.asarray(raster, dtype="float64")
            if not np.isfinite(arr).all():
                raise ValueError("DEM contains NaN/Inf")
            if (arr.min() + GAME_SEA_LEVEL < MIN_WORLD_Y or
                arr.max() + GAME_SEA_LEVEL > MAX_WORLD_Y):
                raise ValueError("1:1 real elevations exceed Minecraft world height; "
                                 "choose another area, never silently clamp mountains")
            return arr


def build_rgt1(values, size: int) -> bytes:
    """RGT1 = big-endian source elevations + neutral optional context arrays."""
    if size not in GRID_CHOICES or len(values) != size:
        raise ValueError("Expected a square, odd USGS raster")
    n = size * size
    origin = -(size // 2) * CELL_METRES
    header = struct.pack(">6i2d", 0x52475431, size, size, origin, origin,
                         GAME_SEA_LEVEL, float(CELL_METRES), 0.0)
    elevations = bytearray(n * 4)
    for z, row in enumerate(values):
        if len(row) != size:
            raise ValueError("Unexpected source raster width")
        for x, elevation in enumerate(row):
            elevation = float(elevation)
            if not math.isfinite(elevation) or not (
                    MIN_WORLD_Y <= elevation + GAME_SEA_LEVEL <= MAX_WORLD_Y):
                raise ValueError("Nonfinite or out-of-bounds USGS elevation")
            struct.pack_into(">f", elevations, (z*size+x)*4, elevation)
    zeros = b"\x00" * (n * 8 + n)  # neutral rainfall/lithology, NOT made-up measurements
    return gzip.compress(header + elevations + zeros, compresslevel=9, mtime=0)


def save_import(out_dir: Path, raster: bytes, request: dict):
    heights = unpack_real_raster(raster, request)
    pack = build_rgt1(heights, request["grid_size"])
    digest = hashlib.sha256(pack).hexdigest()
    manifest = {
        "format": "RealScale RGT1 3DEP extension",
        "origin": SOURCE,
        "source_url": request["url"],
        "source_sha256": hashlib.sha256(raster).hexdigest(),
        "rgt1_sha256": digest,
        "coordinate_reference_system": f"EPSG:{EPSG} NAD83 UTM 10N",
        "elevation_units": "metres",
        "vertical_reference": "USGS 3DEP; typically NAVD88 in CONUS",
        "minecraft_blocks_per_metre": 1,
        "minecraft_sea_level": GAME_SEA_LEVEL,
        "minecraft_origin_xz": [-(request["grid_size"]//2)*CELL_METRES]*2,
        "data_grid": [request["grid_size"]]*2,
        "cell_size_metres": CELL_METRES,
        "measured_ground_elevation_min_m": float(heights.min()),
        "measured_ground_elevation_max_m": float(heights.max()),
        "source_center_lat_lon": [request["latitude"], request["longitude"]],
        "retrieved_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "statement": "Interpolated bare-earth elevations derived from the USGS "
                     "3DEP dynamic service. NOT individually surveyed points. "
                     "NoData rejected; outside coverage is procedural fallback."
    }
    out_dir.mkdir(parents=True, exist_ok=True)
    # Fail before writing to the live config if any step was invalid.
    # Atomic rename keeps unfinished imports from appearing to Minecraft.
    def write_atomic(name, contents):
        with tempfile.NamedTemporaryFile(dir=out_dir, delete=False) as out:
            out.write(contents)
            temp_path = Path(out.name)
        os.replace(temp_path, out_dir / name)
    write_atomic("usgs_3dep.rgt.gz", pack)
    write_atomic("usgs_3dep.sha256", (digest + "\n").encode("ascii"))
    write_atomic("usgs_3dep.json", (json.dumps(manifest, indent=2) + "\n").encode())
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--latitude", type=float, default=CENTER_LAT)
    parser.add_argument("--longitude", type=float, default=CENTER_LON)
    parser.add_argument("--grid", type=int, default=257, choices=GRID_CHOICES)
    parser.add_argument("--output", type=Path, default=Path("config/realscale"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    request = build_request(args.latitude, args.longitude, args.grid)
    if args.dry_run:
        print(json.dumps(request, indent=2))
        return
    raster = fetch_raw_tiff(request["url"])
    report = save_import(args.output, raster, request)
    print(json.dumps(report, indent=2))
    print("Restart Minecraft/server before creating a NEW measured world.")


if __name__ == "__main__":
    main()
