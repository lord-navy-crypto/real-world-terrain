#!/usr/bin/env python3
"""Static safety and datapack regression for opt-in ecological structures."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
spec = importlib.util.spec_from_file_location(
    "habitats", ROOT / "scripts/make_vanilla_habitat_structures_pack.py")
habitats = importlib.util.module_from_spec(spec)
spec.loader.exec_module(habitats)


class VanillaHabitatTests(unittest.TestCase):
    def test_opt_in_namespaced_pack_without_worldgen_or_biome_replacement(self):
        data = habitats.resources()
        habitats.validate(data)
        self.assertEqual(len(habitats.STRUCTURES), 2)
        self.assertEqual(len(data), 7)
        self.assertFalse(any("/worldgen/biome/" in p or "/noise_settings/" in p
                             or "/world_preset/" in p or "/structure_set/" in p
                             for p in data))
        for name, profile in habitats.STRUCTURES.items():
            prefix = f"data/{habitats.NS}"
            configured = data[f"{prefix}/worldgen/configured_feature/{name}.json"]
            modifier = data[f"{prefix}/neoforge/biome_modifier/{name}.json"]
            self.assertEqual(configured["type"], profile["type"])
            self.assertTrue(all(x.startswith("minecraft:") for x in modifier["biomes"]))

    def test_only_safe_vanilla_blocks_and_immutable_density(self):
        java = (SRC / "VanillaHabitatFeature.java").read_text()
        registration = (SRC / "RealScaleMod.java").read_text()
        self.assertIn('id.getNamespace().equals("realscale")', java)
        self.assertIn("new ChunkPos(context.origin())", java)
        self.assertIn("if (!inside(chunk, log)", java)
        self.assertIn("if (!inside(chunk, plant)", java)
        self.assertIn("candidate.canSurvive(level, plant)", java)
        self.assertIn("carpet.canSurvive(level, top)", java)
        self.assertIn("private static boolean waterside", java)
        self.assertNotIn("Blocks.WATER.defaultBlockState()", java)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()", java)
        self.assertNotIn("level.setBlock(ground", java)
        self.assertNotIn("level.setBlock(surface", java)
        self.assertIn('"fallen_timber"', registration)
        self.assertIn('"riparian_reeds"', registration)

    def test_pack_is_valid_zip_and_not_automatically_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "test.zip"
            habitats.build(out)
            with ZipFile(out) as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(set(archive.namelist()), set(habitats.resources()))
                for name, value in habitats.resources().items():
                    self.assertEqual(json.loads(archive.read(name)), value)
        self.assertFalse(any("habitat_structures" in p.name for p in
                             (ROOT / "neoforge/src/main/resources/data/realscale/neoforge/biome_modifier").glob("*.json")))


if __name__ == "__main__":
    unittest.main()
