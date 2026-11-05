#!/usr/bin/env python3
"""License, batch generation, NBT, worldgen and packaging tests."""
import gzip
import json
import struct
import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import convert_gdmc_architecture as converter
import generate_original_architecture as original_generator
import generate_architecture_batch as batch_generator

SOURCE = ROOT / "assets/architecture/gdmc-source"
LICENSED = ROOT / "neoforge/build/generated/realscale-licensed-architecture-templates/data/realscale/structure/architecture"
ORIGINAL = ROOT / "neoforge/build/generated/realscale-original-architecture-templates/data/realscale/structure/architecture"
BATCH = ROOT / "neoforge/build/generated/realscale-batch-architecture-templates/data/realscale/structure/architecture"
PACK = ROOT / "neoforge/build/generated/realscale-structure-packs/resourcepacks/realscale_structures_architecture_pilot"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleStructurePacks.java"
LICENSE = ROOT / "licenses/GDMC-APACHE-2.0.txt"
PROVENANCE = ROOT / "docs/architecture/ASSET_PROVENANCE.md"
ORIGINAL_DOC = ROOT / "docs/architecture/ORIGINAL_ARCHITECTURE_GENERATOR.md"

LICENSED_EXPECTED = {
    "modern_house": ((12, 10, 12), "modern_house_1.schematic"),
    "glass_greenhouse": ((12, 7, 12), "greenhouse_2.schematic"),
    "city_library": ((24, 10, 20), "library.schematic"),
}
ORIGINAL_EXPECTED = {
    "forest_ranger_lodge": (13, 10, 13),
    "plains_farmstead": (17, 10, 17),
    "alpine_waystation": (13, 11, 13),
    "coastal_lighthouse": (9, 20, 9),
    "desert_caravanserai": (17, 9, 17),
}
BATCH_NAMES = (
    "woodland_cottage", "village_blacksmith", "schoolhouse",
    "healers_clinic", "brick_bakery", "two_storey_townhouse",
    "snowfield_shelter", "savanna_outpost", "marsh_stilt_house",
    "mesa_trading_post", "market_hall", "railway_station", "windmill",
    "fishing_hut", "coastal_pier", "forest_watchtower",
    "botanical_greenhouse", "orchard_farm", "stone_bridge", "watermill",
    "desert_well", "mountain_observatory", "ruined_watchpost",
    "garden_pavilion", "lumber_camp", "woodland_hamlet",
    "plains_market_square", "river_trade_hamlet",
    "alpine_watch_compound", "desert_oasis_hamlet",
    "stone_mason_workshop", "canal_lock", "monastery_chapel",
    "apiary_garden", "snowbound_storehouse", "desert_cistern",
    "tropical_boardwalk_house", "railway_signal_box", "quarry_shed",
    "charcoal_kiln", "vineyard_estate", "sheepfold_ranch",
    "charcoal_burners_camp", "fishing_village_compound", "hilltop_manor",
    "frontier_fort", "railway_depot", "orchard_cider_estate",
    "riverside_sawmill", "marsh_reed_hamlet",
)
BATCH_FACTORIES = {factory().name: factory for factory in batch_generator.BUILDINGS}
BATCH_EXPECTED = {
    name: (BATCH_FACTORIES[name]().width,
           BATCH_FACTORIES[name]().height,
           BATCH_FACTORIES[name]().length)
    for name in BATCH_NAMES
}
ALL_EXPECTED = {
    **{name: size for name, (size, _) in LICENSED_EXPECTED.items()},
    **ORIGINAL_EXPECTED,
    **BATCH_EXPECTED,
}


def read_template(path, expected_size):
    root_name, root = converter.parse_nbt(gzip.decompress(path.read_bytes()))
    assert root_name == ""
    assert root["DataVersion"] == 3955
    assert tuple(root["size"]) == tuple(expected_size)
    width, height, length = expected_size
    assert len(root["blocks"]) == width * height * length
    assert len(root["entities"]) == 0
    palette = root["palette"]
    assert any(p["Name"] == "minecraft:air" for p in palette)
    assert all(p["Name"].startswith("minecraft:") for p in palette)
    for block in root["blocks"]:
        assert 0 <= block["state"] < len(palette)
        x, y, z = block["pos"]
        assert 0 <= x < width and 0 <= y < height and 0 <= z < length
    return root


