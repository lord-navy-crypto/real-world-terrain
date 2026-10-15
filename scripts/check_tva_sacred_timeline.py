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
assert "realscale:tva_sacred_timeline_three_realms" in read(
 R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")["values"]
catalog=read(R/"neoforge/ecotones/catalog.json")["entries"]
assert len(catalog)==60
assert len(list((D/"worldgen/biome").glob("*.json")))==67
for e in catalog:
    assert (D/"worldgen/biome"/(e["id"]+".json")).exists(),e["id"]
code=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
for symbol in ("grand_nether_caverns","grand_end_density","sky_ocean",
"sky_blossom_island","coastal_driftwood","tropical_rainforest"):
    assert '"'+symbol+'"' in code,symbol
assert (R/"neoforge/src/main/java/org/realscale/neoforge/RealDemColumnCache.java").exists()
for p in (D/"neoforge/biome_modifier").glob("*.json"):
    m=read(p)
    if m.get("type")!="neoforge:add_features":continue
    features=m["features"] if isinstance(m["features"],list) else [m["features"]]
    assert "realscale:ocean_sediment_stones" not in features,p
print("TVA_STATIC_PASS: 60 ecotones, APES, Grand Nether/End, optional skylands, DEM optimization")
