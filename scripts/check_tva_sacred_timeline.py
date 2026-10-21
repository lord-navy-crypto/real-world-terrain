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
assert src["source"]["type"]=="realscale:rare_earth_biomes_v1"
assert src["source"]["source"]==b["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
for name in ("minecraft:the_nether","minecraft:the_end"):
    assert v["dimensions"][name]==b["dimensions"][name]

FAMILIES={
 "tva_sacred_timeline_three_realms":"realscale:tva_natural_smoothed",
 "tva_alpine_three_realms":"realscale:tva_alpine_smoothed",
 "tva_canyon_three_realms":"realscale:tva_canyon_smoothed",
 "tva_measured_three_realms":"realscale:usgs_big_sur_v2",
}
menu=[
 "realscale:tva_natural_hypsometry_v1_three_realms",
 "realscale:tva_measured_global_landforms_three_realms",
 "realscale:tva_alpine_three_realms",
 "realscale:tva_canyon_three_realms",
]
for tag_name in ("normal","extended"):
    tag=read(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"/(tag_name+".json"))
    assert tag["replace"] is False and tag["values"]==menu,(tag_name,tag)
    assert len(tag["values"])==len(set(tag["values"]))==4
for name,settings in FAMILIES.items():
    world=read(D/"worldgen/world_preset"/(name+".json"))
    assert world["dimensions"]["minecraft:overworld"]["generator"]["settings"]==settings,name
    for dim,setting in (("minecraft:the_nether","realscale:grand_nether"),
                        ("minecraft:the_end","realscale:grand_end")):
        assert world["dimensions"][dim]["generator"]["settings"]==setting,name
    source=world["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    assert source["type"]=="realscale:sky_ocean"
    assert source["source"]["type"]=="realscale:rare_earth_biomes_v1"

# Player worlds share recovered old features but retain different terrain routers.
for identifier in menu:
    world=read(D/"worldgen/world_preset"/(identifier.split(":",1)[1]+".json"))
    source=world["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
    chain=[]
    while "source" in source:
        chain.append(source["type"])
        source=source["source"]
    chain.append(source["type"])
    assert chain==["realscale:sky_ocean","realscale:rare_earth_biomes_v1",
                   "realscale:geo_wonders_opt_in","realscale:cross_realm_free_spawn_v1",
                   "realscale:natural_ecotones_diverse_v3","minecraft:multi_noise"], identifier
    nether=world["dimensions"]["minecraft:the_nether"]["generator"]["biome_source"]
    end=world["dimensions"]["minecraft:the_end"]["generator"]["biome_source"]
    assert nether["type"]=="realscale:cross_realm_invasion",identifier
    assert end["type"]=="realscale:end_accents" and end["source"]["type"]=="realscale:cross_realm_invasion",identifier
# All historical worldgen IDs remain on disk, but cannot pollute the new-world menu.
for old in ("ultimate","realistic_mountains","himalayan","grand_canyon",
"continental","georeal_ultimate","realism_max","usgs_big_sur",
"usgs_big_sur_v2","realism_max_ecotones","realism_max_three_realms",
"grand_ecology_three_realms","apes_gallery_three_realms"):
    assert (D/"worldgen/world_preset"/(old+".json")).is_file(),old
    assert "realscale:"+old not in menu,old
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
rare={"geothermal_sulfur_basin","socotra_dragonblood_woodland",
      "white_dune_lagoon","sundarbans_tidal_mangrove","congo_peat_swamp_forest",
      "steppe_saline_lake","acidic_geothermal_mudflat","montane_mist_forest",
      "permafrost_fen","limestone_island_scrub","namib_fog_desert",
      "serpentine_barrens","socotra_endemic_scrub"}
assert rare <= all_biomes
assert len(all_biomes-new_end-v2-v3-rare)==67
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
print("TVA_STATIC_PASS: FOUR player-facing modes, shared legacy features, distinct terrain, rare ecology, Grand Nether/End and sky islands")
