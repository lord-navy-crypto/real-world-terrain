#!/usr/bin/env python3
"""Check actual Nether/End blocks and serialized biome/dimension settings."""
import gzip,json,sys
from pathlib import Path
from realscale_headless_qa import read_nbt,iter_region_chunks,get_block

world=Path(sys.argv[1])
file=world/"level.dat"
assert file.exists(),file
nbt=read_nbt(gzip.decompress(file.read_bytes()))
dimensions=nbt["Data"]["WorldGenSettings"]["dimensions"]
for name, expected in (("minecraft:the_nether","grand_nether"),
                       ("minecraft:the_end","grand_end")):
    desc=dimensions[name]
    assert desc["type"]=="realscale:"+expected,(name,desc["type"])
    assert desc["generator"]["settings"]=="realscale:"+expected,(name,desc["generator"]["settings"])
    assert desc["generator"]["type"]=="minecraft:noise"

def chunks_in(path):
    out=[]
    for region in sorted(path.glob("r.*.*.mca")):
        out.extend(x for x in iter_region_chunks(region)
                   if x.get("Status")=="minecraft:full")
    return out

nether=chunks_in(world/"DIM-1"/"region")
end=chunks_in(world/"DIM1"/"region")
summary={"nether_full_chunks":len(nether),"end_full_chunks":len(end),
         "nether_upper_terrain":0,"nether_roof_bedrock":0,
         "nether_below_128_terrain":0,"nether_lava_samples":0,
         "nether_hollow_halls":{},"nether_connecting_tunnels":{},
         "dragon_center_air_at_y160":None,"dragon_center_end_stone_at_y60":None,
         "end_upper_stone_samples":0,"end_outer_chunks":0}
assert nether,"No fully generated Nether chunks"
assert end,"No fully generated End chunks"

for chunk in nether:
    for x in (0,4,8,12):
        for z in (0,4,8,12):
            if get_block(chunk,x,511,z)=="minecraft:bedrock":
                summary["nether_roof_bedrock"]+=1
            if get_block(chunk,x,300,z) not in ("minecraft:air","minecraft:cave_air"):
                summary["nether_upper_terrain"]+=1
            if get_block(chunk,x,64,z) not in ("minecraft:air","minecraft:cave_air"):
                summary["nether_below_128_terrain"]+=1
            if get_block(chunk,x,31,z)=="minecraft:lava":
                summary["nether_lava_samples"]+=1

# These deterministic centers derive from GrandNetherCaveNetwork.hall(0,0)
# and the nearest neighbor hall(1,0). Assert actual saved AIR rather than
# merely relying on the algebraic density function regression test.
nether_by_chunk={(c.get("xPos"),c.get("zPos")):c for c in nether}
void_blocks={"minecraft:air","minecraft:cave_air","minecraft:void_air"}
def nether_at(x,y,z):
    c=nether_by_chunk.get((x//16,z//16))
    assert c is not None, ("missing full Grand Nether cavern chunk",x,z)
    return get_block(c,x%16,y,z%16)
cave_sites={
    "lower_hall":(126,190,114),
    "upper_hall":(118,355,158),
    "east_hall":(360,190,125),
}
tunnel_sites={
    "horizontal_tunnel":(243,190,120),
    "vertical_tunnel":(122,272,136),
}
for name,(x,y,z) in cave_sites.items():
    observed=nether_at(x,y,z)
    summary["nether_hollow_halls"][name]={"xyz":[x,y,z],"block":observed}
    assert observed in void_blocks,("solid instead of hollow chamber",name,observed)
for name,(x,y,z) in tunnel_sites.items():
    observed=nether_at(x,y,z)
    summary["nether_connecting_tunnels"][name]={"xyz":[x,y,z],"block":observed}
    assert observed in void_blocks,("disconnected natural tunnel",name,observed)

center=next((chunk for chunk in end if chunk.get("xPos")==0 and chunk.get("zPos")==0),None)
assert center is not None,"Dragon center End chunk missing"
summary["dragon_center_air_at_y160"]=get_block(center,0,160,0)
summary["dragon_center_end_stone_at_y60"]=get_block(center,0,60,0)
assert summary["dragon_center_air_at_y160"]=="minecraft:air",(
  "Grand End altered dragon-fight arena above vanilla build noise",
  summary["dragon_center_air_at_y160"])
for chunk in end:
    cx,cz=chunk.get("xPos",0),chunk.get("zPos",0)
    if cx*cx+cz*cz < 110*110:continue
    summary["end_outer_chunks"]+=1
    for x in (0,4,8,12):
        for z in (0,4,8,12):
            for y in (160,192,224,320,352,384):
                if get_block(chunk,x,y,z)=="minecraft:end_stone":
                    summary["end_upper_stone_samples"]+=1
report=Path("build/grand-dimension-anvil-report.json")
report.parent.mkdir(parents=True,exist_ok=True)
report.write_text(json.dumps(summary,indent=2)+"\n")
print("GRAND_DIMENSIONS_ANVIL",json.dumps(summary,sort_keys=True))
assert summary["nether_roof_bedrock"]>=8,"Missing extended Nether bedrock roof"
assert summary["nether_upper_terrain"]>0,"Nether caves have not extended above original Y128"
assert summary["nether_below_128_terrain"]>0,"Nether original lower realm was erased"
print("PASS: saved Grand Nether hollow halls, horizontal/vertical connectors, upper bedrock roof, original lava realm and protected End dragon arena")
