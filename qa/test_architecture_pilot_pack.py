#!/usr/bin/env python3
"""License, conversion and built-in-pack tests for the RealScale architecture pilot."""
import gzip
import json
import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import convert_gdmc_architecture as converter

SOURCE = ROOT / "assets/architecture/gdmc-source"
GENERATED = ROOT / "neoforge/build/generated/realscale-architecture-templates/data/realscale/structures/architecture"
PACK = ROOT / "neoforge/build/generated/realscale-structure-packs/resourcepacks/realscale_structures_architecture_pilot"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleStructurePacks.java"
LICENSE = ROOT / "licenses/GDMC-APACHE-2.0.txt"
PROVENANCE = ROOT / "docs/architecture/ASSET_PROVENANCE.md"

EXPECTED = {
    "modern_house": ((12, 10, 12), "modern_house_1.schematic"),
    "glass_greenhouse": ((12, 7, 12), "greenhouse_2.schematic"),
    "city_library": ((24, 10, 20), "library.schematic"),
}


class ArchitecturePilotTests(unittest.TestCase):
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
        for _, source_name in EXPECTED.values():
            self.assertTrue((SOURCE / source_name).is_file(), source_name)

    def test_source_schematic_dimensions_match_the_listed_assets(self):
        for template, (expected_size, source_name) in EXPECTED.items():
            with self.subTest(template=template):
                root = converter.read_schematic(SOURCE / source_name)
                self.assertEqual(
                    (root["Width"], root["Height"], root["Length"]),
                    expected_size
                )

    def test_converted_templates_are_valid_1211_structure_nbt(self):
        self.assertTrue(GENERATED.is_dir(),
                        "Run Gradle processResources or build before this test")
        for template, (expected_size, _) in EXPECTED.items():
            with self.subTest(template=template):
                path = GENERATED / f"{template}.nbt"
                self.assertTrue(path.is_file(), path)
                root_name, root = converter.parse_nbt(gzip.decompress(path.read_bytes()))
                self.assertEqual(root_name, "")
                self.assertEqual(root["DataVersion"], 3955)
                self.assertEqual(tuple(root["size"]), expected_size)
                width, height, length = expected_size
                self.assertEqual(len(root["blocks"]), width * height * length)
                self.assertEqual(len(root["entities"]), 0)
                palette = root["palette"]
                self.assertTrue(any(p["Name"] == "minecraft:air" for p in palette))
                self.assertTrue(all(p["Name"].startswith("minecraft:") for p in palette))
                for block in root["blocks"]:
                    self.assertGreaterEqual(block["state"], 0)
                    self.assertLess(block["state"], len(palette))
                    x, y, z = block["pos"]
                    self.assertTrue(
                        0 <= x < width and 0 <= y < height and 0 <= z < length
                    )

    def test_optional_pack_has_biome_aware_single_piece_structures(self):
        self.assertTrue(PACK.is_dir(),
                        "Run Gradle processResources or build before this test")
        meta = json.loads((PACK / "pack.mcmeta").read_text(encoding="utf-8"))
        self.assertEqual(meta["pack"]["pack_format"], 48)
        for template, (size, _) in EXPECTED.items():
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
            self.assertEqual(structure["biomes"], f"#realscale:has_structure/{template}")
            self.assertEqual(structure["size"], 0)
            self.assertEqual(structure["project_start_to_heightmap"], "WORLD_SURFACE_WG")
            self.assertEqual(structure["start_pool"], f"realscale:architecture/{template}")
            self.assertEqual(
                pool["elements"][0]["element"]["location"],
                f"realscale:architecture/{template}"
            )
            self.assertEqual(structure_set["structures"][0]["structure"], f"realscale:{template}")
            self.assertGreater(
                structure_set["placement"]["spacing"],
                structure_set["placement"]["separation"]
            )
            self.assertFalse(biome_tag["replace"])
            self.assertTrue(biome_tag["values"])

    def test_registered_pack_is_optional_and_legacy_sources_are_not_in_jar(self):
        source = JAVA.read_text(encoding="utf-8")
        self.assertIn('"architecture_pilot"', source)
        self.assertIn('"RealScale | Architecture Structures (Pilot)", false)', source)
        self.assertIn("false, // Never mandatory", source)
        jars = list((ROOT / "neoforge/build/libs").glob("*.jar"))
        self.assertTrue(jars, "Build the NeoForge JAR before this test")
        jar = max(jars, key=lambda p: p.stat().st_mtime)
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            prefix = "resourcepacks/realscale_structures_architecture_pilot/"
            for template in EXPECTED:
                self.assertIn(
                    prefix + f"data/realscale/structures/architecture/{template}.nbt",
                    names
                )
            self.assertFalse(any(name.endswith(".schematic") for name in names))


if __name__ == "__main__":
    unittest.main()
