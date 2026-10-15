#!/usr/bin/env python3
"""Static checks for the opt-in Grand Ecology experiment, not visual proof."""
import json
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
DATA = BASE / "neoforge/src/main/resources/data/realscale"
WORLD_PRESET = DATA / "worldgen/world_preset"
NAMES = {
    "temperate_rainforest": "TEMPERATE_RAINFOREST",
    "savanna_canopy": "SAVANNA_CANOPY",
    "chaparral_scrub": "CHAPARRAL_SCRUB",
    "grassland_mosaic": "GRASSLAND_MOSAIC",
    "alpine_tundra": "ALPINE_TUNDRA",
    "floodplain_bank": "FLOODPLAIN_BANK",
}

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def check():
    java = (BASE / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
    feature_java = (BASE / "neoforge/src/main/java/org/realscale/neoforge/GrandEcologyFeature.java").read_text()
    for name, kind in NAMES.items():
        assert ('"' + name + '"') in java, name
        assert kind in feature_java, kind
        configured = read(DATA / "worldgen/configured_feature" / (name + ".json"))
        placed = read(DATA / "worldgen/placed_feature" / (name + ".json"))
        modifier = read(DATA / "neoforge/biome_modifier" / (name + ".json"))
        assert configured == {"type": "realscale:" + name, "config": {}}, name
        assert placed["feature"] == "realscale:" + name, name
        assert placed["placement"][-1]["type"] == "minecraft:biome", name
        assert modifier["type"] == "neoforge:add_features", name
        assert modifier["features"] == "realscale:" + name, name
        assert modifier["step"] == "vegetal_decoration", name
        assert modifier["biomes"], name
        assert not any("ocean" in b or b in ("minecraft:river", "minecraft:frozen_river")
                       for b in modifier["biomes"]), name
    original = read(WORLD_PRESET / "realism_max_three_realms.json")
    ecology = read(WORLD_PRESET / "grand_ecology_three_realms.json")
    for dim in ("minecraft:the_nether", "minecraft:the_end"):
        assert ecology["dimensions"][dim] == original["dimensions"][dim], dim
    old_overworld = original["dimensions"]["minecraft:overworld"]
    new_overworld = ecology["dimensions"]["minecraft:overworld"]
    assert new_overworld["type"] == old_overworld["type"]
    assert new_overworld["generator"]["settings"] == old_overworld["generator"]["settings"]
    assert new_overworld["generator"]["biome_source"] == {
        "type": "realscale:natural_ecotones",
        "source": old_overworld["generator"]["biome_source"]
    }
    tags = read(BASE / "neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
    assert "realscale:grand_ecology_three_realms" in tags["values"]
    assert "realscale:realism_max_three_realms" in tags["values"]
    # Deliberate: surface features cannot modify the noise router, aquifers,
    # cave density, terrain geometry, ocean biomes or live river blocks.
    assert "setBlock" in feature_java
    assert "getFluidState" in feature_java
    assert "sameChunk" in feature_java
    print("Grand Ecology static registry, 6 feature groups and protected dimension configuration: PASS")

if __name__ == "__main__":
    check()