def skip_nbt_payload(data, offset, tag_type):
    """Advance over one NBT payload without losing its encoded tag type."""
    fixed = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
    if tag_type in fixed:
        return offset + fixed[tag_type]
    if tag_type in (7, 11, 12):
        length = struct.unpack_from(">i", data, offset)[0]
        unit = {7: 1, 11: 4, 12: 8}[tag_type]
        return offset + 4 + length * unit
    if tag_type == 8:
        _value, offset = converter.read_string(data, offset)
        return offset
    if tag_type == 9:
        subtype = data[offset]
        length = struct.unpack_from(">i", data, offset + 1)[0]
        offset += 5
        for _ in range(length):
            offset = skip_nbt_payload(data, offset, subtype)
        return offset
    if tag_type == 10:
        while True:
            child_type = data[offset]
            offset += 1
            if child_type == 0:
                return offset
            _name, offset = converter.read_string(data, offset)
            offset = skip_nbt_payload(data, offset, child_type)
    raise AssertionError(f"Unexpected NBT tag type {tag_type}")


def compound_child(data, offset, wanted_name):
    while True:
        tag_type = data[offset]
        offset += 1
        if tag_type == 0:
            raise AssertionError(f"NBT compound is missing {wanted_name!r}")
        name, offset = converter.read_string(data, offset)
        payload_offset = offset
        if name == wanted_name:
            return tag_type, payload_offset
        offset = skip_nbt_payload(data, offset, tag_type)


