#!/usr/bin/env python3
"""Decode ACTUAL saved Minecraft Anvil to prove RealScale custom cave biome IDs.

Arguments: a runServer checkout's NeoForge root directory.
Every region that belongs to an Overworld level.dat is read. Merely
registering JSON/codec cannot pass if the game never selects those IDs.
"""
import json,sys
from pathlib import Path
from collections import Counter
from realscale_headless_qa import iter_region_chunks

VALID={"realscale:"+x for x in (
    "damp_limestone_caverns","deep_fungal_caverns","dry_karst_caves",
    "underground_springs","volcanic_caverns","glacial_subsurface",
    "glowing_moss_grottos","fossil_limestone_caves")}
root=Path(sys.argv[1])
files=[]
for region in root.glob("run/**/region/r.*.*.mca"):
    # Exclude Nether and End region folders.
    if region.parent.parent.joinpath("level.dat").is_file():
        files.append(region)
assert files,"No saved Overworld Anvil regions"
counts=Counter()
full_chunks=0
for region in files:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status")!="minecraft:full":continue
        full_chunks+=1
        for section in chunk.get("sections",[]):
            for biome in section.get("biomes",{}).get("palette",[]):
                counts[biome]+=1
evidence={name:counts[name] for name in sorted(VALID) if counts[name]>0}
print("REALSCALE_ACTUAL_CAVE_BIOME_ANVIL",json.dumps({
    "full_chunks":full_chunks,"region_files":len(files),
    "custom_cave_palette_counts":evidence,
    "vanilla_deep_dark_palette_counts":counts.get("minecraft:deep_dark",0),
    "vanilla_ocean_palette_counts":sum(counts[x] for x in counts if x.endswith("_ocean") or x=="minecraft:ocean")
},sort_keys=True),flush=True)
assert full_chunks>=15,"Insufficient persisted completed world chunks"
assert evidence,"No custom cave biomes in ACTUAL saved Minecraft chunks"
if "--require-new" in sys.argv:
    new_ids=("realscale:glowing_moss_grottos",
             "realscale:fossil_limestone_caves")
    assert any(counts[name]>0 for name in new_ids),(
        "New glow/fossil cave biomes did not reach real saved Anvil sections",
        {name:counts[name] for name in new_ids})
    print("REALSCALE_NEW_CAVE_BIOME_ANVIL_PASS",{
        name:counts[name] for name in new_ids if counts[name]>0},flush=True)
print("REALSCALE_REAL_CAVE_BIOME_ANVIL_PASS",flush=True)
