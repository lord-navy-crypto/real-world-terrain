#!/usr/bin/env python3
"""Anvil WORLD_SURFACE and subsurface scanner for ACTUALLY generated Realism MAX chunks.
This is observational; no claim that natural cliffs never exceed a threshold.
"""
from pathlib import Path
import json,sys,statistics
from realscale_headless_qa import iter_region_chunks,surface_height,get_block,unpack_palette_index

def cave_components(void_voxels):
    """6-face 3D connectivity; a component touching two chunk positions crosses a boundary."""
    remaining=set(void_voxels)
    component_count=0
    big_count=0
    cross_chunk_count=0
    largest=0
    largest_chunk_span=0
    while remaining:
        frontier=[remaining.pop()]
        size=0
        touched_chunks=set()
        while frontier:
            x,y,z=frontier.pop()
            size+=1
            touched_chunks.add((x//16,z//16))
            for neighbor in ((x-1,y,z),(x+1,y,z),(x,y-1,z),
                             (x,y+1,z),(x,y,z-1),(x,y,z+1)):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    frontier.append(neighbor)
        component_count+=1
        big_count+=(size>=64)
        cross_chunk_count+=(size>=16 and len(touched_chunks)>1)
        if size>largest:
            largest=size
            largest_chunk_span=len(touched_chunks)
    return {
        "component_count":component_count,
        "components_at_least_64_voxels":big_count,
        "components_crossing_chunk_boundary":cross_chunk_count,
        "largest_component_voxels":largest,
        "largest_component_chunk_span":largest_chunk_span
    }

def dry_walkable_components(void_voxels, dry_voxels, invalid_floor_voxels=(), sampled_min_y=None):
    """Candidate two-block-high foot cells over supported, non-fluid ground."""
    dry=set(dry_voxels)
    invalid=set(invalid_floor_voxels)
    floor=set()
    for x,y,z in dry:
        if (x,y+1,z) not in dry or (x,y-1,z) in void_voxels or (x,y-1,z) in invalid:
            continue
        if sampled_min_y is not None and y-1<sampled_min_y.get((x,z),y):
            continue
        floor.add((x,y,z))
    remaining=set(floor)
    components=cross=largest=span=0
    while remaining:
        stack=[remaining.pop()]
        size=0
        chunks=set()
        while stack:
            x,y,z=stack.pop()
            size+=1
            chunks.add((x//16,z//16))
            for xx,zz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                for yy in (y-1,y,y+1):
                    candidate=(xx,yy,zz)
                    if candidate in remaining:
                        remaining.remove(candidate)
                        stack.append(candidate)
        components+=1
        if size>=4 and len(chunks)>1:cross+=1
        if size>largest:
            largest=size
            span=len(chunks)
    return {'two_high_grounded_floor_cells':len(floor),
            'walkable_components':components,
            'walkable_components_crossing_chunk_boundaries':cross,
            'largest_walkable_component_floor_cells':largest,
            'largest_walkable_component_chunk_span':span}

def _cave_section_index(chunk):
    """Skip omitted sections: missing data is NOT proof of air underground."""
    sections={}
    for section in chunk.get("sections",[]):
        sy=section.get("Y")
        states=section.get("block_states",{})
        palette=states.get("palette",[])
        if not isinstance(sy,int) or not palette:
            continue
        names=[item.get("Name","?") for item in palette]
        sections[sy]=(names,states.get("data",[]),max(4,(len(names)-1).bit_length()))
    return sections

def scan(root,preset="realism_max"):
    # Each preset has its own Y range and packed WORLD_SURFACE bit width.
    settings_path=(Path(__file__).resolve().parents[2]/
        "neoforge/src/main/resources/data/realscale/worldgen/noise_settings"/
        (preset+".json"))
    noise=json.loads(settings_path.read_text())["noise"]
    min_y=noise["min_y"]
    world_height=noise["height"]
    regions=[x for x in root.glob("run/**/region/r.*.*.mca") if "DIM" not in str(x)]
    assert regions, "No actual saved overworld Anvil region found"
    H={}
    chunks=0
    probes=0
    voids=0
    cave_voxels=set()
    dry_voxels=set()
    lava_voxels=set()
    sampled_min_y={}
    examined_rock_voxels=0
    underground_missing_section_voxels=0
    cave_air_voxels=0
    cave_water_voxels=0
    for region in regions:
        if region.stat().st_size<8192:continue
        for chunk in iter_region_chunks(region):
            chunks+=1
            cx=chunk.get("xPos");cz=chunk.get("zPos")
            if not isinstance(cx,int) or not isinstance(cz,int):continue
            section_index=_cave_section_index(chunk)
            for dz in range(16):
                for dx in range(16):
                    h=surface_height(chunk,dx,dz,min_y,world_height)
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

                    sampled_min_y[(x,z)]=max(-48,h-160)
                    # Full-resolution subsurface cube sampling, not sparse 32m probes.
                    # Stay 20–160 blocks below this column's highest non-air block.
                    # These are real persisted block states, with no extrapolation.
                    for y in range(max(-48,h-160),h-20):
                        section=section_index.get(y//16)
                        if section is None:
                            underground_missing_section_voxels+=1
                            continue
                        names,data,bits=section
                        if len(names)==1:
                            block=names[0]
                        else:
                            packed=(y%16)*256+dz*16+dx
                            entry=unpack_palette_index(data,packed,bits)
                            if entry>=len(names):
                                continue
                            block=names[entry]
                        examined_rock_voxels+=1
                        if block in ("minecraft:lava","minecraft:magma_block"):
                            lava_voxels.add((x,y,z))
                        if block in ("minecraft:air","minecraft:cave_air"):
                            dry_voxels.add((x,y,z))
                        if block in ("minecraft:air","minecraft:cave_air","minecraft:water"):
                            cave_voxels.add((x,y,z))
                            cave_air_voxels+=block in ("minecraft:air","minecraft:cave_air")
                            cave_water_voxels+=block=="minecraft:water"
    delta=[]
    four_boundary=[];inner=[]
    chunk_border=[];within_chunk=[]
    isolated_spikes=0
    for (x,z),h in H.items():
        for nx,nz in ((x+1,z),(x,z+1)):
            if (nx,nz) not in H:continue
            d=abs(h-H[(nx,nz)])
            delta.append(d)
            if (nx%16==0 and nz==z) or (nz%16==0 and nx==x):
                chunk_border.append(d)
            else:
                within_chunk.append(d)
            if (nx%4==0 and nz==z) or (nz%4==0 and nx==x):
                four_boundary.append(d)
            else:inner.append(d)
    # A single-block impulse surrounded on four sides is unlike an extended ridge.
    for (x,z),h in H.items():
        neighbors=[H.get((x-1,z)),H.get((x+1,z)),
                   H.get((x,z-1)),H.get((x,z+1))]
        if all(n is not None for n in neighbors):
            if all(h-n>8 for n in neighbors) or all(n-h>8 for n in neighbors):
                isolated_spikes+=1
    topology=cave_components(cave_voxels)
    walkability=dry_walkable_components(cave_voxels,dry_voxels,lava_voxels,sampled_min_y)
    def pct(values,q):
        a=sorted(values)
        return a[int((len(a)-1)*q)] if a else None
    report={
      "preset":preset,
      "worldgen_min_y":min_y,
      "worldgen_height":world_height,
      "region_files":len(regions),
      "decoded_chunks":chunks,
      "actual_surface_columns":len(H),
      "adjacent_column_pairs":len(delta),
      "height_range":[min(H.values()),max(H.values())] if H else None,
      "adjacent_p50":pct(delta,.5),"adjacent_p95":pct(delta,.95),"adjacent_p99":pct(delta,.99),
      "adjacent_over_8m":sum(d>8 for d in delta),
      "isolated_4_neighbor_spikes_over_8m":isolated_spikes,
      "chunk_border_p95":pct(chunk_border,.95),
      "within_chunk_p95":pct(within_chunk,.95),
      "chunk_border_mean":round(statistics.mean(chunk_border),3) if chunk_border else None,
      "within_chunk_mean":round(statistics.mean(within_chunk),3) if within_chunk else None,
      "four_grid_boundary_mean":round(statistics.mean(four_boundary),3) if four_boundary else None,
      "within_grid_mean":round(statistics.mean(inner),3) if inner else None,
      "subsurface_probes":probes,
      "air_water_cave_probe_count":voids,
      "subsurface_evaluated_voxels":examined_rock_voxels,
      "subsurface_missing_section_voxels":underground_missing_section_voxels,
      "subsurface_air_voxels":cave_air_voxels,
      "subsurface_water_voxels":cave_water_voxels,
      "subsurface_void_fraction":round(len(cave_voxels)/examined_rock_voxels,6) if examined_rock_voxels else None,
      "unusually_high_subsurface_void_fraction":(len(cave_voxels)/examined_rock_voxels>0.15) if examined_rock_voxels else None,
      "cave_connectivity":topology,
      "dry_cave_walkability_candidates":walkability,
      "note":"Measured 20–160m-deep persisted voxels; two-block dry headroom and supported floor measure candidate paths, not proven entrances, daylight access or navigable gameplay."
    }
    (root/"realism_continuity_qa.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
    assert chunks>=30 and len(H)>=500 and len(delta)>=300,"Insufficient actual saved-world sample"
if __name__=="__main__":scan(Path(sys.argv[1]),sys.argv[2] if len(sys.argv)>2 else "realism_max")
