#!/usr/bin/env python3
"""Anvil WORLD_SURFACE and subsurface scanner for ACTUALLY generated Realism MAX chunks.
This is observational; no claim that natural cliffs never exceed a threshold.
"""
from pathlib import Path
import json,sys,statistics
from realscale_headless_qa import iter_region_chunks,surface_height,get_block

def scan(root):
    regions=[x for x in root.glob("run/**/region/r.*.*.mca") if "DIM" not in str(x)]
    assert regions, "No actual saved overworld Anvil region found"
    H={}
    chunks=0
    probes=0
    voids=0
    for region in regions:
        if region.stat().st_size<8192:continue
        for chunk in iter_region_chunks(region):
            chunks+=1
            cx=chunk.get("xPos");cz=chunk.get("zPos")
            if not isinstance(cx,int) or not isinstance(cz,int):continue
            for dz in range(16):
                for dx in range(16):
                    h=surface_height(chunk,dx,dz,-64,2096)
                    if h is None:continue
                    x=cx*16+dx;z=cz*16+dz
                    H[(x,z)]=h
                    if dx%8==0 and dz%8==0 and h>64:
                        for depth in (32,64,96,144):
                            y=h-depth
                            if -55<y<h-16:
                                probes+=1
                                block=get_block(chunk,dx,y,dz)
                                if block in ("minecraft:air","minecraft:cave_air","minecraft:water"):voids+=1
    delta=[]
    four_boundary=[];inner=[]
    for (x,z),h in H.items():
        for nx,nz in ((x+1,z),(x,z+1)):
            if (nx,nz) not in H:continue
            d=abs(h-H[(nx,nz)])
            delta.append(d)
            if (nx%4==0 and nz==z) or (nz%4==0 and nx==x):
                four_boundary.append(d)
            else:inner.append(d)
    def pct(values,q):
        a=sorted(values)
        return a[int((len(a)-1)*q)] if a else None
    report={
      "region_files":len(regions),
      "decoded_chunks":chunks,
      "actual_surface_columns":len(H),
      "adjacent_column_pairs":len(delta),
      "height_range":[min(H.values()),max(H.values())] if H else None,
      "adjacent_p50":pct(delta,.5),"adjacent_p95":pct(delta,.95),"adjacent_p99":pct(delta,.99),
      "adjacent_over_8m":sum(d>8 for d in delta),
      "four_grid_boundary_mean":round(statistics.mean(four_boundary),3) if four_boundary else None,
      "within_grid_mean":round(statistics.mean(inner),3) if inner else None,
      "subsurface_probes":probes,
      "air_water_cave_probe_count":voids,
      "note":"Measured block surfaces, not continuous field derivatives. No cave morphology proof without visual/3D connected-component analysis."
    }
    (root/"realism_continuity_qa.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
    assert chunks>=30 and len(H)>=500 and len(delta)>=300,"Insufficient actual saved-world sample"
if __name__=="__main__":scan(Path(sys.argv[1]))
