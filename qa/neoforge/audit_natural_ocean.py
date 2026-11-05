#!/usr/bin/env python3
"""Saved-chunk audit of NATURAL oceans; never infer ocean floor from biome ID.

Run with (world path) (X) (Z). Only FULL chunks within 80 blocks of the
naturally located ocean are sampled. Emits evidence and fails on dry deep sea.
"""
import json,sys
from pathlib import Path
from realscale_headless_qa import iter_region_chunks,get_block,unpack_palette_index

world=Path(sys.argv[1]); targetX=int(sys.argv[2]);targetZ=int(sys.argv[3])
def biome_at(chunk,x,y,z):
    section=next((sec for sec in chunk.get("sections",[])
                  if sec.get("Y")==y//16),None)
    if section is None: return None
    biomes=section.get("biomes",{})
    palette=biomes.get("palette",[])
    if len(palette)==1:return palette[0]
    if not palette: return None
    bits=max(1,(len(palette)-1).bit_length())
    ordinal=((y%16)//4)*16+(z//4)*4+(x//4)
    index=unpack_palette_index(biomes.get("data",[]),ordinal,bits)
    return palette[index] if index<len(palette) else None

stats={"target":[targetX,targetZ],"full_chunks":0,"ocean_columns":0,
       "submerged_ocean_columns":0,"dry_ocean_columns":0,
       "deep_ocean_columns":0,"shipwreck_or_ruin_starts":0,
       "ocean_variants":{},"bad_samples":[]}
regions=sorted((world/"region").glob("r.*.*.mca"))
assert regions,"World has no saved Anvil regions"
for region in regions:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status")!="minecraft:full":continue
        cx=chunk.get("xPos");cz=chunk.get("zPos")
        if not isinstance(cx,int) or not isinstance(cz,int):continue
        if abs(cx*16+8-targetX)>80 or abs(cz*16+8-targetZ)>80:continue
        stats["full_chunks"]+=1
        starts=chunk.get("structures",{}).get("starts",{})
        for key,entry in starts.items():
            if any(word in key.lower() for word in ("shipwreck","ruin","monument")):
                if isinstance(entry,dict) and entry.get("id") not in ("INVALID","minecraft:empty"):
                    stats["shipwreck_or_ruin_starts"]+=1
        for lx in (0,4,8,12):
            for lz in (0,4,8,12):
                name=biome_at(chunk,lx,62,lz)
                if not name or not name.startswith("minecraft:") or not name.endswith("ocean"):
                    continue
                stats["ocean_columns"]+=1
                stats["ocean_variants"][name]=stats["ocean_variants"].get(name,0)+1
                if "/deep_" in name or name.split(":")[1].startswith("deep_"):
                    stats["deep_ocean_columns"]+=1
                at_sea=get_block(chunk,lx,62,lz)
                if at_sea in ("minecraft:water","minecraft:kelp",
                              "minecraft:kelp_plant","minecraft:seagrass",
                              "minecraft:tall_seagrass","minecraft:bubble_column"):
                    stats["submerged_ocean_columns"]+=1
                else:
                    stats["dry_ocean_columns"]+=1
                    if len(stats["bad_samples"])<20:
                        stats["bad_samples"].append({
                            "xyz":[cx*16+lx,62,cz*16+lz],
                            "biome":name,"block":at_sea
                        })
report_path=Path("build/natural-ocean-bathymetry.json")
report_path.parent.mkdir(parents=True,exist_ok=True)
if stats["ocean_columns"]:
    stats["submerged_fraction"]=round(stats["submerged_ocean_columns"]/stats["ocean_columns"],4)
report_path.write_text(json.dumps(stats,indent=2)+"\n")
print("NATURAL_OCEAN_BATHYMETRY",json.dumps(stats,sort_keys=True))
assert stats["full_chunks"]>=8,"Too few completed naturally generated ocean chunks"
assert stats["deep_ocean_columns"]>=16,"No natural deep-ocean samples at located target"
assert stats["submerged_fraction"]>=0.85,(
    "High, dry marine terrain: custom density needs biome-coastline reconciliation")
print("PASS: naturally selected deep oceans are mostly submerged and ocean biomes are preserved")
