#!/usr/bin/env python3
"""Static regression for opt-in Nether/End generator identity and cave QA wiring.

Server-world and Anvil-block evidence are separately required: this file
does not assert that any particular random world already generated correctly.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources"
GEO=RES/"data/realscale/worldgen"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class FantasyDimensionWiring(unittest.TestCase):
    def test_independent_generated_dimension_noise_settings(self):
        nether=json.loads((GEO/"noise_settings/fantasy_nether_v2.json").read_text())
        end=json.loads((GEO/"noise_settings/fantasy_end_v2.json").read_text())
        self.assertEqual(nether["noise"]["height"],256)
        self.assertEqual(nether["noise"]["min_y"],0)
        self.assertEqual(nether["default_fluid"]["Name"],"minecraft:lava")
        self.assertEqual(nether["sea_level"],32)
        self.assertEqual(nether["noise_router"]["final_density"]["type"],
                         "realscale:fantasy_nether_caverns")
        self.assertEqual(end["noise_router"]["final_density"]["type"],
                         "realscale:fantasy_end_archipelago")
        self.assertEqual(end["default_block"]["Name"],"minecraft:end_stone")
        self.assertEqual(end["noise"]["height"],256)
        self.assertEqual(end["default_fluid"]["Name"],"minecraft:air")

    def test_vanilla_end_dragon_center_and_nether_roof_guards_exist(self):
        end=(SOURCE/"FantasyEndArchipelagoDensity.java").read_text()
        nether=(SOURCE/"FantasyNetherCavernsDensity.java").read_text()
        self.assertIn("distance <= 1152.0",end)
        self.assertIn("smoothstep(1152.0, 1920.0",end)
        self.assertIn("y < 30 || y > 246",nether)
        self.assertIn("smoothstep(222, 246",nether)

    def test_real_rcon_test_checks_saved_dimension_settings(self):
        script=(ROOT/"qa/neoforge/rcon_worldgen_smoke.py").read_text()
        self.assertIn('elif profile=="fantasy"',script)
        self.assertIn('"minecraft:the_nether":"realscale:fantasy_nether_v2"',script)
        self.assertIn('"minecraft:the_end":"realscale:fantasy_end_v2"',script)
        self.assertIn('dimensions.get(dimension,{}).get("generator",{}).get("settings")',script)
        self.assertIn('rcon.send("save-all flush")',script)
        workflow=(ROOT/".github/workflows/fantasy-dimensions-server.yml").read_text()
        self.assertIn('rcon_worldgen_smoke.py . "$LAB_WORLD" fantasy',workflow)
        self.assertIn("audit_fantasy_dimensions.py .",workflow)
        self.assertIn("enable-rcon=true",workflow)

    def test_auditors_do_not_mislabel_unsampled_dimensions_as_verified(self):
        script=(ROOT/"qa/neoforge/audit_fantasy_dimensions.py").read_text()
        self.assertIn('outer_end_solid_samples',script)
        self.assertIn('invalid_palette_indices',script)
        self.assertIn('central_end_chunks',script)
        self.assertIn('outer_chunks_beyond_1920_blocks',script)
        cave=(ROOT/"qa/neoforge/check_realism_continuity.py").read_text()
        self.assertIn('"high_mountain_subsurface"',cave)
        self.assertIn('mountain_air_voxels',cave)

if __name__=="__main__":
    unittest.main()
