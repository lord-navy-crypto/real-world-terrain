#!/usr/bin/env python3
"""Static consistency checks only; this does not execute a Minecraft codec."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "neoforge/src/main/resources/data/realscale"
BIOMES = ROOT / "worldgen/biome"
PRESETS = ROOT / "worldgen/world_preset"
FEATURES = ROOT / "worldgen/configured_feature"
PLACED = ROOT / "worldgen/placed_feature"
MODIFIERS = ROOT / "neoforge/biome_modifier"

def load(file):
    with file.open(encoding="utf-8") as f:
        return json.load(f)

def test_biomes():
    expected = {"woodland_meadow", "semi_arid_scrubland", "subalpine_woodland",
        "damp_limestone_caverns", "deep_fungal_caverns", "dry_karst_caves",
        "underground_springs", "volcanic_caverns", "glacial_subsurface",
        "forest_steppe", "marshy_woodland", "rocky_alpine_meadow"}
    assert expected <= {p.stem for p in BIOMES.glob("*.json")}
    for stem in expected:
        data = load(BIOMES / (stem + ".json"))
        assert "effects" in data and "temperature" in data and "downfall" in data
        assert isinstance(data["features"], list) and len(data["features"]) == 11

def test_modifier_references():
    for f in MODIFIERS.glob("*.json"):
        d = load(f)
        if d.get("type") != "neoforge:add_features":
            continue
        ids = d["features"]
        if isinstance(ids, str):
            ids = [ids]
        for feature_id in ids:
            if feature_id.startswith("realscale:"):
                assert (PLACED / (feature_id.split(":", 1)[1] + ".json")).exists(), f
    for f in PLACED.glob("*.json"):
        d = load(f)
        ref = d.get("feature", "")
        if ref.startswith("realscale:"):
            assert (FEATURES / (ref.split(":", 1)[1] + ".json")).exists(), f

def test_biome_lab():
    d = load(PRESETS / "biome_lab.json")
    source = d["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert source["type"] == "minecraft:checkerboard"
    assert 1 <= source["scale"] <= 6
    assert len(source["biomes"]) == 12
    for biome in source["biomes"]:
        assert biome.startswith("realscale:")
        assert (BIOMES / (biome.split(":", 1)[1] + ".json")).exists()
    stable = load(PRESETS / "realism_max.json")
    assert stable["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["preset"] == "minecraft:overworld"

def test_ecotone_gallery():
    gallery = load(PRESETS / "ecotone_gallery.json")
    source = gallery["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert source["type"] == "minecraft:checkerboard"
    assert len(source["biomes"]) == 40
    assert len(set(source["biomes"])) == 40
    for name in source["biomes"]:
        assert name.startswith("realscale:")
        assert (BIOMES / (name.split(":", 1)[1] + ".json")).exists()

def test_ecotone_catalog():
    catalog = load(Path(__file__).resolve().parents[1] / "neoforge/ecotones/catalog.json")
    entries = catalog["entries"]
    assert len(entries) == 40
    ids = [entry["id"] for entry in entries]
    assert len(set(ids)) == 40
    priority = [entry for entry in entries if entry["phase"] == "priority-10"]
    assert len(priority) == 10
    for entry in entries:
        assert (BIOMES / (entry["id"] + ".json")).exists(), entry["id"]
    for entry in priority:
        assert (BIOMES / (entry["id"] + ".json")).exists(), entry["id"]
        assert 16 <= entry["target_width_blocks"] <= 64
    for entry in entries:
        assert entry["from"] != entry["to"]
        assert 8 <= entry["target_width_blocks"] <= 64

if __name__ == "__main__":
    test_biomes()
    test_modifier_references()
    test_biome_lab()
    test_ecotone_catalog()
    test_ecotone_gallery()
    print("STATIC PASS: 40 ecotone specifications, 10 priority biomes, referenced features and opt-in biome lab.")
    print("RUNTIME NOT TESTED: Minecraft biome codec, server loading, cave/river geometry.")