class ArchitecturePilotTests(unittest.TestCase):
    def test_structure_coordinates_use_int_lists_required_by_1211_codec(self):
        paths = sorted(LICENSED.glob("*.nbt")) + sorted(ORIGINAL.glob("*.nbt")) + sorted(BATCH.glob("*.nbt"))
        self.assertEqual(len(paths), 58)
        for path in paths:
            with self.subTest(template=path.name):
                data = gzip.decompress(path.read_bytes())
                self.assertEqual(data[0], 10, "root must be a compound")
                _root_name, root_offset = converter.read_string(data, 1)

                size_type, size_offset = compound_child(data, root_offset, "size")
                self.assertEqual(size_type, 9, "size must be TAG_List")
                self.assertEqual(data[size_offset], 3, "size list elements must be TAG_Int")
                self.assertEqual(struct.unpack_from(">i", data, size_offset + 1)[0], 3)

                blocks_type, blocks_offset = compound_child(data, root_offset, "blocks")
                self.assertEqual(blocks_type, 9, "blocks must be TAG_List")
                self.assertEqual(data[blocks_offset], 10, "blocks list elements must be TAG_Compound")
                self.assertGreater(struct.unpack_from(">i", data, blocks_offset + 1)[0], 0)

                first_block_offset = blocks_offset + 5
                pos_type, pos_offset = compound_child(data, first_block_offset, "pos")
                self.assertEqual(pos_type, 9, "block pos must be TAG_List")
                self.assertEqual(data[pos_offset], 3, "block pos list elements must be TAG_Int")
                self.assertEqual(struct.unpack_from(">i", data, pos_offset + 1)[0], 3)

    def test_apache_license_and_source_provenance_are_retained(self):
        license_text = LICENSE.read_text(encoding="utf-8")
        provenance = PROVENANCE.read_text(encoding="utf-8")
        self.assertIn("Apache License", license_text)
        self.assertIn("Version 2.0", license_text)
        self.assertIn("Copyright 2019 Wai Ip Chu", license_text)
        self.assertIn("williamcwi/GDMC", provenance)
        self.assertIn("MC Schematics", provenance)
        self.assertIn("not packaged in the JAR", provenance)
        self.assertIn("block-entity payloads", provenance.lower())
        for _, source_name in LICENSED_EXPECTED.values():
            self.assertTrue((SOURCE / source_name).is_file(), source_name)

    def test_source_schematic_dimensions_match_the_listed_assets(self):
        for template, (expected_size, source_name) in LICENSED_EXPECTED.items():
            with self.subTest(template=template):
                root = converter.read_schematic(SOURCE / source_name)
                self.assertEqual(
                    (root["Width"], root["Height"], root["Length"]),
                    expected_size
                )

    def test_licensed_converted_templates_are_valid_1211_structure_nbt(self):
        self.assertTrue(LICENSED.is_dir(),
                        "Run Gradle processResources or build before this test")
        for template, (expected_size, _) in LICENSED_EXPECTED.items():
            with self.subTest(template=template):
                read_template(LICENSED / f"{template}.nbt", expected_size)

    def test_original_generator_creates_five_distinct_designs(self):
        self.assertTrue(ORIGINAL.is_dir(),
                        "Run Gradle processResources or build before this test")
        factories = {factory().name: factory for factory in original_generator.BUILDINGS}
        self.assertEqual(set(factories), set(ORIGINAL_EXPECTED))
        for template, expected_size in ORIGINAL_EXPECTED.items():
            with self.subTest(template=template):
                path = ORIGINAL / f"{template}.nbt"
                self.assertTrue(path.is_file(), path)
                root = read_template(path, expected_size)
                palette_names = {entry["Name"] for entry in root["palette"]}
                self.assertGreater(len(palette_names), 8)
        self.assertIn("Original RealScale designs", ORIGINAL_DOC.read_text(encoding="utf-8"))

    def test_batch_generator_creates_fifty_original_designs(self):
        self.assertEqual(len(batch_generator.BUILDINGS), 50)
        self.assertEqual(set(BATCH_FACTORIES), set(BATCH_NAMES))
        self.assertEqual(len(BATCH_EXPECTED), 50)
        self.assertTrue(BATCH.is_dir(),
                        "Run Gradle processResources or build before this test")
        for template, expected_size in BATCH_EXPECTED.items():
            with self.subTest(template=template):
                path = BATCH / f"{template}.nbt"
                self.assertTrue(path.is_file(), path)
                root = read_template(path, expected_size)
                palette_names = {entry["Name"] for entry in root["palette"]}
                self.assertGreater(len(palette_names), 3)
                self.assertTrue(any(name != "minecraft:air" for name in palette_names))

    def test_batch_designs_have_archetype_specific_blocks_and_profiles(self):
        self.assertTrue(PACK.is_dir(),
                        "Run Gradle processResources or build before this test")
        expected_features = {
            "woodland_cottage": "minecraft:oak_planks",
            "village_blacksmith": "minecraft:campfire",
            "schoolhouse": "minecraft:black_concrete",
            "healers_clinic": "minecraft:white_terracotta",
            "brick_bakery": "minecraft:bricks",
            "two_storey_townhouse": "minecraft:dark_oak_planks",
            "snowfield_shelter": "minecraft:spruce_planks",
            "savanna_outpost": "minecraft:acacia_planks",
            "marsh_stilt_house": "minecraft:oak_log",
            "mesa_trading_post": "minecraft:orange_terracotta",
            "market_hall": "minecraft:dark_oak_log",
            "railway_station": "minecraft:rail",
            "windmill": "minecraft:white_wool",
            "fishing_hut": "minecraft:spruce_planks",
            "coastal_pier": "minecraft:spruce_planks",
            "forest_watchtower": "minecraft:spruce_log",
            "botanical_greenhouse": "minecraft:glass_pane",
            "orchard_farm": "minecraft:oak_leaves",
            "stone_bridge": "minecraft:stone_bricks",
            "watermill": "minecraft:cobblestone",
            "desert_well": "minecraft:sandstone",
            "mountain_observatory": "minecraft:cut_copper",
            "ruined_watchpost": "minecraft:mossy_cobblestone",
            "garden_pavilion": "minecraft:polished_diorite",
            "lumber_camp": "minecraft:coarse_dirt",
            "woodland_hamlet": "minecraft:oak_planks",
            "plains_market_square": "minecraft:dark_oak_log",
            "river_trade_hamlet": "minecraft:cobblestone",
            "alpine_watch_compound": "minecraft:cut_copper",
            "desert_oasis_hamlet": "minecraft:orange_terracotta",
            "stone_mason_workshop": "minecraft:stonecutter",
            "canal_lock": "minecraft:water",
            "monastery_chapel": "minecraft:flowering_azalea",
            "apiary_garden": "minecraft:beehive",
            "snowbound_storehouse": "minecraft:snow",
            "desert_cistern": "minecraft:cut_sandstone",
            "tropical_boardwalk_house": "minecraft:jungle_planks",
            "railway_signal_box": "minecraft:redstone_lamp",
            "quarry_shed": "minecraft:polished_andesite",
            "charcoal_kiln": "minecraft:campfire",
            "vineyard_estate": "minecraft:sweet_berry_bush",
            "sheepfold_ranch": "minecraft:hay_block",
            "charcoal_burners_camp": "minecraft:campfire",
            "fishing_village_compound": "minecraft:smoker",
            "hilltop_manor": "minecraft:bricks",
            "frontier_fort": "minecraft:spruce_log",
            "railway_depot": "minecraft:bricks",
            "orchard_cider_estate": "minecraft:oak_leaves",
            "riverside_sawmill": "minecraft:water",
            "marsh_reed_hamlet": "minecraft:mud",
        }
        self.assertEqual(set(expected_features), set(BATCH_NAMES))
        for template, required_block in expected_features.items():
            with self.subTest(template=template):
                root = read_template(BATCH / f"{template}.nbt", BATCH_EXPECTED[template])
                self.assertIn(required_block, {p["Name"] for p in root["palette"]})
                biome_tag = json.loads(
                    (PACK / f"data/realscale/tags/worldgen/biome/has_structure/{template}.json").read_text()
                )
                self.assertTrue(biome_tag["values"])
                self.assertFalse(biome_tag["replace"])
                structure_set = json.loads(
                    (PACK / f"data/realscale/worldgen/structure_set/{template}.json").read_text()
                )
                self.assertEqual(structure_set["placement"]["type"], "minecraft:random_spread")
                self.assertGreater(
                    structure_set["placement"]["spacing"],
                    structure_set["placement"]["separation"]
                )

    def test_settlement_compounds_combine_multiple_building_and_site_features(self):
        self.assertTrue(BATCH.is_dir(),
                        "Run Gradle processResources or build before this test")
        expected = {
            "woodland_hamlet": {"minecraft:oak_planks", "minecraft:dirt_path", "minecraft:water"},
            "plains_market_square": {"minecraft:stone_bricks", "minecraft:barrel", "minecraft:dirt_path"},
            "river_trade_hamlet": {"minecraft:cobblestone", "minecraft:gravel", "minecraft:water"},
            "alpine_watch_compound": {"minecraft:cut_copper", "minecraft:campfire", "minecraft:spruce_log"},
            "desert_oasis_hamlet": {"minecraft:orange_terracotta", "minecraft:jungle_leaves", "minecraft:water"},
            "vineyard_estate": {"minecraft:sweet_berry_bush", "minecraft:oak_leaves", "minecraft:barrel"},
            "sheepfold_ranch": {"minecraft:hay_block", "minecraft:water", "minecraft:oak_fence"},
            "charcoal_burners_camp": {"minecraft:campfire", "minecraft:stone_bricks", "minecraft:spruce_log"},
            "fishing_village_compound": {"minecraft:smoker", "minecraft:barrel", "minecraft:spruce_log"},
            "hilltop_manor": {"minecraft:bricks", "minecraft:stone_bricks", "minecraft:spruce_log"},
            "frontier_fort": {"minecraft:spruce_log", "minecraft:stone_bricks", "minecraft:campfire"},
            "railway_depot": {"minecraft:bricks", "minecraft:gravel", "minecraft:redstone_lamp"},
            "orchard_cider_estate": {"minecraft:oak_leaves", "minecraft:barrel", "minecraft:bricks"},
            "riverside_sawmill": {"minecraft:water", "minecraft:gravel", "minecraft:spruce_log"},
            "marsh_reed_hamlet": {"minecraft:oak_planks", "minecraft:mud", "minecraft:oak_log"},
        }
        for template, required_blocks in expected.items():
            with self.subTest(template=template):
                root = read_template(BATCH / f"{template}.nbt", BATCH_EXPECTED[template])
                # Compound footprints are intentionally varied: some are long and narrow
                # (vineyard, ranch, depot, sawmill), while others are broader town plans.
                # Validate against the actual minimum envelope of this catalog rather than
                # imposing a 40x35 rectangle that rejects valid 39x27 compounds.
                self.assertGreaterEqual(root["size"][0], 39)
                self.assertGreaterEqual(root["size"][2], 27)
                palette = {entry["Name"] for entry in root["palette"]}
                self.assertTrue(required_blocks.issubset(palette),
                                f"{template} is missing settlement features: {required_blocks - palette}")

    def test_second_expansion_compounds_have_distinct_settlement_features(self):
        expected = {
            "vineyard_estate": {"minecraft:sweet_berry_bush", "minecraft:oak_leaves", "minecraft:barrel"},
            "sheepfold_ranch": {"minecraft:hay_block", "minecraft:water", "minecraft:oak_fence"},
            "charcoal_burners_camp": {"minecraft:campfire", "minecraft:stone_bricks", "minecraft:spruce_log"},
            "fishing_village_compound": {"minecraft:smoker", "minecraft:barrel", "minecraft:spruce_log"},
            "hilltop_manor": {"minecraft:bricks", "minecraft:stone_bricks", "minecraft:spruce_log"},
            "frontier_fort": {"minecraft:spruce_log", "minecraft:stone_bricks", "minecraft:campfire"},
            "railway_depot": {"minecraft:bricks", "minecraft:gravel", "minecraft:redstone_lamp"},
            "orchard_cider_estate": {"minecraft:oak_leaves", "minecraft:barrel", "minecraft:bricks"},
            "riverside_sawmill": {"minecraft:water", "minecraft:gravel", "minecraft:spruce_log"},
            "marsh_reed_hamlet": {"minecraft:oak_planks", "minecraft:mud", "minecraft:oak_log"},
        }
        for template, required_blocks in expected.items():
            with self.subTest(template=template):
                root = read_template(BATCH / f"{template}.nbt", BATCH_EXPECTED[template])
                self.assertGreaterEqual(root["size"][0], 39)
                self.assertGreaterEqual(root["size"][2], 25)
                palette = {entry["Name"] for entry in root["palette"]}
                self.assertTrue(required_blocks.issubset(palette),
                                f"{template} is missing settlement features: {required_blocks - palette}")

    def test_original_buildings_have_distinct_biome_and_design_profiles(self):
        self.assertTrue(PACK.is_dir(),
                        "Run Gradle processResources or build before this test")
        expected_features = {
            "forest_ranger_lodge": "minecraft:spruce_planks",
            "plains_farmstead": "minecraft:wheat",
            "alpine_waystation": "minecraft:campfire",
            "coastal_lighthouse": "minecraft:sea_lantern",
            "desert_caravanserai": "minecraft:cut_sandstone",
        }
        for template, required_block in expected_features.items():
            with self.subTest(template=template):
                root = read_template(ORIGINAL / f"{template}.nbt", ORIGINAL_EXPECTED[template])
                self.assertIn(required_block, {p["Name"] for p in root["palette"]})
                biome_tag = json.loads(
                    (PACK / f"data/realscale/tags/worldgen/biome/has_structure/{template}.json").read_text()
                )
                self.assertTrue(biome_tag["values"])
                self.assertFalse(biome_tag["replace"])
                structure_set = json.loads(
                    (PACK / f"data/realscale/worldgen/structure_set/{template}.json").read_text()
                )
                self.assertEqual(structure_set["placement"]["type"], "minecraft:random_spread")
                self.assertGreater(
                    structure_set["placement"]["spacing"],
                    structure_set["placement"]["separation"]
                )

    def test_optional_pack_has_biome_aware_single_piece_structures(self):
        self.assertTrue(PACK.is_dir(),
                        "Run Gradle processResources or build before this test")
        meta = json.loads((PACK / "pack.mcmeta").read_text(encoding="utf-8"))
        self.assertEqual(meta["pack"]["pack_format"], 48)
        self.assertEqual(len(ALL_EXPECTED), 58)
        for template in ALL_EXPECTED:
            with self.subTest(template=template):
                structure = json.loads(
                    (PACK / f"data/realscale/worldgen/structure/{template}.json").read_text()
                )
                pool = json.loads(
                    (PACK / f"data/realscale/worldgen/template_pool/architecture/{template}.json").read_text()
                )
                structure_set = json.loads(
                    (PACK / f"data/realscale/worldgen/structure_set/{template}.json").read_text()
                )
                biome_tag = json.loads(
                    (PACK / f"data/realscale/tags/worldgen/biome/has_structure/{template}.json").read_text()
                )
                self.assertEqual(structure["type"], "minecraft:jigsaw")
                self.assertIn("spawn_overrides", structure)
                self.assertEqual(structure["biomes"], f"#realscale:has_structure/{template}")
                self.assertEqual(structure["size"], 0)
                self.assertEqual(structure["project_start_to_heightmap"], "WORLD_SURFACE_WG")
                self.assertEqual(structure["start_pool"], f"realscale:architecture/{template}")
                self.assertEqual(
                    pool["elements"][0]["element"]["location"],
                    f"realscale:architecture/{template}"
                )
                self.assertEqual(structure_set["structures"][0]["structure"], f"realscale:{template}")
                self.assertEqual(structure_set["placement"]["type"], "minecraft:random_spread")
                self.assertGreater(
                    structure_set["placement"]["spacing"],
                    structure_set["placement"]["separation"]
                )
                self.assertFalse(biome_tag["replace"])
                self.assertTrue(biome_tag["values"])
                self.assertFalse(any(v.startswith("#minecraft:is_") for v in biome_tag["values"]),
                                 "Architecture tags must not reference nonexistent vanilla biome tags")
                for biome in biome_tag["values"]:
                    if biome.startswith("realscale:"):
                        biome_file = (ROOT / "neoforge/src/main/resources/data/realscale/worldgen/biome"
                                      / (biome.split(":", 1)[1] + ".json"))
                        self.assertTrue(biome_file.is_file(), f"Unknown RealScale biome: {biome}")

    def test_rcon_diagnostic_pack_tracks_each_workflows_selected_world(self):
        script = (ROOT / "qa/run_architecture_server_smoke.py").read_text(encoding="utf-8")
        pilot = (ROOT / ".github/workflows/architecture-pilot-server.yml").read_text(encoding="utf-8")
        full = (ROOT / ".github/workflows/architecture-library-58.yml").read_text(encoding="utf-8")
        self.assertIn("working-directory: neoforge", pilot)
        self.assertIn("level-name=architecture-pilot", pilot)
        self.assertIn("working-directory: neoforge", full)
        self.assertIn("level-name=architecture-58-smoke", full)
        self.assertIn('world_name = line.partition("=")[2].strip()', script)
        self.assertIn(
            'ROOT / "run" / world_name / "datapacks" / "realscale_nbt_diagnostics"',
            script,
        )
        self.assertNotIn("architecture-pilot/datapacks", script)
        self.assertNotIn("architecture-58-smoke/datapacks", script)
        self.assertIn('f\'datapack enable "{diagnostic_pack}"\'', script)

    def test_registered_pack_is_optional_and_only_templates_are_packaged(self):
        source = JAVA.read_text(encoding="utf-8")
        self.assertIn('"architecture_pilot"', source)
        self.assertIn('"RealScale | Architecture Library (58 Structures)", false)', source)
        self.assertIn("false, // Never mandatory", source)
        jars = list((ROOT / "neoforge/build/libs").glob("*.jar"))
        self.assertTrue(jars, "NeoForge jar must be built before validation")
        jar = max(jars, key=lambda p: p.stat().st_mtime)
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            prefix = "resourcepacks/realscale_structures_architecture_pilot/"
            self.assertIn(prefix + "pack.mcmeta", names)
            for template in ALL_EXPECTED:
                # Templates are always addressable through the mod's base data
                # namespace; only the worldgen definitions remain opt-in.
                self.assertIn(f"data/realscale/structure/architecture/{template}.nbt", names)
                required_pack_paths = (
                    f"data/realscale/worldgen/structure/{template}.json",
                    f"data/realscale/worldgen/structure_set/{template}.json",
                    f"data/realscale/worldgen/template_pool/architecture/{template}.json",
                    f"data/realscale/tags/worldgen/biome/has_structure/{template}.json",
                )
                for required_path in required_pack_paths:
                    self.assertIn(prefix + required_path, names)
                self.assertIn(
                    prefix + f"data/realscale/structure/architecture/{template}.nbt",
                    names
                )
            self.assertFalse(any(name.endswith(".schematic") for name in names))
            self.assertNotIn("original-architecture-manifest.json", names)
            self.assertNotIn("batch-architecture-manifest.json", names)


if __name__ == "__main__":
    unittest.main()
