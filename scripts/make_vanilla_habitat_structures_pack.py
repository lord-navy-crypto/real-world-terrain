#!/usr/bin/env python3
"""Optional NeoForge 1.21.1 ecological structures made of ONLY vanilla blocks.

Requires the RealScale mod's registered feature codecs. No world-preset or
biome-source replacement, new block/mob/biome IDs, or automatic installation.
Both feature implementations explicitly reject non-RealScale noise settings.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

NS = "realscale_habitats"
PACK_FORMAT = 48
STRUCTURES = {
    "fallen_timber": {
        "type": "realscale:fallen_timber",
        "biomes": ["minecraft:old_growth_pine_taiga",
                   "minecraft:old_growth_spruce_taiga",
                   "minecraft:taiga", "minecraft:old_growth_birch_forest"],
        "count": 2,
    },
    "riparian_reeds": {
        "type": "realscale:riparian_reeds",
        "biomes": ["minecraft:river", "minecraft:frozen_river"],
        "count": 3,
    },
}

def resources():
    data = {"pack.mcmeta": {"pack": {
        "pack_format": PACK_FORMAT,
        "description": "RealScale opt-in vanilla-block fallen logs and riparian habitats (experimental)"
    }}}
    for name, profile in STRUCTURES.items():
        prefix = f"data/{NS}"
        key = f"{NS}:{name}"
        data[f"{prefix}/worldgen/configured_feature/{name}.json"] = {
            "type": profile["type"], "config": {}
        }
        data[f"{prefix}/worldgen/placed_feature/{name}.json"] = {
            "feature": key,
            "placement": [
                {"type": "minecraft:count", "count": profile["count"]},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
                {"type": "minecraft:biome"},
            ],
        }
        data[f"{prefix}/neoforge/biome_modifier/{name}.json"] = {
            "type": "neoforge:add_features",
            "biomes": profile["biomes"],
            "features": key,
            "step": "vegetal_decoration",
        }
    return data

def validate(data):
    assert data["pack.mcmeta"]["pack"]["pack_format"] == 48
    assert len(data) == 1 + 3 * len(STRUCTURES)
    for name, profile in STRUCTURES.items():
        path = f"data/{NS}"
        cfg = data[f"{path}/worldgen/configured_feature/{name}.json"]
        placed = data[f"{path}/worldgen/placed_feature/{name}.json"]
        mod = data[f"{path}/neoforge/biome_modifier/{name}.json"]
        assert cfg == {"type": profile["type"], "config": {}}
        assert placed["feature"] == mod["features"] == f"{NS}:{name}"
        assert mod["step"] == "vegetal_decoration"
        assert 0 < profile["count"] <= 3
        assert all(b.startswith("minecraft:") for b in profile["biomes"])
        assert placed["placement"][-1] == {"type": "minecraft:biome"}
    assert not any("/worldgen/biome/" in x or "/worldgen/noise_settings/" in x
                   or "/worldgen/world_preset/" in x or "/worldgen/structure_set/" in x
                   for x in data)
    for path, obj in data.items():
        assert not path.startswith("/") and ".." not in Path(path).parts
        json.loads(json.dumps(obj))

def build(destination: Path):
    data = resources()
    validate(data)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
        for path, obj in sorted(data.items()):
            archive.writestr(path, json.dumps(obj, indent=2, ensure_ascii=False) + "\n")
    print("OPT_IN_VANILLA_HABITATS:", destination)
    print("FEATURES:", ", ".join(STRUCTURES))
    print("Only new RealScale chunks in newly created opt-in worlds; not new biomes.")
    return destination

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(
        "build/RealScale-Vanilla-Habitat-Structures-EXPERIMENTAL.zip"))
    args = parser.parse_args()
    build(args.output)
