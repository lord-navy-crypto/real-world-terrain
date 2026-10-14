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
    assert len(all_biomes) == 54, len(all_biomes)
    for path in all_biomes:
        biome = load(path)
        assert isinstance(biome.get("carvers"), dict), path
        assert isinstance(biome["carvers"].get("air"), list), path

def test_vanilla_habitat_microfeatures():
    """Protect original water columns while enriching dry vanilla surfaces."""
    ids = ["forest_wildflower_glades","spruce_berry_understory","mixed_forest_fern_mosaic","savanna_seasonal_grasses","meadow_oxeye_pockets","wetland_fern_islands","highland_grassy_terraces","arid_scrub_mosaics","old_growth_moss_floors","jungle_understory_ferns"]
    assert len(set(ids)) == 10
    for stem in ids:
        configured = load(FEATURES / (stem + ".json"))
        placed = load(PLACED / (stem + ".json"))
        modifier = load(MODIFIERS / (stem + ".json"))
        assert configured["type"] == "minecraft:random_patch", stem
        config = configured["config"]
        assert 8 <= config["tries"] <= 32, stem
        feature = config["feature"]
        assert feature["feature"]["type"] == "minecraft:simple_block", stem
        pred = feature["placement"][0]["predicate"]
        assert pred["type"] == "minecraft:all_of", stem
        air, support = pred["predicates"]
        assert air["type"] == "minecraft:matching_blocks", stem
        assert air["blocks"] == ["minecraft:air"], stem
        assert support["type"] == "minecraft:matching_blocks", stem
        assert support["offset"] == [0, -1, 0], stem
        assert all(name.startswith("minecraft:") for name in support["blocks"]), stem
        assert placed["feature"] == "realscale:" + stem, stem
        assert 6 <= placed["placement"][0]["chance"] <= 20, stem
        assert placed["placement"][2] == {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, stem
        assert placed["placement"][-1]["type"] == "minecraft:biome", stem
        assert modifier["type"] == "neoforge:add_features", stem
        assert modifier["features"] == "realscale:" + stem, stem
        assert modifier["step"] == "vegetal_decoration", stem
        assert len(modifier["biomes"]) >= 2, stem
        assert all(x.startswith("minecraft:") and "ocean" not in x and "river" not in x
                   for x in modifier["biomes"]), stem


def test_surface_ecotone_habitats():
    """Active surface ecotones must retain vanilla ores, life and decoration."""
    mapping = load(Path(__file__).resolve().parents[1] / "neoforge/ecotones/habitat_donors.json")["donor_by_ecotone"]
    catalog = load(Path(__file__).resolve().parents[1] / "neoforge/ecotones/catalog.json")
    assert len(mapping) == 48
    assert set(mapping) == {entry["id"] for entry in catalog["entries"]}
    import re
    source = (Path(__file__).resolve().parents[1] / "neoforge/src/main/java/org/realscale/neoforge/NaturalEcotoneBiomeSource.java").read_text()
    rules = re.findall(r'rule\("([a-z_]+)"', source)
    assert len(rules) == len(set(rules)), "Duplicate natural biome rules"
    assert set(rules) == set(mapping), "Natural biome rule / donor mapping mismatch"
    assert "protectedWater(current)" in source, "Original river/ocean protection removed"
    for stem, donor in mapping.items():
        assert donor and donor.replace("_", "").isalnum(), (stem, donor)
        biome = load(BIOMES / (stem + ".json"))
        stages = biome["features"]
        assert len(stages) == 11, stem
        assert "minecraft:amethyst_geode" in stages[2], stem
        assert any(ref.startswith("minecraft:ore_") for ref in stages[6]), stem
        assert len(stages[9]) > 0, stem
        assert len(biome["spawners"].get("monster", [])) > 0, stem
        assert len(biome["spawners"].get("ambient", [])) > 0, stem
        assert "mood_sound" in biome["effects"], stem

def test_ecotone_gallery():
    gallery = load(PRESETS / "ecotone_gallery.json")
    source = gallery["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert source["type"] == "minecraft:checkerboard"
    assert len(source["biomes"]) == 48
    assert len(set(source["biomes"])) == 48
    for name in source["biomes"]:
        assert name.startswith("realscale:")
        assert (BIOMES / (name.split(":", 1)[1] + ".json")).exists()

def test_ecotone_catalog():
    catalog = load(Path(__file__).resolve().parents[1] / "neoforge/ecotones/catalog.json")
    entries = catalog["entries"]
    assert len(entries) == 48
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
    test_natural_ecotone_preset()
    test_natural_ecotone_undergrowth()
    test_surface_ecotone_habitats()
    test_vanilla_habitat_microfeatures()
    test_ecotone_catalog()
    test_ecotone_gallery()
    test_all_custom_biome_carvers()
    test_supported_microstructures()
    print("STATIC PASS: 48 ecotone specifications, 10 priority biomes, referenced features and opt-in biome lab.")
    print("RUNTIME NOT TESTED: Minecraft biome codec, server loading, cave/river geometry.")
