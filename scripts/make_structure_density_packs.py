#!/usr/bin/env python3
"""Opt-in 2x/3x/4x/6x vanilla structure candidates for Minecraft 1.21.1.

Only overrides selected vanilla overworld structure SET placements.
Full weight tables, salts, spread types, exclusion zones stay unchanged.
Biome conditions, slope/terrain suitability and generated structure quality
remain vanilla: this is NOT a guarantee of 2x/3x real built structures.
DO NOT automatically install on existing worlds.
"""
from __future__ import annotations
import argparse
import copy
import json
import math
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

# 1.21.1 vanilla structure_set catalog (the immutable vanilla fields are
# explicit, rather than guessing them or silently dropping exclusion zones).
# The reference is https://mcasset.cloud/1.21.1/data/minecraft/worldgen/structure_set/
# Strongholds, buried treasures, mineshafts, outpost special frequency, etc.
# must retain their special placement semantics or remain unmodified.
# These 12 vanilla sets are the same historical coverage as the earlier packs.
# Never silently replace a special-placement set with a simplified one.
MULTIPLIERS = (2, 3, 4, 6)
VANILLA = {
    "ancient_cities":(24,8,20083232,["ancient_city"]),
    "desert_pyramids":(32,8,14357617,["desert_pyramid"]),
    "igloos":(32,8,14357618,["igloo"]),
    "jungle_temples":(32,8,14357619,["jungle_pyramid"]),
    "ocean_monuments":(32,5,10387313,["monument"]),
    "ocean_ruins":(20,8,14357621,["ocean_ruin_cold","ocean_ruin_warm"]),
    "shipwrecks":(24,4,165745295,["shipwreck","shipwreck_beached"]),
    "swamp_huts":(32,8,14357620,["swamp_hut"]),
    "trail_ruins":(34,8,83469867,["trail_ruins"]),
    "trial_chambers":(34,12,94251327,["trial_chambers"]),
    "villages":(34,8,10387312,["village_plains","village_desert",
                                 "village_savanna","village_snowy",
                                 "village_taiga"]),
    "woodland_mansions":(80,20,10387319,["mansion"]),
}
TRIANGULAR = frozenset({"ocean_monuments", "woodland_mansions"})

# Separate, opt-in Nether/End expansion for RealScale's enlarged realms.
# Vanilla 1.21.1 values and fortress/bastion 2:3 relative weighting remain
# exactly unchanged apart from spacing. No extra structures are introduced.
DIMENSIONAL = {
    "nether_complexes": (27, 4, 30084232,
                         [("fortress", 2), ("bastion_remnant", 3)]),
    "end_cities": (20, 11, 10387313, [("end_city", 1)]),
}
DIMENSION_TRIANGULAR = frozenset({"end_cities"})
# All items above are Overworld-only structure sets.
# NOTE: ruined_portals is a SHARED Overworld+Nether structure set; modifying
# it would secretly increase Nether ruined portal density too. Omit it.
# Omit pillager_outposts entirely: it has a 0.2 frequency reduction and a
# ten-chunk village exclusion that requires separately modeled interactions.
# Omitting strongholds also preserves their fixed 128-ring distribution.
OMITTED = ("pillager_outposts", "strongholds", "mineshafts",
           "buried_treasures", "ruined_portals", "nether_complexes", "nether_fossils",
           "end_cities")

def original_set(name):
    spacing,separation,salt,structures = VANILLA[name]
    placement = {"type":"minecraft:random_spread", "salt":salt,
                 "separation":separation, "spacing":spacing}
    if name in TRIANGULAR:
        placement["spread_type"] = "triangular"
    return {"placement":placement,
            "structures":[{"structure":"minecraft:"+s,"weight":1} for s in structures]}

def candidate_spacing(original, multiplier, separation):
    # Candidate cells occur about once per spacing^2 chunks; the actual
    # structure start also depends on biome, exclusion, terrain, and seeds.
    assert multiplier in MULTIPLIERS
    return max(separation+2, round(original/math.sqrt(multiplier)))

def pack_resources(multiplier):
    if multiplier not in MULTIPLIERS:
        raise ValueError("density must be 2, 3, 4, or 6")
    data = {
        "pack.mcmeta":{
            "pack":{"pack_format":48,
                    "description":f"EXPERIMENTAL vanilla overworld structure candidates ~{multiplier}x; NOT guaranteed constructed structure count"}
        }
    }
    for name in sorted(VANILLA):
        obj=copy.deepcopy(original_set(name))
        p=obj["placement"]
        p["spacing"]=candidate_spacing(p["spacing"],multiplier,p["separation"])
        data[f"data/minecraft/worldgen/structure_set/{name}.json"]=obj
    return data

def dimension_original_set(name):
    spacing, separation, salt, entries = DIMENSIONAL[name]
    placement = {"type": "minecraft:random_spread", "salt": salt,
                 "separation": separation, "spacing": spacing}
    if name in DIMENSION_TRIANGULAR:
        placement["spread_type"] = "triangular"
    return {"placement": placement,
            "structures": [{"structure": "minecraft:" + entity, "weight": weight}
                           for entity, weight in entries]}


