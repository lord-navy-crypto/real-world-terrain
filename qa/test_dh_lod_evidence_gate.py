"""Independent LOD storage gate: no full-chunk false positives."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from verify_dh_chunky_guard_runtime import verify_lod_storage

def evidence():
    return {
        "state":"candidate_lod_records_present",
        "dh_sqlite_files_discovered":3,
        "dh_sqlite_files_scanned":3,
        "scan_truncated":False,
        "healthy_sqlite_files":3,
        "candidate_lod_data_tables_with_records":1,
        "lod_payload_verified":False,
        "gpu_lod_visible":"unverified",
        "files":[
            {"sqlite_open_ok":True,"integrity_check":"ok","incomplete_snapshot":False},
            {"sqlite_open_ok":True,"integrity_check":"ok","incomplete_snapshot":False},
            {"sqlite_open_ok":True,"integrity_check":"ok","incomplete_snapshot":False},
        ]
    }

class EvidenceGateTests(unittest.TestCase):
    def test_actual_three_db_one_lod_table_pattern_is_valid(self):
        self.assertTrue(verify_lod_storage(evidence())[0])
    def test_no_report_fails(self):
        self.assertFalse(verify_lod_storage(None)[0])
    def test_no_lod_rows_fails_even_when_full_chunk_exists(self):
        r=evidence()
        r["state"]="sqlite_records_found_lod_payload_not_verified"
        r["candidate_lod_data_tables_with_records"]=0
        self.assertFalse(verify_lod_storage(r)[0])
    def test_database_corruption_is_not_a_false_pass(self):
        r=evidence()
        r["files"][0]["integrity_check"]="not_ok"
        self.assertFalse(verify_lod_storage(r)[0])
    def test_wal_present_incomplete_snapshot_never_passes(self):
        r=evidence()
        r["files"][0]["incomplete_snapshot"]=True
        self.assertFalse(verify_lod_storage(r)[0])
    def test_missing_dimension_scan_is_not_a_pass(self):
        r=evidence()
        r["dh_sqlite_files_scanned"]=2
        self.assertFalse(verify_lod_storage(r)[0])
    def test_partial_scan_is_not_a_pass(self):
        r=evidence()
        r["scan_truncated"]=True
        self.assertFalse(verify_lod_storage(r)[0])
    def test_cannot_fake_graphics_proof(self):
        r=evidence()
        r["gpu_lod_visible"]="yes"
        self.assertFalse(verify_lod_storage(r)[0])
    def test_empty_or_forged_file_collection_is_not_a_pass(self):
        r=evidence()
        r["files"]=[]
        self.assertFalse(verify_lod_storage(r)[0])
    def test_bad_json_scalar_does_not_pass(self):
        for invalid in [False,0,[],{},"candidate_lod_records_present"]:
            self.assertFalse(verify_lod_storage(invalid)[0])
    def test_client_uses_independent_gating_after_smoke_runner(self):
        code=(Path(__file__).resolve().parent/".github"/"workflows").exists()
        # root is parents[1], workflow arguments explicitly require evidence.
        workflow=(Path(__file__).resolve().parents[1]/".github/workflows/dh-chunky-real-neoforge-test.yml").read_text()
        self.assertIn("build/dh-chunky-lod-storage.json",workflow)

if __name__=="__main__":
    unittest.main()
