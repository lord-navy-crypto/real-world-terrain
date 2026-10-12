#!/usr/bin/env python3
"""Real saved-chunk QA across the repaired USGS Big Sur DEM boundary.

Measures observed Anvil heights, not a predicted synthetic height field.
Tests the old center source and the newly generated area around +/-240 m.
"""
import json,sys,statistics
from pathlib import Path
from realscale_headless_qa import iter_region_chunks,surface_height
from check_actual_chunk_heights import read_dem

def p95(values):
    return round(sorted(values)[int(.95*(len(values)-1))],3) if values else None

def scan(root):
    resources=root/"src/main/resources/data/realscale/dem"
    src=next(resources.glob("*.rgt.gz"))
    source,bounds=read_dem(src)
    regions=[p for p in root.glob("run/**/region/r.*.*.mca") if "DIM" not in str(p) and p.stat().st_size>=8192]
    assert regions,"No saved Overworld Anvil regions"
    columns={}
    n=0
    for path in regions:
        for chunk in iter_region_chunks(path):
            n+=1
            cx=chunk.get("xPos")
            cz=chunk.get("zPos")
            if not isinstance(cx,int) or not isinstance(cz,int):continue
            for dz in range(16):
                for dx in range(16):
                    x=cx*16+dx;z=cz*16+dz
                    if not (-2400<=x<=2400 and -2400<=z<=2400):continue
                    h=surface_height(chunk,dx,dz,-64,2096)
                    if h is not None:columns[(x,z)]=h
    interior=[abs(y-source(x,z)) for (x,z),y in columns.items()
              if -60<=x<=60 and -60<=z<=60]
    coverage=[abs(y-source(x,z)) for (x,z),y in columns.items()
              if -239<=x<=239 and -239<=z<=239]
    fallback_columns=[(x,z) for x,z in columns if (x<=-2320 or x>=2320) and abs(z)<=32]
    mid_transition_columns=[(x,z) for x,z in columns if 864<=abs(x)<=928 and abs(z)<=32]
    near_edge_columns=[y for (x,z),y in columns.items()
                       if abs(x)<=288 and abs(z)<=288 and (abs(x)>=224 or abs(z)>=224)]
    near_edge_to_floor=sum(y<=-60 for y in near_edge_columns)
    seam_residuals=[]
    nonseam_residuals=[]
    borders=[]
    internal=[]
    worst_adjacent=[]
    directional={side:[] for side in ('west','east','north','south')}
    samples_by_side={s:0 for s in ("west","east","north","south")}
    for (x,z),y in columns.items():
        for nx,nz in ((x+1,z),(x,z+1)):
            other=columns.get((nx,nz))
            if other is None:continue
            step=abs(other-y)
            if abs(x)<=384 and abs(z)<=384 and step>=32:
                worst_adjacent.append({"x":x,"z":z,"nx":nx,"nz":nz,"step":step,"from_y":y,"to_y":other})
            if -239<=x<=239 and -239<=z<=239 and -239<=nx<=239 and -239<=nz<=239:
                expected_step=source(nx,nz)-source(x,z)
                residual=abs((other-y)-expected_step)
                if (nx==x+1 and x%16==15) or (nz==z+1 and z%16==15):
                    seam_residuals.append(residual)
                else:
                    nonseam_residuals.append(residual)
            side=None
            if nz==z and x==-241 and -64<=z<=64:side="west"
            elif nz==z and x==239 and -64<=z<=64:side="east"
            elif nx==x and z==-241 and -64<=x<=64:side="north"
            elif nx==x and z==239 and -64<=x<=64:side="south"
            if side is not None:
                borders.append(step)
                directional[side].append(step)
                samples_by_side[side]+=1
            elif abs(x)<=384 and abs(z)<=384:
                internal.append(step)
    report={
        "saved_region_files":len(regions),"decoded_chunks":n,
        "saved_column_samples":len(columns),
        "measured_interior_samples":len(interior),
        "measured_interior_median_abs_error":round(statistics.median(interior),3) if interior else None,
        "measured_interior_p95_abs_error":p95(interior),
        "dem_full_tile_samples":len(coverage),
        "outer_unmodified_fallback_samples":len(fallback_columns),
        "middle_2048m_transition_samples":len(mid_transition_columns),
        "near_edge_columns":len(near_edge_columns),
        "near_edge_void_floor_columns":near_edge_to_floor,
        "worst_adjacent_steps_near_dem":sorted(worst_adjacent,key=lambda x:-x["step"])[:16],
        "dem_full_tile_p95_abs_error":p95(coverage),
        "chunk_seam_pair_samples":len(seam_residuals),
        "chunk_seam_residual_p95":p95(seam_residuals),
        "nonseam_residual_p95":p95(nonseam_residuals),
        "dem_edge_adjacent_pairs":len(borders),
        "edge_step_p95":p95(borders),
        "edge_step_max":max(borders) if borders else None,
        "nearby_inner_step_p95":p95(internal),
        "sampled_edge_sides":samples_by_side,
        "edge_step_p95_by_side":{side:p95(values) for side,values in directional.items()},
        "note":"Sparse saved-chunk audit; visuals, biomes and distant regions require user testing."
    }
    (root/"usgs_v2_edge_qa.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
    if len(interior)<32 or len(borders)<32 or any(n<8 for n in samples_by_side.values()):
        raise SystemExit("Insufficient USGS interior or four-sided boundary samples; generation unverified")
    if report["measured_interior_p95_abs_error"]>25:
        raise SystemExit("Measured DEM terrain deviates excessively from source heights")
    if len(fallback_columns)<24 or len(mid_transition_columns)<24:
        raise SystemExit("USGS v2 2048m blend midpoint and procedural fallback were not both generated")
    if near_edge_to_floor:
        raise SystemExit("USGS tile edge unexpectedly descends to the world floor; vertical chasm remains")
    if len(coverage)<300 or p95(coverage)>25:
        raise SystemExit("Not enough full tile coverage or DEM elevation disagreement")
    if len(seam_residuals)<32 or len(nonseam_residuals)<100:
        raise SystemExit("Insufficient observed chunk seams for DEM continuity analysis")
    if p95(seam_residuals)>18 or p95(seam_residuals)>max(8,2*p95(nonseam_residuals)+4):
        raise SystemExit("Minecraft chunk seam diverges from expected local DEM slope")
    if report["edge_step_p95"]>8 or report["edge_step_max"]>48:
        raise SystemExit("USGS boundary contains implausibly steep single-block steps")

if __name__=="__main__":
    scan(Path(sys.argv[1]) if len(sys.argv)>1 else Path("."))