def dimension_pack_resources(multiplier):
    if multiplier not in (2, 3):
        raise ValueError("Choose 2x or 3x for the Nether/End companion pack")
    data = {
        "pack.mcmeta": {"pack": {"pack_format": 48,
            "description": (f"EXPERIMENTAL optional Nether/End random-spread candidates "
                            f"~{multiplier}x; preserves fortress/bastion weight ratios")}}
    }
    for name in sorted(DIMENSIONAL):
        item = copy.deepcopy(dimension_original_set(name))
        placement = item["placement"]
        placement["spacing"] = candidate_spacing(
            placement["spacing"], multiplier, placement["separation"])
        data[f"data/minecraft/worldgen/structure_set/{name}.json"] = item
    validate_dimensions(multiplier, data)
    return data


def validate_dimensions(multiplier, data):
    assert set(data) == {"pack.mcmeta"} | {
        f"data/minecraft/worldgen/structure_set/{name}.json"
        for name in DIMENSIONAL}
    assert data["pack.mcmeta"]["pack"]["pack_format"] == 48
    for name in DIMENSIONAL:
        key = f"data/minecraft/worldgen/structure_set/{name}.json"
        original = dimension_original_set(name)
        modified = data[key]
        assert modified["structures"] == original["structures"]
        for field, value in original["placement"].items():
            if field != "spacing":
                assert modified["placement"][field] == value
        assert original["placement"]["separation"] < modified["placement"]["spacing"]
        assert modified["placement"]["spacing"] < original["placement"]["spacing"]
    assert not (set(DIMENSIONAL) & set(VANILLA))


def validate(multiplier, data):
    assert len(data)==len(VANILLA)+1
    assert data["pack.mcmeta"]["pack"]["pack_format"]==48
    names=set()
    for name in VANILLA:
        key=f"data/minecraft/worldgen/structure_set/{name}.json"
        original=original_set(name)
        modified=data[key]
        assert modified["structures"]==original["structures"]
        modified_place=modified["placement"]
        original_place=original["placement"]
        assert set(modified_place)==set(original_place)
        for field in original_place:
            if field!="spacing":
                assert modified_place[field]==original_place[field],(name,field)
        assert original_place["separation"] < modified_place["spacing"] < original_place["spacing"]
        # 6x attempts may be clamped by immutable vanilla separation;
        # validate a bounded increase rather than promising exact density.
        factor=(original_place["spacing"]/modified_place["spacing"])**2
        assert 1.4 < factor <= 6.5, (name,multiplier,factor)
        names.add(name)
    assert names.isdisjoint(OMITTED)
    assert all(key.startswith(("pack.mcmeta","data/minecraft/worldgen/structure_set/"))
               for key in data)

def build(outdir=Path("build")):
    outdir=Path(outdir)
    outdir.mkdir(parents=True,exist_ok=True)
    result=[]
    for ratio in MULTIPLIERS:
        data=pack_resources(ratio)
        validate(ratio,data)
        target=outdir/f"RealScale-Vanilla-Structures-{ratio}x-EXPERIMENTAL.zip"
        with ZipFile(target,"w",compression=ZIP_DEFLATED) as z:
            for path,obj in sorted(data.items()):
                z.writestr(path,json.dumps(obj,indent=2)+"\n")
        result.append(target)
        print(f"STRUCTURE_{ratio}X_PACK:",target)
        print(f"OVERRIDDEN_OVERWORLD_RANDOM_SPREAD_SETS: {len(VANILLA)}; REST UNCHANGED")
        print(f"STRUCTURE_{ratio}X_CANDIDATE_SPACING:",
              ", ".join(f"{name}={candidate_spacing(VANILLA[name][0], ratio, VANILLA[name][1])}"
                        for name in ("villages", "shipwrecks", "ancient_cities",
                                     "woodland_mansions", "ocean_ruins")))
    for ratio in (2,3):
        data=dimension_pack_resources(ratio)
        target=outdir/f"RealScale-Nether-End-Structures-{ratio}x-EXPERIMENTAL.zip"
        with ZipFile(target,"w",compression=ZIP_DEFLATED) as z:
            for path,obj in sorted(data.items()):
                z.writestr(path,json.dumps(obj,indent=2)+"\n")
        result.append(target)
        print(f"DIMENSION_STRUCTURE_{ratio}X_PACK:",target)
    print("IMPORTANT: choose ONLY ONE Overworld density pack per world.")
    print("Optional Nether/End pack: choose ONLY ONE 2x/3x companion pack.")
    print("These override minecraft structure_set entries in THAT WORLD;")
    print("they do NOT guarantee constructed structures or overcome biome/terrain filters.")
    return result

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--outdir",type=Path,default=Path("build"))
    args=parser.parse_args()
    build(args.outdir)
