#!/usr/bin/env python3
"""Compile verified fitted profiles into a selectable seven-mode worldgen datapack.

The output is a staging datapack/resource-pack pair. It never modifies source
resources in-place; reviewed output can be copied into the mod after QA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DEFAULT_RESOURCES = ROOT / "neoforge" / "src" / "main" / "resources"
FAMILY_CONFIG = {
    "alpine": {
        "target": "everest_himalaya",
        "preset": "alpine_three_realms",
        "display_en": "RealScale | Alpine Mountains — Everest/Himalaya fit",
        "display_zh": "RealScale｜高山地貌（珠峰/喜马拉雅拟合）",
        "density_root": "tva_alpine_fitted",
        "template_root": "tva_alpine_smoothed",
        "legacy_source": "himalayan",
    },
    "canyon": {
        "target": "grand_canyon",
        "preset": "canyon_three_realms",
        "display_en": "RealScale | Canyons & Valleys — Grand Canyon fit",
        "display_zh": "RealScale｜峡谷与河谷（大峡谷拟合）",
        "density_root": "tva_canyon_fitted",
        "template_root": "tva_canyon_smoothed",
        "legacy_source": "grand_canyon",
    },
    "glacial": {
        "target": "yosemite_glacial_valley",
        "preset": "glacial_three_realms",
        "display_en": "RealScale | Glacial Valleys — Yosemite fit",
        "display_zh": "RealScale｜冰川山谷（优胜美地拟合）",
        "density_root": "tva_glacial_fitted",
        "template_root": "tva_alpine_smoothed",
        "legacy_source": "himalayan",
    },
    "volcanic": {
        "target": "hawaii_volcanic",
        "preset": "volcanic_three_realms",
        "display_en": "RealScale | Volcanic Landscapes — Hawaiʻi fit",
        "display_zh": "RealScale｜火山地貌（夏威夷拟合）",
        "density_root": "tva_volcanic_fitted",
        "template_root": "tva_natural_smoothed",
        "legacy_source": "realism_max",
    },
    "karst": {
        "target": "guilin_karst",
        "preset": "karst_three_realms",
        "display_en": "RealScale | Karst Landscapes — Guilin fit",
        "display_zh": "RealScale｜喀斯特地貌（桂林拟合）",
        "density_root": "tva_karst_fitted",
        "template_root": "tva_canyon_smoothed",
        "legacy_source": "grand_canyon",
    },
    "dune": {
        "target": "namib_dunes",
        "preset": "dune_three_realms",
        "display_en": "RealScale | Sand Dunes — Namib Sand Sea fit",
        "display_zh": "RealScale｜沙丘地貌（纳米布沙海拟合）",
        "density_root": "tva_dune_fitted",
        "template_root": "tva_natural_smoothed",
        "legacy_source": "realism_max",
    },
}
CANONICAL = [
    ("natural_three_realms", "realscale:tva_natural_smoothed"),
    ("alpine_three_realms", "realscale:tva_alpine_fitted"),
    ("canyon_three_realms", "realscale:tva_canyon_fitted"),
    ("glacial_three_realms", "realscale:tva_glacial_fitted"),
    ("volcanic_three_realms", "realscale:tva_volcanic_fitted"),
    ("karst_three_realms", "realscale:tva_karst_fitted"),
    ("dune_three_realms", "realscale:tva_dune_fitted"),
]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def replace_root(value, old: str, new: str):
    if isinstance(value, str):
        return value.replace(old, new)
    if isinstance(value, list):
        return [replace_root(item, old, new) for item in value]
    if isinstance(value, dict):
        return {key: replace_root(item, old, new) for key, item in value.items()}
    return value


def compile_profiles(profiles_dir: Path, resources_root: Path, out_dir: Path):
    data_root = resources_root / "data" / "realscale" / "worldgen"
    preset_template = read_json(data_root / "world_preset" / "natural_three_realms.json")
    manifest_entries = []
    lang_en = {}
    lang_zh = {}
    preset_values = [f"realscale:{name}" for name, _ in CANONICAL]

    for family, config in FAMILY_CONFIG.items():
        profile_path = profiles_dir / f"{config['target']}.json"
        if not profile_path.is_file():
            raise FileNotFoundError(f"Missing real-data fit profile for {config['target']}: {profile_path}")
        profile = read_json(profile_path)
        if profile.get("schema") != "realscale:terrain_archetype_fit/v1":
            raise ValueError(f"Unexpected fit schema in {profile_path}")
        if profile.get("family") != family:
            raise ValueError(f"Profile family mismatch in {profile_path}: expected {family}")
        source = profile.get("source", {})
        if not source.get("sha256") or not source.get("url") or not source.get("vertical_datum"):
            raise ValueError(f"Fit profile has incomplete source provenance: {profile_path}")
        if profile.get("generation_contract", {}).get("raw_elevation_samples_embedded") is not False:
            raise ValueError(f"Refusing profile that embeds raw elevations: {profile_path}")

        cfg = config
        root_id = cfg["density_root"]
        template_id = cfg["template_root"]
        legacy_id = cfg["legacy_source"]
        fit = profile["fit_parameters"]
        required = (
            "macro_gain", "meso_gain", "micro_gain", "relief_gain", "ridge_sharpness",
            "cliff_index", "valley_index", "ridge_index", "anisotropy_gain",
            "dune_axis_radians", "dominant_wavelength_m",
        )
        missing = [key for key in required if key not in fit]
        if missing:
            raise ValueError(f"Profile {profile_path} lacks fit parameters: {missing}")

        noise_settings = read_json(data_root / "noise_settings" / f"{template_id}.json")
        noise_settings = replace_root(noise_settings, template_id, root_id)
        router = noise_settings["noise_router"]
        router["continents"] = f"realscale:{legacy_id}/continents"
        router["erosion"] = f"realscale:{legacy_id}/erosion"
        router["ridges"] = f"realscale:{legacy_id}/ridges"
        router["depth"] = f"realscale:{root_id}/terrain_density"
        router["initial_density_without_jaggedness"] = f"realscale:{root_id}/preliminary_density"
        router["final_density"]["base"] = f"realscale:{root_id}/terrain_density"
        adjusted = router.get("temperature", {}).get("adjusted", {})
        if isinstance(adjusted, dict):
            adjusted["height_signal"] = f"realscale:{root_id}/original_height_signal"
            adjusted["continents"] = f"realscale:{legacy_id}/continents"
        write_json(out_dir / "data/realscale/worldgen/noise_settings" / f"{root_id}.json", noise_settings)

        density_dir = out_dir / "data/realscale/worldgen/density_function" / root_id
        write_json(density_dir / "original_height_signal.json", {
            "type": "minecraft:interpolated",
            "argument": f"realscale:{legacy_id}/height_signal",
        })
        write_json(density_dir / "height_signal.json", {
            "type": "realscale:terrain_archetype",
            "base": f"realscale:{root_id}/original_height_signal",
            "continents": f"realscale:{legacy_id}/continents",
            "selector": {
                "type": "minecraft:noise",
                "noise": "minecraft:erosion",
                "xz_scale": 0.006,
                "y_scale": 0,
            },
            "family": family,
            "macro_gain": float(fit["macro_gain"]),
            "meso_gain": float(fit["meso_gain"]),
            "micro_gain": float(fit["micro_gain"]),
            "relief_gain": float(fit["relief_gain"]),
            "ridge_sharpness": float(fit["ridge_sharpness"]),
            "cliff_index": float(fit["cliff_index"]),
            "valley_index": float(fit["valley_index"]),
            "ridge_index": float(fit["ridge_index"]),
            "anisotropy_gain": float(fit["anisotropy_gain"]),
            "dune_axis_radians": max(-math.pi, min(math.pi, float(fit["dune_axis_radians"]))),
            "dominant_wavelength_m": max(1.0, min(1000000.0, float(fit["dominant_wavelength_m"]))),
        })
        for file_name in ("terrain_density.json", "preliminary_density.json"):
            source_file = data_root / "density_function" / template_id / file_name
            write_json(density_dir / file_name, replace_root(read_json(source_file), template_id, root_id))

        preset = json.loads(json.dumps(preset_template))
        preset["dimensions"]["minecraft:overworld"]["generator"]["settings"] = f"realscale:{root_id}"
        write_json(out_dir / "data/realscale/worldgen/world_preset" / f"{cfg['preset']}.json", preset)
        lang_en[f"generator.realscale.{cfg['preset']}"] = cfg["display_en"]
        lang_zh[f"generator.realscale.{cfg['preset']}"] = cfg["display_zh"]
        manifest_entries.append({
            "family": family,
            "target_id": config["target"],
            "preset_id": config["preset"],
            "noise_settings_id": root_id,
            "profile_sha256": sha256_file(profile_path),
            "source_sha256": source["sha256"],
            "source_tile_sha256": source.get("source_tile_sha256"),
            "source_url": source["url"],
            "vertical_datum": source["vertical_datum"],
            "native_spacing_m": source["native_spacing_m"],
            "relief_p95_minus_p05_m": profile["observed_statistics"]["relief_p95_minus_p05_m"],
        })

    for tag_name in ("normal", "extended"):
        write_json(out_dir / "data/minecraft/tags/worldgen/world_preset" / f"{tag_name}.json", {
            "replace": False,
            "values": preset_values,
        })
    lang_dir = out_dir / "assets/minecraft/lang"
    write_json(lang_dir / "en_us.json", lang_en)
    write_json(lang_dir / "zh_cn.json", lang_zh)
    write_json(out_dir / "terrain-profile-provenance.json", {
        "schema": "realscale:compiled_terrain_archetypes/v1",
        "default_mode": "realscale:natural_three_realms",
        "preset_count": len(CANONICAL),
        "profile_count": len(manifest_entries),
        "profiles": manifest_entries,
        "raw_dem_grids_embedded": False,
        "runtime_density_function": "realscale:terrain_archetype",
        "status": "compiled_from_verified_real_dem_profiles",
    })
    write_json(out_dir / "pack.mcmeta", {
        "pack": {
            "pack_format": 48,
            "description": "RealScale fitted terrain archetypes — Natural + six real-data profiles",
        }
    })
    return manifest_entries


def main(argv=None):
    parser = argparse.ArgumentParser(description="Compile six verified fit profiles into a selectable RealScale worldgen pack")
    parser.add_argument("--profiles-dir", type=Path, required=True)
    parser.add_argument("--resources-root", type=Path, default=DEFAULT_RESOURCES)
    parser.add_argument("--out-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    entries = compile_profiles(args.profiles_dir, args.resources_root, args.out_dir)
    print(json.dumps({
        "out_dir": str(args.out_dir),
        "preset_count": len(CANONICAL),
        "fitted_profile_count": len(entries),
        "families": [entry["family"] for entry in entries],
        "raw_dem_grids_embedded": False,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
