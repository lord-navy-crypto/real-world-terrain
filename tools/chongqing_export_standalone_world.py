#!/usr/bin/env python3
"""Build a REAL playable vanilla Minecraft 1.21.1 Chaotianmen world save.

Unlike the RealScale mod GIS GUI, this outputs actual fully populated
Anvil region/*.mca blocks; NO mod is needed to open the result. The height
datapack handles buildings above vanilla's normal 320-block build limit.
Pure stdlib for the block/region writer; nbtlib only to safely patch the
Mojang-generated level.dat during final packaging. First bootstrap an
empty vanilla server world (see .github/workflows/chongqing-standalone-map.yml).

The city is an EXPERIMENTAL source-faithful GIS schematic, NOT a complete
facade/cadastral survey. Only known OSM footprint geometries are placed.
"""
from __future__ import annotations
import argparse
import base64
import gzip
import hashlib
import json
import math
import shutil
import struct
import time
import zipfile
import zlib
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DEM=(ROOT/"neoforge/src/main/resources/assets/realscale/terrain/"
     "chongqing_chaotianmen_dem_v1.properties")
SOURCE=(ROOT/"neoforge/src/main/resources/assets/realscale/terrain/"
        "chaotianmen_authentic_osm_ui_plan_v2.json.gz.b64")
WORLD="Chaotianmen_500m_Standalone"
PLAN_HASH="04a380b144d3f002c0157d841edefb68d5a5bfacf6ca07149c43e8f03a858980"
MIN_Y=-64
HEIGHT=1024
DATA_VERSION=3955
SHELL=40
RADIUS=500
NAME={0:"minecraft:air",1:"minecraft:stone",2:"minecraft:dirt",
      3:"minecraft:grass_block",4:"minecraft:light_gray_concrete",
      5:"minecraft:gray_concrete",6:"minecraft:polished_andesite",
      7:"minecraft:blue_concrete"}
MATERIAL={"building_footprint":4,"road":5,"railway":6,"water_reference":7}

def read_inputs():
    props={}
    for line in DEM.read_text().splitlines():
        if not line or line.startswith("#"):continue
        k,sep,v=line.partition("=")
        if sep:props[k]=v
    raw=gzip.decompress(base64.b64decode(
        props["heights_gzip_base64"],validate=True))
    assert hashlib.sha256(raw).hexdigest()==props["source_raw_sha256"]
    n=int(props["width"])
    assert n==129 and int(props["spacing_m"])==64
    grid=struct.unpack(">"+str(n*n)+"h",raw)
    compressed=base64.b64decode(SOURCE.read_bytes().strip(),validate=True)
    json_bytes=gzip.decompress(compressed)
    if hashlib.sha256(json_bytes).hexdigest()!=PLAN_HASH:
        raise ValueError("Embedded authentic OSM V2 hash changed")
    plan=json.loads(json_bytes)
    if (plan["format"]!="REALSCALE_CHAOTIANMEN_PLACEMENT_V2"
            or plan["radius_m"]!=500
            or plan["dem_source_raw_sha256"]!=props["source_raw_sha256"]
            or plan["planned_block_writes"]!=494988):
        raise ValueError("Unsupported map, DEM, or GIS writing format")
    return grid,plan

