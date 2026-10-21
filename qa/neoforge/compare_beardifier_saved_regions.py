#!/usr/bin/env python3
"""Compare saved heightmaps and blocks in the same actual virgin chunks.

A matched A/B timing is not sufficient if a structure's Beardifier contribution
is lost. Compare persisted Anvil region NBT in exact far-route chunks.
"""
import glob
import pathlib
import struct
import sys
import zlib
import gzip

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from realscale_headless_qa import read_nbt

WAYPOINTS=((1024,1024),(-2048,1536),(4096,-2048))
def find_region(project,rx,rz):
    root=pathlib.Path(project)
    paths=list(root.glob(f"run/**/region/r.{rx}.{rz}.mca"))
    paths+=list(root.glob(f"run/server/**/region/r.{rx}.{rz}.mca"))
    paths+=list(root.glob(f"run/gametestserver/**/region/r.{rx}.{rz}.mca"))
    paths=list(dict.fromkeys(paths))
    if len(paths)!=1:raise AssertionError(f"Expected one saved Anvil region {rx},{rz}: {paths}")
    return paths[0]
def load_chunk(root,x,z):
    cx=x//16;cz=z//16;rx=cx//32;rz=cz//32
    data=find_region(root,rx,rz).read_bytes()
    slot=(cx%32)+(cz%32)*32
    offset=int.from_bytes(data[slot*4:slot*4+3],"big")*4096
    if not offset:raise AssertionError(f"Missing saved chunk {cx},{cz}")
    size=int.from_bytes(data[offset:offset+4],"big")
    typ=data[offset+4]
    compressed=data[offset+5:offset+4+size]
    raw=zlib.decompress(compressed) if typ==2 else gzip.decompress(compressed) if typ==1 else compressed
    return read_nbt(raw)
def normalized(nbt):
    return {"sections":nbt.get("sections",[]),
            "Heightmaps":nbt.get("Heightmaps",{}),
            "structures":nbt.get("structures",{})}
def compare(before,after):
    for x,z in WAYPOINTS:
        a=normalized(load_chunk(before,x,z));b=normalized(load_chunk(after,x,z))
        if a!=b:raise AssertionError(f"Beardifier optimization altered persisted heightmap, biome, block states or structures at {x},{z}")
        print(f"REALSCALE_BEARDIFIER_ANVIL_BLOCK_PARITY_PASS: {x} {z}",flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3:raise SystemExit("usage: compare_beardifier_saved_regions.py before-neoforge after-neoforge")
    compare(sys.argv[1],sys.argv[2])
