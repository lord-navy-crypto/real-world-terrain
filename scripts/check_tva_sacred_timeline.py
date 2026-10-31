#!/usr/bin/env python3
"""Static RealScale integrated preset contract, not an actual saved-world proof."""
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
expected=["realscale:"+k for k in ("natural_three_realms","alpine_three_realms","canyon_three_realms","glacial_three_realms","volcanic_three_realms","karst_three_realms","dune_three_realms")]
# Only the generic default and six data-fitted terrain modes are exposed to new worlds.
# The optional cave ecology experiment remains available but hidden.
assert (D/"worldgen/world_preset/tva_cave_ocean_ecology_test_three_realms.json").is_file()
for name in ("normal","extended"):
    tag=read(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"/(name+".json"))
    assert tag["replace"] is False and tag["values"]==expected,(name,tag)
    assert len(tag["values"])==len(set(tag["values"]))==7
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
# The separate TEST ecology expansion adds 2 cave IDs; stable main is 67.
new_biomes=all((D/"worldgen/biome"/(n+".json")).is_file()
    for n in ("glowing_moss_grottos","fossil_limestone_caves"))
assert len(list((D/"worldgen/biome").glob("*.json")))==(69 if new_biomes else 67)
for e in catalog:
    assert (D/"worldgen/biome"/(e["id"]+".json")).exists(),e["id"]
guard=R/"neoforge/src/main/java/org/realscale/neoforge/TVAWorldGuard.java"
assert guard.is_file()
assert "generator.generatorSettings().unwrapKey()" in guard.read_text()
for f in ("GrandEcologyFeature","GroundMicrostructureFeature","HabitatPatchFeature","CaveMarineHabitatFeature"):
    impl=(R/"neoforge/src/main/java/org/realscale/neoforge"/(f+".java")).read_text()
    assert "TVAWorldGuard.hasRealScaleGenerator(context)" in impl,f
code=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
for symbol in ("grand_nether_caverns","grand_end_density","sky_ocean",
"sky_blossom_island","coastal_driftwood","tropical_rainforest"):
    assert '"'+symbol+'"' in code,symbol
assert (R/"neoforge/src/main/java/org/realscale/neoforge/RealDemColumnCache.java").exists()
build=(R/"neoforge/build.gradle").read_text()
meta=(R/"neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text()
# Production remains the original preview. A separately versioned TEST
# branch deliberately changes its display name and version while retaining
# the same mod ID, dependencies, RealScale menu and dimensions.
import re
gradle_version=re.search(r"(?m)^version='([^']+)'",build)
mod_version=re.search(r'(?m)^version="([^"]+)"',meta)
assert gradle_version and mod_version
version=gradle_version.group(1)
assert version==mod_version.group(1),("Gradle/NeoForge version mismatch",version)
assert "RealScale-NeoForge-1.21.1" in build
if version.startswith("2.0.0-preview.2-test."):
    assert 'displayName="RealScale — Terrain, Caves & Oceans (Experimental)"' in meta
else:
    assert version=="2.0.0-tva-preview.1"
    assert "Sacred Timeline Ultimate" in meta
for p in (D/"neoforge/biome_modifier").glob("*.json"):
    m=read(p)
    if m.get("type")!="neoforge:add_features":continue
    features=m["features"] if isinstance(m["features"],list) else [m["features"]]
    assert "realscale:ocean_sediment_stones" not in features,p
print("REALSCALE_STATIC_PASS: one generic default + six DEM-fitted archetypes, legacy-safe IDs, hidden experiments, 60 ecotones, APES, Grand Nether/End and skylands")
