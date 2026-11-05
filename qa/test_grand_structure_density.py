#!/usr/bin/env python3
"""Regress 1.21.1 Grand Exploration structure candidate presets and dimensions."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"scripts"))
import make_structure_density_packs as structure


class GrandStructureDensityTests(unittest.TestCase):
    def test_legacy_candidate_positions_are_not_changed(self):
        self.assertEqual(structure.candidate_spacing(34,2,8),24)
        self.assertEqual(structure.candidate_spacing(34,3,8),20)
        self.assertEqual(structure.original_set("villages")["placement"]["spacing"],34)

    def test_4x_and_6x_candidate_spacing_only(self):
        for ratio in (4,6):
            data = structure.pack_resources(ratio)
            structure.validate(ratio, data)
            self.assertEqual(len(data),13)
            for name in structure.VANILLA:
                key=f"data/minecraft/worldgen/structure_set/{name}.json"
                old=structure.original_set(name)
                new=data[key]
                self.assertEqual(new["structures"],old["structures"])
                self.assertEqual(set(old["placement"]),set(new["placement"]))
                for key_name in old["placement"]:
                    if key_name == "spacing":
                        self.assertGreater(new["placement"][key_name],
                                           old["placement"]["separation"])
                        self.assertLess(new["placement"][key_name],
                                        old["placement"][key_name])
                    else:
                        self.assertEqual(old["placement"][key_name],
                                         new["placement"][key_name])
                self.assertFalse(
                    any(x in name for x in ("stronghold","pillager","buried_treasure")))
        self.assertEqual(structure.candidate_spacing(34,4,8),17)
        self.assertEqual(structure.candidate_spacing(34,6,8),14)
        # Ocean ruins cannot safely reach sixfold candidates because vanilla
        # separation is eight chunks; spacing is clamped to ten chunks.
        self.assertEqual(structure.candidate_spacing(20,6,8),10)
        self.assertEqual(structure.candidate_spacing(80,6,20),33)

    def test_nether_end_preserves_weighting_and_spread(self):
        self.assertEqual(structure.dimension_original_set("nether_complexes")
                         ["structures"], [
                             {"structure":"minecraft:fortress","weight":2},
                             {"structure":"minecraft:bastion_remnant","weight":3}])
        self.assertEqual(structure.dimension_original_set("end_cities")
                         ["placement"]["spread_type"],"triangular")
        for ratio in (2,3):
            pack = structure.dimension_pack_resources(ratio)
            structure.validate_dimensions(ratio,pack)
            self.assertEqual(len(pack),3)
            end=pack["data/minecraft/worldgen/structure_set/end_cities.json"]
            self.assertEqual(end["placement"]["separation"],11)
            self.assertEqual(end["placement"]["salt"],10387313)
            self.assertEqual(end["placement"]["spread_type"],"triangular")
            self.assertGreaterEqual(end["placement"]["spacing"],13)

    def test_all_six_datapacks_are_valid_and_disjoint_by_dimension(self):
        with tempfile.TemporaryDirectory() as dirname:
            targets=structure.build(Path(dirname))
            self.assertEqual(len(targets),6)
            for target in targets:
                with ZipFile(target) as zipf:
                    self.assertIsNone(zipf.testzip())
                    self.assertEqual(
                        json.loads(zipf.read("pack.mcmeta"))["pack"]["pack_format"],48)
                    self.assertTrue(all(
                        n=="pack.mcmeta"
                        or n.startswith("data/minecraft/worldgen/structure_set/")
                        for n in zipf.namelist()))
            for ratio in (2,3):
                overworld=set(structure.pack_resources(ratio))-{"pack.mcmeta"}
                dimensions=set(structure.dimension_pack_resources(ratio))-{"pack.mcmeta"}
                self.assertFalse(overworld & dimensions)


if __name__=="__main__":
    unittest.main()
