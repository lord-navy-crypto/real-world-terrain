#!/usr/bin/env python3
"""Anvil WORLD_SURFACE and subsurface scanner for ACTUALLY generated Realism MAX chunks.
This is observational; no claim that natural cliffs never exceed a threshold.
"""
from pathlib import Path
import json,sys,statistics,math
from realscale_headless_qa import iter_region_chunks,surface_height,get_block,unpack_palette_index,read_nbt
import gzip

def radial_profile_from_noise(settings_path):
    """Follow the actual temperature density graph, including nested inline nodes.

    Sacred worlds currently use climate_budget -> mountain_basin_temperature ->
    radial_latitude_temperature. Checking only a string root silently reported
    'not_radial' for worlds where radial latitude is active.
    """
    settings=json.loads(settings_path.read_text(encoding="utf-8"))
    temperature=settings.get("noise_router",{}).get("temperature")
    resources=settings_path.parent.parent/"density_function"
    seen=set()

    def find(node):
        if isinstance(node,str) and node.startswith("realscale:"):
            ref=node.split(":",1)[1]
            if ref in seen:
                return None
            seen.add(ref)
            density_file=resources/(ref+".json")
            if density_file.is_file():
                return find(json.loads(density_file.read_text(encoding="utf-8")))
        elif isinstance(node,dict):
            if node.get("type")=="realscale:radial_latitude_temperature":
                return {
                    **{k:node[k] for k in ("center_x","center_z","pole_distance","strength")},
                    "balanced":node.get("balanced",False),
                    "area_balanced":node.get("area_balanced",False),
                    "single_pole_area":node.get("single_pole_area",False)
                }
            for child in node.values():
                match=find(child)
                if match is not None:
                    return match
        elif isinstance(node,list):
            for child in node:
                match=find(child)
                if match is not None:
                    return match
        return None

    return find(temperature)



def meridional_profile_from_noise(settings_path):
    """Follow the live temperature density graph to its signed N/S latitude node."""
    settings=json.loads(settings_path.read_text(encoding="utf-8"))
    temperature=settings.get("noise_router",{}).get("temperature")
    resources=settings_path.parent.parent/"density_function"
    seen=set()
    def find(node):
        if isinstance(node,str) and node.startswith("realscale:"):
            ref=node.split(":",1)[1]
            if ref in seen:return None
            seen.add(ref)
            density_file=resources/(ref+".json")
            if density_file.is_file():
                return find(json.loads(density_file.read_text(encoding="utf-8")))
        elif isinstance(node,dict):
            if node.get("type")=="realscale:latitude_temperature":
                return {k:node[k] for k in ("equator_z","pole_distance","strength")}
            for child in node.values():
                match=find(child)
                if match is not None:return match
        elif isinstance(node,list):
            for child in node:
                match=find(child)
                if match is not None:return match
        return None
    return find(temperature)


def meridional_latitude_degrees(z,profile):
    """Signed latitude parity with GeoMath.latitudeDeg; north is negative Z."""
    phase=((profile["equator_z"]-z)/profile["pole_distance"])%4.0
    if phase<=1.0:return 90.0*phase
    if phase<=3.0:return 90.0*(2.0-phase)
    return 90.0*(phase-4.0)


def meridional_biome_audit(heights,surface_biomes,profile):
    """Observed north/equatorial/south biome bands from persisted surface columns."""
    bands={"north_polar_60_90":[],"north_mid_30_60":[],
           "equatorial_0_30":[],"south_mid_30_60":[],"south_polar_60_90":[]}
    for (x,z),y in heights.items():
        biome=surface_biomes.get((x,z))
        if not isinstance(biome,str):continue
        latitude=meridional_latitude_degrees(z,profile)
        absolute=abs(latitude)
        if absolute<30:label="equatorial_0_30"
        elif absolute<60:label="north_mid_30_60" if latitude>0 else "south_mid_30_60"
        else:label="north_polar_60_90" if latitude>0 else "south_polar_60_90"
        bands[label].append(biome)
    total=sum(map(len,bands.values()))
    result={}
    for label,biomes in bands.items():
        cold=sum(any(token in b for token in ("snowy","frozen","ice_spikes","grove")) for b in biomes)
        warm=sum(any(token in b for token in ("jungle","savanna","desert","badlands")) for b in biomes)
        result[label]={"sample_columns":len(biomes),
            "sample_fraction":round(len(biomes)/total,6) if total else None,
            "cold_biome_fraction":round(cold/len(biomes),6) if biomes else None,
            "warm_biome_fraction":round(warm/len(biomes),6) if biomes else None}
    result["mode"]="signed_north_south"
    result["equator_z"]=profile["equator_z"]
    result["pole_distance_blocks"]=profile["pole_distance"]
    result["note"]="Observed sampled Anvil columns only; not a global biome-area or thermodynamic guarantee."
    return result


