#!/usr/bin/env python3
"""Read ACTUAL generated Special Chongqing Anvil saves.

This is real-block observation, NOT an image preview or static code check.
The dedicated NeoForge CI server must already have created the world.
"""
import base64,gzip,json,math,struct,sys
from pathlib import Path
from realscale_headless_qa import iter_region_chunks,surface_height,get_block

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/"neoforge/src/main/resources/assets/realscale/terrain/chongqing_chaotianmen_dem_v1.properties"

def source_grid():
    props={}
    for line in DATA.read_text().splitlines():
        if line and not line.startswith("#"):
            key,sep,value=line.partition("=")
            if sep:props[key]=value
    values=struct.unpack(">16641h",gzip.decompress(
        base64.b64decode(props["heights_gzip_base64"])))
    return values

def mapped_metres(grid,x,z):
    if not(-4096<=x<=4096 and -4096<=z<=4096):
        return None
    gx=(x+4096)/64
    gz=(z+4096)/64
    col=min(127,math.floor(gx));row=min(127,math.floor(gz))
    u=gx-col;v=gz-row
    a=grid[row*129+col];b=grid[row*129+col+1]
    c=grid[(row+1)*129+col];d=grid[(row+1)*129+col+1]
    return (a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v

def inspect(mod_root):
    grid=source_grid()
    regions=[p for p in mod_root.glob("run/**/region/r.*.*.mca")
             if "DIM" not in str(p)]
    if not regions:raise AssertionError("No real generated world region files")
    samples=[]
    inspected=0
    for region in regions:
        for chunk in iter_region_chunks(region):
            cx=chunk.get("xPos");cz=chunk.get("zPos")
            if not isinstance(cx,int) or not isinstance(cz,int):continue
            world_x=16*cx+8;world_z=16*cz+8
            expected=mapped_metres(grid,world_x,world_z)
            if expected is None:continue
            # Sample only a bounded spread, without treating an un-generated
            # JSON preset as evidence of real saved voxel content.
            inspected+=1
            # Saved region may contain only a narrow band around the
            # requested chunk; modulo-five sampling can MISS ALL samples.
            # Instead inspect the first 140 genuine persisted columns.
            if len(samples)>=140:continue
            observed=surface_height(chunk,8,8,min_y=-64,height=1024)
            if observed is None:continue
            top=get_block(chunk,8,observed,8)
            below=get_block(chunk,8,observed-75,8)
            deep=get_block(chunk,8,120,8)
            # Exact GUI target matches exporter block_y():
            # floor(DEM + 96) + 1, not rounded coarse DEM.
            gui_target_y=math.floor(expected+96)+1
            first_air_y=observed+1
            samples.append({"x":world_x,"z":world_z,
                            "dem_m":round(expected,2),
                            "expected_y":round(expected+96,2),
                            "observed_y":observed,
                            "gui_target_y":gui_target_y,
                            "actual_first_air_y":first_air_y,
                            "gui_target_minus_actual_first_air":gui_target_y-first_air_y,
                            "top":top,"below":below,"deep":deep})
    if inspected<4 or len(samples)<3:
        raise AssertionError(
            f"Insufficient genuine Chongqing Anvil samples: "
            f"{inspected} region chunks in mapped area, "
            f"{len(samples)} saved WORLD_SURFACE columns")
    bad=[s for s in samples
         if abs(s["observed_y"]-s["expected_y"])>12 or
            s["below"]!="minecraft:air" or
            s["deep"]!="minecraft:air" or
            s["top"] not in ("minecraft:grass_block","minecraft:dirt",
                             "minecraft:stone")]
    # A nonzero delta is a REAL WORLD overlay alignment warning even if
    # the older +/-12m DEM-vs-saved-world sanity gate still passes.
    from collections import Counter
    delta_counts=Counter(s["gui_target_minus_actual_first_air"]
                         for s in samples)
    report={"preset":"realscale:special_chongqing_sky_city",
            "gis_overlay_target_delta_histogram":dict(sorted(delta_counts.items())),
            "gis_overlay_exact_y_matches":delta_counts.get(0,0),
            "gis_overlay_placement_y_is_checked_by_client":True,
            "actual_saved_chunks_inspected":inspected,
            "actual_columns":len(samples),
            "real_dem_height_tolerance_blocks":12,
            "pass":not bad,"bad":bad[:6],
            "sample":samples[:20]}
    mod_root.joinpath("chongqing_actual_anvil_qa.json").write_text(
            json.dumps(report,indent=2,ensure_ascii=False))
    print("CHONGQING_ACTUAL_ANVIL_QA",json.dumps({
        "pass":report["pass"],"chunks":inspected,"samples":len(samples),
        "bad":len(bad),
        "gis_overlay_target_deltas":dict(sorted(delta_counts.items()))
    }),flush=True)
    if bad:raise AssertionError("Real Chongqing sky island block or height mismatch")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("usage: script.py <neoforge-dir>")
    inspect(Path(sys.argv[1]).resolve())
