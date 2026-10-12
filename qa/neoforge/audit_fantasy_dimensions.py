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
    outer_end_solid_samples=0
    outer_end_air_samples=0
    empty_section_voxels=0
    invalid_palette_indices=0
    center_chunks=0
    chunk_coordinates=set()
    for file in files:
        for chunk in iter_region_chunks(file):
            chunks+=1
            x=int(chunk.get("xPos",0));z=int(chunk.get("zPos",0))
            chunk_coordinates.add((x,z))
            far=(dimension=="end" and (x*x+z*z)*256>=1920*1920)
            if far:far_chunks+=1
            if dimension=="end" and x*x+z*z<=16*16:center_chunks+=1
            sampled_sections={int(section.get("Y")) for section in chunk.get("sections",[])
                              if isinstance(section.get("Y"),int)
                              and section.get("block_states",{}).get("palette")}
            highest=False
            for dx in (1,5,9,13):
                for dz in (1,5,9,13):
                    for y in range(40,248,8):
                        if dimension=="nether":
                            band=("lower" if y<96 else "middle" if y<160
                                  else "upper" if y<224 else "roof")
                        else:
                            band=("lower" if y<128 else "high" if y<200 else "sky")
                        group=totals[band]
                        group["samples"]+=1
                        if y//16 not in sampled_sections:
                            empty_section_voxels+=1
                            group["omitted_sections"]+=1
                            continue
                        state=get_block(chunk,dx,y,dz)
                        if state=="INVALID_PALETTE_INDEX":
                            invalid_palette_indices+=1
                            continue
                        if state in ("minecraft:air","minecraft:cave_air","minecraft:void_air"):
                            group["air"]+=1
                            if far:outer_end_air_samples+=1
                        elif state in ("minecraft:lava","minecraft:water"):
                            group["liquid"]+=1
                        else:
                            group["solid"]+=1
                            if far:outer_end_solid_samples+=1
                            if dimension=="end" and y>=200:highest=True
            high_columns+=int(highest)
    output={"anvil_region_files":len(files),"decoded_chunks":chunks,
            "distinct_chunk_positions":len(chunk_coordinates),
            "skipped_omitted_section_voxels":empty_section_voxels,
            "invalid_palette_indices":invalid_palette_indices,
            "observed_bands":dict(totals)}
    if dimension=="end":
        output["outer_chunks_beyond_1920_blocks"]=far_chunks
        output["central_end_chunks"]=center_chunks
        output["outer_end_solid_samples"]=outer_end_solid_samples
        output["outer_end_air_samples"]=outer_end_air_samples
        output["sampled_chunks_with_sky_solids"]=high_columns
    return output

report={"nether":collect("nether"),"end":collect("end"),
        "note":"Only sampled persisted voxels. Does not guarantee a cavern network, islands on unsampled coords, structures or portal safety."}
print(json.dumps(report,indent=2))
(ROOT/"fantasy_dimensions_anvil_qa.json").write_text(json.dumps(report,indent=2)+"\n")
for dim in ("nether","end"):
    if report[dim]["decoded_chunks"]==0:
        raise SystemExit("No saved "+dim+" Anvil chunks; fantasy dimension generation UNVERIFIED")
    if not any(r["solid"] or r["air"] or r["liquid"]
               for r in report[dim]["observed_bands"].values()):
        raise SystemExit("No actual saved block state samples in "+dim)
    if report[dim]["invalid_palette_indices"]:
        raise SystemExit("Invalid persisted block indices in "+dim)
if report["end"]["outer_chunks_beyond_1920_blocks"]<4:
    raise SystemExit("Outer End archipelago needs multiple distant generated samples")
if report["end"]["central_end_chunks"]==0:
    raise SystemExit("The preserved End dragon-center area was never sampled")
if report["end"]["outer_end_solid_samples"]==0:
    raise SystemExit("Outer End has no saved solid blocks in sampled regions; island terrain UNVERIFIED")
if report["nether"]["decoded_chunks"]<2:
    raise SystemExit("Multiple actual Nether chunks required for cavern check")
print("FANTASY_DIMENSIONS_SAVED_ANVIL_OBSERVATION: PASS (not visual/gameplay QA)")
