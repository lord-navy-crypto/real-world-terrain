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
    "tropical_rainforest": "TROPICAL_RAINFOREST",
    "temperate_seasonal_forest": "TEMPERATE_SEASONAL_FOREST",
    "boreal_taiga_floor": "BOREAL_TAIGA",
    "desert_outcrops": "DESERT_OUTCROPS",
    "arctic_tundra": "ARCTIC_TUNDRA",
    "freshwater_bed": "FRESHWATER_BED",
    "coastal_seagrass": "COASTAL_SEAGRASS",
    "coral_reef_floor": "CORAL_REEF_FLOOR",
    "estuary_shallows": "ESTUARY_SHALLOWS",
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
        aquatic = name in ("freshwater_bed", "coastal_seagrass",
                           "coral_reef_floor", "estuary_shallows")
        expected_heightmap = "OCEAN_FLOOR_WG" if aquatic else "MOTION_BLOCKING_NO_LEAVES"
        assert placed["placement"][2]["heightmap"] == expected_heightmap, name
        if not aquatic:
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
    assert "realscale:natural_three_realms" in tags["values"]
    assert (WORLD_PRESET / "grand_ecology_three_realms.json").is_file()
    assert (WORLD_PRESET / "realism_max_three_realms.json").is_file()
    gallery = read(WORLD_PRESET / "apes_gallery_three_realms.json")
    gallery_source = gallery["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert gallery_source["type"] == "minecraft:checkerboard"
    assert gallery_source["scale"] == 0
    assert len(gallery_source["biomes"]) == 16
    assert len(set(gallery_source["biomes"])) == 16
    assert set(gallery_source["biomes"]) == {
        "minecraft:jungle", "minecraft:old_growth_spruce_taiga", "minecraft:forest",
        "minecraft:taiga", "minecraft:plains", "minecraft:savanna",
        "minecraft:wooded_badlands", "minecraft:desert", "minecraft:snowy_plains",
        "minecraft:stony_peaks", "minecraft:river", "minecraft:swamp",
        "minecraft:mangrove_swamp", "minecraft:lukewarm_ocean",
        "minecraft:warm_ocean", "minecraft:deep_ocean",
    }
    for dim in ("minecraft:the_nether", "minecraft:the_end"):
        assert gallery["dimensions"][dim] == original["dimensions"][dim], dim
    assert gallery["dimensions"]["minecraft:overworld"]["generator"]["settings"] == (
        old_overworld["generator"]["settings"]
    )
    assert (WORLD_PRESET / "apes_gallery_three_realms.json").is_file()
    # Deliberate: surface features cannot modify the noise router, aquifers,
    # cave density, terrain geometry, ocean biomes or live river blocks.
    assert "setBlock" in feature_java
    assert "getFluidState" in feature_java
    assert "sameChunk" in feature_java
    print("Grand Ecology: 15 biome feature groups including all nine APES terrestrial categories, plus aquatic proxies: PASS")

if __name__ == "__main__":
    check()
