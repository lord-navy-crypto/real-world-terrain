#!/usr/bin/env python3
"""Vanilla-only ecology/geology pilot with WINTER + karst + lithology accents.

Opt-in NeoForge 1.21.1 datapack for NEW worlds, not a new biome ID,
a geo-physics simulator, or a replacement world generator.  It preserves
the previous 3 microhabitats and augments them in a separate build.
"""
from __future__ import annotations
import argparse
import copy
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import make_vanilla_ecology_pack as pilot

NS="realscale_geoeco_lab"
# Existing biome -> substrate-conditioned surface decoration, no surface
# replacement or terrain carving, no snow placed in warm biomes.
PATCHES={
    "alpine_dry_powder_snow":{
        "block":"minecraft:powder_snow",
        "biomes":["minecraft:frozen_peaks","minecraft:snowy_slopes","minecraft:grove"],
        "supports":["minecraft:snow_block","minecraft:packed_ice"],
        "count":1,"tries":8,"xz":4,"y":2,
        "step":"top_layer_modification",
    },
    "alpine_wind_snow":{
        "block":"minecraft:snow",
        "biomes":["minecraft:frozen_peaks","minecraft:snowy_slopes","minecraft:jagged_peaks"],
        "supports":["minecraft:stone","minecraft:andesite","minecraft:snow_block",
                    "minecraft:packed_ice"],
        "count":2,"tries":16,"xz":5,"y":2,
        "step":"top_layer_modification",
    },
    "winter_taiga_snow":{
        "block":"minecraft:snow",
        "biomes":["minecraft:snowy_taiga","minecraft:grove","minecraft:snowy_plains"],
        "supports":["minecraft:grass_block","minecraft:podzol",
                    "minecraft:dirt","minecraft:snow_block"],
        "count":2,"tries":12,"xz":4,"y":2,
        "step":"top_layer_modification",
    },
    "glacial_moraine_scree":{
        "block":"minecraft:gravel",
        "biomes":["minecraft:stony_peaks","minecraft:frozen_peaks",
                  "minecraft:jagged_peaks"],
        "supports":["minecraft:stone","minecraft:andesite","minecraft:packed_ice"],
        "count":1,"tries":12,"xz":3,"y":1,
        "step":"local_modifications",
    },
    "highland_scree_andesite":{
        "block":"minecraft:gravel",
        "biomes":["minecraft:windswept_gravelly_hills","minecraft:windswept_hills"],
        "supports":["minecraft:stone","minecraft:andesite"],
        "count":1,"tries":8,"xz":3,"y":1,
        "step":"local_modifications",
    },
    "wetland_silt_mud":{
        "block":"minecraft:mud",
        "biomes":["minecraft:swamp","minecraft:mangrove_swamp"],
        "supports":["minecraft:clay","minecraft:dirt"],
        "count":1,"tries":6,"xz":2,"y":1,
        "step":"local_modifications",
    },
    "cool_birch_fern":{
        "block":"minecraft:fern",
        "biomes":["minecraft:old_growth_birch_forest","minecraft:taiga"],
        "supports":["minecraft:grass_block","minecraft:podzol","minecraft:dirt"],
        "count":2,"tries":12,"xz":3,"y":2,
        "step":"vegetal_decoration",
    },
}
# Small lithological pockets approximate old v0.8/v0.9 vanilla material
# remapping without replacing existing ores, caves or arbitrary blockstates.
# Only literal minecraft:stone can be replaced. These are not claimed to
# model 3-D stratigraphy, carbonates, thermal history or karst aquifers.
POCKETS={
    "karst_calcite_lenses":{
        "block":"minecraft:calcite",
        "biomes":["minecraft:dripstone_caves"],
        "size":8,"count":2,"min_y":0,"max_y":175,
    },
    "mountain_calcite_seams":{
        "block":"minecraft:calcite",
        "biomes":["minecraft:stony_peaks","minecraft:jagged_peaks"],
        "size":7,"count":2,"min_y":160,"max_y":900,
    },
    "deep_tuff_lenses":{
        "block":"minecraft:tuff",
        "biomes":["minecraft:deep_dark","minecraft:dripstone_caves"],
        "size":9,"count":2,"min_y":-40,"max_y":90,
    },
}
def _patch_cfg(p):
    return {
        "type":"minecraft:random_patch",
        "config":{
            "tries":p["tries"],"xz_spread":p["xz"],"y_spread":p["y"],
            "feature":{
                "feature":{
                    "type":"minecraft:simple_block",
                    "config":{"to_place":{"type":"minecraft:simple_state_provider",
                                          "state":{"Name":p["block"]}}}
                },
                "placement":[{
                    "type":"minecraft:block_predicate_filter",
                    "predicate":{"type":"minecraft:all_of","predicates":[
                        {"type":"minecraft:matching_blocks","blocks":"minecraft:air"},
                        {"type":"minecraft:matching_blocks","blocks":p["supports"],
                         "offset":[0,-1,0]}
                    ]}
                }]
            }
        }
    }

