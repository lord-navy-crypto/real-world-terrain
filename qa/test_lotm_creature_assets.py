"""Validate original, complete GeckoLib assets for all ten independent LOTM creatures."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts/generate_lotm_visuals.py"
CREATURE_IDS = (
    "starlit_squid",
    "nightmare_eye",
    "night_owl",
    "black_spotted_frog",
    "shroudman",
    "hornacis_goat",
    "ironarm_beast",
    "four_eyed_turtle",
    "crimson_howler",
    "death_crow",
)


def load_generator():
    spec = importlib.util.spec_from_file_location("lotm_visuals", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class LotmCreatureAssetTests(unittest.TestCase):
    def test_generator_declares_the_complete_active_creature_roster(self):
        module = load_generator()
        self.assertEqual(CREATURE_IDS, tuple(module.CREATURES.keys()))

    def test_generator_emits_unique_64px_textures_models_and_animations(self):
        module = load_generator()
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            module.generate(output)
            asset_root = output / "assets" / "realscale"
            texture_hashes = set()

            for creature_id in CREATURE_IDS:
                with self.subTest(creature=creature_id):
                    texture_path = asset_root / "textures" / "entity" / f"lotm_{creature_id}.png"
                    geometry_path = asset_root / "geo" / f"lotm_{creature_id}.geo.json"
                    animation_path = asset_root / "animations" / f"lotm_{creature_id}.animation.json"

                    self.assertTrue(texture_path.is_file(), f"missing texture for {creature_id}")
                    self.assertTrue(geometry_path.is_file(), f"missing geometry for {creature_id}")
                    self.assertTrue(animation_path.is_file(), f"missing animations for {creature_id}")

                    texture = texture_path.read_bytes()
                    self.assertTrue(texture.startswith(bytes((137, 80, 78, 71, 13, 10, 26, 10))))
                    self.assertEqual((64, 64), struct.unpack(">II", texture[16:24]))
                    digest = hashlib.sha256(texture).hexdigest()
                    self.assertNotIn(digest, texture_hashes, "creature textures must be distinct")
                    texture_hashes.add(digest)

                    geometry = json.loads(geometry_path.read_text(encoding="utf-8"))
                    self.assertIn("minecraft:geometry", geometry)
                    bones = geometry["minecraft:geometry"][0]["bones"]
                    bone_names = {bone["name"] for bone in bones}
                    self.assertIn("root", bone_names)
                    self.assertGreaterEqual(len(bone_names), 5)
                    self.assertEqual(len(bone_names), len(bones), "bone names must be unique")

                    animations = json.loads(animation_path.read_text(encoding="utf-8"))["animations"]
                    for action in ("idle", "walk", "attack", "hurt", "death"):
                        animation_name = f"animation.{creature_id}.{action}"
                        self.assertIn(animation_name, animations)
                        animation_bones = set(animations[animation_name].get("bones", {}))
                        self.assertTrue(animation_bones.issubset(bone_names),
                                        f"{animation_name} references an unknown bone")

    def test_creature_geometry_has_species_specific_identifiers(self):
        module = load_generator()
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            module.generate(output)
            geo_root = output / "assets" / "realscale" / "geo"
            identifiers = set()
            for creature_id in CREATURE_IDS:
                data = json.loads((geo_root / f"lotm_{creature_id}.geo.json").read_text(encoding="utf-8"))
                identifiers.add(data["minecraft:geometry"][0]["description"]["identifier"])
            self.assertEqual(len(CREATURE_IDS), len(identifiers))
            self.assertEqual(
                {f"geometry.realscale.lotm_{creature_id}" for creature_id in CREATURE_IDS},
                identifiers,
            )


if __name__ == "__main__":
    unittest.main()
