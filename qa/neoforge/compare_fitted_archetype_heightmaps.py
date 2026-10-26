#!/usr/bin/env python3
"""Acceptance gate comparing actual saved surface heights for seven world modes."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

EXPECTED = {"natural", "alpine", "canyon", "glacial", "volcanic", "karst", "dune"}
FITTED = sorted(EXPECTED - {"natural"})


def load_reports(root: Path) -> dict[str, dict]:
    reports: dict[str, dict] = {}
    for path in root.rglob("*-height-summary.json"):
        report = json.loads(path.read_text(encoding="utf-8"))
        slug = report.get("slug")
        if slug not in EXPECTED:
            continue
        if slug in reports:
            raise RuntimeError(f"Duplicate heightmap report for {slug}")
        reports[slug] = report
    missing = EXPECTED - reports.keys()
    if missing:
        raise RuntimeError(f"Missing saved-world heightmap reports: {sorted(missing)}")
    for slug, report in reports.items():
        if report.get("schema") != "realscale:fitted_archetype_heightmap_audit/v1":
            raise RuntimeError(f"Unexpected report schema for {slug}")
        if report.get("sampled_columns", 0) < 100 or report.get("decoded_chunks", 0) < 1:
            raise RuntimeError(f"Insufficient real chunk samples for {slug}")
        if not report.get("heightmap_by_chunk"):
            raise RuntimeError(f"No persisted heightmap payload for {slug}")
    return reports


def compare(reports: dict[str, dict]) -> dict:
    common_chunks = set.intersection(*[
        set(reports[slug]["heightmap_by_chunk"]) for slug in sorted(EXPECTED)
    ])
    comparisons = {}
    all_columns = 0
    for chunk_key in sorted(common_chunks):
        arrays = {
            slug: reports[slug]["heightmap_by_chunk"][chunk_key]
            for slug in sorted(EXPECTED)
        }
        for index in range(256):
            values = {slug: arrays[slug][index] for slug in EXPECTED}
            if any(value is None for value in values.values()):
                continue
            all_columns += 1
            baseline = values["natural"]
            for slug in FITTED:
                delta = abs(values[slug] - baseline)
                metric = comparisons.setdefault(slug, {"changed_columns": 0, "max_abs_delta_blocks": 0})
                if delta:
                    metric["changed_columns"] += 1
                metric["max_abs_delta_blocks"] = max(metric["max_abs_delta_blocks"], delta)

    if all_columns < 100:
        raise RuntimeError(
            f"Too few common X/Z columns to compare modes: {all_columns}; "
            f"common chunks={len(common_chunks)}"
        )

    for slug in FITTED:
        metric = comparisons.get(slug, {"changed_columns": 0, "max_abs_delta_blocks": 0})
        metric["common_columns"] = all_columns
        metric["changed_fraction"] = round(metric["changed_columns"] / all_columns, 6)
        if metric["changed_columns"] < 4 or metric["max_abs_delta_blocks"] < 1:
            raise RuntimeError(
                f"{slug} did not measurably change persisted surface heights vs natural: {metric}"
            )
        comparisons[slug] = metric

    signatures = {slug: reports[slug]["heightmap_sha256"] for slug in sorted(EXPECTED)}
    duplicate_groups: dict[str, list[str]] = {}
    for slug, digest in signatures.items():
        duplicate_groups.setdefault(digest, []).append(slug)
    duplicates = [group for group in duplicate_groups.values() if len(group) > 1]
    if duplicates:
        raise RuntimeError(f"World modes have identical persisted heightmap signatures: {duplicates}")

    return {
        "schema": "realscale:fitted_archetype_heightmap_comparison/v1",
        "modes": sorted(EXPECTED),
        "common_chunks": len(common_chunks),
        "common_columns": all_columns,
        "heightmap_signatures": signatures,
        "fitted_vs_natural": comparisons,
        "pass": True,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact_root", type=Path)
    parser.add_argument("--output", type=Path, default=Path("fitted-archetype-heightmap-comparison.json"))
    args = parser.parse_args()
    reports = load_reports(args.artifact_root)
    result = compare(reports)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
