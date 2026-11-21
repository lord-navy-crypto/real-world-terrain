#!/usr/bin/env python3
"""Validate the audited preset catalog against the actual 1.21.1 resources."""
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge" / "src" / "main" / "resources"
CATALOG = ROOT / "docs" / "PRESET_CATALOG.json"


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def fail(message: str) -> None:
    raise SystemExit("PRESET CATALOG ERROR: " + message)


def main() -> int:
    catalog = load(CATALOG)
    public = catalog["public_profiles"]
    hidden = catalog["hidden_profiles"]
    legacy = catalog["legacy_profiles"]
    public_ids = [entry["id"] for entry in public]
    all_ids = public_ids + [entry["id"] for entry in hidden] + [
        entry["id"] for entry in legacy
    ]

    if len(all_ids) != len(set(all_ids)):
        fail("duplicate preset ID in catalog")
    if len(public_ids) != 1:
        fail(f"expected exactly one public profile, found {len(public_ids)}")
    if len(hidden) + len(legacy) != catalog["counts"]["hidden_or_legacy_profiles"]:
        fail("hidden/legacy catalog count does not match metadata")

    preset_root = RES / "data" / "realscale" / "worldgen" / "world_preset"
    actual_ids = sorted(path.stem for path in preset_root.glob("*.json"))
    if set(actual_ids) != set(all_ids):
        missing = sorted(set(actual_ids) - set(all_ids))
        unknown = sorted(set(all_ids) - set(actual_ids))
        fail(f"catalog/resource mismatch; uncataloged={missing}, missing_resources={unknown}")

    expected_tag_values = [f"realscale:{preset_id}" for preset_id in public_ids]
    tag_root = RES / "data" / "minecraft" / "tags" / "worldgen" / "world_preset"
    for tag_name in ("normal", "extended"):
        tag = load(tag_root / f"{tag_name}.json")
        if tag.get("replace") is not False:
            fail(f"{tag_name}.json must keep replace=false")
        if tag.get("values") != expected_tag_values:
            fail(f"{tag_name}.json does not match the public catalog order")

    for profile in public:
        preset_id = profile["id"]
        preset = load(preset_root / f"{preset_id}.json")
        dimensions = preset.get("dimensions", {})
        if set(dimensions) != set(profile["dimensions"]):
            fail(f"{preset_id}: dimension set differs from catalog")
        overworld = dimensions["minecraft:overworld"]["generator"]
        expected_noise = "realscale:" + profile["noise_settings"]
        if overworld.get("settings") != expected_noise:
            fail(f"{preset_id}: expected noise settings {expected_noise}")
        noise_path = RES / "data" / "realscale" / "worldgen" / "noise_settings" / (
            profile["noise_settings"] + ".json"
        )
        if not noise_path.is_file():
            fail(f"{preset_id}: missing noise settings resource {noise_path.name}")

        chain = []
        biome_source = overworld.get("biome_source", {})
        while isinstance(biome_source, dict) and biome_source.get("type"):
            chain.append(biome_source["type"])
            biome_source = biome_source.get("source")
        if chain != profile["biome_source_chain"]:
            fail(f"{preset_id}: biome source chain differs; actual={chain}")
        if preset_id == "natural_automate_three_realms":
            height_path = RES / "data/realscale/worldgen/density_function/natural_automate/height_signal.json"
            kernel = load(height_path).get("argument", {})
            if kernel.get("family") != "natural_automate":
                fail("Natural Automate is missing its composite morphology density")
            environment = kernel.get("environment", {})
            if not {"temperature", "humidity", "region_selector"}.issubset(environment):
                fail("Natural Automate must use raw temperature, humidity and regional suitability inputs")
            if environment.get("region_selector", {}).get("noise") != "minecraft:offset":
                fail("Natural Automate regional selector must reference a registered vanilla noise")
            expected_chain = [
                "realscale:sky_ocean",
                "realscale:geo_wonders_opt_in",
                "realscale:natural_ecotones",
                "realscale:cave_depth_habitats",
                "minecraft:multi_noise",
            ]
            if chain != expected_chain:
                fail(f"Natural Automate ecology chain differs; actual={chain}")

    # Regression guard: minecraft:weirdness is a density-function key, not a
    # worldgen/noise registry entry. It must never be referenced by minecraft:noise.
    density_root = RES / "data/realscale/worldgen/density_function"
    for density_path in density_root.rglob("*.json"):
        density_data = load(density_path)
        def reject_unregistered_weirdness(node):
            if isinstance(node, dict):
                if node.get("type") == "minecraft:noise" and node.get("noise") == "minecraft:weirdness":
                    fail(f"{density_path.relative_to(ROOT)} references unregistered minecraft:weirdness noise")
                for value in node.values():
                    reject_unregistered_weirdness(value)
            elif isinstance(node, list):
                for value in node:
                    reject_unregistered_weirdness(value)
        reject_unregistered_weirdness(density_data)

    resource_counts = {
        "world_preset_json": len(list(preset_root.glob("*.json"))),
        "noise_settings_json": len(list((RES / "data/realscale/worldgen/noise_settings").glob("*.json"))),
        "density_function_json": len(list((RES / "data/realscale/worldgen/density_function").rglob("*.json"))),
        "biome_json": len(list((RES / "data/realscale/worldgen/biome").glob("*.json"))),
        "configured_feature_json": len(list((RES / "data/realscale/worldgen/configured_feature").glob("*.json"))),
        "placed_feature_json": len(list((RES / "data/realscale/worldgen/placed_feature").glob("*.json"))),
    }
    for key, actual in resource_counts.items():
        expected = catalog["counts"][key]
        if actual != expected:
            fail(f"{key}: catalog says {expected}, repository has {actual}")

    print(
        "PASS: preset catalog matches all 41 world presets, both single-entry menu tags, "
        "public noise settings, biome-source chains and audited resource counts."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
