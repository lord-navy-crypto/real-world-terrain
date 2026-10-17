#!/usr/bin/env python3
"""TVA heightmap decode geometry must use dimension build height, not a
shorter noise vertical range. Independent of headless Minecraft samples.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data/realscale"

class TVAHeightmapDecoding(unittest.TestCase):
    def test_all_four_decode_with_real_dimension_bits(self):
        for family in ("tva_sacred_timeline_three_realms",
                       "tva_alpine_three_realms",
                       "tva_canyon_three_realms",
                       "tva_measured_three_realms"):
            with self.subTest(family=family):
                preset=json.loads((ROOT/"worldgen/world_preset"/(family+".json")).read_text())
                overworld=preset["dimensions"]["minecraft:overworld"]
                dim=json.loads((ROOT/"dimension_type"/(
                    overworld["type"].split(":",1)[1]+".json")).read_text())
                noise_id=overworld["generator"]["settings"].split(":",1)[1]
                gen=json.loads((ROOT/"worldgen/noise_settings"/(noise_id+".json")).read_text())
                self.assertEqual((dim["min_y"],dim["height"]),(-64,2096))
                self.assertLessEqual(gen["noise"]["min_y"]+gen["noise"]["height"],
                                     dim["min_y"]+dim["height"])
                self.assertGreaterEqual(gen["noise"]["min_y"],dim["min_y"])
                bits=(dim["height"]+1).bit_length()
                self.assertEqual(bits,12)
                if family=="tva_canyon_three_realms":
                    self.assertEqual(gen["noise"]["height"],1024)
                    self.assertEqual((gen["noise"]["height"]+1).bit_length(),11)
    def test_checkerboard_decoder_guard_present(self):
        code=(Path(__file__).resolve().parent/"neoforge/check_realism_continuity.py").read_text()
        self.assertIn("world_height=dimension[\"height\"]",code)
        self.assertIn('heightmap_decode_basis',code)

if __name__=="__main__":
    unittest.main()
