#!/usr/bin/env python3
"""Migrate legacy RealScale worldgen resources at packaging time for Minecraft 26.3.
Never run on the original source tree. Retains original profiles and custom codecs.
"""
import copy
import json
import pathlib
import sys
from collections import Counter

ROOT = pathlib.Path(sys.argv[1])
DATA = ROOT / "data/realscale"
WG = DATA / "worldgen"
counts = Counter()
PROVIDERS = {
    "simple_state_provider": "simple", "weighted_state_provider": "weighted",
    "noise_provider": "noise", "dual_noise_provider": "dual_noise",
    "noise_threshold_provider": "noise_threshold",
    "randomized_int_state_provider": "randomized_int",
    "rule_based_state_provider": "rule_based", "rotated_block_provider": "rotated",
}
def read(path):
    return json.loads(path.read_text(encoding="utf-8"))
def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
def node(t, **fields):
    return {"type": "minecraft:" + t, **fields}

def rewrite(value, grid=(4, 4)):
    if isinstance(value, list):
        return [rewrite(x, grid) for x in value]
    if not isinstance(value, dict):
        return value
    if "Name" in value and isinstance(value["Name"], str) and set(value) <= {"Name", "Properties"}:
        return {"id": value["Name"], **({"properties": value["Properties"]} if "Properties" in value else {})}
    d = {k: rewrite(v, grid) for k,v in value.items()}
    t = d.get("type", "")
    if not isinstance(t, str) or not t.startswith("minecraft:"):
        return d
    t = t[10:]
    if t in PROVIDERS:
        d["type"] = "minecraft:" + PROVIDERS[t]
        if t == "simple_state_provider":
            # A literal Block State is also a valid Block State Provider.
            return d["state"]
    if t in ("add", "mul", "min", "max") and "argument1" in d:
        d["left"] = d.pop("argument1")
        d["right"] = d.pop("argument2")
    if t in ("abs","square","cube","half_negative","quarter_negative","squeeze","interpolated","flat_cache","cache_2d","cache_once","cache_all_in_cell","blend_density") and "argument" in d:
        d["input"] = d.pop("argument")
    if t in ("shift","shift_a","shift_b") and "argument" in d:
        d["noise"] = d.pop("argument")
    if t == "constant" and "argument" in d:
        d["value"] = d.pop("argument")
    if t == "shifted_noise":
        d["type"] = "minecraft:noise"
    if t == "invert":
        d["type"] = "minecraft:reciprocal"
    if t == "y_clamped_gradient":
        d.update(type="minecraft:gradient", axis="y", tiling="clamp_to_edge",
                 from_coordinate=d.pop("from_y"), to_coordinate=d.pop("to_y"))
    if t == "flat_cache":
        return node("cache", input=node("slice", axis="y", coordinate=0, input=d["input"]))
    if t in ("cache_2d","cache_once","cache_all_in_cell"):
        d["type"] = "minecraft:cache"
    if t == "interpolated":
        d.setdefault("cell_size_xz", grid[0])
        d.setdefault("cell_size_y", grid[1])
    if t == "end_islands":
        # 26.3 vanilla graph combines the central island with end_outer_islands.
        return "minecraft:end/islands"
    return d

def biome(d):
    if isinstance(d.get("carvers"), dict):
        old = d["carvers"]
        if set(old) - {"air"}:
            raise ValueError("Unknown carver stage: " + str(old.keys()))
        d["carvers"] = old.get("air", [])
    a = d.setdefault("attributes", {})
    e = d.get("effects", {})
    for key in ("fog_color", "sky_color", "water_fog_color"):
        if key in e:
            v=e.pop(key)
            a["minecraft:visual/"+key] = "#%06x" % v if isinstance(v,int) else v
    for key in ("water_color","grass_color","foliage_color","dry_foliage_color"):
        if isinstance(e.get(key),int):
            e[key] = "#%06x" % e[key]
    if "mood_sound" in e:
        a.setdefault("minecraft:audio/ambient_sounds", {})["mood"] = e.pop("mood_sound")
    if "spawners" in d:
        by_category={}
        for cat, entries in d.pop("spawners").items():
            if not entries:
                continue
            by_category[cat]=[]
            for entry in entries:
                x=dict(entry)
                lo=x.pop("minCount")
                hi=x.pop("maxCount")
                x["count"]=lo if lo==hi else node("uniform",min_inclusive=lo,max_inclusive=hi)
                by_category[cat].append(x)
        a["minecraft:gameplay/natural_mob_spawns"]={"modifier":"overlay","argument":{"spawns_by_category":by_category,"spawn_costs":d.pop("spawn_costs",{})}}
    return d

def dimension(d):
    realm=d.pop("effects", "minecraft:overworld")
    end=realm=="minecraft:the_end"
    nether=realm=="minecraft:the_nether"
    d.setdefault("has_ender_dragon_fight",end)
    a=d.setdefault("attributes",{})
    bed=d.pop("bed_works",not(nether or end))
    raid=d.pop("has_raids",not nether)
    ultra=d.pop("ultrawarm",nether)
    piglin=d.pop("piglin_safe",nether)
    anchor=d.pop("respawn_anchor_works",nether)
    d.pop("natural",None)
    a["minecraft:gameplay/bed_rule"]={"can_set_spawn":"always","can_sleep":"when_dark","error_message":{"translate":"block.minecraft.bed.no_sleep"}} if bed else {"can_set_spawn":"never","can_sleep":"never","destroy_on_use":True}
    a["minecraft:gameplay/can_start_raid"]=raid
    a["minecraft:gameplay/fast_lava"]=ultra
    a["minecraft:gameplay/water_evaporates"]=ultra
    a["minecraft:gameplay/piglins_zombify"]=not piglin
    a["minecraft:gameplay/respawn_anchor_works"]=anchor
    if "fixed_time" in d:
        d.pop("fixed_time")
        d["has_fixed_time"]=True
    if nether:
        d.update(cardinal_light="nether",skybox="none",timelines="#minecraft:in_nether")
    elif end:
        d.update(skybox="end",default_clock="minecraft:the_end",timelines="#minecraft:in_end")
    else:
        d.update(default_clock="minecraft:overworld",timelines="#minecraft:in_overworld")
        a.setdefault("minecraft:visual/sky_color","#78a7ff")
        a.setdefault("minecraft:visual/fog_color","#c0d8ff")
        a.setdefault("minecraft:visual/cloud_height",192.33)
        a.setdefault("minecraft:visual/cloud_color","#ccffffff")
    return d

