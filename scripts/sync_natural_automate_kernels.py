#!/usr/bin/env python3
"""Synchronize Natural Automate's embedded morphology kernels with fitted resources.

The public Natural Automate density codec composes six pure-Java sub-kernels.
The standalone fitted JSON graphs are retained for compatibility and QA; this
script makes their compiled coefficients the single source of truth for the
six kernels embedded in TerrainArchetypeMath.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "neoforge/src/main/resources/data/realscale/worldgen/density_function"
JAVA_FILE = ROOT / "neoforge/src/main/java/org/realscale/neoforge/TerrainArchetypeMath.java"
FAMILIES = ("alpine", "glacial", "canyon", "volcanic", "karst", "dune")
FIELDS = (
    "macro_gain",
    "meso_gain",
    "micro_gain",
    "relief_gain",
    "ridge_sharpness",
    "cliff_index",
    "valley_index",
    "ridge_index",
    "anisotropy_gain",
    "dune_axis_radians",
    "dominant_wavelength_m",
)


def java_number(value: object, family: str, field: str) -> str:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{family}.{field} must be numeric")
    number = float(value)
    if not (-1.0e9 < number < 1.0e9):
        raise ValueError(f"{family}.{field} is outside the supported finite range")
    rendered = repr(number)
    if "." not in rendered and "e" not in rendered.lower():
        rendered += ".0"
    return rendered


def fitted_constructor(family: str) -> str:
    path = RESOURCE_ROOT / f"tva_{family}_fitted/height_signal.json"
    resource = json.loads(path.read_text(encoding="utf-8"))
    if resource.get("type") != "realscale:terrain_archetype":
        raise ValueError(f"{path} is not a terrain_archetype resource")
    if resource.get("family") != family:
        raise ValueError(f"{path} declares family {resource.get('family')!r}, expected {family!r}")
    values = [java_number(resource.get(field), family, field) for field in FIELDS]
    return f'new TerrainArchetypeMath("{family}",' + ",".join(values) + ")"


def synchronize(source: str) -> str:
    updated = source
    for family in FAMILIES:
        pattern = re.compile(
            r'new TerrainArchetypeMath\("' + re.escape(family) + r'",[^)]*\)'
        )
        matches = list(pattern.finditer(updated))
        if len(matches) != 1:
            raise ValueError(
                f"Expected exactly one embedded {family} kernel in {JAVA_FILE}, found {len(matches)}"
            )
        updated = pattern.sub(fitted_constructor(family), updated, count=1)
    return updated


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if the embedded Natural Automate kernels differ from fitted JSON resources",
    )
    args = parser.parse_args()
    source = JAVA_FILE.read_text(encoding="utf-8")
    updated = synchronize(source)
    if args.check:
        if updated != source:
            print("FAIL: Natural Automate's embedded kernels are out of sync with fitted resources", file=sys.stderr)
            return 1
        print("PASS: Natural Automate embeds the exact six fitted profile coefficients")
        return 0
    if updated != source:
        JAVA_FILE.write_text(updated, encoding="utf-8")
        print("UPDATED: synchronized Natural Automate with six fitted profile resources")
    else:
        print("UNCHANGED: Natural Automate already matches all six fitted profile resources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
