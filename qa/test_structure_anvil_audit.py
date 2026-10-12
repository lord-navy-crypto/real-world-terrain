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
