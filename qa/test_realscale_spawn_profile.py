#!/usr/bin/env python3
"""The unified RealScale preset deliberately uses vanilla spawn selection."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
PRESET = ROOT / "neoforge/src/main/resources/data/realscale/worldgen/world_preset/natural_automate_three_realms.json"


class VanillaSpawnSelectionTests(unittest.TestCase):
    def test_custom_spawn_search_is_not_registered(self):
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        self.assertNotIn("RealScaleSpawnPosition", mod)
        self.assertNotIn("CreateSpawnPosition", mod)

    def test_custom_spawn_search_implementation_is_removed(self):
        self.assertFalse((JAVA / "RealScaleSpawnPosition.java").exists())
        self.assertFalse((JAVA / "RealScaleSpawnMath.java").exists())

    def test_natural_automate_does_not_override_vanilla_spawn(self):
        preset = json.loads(PRESET.read_text(encoding="utf-8"))
        overworld = preset["dimensions"]["minecraft:overworld"]
        self.assertNotIn("spawn", overworld)
        self.assertNotIn("spawn_position", overworld)
        self.assertNotIn("spawn_override", overworld)


if __name__ == "__main__":
    unittest.main()
