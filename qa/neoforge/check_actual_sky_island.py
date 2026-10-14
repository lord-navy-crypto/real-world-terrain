#!/usr/bin/env python3
"""Check actual Anvil palettes for the exclusive sky biome AND solid island blocks."""
import gzip, sys, zlib, time
from pathlib import Path
root=Path(sys.argv[1])
regions=list((root/"region").glob("*.mca"))
assert regions, "No forced skyland Anvil regions found"
def scan():
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
    return total, biome_chunks, solid_chunks, found

deadline=time.monotonic()+100
while True:
    total,biome_chunks,solid_chunks,found=scan()
    print("FORCED_SKYLANDS_PALETTE_STATS",{"chunks":total,"sky_biome_chunks":biome_chunks,
        "floating_island_block_chunks":solid_chunks,"seen":sorted(found)},flush=True)
    if total >= 100 and biome_chunks > 0 and solid_chunks > 0: break
    if time.monotonic() >= deadline:
        raise AssertionError("Sky biome or island blocks absent after Anvil save flush")
    time.sleep(5)
# Validate vertical geology at the deterministic test island center, not
# just palette membership (palettes alone cannot prove connected structures).
from realscale_headless_qa import iter_region_chunks, get_block
center_chunk = None
for chunk in iter_region_chunks(root / "region/r.0.0.mca"):
    if chunk.get("xPos") == 2 and chunk.get("zPos") == 3:
        center_chunk = chunk
        break
assert center_chunk is not None, "Island center chunk (2,3) missing"
def block(y):
    return get_block(center_chunk, 0, y, 15)  # world X=32, Z=63
expected = {
    174: {"minecraft:air"},
    175: {"minecraft:stone","minecraft:end_stone"},
    178: {"minecraft:stone","minecraft:end_stone"},
    181: {"minecraft:dirt"},
    182: {"minecraft:dirt"},
    183: {"minecraft:moss_block","minecraft:grass_block"},
    184: {"minecraft:amethyst_block"},
    185: {"minecraft:sea_lantern"},
    186: {"minecraft:air"},
}
for y, allowed in expected.items():
    observed = block(y)
    assert observed in allowed, (y, observed, sorted(allowed))
sky_sections = [section for section in center_chunk.get("sections", [])
                if "realscale:sky_blossom_isles" in section.get("biomes", {}).get("palette", [])]
assert any(section.get("Y") == 11 for section in sky_sections), "Sky biome missing at Y176..191"
print("PASS: verified real floating geological column and sky-specific biome at X32/Z63")
print("PASS: actual generated sky biome and solid fantasy island terrain")
