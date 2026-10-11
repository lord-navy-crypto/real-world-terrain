#!/usr/bin/env python3
"""Anvil WORLD_SURFACE and subsurface scanner for ACTUALLY generated Realism MAX chunks.
This is observational; no claim that natural cliffs never exceed a threshold.
"""
from pathlib import Path
import json,sys,statistics
from realscale_headless_qa import iter_region_chunks,surface_height,get_block,unpack_palette_index,read_nbt
import gzip

def terrain_quality_gates(chunk_border_deltas, within_chunk_deltas,
                          grid_boundary_deltas, inner_grid_deltas,
                          isolated_spikes, sample_columns, high_nonriver_water):
    """Detect artificial chunk seams, grid seams, solitary block towers and floods.

    Wide thresholds recognize natural cliffs and rainfall-fed inland lakes.
    They are NOT a guarantee of pretty or geologically accurate terrain.
    """
    def ratio(a,b):
        if not a or not b:return None
        return round(statistics.mean(a)/max(0.01,statistics.mean(b)),6)
    chunk_ratio=ratio(chunk_border_deltas,within_chunk_deltas)
    grid_ratio=ratio(grid_boundary_deltas,inner_grid_deltas)
    inland_wet=high_nonriver_water/max(1,sample_columns)
    issues=[]
    if chunk_ratio is not None and len(chunk_border_deltas)>500 and len(within_chunk_deltas)>500:
        if statistics.mean(chunk_border_deltas)>statistics.mean(within_chunk_deltas)*1.6+0.5:
            issues.append("Systematic 16-block chunk-border height seam")
    if grid_ratio is not None and len(grid_boundary_deltas)>500 and len(inner_grid_deltas)>500:
        if statistics.mean(grid_boundary_deltas)>statistics.mean(inner_grid_deltas)*1.6+0.5:
            issues.append("Systematic 4-block interpolation-grid height seam")
    if sample_columns>=500 and isolated_spikes>max(4,sample_columns//2500):
        issues.append("Excess isolated 4-neighbor spikes or one-block pits")
    if sample_columns>=500 and inland_wet>0.08:
        issues.append("Unexpected widespread high-altitude water outside River biomes")
    return {
      "chunk_border_mean_ratio":chunk_ratio,
      "four_grid_boundary_mean_ratio":grid_ratio,
      "isolated_spikes":isolated_spikes,
      "high_nonriver_water_columns":high_nonriver_water,
      "high_nonriver_water_fraction":round(inland_wet,6),
      "issues":issues,
      "pass":not issues
    }


def spawn_water_audit(spawn, heights, surface_blocks, surface_biomes, sea_level=63, radius=96):
    """Inspect ACTUAL spawn and nearby persisted surface columns; no guesswork.

    A watery spawn is not rescued by the generator merely booting.
    Unloaded spawn coordinates are explicitly UNKNOWN, never assumed safe.
    """
    if spawn is None:
        return {"spawn_known":False, "spawn_sampled":False, "spawn_unverified":True}
    sx,sy,sz=spawn
    center=(sx,sz)
    candidates=[pos for pos in heights
                if (pos[0]-sx)**2+(pos[1]-sz)**2<=radius**2]
    watery={"minecraft:water","minecraft:bubble_column","minecraft:kelp",
            "minecraft:kelp_plant","minecraft:seagrass","minecraft:tall_seagrass"}
    def ocean(biome):
        return isinstance(biome,str) and "ocean" in biome
    n=len(candidates)
    liquid=sum(surface_blocks.get(pos) in watery for pos in candidates)
    ocean_n=sum(ocean(surface_biomes.get(pos)) for pos in candidates)
    sampled=center in heights
    center_block=surface_blocks.get(center)
    center_biome=surface_biomes.get(center)
    center_liquid=center_block in watery
    center_ocean=ocean(center_biome)
    frac=liquid/n if n else None
    # A tiny island surrounded by water is not a desirable start either.
    majority_water=(n>=100 and frac>0.60)
    return {
      "spawn_known":True,"spawn_sampled":sampled,"spawn_unverified":not sampled,
      "spawn_x":sx,"spawn_y":sy,"spawn_z":sz,
      "spawn_surface_y":heights.get(center),
      "spawn_surface_block":center_block,
      "spawn_surface_biome":center_biome,
      "spawn_in_ocean_biome":center_ocean,
      "spawn_in_liquid":center_liquid,
      "nearby_radius_blocks":radius,
      "nearby_sampled_columns":n,
      "nearby_surface_liquid_fraction":round(frac,6) if frac is not None else None,
      "nearby_ocean_biome_fraction":round(ocean_n/n,6) if n else None,
      "nearby_majority_water":majority_water,
      "unsafe_ocean_spawn":bool(sampled and
            (center_liquid or center_ocean or majority_water)),
      "note":"Saved world spawn coordinate and saved overworld blocks only; not a client respawn, no claim about unexplored chunks."
    }


def saved_surface_biome(chunk,dx,y,dz):
    """Decode the actual 4x4x4 section-biome palette, not biome guesses."""
    section=next((x for x in chunk.get("sections",[]) if x.get("Y")==y//16),None)
    if not section:return None
    biomes=section.get("biomes",{})
    palette=biomes.get("palette",[])
    if not palette:return None
    if len(palette)==1:return palette[0]
    index=((y%16)//4)*16+(dz//4)*4+(dx//4)
    bits=max(1,(len(palette)-1).bit_length())
    idx=unpack_palette_index(biomes.get("data",[]),index,bits)
    return palette[idx] if idx<len(palette) else None


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
    surface_blocks={}
    surface_biomes={}
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
    surface_fluid_water=0
    surface_fluid_lava=0
    surface_under_sea=0
    surface_block_missing=0
    surface_biome_counts={}
    river_biome_columns=0
    river_water_columns=0
    river_ice_surface_columns=0
    river_water_below_ice_columns=0
    high_elevation_river_water_columns=0
    high_elevation_nonriver_water_columns=0
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
                    surface_under_sea+=h<=62
                    upper_block=get_block(chunk,dx,h,dz)
                    surface_blocks[(x,z)]=upper_block
                    surface_fluid_water+=upper_block=="minecraft:water"
                    surface_fluid_lava+=upper_block=="minecraft:lava"
                    surface_block_missing+=upper_block=="INVALID_PALETTE_INDEX"
                    biome=saved_surface_biome(chunk,dx,h,dz)
                    surface_biomes[(x,z)]=biome
                    if biome:
                        surface_biome_counts[biome]=surface_biome_counts.get(biome,0)+1
                    if biome not in ("minecraft:river","minecraft:frozen_river"):
                        high_elevation_nonriver_water_columns += (
                            h > 90 and upper_block in ("minecraft:water","minecraft:ice")
                        )
                    if biome in ("minecraft:river","minecraft:frozen_river"):
                        river_biome_columns+=1
                        wet=upper_block in ("minecraft:water","minecraft:ice")
                        river_water_columns+=wet
                        ice=(upper_block=="minecraft:ice")
                        river_ice_surface_columns+=ice
                        river_water_below_ice_columns+=(ice and get_block(chunk,dx,h-1,dz)=="minecraft:water")
                        high_elevation_river_water_columns+=(wet and h>70)
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
    world_spawn=None
    spawn_file=None
    for level_file in root.glob("run/**/level.dat"):
        if "DIM" in str(level_file):continue
        try:
            root_nbt=read_nbt(gzip.decompress(level_file.read_bytes()))
            data=root_nbt.get("Data",root_nbt)
            gen=(data.get("WorldGenSettings",{}).get("dimensions",{})
                 .get("minecraft:overworld",{}).get("generator",{}))
            if gen.get("settings")!=f"realscale:{preset}":
                continue
            if all(isinstance(data.get(k),int) for k in ("SpawnX","SpawnY","SpawnZ")):
                world_spawn=(data["SpawnX"],data["SpawnY"],data["SpawnZ"])
                spawn_file=str(level_file)
                break
        except Exception as ex:
            print("Could not read world spawn from",level_file,ex)
    spawn_report=spawn_water_audit(world_spawn,H,surface_blocks,surface_biomes)
    topology=cave_components(cave_voxels)
    walkability=dry_walkable_components(cave_voxels,dry_voxels,lava_voxels,sampled_min_y)
    quality=terrain_quality_gates(chunk_border,within_chunk,four_boundary,inner,
                                  isolated_spikes,len(H),high_elevation_nonriver_water_columns)
    def pct(values,q):
        a=sorted(values)
        return a[int((len(a)-1)*q)] if a else None
    report={
      "preset":preset,
      "surface_quality_gates":quality,
      "spawn_level_dat":spawn_file,
      "spawn_surface_audit":spawn_report,
      "ocean_biome_surface_columns":sum(
        isinstance(b,str) and "ocean" in b for b in surface_biomes.values()),
      "worldgen_min_y":min_y,
      "worldgen_height":world_height,
      "region_files":len(regions),
      "decoded_chunks":chunks,
      "actual_surface_columns":len(H),
      "adjacent_column_pairs":len(delta),
      "height_range":[min(H.values()),max(H.values())] if H else None,
      "height_p05":pct(list(H.values()),.05),
      "height_p50":pct(list(H.values()),.5),
      "height_p95":pct(list(H.values()),.95),
      "configured_sea_level":json.loads(settings_path.read_text())["sea_level"],
      "surface_biome_counts":surface_biome_counts,
      "river_biome_columns":river_biome_columns,
      "river_water_or_ice_columns":river_water_columns,
      "river_surface_ice_columns":river_ice_surface_columns,
      "river_ice_with_water_beneath_columns":river_water_below_ice_columns,
      "river_water_or_ice_above_y70":high_elevation_river_water_columns,
      "river_wet_fraction":round(river_water_columns/river_biome_columns,6) if river_biome_columns else None,
      "river_biome_dry_warning":river_biome_columns>100 and river_water_columns==0,
      "surface_water_columns":surface_fluid_water,
      "surface_water_fraction":round(surface_fluid_water/len(H),6) if H else None,
      "surface_lava_columns":surface_fluid_lava,
      "surface_below_sea_level_columns":surface_under_sea,
      "bad_surface_palette_indices":surface_block_missing,
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
    assert surface_block_missing==0,"Invalid Anvil block palette indices"
    assert quality["pass"],"Terrain grid/flood regression: "+str(quality["issues"])
    assert spawn_report["spawn_known"],"Missing saved overworld spawn coordinates (cannot verify spawn)"
    assert spawn_report["spawn_sampled"],"Saved spawn chunk absent from inspected world (cannot verify spawn)"
    assert not spawn_report["unsafe_ocean_spawn"], (
        f"Unsafe water/ocean world spawn for {preset}: {spawn_report}"
    )
    # Streams must not silently disappear again when the biome itself is
    # present in a substantial sample. This catches the regression where
    # the bank guard produced 180 river columns and zero visible water.
    if river_biome_columns >= 100:
        assert river_water_columns >= 5, (
            f"Dry river regression: {river_water_columns} wet of "
            f"{river_biome_columns} river-biome surface columns"
        )
    # Flag the confirmed hollow mountain failure instead of labeling it a PASS.
    # 20% is an experimental QA guard, not a universal geological porosity target.
    assert examined_rock_voxels>0 and len(cave_voxels)/examined_rock_voxels<0.20, (
        f"Excessive sampled subsurface voids: {len(cave_voxels)}/{examined_rock_voxels}"
    )
if __name__=="__main__":scan(Path(sys.argv[1]),sys.argv[2] if len(sys.argv)>2 else "realism_max")
