#!/usr/bin/env python3
"""RealScale Terrain Lab: safe, per-world selectable worldgen profiles.

Creates a NEW, uniquely named Minecraft world preset, noise settings and four
density functions inside the mod's resources. Does not change the nine
existing world IDs, Y=63 sea level, archived releases or existing save data.

Apply settings BEFORE building/restarting the Minecraft client. Worldgen
registries cannot be edited safely in the middle of a running world.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys

PRESETS = (
    "realism_max", "georeal_ultimate", "himalayan", "grand_canyon",
    "usgs_big_sur", "usgs_big_sur_v2", "continental", "realistic_mountains", "ultimate",
)
# A small public UI, with legacy algorithms still accepted for old worlds and CLI.
# These are REAL combinations of two underlying height density signals, not UI aliases.
FAMILIES = {
    "landscape": ("realism_max", "georeal_ultimate"),
    "alpine": ("himalayan", "realistic_mountains"),
    "canyon": ("grand_canyon", "continental"),
}
EDITABLE = tuple(FAMILIES)
LEGACY_EDITABLE = tuple(x for x in PRESETS if x not in ("usgs_big_sur","usgs_big_sur_v2"))
PROFILES = {
    "natural":  (1.0, 0, 50, 6),
    "gentle":   (0.9, -12, 35, 3),
    "alpine":   (1.12, 12, 50, 6),
    "dramatic": (1.2, 24, 65, 9),
}
ENGLISH = {
    "realism_max":"Realism MAX", "georeal_ultimate":"GeoReal Ultimate",
    "himalayan":"Himalayan", "grand_canyon":"Grand Canyon",
    "usgs_big_sur":"USGS Big Sur (historical DEM; read-only)",
    "usgs_big_sur_v2":"USGS Big Sur v2 (seamless DEM; measured/read-only)",
    "continental":"Continental", "realistic_mountains":"Realistic Mountains",
    "ultimate":"Ultimate",
    "landscape":"Natural Landscape (Realism + GeoReal)",
    "alpine":"Alpine Mountains (Himalayan + Realistic Mountains)",
    "canyon":"Canyons and Plateaus (Grand Canyon + Continental)",
}
CHINESE = {
    "realism_max":"综合真实地形", "georeal_ultimate":"多尺度地貌",
    "himalayan":"喜马拉雅", "grand_canyon":"大峡谷",
    "usgs_big_sur":"USGS Big Sur（历史实测 DEM，不允许修改）",
    "usgs_big_sur_v2":"USGS Big Sur v2（平滑过渡实测地形，不允许修改）",
    "continental":"大陆巨构", "realistic_mountains":"真实山脉",
    "ultimate":"终极地形",
    "landscape":"自然地貌（真实综合 + GeoReal）",
    "alpine":"高山地貌（喜马拉雅 + 真实山脉）",
    "canyon":"峡谷台地（大峡谷 + 大陆地形）",
}
NORMAL = ("natural_three_realms","alpine_three_realms","canyon_three_realms","glacial_three_realms","volcanic_three_realms","karst_three_realms","dune_three_realms")
GEO_ROOT = Path("data/realscale/worldgen")
LABEL_ROOT = Path("assets/minecraft/lang")
TAG_ROOT = Path("data/minecraft/tags/worldgen/world_preset")

def resource_root():
    return Path(__file__).resolve().parents[1] / "neoforge/src/main/resources"

def jread(root, path):
    return json.loads((root/path).read_text(encoding="utf-8"))

def serialized(obj):
    return json.dumps(obj,indent=2,ensure_ascii=False)+"\n"

def replace_refs(node, replacements):
    if isinstance(node,str):
        return replacements.get(node,node)
    if isinstance(node,list):
        return [replace_refs(item,replacements) for item in node]
    if isinstance(node,dict):
        return {key:replace_refs(value,replacements) for key,value in node.items()}
    return node

def validate(preset, relief, elevation, caves, river_grade, blend_percent=25, ridge=0, erosion=0, strata=0, climate=0, valley=0, latitude="off", latitude_strength=70, equator_z=0, basin=0, center_x=0, cold_limit=60, fantasy_dimensions=False, lithostitched_blend=False, lithostitched_geology=False):
    if preset not in PRESETS and preset not in FAMILIES:
        raise ValueError("Unknown terrain preset")
    if preset in ("usgs_big_sur","usgs_big_sur_v2"):
        raise ValueError("USGS Big Sur DEM is measured, read-only terrain: cannot rescale sampled height")
    if type(lithostitched_blend) is not bool:
        raise ValueError("Lithostitched adaptive blending must be a boolean")
    if lithostitched_blend and preset not in FAMILIES:
        raise ValueError("Lithostitched adaptive blending only supports the three merged Lab families")
    if type(lithostitched_geology) is not bool:
        raise ValueError("Lithostitched Deep Geology must be a boolean")
    if lithostitched_geology and (preset not in FAMILIES or blend_percent == 0):
        raise ValueError("Lithostitched Deep Geology requires a merged Lab family and nonzero blend")
    if preset in FAMILIES and (type(blend_percent) is not int or not 0 <= blend_percent <= 50):
        raise ValueError("Feature blend must be 0..50%")
    if preset not in FAMILIES and blend_percent != 25:
        raise ValueError("Legacy algorithm does not support feature blending")
    if type(climate) is not int or not 0 <= climate <= 100:
        raise ValueError("Altitude biome cooling must be an integer between 0 and 100")
    if type(valley) is not int or not 0 <= valley <= 100:
        raise ValueError("Valley warming must be an integer between 0 and 100")
    if preset not in FAMILIES and climate:
        raise ValueError("Altitude biome cooling applies only to merged Lab families")
    if preset not in FAMILIES and valley:
        raise ValueError("Valley warming applies only to merged Lab families")
    if latitude not in ("off","earth","preview","radial_earth","radial_preview","radial_balanced","radial_equal_area","radial_equal_area_single_pole"):
        raise ValueError("Latitude mode must be off, earth, preview, radial_earth, radial_preview, radial_balanced, radial_equal_area or radial_equal_area_single_pole")
    if type(latitude_strength) is not int or not 0 <= latitude_strength <= 100:
        raise ValueError("Latitude strength must be 0..100")
    if type(equator_z) is not int or not -29_000_000 <= equator_z <= 29_000_000:
        raise ValueError("Equator Z must be a world coordinate within +/-29000000")
    if type(basin) is not int or not 0 <= basin <= 100:
        raise ValueError("Basin warming must be 0..100")
    if type(center_x) is not int or not -29_000_000 <= center_x <= 29_000_000:
        raise ValueError("Radial center X must be within world boundaries")
    if type(cold_limit) is not int or not 0 <= cold_limit <= 100:
        raise ValueError("Cold budget must be an integer from 0 to 100")
    if preset not in FAMILIES and (latitude != "off" or basin):
        raise ValueError("Latitude and basin climate apply only to merged Lab families")
    if any(type(n) is not int for n in (ridge,erosion,strata)):
        raise ValueError("Geology controls must be integers")
    if not (0 <= ridge <= 40 and 0 <= erosion <= 30 and 0 <= strata <= 20):
        raise ValueError("Ridge 0..40, erosion 0..30, strata 0..20")
    if preset not in FAMILIES and (ridge or erosion or strata):
        raise ValueError("Geology functions currently apply only to merged families")
    if not 0.8 <= relief <= 1.25:
        raise ValueError("Relief must be 0.80 to 1.25")
    if not -48 <= elevation <= 48:
        raise ValueError("Elevation must be -48 to 48 blocks")
    if not 20 <= caves <= 80:
        raise ValueError("Cave activity must be 20 to 80")
    if river_grade not in (0,3,6,9):
        raise ValueError("River slope must be Off (0), Conservative (3), Standard (6) or Flexible (9)")

def files_for(root, preset, relief, elevation, caves, river_grade, inland=True, blend_percent=25, ridge=0, erosion=0, strata=0, climate=0, valley=0, latitude="off", latitude_strength=70, equator_z=0, basin=0, center_x=0, cold_limit=60, fantasy_dimensions=False, lithostitched_blend=False, lithostitched_geology=False):
    validate(preset,relief,elevation,caves,river_grade,blend_percent,ridge,erosion,strata,climate,valley,latitude,latitude_strength,equator_z,basin,center_x,cold_limit,fantasy_dimensions,lithostitched_blend,lithostitched_geology)
    if type(fantasy_dimensions) is not bool or (fantasy_dimensions and preset not in FAMILIES):
        raise ValueError("Fantasy dimensions require a merged Lab family and a boolean setting")
    source=FAMILIES[preset][0] if preset in FAMILIES else preset
    secondary=FAMILIES[preset][1] if preset in FAMILIES else None
    options=dict(preset=preset,relief=round(float(relief),3),
                 elevation=int(elevation),caves=int(caves),
                 river_grade=int(river_grade),inland=bool(inland))
    # Do not mutate the hash and files of any previously generated legacy preset.
    if preset in FAMILIES:
        options["blend_percent"]=int(blend_percent)
        if lithostitched_blend and blend_percent and not lithostitched_geology:
            # Deep Geology owns its adaptive mix; v1 toggle cannot make an
            # otherwise identical deep profile get a different registry ID.
            options["blend_engine"]="lithostitched_adaptive_v1"
        if lithostitched_geology:
            options["lithostitched_geology"]="perlin_shift_strata_caves_v4"
        if fantasy_dimensions:
            options["fantasy_dimensions"]="v2"
        # Backward compatibility: an all-zero geology draft emits exactly the
        # old v1.5 hash AND byte-identical profile. Never alter saved IDs.
        if ridge or erosion or strata:
            options.update(ridge=int(ridge),erosion=int(erosion),strata=int(strata))
        if caves >= 60:
            options["mountain_caves_revision"]=5
        if climate:
            options["climate"]=climate
        if valley:
            options["valley"]=valley
        if latitude != "off" and latitude_strength:
            options.update(latitude=latitude,latitude_strength=latitude_strength,equator_z=equator_z)
            if latitude.startswith("radial_"):
                options["center_x"]=center_x
                if cold_limit:
                    options["cold_limit"]=cold_limit
        if basin:
            options["basin"]=basin
    checksum=hashlib.sha256(json.dumps(options,sort_keys=True).encode()).hexdigest()[:10]
    name="lab_"+preset+"_"+checksum+"_g"+str(river_grade)
    density_folder="terrain_lab/"+name
    density_id="realscale:"+density_folder
    existing="realscale:"+source
    repl={existing+"/terrain_density":density_id+"/terrain_density",
          existing+"/preliminary_density":density_id+"/preliminary_density",
          existing+"/cave_density":density_id+"/cave_density"}
    new_height=density_id+"/height_signal"
    base=GEO_ROOT/"density_function"/source
    input_terrain=jread(root,base/"terrain_density.json")
    ref_height=existing+"/height_signal"
    def swap_height(obj):
        if obj==ref_height:return new_height
        if isinstance(obj,dict):return {key:swap_height(value) for key,value in obj.items()}
        if isinstance(obj,list):return [swap_height(x) for x in obj]
        return obj
    terrain=swap_height(input_terrain)
    if terrain==input_terrain:
        raise ValueError("Preset does not reference an editable height signal: "+preset)
    # Blend the underlying smooth/interpolated height signals BEFORE scaling;
    # this is a density-field merge, not a cosmetic preset rename.
    raw_height=ref_height
    # Each deep-geology Lab profile owns a separate seeded Perlin noise
    # registry entry, stored with that world's DATAPACK when exported.
    perlin_id="realscale:terrain_lab/"+name
    perlin={"type":"minecraft:cache_2d","argument":{
        "type":"lithostitched:fast_noise",
        "config":perlin_id,"xz_scale":1.0,"y_scale":0.0}} if lithostitched_geology else None
    if secondary and blend_percent:
        weight=round(blend_percent/100.0,4)
        if lithostitched_geology:
            # Sampling the FULL height_signal via lithostitched:shift
            # bypassed Minecraft's interpolation caches and made 2096-high
            # chunks extremely slow (51% spawn at the 480s smoke deadline).
            # Shift ONLY one inexpensive seeded Perlin sample; use its
            # bounded +/-0.02 height signal as an added secondary microform
            # (~2.56 vertical blocks with a 128-block height gradient).
            shifted_perlin={"type":"minecraft:cache_2d",
                "argument":{"type":"lithostitched:shift",
                    "input":{"type":"lithostitched:fast_noise",
                             "config":perlin_id,"xz_scale":1.0,"y_scale":0.0},
                    "shift_x":64.0,"shift_y":0.0,"shift_z":-48.0}}
            spatial_weight={"type":"minecraft:clamp",
                "input":{"type":"minecraft:add","argument1":weight,
                    "argument2":{"type":"minecraft:mul",
                        "argument1":0.04,"argument2":perlin}},
                "min":0.0,"max":0.5}
            # Lithostitched mix is intentionally LIMITED to two cheap,
            # well-bounded +/-1 Perlin fields. Its 1.21.1 implementation
            # evaluates complex argument functions point-by-point and
            # has a conservative maxValue(): mixing full-height signals
            # can hurt both performance and range accounting.
            microform={"type":"lithostitched:mix",
                "input":spatial_weight,
                "argument1":perlin,"argument2":shifted_perlin}
            secondary_height={"type":"minecraft:add",
                "argument1":"realscale:"+secondary+"/height_signal",
                "argument2":{"type":"minecraft:mul",
                    "argument1":0.02,"argument2":microform}}
            primary_weight={"type":"minecraft:add",
                "argument1":1.0,
                "argument2":{"type":"minecraft:mul",
                    "argument1":-1.0,"argument2":spatial_weight}}
            # Vanilla density arithmetic handles the expensive mountain
            # signals and retains normal batch/fillArray optimization.
            raw_height={"type":"minecraft:add",
                "argument1":{"type":"minecraft:mul",
                    "argument1":primary_weight,"argument2":ref_height},
                "argument2":{"type":"minecraft:mul",
                    "argument1":spatial_weight,"argument2":secondary_height}}
        elif lithostitched_blend:
            # Genuine Lithostitched 1.6.0 codec: low-frequency erosion drives
            # at most +/-5 percentage points of smooth regional blend variation.
            # Only a NEW hashed Lab world can contain this dependency.
            local_weight={"type":"minecraft:clamp",
                "input":{"type":"minecraft:add",
                    "argument1":weight,
                    "argument2":{"type":"minecraft:mul","argument1":0.05,
                        "argument2":"realscale:"+secondary+"/erosion"}},
                "min":0.0,"max":0.5}
            raw_height={"type":"lithostitched:mix",
                "input":local_weight,"argument1":ref_height,
                "argument2":"realscale:"+secondary+"/height_signal"}
        else:
            # Exactly preserve existing opt-out Lab density trees.
            raw_height={"type":"minecraft:add",
                "argument1":{"type":"minecraft:mul",
                             "argument1":round(1.0-weight,4),"argument2":ref_height},
                "argument2":{"type":"minecraft:mul",
                             "argument1":weight,
                             "argument2":"realscale:"+secondary+"/height_signal"}}
    if ridge or erosion:
        # Each signal is a seed-dependent, cached vanilla/generated density
        # function from the secondary biome geometry; the actual evaluation
        # occurs in the registered custom Java codec.
        raw_height={"type":"realscale:ridge_erosion",
                    "base":raw_height,
                    "ridge_signal":"realscale:"+secondary+"/ridges",
                    "erosion_signal":"realscale:"+secondary+"/erosion",
                    "ridge_gain":round(ridge*0.004,5),
                    "erosion_gain":round(erosion*0.003,5)}
    height={"type":"minecraft:add",
            "argument1":{"type":"minecraft:mul",
                         "argument1":float(options["relief"]),
                         "argument2":raw_height},
            "argument2":options["elevation"]/128.0}
    if strata:
        strata_warp="realscale:"+secondary+"/erosion"
        if lithostitched_geology:
            # Subtle vertically wavy solids, not a rock-material swap.
            sine={"type":"lithostitched:sin",
                "argument":{"type":"minecraft:mul",
                    "argument1":0.0490873852,
                    "argument2":{"type":"lithostitched:axis","axis":"y"}}}
            # IMPORTANT: Lithostitched 1.6.0's HOLDER_HELPER codec
            # accepts the EXISTING direct erosion registry reference, but
            # wrapping that reference inside minecraft:clamp fails registry
            # decoding in dedicated NeoForge 1.21.1. The source erosion
            # noise already samples within [-1,+1], like the sine branch.
            strata_warp={"type":"lithostitched:mix",
                "input":0.2,"argument1":strata_warp,"argument2":sine}
        terrain={"type":"realscale:strata",
                 "base":terrain,
                 "warp":strata_warp,
                 "strength":round(strata*0.002,5),
                 "period":64}
    preliminary=replace_refs(jread(root,base/"preliminary_density.json"),repl)
    caves_base=replace_refs(jread(root,base/"cave_density.json"),repl)
    # Density >0 means solid. Negative cave bias opens slightly more voxels.
    bias=round((50-options["caves"])/250.0,5)
    new_caves={"type":"minecraft:add","argument1":caves_base,"argument2":bias}
    if lithostitched_geology:
        # Bounded 0..0.025 positive solidity bias varies regionally;
        # surface shell/river water protections are never weakened.
        cave_weight={"type":"minecraft:clamp",
            "input":{"type":"minecraft:add","argument1":0.5,
                "argument2":{"type":"minecraft:mul",
                    "argument1":0.35,"argument2":perlin}},
            "min":0.0,"max":1.0}
        # Vanilla multiplication is faster than Lithostitched mix here.
        # Important: upstream 1.21.1 mix.maxValue() returns min of its two
        # argument maxima; for mix(0,0.025) this advertises max 0 despite
        # real positive output. Avoid incorrect ranges for cave density.
        # Compute the EXPENSIVE cave function exactly once.
        regional_bias={"type":"minecraft:mul",
            "argument1":0.025,"argument2":cave_weight}
        new_caves={"type":"minecraft:add",
            "argument1":new_caves,"argument2":regional_bias}
    original_settings=jread(root,GEO_ROOT/"noise_settings"/(source+".json"))
    if original_settings["sea_level"]!=63:raise ValueError("Refuse to modify a non-Y63 preset")
    noise=replace_refs(original_settings,repl)
    # Extra upper-mountain cave corridors for newly generated Lab presets only.
    # The existing caves use a terrain-density window and high-density closure,
    # which can leave tall mountain interiors largely solid. Extend exploration
    # into thick rock, but preserve a positive-density surface shell.
    # This intentionally does NOT change biome placement or legacy presets.
    if preset in FAMILIES and caves >= 60:
        terrain_depth=density_id+"/terrain_density"
        # Independent narrow spaghetti-like corridor: abs(core noise) near zero
        # can open in sufficiently thick mountain rock. No broad cheese OR.
        corridor={"type":"minecraft:add",
                  "argument1":{"type":"minecraft:abs",
                               "argument":{"type":"minecraft:noise",
                                           "noise":"minecraft:spaghetti_3d_1",
                                           "xz_scale":0.45,"y_scale":0.35}},
                  "argument2":-0.08}
        # All three editable families use a 128-block-per-height-signal
        # vertical gradient with zero crossing at Y=63. Derive estimated
        # LOCAL underground depth = (63 + 128*height_signal) - current Y.
        # This is a predicted surface, not a raycast of the final block
        # heightmap; noise/caves/surface decorations can differ.
        depth={"type":"minecraft:add",
               "argument1":{"type":"minecraft:mul","argument1":128.0,
                            "argument2":new_height},
               "argument2":{"type":"minecraft:y_clamped_gradient",
                            "from_y":-64,"to_y":2032,
                            "from_value":127.0,"to_value":-1969.0}}
        def positive_part(node,max_value):
            return {"type":"minecraft:clamp","input":node,
                    "min":0.0,"max":max_value}
        def subtract(a,b):
            return {"type":"minecraft:add","argument1":a,
                    "argument2":{"type":"minecraft:mul","argument1":-1.0,
                                 "argument2":b}}
        # Minimum roof of ~24 estimated blocks, tapering toward open
        # passages by 64 blocks below local surface. Fade far underground
        # so the old deep-cave system retains its character.
        roof_penalty={"type":"minecraft:mul","argument1":0.018,
            "argument2":positive_part(subtract(64.0,depth),64.0)}
        deep_penalty={"type":"minecraft:mul","argument1":0.0025,
            "argument2":positive_part(subtract(depth,512.0),256.0)}
        # Sparse, wide chamber pockets. Unlike a general cheese-OR carve,
        # the chamber must pass BOTH a rare low-frequency threshold and
        # the local-depth roof/floor penalties. Connectors share the
        # same final cave envelope as narrow spaghetti corridors.
        chamber={"type":"minecraft:add",
                 "argument1":{"type":"minecraft:noise",
                              "noise":"minecraft:cave_cheese",
                              "xz_scale":0.16,"y_scale":0.12},
                 "argument2":0.84}
        chamber_roof={"type":"minecraft:mul","argument1":0.05,
                      "argument2":positive_part(subtract(96.0,depth),96.0)}
        chamber_deep={"type":"minecraft:mul","argument1":0.004,
                      "argument2":positive_part(subtract(depth,448.0),256.0)}
        chamber_guard={"type":"minecraft:add",
            "argument1":{"type":"minecraft:add",
                         "argument1":chamber,"argument2":chamber_roof},
            "argument2":chamber_deep}
        # Chamber pockets are sparse; a second broad noise mask prevents
        # a single cheese field from hollowing an entire mountain.
        # A chamber only opens when BOTH fields permit carving.
        chamber_mask={"type":"minecraft:add",
            "argument1":{"type":"minecraft:abs",
                "argument":{"type":"minecraft:noise",
                    "noise":"minecraft:spaghetti_3d_2",
                    "xz_scale":0.12,"y_scale":0.10}},
            "argument2":-0.27}
        bounded_chamber={"type":"minecraft:max",
            "argument1":chamber_guard,"argument2":chamber_mask}
        connector={"type":"minecraft:min",
                   "argument1":corridor,"argument2":bounded_chamber}
        guarded={"type":"minecraft:add",
                 "argument1":{"type":"minecraft:add",
                              "argument1":connector,"argument2":roof_penalty},
                 "argument2":deep_penalty}
        # Retain original cave noise and refuse extra cutting where
        # original terrain is not solid. The safety envelope is
        # continuous and derived from local surface height.
        final=noise["noise_router"]["final_density"]
        noise["noise_router"]["final_density"]={
            "type":"minecraft:min","argument1":final,
            "argument2":{"type":"minecraft:max",
                "argument1":{"type":"minecraft:mul","argument1":-1.0,
                    "argument2":positive_part(
                        {"type":"minecraft:mul","argument1":-1.0,
                         "argument2":terrain_depth},1.0)},
                "argument2":guarded}}
    # Temperature drives the VANILLA MultiNoise biome sampler.
    # It is specific to this new world noise_settings ID, never a
    # dimension-wide Lithostitched injector affecting unrelated worlds.
    # minecraft:y_clamped_gradient means actual biome sampling altitude,
    # not the static height of a hill at sea level. Lowland temperature is
    # unchanged; high-altitude quart-biome samples are cooled smoothly.
    if climate or valley or basin or (latitude != "off" and latitude_strength):
        temperature_id=density_id+"/biome_temperature"
        temperature_before=copy.deepcopy(noise["noise_router"]["temperature"])
        cool=round(climate*0.009,6)
        noise["noise_router"]["temperature"]=temperature_id
    if inland:
        for target in noise.get("spawn_target",[]):
            if "continentalness" in target:
                target["continentalness"][0]=0.12
    # The original Terrain Lab used legacy presets and VANILLA Nether/End.
    # Every NEW generated RealScale profile must inherit the merged three realms
    # and ocean-aware ecotones, never silently regress to vanilla dimensions.
    realscale_families={
        "landscape":"tva_sacred_timeline_three_realms",
        "alpine":"tva_alpine_three_realms",
        "canyon":"tva_canyon_three_realms",
    }
    if preset in realscale_families:
        original_preset=jread(root,GEO_ROOT/"world_preset"/(realscale_families[preset]+".json"))
    else:
        original_preset=jread(root,GEO_ROOT/"world_preset"/(source+".json"))
    world_preset=copy.deepcopy(original_preset)
    world_preset["dimensions"]["minecraft:overworld"]["generator"]["settings"]="realscale:"+name
    # Legacy fantasyDimensions drafts remain readable, but must not downgrade
    # the RealScale Grand Nether/End from 512 blocks back to 256.
    # The optional 256-height fantasy presets remain in the registry as
    # independent historical experiments, never replacing the canonical RealScale presets.
    if fantasy_dimensions and preset in realscale_families:
        assert world_preset["dimensions"]["minecraft:the_nether"]["generator"]["settings"]=="realscale:grand_nether"
        assert world_preset["dimensions"]["minecraft:the_end"]["generator"]["settings"]=="realscale:grand_end"
    elif fantasy_dimensions:
        raise ValueError("Legacy fantasy dimension overrides require RealScale family settings")
    output={
        GEO_ROOT/"density_function"/density_folder/"height_signal.json":height,
        GEO_ROOT/"density_function"/density_folder/"terrain_density.json":terrain,
        GEO_ROOT/"density_function"/density_folder/"preliminary_density.json":preliminary,
        GEO_ROOT/"density_function"/density_folder/"cave_density.json":new_caves,
        GEO_ROOT/"noise_settings"/(name+".json"):noise,
        GEO_ROOT/"world_preset"/(name+".json"):world_preset,
    }
    if lithostitched_geology:
        output[Path("data/realscale/lithostitched/fast_noise_config/terrain_lab")
            /(name+".json")]={"type":"lithostitched:perlin",
                               "frequency":0.0015,"salt":731947}
    if climate or valley or basin or (latitude != "off" and latitude_strength):
        # Preserve the exact prior cooling-only object when all new options are off.
        temp_value=temperature_before
        if climate:
            temp_value={
                "type":"minecraft:add",
                "argument1":temperature_before,
                "argument2":{"type":"minecraft:mul",
                             "argument1":-cool,
                             "argument2":{"type":"minecraft:y_clamped_gradient",
                                          "from_y":96,
                                          "to_y":320,
                                          "from_value":0.0,
                                          "to_value":1.0}}
            }
        if latitude != "off" and latitude_strength:
            if latitude.startswith("radial_"):
                temp_value={
                    "type":"realscale:radial_latitude_temperature",
                    "base":temp_value,
                    "center_x":center_x,
                    "center_z":equator_z,
                    "pole_distance":10_001_966 if latitude=="radial_earth" else (8_192 if latitude in ("radial_balanced","radial_equal_area","radial_equal_area_single_pole") else 16_384),
                    "strength":round(latitude_strength/100.0,5),
                    **({"balanced":True,"altitude_cooling":cool} if latitude in ("radial_balanced","radial_equal_area","radial_equal_area_single_pole") else {}),
                    **({"area_balanced":True} if latitude in ("radial_equal_area","radial_equal_area_single_pole") else {}),
                    **({"single_pole_area":True} if latitude=="radial_equal_area_single_pole" else {})
                }
            else:
                # Preserve old North/South latitude semantics AND bytes.
                temp_value={
                    "type":"realscale:latitude_temperature",
                    "base":temp_value,
                    "equator_z":equator_z,
                    "pole_distance":10_001_966 if latitude=="earth" else 16_384,
                    "strength":round(latitude_strength/100.0,5)
                }
        if valley:
            # Sample height-signal differences across neighboring points,
            # not every Y<96 block. Inland + valley relief gate the warmth.
            # Never wrap legacy/foreign worlds' temperature registry.
            temp_value={
                "type":"realscale:valley_temperature",
                "base":temp_value,
                "height_signal":new_height,
                "continents":copy.deepcopy(noise["noise_router"]["continents"]),
                "warmth":round(valley*0.0035,6),
                "radius":96
            }
        if basin:
            # Basin is a topographic enclosure proxy: this does NOT carve a
            # new basin; compare the terrain against eight surrounding samples.
            temp_value={
                "type":"realscale:basin_temperature",
                "base":temp_value,
                "height_signal":new_height,
                "continents":copy.deepcopy(noise["noise_router"]["continents"]),
                "warmth":round(basin*0.0035,5),
                "radius":192
            }
        if latitude.startswith("radial_") and latitude_strength and cold_limit:
            # Limit the *combined* high-altitude + polar cold correction
            # relative to the original biome temperature; keep tropical
            # lowlands available. This caps math, not target biome fractions.
            temp_value={
                "type":"realscale:climate_budget",
                "original":temperature_before,
                "adjusted":temp_value,
                "max_cooling":round(cold_limit/100.0,5),
                "max_warming":0.45
            }
        output[GEO_ROOT/"density_function"/density_folder/"biome_temperature.json"]=temp_value
    return name,options,output

def apply(root, preset, relief, elevation, caves, river_grade, inland=True, dry_run=False, blend_percent=25, ridge=0, erosion=0, strata=0, climate=0, valley=0, latitude="off", latitude_strength=70, equator_z=0, basin=0, center_x=0, cold_limit=60, fantasy_dimensions=False, lithostitched_blend=False, lithostitched_geology=False):
    name,options,output=files_for(root,preset,relief,elevation,caves,river_grade,inland,blend_percent,ridge,erosion,strata,climate,valley,latitude,latitude_strength,equator_z,basin,center_x,cold_limit,fantasy_dimensions,lithostitched_blend,lithostitched_geology)
    paths={path:serialized(obj) for path,obj in output.items()}
    preset_id="realscale:"+name
    for tag in ("normal","extended"):
        path=TAG_ROOT/(tag+".json")
        obj=jread(root,path)
        if preset_id not in obj["values"]:
            obj["values"].append(preset_id)
        paths[path]=serialized(obj)
    for lang,names in (("en_us",ENGLISH),("zh_cn",CHINESE)):
        path=LABEL_ROOT/(lang+".json")
        labels=jread(root,path)
        label=(f"RealScale Lab | {names[preset]} "
               f"| relief {relief:.2f}, height {elevation:+d}, caves {caves}, river {river_grade}"
               if lang=="en_us"
               else f"地形实验室 | {names[preset]} | 起伏 {relief:.2f}, 高差 {elevation:+d}, 洞穴 {caves}, 河流 {river_grade}")
        if preset in FAMILIES:
            label+=f" | mix {blend_percent}%"
            if ridge or erosion or strata:
                label+=f" | ridge {ridge}, erosion {erosion}, strata {strata}"
            if climate:
                label+=f" | altitude climate {climate}%"
            if valley:
                label+=f" | valley warming {valley}%"
            if latitude != "off" and latitude_strength:
                label+=f" | latitude {latitude} {latitude_strength}%"
                if latitude.startswith("radial_"):
                    label+=f" | center ({center_x},{equator_z}), cold limit {cold_limit}%"
            if basin:
                label+=f" | basin warmth {basin}%"
            if fantasy_dimensions:
                label+= " | Experimental variant (keeps 512-block Grand Nether/End)"
            if lithostitched_blend and blend_percent and not lithostitched_geology:
                label+= " | Lithostitched adaptive mix"
            if lithostitched_geology:
                label+= " | Lithostitched Deep Geology"
        labels["generator.realscale."+name]=label
        paths[path]=serialized(labels)
    # Never silently modify a previously generated worldgen codec: if a
    # profile already exists, its complete serialized settings stay identical.
    for path,obj in output.items():
        dest=root/path
        if dest.exists() and dest.read_text(encoding="utf-8")!=serialized(obj):
            raise ValueError("Existing terrain profile must not be overwritten: "+str(dest))
    if not dry_run:
        for path,body in paths.items():
            dest=root/path
            dest.parent.mkdir(parents=True,exist_ok=True)
            if not dest.exists() or dest.read_text(encoding="utf-8")!=body:
                dest.write_text(body,encoding="utf-8")
    return name,options,list(paths)

def gui(root, launch):
    try:
        import tkinter as tk
        from tkinter import ttk,messagebox
    except ImportError as ex:
        raise RuntimeError("This Python has no tkinter; use CLI options instead") from ex
    app=tk.Tk()
    app.title("RealScale Terrain Lab | NeoForge 1.21.1")
    app.geometry("600x900")
    main=ttk.Frame(app,padding=20)
    main.pack(fill="both",expand=True)
    ttk.Label(main,text="RealScale Terrain Lab",font=("",18,"bold")).pack(anchor="w",pady=(0,8))
    ttk.Label(main,text="Each Apply creates a new world preset. Existing worlds are unchanged.").pack(anchor="w")
    preset=tk.StringVar(value="landscape")
    profile=tk.StringVar(value="natural")
    relief=tk.DoubleVar(value=1.0)
    elevation=tk.IntVar(value=0)
    caves=tk.IntVar(value=50)
    grade=tk.IntVar(value=6)
    blend=tk.IntVar(value=25)
    ridge=tk.IntVar(value=0)
    erosion=tk.IntVar(value=0)
    strata=tk.IntVar(value=0)
    climate=tk.IntVar(value=65)
    valley=tk.IntVar(value=0)
    latitude=tk.StringVar(value="off")
    latitude_strength=tk.IntVar(value=70)
    equator_z=tk.IntVar(value=0)
    center_x=tk.IntVar(value=0)
    cold_limit=tk.IntVar(value=60)
    basin=tk.IntVar(value=0)
    inland=tk.BooleanVar(value=True)
    fantasy=tk.BooleanVar(value=False)
    lithomix=tk.BooleanVar(value=False)
    lithogeology=tk.BooleanVar(value=False)
    def chooser(label,variable,values):
        ttk.Label(main,text=label).pack(anchor="w",pady=(9,0))
        box=ttk.Combobox(main,textvariable=variable,values=values,state="readonly")
        box.pack(fill="x")
        return box
    chooser("Original terrain mode",preset,EDITABLE)
    chooser("Suggested tuning mode",profile,tuple(PROFILES))
    def slider(label,variable,frm,to,resolution=1):
        ttk.Label(main,text=label).pack(anchor="w",pady=(9,0))
        frame=ttk.Frame(main)
        frame.pack(fill="x")
        tk.Scale(frame,from_=frm,to=to,resolution=resolution,orient="horizontal",
                 showvalue=True,variable=variable,length=440).pack(side="left",fill="x",expand=True)
    slider("Terrain relief multiplier",relief,0.8,1.25,.01)
    slider("Secondary landform feature mix (%)",blend,0,50,1)
    ttk.Checkbutton(main,text="Lithostitched adaptive blend (new world)",variable=lithomix).pack(anchor="w",pady=(7,0))
    ttk.Checkbutton(main,text="Lithostitched Deep Geology (new world)",variable=lithogeology).pack(anchor="w",pady=(7,0))
    slider("Coherent ridge shaping (0..40)",ridge,0,40,1)
    slider("Noise-driven incisional erosion (0..30)",erosion,0,30,1)
    slider("Warped vertical strata solidity (0..20)",strata,0,20,1)
    slider("Altitude-aware biome cooling (0..100)",climate,0,100,1)
    slider("Optional valley-floor warming (0..100)",valley,0,100,5)
    chooser("Latitude climate cycle",latitude,("off","earth","preview","radial_earth","radial_preview","radial_balanced","radial_equal_area","radial_equal_area_single_pole"))
    slider("Latitude temperature strength (0..100)",latitude_strength,0,100,5)
    slider("Equator origin Z / ring center Z",equator_z,-4096,4096,128)
    slider("Concentric ring center X",center_x,-4096,4096,128)
    slider("Concentric maximum cooling budget (0 = off)",cold_limit,0,100,5)
    slider("Optional enclosed-basin warming (0..100)",basin,0,100,5)
    slider("Elevation offset (blocks)",elevation,-48,48)
    slider("Cave activity (higher = more caves)",caves,20,80)
    chooser("Safe river grade: Off=0, Steep-safe=3, Normal=6, Flexible=9",
            grade,(0,3,6,9))
    ttk.Checkbutton(main,text="Prefer inland spawn climate",variable=inland).pack(anchor="w",pady=(7,0))
    ttk.Checkbutton(main,text="Experimental: enlarge Nether and End for NEW worlds",variable=fantasy).pack(anchor="w",pady=(7,0))
    def change_profile(*_):
        relief.set(PROFILES[profile.get()][0])
        elevation.set(PROFILES[profile.get()][1])
        caves.set(PROFILES[profile.get()][2])
        grade.set(PROFILES[profile.get()][3])
    profile.trace_add("write",change_profile)
    result={"done":False}
    def save():
        try:
            name,_,_=apply(root,preset.get(),round(relief.get(),2),elevation.get(),caves.get(),grade.get(),inland.get(),blend_percent=blend.get(),ridge=ridge.get(),erosion=erosion.get(),strata=strata.get(),climate=climate.get(),valley=valley.get(),latitude=latitude.get(),
                    latitude_strength=latitude_strength.get(),equator_z=equator_z.get(),basin=basin.get(),
                    center_x=center_x.get(),cold_limit=cold_limit.get(),fantasy_dimensions=fantasy.get(),lithostitched_blend=lithomix.get(),lithostitched_geology=lithogeology.get())
        except Exception as ex:
            messagebox.showerror("Terrain Lab",str(ex))
            return
        result["done"]=True
        messagebox.showinfo("New world preset ready",
            "Created: "+name+"\n\nRestart/rebuild the Minecraft client and select the "
            "RealScale Lab preset under Create New World > World Type.\n"
            "This DOES NOT alter a world already generated.")
        app.destroy()
    ttk.Button(main,text="Create selectable world preset",command=save).pack(fill="x",pady=(18,4))
    app.mainloop()
    return result["done"] and launch

def main(argv=None):
    parser=argparse.ArgumentParser(description="Generate a selectable RealScale world preset with real numeric controls")
    parser.add_argument("--gui",action="store_true",help="Open graphical profile chooser")
    parser.add_argument("--from-yacl",type=Path,default=None,
                        help="Import a saved YACL draft; creates a NEW immutable world preset")
    parser.add_argument("--list",action="store_true",help="List preset and tuning choices")
    parser.add_argument("--preset",choices=PRESETS+tuple(FAMILIES),default="landscape")
    parser.add_argument("--profile",choices=PROFILES,default="natural")
    parser.add_argument("--relief",type=float,default=None)
    parser.add_argument("--blend",type=int,default=25,help="Secondary landform mix (0-50%) for merged modes")
    parser.add_argument("--ridge",type=int,default=0,help="Opt-in ridgeline density perturbation (0-40)")
    parser.add_argument("--erosion",type=int,default=0,help="Opt-in erosion density perturbation (0-30)")
    parser.add_argument("--strata",type=int,default=0,help="Opt-in vertical strata solidity (0-20)")
    parser.add_argument("--biome-cooling",type=int,default=0,
                        help="Opt-in height-aware temperature biome signal (0..100)")
    parser.add_argument("--valley-warming",type=int,default=0,
                        help="Opt-in bounded relative-valley temperature boost (0..100)")
    parser.add_argument("--latitude-mode",choices=("off","earth","preview","radial_earth","radial_preview","radial_balanced","radial_equal_area","radial_equal_area_single_pole"),default="off",
                        help="meridional or concentric latitude; Earth = 1m/block, preview = pole at radius 16,384 blocks")
    parser.add_argument("--latitude-strength",type=int,default=70,help="Latitude climate strength 0..100")
    parser.add_argument("--equator-z",type=int,default=0,
                        help="World Z of latitude zero (or radial ring center Z). Not the future auto-selected spawn.")
    parser.add_argument("--radial-center-x",type=int,default=0,
                        help="World X center of radial latitude rings; Z center is --equator-z")
    parser.add_argument("--cold-limit",type=int,default=60,
                        help="Radial maximum combined cold correction (0=off, default 60%); prevents polar+high-altitude overcooling")
    parser.add_argument("--basin-warming",type=int,default=0,
                        help="Enclosed basin temperature proxy 0..100, default off")
    parser.add_argument("--elevation",type=int,default=None)
    parser.add_argument("--caves",type=int,default=None)
    parser.add_argument("--river-grade",type=int,choices=(0,3,6,9),default=None)
    parser.add_argument("--coastal-spawn",action="store_true",help="Do not prefer inland world spawn")
    parser.add_argument("--fantasy-dimensions",action="store_true",help="Experimental 256-block Nether and multi-tier End (new Lab worlds only)")
    parser.add_argument("--lithostitched-blend",action="store_true",help="Opt-in Lithostitched:mix spatial blending in NEW merged Lab worlds")
    parser.add_argument("--lithostitched-geology",action="store_true",help="Opt-in seeded Perlin, shifted terrain, layered geology and regional cave blending")
    parser.add_argument("--dry-run",action="store_true")
    parser.add_argument("--launch",action="store_true",help="Launch runClient after generating a preset")
    parser.add_argument("--root",type=Path,default=None,help=argparse.SUPPRESS)
    args=parser.parse_args(argv)
    if args.list:
        print("Main merged modes:",", ".join(FAMILIES))
        print("Measured (read-only): usgs_big_sur_v2; original legacy DEM: usgs_big_sur")
        print("Legacy compatible algorithms:",", ".join(LEGACY_EDITABLE))
        print("Tuning modes:",", ".join(PROFILES))
        print("Adjustable: mix 0..50%, ridge 0..40, erosion 0..30, strata 0..20, biome cooling 0..100, valley warming 0..100, latitude mode off/earth/preview/radial_earth/radial_preview/radial_balanced/radial_equal_area/radial_equal_area_single_pole, latitude strength 0..100, equator-z, basin warming 0..100, relief 0.80..1.25, elevation -48..48, caves 20..80, river grade 0/3/6/9.")
        print("USGS Big Sur v2 is the read-only measured DEM. RealScale's 512-block dimension layouts remain enabled.")
        print("Optional Lithostitched terrain blending: --lithostitched-blend (merged families).")
        print("Deep geology suite: --lithostitched-geology (nonzero merged blend required).")
        return 0
    root=args.root or resource_root()
    if args.from_yacl is not None:
        raw=json.loads(args.from_yacl.read_text(encoding="utf-8"))
        if not isinstance(raw,dict):
            raise ValueError("Terrain Lab YACL file must contain one JSON object")
        # Be strict, do not silently create worlds from malformed UI settings.
        if type(raw.get("reliefPercent")) is not int:
            raise ValueError("YACL reliefPercent must be an integer")
        if type(raw.get("elevation")) is not int or type(raw.get("caves")) is not int:
            raise ValueError("YACL elevation and caves must be integers")
        if type(raw.get("riverGrade")) is not int or type(raw.get("inland")) is not bool:
            raise ValueError("YACL riverGrade and inland must have their declared types")
        if not isinstance(raw.get("preset"),str):
            raise ValueError("YACL preset must be a string")
        name,opts,files=apply(root,raw["preset"],raw["reliefPercent"]/100.0,
            raw["elevation"],raw["caves"],raw["riverGrade"],raw["inland"],args.dry_run,
            raw.get("blendPercent",25),
            raw.get("ridgePercent",0),raw.get("erosionPercent",0),raw.get("strataPercent",0),
            raw.get("climatePercent",0),raw.get("valleyPercent",0),
            raw.get("latitudeMode","off"),raw.get("latitudeStrength",70),raw.get("equatorZ",0),
            raw.get("basinPercent",0),raw.get("radialCenterX",0),raw.get("coldLimitPercent",60),
            raw.get("fantasyDimensions",False),raw.get("lithostitchedBlend",False),
            raw.get("lithostitchedGeology",False))
        print("YACL draft compiled to new world preset: realscale:"+name)
        print("Worldgen resource files:",len(files))
        return 0
    if args.gui:
        launch=gui(root,args.launch)
    else:
        defaults=PROFILES[args.profile]
        relief=round(args.relief if args.relief is not None else defaults[0],3)
        elevation=args.elevation if args.elevation is not None else defaults[1]
        caves=args.caves if args.caves is not None else defaults[2]
        grade=args.river_grade if args.river_grade is not None else defaults[3]
        name,opts,files=apply(root,args.preset,relief,elevation,caves,grade,
                              not args.coastal_spawn,args.dry_run,args.blend,args.ridge,args.erosion,args.strata,args.biome_cooling,args.valley_warming,
                              args.latitude_mode,args.latitude_strength,args.equator_z,args.basin_warming,
                              args.radial_center_x,args.cold_limit,args.fantasy_dimensions,args.lithostitched_blend,args.lithostitched_geology)
        print("Prepared world type: realscale:"+name)
        print("Settings:",json.dumps(opts,sort_keys=True))
        print("World preset files:",len(files))
        print("Apply to a NEW WORLD only. Select it in Create World > World Type.")
        launch=args.launch and not args.dry_run
    if launch:
        work=root.parents[2] # resource root: neoforge/src/main/resources -> neoforge
        if not (work/"build.gradle").exists():
            raise RuntimeError("Expected build.gradle at "+str(work))
        return subprocess.call(["gradle","--no-daemon","--console=plain","runClient"],cwd=work)
    return 0

if __name__=="__main__":
    try:sys.exit(main())
    except (ValueError,RuntimeError,FileNotFoundError) as e:
        print("Terrain Lab error:",str(e),file=sys.stderr)
        sys.exit(1)
