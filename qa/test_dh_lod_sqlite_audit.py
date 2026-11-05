from __future__ import annotations

import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "qa" / "neoforge"))
from audit_dh_lod_sqlite import scan  # noqa: E402


class DistantHorizonsLodSqliteAuditTests(unittest.TestCase):
    def make_database(self, root: Path, *, table: str = "Metadata", rows: int = 1) -> Path:
        database = root / "world" / "data" / "DistantHorizons.sqlite"
        database.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(database) as connection:
            connection.execute(f'CREATE TABLE "{table}" (value TEXT)')
            for index in range(rows):
                connection.execute(
                    f'INSERT INTO "{table}" (value) VALUES (?)',
                    (f"row-{index}",),
                )
        return database

    def test_no_official_database_is_reported_as_unknown(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "other.sqlite").write_bytes(b"not a DH database")
            report = scan(root)
            self.assertEqual(report["state"], "no_lod_database_found")
            self.assertEqual(report["database_count"], 0)
            self.assertFalse(report["lod_payload_verified"])
            self.assertEqual(report["gpu_lod_visible"], "unverified")

    def test_metadata_rows_do_not_claim_lod_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            database = self.make_database(Path(temporary), table="Metadata")
            report = scan(Path(temporary))
            self.assertEqual(
                report["state"],
                "sqlite_records_found_lod_payload_not_verified",
            )
            self.assertEqual(report["tables_with_rows"], 1)
            self.assertEqual(report["candidate_lod_tables_with_rows"], 0)
            self.assertFalse(report["lod_payload_verified"])
            self.assertTrue(database.is_file())

    def test_candidate_full_data_rows_are_only_partial_storage_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.make_database(Path(temporary), table="FullData")
            report = scan(Path(temporary))
            self.assertEqual(report["state"], "candidate_lod_records_present")
            self.assertEqual(
                report["lod_storage_level"],
                "candidate_table_presence_only",
            )
            self.assertFalse(report["lod_payload_verified"])
            self.assertEqual(report["gpu_lod_visible"], "unverified")

    def test_corrupt_database_is_reported_without_raw_error_or_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            database = root / "world" / "DistantHorizons.sqlite"
            database.parent.mkdir(parents=True)
            database.write_bytes(b"not sqlite")
            report = scan(root)
            self.assertEqual(
                report["state"],
                "database_read_or_integrity_problem",
            )
            serialized = json.dumps(report, sort_keys=True)
            self.assertNotIn(str(root), serialized)
            self.assertNotIn("DistantHorizons.sqlite", serialized)
            self.assertNotIn("not sqlite", serialized)

    def test_wal_sidecar_is_flagged_and_scan_does_not_change_database(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            database = root / "world" / "DistantHorizons.sqlite"
            database.parent.mkdir(parents=True)
            writer = sqlite3.connect(database)
            try:
                writer.execute("PRAGMA journal_mode=WAL")
                writer.execute("CREATE TABLE FullData (value TEXT)")
                writer.execute("INSERT INTO FullData VALUES ('lod-record')")
                writer.commit()
                wal = Path(str(database) + "-wal")
                self.assertTrue(wal.exists())
                before_database = database.read_bytes()
                before_wal = wal.read_bytes()

                report = scan(root)

                self.assertEqual(
                    report["state"],
                    "incomplete_due_to_sqlite_wal",
                )
                self.assertEqual(report["wal_snapshot_count"], 1)
                self.assertFalse(report["snapshot_complete"])
                self.assertFalse(report["lod_payload_verified"])
                self.assertEqual(database.read_bytes(), before_database)
                self.assertEqual(wal.read_bytes(), before_wal)
                # The scanner must not hold a write lock or modify the open DB.
                writer.execute("INSERT INTO FullData VALUES ('still-writable')")
                writer.commit()
            finally:
                writer.close()

    def test_empty_table_is_not_reported_as_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            database = Path(temporary) / "DistantHorizons.sqlite"
            with sqlite3.connect(database) as connection:
                connection.execute("CREATE TABLE RenderData (value TEXT)")
            report = scan(Path(temporary))
            self.assertEqual(report["state"], "sqlite_opened_no_table_records")
            self.assertEqual(report["candidate_lod_tables_with_rows"], 0)

    def test_report_never_contains_table_names_or_save_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_database(root, table="FullData")
            report = scan(root)
            serialized = json.dumps(report, sort_keys=True)
            self.assertNotIn(str(root), serialized)
            self.assertNotIn("FullData", serialized)
            self.assertNotIn("DistantHorizons.sqlite", serialized)
            self.assertTrue(report["read_only"])


if __name__ == "__main__":
    unittest.main()
