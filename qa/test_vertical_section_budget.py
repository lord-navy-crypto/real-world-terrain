#!/usr/bin/env python3
"""RealScale tall-world palette inventory, not a client rendering test."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "neoforge"))
from vertical_section_budget import (
    count_chunk_sections, section_kind, summarize
)


def section(y, *states):
    return {"Y": y, "block_states": {
        "palette": [{"Name": name} for name in states]
    }}


class VerticalSectionBudgetTest(unittest.TestCase):
    def test_full_2096_dimension_contains_131_sections(self):
        result = count_chunk_sections({"sections": []}, min_y=-64, height=2096)
        self.assertEqual(result["section_slots"], 131)
        self.assertEqual(result["stored_in_range"], 0)
        self.assertEqual(result["not_explicitly_stored"], 131)
        self.assertEqual(result["sections"], {})

    def test_palette_metadata_is_not_assumed_invisible(self):
        chunk = {"sections": [
            section(-4, "minecraft:bedrock"),
            section(-3, "minecraft:stone"),
            section(8, "minecraft:stone", "minecraft:cave_air"),
            section(9, "minecraft:water"),
            section(12, "minecraft:air"),
            section(20, "minecraft:glass"),
            section(126, "minecraft:deepslate"),
        ]}
        result = count_chunk_sections(chunk)
        self.assertEqual(result["stored_in_range"], 7)
        self.assertEqual(result["kinds"]["uniform_rock_candidate"], 2)
        self.assertEqual(result["kinds"]["mixed_with_air"], 1)
        self.assertEqual(result["kinds"]["uniform_air"], 1)
        self.assertEqual(result["kinds"]["other_palette"], 3)
        self.assertEqual(result["not_explicitly_stored"], 124)
        # A cave/shaft is only a potential visibility path, not a render state.
        self.assertEqual(result["sections"][8], "mixed_with_air")
        self.assertEqual(result["sections"][126], "uniform_rock_candidate")

    def test_unknown_modded_and_transparent_never_marked_opaque(self):
        for item in ("minecraft:water", "minecraft:glass",
                     "example:modded_rock", "minecraft:lava"):
            self.assertEqual(section_kind(section(2, item)),
                             "other_palette")
        self.assertEqual(section_kind({"Y": 2}), "missing_palette")
        self.assertEqual(section_kind(section(2, "minecraft:stone",
                                               "minecraft:water")),
                         "other_palette")
        self.assertEqual(section_kind(section(2, "minecraft:cave_air")),
                         "uniform_air")

    def test_outside_dimension_and_duplicate_sections_are_explicit(self):
        result = count_chunk_sections({"sections": [
            section(-5, "minecraft:stone"),
            section(-4, "minecraft:stone"),
            section(-4, "minecraft:air"),
            section(127, "minecraft:air"),
        ]})
        self.assertEqual(result["stored_in_range"], 1)
        self.assertEqual(result["duplicate_y"], [-4])
        self.assertEqual(result["out_of_range_or_invalid_y"], [-5, 127])

    def test_bounded_sample_and_by_height_breakdown(self):
        chunks = [
            {"sections": [section(-4, "minecraft:stone"),
                          section(0, "minecraft:stone",
                                  "minecraft:cave_air")]},
            {"sections": [section(-4, "minecraft:stone"),
                          section(126, "minecraft:air")]},
            {"sections": [section(5, "minecraft:lava")]},
        ]
        report = summarize(iter(chunks), max_chunks=2)
        self.assertEqual(report["sampled_chunks"], 2)
        self.assertEqual(report["vertical_section_slots_per_chunk"], 131)
        self.assertEqual(report["total_addressable_section_slots"], 262)
        self.assertEqual(report["explicit_sections"], 4)
        self.assertEqual(report["absent_section_slots"], 258)
        self.assertEqual(report["palette_categories"]["uniform_rock_candidate"], 2)
        self.assertEqual(report["by_section_y"]["-4"]["uniform_rock_candidate"], 2)
        self.assertEqual(report["by_section_y"]["126"]["uniform_air"], 1)
        self.assertIn("NOT draw calls", report["warning"])

    def test_invalid_min_y_and_height_rejected(self):
        for low, height in ((-63, 2096), (-64, 2095), (-64, 0)):
            with self.assertRaises(ValueError):
                count_chunk_sections({}, min_y=low, height=height)
        with self.assertRaises(ValueError):
            summarize([], max_chunks=0)


if __name__ == "__main__":
    unittest.main()
