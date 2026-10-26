#!/usr/bin/env python3
"""Fit compact procedural terrain parameters from a sourced DEM, never replay its grid."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import struct
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter

RGT_HEADER_BYTES = 40
FAMILIES = ("natural", "alpine", "canyon")
LAGS_PIXELS = (1, 2, 4, 8, 16, 32)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_rgt(path: Path):
    raw = gzip.decompress(path.read_bytes())
    if len(raw) < RGT_HEADER_BYTES or raw[:4] != b"RGT1":
        raise ValueError("Unsupported RGT file; expected the RealScale RGT1 format")
    cols, rows, _x0, _z0, _sea_y, cell_m, _vertical_offset = struct.unpack_from(">5i2d", raw, 4)
    count = cols * rows
    expected = RGT_HEADER_BYTES + count * 3 * 4 + count
    if cols < 32 or rows < 32 or count > 4_000_000 or len(raw) != expected:
        raise ValueError("Invalid RGT dimensions or payload length")
    if not math.isfinite(cell_m) or cell_m <= 0:
        raise ValueError("RGT grid spacing must be a positive finite number")
    elevation = np.frombuffer(raw, dtype=">f4", count=count, offset=RGT_HEADER_BYTES)
    elevation = elevation.reshape((rows, cols)).astype(np.float64)
    if not np.isfinite(elevation).all():
        raise ValueError("DEM contains NoData/non-finite elevations; do not silently fill them")
    return elevation, float(cell_m), float(cell_m), {
        "input_format": "RGT1",
        "crs": "local projected grid recorded by RGT1",
        "native_spacing_m": [float(cell_m), float(cell_m)],
        "rows": int(rows),
        "cols": int(cols),
    }


def read_geotiff(path: Path, elevation_unit: str):
    try:
        import rasterio
    except ImportError as exc:
        raise RuntimeError("GeoTIFF input requires rasterio; install research/v1.1/requirements.txt") from exc

    with rasterio.open(path) as src:
        if src.count < 1 or src.crs is None:
            raise ValueError("GeoTIFF must contain an elevation band and a declared CRS")
        masked = src.read(1, masked=True).astype(np.float64)
        elevation = np.asarray(masked.filled(np.nan), dtype=np.float64)
        transform = src.transform
        if src.crs.is_geographic:
            latitude = (float(src.bounds.top) + float(src.bounds.bottom)) / 2.0
            lon_factor = 111320.0 * math.cos(math.radians(latitude))
            lat_factor = 111132.0
            cell_x = math.hypot(transform.a * lon_factor, transform.d * lat_factor)
            cell_z = math.hypot(transform.b * lon_factor, transform.e * lat_factor)
        elif src.crs.is_projected:
            unit_info = src.crs.linear_units_factor
            factor = float(unit_info[1]) if unit_info else 1.0
            cell_x = math.hypot(transform.a, transform.d) * factor
            cell_z = math.hypot(transform.b, transform.e) * factor
        else:
            raise ValueError("Unsupported CRS type; use a geographic or projected GeoTIFF")
        crs_name = src.crs.to_string()
        rows, cols = elevation.shape

    if elevation_unit == "foot":
        elevation *= 0.3048
    if not math.isfinite(cell_x) or not math.isfinite(cell_z) or cell_x <= 0 or cell_z <= 0:
        raise ValueError("Could not infer positive metric pixel spacing from GeoTIFF metadata")
    if not np.isfinite(elevation).all():
        raise ValueError("DEM contains NoData/non-finite elevations; crop to complete valid coverage")
    return elevation, float(cell_x), float(cell_z), {
        "input_format": "GeoTIFF",
        "crs": crs_name,
        "native_spacing_m": [float(cell_x), float(cell_z)],
        "rows": int(rows),
        "cols": int(cols),
        "input_elevation_unit": elevation_unit,
    }


def read_dem(path: Path, elevation_unit: str):
    if path.name.lower().endswith(".rgt.gz"):
        if elevation_unit != "meter":
            raise ValueError("RGT1 elevations are already stored in meters; do not convert them again")
        return read_rgt(path)
    return read_geotiff(path, elevation_unit)


def _smooth(array: np.ndarray, scale_m: float, cell_x: float, cell_z: float) -> np.ndarray:
    sigma = (max(0.5, scale_m / cell_z), max(0.5, scale_m / cell_x))
    return gaussian_filter(array, sigma=sigma, mode="reflect")


def _clip(value: float, lower: float, upper: float) -> float:
    return float(max(lower, min(upper, value)))


def fit_profile(
    elevation: np.ndarray,
    cell_x: float,
    cell_z: float,
    family: str,
    source_name: str,
    source_url: str,
    vertical_datum: str,
    source_sha256: str,
    source_file: str,
    input_meta: dict,
):
    if family not in FAMILIES:
        raise ValueError("family must be one of: " + ", ".join(FAMILIES))
    if elevation.ndim != 2 or min(elevation.shape) < 32:
        raise ValueError("Archetype fitting needs a 2D DEM with at least 32 rows and 32 columns")
    if not np.isfinite(elevation).all():
        raise ValueError("DEM contains NoData/non-finite elevations; no values will be invented")
    if cell_x <= 0 or cell_z <= 0:
        raise ValueError("Pixel spacing must be positive")

    rows, cols = elevation.shape
    extent_x = (cols - 1) * cell_x
    extent_z = (rows - 1) * cell_z
    extent_m = min(extent_x, extent_z)
    if extent_m < 1500.0:
        raise ValueError("DEM window is too small for a stable regional fit; need at least 1.5 km per side")
    cell_mean = math.sqrt(cell_x * cell_z)

    fine_m = max(2.0 * cell_mean, min(250.0, extent_m / 32.0))
    mid_m = max(2.0 * fine_m, min(1000.0, extent_m / 8.0))
    coarse_m = max(2.0 * mid_m, min(4000.0, extent_m / 3.0))
    if coarse_m > extent_m * 0.8:
        coarse_m = extent_m * 0.8
    if mid_m >= coarse_m:
        mid_m = coarse_m / 2.0
    if fine_m >= mid_m:
        fine_m = mid_m / 2.0

    centered = elevation - float(np.mean(elevation))
    fine = _smooth(elevation, fine_m, cell_x, cell_z)
    coarse = _smooth(elevation, coarse_m, cell_x, cell_z)
    macro_field = coarse - float(np.mean(coarse))
    meso_field = fine - coarse
    micro_field = elevation - fine
    macro_rms = float(np.sqrt(np.mean(macro_field * macro_field)))
    meso_rms = float(np.sqrt(np.mean(meso_field * meso_field)))
    micro_rms = float(np.sqrt(np.mean(micro_field * micro_field)))
    total_rms = math.sqrt(macro_rms**2 + meso_rms**2 + micro_rms**2)

    dz, dx = np.gradient(centered, cell_z, cell_x)
    slope = np.degrees(np.arctan(np.hypot(dx, dz)))
    d2x = np.gradient(dx, cell_x, axis=1)
    d2z = np.gradient(dz, cell_z, axis=0)
    laplacian = d2x + d2z
    slope_p50 = float(np.percentile(slope, 50))
    slope_p75 = float(np.percentile(slope, 75))
    slope_p95 = float(np.percentile(slope, 95))
    high_slope = slope >= slope_p75
    if np.any(high_slope):
        valley_index = float(np.mean(laplacian[high_slope] > 0))
        ridge_index = float(np.mean(laplacian[high_slope] < 0))
    else:
        valley_index = ridge_index = 0.0

    semivariograms = []
    fit_x = []
    fit_z = []
    for lag in LAGS_PIXELS:
        if lag >= min(rows, cols):
            continue
        delta_x = elevation[:, lag:] - elevation[:, :-lag]
        delta_z = elevation[lag:, :] - elevation[:-lag, :]
        semi_x = 0.5 * float(np.mean(delta_x * delta_x))
        semi_z = 0.5 * float(np.mean(delta_z * delta_z))
        lag_x_m = lag * cell_x
        lag_z_m = lag * cell_z
        semivariograms.append({
            "lag_pixels": lag,
            "lag_x_m": round(lag_x_m, 4),
            "lag_z_m": round(lag_z_m, 4),
            "semi_x_m2": round(semi_x, 6),
            "semi_z_m2": round(semi_z, 6),
        })
        if semi_x > 1e-12:
            fit_x.append((math.log(lag_x_m), math.log(semi_x)))
        if semi_z > 1e-12:
            fit_z.append((math.log(lag_z_m), math.log(semi_z)))

    pairs = fit_x + fit_z
    if len(pairs) >= 2:
        variogram_exponent = float(np.polyfit(
            np.asarray([p[0] for p in pairs]),
            np.asarray([p[1] for p in pairs]), 1
        )[0])
    else:
        variogram_exponent = 1.0
    variogram_exponent = _clip(variogram_exponent, 0.0, 2.5)

    corrected_ratios = []
    for item in semivariograms:
        semi_x = item["semi_x_m2"]
        semi_z = item["semi_z_m2"]
        if semi_x > 1e-12 and semi_z > 1e-12:
            distance_correction = (item["lag_z_m"] / item["lag_x_m"]) ** variogram_exponent
            corrected_ratios.append((semi_x / semi_z) * distance_correction)
    anisotropy_raw = float(np.median(corrected_ratios)) if corrected_ratios else 1.0
    anisotropy_gain = _clip(math.sqrt(max(anisotropy_raw, 1e-12)), 0.5, 2.0)

    p05, p25, p50, p75, p95 = [float(np.percentile(elevation, p)) for p in (5, 25, 50, 75, 95)]
    relief = p95 - p05
    if relief <= 1e-6:
        raise ValueError("DEM relief is effectively flat; cannot fit a terrain archetype")
    rms_denominator = max(total_rms, 1e-12)
    fit_parameters = {
        "macro_amplitude_rms_m": round(macro_rms, 5),
        "meso_amplitude_rms_m": round(meso_rms, 5),
        "micro_amplitude_rms_m": round(micro_rms, 5),
        "macro_gain": round(macro_rms / rms_denominator, 6),
        "meso_gain": round(meso_rms / rms_denominator, 6),
        "micro_gain": round(micro_rms / rms_denominator, 6),
        "variogram_exponent": round(variogram_exponent, 6),
        "anisotropy_xz_raw": round(anisotropy_raw, 6),
        "anisotropy_gain": round(anisotropy_gain, 6),
        "ridge_sharpness": round(_clip((slope_p95 - slope_p50) / 60.0, 0.0, 1.0), 6),
        "cliff_index": round(_clip((slope_p95 - 20.0) / 70.0, 0.0, 1.0), 6),
        "valley_index": round(_clip(valley_index, 0.0, 1.0), 6),
        "ridge_index": round(_clip(ridge_index, 0.0, 1.0), 6),
    }

    family_kernel = {
        "natural": "balanced_multiscale",
        "alpine": "ridge_sharpened_multiscale",
        "canyon": "valley_and_cliff_multiscale",
    }[family]
    return {
        "schema": "realscale:terrain_archetype_fit/v1",
        "family": family,
        "family_kernel": family_kernel,
        "source": {
            "name": source_name,
            "url": source_url,
            "file_name": source_file,
            "sha256": source_sha256,
            "vertical_datum": vertical_datum,
            "elevation_units_used_for_fit": "meters",
            "input_format": input_meta["input_format"],
            "input_crs": input_meta["crs"],
            "native_spacing_m": input_meta["native_spacing_m"],
            "rows": rows,
            "cols": cols,
            "horizontal_extent_m": [round(extent_x, 3), round(extent_z, 3)],
        },
        "observed_statistics": {
            "elevation_percentiles_m": {
                "p05": round(p05, 4),
                "p25": round(p25, 4),
                "p50": round(p50, 4),
                "p75": round(p75, 4),
                "p95": round(p95, 4),
            },
            "relief_p95_minus_p05_m": round(relief, 4),
            "slope_degrees": {
                "p50": round(slope_p50, 5),
                "p75": round(slope_p75, 5),
                "p95": round(slope_p95, 5),
            },
            "scale_bands_m": {
                "fine": round(fine_m, 3),
                "mid": round(mid_m, 3),
                "coarse": round(coarse_m, 3),
            },
            "semivariograms": semivariograms,
        },
        "fit_parameters": fit_parameters,
        "generation_contract": {
            "mode": "procedural_from_fitted_coefficients",
            "raw_elevation_samples_embedded": False,
            "horizontal_meters_per_block": 1.0,
            "vertical_meters_per_block": None,
            "vertical_mapping_requires_explicit_choice": True,
            "absolute_elevation_is_not_replayed": True,
            "minecraft_height_budget_blocks": 2096,
            "preview_generator": "seeded_multiscale_spectral_noise_prototype",
        },
        "limitations": [
            "This is a parametric calibration profile, not a neural-network model.",
            "The raw DEM grid is not included and must not be replayed as the infinite-world generator.",
            "The profile is not yet wired into NeoForge DensityFunctions.",
            "DEM-derived morphology does not by itself prove tectonic, rainfall, or lithology causes.",
            "Absolute altitude and vertical scale must be handled explicitly by the eventual generator.",
        ],
    }


def _spectral_band_noise(rows, cols, cell_x, cell_z, exponent, anisotropy, low_cut, high_cut, rng):
    white = rng.standard_normal((rows, cols))
    spectrum = np.fft.fft2(white)
    fx = np.fft.fftfreq(cols, d=cell_x)[None, :]
    fz = np.fft.fftfreq(rows, d=cell_z)[:, None]
    frequency = np.sqrt((fx / max(anisotropy, 1e-6)) ** 2 + fz ** 2)
    mask = frequency > 0
    if low_cut is not None:
        mask &= frequency >= low_cut
    if high_cut is not None:
        mask &= frequency < high_cut
    if not np.any(mask):
        mask = frequency > 0
    spectral_filter = np.zeros_like(frequency)
    spectral_filter[mask] = np.power(frequency[mask], -exponent / 2.0)
    field = np.fft.ifft2(spectrum * spectral_filter).real
    field -= float(np.mean(field))
    spread = float(np.std(field))
    if spread < 1e-9:
        field = white - float(np.mean(white))
        spread = float(np.std(field))
    return field / max(spread, 1e-9)


def synthesize_heightfield(profile: dict, rows: int, cols: int, cell_x: float, cell_z: float, seed: int = 0):
    """Generate a new relative-height sample from fitted coefficients only."""
    if rows < 32 or cols < 32:
        raise ValueError("Synthetic preview needs at least 32 rows and 32 columns")
    if cell_x <= 0 or cell_z <= 0:
        raise ValueError("Preview pixel spacing must be positive")
    family = profile.get("family")
    if family not in FAMILIES:
        raise ValueError("Unknown fitted terrain family")
    params = profile["fit_parameters"]
    scales = profile["observed_statistics"]["scale_bands_m"]
    exponent = float(params["variogram_exponent"])
    anisotropy = float(params["anisotropy_gain"])
    fine_m = float(scales["fine"])
    coarse_m = float(scales["coarse"])
    rng = np.random.default_rng(seed)

    macro = _spectral_band_noise(rows, cols, cell_x, cell_z, exponent, anisotropy,
                                 None, 1.0 / max(coarse_m, 1e-6), rng)
    meso = _spectral_band_noise(rows, cols, cell_x, cell_z, exponent, anisotropy,
                                1.0 / max(coarse_m, 1e-6), 1.0 / max(fine_m, 1e-6), rng)
    micro = _spectral_band_noise(rows, cols, cell_x, cell_z, exponent, anisotropy,
                                 1.0 / max(fine_m, 1e-6), None, rng)
    result = (
        float(params["macro_amplitude_rms_m"]) * macro
        + float(params["meso_amplitude_rms_m"]) * meso
        + float(params["micro_amplitude_rms_m"]) * micro
    )
    if family == "alpine":
        ridge = 1.0 - np.abs(macro)
        ridge = (ridge - float(np.mean(ridge))) / max(float(np.std(ridge)), 1e-9)
        result += float(params["ridge_sharpness"]) * 0.25 * float(params["macro_amplitude_rms_m"]) * ridge
    elif family == "canyon":
        valley = np.abs(meso)
        valley = (valley - float(np.mean(valley))) / max(float(np.std(valley)), 1e-9)
        result -= float(params["cliff_index"]) * 0.25 * float(params["meso_amplitude_rms_m"]) * valley
    result -= float(np.mean(result))
    return result



def main():
    parser = argparse.ArgumentParser(description="Fit compact RealScale terrain archetype parameters from a sourced DEM")
    parser.add_argument("--dem", required=True, type=Path, help="Verified GeoTIFF or RealScale RGT1 .rgt.gz")
    parser.add_argument("--source", required=True, help="Exact provider/product name")
    parser.add_argument("--source-url", required=True, help="Official product/file provenance URL")
    parser.add_argument("--vertical-datum", required=True, help="Datum from the exact source metadata")
    parser.add_argument("--family", choices=FAMILIES, required=True)
    parser.add_argument("--elevation-unit", choices=("meter", "foot"), default="meter")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--preview-out", type=Path, help="Optional synthetic preview .npz generated from fitted parameters only")
    parser.add_argument("--preview-rows", type=int, default=256)
    parser.add_argument("--preview-cols", type=int, default=256)
    parser.add_argument("--preview-cell-m", type=float, default=32.0)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()

    if not args.dem.is_file():
        parser.error("DEM file not found: " + str(args.dem))
    elevation, cell_x, cell_z, input_meta = read_dem(args.dem, args.elevation_unit)
    profile = fit_profile(
        elevation=elevation,
        cell_x=cell_x,
        cell_z=cell_z,
        family=args.family,
        source_name=args.source,
        source_url=args.source_url,
        vertical_datum=args.vertical_datum,
        source_sha256=sha256_file(args.dem),
        source_file=args.dem.name,
        input_meta=input_meta,
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(profile, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.preview_out:
        preview = synthesize_heightfield(
            profile, args.preview_rows, args.preview_cols,
            args.preview_cell_m, args.preview_cell_m, args.seed
        )
        args.preview_out.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(
            args.preview_out,
            height_m=preview.astype(np.float32),
            cell_x_m=float(args.preview_cell_m),
            cell_z_m=float(args.preview_cell_m),
            seed=int(args.seed),
            family=profile["family"],
        )
    print(json.dumps({
        "output": str(args.out),
        "preview_output": str(args.preview_out) if args.preview_out else None,
        "family": profile["family"],
        "source_sha256": profile["source"]["sha256"],
        "relief_p95_minus_p05_m": profile["observed_statistics"]["relief_p95_minus_p05_m"],
        "fit_parameters": profile["fit_parameters"],
        "raw_elevation_samples_embedded": False,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
