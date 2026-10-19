#!/usr/bin/env python3
"""Static TVA integrated preset contract, not an actual saved-world proof."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]
D=R/"neoforge/src/main/resources/data/realscale"
def read(p): return json.loads(p.read_text(encoding="utf-8"))
v=read(D/"worldgen/world_preset/tva_sacred_timeline_three_realms.json")
b=read(D/"worldgen/world_preset/grand_ecology_three_realms.json")
src=v["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
assert src["type"]=="realscale:sky_ocean"
assert src["source"]==b["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
for name in ("minecraft:the_nether","minecraft:the_end"):
    assert v["dimensions"][name]==b["dimensions"][name]
FAMILIES={
 "tva_sacred_timeline_three_realms":"realscale:tva_natural_smoothed",
 "tva_alpine_three_realms":"realscale:tva_alpine_smoothed",
 "tva_canyon_three_realms":"realscale:tva_canyon_smoothed",
 "tva_measured_three_realms":"realscale:usgs_big_sur_v2",
}
expected=["realscale:"+k for k in FAMILIES]
# Preserve the exact four stable entries and expose experimental weaving only
# as a separately selected fifth world, never by mutating a canonical preset.
opt_in="realscale:tva_wonders_invasion_three_realms"
menu=expected+[opt_in]
# Preserve exact first five original choices, and append two NEW opt-in
# biodiversity profiles to the normal menu only. Extended remains unchanged.
diverse=["realscale:tva_sacred_biodiversity_end3_spawn3_v2",
         "realscale:tva_measured_biodiversity_end3_spawn3_v2"]
diverse_v3=["realscale:tva_sacred_biodiversity_end3_spawn3_v3",
            "realscale:tva_measured_biodiversity_end3_spawn3_v3"]
for name in ("normal","extended"):
    tag=read(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"/(name+".json"))
    selected=menu+(diverse+diverse_v3+["realscale:tva_regional_landform_expedition_three_realms","realscale:tva_regional_landform_expedition_v2_three_realms","realscale:tva_global_landform_expedition_v3_three_realms","realscale:tva_measured_global_landforms_three_realms"] if name=="normal" else [])
    assert tag["replace"] is False and tag["values"]==selected,(name,tag)
    assert len(tag["values"])==len(set(tag["values"]))==len(selected)
for name,settings in FAMILIES.items():
    world=read(D/"worldgen/world_preset"/(name+".json"))
    assert world["dimensions"]["minecraft:overworld"]["generator"]["settings"]==settings,name
    for dim in ("minecraft:the_nether","minecraft:the_end"):
        assert world["dimensions"][dim]==b["dimensions"][dim],name
    assert world["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]==src,name
# All historical worldgen IDs remain on disk, but cannot pollute the new-world menu.
for old in ("ultimate","realistic_mountains","himalayan","grand_canyon",
"continental","georeal_ultimate","realism_max","usgs_big_sur",
"usgs_big_sur_v2","realism_max_ecotones","realism_max_three_realms",
"grand_ecology_three_realms","apes_gallery_three_realms"):
    assert (D/"worldgen/world_preset"/(old+".json")).is_file(),old
    assert "realscale:"+old not in expected,old
assert (D/"worldgen/noise_settings/usgs_big_sur_v2.json").is_file()
assert (D/"worldgen/density_function/realism_max/cave_core.json").is_file()
cave=read(D/"worldgen/density_function/usgs_big_sur_v2/cave_density.json")
assert cave["argument1"]["argument1"]["argument1"]=="realscale:realism_max/cave_core"
for part in ("terrain_density","preliminary_density","cave_density"):
    assert (D/"worldgen/density_function/usgs_big_sur_v2"/(part+".json")).is_file(),part

catalog=read(R/"neoforge/ecotones/catalog.json")["entries"]
assert len(catalog)==60
all_biomes={p.stem for p in (D/"worldgen/biome").glob("*.json")}
new_end={"end_chorus_gardens","end_crystal_barrens","end_shattered_glades"}
assert new_end <= all_biomes
v2={"tropical_dry_woodland","cool_temperate_rainforest",
    "alpine_sedge_fen","polar_fellfield",
    "arid_riparian_woodland","windy_coastal_headland"}
assert v2 <= all_biomes
v3={"mediterranean_chaparral","temperate_tallgrass_prairie",
    "monsoon_woodland","montane_cloud_scrub",
    "brackish_tidal_marsh","coastal_pine_barrens",
    "frost_heath_moor","arid_rocksteppe"}
assert v3 <= all_biomes
assert len(all_biomes-new_end-v2-v3)==67
for e in catalog:
    assert (D/"worldgen/biome"/(e["id"]+".json")).exists(),e["id"]
guard=R/"neoforge/src/main/java/org/realscale/neoforge/TVAWorldGuard.java"
assert guard.is_file()
assert "generator.generatorSettings().unwrapKey()" in guard.read_text()
for f in ("GrandEcologyFeature","GroundMicrostructureFeature","HabitatPatchFeature"):
    impl=(R/"neoforge/src/main/java/org/realscale/neoforge"/(f+".java")).read_text()
    assert "TVAWorldGuard.hasRealScaleGenerator(context)" in impl,f
code=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
for symbol in ("grand_nether_caverns","grand_end_density","sky_ocean",
"sky_blossom_island","coastal_driftwood","tropical_rainforest"):
    assert '"'+symbol+'"' in code,symbol
assert (R/"neoforge/src/main/java/org/realscale/neoforge/RealDemColumnCache.java").exists()
build=(R/"neoforge/build.gradle").read_text()
meta=(R/"neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text()
assert "2.0.0-tva-preview.1" in build and "2.0.0-tva-preview.1" in meta
assert "RealScale-TVA-Ultimate" in build and "Sacred Timeline Ultimate" in meta
for p in (D/"neoforge/biome_modifier").glob("*.json"):
    m=read(p)
    if m.get("type")!="neoforge:add_features":continue
    features=m["features"] if isinstance(m["features"],list) else [m["features"]]
    assert "realscale:ocean_sediment_stones" not in features,p
print("TVA_STATIC_PASS: FOUR canonical families + one opt-in woven world, two additive v2 profiles, 60 historical ecotones + six new, APES, Grand Nether/End and skylands")
