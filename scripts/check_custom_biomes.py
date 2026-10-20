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

def test_ecotone_feature_order_isolation():
    """The same PlacedFeature holder in conflicting relative order causes a
    FeatureSorter cycle, e.g. swamp vs seasonal_floodforest. Alias the placed
    features, not the configured blocks, for ecotone-only decoration.
    """
    groups = {
        "arid": "dry_scrub_patch",
        "coastal": "meadow_wildflower_patch",
        "forest": "birch_understory_ferns",
        "highlands": "stony_highland_sparse_grass",
        "wetland": "wetland_moss_patch",
    }
    for group, original_name in groups.items():
        modifier = load(MODIFIERS / ("ecotone_" + group + "_undergrowth.json"))
        custom_id = "ecotone_" + group + "_undergrowth"
        assert modifier["features"] == "realscale:" + custom_id
        assert all(b.startswith("realscale:") for b in modifier["biomes"])
        clone = load(PLACED / (custom_id + ".json"))
        original = load(PLACED / (original_name + ".json"))
        assert clone == original, "Ecotone features must preserve placement: " + group
        assert clone["feature"].startswith("realscale:")
    vanilla_features = set()
    for path in MODIFIERS.glob("*.json"):
        d = load(path)
        if d.get("type") != "neoforge:add_features":
            continue
        if any(b.startswith("minecraft:") for b in d["biomes"]):
            fs = d["features"] if isinstance(d["features"], list) else [d["features"]]
            vanilla_features.update(fs)
    for group in groups:
        assert ("realscale:ecotone_" + group + "_undergrowth") not in vanilla_features

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
    assert (PRESETS / "realism_max_ecotones.json").is_file()  # historical preset remains available
    assert "realscale:tva_sacred_biodiversity_end3_spawn3_v3" in tag["values"]\n    assert len(tag["values"]) == 4

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
    # Preserve the historical 67-biome catalog, plus three End and exactly
    # six additive versioned v2 ecotones. Do not silently lose or add biomes.
    end_extras = {"end_chorus_gardens", "end_crystal_barrens", "end_shattered_glades"}
    v2_extras = {"tropical_dry_woodland", "cool_temperate_rainforest",
                 "alpine_sedge_fen", "polar_fellfield",
                 "arid_riparian_woodland", "windy_coastal_headland"}
    v3_extras = {"mediterranean_chaparral", "temperate_tallgrass_prairie",
                 "monsoon_woodland", "montane_cloud_scrub",
                 "brackish_tidal_marsh", "coastal_pine_barrens",
                 "frost_heath_moor", "arid_rocksteppe"}
    names = {p.stem for p in all_biomes}
    assert end_extras <= names, ("Missing End biomes", end_extras - names)
    assert v2_extras <= names, ("Missing v2 biomes", v2_extras - names)
    assert v3_extras <= names, ("Missing v3 biomes", v3_extras - names)
    rare_earth = {"geothermal_sulfur_basin", "socotra_dragonblood_woodland",
                  "white_dune_lagoon", "sundarbans_tidal_mangrove",
                  "congo_peat_swamp_forest", "steppe_saline_lake",
                  "acidic_geothermal_mudflat", "montane_mist_forest",
                  "permafrost_fen", "limestone_island_scrub",
                  "namib_fog_desert", "serpentine_barrens", "socotra_endemic_scrub"}
    assert rare_earth <= names, ("Missing rare Earth biomes", rare_earth - names)
    assert len(names - end_extras - v2_extras - v3_extras - rare_earth) == 67, len(names)
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
    assert len(entries) == 60
    ids = [entry["id"] for entry in entries]
    assert len(set(ids)) == 60
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