# Use old grid sizes for inline interpolation; file-level legacy profiles use
# their own noise-settings grid. Resolve referenced density profiles below.
noise_files=sorted((WG/"noise_settings").glob("*.json"))
grids={}
def refs(v):
    if isinstance(v,str) and v.startswith("realscale:"):
        yield v.split(":",1)[1]
    elif isinstance(v,list):
        for x in v: yield from refs(x)
    elif isinstance(v,dict):
        for x in v.values(): yield from refs(x)
for p in noise_files:
    d=read(p)
    n=d["noise"]
    g=(4*n.get("size_horizontal",1),4*n.get("size_vertical",1))
    todo=list(refs(d["noise_router"]))
    seen=set()
    while todo:
        ref=todo.pop()
        if ref in seen: continue
        seen.add(ref)
        f=WG/"density_function"/(ref+".json")
        if not f.is_file(): continue
        grids.setdefault(ref,set()).add(g)
        todo.extend(refs(read(f)))
for p in sorted((WG/"density_function").rglob("*.json")):
    original=read(p)
    name=p.relative_to(WG/"density_function").as_posix()[:-5]
    choices=grids.get(name,{(4,4)})
    # Shared files can occur in multiple profiles; retain fine-grid interpolation
    # rather than coarsen them. Exact parity across different old grids needs QA.
    grid=min(choices)
    write(p,rewrite(original,grid))
    counts["density_function"]+=1

axis_map={"continentalness":"continents","depth":"depth","erosion":"erosion","humidity":"vegetation","temperature":"temperature","weirdness":"ridges"}
for p in noise_files:
    d=read(p)
    n=d["noise"]
    grid=(4*n.pop("size_horizontal",1),4*n.pop("size_vertical",1))
    d=rewrite(d,grid)
    r=d["noise_router"]
    initial=r.pop("initial_density_without_jaggedness")
    enabled=d.pop("aquifers_enabled")
    if enabled:
        # Old preliminary density is a signed density, not a Y coordinate.
        # Restore the old positive threshold and search downward for the surface.
        surf=node("find_top_surface",cell_height=grid[1],
                  density=node("add",left=initial,right=-0.390625),
                  lower_bound=n["min_y"],upper_bound=n["min_y"]+n["height"]-1)
        d["aquifers"]={k:r.pop(k) for k in ("barrier","fluid_level_floodedness","fluid_level_spread","lava")}
        d["aquifers"].update(surface_level=surf,exclusion=0)
        r["chunk_surface_level"]=node("interpolated",cell_size_xz=16,cell_size_y=1,input=surf)
    else:
        for k in ("barrier","fluid_level_floodedness","fluid_level_spread","lava"):r.pop(k,None)
        r["chunk_surface_level"]=0
    if d.pop("ore_veins_enabled"):
        raise ValueError("Explicit ore-vein migration required: "+str(p))
    for k in ("vein_toggle","vein_ridged","vein_gap"):r.pop(k,None)
    r["final_density"]=node("add",left=r["final_density"],right=node("beardifier"))
    d["material_rule"]=d.pop("surface_rule")
    targets=[]
    for target in d["spawn_target"]:
        result={}
        for old, interval in target.items():
            if old=="offset":
                if interval!=0: raise ValueError("Nonzero spawn offset")
                continue
            axis=axis_map[old]
            fun=r[axis]
            if isinstance(fun,str):
                key=fun
            else:
                key="realscale:spawn_axis/"+p.stem+"/"+axis
                write(WG/"density_function"/("spawn_axis/"+p.stem+"/"+axis+".json"),fun)
            result[key]=interval
        targets.append(result)
    d["spawn_target"]=targets
    write(p,d)
    counts["noise_settings"]+=1

for p in sorted((WG/"biome").glob("*.json")):
    write(p,biome(rewrite(read(p))))
    counts["biome"]+=1
for p in sorted((DATA/"dimension_type").glob("*.json")):
    write(p,dimension(rewrite(read(p))))
    counts["dimension_type"]+=1
for directory in ("feature","placed_feature"):
    for p in sorted((WG/directory).glob("*.json")):
        write(p,rewrite(read(p)))
        counts[directory]+=1

# Vehicles are already excluded from the 26.3 Java source set. Do not leave
# required registry references to an unregistered entity or item in the jar.
for p in (ROOT/"data/minecraft/tags/entity_type/boat.json", DATA/"recipe/hovercraft.json"):
    if p.is_file():
        if "tags" in p.parts:
            d=read(p)
            d["values"]=[x for x in d["values"] if x!="realscale:hovercraft"]
            if d["values"]:write(p,d)
            else:p.unlink()
        else:p.unlink()
        counts["deferred_vehicle_resource"]+=1
assert counts["biome"]>=69 and counts["density_function"]>=129 and counts["noise_settings"]>=34,counts
print("REALSCALE_26_3_MIGRATED",dict(counts))
