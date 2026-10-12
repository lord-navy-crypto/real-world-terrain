#!/usr/bin/env python3
"""Exercise structure NBT evidence classification without pretending it is live worldgen."""
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"qa/neoforge"))
import rcon_worldgen_smoke as worldgen
spec=importlib.util.spec_from_file_location("structure_audit",ROOT/"qa/neoforge/audit_vanilla_structures.py")
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

def chunk(with_start):
    bb=[4,68,4,11,77,11]
    children=[{"BB":[5,68,5,10,75,10],"id":"minecraft:trial_chambers/corridor"}]
    starts={"minecraft:trial_chambers":{
        "id":"minecraft:trial_chambers","BB":bb,"Children":children
    }} if with_start else {}
    return {"xPos":0,"zPos":0,"Status":"minecraft:full",
            "structures":{"starts":starts,"References":{}}}

class StructureAnvilAuditorTests(unittest.TestCase):
    def test_serialize_one_chunk_without_dropping_village_search(self):
        required=("minecraft:trial_chambers","minecraft:ruined_portal",
                  "minecraft:village_taiga")
        for settings in ("realscale:realism_max","realscale:himalayan",
                         "minecraft:overworld"):
            self.assertEqual(worldgen.structure_targets(settings),required)

    def fixture(self,root):
        p=root/"run/world/region"
        p.mkdir(parents=True)
        (p/"r.0.0.mca").write_bytes(b"\0"*8192)
        (root/"vanilla_structure_locate_qa.json").write_text(json.dumps([
          {"located":True,"structure":"minecraft:trial_chambers","x":8,"z":8}
        ]))
    def test_saved_natural_start_proven_with_children(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);self.fixture(root)
            with patch.object(audit,"iter_region_chunks",return_value=iter([chunk(True)])),patch.object(audit,"get_block",return_value="minecraft:stone"):
                result=audit.audit(root)
            self.assertEqual(result["natural_structure_starts"],1)
            self.assertEqual(result["status"],"STARTS_VERIFIED")
            self.assertEqual(result["saved_chunk_statuses"],{"minecraft:full":1})
            self.assertTrue(result["located_candidate_chunk_evidence"][0]["candidate_chunk_saved"])
            self.assertEqual(result["support_results"],{"supported":3})
    def test_modern_1211_structure_start_has_no_root_bounding_box(self):
        # Actual Minecraft 1.21.1 vanilla villages, trial chambers, ruins
        # and ancient cities omit a root BB; every jigsaw child has BB.
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);self.fixture(root)
            modern=chunk(True)
            modern["structures"]["starts"]["minecraft:trial_chambers"].pop("BB")
            modern["structures"]["starts"]["minecraft:trial_chambers"]["Children"]=[
                {"BB":[1,65,2,10,75,12],"id":"minecraft:jigsaw"},
                {"BB":[-4,61,-2,4,69,4],"id":"minecraft:jigsaw"}
            ]
            with patch.object(audit,"iter_region_chunks",return_value=iter([modern])),patch.object(audit,"get_block",return_value="minecraft:stone"):
                result=audit.audit(root)
            self.assertEqual(result["natural_structure_starts"],1)
            self.assertEqual(result["fully_generated_structure_starts"],1)
            self.assertEqual(result["bbox_sources"],{"children_BB":1})
            self.assertEqual(result["structure_start_examples"][0]["bbox"],[-4,61,-2,10,75,12])
            self.assertTrue(result["conclusive_saved_full_structure"])
            self.assertIn("minecraft:trial_chambers",
                          result["located_candidate_chunk_evidence"][0]["candidate_chunk_live_start_ids"])

    def test_partial_proto_chunk_cannot_prove_complete_build(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);self.fixture(root)
            part=chunk(True)
            part["Status"]="minecraft:structure_starts"
            part["structures"]["starts"]["minecraft:trial_chambers"].pop("BB")
            with patch.object(audit,"iter_region_chunks",return_value=iter([part])):
                result=audit.audit(root)
            self.assertEqual(result["natural_structure_starts"],1)
            self.assertEqual(result["fully_generated_structure_starts"],0)
            self.assertFalse(result["conclusive_saved_full_structure"])
            self.assertEqual(result["support_results"],
                             {"piece_chunk_not_fully_generated":3})

    def test_no_natural_starts_does_not_claim_no_structures_globally(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);self.fixture(root)
            with patch.object(audit,"iter_region_chunks",return_value=iter([chunk(False)])):
                result=audit.audit(root)
            self.assertEqual(result["natural_structure_starts"],0)
            self.assertEqual(result["status"],"NOT_VERIFIED_NO_STARTS_SAMPLED")
            self.assertEqual(result["chunks_with_nonempty_structure_compound"],1)
            self.assertEqual(result["saved_chunk_statuses"]["minecraft:full"],1)
            self.assertIsNotNone(result["warning_if_zero_starts"])

if __name__=="__main__":
    unittest.main()
