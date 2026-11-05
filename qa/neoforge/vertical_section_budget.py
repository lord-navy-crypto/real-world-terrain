#!/usr/bin/env python3
"""Read-only inventory of persisted vertical Minecraft Sections.

A 2096-high RealScale world has 131 16-block Section slots per X/Z chunk
column. Most are NOT necessarily GPU rendered: this tool reads only saved
Anvil block-state palettes, not client visibility, GPU or mesh state.

Run against a copy of a generated test world's region directory:
  python3 qa/neoforge/vertical_section_budget.py /path/to/world/region \
      --min-y -64 --height 2096 --max-chunks 128

Uniform stone candidate != invisible section. Even a completely stone-filled
Section can have externally visible boundary faces, and transparent paths
through adjacent cave Sections may be visible from very far away.
Do not use this inventory as a vertical culling decision.
"""
import argparse
import collections
import json
from pathlib import Path

from realscale_headless_qa import iter_region_chunks

AIRLIKE = frozenset(("minecraft:air", "minecraft:cave_air",
                     "minecraft:void_air"))
# Conservative whitelist: never mistake water, glass, modded blocks or an
# unknown state for a guaranteed opaque/solid rock Section.
UNIFORM_ROCK = frozenset((
    "minecraft:stone", "minecraft:deepslate", "minecraft:tuff",
    "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
    "minecraft:calcite", "minecraft:basalt", "minecraft:blackstone",
    "minecraft:netherrack", "minecraft:end_stone"
))
KINDS = ("uniform_air", "uniform_rock_candidate", "mixed_with_air",
         "other_palette", "missing_palette")


def section_kind(section):
    """Classify palette composition ONLY, never renderer visibility."""
    states = section.get("block_states")
    if not isinstance(states, dict):
        return "missing_palette"
    palette = states.get("palette")
    if not isinstance(palette, list) or not palette:
        return "missing_palette"
    names = []
    for entry in palette:
        if not isinstance(entry, dict) or not isinstance(entry.get("Name"), str):
            return "missing_palette"
        names.append(entry["Name"])
    if len(names) == 1:
        if names[0] in AIRLIKE:
            return "uniform_air"
        if names[0] in UNIFORM_ROCK:
            return "uniform_rock_candidate"
    if any(name in AIRLIKE for name in names):
        return "mixed_with_air"
    return "other_palette"


def count_chunk_sections(chunk, min_y=-64, height=2096):
    """Return exact saved palette inventory; do not synthesize loaded sections.

    Only full multiples of 16 are accepted. The expected per-chunk inventory
    height describes addressable vertical Section slots, not how many
    persisted Sections the game necessarily contains or renders.
    """
    if height <= 0 or height % 16 or min_y % 16:
        raise ValueError("min_y and height must align to 16-block Sections")
    first, last = min_y // 16, (min_y + height) // 16
    by_y = {}
    invalid = []
    duplicates = []
    for section in chunk.get("sections", []):
        y = section.get("Y")
        if not isinstance(y, int) or isinstance(y, bool):
            invalid.append(y)
        elif not first <= y < last:
            invalid.append(y)
        elif y in by_y:
            duplicates.append(y)
        else:
            by_y[y] = section_kind(section)
    return {
        "section_slots": last - first,
        "stored_in_range": len(by_y),
        "not_explicitly_stored": last - first - len(by_y),
        "kinds": dict(collections.Counter(by_y.values())),
        "sections": by_y,
        "out_of_range_or_invalid_y": invalid,
        "duplicate_y": duplicates,
    }


def summarize(chunks, *, min_y=-64, height=2096, max_chunks=128):
    if max_chunks <= 0:
        raise ValueError("max_chunks must be positive")
    kinds = collections.Counter()
    altitude = collections.defaultdict(collections.Counter)
    invalid_count = 0
    duplicated_count = 0
    loaded = 0
    slots_total = 0
    stored_total = 0
    for chunk in chunks:
        if loaded >= max_chunks:
            break
        data = count_chunk_sections(chunk, min_y=min_y, height=height)
        loaded += 1
        slots_total += data["section_slots"]
        stored_total += data["stored_in_range"]
        kinds.update(data["kinds"])
        invalid_count += len(data["out_of_range_or_invalid_y"])
        duplicated_count += len(data["duplicate_y"])
        for y, kind in data["sections"].items():
            altitude[y][kind] += 1
    return {
        "source": "Persisted Anvil Sections, block-state palette metadata only",
        "sampled_chunks": loaded,
        "min_y": min_y,
        "height": height,
        "vertical_section_slots_per_chunk": height // 16,
        "total_addressable_section_slots": slots_total,
        "explicit_sections": stored_total,
        "absent_section_slots": slots_total - stored_total,
        "palette_categories": {kind: kinds[kind] for kind in KINDS},
        "invalid_section_y_entries": invalid_count,
        "duplicate_section_y_entries": duplicated_count,
        "by_section_y": {
            str(y): {kind: altitude[y][kind] for kind in KINDS
                     if altitude[y][kind]}
            for y in sorted(altitude)
        },
        "warning": (
            "Uniform rock, air and mixed palette counts are NOT draw calls, "
            "meshed Section counts or a safe occlusion proof. This report "
            "cannot justify hiding underground caves or skipping worldgen."
        ),
    }


def regions_at(paths):
    for path in paths:
        if path.is_file():
            if path.suffix == ".mca":
                yield path
        elif path.is_dir():
            yield from sorted(path.rglob("r.*.*.mca"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("regions", type=Path, nargs="+",
                        help="saved world region directory or .mca file")
    parser.add_argument("--min-y", type=int, default=-64)
    parser.add_argument("--height", type=int, default=2096)
    parser.add_argument("--max-chunks", type=int, default=128)
    parser.add_argument("--output", type=Path,
                        help="optional JSON diagnostic report path")
    args = parser.parse_args(argv)
    files = list(regions_at(args.regions))
    if not files:
        parser.error("No saved region .mca files found")
    def chunks():
        for region in files:
            yield from iter_region_chunks(region)
    report = summarize(chunks(), min_y=args.min_y, height=args.height,
                       max_chunks=args.max_chunks)
    if not report["sampled_chunks"]:
        parser.error("No saved chunks found")
    result = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result, encoding="utf-8")
    print(result, end="")


if __name__ == "__main__":
    main()