def elevation(grid,x,z):
    # Local real DEM WGS84 georeferenced grid, identical to the mod.
    gx=(x+4096)/64;gz=(z+4096)/64
    ix=min(127,int(gx));iz=min(127,int(gz))
    u=gx-ix;v=gz-iz
    i=iz*129+ix
    a,b,c,d=grid[i],grid[i+1],grid[i+129],grid[i+130]
    return ((a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v)+96

# Lightweight standard Java NBT writer, byte-accurate big-endian.
def tag(tag_id,name,payload):
    encoded=name.encode("utf8")
    return bytes((tag_id,))+struct.pack(">H",len(encoded))+encoded+payload

def string(value):
    encoded=value.encode("utf8")
    return struct.pack(">H",len(encoded))+encoded

def compound(items):
    return b"".join(tag(kind,key,value) for kind,key,value in items)+b"\x00"

def list_payload(kind,items):
    return bytes((kind,))+struct.pack(">i",len(items))+b"".join(items)

def signed_longs(values):
    return struct.pack(">i",len(values))+b"".join(
        struct.pack(">q",v if v<2**63 else v-2**64) for v in values)

def packed(values,bits):
    per=64//bits
    result=[]
    for offset in range(0,len(values),per):
        word=0
        for shift,value in enumerate(values[offset:offset+per]):
            word|=int(value)<<(shift*bits)
        result.append(word)
    return signed_longs(result)

def section_payload(y,voxels):
    unique=sorted(set(voxels))
    if not unique or unique==[0]:return None
    local={value:i for i,value in enumerate(unique)}
    palette=list_payload(10,[
        compound([(8,"Name",string(NAME[index]))]) for index in unique])
    states=[(9,"palette",palette)]
    if len(unique)>1:
        bits=max(4,(len(unique)-1).bit_length())
        states.append((12,"data",packed([local[v] for v in voxels],bits)))
    biome=compound([(9,"palette",list_payload(8,[string("minecraft:plains")]))])
    return compound([(1,"Y",struct.pack(">b",y)),
                     (10,"block_states",compound(states)),
                     (10,"biomes",biome)])

def chunk_nbt(cx,cz,sections,highest):
    sec=[]
    for y,arr in sorted(sections.items()):
        payload=section_payload(y,arr)
        if payload is not None:sec.append(payload)
    heights=[max(0,v+1-MIN_Y) for v in highest]
    hm=packed(heights,(HEIGHT+1).bit_length())
    heightmaps=compound([
        (12,k,hm) for k in (
            "WORLD_SURFACE","MOTION_BLOCKING",
            "MOTION_BLOCKING_NO_LEAVES","OCEAN_FLOOR")])
    root=compound([
        (3,"DataVersion",struct.pack(">i",DATA_VERSION)),
        (3,"xPos",struct.pack(">i",cx)),
        (3,"yPos",struct.pack(">i",MIN_Y//16)),
        (3,"zPos",struct.pack(">i",cz)),
        (8,"Status",string("minecraft:full")),
        (4,"LastUpdate",struct.pack(">q",0)),
        (4,"InhabitedTime",struct.pack(">q",0)),
        (1,"isLightOn",b"\x00"),
        (10,"Heightmaps",heightmaps),
        (9,"sections",list_payload(10,sec)),
        (9,"block_entities",list_payload(10,[])),
        (9,"block_ticks",list_payload(10,[])),
        (9,"fluid_ticks",list_payload(10,[])),
        (9,"PostProcessing",list_payload(9,[])),
        (10,"structures",compound([
            (10,"starts",compound([])),
            (10,"References",compound([]))
        ]))
    ])
    return tag(10,"",root)

class Region:
    def __init__(self,path):
        self.path=path
        self.entries=[]
        self.sector=2
    def add(self,cx,cz,chunk):
        payload=zlib.compress(chunk,level=6)
        data=struct.pack(">I",len(payload)+1)+b"\x02"+payload
        sectors=(len(data)+4095)//4096
        if sectors>=256:raise ValueError("Minecraft region sector cap exceeded")
        slot=(cz%32)*32+(cx%32)
        self.entries.append((slot,self.sector,sectors,data))
        self.sector+=sectors
    def flush(self):
        header=bytearray(8192)
        epoch=int(time.time())
        with self.path.open("wb") as out:
            out.write(header)
            for slot,start,count,data in sorted(self.entries,key=lambda e:e[1]):
                header[slot*4:slot*4+4]=struct.pack(">I",(start<<8)|count)
                header[4096+slot*4:4100+slot*4]=struct.pack(">I",epoch)
                out.write(data)
                out.write(b"\x00"*((-len(data))%4096))
            out.seek(0);out.write(header)

def overlay_index(plan):
    output=defaultdict(list)
    for row in plan["rows"]:
        z=row["z"];x=row["x0"]
        while x<=row["x1"]:
            cx=x//16;end=min(row["x1"],(cx+1)*16-1)
            output[(cx,z//16)].append((row,x,end))
            x=end+1
    return output

def generate(world):
    grid,plan=read_inputs()
    region_dir=world/"region"
    region_dir.mkdir(parents=True,exist_ok=True)
    for old in region_dir.glob("r.*.*.mca"):old.unlink()
    index=overlay_index(plan)
    regions={}
    stats=defaultdict(int)
    for cz in range(-32,32):
        for cx in range(-32,32):
            sections={}
            highest=[MIN_Y-1]*256
            def set_block(lx,y,lz,block):
                if y<MIN_Y or y>=MIN_Y+HEIGHT:
                    raise ValueError("Vanilla custom build height exceeded")
                array=sections.setdefault(y//16,bytearray(4096))
                array[((y&15)<<8)|(lz<<4)|lx]=block
                slot=lz*16+lx
                if y>highest[slot]:highest[slot]=y

            for lz in range(16):
                z=cz*16+lz
                if not -RADIUS<=z<=RADIUS:continue
                for lx in range(16):
                    x=cx*16+lx
                    if not -RADIUS<=x<=RADIUS:continue
                    surface=elevation(grid,x,z)
                    top=math.ceil(surface)-1
                    bottom=math.floor(surface-SHELL)+1
                    for y in range(bottom,top-3):
                        set_block(lx,y,lz,1)
                    for y in range(max(bottom,top-3),top):
                        set_block(lx,y,lz,2)
                    set_block(lx,top,lz,3)
                    stats["dem_terrain_columns"]+=1
            for row,x0,x1 in index.get((cx,cz),()):
                y=row["y"];z=row["z"];localz=z&15
                height=row["height"];shell=row["shell"]
                block=MATERIAL[row["kind"]]
                for x in range(x0,x1+1):
                    lx=x&15
                    set_block(lx,y,localz,block)
                    stats["actual_overlay_writes"]+=1
                    if shell=="facade":
                        for dy in range(1,height):
                            set_block(lx,y+dy,localz,4)
                            stats["actual_overlay_writes"]+=1
                        set_block(lx,y+height,localz,5)
                        stats["actual_overlay_writes"]+=1
                    elif shell=="roof":
                        set_block(lx,y+height,localz,5)
                        stats["actual_overlay_writes"]+=1
            if not sections:continue
            region_key=(cx//32,cz//32)
            reg=regions.get(region_key)
            if reg is None:
                region_file=region_dir/("r.%d.%d.mca"%region_key)
                reg=regions[region_key]=Region(region_file)
            reg.add(cx,cz,chunk_nbt(cx,cz,sections,highest))
            stats["chunks"]+=1
        print(f"Processed chunk Z row {cz+33}/64",flush=True)
    for reg in regions.values():reg.flush()
    if stats["actual_overlay_writes"]!=plan["planned_block_writes"]:
        raise ValueError("Exported world overlay write count mismatch: "+
                         repr(dict(stats)))
    stats["regions"]=len(regions)
    stats["real_osm_rows"]=plan["row_count"]
    stats["real_osm_source_sha256"]=plan["vector_source_sha256"]
    stats["gis_plan_sha256"]=PLAN_HASH
    stats["dem_sha256"]=plan["dem_source_raw_sha256"]
    stats["terrain_policy"]="1m horizontal and 1m vertical, real DEM 40m floating shell"
    stats["building_policy"]="Source-footprint-based hollow facade and roof; schematic"
    stats["world_y_range"]=[MIN_Y,MIN_Y+HEIGHT-1]
    stats["world_xz_range"]=[-RADIUS,RADIUS]
    return stats

def prepare_server(root):
    world=root/WORLD
    world.mkdir(parents=True,exist_ok=True)
    pack=world/"datapacks"/"realscale_standalone_height_1024"
    dim=pack/"data"/"minecraft"/"dimension_type"
    dim.mkdir(parents=True,exist_ok=True)
    (pack/"pack.mcmeta").write_text(json.dumps({
        "pack":{"pack_format":48,
                "description":"Standalone Chaotianmen 1024-block vanilla Overworld height"}},indent=2))
    (dim/"overworld.json").write_text(json.dumps({
        "ultrawarm":False,"natural":True,"piglin_safe":False,
        "respawn_anchor_works":False,"bed_works":True,
        "has_raids":True,"has_skylight":True,"has_ceiling":False,
        "coordinate_scale":1.0,"ambient_light":0.0,
        "min_y":MIN_Y,"height":HEIGHT,"logical_height":HEIGHT,
        "infiniburn":"#minecraft:infiniburn_overworld",
        "effects":"minecraft:overworld",
        "monster_spawn_light_level":{"type":"minecraft:uniform",
            "value":{"min_inclusive":0,"max_inclusive":7}},
        "monster_spawn_block_light_limit":0
    },indent=2)+"\n")
    (root/"eula.txt").write_text("eula=true\n")
    (root/"server.properties").write_text(
        "level-name="+WORLD+"\n"
        "level-type=minecraft:flat\n"
        "generator-settings={\"biome\":\"minecraft:plains\",\"layers\":[],\"features\":false,\"lakes\":false,\"structure_overrides\":[]}\n"
        "gamemode=creative\nforce-gamemode=true\n"
        "difficulty=peaceful\nallow-flight=true\n"
        "online-mode=false\nspawn-protection=0\n"
        "view-distance=4\nsimulation-distance=4\n"
        "max-players=1\n"
        "max-tick-time=-1\n"
        "motd=RealScale Chaotianmen standalone experimental map\n")
    print("PREPARED empty official-vanilla server world and datapack",world)

def package_world(root,out):
    world=root/WORLD
    if not (world/"level.dat").is_file():
        raise RuntimeError("No vanilla 1.21.1 generated level.dat. Bootstrap the server first.")
    stats=generate(world)
    import nbtlib
    file=nbtlib.load(world/"level.dat")
    data=file["Data"]
    grid,_=read_inputs()
    spawn=math.ceil(elevation(grid,0,0))+6
    data["SpawnX"]=nbtlib.Int(0)
    data["SpawnY"]=nbtlib.Int(spawn)
    data["SpawnZ"]=nbtlib.Int(0)
    data["GameType"]=nbtlib.Int(1)
    data["hardcore"]=nbtlib.Byte(0)
    data["allowCommands"]=nbtlib.Byte(1)
    data["LevelName"]=nbtlib.String("Chaotianmen Chongqing 500m - TRUE OSM Prototype")
    file.save(world/"level.dat",gzipped=True)
    # Do not package Mojang runtime, server settings or private debug files.
    notice=(
      "CHONGQING CHAOTIANMEN -- INDEPENDENT MINECRAFT JAVA 1.21.1 WORLD\n"
      "No RealScale mod required; unzip this folder directly inside saves/.\n"
      "REAL DATA: © OpenStreetMap contributors (ODbL 1.0), pinned OSM way geometries.\n"
      "Source DEM: same checksum-verified elevation dataset as RealScale.\n"
      "Map XY: 1 metre = 1 block, X east, Z south, origin lat 29.5667 lon 106.5874.\n"
      "Y: true DEM elevation +96, custom vanilla datapack world height -64..959.\n"
      "Buildings are source-footprint schematics with hollow interior, NOT full facades.\n"
      "The Crystal elevated deck, road bridges, river heights and unknown structures\n"
      "are deliberately NOT fabricated. Blue concrete is a WATER REFERENCE only.\n"
      "Experimental 500m halfwidth square, outside = void. Use CREATIVE only.\n"
      "World and original template level.dat were created with official vanilla server.\n"
      "No mods, command blocks, cheats or external GIS files required to VIEW the map.\n"
      "Do not remove the included custom-height datapack from this world.\n"
      "Data credit: https://www.openstreetmap.org/copyright\n"
      "Upstream snapshot hash: "+stats["real_osm_source_sha256"]+"\n"
    )
    (world/"README-CHONGQING-REAL-OSM.txt").write_text(notice,encoding="utf-8")
    (world/"realscale-standalone-export-metrics.json").write_text(
        json.dumps(stats,ensure_ascii=False,indent=2)+"\n")
    out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED,compresslevel=8) as z:
        for path in sorted(world.rglob("*")):
            if not path.is_file() or path.name in ("session.lock","level.dat_old"):
                continue
            if ("logs" in path.parts or "playerdata" in path.parts
                    or "stats" in path.parts or "advancements" in path.parts):
                continue
            z.write(path,arcname=str(Path(WORLD)/path.relative_to(world)))
    stats["download_zip_bytes"]=out.stat().st_size
    print("STANDALONE_TRUE_OSM_WORLD_PASS",json.dumps(stats,ensure_ascii=False))
    return stats

def main():
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--mode",choices=["prepare","package"],required=True)
    cli.add_argument("--server-root",type=Path,required=True)
    cli.add_argument("--zip",type=Path)
    args=cli.parse_args()
    root=args.server_root.resolve()
    root.mkdir(parents=True,exist_ok=True)
    if args.mode=="prepare":prepare_server(root)
    else:
        if args.zip is None:cli.error("--zip is required for packaging")
        package_world(root,args.zip.resolve())

if __name__=="__main__":main()