def radial_latitude_degrees(x,z,radial):
    """Python parity with GeoMath: legacy, balanced, two-crest, PR25 one-crest.

    This calculates latitude only; biome counts still come from saved Anvil
    data. Never infer real global area fractions from sampled spawn chunks.
    """
    distance=radial["pole_distance"]
    radius=math.hypot(x-radial["center_x"],z-radial["center_z"])
    if radial.get("single_pole_area",False):
        period=2.0*distance
        cycle=math.floor(radius/period)
        inner=cycle*period
        offset=radius-inner
        fraction=offset*(2.0*inner+offset)/(period*(2.0*inner+period))
        fraction=max(0.0,min(1.0,fraction))
        return 90.0*(1.0-abs(2.0*fraction-1.0))
    if radial.get("area_balanced",False):
        period=2.0*distance
        cycle=math.floor(radius/period)
        inner=cycle*period
        fraction=(radius-inner)/period
        area_phase=(2.0*cycle*fraction+fraction*fraction)/(2.0*cycle+1.0)
        quarter=4.0*area_phase
        if quarter<=1.0:
            return 90.0*quarter
        if quarter<=2.0:
            return 90.0*(2.0-quarter)
        if quarter<=3.0:
            return 90.0*(quarter-2.0)
        return 90.0*(4.0-quarter)
    phase=(radius/distance)%2.0
    outward=phase if phase<=1.0 else 2.0-phase
    return 90.0*(outward**1.15 if radial.get("balanced",False) else outward)


def radial_biome_audit(heights,surface_biomes,radial):
    """Observed area and biome fractions in concentric belts only.

    Never infer a global biome ratio from a few generated spawn chunks.
    """
    if not radial:return {"mode":"not_radial"}
    cx=radial["center_x"]
    cz=radial["center_z"]
    distance=radial["pole_distance"]
    bands={"equatorial_0_30":[],"middle_30_60":[],"polar_60_90":[]}
    radii=[]
    tropical_high=[]
    tropical_all=[]
    for (x,z),y in heights.items():
        b=surface_biomes.get((x,z))
        if not isinstance(b,str):continue
        r=math.hypot(x-cx,z-cz)
        lat=radial_latitude_degrees(x,z,radial)
        if lat<23.5:
            tropical_all.append(b)
            if y>=240:tropical_high.append(b)
        if lat<30:label="equatorial_0_30"
        elif lat<60:label="middle_30_60"
        else:label="polar_60_90"
        bands[label].append(b)
        radii.append(r)
    result={}
    n=sum(len(v) for v in bands.values())
    for name,biomes in bands.items():
        cold=sum("snowy" in b or "frozen" in b or "ice_spikes" in b or "grove" in b
                 for b in biomes)
        warm=sum("jungle" in b or "savanna" in b or "desert" in b or "badlands" in b
                 for b in biomes)
        result[name]={"sample_columns":len(biomes),
            "sample_fraction":round(len(biomes)/n,6) if n else None,
            "cold_biome_fraction":round(cold/len(biomes),6) if biomes else None,
            "warm_biome_fraction":round(warm/len(biomes),6) if biomes else None}
    def cold_fraction(items):
        return round(sum(any(s in b for s in ("snowy","frozen","ice_spikes","grove"))
                         for b in items)/len(items),6) if items else None
    tropical_cold=cold_fraction(tropical_all)
    plateau_cold=cold_fraction(tropical_high)
    result["equatorial_plateau_check"]={
        "tropical_columns":len(tropical_all),
        "high_plateau_columns_y240_plus":len(tropical_high),
        "tropical_cold_fraction":tropical_cold,
        "high_plateau_cold_fraction":plateau_cold,
        "warning":("High tropical snow-biome share in observed plateau columns"
                   if len(tropical_high)>=64 and plateau_cold>0.25 else None),
        "note":"Y>=240 is a diagnostic height band, not proof of a plateau. Sampled chunks only."
    }
    result["mode"]=("radial_equal_area_single_pole" if radial.get("single_pole_area",False)
        else "radial_equal_area" if radial.get("area_balanced",False)
        else "radial_balanced" if radial.get("balanced",False)
        else "radial_legacy")
    result["range_blocks"]=[round(min(radii)),round(max(radii))] if radii else None
    result["pole_distance_blocks"]=distance
    result["center_world_xz"]=[cx,cz]
    result["note"]="Observed region chunks only. No global or thermodynamic cold/warm balance claim."
    return result


