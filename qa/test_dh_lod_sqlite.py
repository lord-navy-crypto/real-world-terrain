"""Isolated SQLite fixtures for RealScale/DH read-only LOD persistence QA."""
import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from audit_dh_lod_sqlite import scan

class DhLodPersistenceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()
    def path(self):
        p=self.root/"world"/"data"/"DistantHorizons.sqlite"
        p.parent.mkdir(parents=True,exist_ok=True)
        return p
    def create(self,sql):
        p=self.path()
        con=sqlite3.connect(str(p))
        try:
            for stmt in sql:
                con.execute(stmt)
            con.commit()
        finally:
            con.close()
        return p
    def test_missing_database_is_not_a_success_or_crash(self):
        result=scan(self.root)
        self.assertEqual(result["state"],"no_lod_database_found")
        self.assertEqual(result["gpu_lod_visible"],"unverified")
        self.assertFalse(result["lod_payload_verified"])
    def test_filled_full_data_table_is_candidate_not_gpu_proof(self):
        db=self.create([
            'CREATE TABLE "FullData" (id INTEGER PRIMARY KEY, payload BLOB)',
            "INSERT INTO FullData(payload) VALUES (X'123456')"
        ])
        before=db.read_bytes()
        result=scan(self.root)
        self.assertEqual(result["state"],"candidate_lod_records_present")
        self.assertEqual(result["candidate_lod_data_tables_with_records"],1)
        self.assertEqual(result["healthy_sqlite_files"],1)
        self.assertFalse(result["lod_payload_verified"])
        self.assertEqual(db.read_bytes(),before)
    def test_metadata_only_database_does_not_prove_lod_data(self):
        self.create([
            'CREATE TABLE "SchemaVersion" (version INTEGER)',
            "INSERT INTO SchemaVersion VALUES (4)"
        ])
        result=scan(self.root)
        self.assertEqual(result["state"],"sqlite_records_found_lod_payload_not_verified")
        self.assertEqual(result["candidate_lod_data_tables_with_records"],0)
    def test_wal_presence_is_explicit_snapshot_uncertainty(self):
        db=self.create(['CREATE TABLE FullData (id INTEGER)'])
        wal=Path(str(db)+"-wal")
        wal.write_bytes(b"not consumed by immutable snapshot")
        result=scan(self.root)
        self.assertEqual(result["state"],"incomplete_due_to_sqlite_wal")
        self.assertTrue(result["files"][0]["incomplete_snapshot"])
        self.assertTrue(wal.exists())
    def test_damaged_sqlite_not_treated_as_valid_records(self):
        db=self.path()
        db.write_bytes(b"not sqlite")
        result=scan(self.root)
        self.assertEqual(result["state"],"database_read_or_integrity_problem")
        self.assertFalse(result["files"][0]["sqlite_open_ok"])
    def test_private_world_path_and_sql_table_names_never_emitted(self):
        db=self.create([
            'CREATE TABLE "PrivateWorldPersonalNickname" (s TEXT)',
            "INSERT INTO PrivateWorldPersonalNickname VALUES ('secret')"
        ])
        result=scan(self.root)
        rendered=str(result)
        self.assertNotIn(self.root.name,rendered)
        self.assertNotIn("PrivateWorldPersonalNickname",rendered)
        self.assertNotIn("secret",rendered)
        self.assertNotIn(str(db),rendered)
    def test_never_accepts_missing_folder(self):
        with self.assertRaises(ValueError):
            scan(self.root/"missing")
if __name__=="__main__":
    unittest.main()
