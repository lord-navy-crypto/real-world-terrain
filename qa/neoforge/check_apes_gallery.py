#!/usr/bin/env python3
"""Audit REAL saved Anvil chunks in the deliberately artificial APES gallery.

This tests biome-selection coverage and broad chunk generation success.
It does NOT prove a particular custom feature was actually placed; Minecraft
vanilla vegetation often uses the same native blocks. Do not use gallery as
a natural survival-world climate preset.
"""
import json
import sys
from collections import Counter
from pathlib import Path

from realscale_headless_qa import iter_region_chunks

WORLD = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("run/apes-gallery-test")
EXPECTED = [
    "minecraft:jungle",
    "minecraft:old_growth_spruce_taiga",
    "minecraft:forest",
    "minecraft:taiga",
    "minecraft:plains",
    "minecraft:savanna",
    "minecraft:wooded_badlands",
    "minecraft:desert",
    "minecraft:snowy_plains",
    "minecraft:stony_peaks",
    "minecraft:river",
    "minecraft:swamp",
    "minecraft:mangrove_swamp",
    "minecraft:lukewarm_ocean",
    "minecraft:warm_ocean",
    "minecraft:deep_ocean",
]

counts = Counter()
observed_blocks = Counter()
chunks = 0
outpost_chunks = 0
regions = sorted((WORLD / "region").glob("r.*.*.mca"))
assert regions, "No gallery overworld region files persisted"
for region in regions:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status") != "minecraft:full":
            continue
        chunks += 1
        if abs(chunk.get("xPos", 0)) >= 8:
            outpost_chunks += 1
        for section in chunk.get("sections", []):
            biome_palette = section.get("biomes", {}).get("palette", [])
            counts.update(set(x for x in biome_palette if isinstance(x, str) and x in EXPECTED))
            # Informational only: these blocks could come from vanilla worldgen.
            blocks = section.get("block_states", {}).get("palette", [])
            observed_blocks.update(set(
                item.get("Name") for item in blocks if isinstance(item, dict)
                and item.get("Name") in (
                    "minecraft:jungle_log", "minecraft:spruce_log",
                    "minecraft:acacia_log", "minecraft:seagrass",
                    "minecraft:sandstone", "minecraft:oak_leaves"
                )
            ))

missing = sorted(set(EXPECTED) - set(counts))
report = {
    "purpose": "diagnostic checkerboard, NOT natural world or decoration proof",
    "full_chunks": chunks,
    "outpost_full_chunks": outpost_chunks,
    "region_files": len(regions),
    "biome_palette_sections": dict(sorted(counts.items())),
    "informational_block_palette_sections": dict(sorted(observed_blocks.items())),
    "missing_biomes": missing,
}
output = Path("build/apes-gallery-anvil-report.json")
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("APES_GALLERY_SAVED_WORLD", json.dumps(report, sort_keys=True))
# High-ceiling terrain has fewer fully saved spawn chunks under short CI runs.
# Initial spawn-only generation reached 13/16. We must see the saved
# satellite force-loaded site (not just a launch flag) and all 16 biome IDs.
assert chunks >= 40, f"Too few fully generated chunks: {chunks}"
assert outpost_chunks >= 1, "Force-loaded satellite never reached FULL Anvil status"
assert not missing, f"Gallery lacked biome sections for: {missing}"
print("PASS: all nine APES terrestrial categories and seven aquatic/alpine controls have saved chunk biome palettes")
