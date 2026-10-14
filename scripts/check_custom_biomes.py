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
        assert isinstance(data["carvers"], dict), "Minecraft 1.21.1 biome carvers must be an object: " + stem

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

def test_natural_ecotone_undergrowth():
    seen = set()
    for modifier_path in MODIFIERS.glob("ecotone_*_undergrowth.json"):
        d = load(modifier_path)
        assert d["type"] == "neoforge:add_features"
        assert d["step"] == "vegetal_decoration"
        assert (PLACED / (d["features"].split(":", 1)[1] + ".json")).exists()
        for biome in d["biomes"]:
            assert biome.startswith("realscale:")
            assert biome not in seen, "Ecotone has duplicated vegetation modifier: " + biome
            seen.add(biome)
    catalog = load(Path(__file__).resolve().parents[1] / "neoforge/ecotones/catalog.json")
    assert seen == {"realscale:" + entry["id"] for entry in catalog["entries"]}

def test_natural_ecotone_preset():
    natural = load(PRESETS / "realism_max_ecotones.json")
    original = load(PRESETS / "realism_max.json")
    generator = natural["dimensions"]["minecraft:overworld"]["generator"]
    baseline = original["dimensions"]["minecraft:overworld"]["generator"]
    assert generator["settings"] == baseline["settings"]
    assert generator["biome_source"]["type"] == "realscale:natural_ecotones"
    assert generator["biome_source"]["source"] == baseline["biome_source"]
    assert load(PRESETS / "realism_max.json") == original
    tag = load(ROOT.parent / "minecraft/tags/worldgen/world_preset/normal.json")
    assert "realscale:realism_max_ecotones" in tag["values"]

def test_skylands_and_habitats():
    sky = load(PRESETS / "realism_max_ecotones_skylands.json")
    prior = load(PRESETS / "realism_max_ecotones.json")
    wrapped = sky["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert wrapped["type"] == "realscale:sky_ocean"
    assert wrapped["source"] == prior["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert sky["dimensions"]["minecraft:overworld"]["generator"]["settings"] == "realscale:realism_max"
    normal = load(ROOT.parent / "minecraft/tags/worldgen/world_preset/normal.json")
    assert "realscale:realism_max_ecotones_skylands" in normal["values"]
    assert (BIOMES / "sky_blossom_isles.json").exists()
    for name in ("sky_blossom_island", "cave_moss_nooks", "cave_stalagmites",
                 "ocean_seagrass_patches", "ocean_sediment_stones"):
        configured = load(FEATURES / (name+".json"))
        assert configured == {"type": "realscale:"+name, "config": {}}
        placed = load(PLACED / (name+".json"))
        assert placed["feature"] == "realscale:"+name
        if name.startswith("ocean_"):
            assert not (MODIFIERS / (name+".json")).exists(), (
                "Vanilla ocean biome injection must be disabled: "+name)
        else:
            modifier = load(MODIFIERS / (name+".json"))
            assert modifier["type"] == "neoforge:add_features"
            assert modifier["features"] == "realscale:"+name
    # Avoid silently suppressing ocean structures or changing the seabed.
    # No RealScale modifiers attach additional features to vanilla ocean biomes.
    for modifier_path in MODIFIERS.glob("*.json"):
        modifier = load(modifier_path)
        for biome in modifier.get("biomes", []):
            if biome.startswith("minecraft:") and "ocean" in biome:
                raise AssertionError("Vanilla ocean modification: "+str(modifier_path))
    sky_mod = load(MODIFIERS/"sky_blossom_island.json")
    assert sky_mod["biomes"] == ["realscale:sky_blossom_isles"]
    assert load(PLACED/"sky_blossom_island.json")["placement"] == []
    java = (Path(__file__).resolve().parents[1] /
        "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
    for name in ("sky_ocean", "sky_blossom_island", "cave_moss_nooks", "cave_stalagmites",
                 "ocean_seagrass_patches", "ocean_sediment_stones"):
        assert '"'+name+'"' in java

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

def test_supported_microstructures():
    java = (Path(__file__).resolve().parents[1]
            / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
    features = ["fallen_oak_log", "fallen_spruce_log", "woodland_hummock", "upland_scree"]
    for name in features:
        assert ('"' + name + '"') in java, "Missing Java Feature registration: " + name
        feature = load(FEATURES / (name + ".json"))
        assert feature["type"] == "realscale:" + name, name
        assert feature["config"] == {}, name
        placed = load(PLACED / (name + ".json"))
        assert placed["feature"] == "realscale:" + name, name
        assert placed["placement"][-1]["type"] == "minecraft:biome", name
        assert 4 <= placed["placement"][0]["chance"] <= 20, name
        assert placed["placement"][2]["heightmap"] == "MOTION_BLOCKING_NO_LEAVES", name
        modifier = load(MODIFIERS / (name + ".json"))
        assert modifier["type"] == "neoforge:add_features", name
        assert len(modifier["biomes"]) >= 3, name

def test_all_custom_biome_carvers():
    # Minecraft 1.21.1 expects a carving-stage map, not the later-version list.
    all_biomes = list(BIOMES.glob("*.json"))
    assert len(all_biomes) == 47, len(all_biomes)
    for path in all_biomes:
        biome = load(path)
        assert isinstance(biome.get("carvers"), dict), path
        assert isinstance(biome["carvers"].get("air"), list), path

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
    test_skylands_and_habitats()
    test_natural_ecotone_preset()
    test_natural_ecotone_undergrowth()
    test_ecotone_catalog()
    test_ecotone_gallery()
    test_all_custom_biome_carvers()
    test_supported_microstructures()
    print("STATIC PASS: 40 ecotone specifications, 10 priority biomes, referenced features and opt-in biome lab.")
    print("RUNTIME NOT TESTED: Minecraft biome codec, server loading, cave/river geometry.")