def test_grand_dimensions():
    """Dimensions use full-size noise, not only taller empty dimension types."""
    preset = load(PRESETS / "realism_max_three_realms.json")
    old = load(PRESETS / "realism_max.json")
    assert preset["dimensions"]["minecraft:overworld"] == old["dimensions"]["minecraft:overworld"]
    for dimension, type_id, settings, sea in (
        ("minecraft:the_nether", "realscale:grand_nether","grand_nether",32),
        ("minecraft:the_end", "realscale:grand_end","grand_end",0)):
        d = preset["dimensions"][dimension]
        prior = old["dimensions"][dimension]
        assert d["type"] == type_id
        assert d["generator"]["type"] == "minecraft:noise"
        assert d["generator"]["settings"] == "realscale:"+settings
        assert d["generator"]["biome_source"] == prior["generator"]["biome_source"]
        dim_type = load(ROOT / "dimension_type" / (settings+".json"))
        noise = load(ROOT / "worldgen/noise_settings" / (settings+".json"))
        assert dim_type["min_y"] == noise["noise"]["min_y"] == 0
        assert dim_type["height"] == dim_type["logical_height"] == noise["noise"]["height"] == 512
        assert noise["sea_level"] == sea
    nether = load(ROOT / "worldgen/noise_settings/grand_nether.json")
    assert nether["noise_router"]["final_density"]["type"] == "realscale:grand_nether_caverns"
    assert nether["noise_router"]["final_density"]["base"]["type"] == "minecraft:squeeze"
    assert nether["noise_router"]["final_density"]["warp"]["type"] == "minecraft:noise"
    assert nether["noise_router"]["final_density"]["warp"]["noise"] == "minecraft:continentalness"
    assert '"grand_nether_caverns"' in (ROOT.parents[2] /
        "java/org/realscale/neoforge/RealScaleMod.java").read_text()
    assert nether["default_fluid"]["Name"] == "minecraft:lava"
    assert nether["default_block"]["Name"] == "minecraft:netherrack"
    assert nether["surface_rule"]["sequence"][0]["if_true"]["random_name"] == "minecraft:bedrock_floor"
    assert nether["surface_rule"]["sequence"][1]["if_true"]["invert"]["random_name"] == "minecraft:bedrock_roof"
    def gradients(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:y_clamped_gradient":
                yield (node["from_y"], node["to_y"])
            for value in node.values():
                yield from gradients(value)
        elif isinstance(node, list):
            for value in node:
                yield from gradients(value)
    assert (448,496) in list(gradients(nether["noise_router"]["final_density"]))
    assert load(ROOT/"dimension_type/grand_nether.json")["coordinate_scale"] == 8
    end = load(ROOT/"worldgen/noise_settings/grand_end.json")
    assert end["default_block"]["Name"] == "minecraft:end_stone"
    assert end["noise_router"]["final_density"]["type"] == "realscale:grand_end_density"
    assert end["noise_router"]["initial_density_without_jaggedness"]["type"] == "realscale:grand_end_density"
    assert load(ROOT/"dimension_type/grand_end.json")["coordinate_scale"] == 1
    mod_java = (ROOT.parents[2] / "java/org/realscale/neoforge/RealScaleMod.java").read_text()
    assert '"grand_end_density"' in mod_java
    tag = load(ROOT.parent/"minecraft/tags/worldgen/world_preset/normal.json")
    assert "realscale:tva_sacred_timeline_three_realms" in tag["values"]
    assert (PRESETS/"realism_max_three_realms.json").exists()
    for file in ("realism_max.json","realism_max_ecotones.json"):
        assert load(PRESETS/file)["dimensions"]["minecraft:the_nether"]["generator"]["settings"] == "minecraft:nether"
        assert load(PRESETS/file)["dimensions"]["minecraft:the_end"]["generator"]["settings"] == "minecraft:end"

if __name__ == "__main__":
    test_biomes()
    test_grand_dimensions()
    test_modifier_references()
    test_biome_lab()
    test_natural_ecotone_preset()
    test_natural_ecotone_undergrowth()
    test_ecotone_feature_order_isolation()
    test_ecotone_catalog()
    test_ecotone_gallery()
    test_all_custom_biome_carvers()
    test_supported_microstructures()
    print("STATIC PASS: 40 ecotone specifications, 10 priority biomes, referenced features and opt-in biome lab.")
    print("RUNTIME NOT TESTED: Minecraft biome codec, server loading, cave/river geometry.")