def altitude_biome_audit(heights, surface_biomes):
    """Observed saved-block biome distribution by actual surface elevation.

    Diagnostic, not a guarantee of climatically correct biome placement.
    Nether/end and all unsampled positions are excluded by caller.
    """
    categories=((63,95),(96,159),(160,239),(240,4095))
    bands={}
    for low,high in categories:
        subset=[surface_biomes.get(pos) for pos,y in heights.items()
                if low<=y<=high and isinstance(surface_biomes.get(pos),str)]
        snowy=sum(any(fragment in b for fragment in (
            "snowy","frozen","ice_spikes","grove")) for b in subset)
        hot=sum(any(fragment in b for fragment in (
            "desert","savanna","jungle","badlands")) for b in subset)
        bands[f"{low}-{high}"]={
            "sample_columns":len(subset),
            "snowy_or_frozen_count":snowy,
            "hot_dry_or_tropical_count":hot,
            "snowy_or_frozen_fraction":round(snowy/len(subset),6) if subset else None,
            "hot_dry_or_tropical_fraction":round(hot/len(subset),6) if subset else None,
        }
    return {
        "bands":bands,
        "method":"Saved surface Y and saved surface biome palette only",
        "note":"No guaranteed monotonic snowline; local humidity and world seed still matter."
    }


def classify_river_surface(upper, beneath, deep):
    """Only claim a wet river if liquid water actually exists.

    Snow may hide a frozen river: WORLD_SURFACE contains a snow layer at
    the highest Y, an ice lid one block down, and liquid water two down.
    """
    direct_water=(upper=="minecraft:water")
    exposed_ice_water=(upper=="minecraft:ice" and beneath=="minecraft:water")
    snow_covered_ice_water=(upper=="minecraft:snow" and
                            beneath=="minecraft:ice" and deep=="minecraft:water")
    return {
        "wet":direct_water or exposed_ice_water or snow_covered_ice_water,
        "exposed_ice_with_liquid":exposed_ice_water,
        "snow_over_ice_with_liquid":snow_covered_ice_water,
    }


