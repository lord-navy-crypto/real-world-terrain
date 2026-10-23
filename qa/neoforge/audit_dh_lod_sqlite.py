#!/usr/bin/env python3
"""Read-only Distant Horizons SQLite persistence audit.

This checks whether the DH LOD storage layer persisted inspectable records,
not whether the Iris/Sodium client displayed an LOD mesh. Never copies,
repairs, vacuums, checkpoints, or modifies a Minecraft world.

Important: SQLite WALs may contain uncheckpointed rows. Open immutable
read-only; when sidecars are present, report incomplete_snapshot, never
pretend the main database alone contains all current LOD data.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

DB_NAME = "DistantHorizons.sqlite"
KNOWN_DATA = re.compile(r"(?:full.?data|render.?data|lod|data.?source)", re.I)
MAX_FILES = 24
MAX_TABLES = 64


def scan(root):
    root = Path(root)
    if not root.is_dir():
        raise ValueError("DH database scan root is not an existing directory")
    # Never recurse symlinks into arbitrary personal directories.
    databases = sorted(
        p for p in root.rglob(DB_NAME)
        if p.is_file() and not p.is_symlink()
        and not any(part.startswith(".") and part not in (".", "..")
                    for part in p.relative_to(root).parts)
    )
    files = []
    for database in databases[:MAX_FILES]:
        sidecar_wal = Path(str(database) + "-wal").is_file()
        sidecar_shm = Path(str(database) + "-shm").is_file()
        entry = {
            "scope": "server_world_lod_database",
            "db_bytes": database.stat().st_size,
            "wal_present": sidecar_wal,
            "shm_present": sidecar_shm,
            "incomplete_snapshot": sidecar_wal,
            "sqlite_open_ok": False,
            "integrity_check": "not_checked",
            "user_tables": 0,
            "tables_with_records": 0,
            "candidate_lod_tables_with_records": 0,
            "error_class": None,
        }
        # Path, save name, username, and SQL table contents are never included.
        try:
            # SQLite immutable read-only never acquires write locks or changes
            # original DB/WAL/SHM. This can miss WAL content (flagged above).
            con = sqlite3.connect(database.resolve().as_uri()+"?mode=ro&immutable=1",
                                  uri=True, timeout=2)
            try:
                con.execute("PRAGMA query_only = ON")
                con.set_progress_handler(lambda: 1, 5_000_000)
                r = con.execute("PRAGMA quick_check(1)").fetchone()
                entry["integrity_check"] = "ok" if r and r[0] == "ok" else "not_ok"
                tables = con.execute(
                    "SELECT name FROM sqlite_master "
                    "WHERE type='table' AND name NOT LIKE 'sqlite_%' "
                    "ORDER BY name LIMIT ?", (MAX_TABLES + 1,)
                ).fetchall()
                entry["user_tables"] = min(len(tables), MAX_TABLES)
                entry["table_scan_truncated"] = len(tables)>MAX_TABLES
                for (name,) in tables[:MAX_TABLES]:
                    ident='"'+name.replace('"','""')+'"'
                    exists = con.execute("SELECT 1 FROM "+ident+" LIMIT 1").fetchone()
                    if exists:
                        entry["tables_with_records"] += 1
                        if KNOWN_DATA.search(name):
                            entry["candidate_lod_tables_with_records"] += 1
                entry["sqlite_open_ok"] = True
            finally:
                con.close()
        except (sqlite3.Error, OSError, ValueError) as ex:
            # Never report raw exceptions: some contain local private paths.
            entry["error_class"] = type(ex).__name__
        files.append(entry)
    scanned = len(files)
    main_db_healthy = sum(1 for x in files
                          if x["sqlite_open_ok"] and x["integrity_check"]=="ok")
    candidate_tables = sum(x["candidate_lod_tables_with_records"] for x in files)
    records = sum(x["tables_with_records"] for x in files)
    if not databases:
        state = "no_lod_database_found"
    elif any(x["incomplete_snapshot"] for x in files):
        state = "incomplete_due_to_sqlite_wal"
    elif any(not x["sqlite_open_ok"] or x["integrity_check"]!="ok" for x in files):
        state = "database_read_or_integrity_problem"
    elif candidate_tables:
        state = "candidate_lod_records_present"
    elif records:
        state = "sqlite_records_found_lod_payload_not_verified"
    else:
        state = "sqlite_opened_no_table_records"
    return {
        "state": state,
        "dh_sqlite_files_discovered": len(databases),
        "dh_sqlite_files_scanned": scanned,
        "scan_truncated": len(databases)>MAX_FILES,
        "healthy_sqlite_files": main_db_healthy,
        "nonempty_user_tables": records,
        "candidate_lod_data_tables_with_records": candidate_tables,
        "lod_payload_verified": False,  # schema-specific decoding not performed
        "gpu_lod_visible": "unverified",
        "files": files
    }


def main(args):
    if len(args) not in (2,3):
        raise SystemExit("Usage: audit_dh_lod_sqlite.py ROOT [OUTPUT_JSON]")
    result=scan(args[1])
    summary=json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)+"\n"
    if len(args)==3:
        output=Path(args[2])
        output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(summary,encoding="utf-8")
    print("REALSCALE_DH_READ_ONLY_LOD_STORAGE_AUDIT",summary)


if __name__=="__main__":
    main(sys.argv)
