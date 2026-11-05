"""Read-only, privacy-preserving audit of official Distant Horizons SQLite LOD stores.

This module deliberately reports only generic storage evidence. It never
returns save paths, world names, table names, SQL rows, or raw exceptions.
It opens databases in immutable read-only mode and never checkpoints WAL.
"""
from __future__ import annotations

import re
import sqlite3
from pathlib import Path
from typing import Any

_OFFICIAL_DB_NAME = "distanthorizons.sqlite"
_LOD_TABLE_NAME = re.compile(
    r"(?:lod|render|full.?data|detail|section|chunk|column)",
    re.IGNORECASE,
)
_MAX_DATABASES = 64
_MAX_TABLES_PER_DATABASE = 128


def _has_row(connection: sqlite3.Connection, table_name: str) -> bool:
    # Table names come only from sqlite_master. Quote them as identifiers;
    # do not expose them or interpolate any user-controlled SQL.
    identifier = '"' + table_name.replace('"', '""') + '"'
    return connection.execute(
        f"SELECT 1 FROM {identifier} LIMIT 1"
    ).fetchone() is not None


def _inspect_database(path: Path, index: int) -> dict[str, Any]:
    try:
        size_bytes = path.stat().st_size
    except OSError:
        size_bytes = 0

    wal_path = path.with_name(path.name + "-wal")
    try:
        wal_present = wal_path.exists()
        wal_bytes = wal_path.stat().st_size if wal_present else 0
    except OSError:
        wal_present = True
        wal_bytes = 0

    result: dict[str, Any] = {
        "index": index,
        "size_bytes": size_bytes,
        "wal_present": wal_present,
        "wal_bytes": wal_bytes,
        "sqlite_opened": False,
        "quick_check_ok": False,
        "table_count": 0,
        "tables_inspected": 0,
        "tables_with_rows": 0,
        "candidate_lod_tables_with_rows": 0,
        "table_scan_truncated": False,
        "status": "database_read_or_integrity_problem",
    }

    connection: sqlite3.Connection | None = None
    try:
        # immutable=1 guarantees SQLite will not create journal files or
        # incorporate a live WAL snapshot. A WAL sidecar is reported separately.
        uri = path.resolve(strict=True).as_uri() + "?mode=ro&immutable=1"
        connection = sqlite3.connect(uri, uri=True, timeout=0.5)
        connection.execute("PRAGMA query_only=ON")
        result["sqlite_opened"] = True

        check = connection.execute("PRAGMA quick_check(1)").fetchone()
        result["quick_check_ok"] = bool(
            check and str(check[0]).strip().lower() == "ok"
        )
        if not result["quick_check_ok"]:
            return result

        rows = connection.execute(
            "SELECT name FROM sqlite_master "
            "WHERE type='table' AND name NOT LIKE 'sqlite_%' "
            "ORDER BY name"
        ).fetchall()
        table_names = [str(row[0]) for row in rows]
        result["table_count"] = len(table_names)

        inspected = table_names[:_MAX_TABLES_PER_DATABASE]
        result["tables_inspected"] = len(inspected)
        result["table_scan_truncated"] = len(table_names) > len(inspected)

        for table_name in inspected:
            if _has_row(connection, table_name):
                result["tables_with_rows"] += 1
                if _LOD_TABLE_NAME.search(table_name):
                    result["candidate_lod_tables_with_rows"] += 1

        result["status"] = (
            "database_read_or_integrity_problem"
            if result["table_scan_truncated"]
            else "ok"
        )
    except (OSError, sqlite3.Error, ValueError):
        # Raw exception text can contain absolute paths or schema details.
        result["status"] = "database_read_or_integrity_problem"
    finally:
        if connection is not None:
            try:
                connection.close()
            except sqlite3.Error:
                result["status"] = "database_read_or_integrity_problem"

    return result


def scan(root: str | Path) -> dict[str, Any]:
    """Inspect official DistantHorizons.sqlite files under *root*, read-only."""
    root_path = Path(root)
    try:
        root_available = root_path.is_dir() and not root_path.is_symlink()
    except OSError:
        root_available = False

    if not root_available:
        found: list[Path] = []
        scan_truncated = False
    else:
        try:
            candidates = (
                path
                for path in root_path.rglob("*")
                if path.is_file()
                and not path.is_symlink()
                and path.name.lower() == _OFFICIAL_DB_NAME
            )
            ordered = sorted(candidates, key=lambda item: item.as_posix())
            scan_truncated = len(ordered) > _MAX_DATABASES
            found = ordered[:_MAX_DATABASES]
        except OSError:
            found = []
            scan_truncated = True

    files = [_inspect_database(path, index) for index, path in enumerate(found, 1)]
    database_count = len(files)
    opened_count = sum(bool(item["sqlite_opened"]) for item in files)
    quick_check_ok_count = sum(bool(item["quick_check_ok"]) for item in files)
    wal_count = sum(bool(item["wal_present"]) for item in files)
    table_count = sum(int(item["table_count"]) for item in files)
    tables_with_rows = sum(int(item["tables_with_rows"]) for item in files)
    candidate_count = sum(
        int(item["candidate_lod_tables_with_rows"]) for item in files
    )
    total_bytes = sum(int(item["size_bytes"]) for item in files)
    error_count = sum(
        item["status"] != "ok" for item in files
    )
    scan_truncated = scan_truncated or any(
        bool(item["table_scan_truncated"]) for item in files
    )

    if database_count == 0:
        state = "no_lod_database_found"
    elif wal_count:
        state = "incomplete_due_to_sqlite_wal"
    elif error_count or scan_truncated or opened_count != database_count:
        state = "database_read_or_integrity_problem"
    elif candidate_count:
        state = "candidate_lod_records_present"
    elif tables_with_rows:
        state = "sqlite_records_found_lod_payload_not_verified"
    else:
        state = "sqlite_opened_no_table_records"

    return {
        "state": state,
        "root_available": root_available,
        "read_only": True,
        "database_count": database_count,
        "opened_count": opened_count,
        "quick_check_ok_count": quick_check_ok_count,
        "wal_snapshot_count": wal_count,
        "table_count": table_count,
        "tables_with_rows": tables_with_rows,
        "candidate_lod_tables_with_rows": candidate_count,
        "total_bytes": total_bytes,
        "scan_truncated": scan_truncated,
        "lod_storage_level": (
            "candidate_table_presence_only"
            if candidate_count and not wal_count and not error_count and not scan_truncated
            else "not_proven"
        ),
        "lod_payload_verified": False,
        "gpu_lod_visible": "unverified",
        "snapshot_complete": bool(
            database_count
            and opened_count == database_count
            and not wal_count
            and not error_count
            and not scan_truncated
        ),
        "files": files,
    }


__all__ = ["scan"]
