#!/usr/bin/env python3
"""Semantic persisted-Anvil block/biome/heightmap equality for a real A/B.

Minecraft blockstate palettes can use different storage orders despite identical
blocks. Decode each state index instead of comparing raw NBT lists. Also compare
a vanilla-vs-vanilla control world to detect scheduler nondeterminism.
"""
import pathlib
import sys
import zlib
import gzip

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from realscale_headless_qa import read_nbt

WAYPOINTS=((1024,1024),(-2048,1536),(4096,-2048))
MASK=(1<<64)-1

def region_path(project,rx,rz):
    root=pathlib.Path(project)
    hits=list(root.glob(f"run/**/region/r.{rx}.{rz}.mca"))
    hits=list(dict.fromkeys(hits))
    if len(hits)!=1:
        raise AssertionError(f"Expected one saved region {rx},{rz}: {hits}")
    return hits[0]

def load_chunk(root,x,z):
    cx,cz=x//16,z//16
    data=region_path(root,cx//32,cz//32).read_bytes()
    slot=cx%32 + (cz%32)*32
    off=int.from_bytes(data[slot*4:slot*4+3],"big")*4096
    if off==0:raise AssertionError(f"Missing chunk {cx},{cz}")
    size=int.from_bytes(data[off:off+4],"big")
    typ=data[off+4]
    body=data[off+5:off+4+size]
    raw=zlib.decompress(body) if typ==2 else gzip.decompress(body) if typ==1 else body
    return read_nbt(raw)

def ids_from_palette(container,blocks):
    palette=container.get("palette",[])
    if not palette:return ()
    if blocks:
        normalized=[(p["Name"],tuple(sorted(p.get("Properties",{}).items())))
                    for p in palette]
        size=4096;bits=max(4,(len(palette)-1).bit_length())
    else:
        normalized=[str(p) for p in palette]
        size=64;bits=max(1,(len(palette)-1).bit_length())
    if len(palette)==1:return (normalized[0],)*size
    data=container.get("data",[])
    per_long=64//bits;mask=(1<<bits)-1
    ret=[]
    for i in range(size):
        word=i//per_long
        if word>=len(data):
            raise AssertionError("Truncated NBT block/biome palette index data")
        index=((data[word]&MASK)>>((i%per_long)*bits))&mask
        if index>=len(normalized):raise AssertionError("Invalid blockstate palette index")
        ret.append(normalized[index])
    return tuple(ret)

def sections(nbt,blocks):
    values={}
    for sec in nbt.get("sections",[]):
        y=sec["Y"]
        container=sec.get("block_states" if blocks else "biomes",{})
        if container.get("palette"):
            values[y]=ids_from_palette(container,blocks)
    return values

def summarize_mismatch(a,b):
    mismatches=[]
    for kind in ("blocks","biomes"):
        lhs=sections(a,kind=="blocks");rhs=sections(b,kind=="blocks")
        count=0;example=None
        for sy in sorted(set(lhs)|set(rhs)):
            missing="minecraft:air" if kind=="blocks" else None
            left=lhs.get(sy,((missing,()) if kind=="blocks" else missing,)*(4096 if kind=="blocks" else 64))
            right=rhs.get(sy,((missing,()) if kind=="blocks" else missing,)*(4096 if kind=="blocks" else 64))
            for i,(x,y) in enumerate(zip(left,right)):
                if x!=y:
                    count+=1
                    if example is None:example=(sy,i,str(x)[:120],str(y)[:120])
        if count:mismatches.append((kind,count,example))
    for kind in ("Heightmaps","structures"):
        if a.get(kind,{})!=b.get(kind,{}):
            mismatches.append((kind,"NBT contents differ",None))
    return mismatches

def compare(before,after):
    failed=False
    for x,z in WAYPOINTS:
        a=load_chunk(before,x,z)
        b=load_chunk(after,x,z)
        mismatch=summarize_mismatch(a,b)
        if mismatch:
            print(f"REALSCALE_AQUIFER_ANVIL_MISMATCH {x},{z}: {mismatch}",flush=True)
            failed=True
        else:
            print(f"REALSCALE_AQUIFER_ANVIL_BLOCK_PARITY_PASS: {x} {z}",flush=True)
    if failed:
        raise AssertionError("Saved blocks/biomes/heightmaps/structure data differ; do not merge experimental Mixin")

if __name__=="__main__":
    if len(sys.argv)!=3:raise SystemExit("usage: compare_beardifier_saved_regions.py before after")
    compare(sys.argv[1],sys.argv[2])