def surface_water_components(wet_columns):
    """Four-face connectivity of persisted surface river water/ice, not just count.

    No interpolation between separated cells; tiles at chunk edges are
    connected only if both saved columns really exist and are wet.
    """
    remaining=set(wet_columns)
    sizes=[]
    cross_chunk=0
    while remaining:
        stack=[remaining.pop()]
        count=0
        chunks=set()
        while stack:
            x,z=stack.pop()
            count+=1
            chunks.add((x//16,z//16))
            for pos in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                if pos in remaining:
                    remaining.remove(pos)
                    stack.append(pos)
        sizes.append(count)
        if len(chunks)>1:
            cross_chunk+=1
    sizes.sort(reverse=True)
    return {
        "wet_components":len(sizes),
        "largest_wet_component_columns":sizes[0] if sizes else 0,
        "largest_wet_component_fraction":round((sizes[0]/len(wet_columns)),6) if sizes else 0,
        "isolated_one_column_puddles":sum(a==1 for a in sizes),
        "wet_components_crossing_chunk_boundaries":cross_chunk,
        "size_distribution_largest_first":sizes[:20],
        "note":"Surface water and ice connected orthogonally in saved Anvil chunks; does not prove downhill flow."
    }


def navigable_water_components(water_columns, max_surface_step=0, min_depth=2):
    """Inspect real saved surface water for plausible boat corridors.

    water_columns: (absolute_x,absolute_z) -> (water_surface_y, water_depth).
    Ice and unverified surface blocks must be excluded by the caller.
    This is a conservative geometric proxy: it does NOT simulate boats, flow
    velocity, obstruction above the surface, turn radius, or waterfalls.
    """
    eligible={p: v for p,v in water_columns.items() if v[1]>=min_depth}
    remaining=set(eligible)
    sizes=[]
    cross_chunk=0
    while remaining:
        start=remaining.pop()
        stack=[start]
        component_size=0
        chunks=set()
        while stack:
            x,z=stack.pop()
            component_size+=1
            chunks.add((x//16,z//16))
            water_y=eligible[(x,z)][0]
            for next_pos in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                if next_pos not in remaining:
                    continue
                if abs(eligible[next_pos][0]-water_y) > max_surface_step:
                    continue
                remaining.remove(next_pos)
                stack.append(next_pos)
        sizes.append(component_size)
        if len(chunks)>1:cross_chunk+=1
    sizes.sort(reverse=True)
    edges_with_steep_step=0
    for (x,z),(y,depth) in eligible.items():
        for pos in ((x+1,z),(x,z+1)):
            if pos in eligible and abs(y-eligible[pos][0])>max_surface_step:
                edges_with_steep_step+=1
    return {
        "sampled_deep_water_columns":len(eligible),
        "navigable_components":len(sizes),
        "largest_navigable_component_columns":sizes[0] if sizes else 0,
        "largest_navigable_component_fraction":round(sizes[0]/len(eligible),6)
                if eligible else 0,
        "cross_chunk_navigable_components":cross_chunk,
        "steep_adjacent_water_edges":edges_with_steep_step,
        "min_depth_blocks":min_depth,
        "max_surface_step_blocks":max_surface_step,
        "note":"Geometric proxy based on actually saved Anvil water; not a boat gameplay test."
    }


def underlying_ground_below_stem(chunk, dx, dz, top_y, min_y):
    """Resolve saved tall jungle vegetation to the actual supporting ground.

    MOTION_BLOCKING_NO_LEAVES intentionally ignores foliage, but it DOES
    include vertical logs, bamboo and hanging vines. On a dense jungle these yield many
    artificial 10-25-block "ground" spikes. Only those explicit vegetation
    stems may be traversed, with gaps of air/leaves between branches.

    Never reject a geological spike: stone, soil, structures, water, lava,
    unknown palette entries and every other block terminate the descent.
    If no real support is found within 160 blocks, keep the original sample
    so an unsupported floating structure is still flagged by terrain QA.
    """
    def stem(name):
        if name in ("minecraft:vine", "minecraft:bamboo", "minecraft:bamboo_block"):
            return True
        if not name.startswith("minecraft:"):
            return False
        return name.endswith(("_log", "_wood", "_stem", "_hyphae")) or (
            name.startswith("minecraft:stripped_") and
            name.endswith(("_log", "_wood", "_stem", "_hyphae")))

    current = get_block(chunk, dx, top_y, dz)
    if not stem(current):
        return top_y, current, False
    scan_floor = max(min_y, top_y - 160)
    for y in range(top_y - 1, scan_floor - 1, -1):
        below = get_block(chunk, dx, y, dz)
        if stem(below) or below in ("minecraft:air", "minecraft:cave_air",
                                    "minecraft:void_air") or below.endswith("_leaves"):
            continue
        if below in ("UNKNOWN", "INVALID_PALETTE_INDEX"):
            return top_y, current, False
        return y, below, True
    return top_y, current, False


def isolated_height_impulses(heights, surface_blocks, surface_biomes):
    """Detect >8m one-column peaks and pits on a SELECTED saved heightmap.

    Ground relief uses MOTION_BLOCKING_NO_LEAVES. Raw WORLD_SURFACE reports
    stay separate so foliage-induced false positives remain observable.
    """
    vegetation_surface={"minecraft:bamboo", "minecraft:oak_leaves",
                        "minecraft:birch_leaves", "minecraft:spruce_leaves",
                        "minecraft:jungle_leaves", "minecraft:acacia_leaves",
                        "minecraft:dark_oak_leaves", "minecraft:mangrove_leaves",
                        "minecraft:cherry_leaves", "minecraft:short_grass",
                        "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern"}
    count=0
    examples=[]
    block_counts={}
    vegetation=0
    other=0
    for (x,z),h in heights.items():
        neighbors=[heights.get((x-1,z)),heights.get((x+1,z)),
                   heights.get((x,z-1)),heights.get((x,z+1))]
        if not all(n is not None for n in neighbors):
            continue
        if not (all(h-n>8 for n in neighbors) or all(n-h>8 for n in neighbors)):
            continue
        count+=1
        block=surface_blocks.get((x,z)) or "UNKNOWN"
        block_counts[block]=block_counts.get(block,0)+1
        if block in vegetation_surface:
            vegetation+=1
        else:
            other+=1
        if len(examples)<64:
            examples.append({
                "x":x,"z":z,"height":h,"neighbor_heights":neighbors,
                "surface_block":block,"surface_biome":surface_biomes.get((x,z)),
                "is_chunk_boundary":x%16 in (0,15) or z%16 in (0,15),
                "is_four_grid_boundary":x%4 in (0,3) or z%4 in (0,3)
            })
    return {"count":count, "examples":examples, "blocks":block_counts,
            "vegetation":vegetation, "other":other}



def adjacent_height_deltas(heights):
    """Measure REAL adjacent ground columns, with signed world-coordinate seams.

    A visible canopy/vine/stem surface can be 20+ blocks above the underlying
    earth. Keep WORLD_SURFACE data for diagnosis, but feed the quality gate
    persisted ground heights so vertical vegetation never appears to be a
    Minecraft chunk/4-grid terrain seam. Do not interpolate missing neighbors:
    unloaded far chunks must not be mistaken for sampled continuity.
    """
    adjacent=[]
    grid_edge=[];grid_inner=[]
    chunk_edge=[];chunk_inner=[]
    for (x,z),h in heights.items():
        for nx,nz in ((x+1,z),(x,z+1)):
            neighbor=heights.get((nx,nz))
            if neighbor is None:continue
            delta=abs(h-neighbor)
            adjacent.append(delta)
            if (nx%16==0 and nz==z) or (nz%16==0 and nx==x):
                chunk_edge.append(delta)
            else:
                chunk_inner.append(delta)
            if (nx%4==0 and nz==z) or (nz%4==0 and nx==x):
                grid_edge.append(delta)
            else:
                grid_inner.append(delta)
    return adjacent,grid_edge,grid_inner,chunk_edge,chunk_inner


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
    # A TVA world-preset ID selects a different underlying noise-settings
    # registry ID. Read the real mapping instead of guessing both IDs match.
    root_data=Path(__file__).resolve().parents[2]/"neoforge/src/main/resources/data/realscale/worldgen"
    source= preset
    world_path=root_data/"world_preset"/(preset+".json")
    if world_path.is_file():
        world=json.loads(world_path.read_text())
        source=world["dimensions"]["minecraft:overworld"]["generator"]["settings"].split(":",1)[1]
    expected_noise_id="realscale:"+source
    settings_path=root_data/"noise_settings"/(source+".json")
    noise=json.loads(settings_path.read_text())["noise"]
    min_y=noise["min_y"]
    noise_height=noise["height"]
    # Minecraft heightmaps are encoded against DIMENSION TYPE build height,
    # not against the generator's potentially shorter vertical noise range.
    # TVA Canyon has 1024-high noise but a 2096-high dimension: decoding with
    # 1024 produces fake 500-block checkerboard spikes and bogus failures.
    world_height=noise_height
    if world_path.is_file():
        dim_key=world["dimensions"]["minecraft:overworld"]["type"]
        if dim_key.startswith("realscale:"):
            dimension_file=root_data.parent/"dimension_type"/(dim_key.split(":",1)[1]+".json")
            dimension=json.loads(dimension_file.read_text())
            if noise["min_y"] < dimension["min_y"] or (
                    noise["min_y"]+noise_height > dimension["min_y"]+dimension["height"]):
                raise AssertionError("Noise range is outside overworld dimension bounds")
            min_y=dimension["min_y"]
            world_height=dimension["height"]
    regions=[x for x in root.glob("run/**/region/r.*.*.mca") if "DIM" not in str(x)]
    assert regions, "No actual saved overworld Anvil region found"
    H={}
    # Persisted relief without tree canopy, alongside the full surface metric.
    terrain_heights={}
    terrain_surface_blocks={}
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
    mountain_columns=0
    mountain_voxels=0
    mountain_air_voxels=0
    mountain_water_voxels=0
    surface_fluid_water=0
    surface_fluid_lava=0
    surface_under_sea=0
    surface_block_missing=0
    surface_biome_counts={}
    river_biome_columns=0
    river_water_columns=0
    river_wet_surface_positions=set()
    river_water_navigation_columns={}
    river_surface_block_counts={}
    river_dry_surface_block_counts={}
    river_ice_surface_columns=0
    river_water_below_ice_columns=0
    river_snow_over_ice_with_water_columns=0
    river_snow_beneath_block_counts={}
    river_snow_deep_block_counts={}
    stem_corrected_columns=0
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
                    terrain_h=surface_height(
                        chunk,dx,dz,min_y,world_height,"MOTION_BLOCKING_NO_LEAVES")
                    if terrain_h is not None:
                        ground_h, ground_block, corrected = underlying_ground_below_stem(
                            chunk, dx, dz, terrain_h, min_y)
                        terrain_heights[(x,z)]=ground_h
                        terrain_surface_blocks[(x,z)]=ground_block
                        stem_corrected_columns+=corrected
                    if h>=384:mountain_columns+=1
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
                        river_surface_block_counts[upper_block]=river_surface_block_counts.get(upper_block,0)+1
                        beneath=get_block(chunk,dx,h-1,dz)
                        deep=get_block(chunk,dx,h-2,dz)
                        wet_state=classify_river_surface(upper_block,beneath,deep)
                        wet=wet_state["wet"]
                        if upper_block=="minecraft:snow":
                            river_snow_beneath_block_counts[beneath]=river_snow_beneath_block_counts.get(beneath,0)+1
                            river_snow_deep_block_counts[deep]=river_snow_deep_block_counts.get(deep,0)+1
                        if not wet:
                            river_dry_surface_block_counts[upper_block]=river_dry_surface_block_counts.get(upper_block,0)+1
                        river_water_columns+=wet
                        if wet:
                            river_wet_surface_positions.add((x,z))
                        # Open liquid surfaces with >=2 full water blocks
                        # are navigable candidates; frozen lids are excluded.
                        if upper_block=="minecraft:water":
                            depth=1
                            for water_depth in range(1,5):
                                if get_block(chunk,dx,h-water_depth,dz)!="minecraft:water":
                                    break
                                depth+=1
                            river_water_navigation_columns[(x,z)]=(h,depth)
                        ice=(upper_block=="minecraft:ice")
                        river_ice_surface_columns+=ice
                        river_water_below_ice_columns+=wet_state["exposed_ice_with_liquid"]
                        river_snow_over_ice_with_water_columns+=wet_state["snow_over_ice_with_liquid"]
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
                        if h>=384:
                            mountain_voxels+=1
                            mountain_air_voxels+=block in ("minecraft:air","minecraft:cave_air")
                            mountain_water_voxels+=block=="minecraft:water"
                        if block in ("minecraft:lava","minecraft:magma_block"):
                            lava_voxels.add((x,y,z))
                        if block in ("minecraft:air","minecraft:cave_air"):
                            dry_voxels.add((x,y,z))
                        if block in ("minecraft:air","minecraft:cave_air","minecraft:water"):
                            cave_voxels.add((x,y,z))
                            cave_air_voxels+=block in ("minecraft:air","minecraft:cave_air")
                            cave_water_voxels+=block=="minecraft:water"
    # Preserve raw foliage-bearing surface deltas as diagnostics, but pass
    # only physically supported ground heights to chunk/4-grid seam gates.
    raw_delta,raw_four_boundary,raw_inner,raw_chunk_border,raw_within_chunk = (
        adjacent_height_deltas(H))
    delta,four_boundary,inner,chunk_border,within_chunk = (
        adjacent_height_deltas(terrain_heights))
    # Preserve the original WORLD_SURFACE anomalies for diagnosis, but
    # evaluate real ground-height artifacts with the same 8m threshold.
    raw_impulses=isolated_height_impulses(H,surface_blocks,surface_biomes)
    ground_impulses=isolated_height_impulses(
        terrain_heights,terrain_surface_blocks,surface_biomes)
    isolated_spikes=ground_impulses["count"]
    isolated_spike_examples=ground_impulses["examples"]
    isolated_spike_blocks=ground_impulses["blocks"]
    vegetation_spikes=ground_impulses["vegetation"]
    exposed_ground_spikes=ground_impulses["other"]
    world_spawn=None
    spawn_file=None
    for level_file in root.glob("run/**/level.dat"):
        if "DIM" in str(level_file):continue
        try:
            root_nbt=read_nbt(gzip.decompress(level_file.read_bytes()))
            data=root_nbt.get("Data",root_nbt)
            gen=(data.get("WorldGenSettings",{}).get("dimensions",{})
                 .get("minecraft:overworld",{}).get("generator",{}))
            if gen.get("settings")!=expected_noise_id:
                continue
            if preset.startswith("tva_"):
                biome=gen.get("biome_source",{})
                if not isinstance(biome,dict) or biome.get("type")!="realscale:sky_ocean":
                    continue
            if all(isinstance(data.get(k),int) for k in ("SpawnX","SpawnY","SpawnZ")):
                world_spawn=(data["SpawnX"],data["SpawnY"],data["SpawnZ"])
                spawn_file=str(level_file)
                break
        except Exception as ex:
            print("Could not read world spawn from",level_file,ex)
    spawn_report=spawn_water_audit(world_spawn,H,surface_blocks,surface_biomes)
    river_topology=surface_water_components(river_wet_surface_positions)
    navigation_flat=navigable_water_components(river_water_navigation_columns,0,2)
    navigation_hovercraft=navigable_water_components(river_water_navigation_columns,1,2)
    topology=cave_components(cave_voxels)
    walkability=dry_walkable_components(cave_voxels,dry_voxels,lava_voxels,sampled_min_y)
    quality=terrain_quality_gates(chunk_border,within_chunk,four_boundary,inner,
                                  isolated_spikes,len(H),high_elevation_nonriver_water_columns)
    # Missing ground heightmaps are an error: never silently skip bad columns.
    if len(terrain_heights)!=len(H):
        quality["issues"].append(
            f"Missing saved MOTION_BLOCKING_NO_LEAVES heights for "
            f"{len(H)-len(terrain_heights)} of {len(H)} surface columns")
        quality["pass"]=False
    def pct(values,q):
        a=sorted(values)
        return a[int((len(a)-1)*q)] if a else None
    report={
      "preset":preset,
      "surface_quality_gates":quality,
      "terrain_heightmap":"MOTION_BLOCKING_NO_LEAVES",
      "stem_corrected_ground_columns":stem_corrected_columns,
      "stem_correction_note":"Only logged/bamboo/vine columns are traced to persisted supporting material; stone/soil/spikes remain tested.",
      "ground_heightmap_columns":len(terrain_heights),
      "ground_coordinate_bounds":({
          "min_x":min(x for x,z in terrain_heights),
          "max_x":max(x for x,z in terrain_heights),
          "min_z":min(z for x,z in terrain_heights),
          "max_z":max(z for x,z in terrain_heights),
      } if terrain_heights else None),
      "ground_seam_measurement":"persisted supported MOTION_BLOCKING_NO_LEAVES",
      "world_surface_raw_adjacent_p95":pct(raw_delta,.95),
      "world_surface_raw_chunk_border_p95":pct(raw_chunk_border,.95),
      "world_surface_raw_within_chunk_p95":pct(raw_within_chunk,.95),
      "world_surface_raw_four_grid_p95":pct(raw_four_boundary,.95),
      "world_surface_raw_within_four_grid_p95":pct(raw_inner,.95),
      "world_surface_raw_spike_count":raw_impulses["count"],
      "world_surface_raw_spike_examples":raw_impulses["examples"],
      "world_surface_raw_spike_block_counts":raw_impulses["blocks"],
      "isolated_spike_examples":isolated_spike_examples,
      "isolated_spike_surface_block_counts":isolated_spike_blocks,
      "vegetation_surface_spike_count":vegetation_spikes,
      "other_surface_spike_count":exposed_ground_spikes,
      "spawn_level_dat":spawn_file,
      "spawn_surface_audit":spawn_report,
      "ocean_biome_surface_columns":sum(
        isinstance(b,str) and "ocean" in b for b in surface_biomes.values()),
      "worldgen_min_y":min_y,
      "worldgen_height":world_height,
      "noise_generation_height":noise_height,
      "heightmap_decode_basis":"dimension_type_height",
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
      "altitude_biome_audit":altitude_biome_audit(H,surface_biomes),
      "radial_biome_audit":radial_biome_audit(H,surface_biomes,radial_profile_from_noise(settings_path)),
      "north_south_biome_audit":meridional_biome_audit(H,surface_biomes,meridional_profile_from_noise(settings_path)),
      "river_biome_columns":river_biome_columns,
      "river_surface_block_counts":river_surface_block_counts,
      "river_dry_surface_block_counts":river_dry_surface_block_counts,
      "river_water_or_ice_columns":river_water_columns,
      "river_surface_connected_water":river_topology,
      "river_navigation_flat_water":navigation_flat,
      "river_navigation_one_block_steps":navigation_hovercraft,
      "river_surface_ice_columns":river_ice_surface_columns,
      "river_ice_with_water_beneath_columns":river_water_below_ice_columns,
      "river_snow_over_ice_with_water_columns":river_snow_over_ice_with_water_columns,
      "river_snow_beneath_block_counts":river_snow_beneath_block_counts,
      "river_snow_deep_block_counts":river_snow_deep_block_counts,
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
      "high_mountain_subsurface":{
        "minimum_surface_y":384,
        "measured_columns":mountain_columns,
        "measured_subsurface_voxels":mountain_voxels,
        "measured_air_voxels":mountain_air_voxels,
        "measured_water_voxels":mountain_water_voxels,
        "measured_air_fraction":round(mountain_air_voxels/mountain_voxels,6) if mountain_voxels else None,
        "note":"Evidence from saved blocks only; low void fraction is a diagnostic, not automatically a cave-density bug."
      },
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
    assert quality["pass"], (
        "Terrain grid/flood regression: " + str(quality["issues"])
        + "; spike surface blocks=" + str(isolated_spike_blocks)
        + "; vegetation candidates=" + str(vegetation_spikes)
        + "; other surface candidates=" + str(exposed_ground_spikes)
        + "; first spike coordinates=" + str(isolated_spike_examples[:12])
    )
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
    # Some river water is not enough: a saved river with many sampled columns
    # must have at least one genuinely adjacent water/ice pair, not just
    # isolated one-column springs. Cross-chunk connectivity is still reported.
    if river_biome_columns >= 100 and river_water_columns >= 5:
        assert river_topology["largest_wet_component_columns"] >= 2, (
            f"Disconnected river regression: {river_water_columns} isolated "
            f"wet columns among {river_biome_columns} River surface columns"
        )
    # Flag the confirmed hollow mountain failure instead of labeling it a PASS.
    # 20% is an experimental QA guard, not a universal geological porosity target.
    assert examined_rock_voxels>0 and len(cave_voxels)/examined_rock_voxels<0.20, (
        f"Excessive sampled subsurface voids: {len(cave_voxels)}/{examined_rock_voxels}"
    )
if __name__=="__main__":scan(Path(sys.argv[1]),sys.argv[2] if len(sys.argv)>2 else "realism_max")
