#!/usr/bin/env python3
"""Observe actually generated Nether/End Anvil chunks for fantasy worldgen.

A boot alone does not prove that layered caverns or sky islands appeared.
Output is an observational report; sparse samples are explicitly not a
universal pass on terrain quality or End City/dragon/gameplay compatibility.
"""
from pathlib import Path
import json,sys
from collections import defaultdict
from realscale_headless_qa import iter_region_chunks,get_block

ROOT=Path(sys.argv[1]) if len(sys.argv)>1 else Path(".")

def collect(dimension):
    files=list(ROOT.rglob("DIM-1/region/*.mca" if dimension=="nether"
                         else "DIM1/region/*.mca"))
    totals=defaultdict(lambda:{"samples":0,"solid":0,"air":0,"liquid":0})
    chunks=0
    high_columns=0
    far_chunks=0
    for file in files:
        for chunk in iter_region_chunks(file):
            chunks+=1
            x=int(chunk.get("xPos",0));z=int(chunk.get("zPos",0))
            if dimension=="end" and (x*x+z*z)*256>=1920*1920:
                far_chunks+=1
            highest=False
            for dx in (1,5,9,13):
                for dz in (1,5,9,13):
                    for y in range(40,248,8):
                        if dimension=="nether":
                            band=("lower" if y<96 else "middle" if y<160
                                  else "upper" if y<224 else "roof")
                        else:
                            band=("lower" if y<128 else "high" if y<200 else "sky")
                        state=get_block(chunk,dx,y,dz)
                        group=totals[band]
                        group["samples"]+=1
                        if state in ("minecraft:air","minecraft:cave_air","minecraft:void_air"):
                            group["air"]+=1
                        elif state in ("minecraft:lava","minecraft:water"):
                            group["liquid"]+=1
                        else:
                            group["solid"]+=1
                            if dimension=="end" and y>=200:highest=True
            high_columns+=int(highest)
    output={"anvil_region_files":len(files),"decoded_chunks":chunks,
            "observed_bands":dict(totals)}
    if dimension=="end":
        output["outer_chunks_beyond_1920_blocks"]=far_chunks
        output["sampled_chunks_with_sky_solids"]=high_columns
    return output

report={"nether":collect("nether"),"end":collect("end"),
        "note":"Only sampled persisted voxels. Does not guarantee a cavern network, islands on unsampled coords, structures or portal safety."}
print(json.dumps(report,indent=2))
(ROOT/"fantasy_dimensions_anvil_qa.json").write_text(json.dumps(report,indent=2)+"\n")
for dim in ("nether","end"):
    if report[dim]["decoded_chunks"]==0:
        raise SystemExit("No saved "+dim+" Anvil chunks; fantasy dimension generation UNVERIFIED")
    if not any(r["samples"] for r in report[dim]["observed_bands"].values()):
        raise SystemExit("No actual voxels read in "+dim)
if report["end"]["outer_chunks_beyond_1920_blocks"]==0:
    raise SystemExit("Outer End archipelago was not sampled: insufficient chunk generation")
print("FANTASY_DIMENSIONS_SAVED_ANVIL_OBSERVATION: PASS (not visual/gameplay QA)")
