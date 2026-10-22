#!/usr/bin/env python3
"""Actual saved-Anvil ocean ecology COVERAGE audit, not proof-by-seed-name.

Read only completed Java 1.21.1 Minecraft regions. Count actual warm/cold
biome palette samples, coral/seagrass hardground blocks and log-reported
feature attempts and successes. Expected vanilla seed descriptions are NOT
treated as evidence: RealScale may remap biomes and changes sea geometry.
"""
import json,re,sys
from collections import Counter
from pathlib import Path
from realscale_headless_qa import iter_region_chunks,get_block,unpack_palette_index

world=Path(sys.argv[1])
serverlog=Path(sys.argv[2])
out=Path(sys.argv[3])
seed=sys.argv[4]

biome_categories={
 "cold": {"minecraft:cold_ocean","minecraft:deep_cold_ocean",
          "minecraft:frozen_ocean","minecraft:deep_frozen_ocean",
          "minecraft:deep_ocean"},
 "warm": {"minecraft:warm_ocean"},
 "kelp": {"minecraft:lukewarm_ocean","minecraft:deep_lukewarm_ocean",
          "minecraft:deep_ocean","minecraft:cold_ocean",
          "minecraft:deep_cold_ocean"}
}
blocks_interest={
 "minecraft:dead_brain_coral_fan",
 "minecraft:dead_horn_coral_fan",
 "minecraft:brain_coral_fan",
 "minecraft:horn_coral_fan",
 "minecraft:tube_coral_fan",
 "minecraft:bubble_coral_fan",
 "minecraft:fire_coral_fan",
 "minecraft:sea_pickle",
 "minecraft:kelp",
 "minecraft:kelp_plant",
 "minecraft:seagrass",
 "minecraft:calcite",
 "minecraft:tuff",
}
wet={"minecraft:water","minecraft:kelp","minecraft:kelp_plant",
     "minecraft:seagrass","minecraft:tall_seagrass","minecraft:bubble_column",
     "minecraft:sea_pickle"} | {
     name for name in blocks_interest if "coral_fan" in name}
counters=Counter()
biomes=Counter()
block_palettes=Counter()
sampled_full=0
ocean_columns=0
wet_columns=0
dry_examples=[]
regions=sorted((world/"region").glob("r.*.*.mca"))
assert regions,"Marine coverage audit has no actual saved Anvil region files"

def sampled_biome(chunk,lx,y,lz):
    section=next((s for s in chunk.get("sections",[]) if s.get("Y")==y//16),None)
    if not section:return None
    palette=section.get("biomes",{}).get("palette",[])
    if not palette:return None
    if len(palette)==1:return palette[0]
    bits=max(1,(len(palette)-1).bit_length())
    index=((y%16)//4)*16+(lz//4)*4+lx//4
    data=section.get("biomes",{}).get("data",[])
    ordinal=unpack_palette_index(data,index,bits)
    return palette[ordinal] if ordinal<len(palette) else None

for region in regions:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status")!="minecraft:full":continue
        sampled_full+=1
        for sec in chunk.get("sections",[]):
            for item in sec.get("block_states",{}).get("palette",[]):
                name=item.get("Name")
                if name in blocks_interest:
                    block_palettes[name]+=1
        for x in (0,4,8,12):
            for z in (0,4,8,12):
                name=sampled_biome(chunk,x,62,z)
                if not name:continue
                biomes[name]+=1
                if name.startswith("minecraft:") and name.endswith("ocean"):
                    ocean_columns+=1
                    actual=get_block(chunk,x,62,z)
                    if actual in wet:wet_columns+=1
                    elif len(dry_examples)<10:
                        dry_examples.append({
                            "x":chunk.get("xPos",0)*16+x,
                            "z":chunk.get("zPos",0)*16+z,
                            "biome":name,"block":actual})
for category,values in biome_categories.items():
    counters[category+"_ocean_columns"]=sum(biomes[n] for n in values)
trace=serverlog.read_text(encoding="utf8",errors="replace")
matches=re.findall(r"REALSCALE_CAVE_MARINE_HABITATS ([^\r\n]+)",trace)
assert matches,"No actual Feature shutdown counters found; cannot claim test coverage"
attempts={}
for item in matches[-1].split():
    m=re.fullmatch(r"([A-Z_]+)=(\d+):(\d+)",item)
    if not m:continue
    name,called,placed=m.group(1),int(m.group(2)),int(m.group(3))
    assert called>=placed>=0,(name,called,placed)
    attempts[name]={"attempts":called,"successful_calls":placed}
for category in ("COLD_WATER_REEF","WARM_REEF_GARDEN","KELP_MEADOW"):
    assert category in attempts,"Missing worldgen Feature provenance: "+category
report={
 "seed":str(seed),
 "region_files":len(regions),"complete_chunks":sampled_full,
 "biome_columns_y62":dict(sorted(biomes.items())),
 "marine_biome_coverage":dict(counters),
 "marine_feature_attempts_and_successes":attempts,
 "interest_block_palette_sections":dict(sorted(block_palettes.items())),
 "ocean_columns":ocean_columns,
 "wet_ocean_columns_y62":wet_columns,
 "wet_fraction_y62":round(wet_columns/ocean_columns,5) if ocean_columns else None,
 "dry_ocean_examples":dry_examples,
 "coverage_conclusion":{
     "cold_feature_confirmed":attempts["COLD_WATER_REEF"]["successful_calls"]>0,
     "warm_feature_confirmed":attempts["WARM_REEF_GARDEN"]["successful_calls"]>0,
     "kelp_feature_confirmed":attempts["KELP_MEADOW"]["successful_calls"]>0,
     "cave_biome_coverage_is_not_ocean_coverage":True
 }
}
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
print("REALSCALE_TARGETED_MARINE_ECOLOGY_COVERAGE",json.dumps(report,sort_keys=True),flush=True)
assert sampled_full>=8,"Insufficient actually completed Minecraft world chunks"
if ocean_columns>=16:
    assert wet_columns/ocean_columns>=0.85,(
        "Generated ocean biome has too many missing water columns",
        wet_columns,ocean_columns,dry_examples)
print("REALSCALE_TARGETED_MARINE_OCEAN_INTEGRITY_PASS",flush=True)