def resources():
    source=pilot.resources()
    pilot.validate(source)
    result=copy.deepcopy(source)
    result["pack.mcmeta"]["pack"]["description"]=(
        "RealScale GeoEcology: opt-in WINTER/scree/wetland/cave geology microhabitats (NOT new biome IDs)")
    for name,p in PATCHES.items():
        prefix=f"data/{NS}"
        key=f"{NS}:{name}"
        result[f"{prefix}/worldgen/configured_feature/{name}.json"]=_patch_cfg(p)
        result[f"{prefix}/worldgen/placed_feature/{name}.json"]={
            "feature":key,
            "placement":[
                {"type":"minecraft:count","count":p["count"]},
                {"type":"minecraft:in_square"},
                {"type":"minecraft:heightmap","heightmap":"MOTION_BLOCKING_NO_LEAVES"},
                {"type":"minecraft:biome"}
            ]
        }
        result[f"{prefix}/neoforge/biome_modifier/{name}.json"]={
            "type":"neoforge:add_features",
            "biomes":p["biomes"],
            "features":key,"step":p["step"]
        }
    for name,p in POCKETS.items():
        prefix=f"data/{NS}"
        key=f"{NS}:{name}"
        result[f"{prefix}/worldgen/configured_feature/{name}.json"]={
            "type":"minecraft:ore",
            "config":{
                "discard_chance_on_air_exposure":0.0,
                "size":p["size"],
                "targets":[{
                    "target":{"predicate_type":"minecraft:block_match","block":"minecraft:stone"},
                    "state":{"Name":p["block"]}
                }]
            }
        }
        result[f"{prefix}/worldgen/placed_feature/{name}.json"]={
            "feature":key,"placement":[
                {"type":"minecraft:count","count":p["count"]},
                {"type":"minecraft:in_square"},
                {"type":"minecraft:height_range","height":{
                    "type":"minecraft:uniform",
                    "min_inclusive":{"absolute":p["min_y"]},
                    "max_inclusive":{"absolute":p["max_y"]}}},
                {"type":"minecraft:biome"}
            ]
        }
        result[f"{prefix}/neoforge/biome_modifier/{name}.json"]={
            "type":"neoforge:add_features",
            "biomes":p["biomes"],
            "features":key,"step":"underground_ores"
        }
    return result

def validate(data):
    assert len(data)==1+3*(len(pilot.PROFILES)+len(PATCHES)+len(POCKETS))
    assert data["pack.mcmeta"]["pack"]["pack_format"]==48
    names=[]
    for name,p in PATCHES.items():
        prefix=f"data/{NS}"
        cfg=data[f"{prefix}/worldgen/configured_feature/{name}.json"]
        placed=data[f"{prefix}/worldgen/placed_feature/{name}.json"]
        mod=data[f"{prefix}/neoforge/biome_modifier/{name}.json"]
        assert cfg["type"]=="minecraft:random_patch"
        assert p["block"].startswith("minecraft:")
        assert all(x.startswith("minecraft:") for x in p["supports"]+p["biomes"])
        assert cfg["config"]["feature"]["placement"][0]["predicate"]["predicates"][0]["blocks"]=="minecraft:air"
        assert placed["feature"]==mod["features"]==f"{NS}:{name}"
        assert mod["step"]==p["step"]
        assert 0<p["count"]<=2 and 0<p["tries"]<=16
        names.append(name)
    for name,p in POCKETS.items():
        prefix=f"data/{NS}"
        cfg=data[f"{prefix}/worldgen/configured_feature/{name}.json"]
        placed=data[f"{prefix}/worldgen/placed_feature/{name}.json"]
        mod=data[f"{prefix}/neoforge/biome_modifier/{name}.json"]
        assert cfg["type"]=="minecraft:ore"
        assert cfg["config"]["size"]<=9
        assert cfg["config"]["targets"][0]["target"]=={
            "predicate_type":"minecraft:block_match","block":"minecraft:stone"}
        assert cfg["config"]["targets"][0]["state"]["Name"]==p["block"]
        assert placed["feature"]==mod["features"]==f"{NS}:{name}"
        assert mod["step"]=="underground_ores"
        names.append(name)
    assert len(names)==len(set(names))
    assert not any("/worldgen/noise_settings/" in p or
                   "/worldgen/density_function/" in p or
                   "/worldgen/biome/" in p or
                   "/worldgen/structure_set/" in p for p in data)
    for path,obj in data.items():
        assert not path.startswith("/") and ".." not in Path(path).parts
        json.loads(json.dumps(obj))

def build(destination:Path):
    data=resources()
    validate(data)
    destination=Path(destination)
    destination.parent.mkdir(parents=True,exist_ok=True)
    with ZipFile(destination,"w",compression=ZIP_DEFLATED) as z:
        for path,obj in sorted(data.items()):
            z.writestr(path,json.dumps(obj,ensure_ascii=False,indent=2)+"\n")
    print("GEOECO_PACK:",destination)
    print(f"PATCH_ECOTOPES:{len(pilot.PROFILES)+len(PATCHES)} SUBSTRATE_POCKETS:{len(POCKETS)}")
    print("Opt-in NEW WORLD only. No structure-set changes and no actual new Biome ID.")
    return destination

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--output",type=Path,
                   default=Path("build/RealScale-GeoEcology-Winter-EXPERIMENTAL.zip"))
    build(p.parse_args().output)
