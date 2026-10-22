#!/usr/bin/env python3
"""Offline regression test for the DH experiment; no Minecraft assets needed."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "qa/neoforge"))
from audit_distant_horizons_compat import audit
from install_distant_horizons import select_jar

class DistantHorizonsCompatibilityTests(unittest.TestCase):
    def test_modrinth_release_selection_rejects_other_loaders(self):
        v = {"id":"9w34y8ai","project_id":"uCdwusMi",
             "game_versions":["1.21.1"],"loaders":["neoforge"],
             "files":[{"filename":"DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar",
                       "url":"https://cdn.modrinth.com/data/uCdwusMi/fake.jar",
                       "hashes":{"sha512":"abc"}}]}
        self.assertEqual(select_jar(v)["hashes"]["sha512"], "abc")
        v["loaders"]=["fabric"]
        with self.assertRaises(ValueError):
            select_jar(v)

    def test_dh_not_marked_compatible_without_real_chunk(self):
        x = audit("DistantHorizons initialized", True, "No chunks loaded")
        self.assertFalse(x["pass"])

    def test_dh_pass_requires_log_marker_and_completed_chunk(self):
        status = "QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS: PASS"
        self.assertTrue(audit("DistantHorizons started", True, status)["pass"])
        self.assertFalse(audit("RealScale alone", True, status)["pass"])
        self.assertTrue(audit("RealScale alone", False, status)["pass"])

    def test_actual_mixin_failure_is_not_success(self):
        x=audit("DistantHorizons MixinApplyError", True,
                "QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS: PASS")
        self.assertFalse(x["pass"])

    def test_base_world_height_is_not_silently_reduced(self):
        import json
        p=ROOT / "neoforge/src/main/resources/data/realscale/dimension_type/realism_max_overworld.json"
        d=json.loads(p.read_text())
        self.assertEqual((d["min_y"], d["height"], d["logical_height"]),(-64,2096,2096))

if __name__=="__main__":
    unittest.main()
