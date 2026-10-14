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

# The forced ocean biome alone does not guarantee submerged terrain. Check the
# actual saved ocean world independently below sea level, and forbid any sky
# archipelago palette at Y48..63 (ocean and structures stay vanilla).
ocean_chunks = 0
submerged_columns = 0
original_structure_starts = 0
for region_path in regions:
    for chunk in iter_region_chunks(region_path):
        section = next((section for section in chunk.get("sections", [])
                        if section.get("Y") == 3), None)
        if section is None: continue
        biomes = section.get("biomes", {}).get("palette", [])
        assert biomes == ["minecraft:deep_ocean"], (
            "original ocean biome was overwritten below sea level",
            chunk.get("xPos"),chunk.get("zPos"),biomes)
        ocean_chunks += 1
        for lx in (0,4,8,12):
            for lz in (0,4,8,12):
                if get_block(chunk,lx,62,lz) == "minecraft:water":
                    submerged_columns += 1
        starts = chunk.get("structures",{}).get("starts",{})
        original_structure_starts += sum(
            1 for val in starts.values()
            if isinstance(val,dict) and val.get("id") not in ("INVALID","minecraft:empty"))
print("VANILLA_OCEAN_INTEGRITY",{"actual_ocean_chunks":ocean_chunks,
    "submerged_water_columns_at_y62":submerged_columns,
    "existing_structure_starts":original_structure_starts})
assert ocean_chunks >= 10, "No saved underwater ocean biome sections"
assert submerged_columns >= 10, "No submerged ocean columns detected"
print("PASS: original deep ocean biome preserved below sea level, real seawater present")

# Require every member of the maximal five-island archipelago to become a
# FULLY DECORATED chunk, not just a sky biome in a partially saved protochunk.
# The forced-deep-ocean fixture always qualifies for isolation tier 3.
candidates = ((32,63,183),(128,63,181),(-64,63,185),(32,159,186),(32,-33,186))
wanted = {(x//16,z//16): (x,z,top) for x,z,top in candidates}
found_chunks = {}
for path in regions:
    for ch in iter_region_chunks(path):
        key = (ch.get("xPos"),ch.get("zPos"))
        if key in wanted: found_chunks[key] = ch
assert len(found_chunks) == 5, (
    "Satellite chunks not generated; check CI RCON forceload responses",
    sorted(found_chunks), sorted(wanted))
for key, (x,z,top) in wanted.items():
    chunk = found_chunks[key]
    assert chunk.get("Status") == "minecraft:full", (key,chunk.get("Status"))
    lx,lz = x%16,z%16
    assert get_block(chunk,lx,top+1,lz) == "minecraft:amethyst_block", (x,z,top)
    assert get_block(chunk,lx,top+2,lz) == "minecraft:sea_lantern", (x,z,top)
    assert get_block(chunk,lx,top-10,lz) == "minecraft:air", (
        "Island underside must remain detached",x,z,top)
    sections = [v for v in chunk.get("sections",[])
                if "realscale:sky_blossom_isles" in v.get("biomes",{}).get("palette",[])]
    assert sections, (x,z,"missing satellite SKY biome")
print("PASS: five separate physically generated floating islands, 3D biomes, and detached undersides")
print("PASS: actual generated sky biome and solid fantasy island terrain")
