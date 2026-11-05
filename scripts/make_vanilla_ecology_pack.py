#!/usr/bin/env python3
"""Build a separately installable, vanilla-block-only NeoForge 1.21.1 datapack.

Experimental MICROHABITATS, not new Biome registry IDs.  The resulting ZIP is
never automatically installed and changes no existing RealScale worldgen
density, sea level, biome source or saved chunks.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

PACK_FORMAT = 48
NAMESPACE = "realscale_eco_lab"
PROFILES = {
    "alpine_lichen_mat": {
        "biomes": ["minecraft:stony_peaks", "minecraft:windswept_gravelly_hills"],
        "block": "minecraft:moss_carpet",
        "supports": ["minecraft:stone", "minecraft:andesite", "minecraft:gravel"],
        "count": 3, "tries": 20, "xz_spread": 4, "y_spread": 2,
        "heightmap": "MOTION_BLOCKING_NO_LEAVES",
    },
    "arid_canyon_scrub": {
        "biomes": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"],
        "block": "minecraft:dead_bush",
        "supports": ["minecraft:sand", "minecraft:red_sand", "minecraft:terracotta"],
        "count": 3, "tries": 16, "xz_spread": 4, "y_spread": 2,
        "heightmap": "MOTION_BLOCKING_NO_LEAVES",
    },
    "cool_forest_fern_pockets": {
        "biomes": ["minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                   "minecraft:windswept_forest"],
        "block": "minecraft:fern",
        "supports": ["minecraft:grass_block", "minecraft:dirt", "minecraft:podzol",
                     "minecraft:coarse_dirt"],
        "count": 3, "tries": 16, "xz_spread": 4, "y_spread": 2,
        "heightmap": "MOTION_BLOCKING_NO_LEAVES",
    },
}

def resources():
    """Return all ZIP-relative resource paths and JSON objects, without side effects."""
    data = {
        "pack.mcmeta": {
            "pack": {
                "pack_format": PACK_FORMAT,
                "description": "RealScale opt-in vanilla-block microhabitats (NeoForge 1.21.1); not new biome IDs"
            }
        }
    }
    for name, p in PROFILES.items():
        key = f"{NAMESPACE}:{name}"
        prefix = f"data/{NAMESPACE}"
        data[f"{prefix}/worldgen/configured_feature/{name}.json"] = {
            "type": "minecraft:random_patch",
            "config": {
                "tries": p["tries"], "xz_spread": p["xz_spread"], "y_spread": p["y_spread"],
                "feature": {
                    "feature": {
                        "type": "minecraft:simple_block",
                        "config": {
                            "to_place": {
                                "type": "minecraft:simple_state_provider",
                                "state": {"Name": p["block"]}
                            }
                        }
                    },
                    "placement": [{
                        "type": "minecraft:block_predicate_filter",
                        "predicate": {
                            "type": "minecraft:all_of",
                            "predicates": [
                                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                                {"type": "minecraft:matching_blocks",
                                 "blocks": p["supports"], "offset": [0, -1, 0]}
                            ]
                        }
                    }]
                }
            }
        }
        data[f"{prefix}/worldgen/placed_feature/{name}.json"] = {
            "feature": key,
            "placement": [
                {"type": "minecraft:count", "count": p["count"]},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": p["heightmap"]},
                {"type": "minecraft:biome"}
            ]
        }
        data[f"{prefix}/neoforge/biome_modifier/{name}.json"] = {
            "type": "neoforge:add_features",
            "biomes": p["biomes"],
            "features": key,
            "step": "vegetal_decoration"
        }
    return data

def validate(data):
    assert PACK_FORMAT == 48
    assert len(data) == 1 + len(PROFILES) * 3
    for name, p in PROFILES.items():
        prefix = f"data/{NAMESPACE}"
        feature = data[f"{prefix}/worldgen/configured_feature/{name}.json"]
        placed = data[f"{prefix}/worldgen/placed_feature/{name}.json"]
        modifier = data[f"{prefix}/neoforge/biome_modifier/{name}.json"]
        assert placed["feature"] == modifier["features"] == f"{NAMESPACE}:{name}"
        assert feature["type"] == "minecraft:random_patch"
        assert feature["config"]["feature"]["feature"]["type"] == "minecraft:simple_block"
        assert feature["config"]["feature"]["placement"][0]["predicate"]["predicates"][1]["blocks"] == p["supports"]
        assert p["block"] in {"minecraft:moss_carpet", "minecraft:dead_bush", "minecraft:fern"}
        assert all(s.startswith("minecraft:") for s in p["supports"] + p["biomes"])
        assert modifier["step"] == "vegetal_decoration"
        assert all(item["type"].startswith("minecraft:") for item in placed["placement"])
        assert "minecraft:biome" == placed["placement"][-1]["type"]
        assert p["count"] <= 3 and p["tries"] <= 20
    for filename, obj in data.items():
        assert not filename.startswith("/") and ".." not in Path(filename).parts
        json.loads(json.dumps(obj))

def build(output: Path):
    data = resources()
    validate(data)
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, "w", compression=ZIP_DEFLATED) as z:
        for filename, obj in sorted(data.items()):
            z.writestr(filename, json.dumps(obj, indent=2, ensure_ascii=False) + "\n")
    print("EXPERIMENTAL_DATAPACK:", output)
    print("PATCH_TYPES:", ", ".join(PROFILES))
    print("Only new chunks after explicit datapack activation are affected.")
    return output

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path,
                        default=Path("build/RealScale-Vanilla-Ecology-Lab-EXPERIMENTAL.zip"))
    args = parser.parse_args()
    build(args.output)
