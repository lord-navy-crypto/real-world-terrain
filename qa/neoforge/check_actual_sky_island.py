#!/usr/bin/env python3
"""Check actual Anvil palettes for the exclusive sky biome AND solid island blocks."""
import gzip, sys, zlib
from pathlib import Path
root=Path(sys.argv[1])
regions=list((root/"region").glob("*.mca"))
assert regions, "No forced skyland Anvil regions found"
biome_chunks=0
solid_chunks=0
total=0
found=set()
for region in regions:
    data=region.read_bytes()
    for i in range(1024):
        offset=int.from_bytes(data[4*i:4*i+3],"big")*4096
        if offset <= 0 or offset+5 > len(data): continue
        size=int.from_bytes(data[offset:offset+4],"big")
        if size<=1 or offset+4+size>len(data): continue
        kind=data[offset+4]
        payload=data[offset+5:offset+4+size]
        if kind==2:raw=zlib.decompress(payload)
        elif kind==1:raw=gzip.decompress(payload)
        elif kind==3:raw=payload
        else:continue
        total+=1
        if b"realscale:sky_blossom_isles" in raw:
            biome_chunks+=1
        if b"minecraft:amethyst_block" in raw and b"minecraft:sea_lantern" in raw:
            solid_chunks+=1
        for s in [b"realscale:sky_blossom_isles",b"minecraft:amethyst_block",b"minecraft:sea_lantern"]:
            if s in raw:found.add(s.decode())
print("FORCED_SKYLANDS_PALETTE_STATS",{"chunks":total,"sky_biome_chunks":biome_chunks,
   "floating_island_block_chunks":solid_chunks,"seen":sorted(found)})
assert total>0 and biome_chunks>0 and solid_chunks>0, "Sky biome or real island beacon not found in generated world"
print("PASS: actual generated sky biome and solid fantasy island terrain")
