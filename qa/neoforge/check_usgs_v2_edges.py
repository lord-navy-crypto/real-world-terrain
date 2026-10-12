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
    regions=[p for p in root.glob("run/**/region/r.*.*.mca") if "DIM" not in str(p)]
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
                    if not (-1024<=x<=1024 and -128<=z<=128):continue
                    h=surface_height(chunk,dx,dz,-64,2096)
                    if h is not None:columns[(x,z)]=h
    interior=[abs(y-source(x,z)) for (x,z),y in columns.items()
              if -60<=x<=60 and -60<=z<=60]
    borders=[]
    internal=[]
    samples_by_side={s:0 for s in ("west","east","north","south")}
    for (x,z),y in columns.items():
        for nx,nz in ((x+1,z),(x,z+1)):
            other=columns.get((nx,nz))
            if other is None:continue
            step=abs(other-y)
            if x in (-241,-240,239,240) or z in (-241,-240,239,240):
                borders.append(step)
                if x<0:samples_by_side["west"]+=1
                if x>=0:samples_by_side["east"]+=1
                if z<0:samples_by_side["north"]+=1
                if z>=0:samples_by_side["south"]+=1
            elif abs(x)<=384 and abs(z)<=384:
                internal.append(step)
    report={
        "saved_region_files":len(regions),"decoded_chunks":n,
        "saved_column_samples":len(columns),
        "measured_interior_samples":len(interior),
        "measured_interior_median_abs_error":round(statistics.median(interior),3) if interior else None,
        "measured_interior_p95_abs_error":p95(interior),
        "dem_edge_adjacent_pairs":len(borders),
        "edge_step_p95":p95(borders),
        "edge_step_max":max(borders) if borders else None,
        "nearby_inner_step_p95":p95(internal),
        "sampled_edge_sides":samples_by_side,
        "note":"Sparse saved-chunk audit; visuals, biomes and distant regions require user testing."
    }
    (root/"usgs_v2_edge_qa.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
    if len(interior)<32 or len(borders)<32:
        raise SystemExit("Insufficient USGS interior/border samples; generation unverified")
    if report["measured_interior_p95_abs_error"]>25:
        raise SystemExit("Measured DEM terrain deviates excessively from source heights")
    if report["edge_step_p95"]>32 or report["edge_step_max"]>96:
        raise SystemExit("USGS boundary has an implausible discontinuity")

if __name__=="__main__":
    scan(Path(sys.argv[1]) if len(sys.argv)>1 else Path("."))
