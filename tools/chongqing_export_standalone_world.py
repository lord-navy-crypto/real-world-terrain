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
SOURCE_MASK_RADIUS=500
ORIGINAL_GIS_RADIUS=500
NAME={0:"minecraft:air",1:"minecraft:stone",2:"minecraft:dirt",
      3:"minecraft:grass_block",4:"minecraft:light_gray_concrete",
      5:"minecraft:gray_concrete",6:"minecraft:polished_andesite",
      7:"minecraft:blue_concrete",
      8:"minecraft:smooth_stone",9:"minecraft:stone_bricks",
      10:"minecraft:cobblestone",11:"minecraft:gravel",
      12:"minecraft:coarse_dirt",13:"minecraft:oak_planks",
      14:"minecraft:sand",15:"minecraft:mud",
      16:"minecraft:white_concrete",
      17:"minecraft:light_blue_stained_glass",
      18:"minecraft:smooth_quartz"}
MATERIAL={"building_footprint":4,"road":5,"railway":6,"water_reference":7}

def schematic_facade_block(x:int,z:int,offset:int,height:int)->int:
    """Generic neutral glazing, NOT a surveyed Chongqing window layout.

    Only source-tagged taller buildings receive a glass/white grid.
    OSM geometry and source height are unchanged; the decorative
    mullion pattern is an openly acknowledged artistic approximation.
    """
    if height<30:
        return 4  # small buildings intentionally neutral until surveyed
    if offset%8 in (0,1) or (x+z)%9==0:
        return 16  # white structural-looking bands, not true load bearings
    return 17  # blue-tinted glass, source-supported only as generic city style
SURFACE_BLOCK={
    "asphalt":5,"paving":8,"concrete":4,
    "cobble":10,"gravel":11,"dirt":12,"grass":3,
    "wooden_paving":13,"sand":14,"mud":15,
    # LOW confidence: the OSM category is commercial/residential land-use;
    # this surface is a clearly documented visual approximation, not a
    # surveyed real paving-stone material.
    "urban_inferred":8,
}
SURFACE_FORMAT="REALSCALE_CHAOTIANMEN_OSM_SURFACE_V1"

def read_surface_mask(file: Path, dem_sha: str) -> tuple[dict,dict]:
    if not file.is_file() or file.stat().st_size>12*1024*1024:
        raise ValueError("Missing/oversize attributed real OSM surface overlay")
    with gzip.open(file,"rb") as f:
        raw=f.read(35*1024*1024+1)
    if len(raw)>35*1024*1024:
        raise ValueError("OSM surface decompression exceeds safety cap")
    data=json.loads(raw)
    source_radius=data.get("radius_m")
    if (data["format"]!=SURFACE_FORMAT or
            source_radius not in (500,600) or source_radius>RADIUS
            or data["resolution_m"]!=1 or
            data["dem_source_raw_sha256"]!=dem_sha or
            data["origin_lat"]!=29.5667 or data["origin_lon"]!=106.5874 or
            data["license"]!="ODbL-1.0" or
            not __import__("re").fullmatch(
                r"[0-9a-f]{64}",data.get("raw_osm_api_sha256",""))):
        raise ValueError("Misaligned or unlicensed surface overlay")
    if not (0<len(data["rows"])<=1000000):
        raise ValueError("Surface rows missing or beyond pilot cap")
    lookup={}
    for item in data["rows"]:
        z,x0,x1,material,confidence,source_id=item
        if (not all(isinstance(v,int) for v in (z,x0,x1)) or
                min(z,x0,x1)<-source_radius or
                max(z,x0,x1)>source_radius or
                x1<x0 or material not in SURFACE_BLOCK or
                confidence not in ("low","medium","high") or
                not (source_id.startswith("way/") or source_id.startswith("relation/"))):
            raise ValueError("Invalid surface OSM raster row")
        for x in range(x0,x1+1):
            if (x,z) in lookup:
                raise ValueError("Duplicated OSM surface cell")
            lookup[(x,z)]=SURFACE_BLOCK[material]
    if len(lookup)!=data["known_surface_columns"]:
        raise ValueError("OSM surface count integrity mismatch")
    return lookup,data


def read_copernicus_mask(path: Path, dem_sha: str) -> tuple[dict,dict]:
    """Strict satellite-evidence mask. 10m imagery is NOT 1m detail."""
    if not path.is_file() or path.stat().st_size>12*1024*1024:
        raise ValueError("Missing or oversized open-data Sentinel-2 mask")
    with gzip.open(path,"rb") as f:
        raw=f.read(36*1024*1024)
    if len(raw)>=36*1024*1024:
        raise ValueError("Oversized Sentinel-2 ground mask")
    data=json.loads(raw)
    satellite_radius=data.get("radius_m")
    if (data.get("format")!="REALSCALE_CHAOTIANMEN_S2_LANDCOVER_V1"
            or satellite_radius not in (500,600)
            or satellite_radius>RADIUS
            or data.get("resolution_m")!=10
            or data.get("dem_source_raw_sha256")!=dem_sha
            or data.get("origin_lat")!=29.5667
            or data.get("origin_lon")!=106.5874
            or data.get("license")!="Copernicus Sentinel free and open data"):
        raise ValueError("Unlicensed/misaligned satellite landcover evidence")
    item=data.get("source_stac_item","")
    if not isinstance(item,str) or len(item)<10 or len(item)>250:
        raise ValueError("Missing Sentinel-2 image provenance")
    result={}
    for row in data["rows"]:
        z,x0,x1,material,confidence,source=row
        if (not all(type(n) is int for n in (z,x0,x1))
                or not(-satellite_radius<=z<=satellite_radius
                        and -satellite_radius<=x0<=x1<=satellite_radius)
                or material not in ("grass","urban_inferred")
                or confidence not in ("low","medium")
                or source!="sentinel-2-l2a/"+item):
            raise ValueError("Malformed or unsupported 10m landcover row")
        for x in range(x0,x1+1):
            if (x,z) in result:
                raise ValueError("Overlapping satellite-ground cells")
            result[(x,z)]=SURFACE_BLOCK[material]
    if len(result)!=data["known_surface_columns"]:
        raise ValueError("Sentinel-2 ground count doesn't match provenance")
    return result,data


ROAD_FORMAT="REALSCALE_CHAOTIANMEN_ROAD_CORRIDORS_V1"

def read_road_corridors(path: Path, dem_sha: str):
    """Accept only geo-aligned ODbL highway footprints with audited widths."""
    if not path.is_file() or path.stat().st_size > 12*1024*1024:
        raise ValueError("Missing or oversized REAL OSM road-corridor mask")
    with gzip.open(path,"rb") as reader:
        raw=reader.read(35*1024*1024+1)
    if len(raw)>35*1024*1024:
        raise ValueError("OSM road footprint archive unbounded")
    src=json.loads(raw)
    road_radius=src.get("radius_m")
    if (src.get("format")!=ROAD_FORMAT
            or road_radius not in (500,600) or road_radius>RADIUS
            or src.get("resolution_m")!=1
            or src.get("origin_lat")!=29.5667
            or src.get("origin_lon")!=106.5874
            or src.get("dem_source_raw_sha256")!=dem_sha
            or src.get("license")!="ODbL-1.0"
            or not __import__("re").fullmatch(
                r"[0-9a-f]{64}",src.get("raw_osm_api_sha256",""))):
        raise ValueError("Invalid georeference or origin license for roads")
    if not 0<len(src.get("rows",[]))<=300000:
        raise ValueError("Empty or excess road source rows")
    positions={}
    for r in src["rows"]:
        if len(r)!=7:raise ValueError("Invalid road provenance row size")
        z,x0,x1,material,confidence,source_id,evidence=r
        if (not all(type(n)==int for n in (z,x0,x1))
                or not(-road_radius<=z<=road_radius
                       and -road_radius<=x0<=x1<=road_radius)
                or confidence not in ("low","medium","high")
                or not source_id.startswith("way/")
                or evidence not in (
                    "osm_width_m","osm_lanes_estimate","highway_class_inference")
                or material not in SURFACE_BLOCK):
            raise ValueError("Malformed road source material/provenance")
        for x in range(x0,x1+1):
            if (x,z) in positions:
                raise ValueError("Overlapping OSM road mask; expected unique run")
            positions[(x,z)]=SURFACE_BLOCK[material]
    if len(positions)!=src["classified_road_surface_cells"]:
        raise ValueError("OSM road source coverage count mismatch")
    return positions,src


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

def generate(world, surface_file=None, satellite_file=None, road_file=None,
             add_crystal=False):
    grid,plan=read_inputs()
    surface_lookup,surface_meta=({},None)
    satellite_lookup,satellite_meta=({},None)
    roads_lookup,roads_meta=({},None)
    if road_file is not None:
        roads_lookup,roads_meta=read_road_corridors(
            Path(road_file),plan["dem_source_raw_sha256"])
    if surface_file is not None:
        surface_lookup,surface_meta=read_surface_mask(
            Path(surface_file),plan["dem_source_raw_sha256"])
    if satellite_file is not None:
        satellite_lookup,satellite_meta=read_copernicus_mask(
            Path(satellite_file),plan["dem_source_raw_sha256"])

    # A real OSM material marker is written BEFORE roads and buildings.
    # Existing pinned OSM V2 structure blocks retain priority.

    region_dir=world/"region"
    region_dir.mkdir(parents=True,exist_ok=True)
    for old in region_dir.glob("r.*.*.mca"):old.unlink()
    index=overlay_index(plan)
    # T1 was outside the original 500m V2 plan. Resolve genuine outer
    # tower geometry BEFORE buffering all 600m roads so new roads never
    # paint over its entrance/base or contradict the source tower mask.
    extra_writes=[]
    extra_tower_metrics=None
    if RADIUS>ORIGINAL_GIS_RADIUS:
        from chongqing_extended_tower1 import build_real_outer_t1
        extra_writes,extra_tower_metrics=build_real_outer_t1(radius=RADIUS)
    # Do not accidentally pave through OSM building footprints, genuine
    # water reference locations or tracks. Streets with known road tags
    # never mutate this original 494,988-operation GIS source plan.
    protected=set()
    for row in plan["rows"]:
        if row["kind"]!="road":
            protected.update((x,row["z"]) for x in
                             range(row["x0"],row["x1"]+1))
    protected.update((x,z) for x,y,z,material in extra_writes)
    road_by_chunk=defaultdict(list)
    for (x,z),material in roads_lookup.items():
        if (x,z) not in protected:
            road_by_chunk[(x//16,z//16)].append((x,z,material))
    # The 3D Crystal shell is explicit opt-in: source OSM polygon XY and
    # min_height/height tags are real; a model rooftop median sets absolute
    # Y because geodetically registered building ground-level data is absent.
    # Do NOT insert a random 3D bridge across the river.
    crystal_by_chunk=defaultdict(list)
    crystal_metrics=None
    extra_tower_by_chunk=defaultdict(list)
    for x,y,z,material in extra_writes:
        extra_tower_by_chunk[(x//16,z//16)].append((x,y,z,material))
    if add_crystal:
        from chongqing_crystal_3d import build_crystal
        placements,crystal_metrics=build_crystal()
        for x,y,z,material in placements:
            crystal_by_chunk[(x//16,z//16)].append((x,y,z,material))
    regions={}
    stats=defaultdict(int)
    stats["road_corridor_cells_source"]=len(roads_lookup)
    stats["road_corridor_cells_protected"]=len(roads_lookup)-sum(
        len(rows) for rows in road_by_chunk.values())
    for cz in range(-math.ceil(RADIUS/16),RADIUS//16+1):
        for cx in range(-math.ceil(RADIUS/16),RADIUS//16+1):
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
                    ground=surface_lookup.get((x,z),
                              satellite_lookup.get((x,z),3))
                    set_block(lx,top,lz,ground)
                    if ground!=3:
                        stats["source_qualified_non_grass_ground_columns"]+=1
                    stats["dem_terrain_columns"]+=1
            # Wider true-OSM road corridors are a new visual surface pass:
            # explicit width > lane-count estimate > conservative class
            # default; bridge/tunnel geometry is DEFERRED, not flattened.
            # The original accurate source building and railway rows are
            # written afterwards and therefore retain ownership.
            for rx,rz,material in road_by_chunk.get((cx,cz),()):
                ry=math.ceil(elevation(grid,rx,rz))
                set_block(rx&15,ry,rz&15,material)
                stats["road_corridor_new_surface_writes"]+=1
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
                            facade=schematic_facade_block(x,z,dy,height)
                            set_block(lx,y+dy,localz,facade)
                            if facade==17:
                                stats["schematic_glazed_facade_blocks"]+=1
                            stats["actual_overlay_writes"]+=1
                        set_block(lx,y+height,localz,5)
                        stats["actual_overlay_writes"]+=1
                    elif shell=="roof":
                        set_block(lx,y+height,localz,5)
                        stats["actual_overlay_writes"]+=1
            # Elevated source-backed Crystal geometry after the original
            # 2D tower overlay. No floors generated beyond OSM 3D tag span.
            for x,y,z,material in crystal_by_chunk.get((cx,cz),()):
                set_block(x&15,y,z&15,material)
                stats["crystal_schematic_block_writes"]+=1
            for x,y,z,material in extra_tower_by_chunk.get((cx,cz),()):
                set_block(x&15,y,z&15,material)
                stats["extra_t1_source_building_writes"]+=1
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
    stats["gis_source_overlay_radius_m"]=SOURCE_MASK_RADIUS
    if extra_tower_metrics is not None:
        stats["expanded_real_t1_evidence"]=extra_tower_metrics
        stats["expanded_extra_landcover_note"]="full 600m official OSM road and surface tags if supplied; 600m satellite georeference; pinned 500m primary building GIS + verified extra T1"
    if crystal_metrics is not None:
        stats["crystal_3d_evidence"]=crystal_metrics
        stats["crystal_standalone_building_mode"]="real OSM polygon/min_height/height + LOW-confidence vertical anchor + schematic glass enclosure"
    if roads_meta is not None:
        stats["osm_road_corridor_sha256"]=roads_meta["raw_osm_api_sha256"]
        stats["osm_road_corridor_radius_m"]=roads_meta["radius_m"]
        stats["osm_road_corridor_width_note"]=roads_meta["width_note"]
        stats["osm_road_corridor_deferred"]=roads_meta["deferred_road_types"]
        stats["osm_road_corridor_feature_count"]=roads_meta["osm_road_type_count"]
    if satellite_meta is not None:
        stats["satellite_evidence_used_after_exact_osm_precedence"]=sum(
            1 for pos in satellite_lookup if pos not in surface_lookup)
        stats["sentinel_source_stac_item"]=satellite_meta["source_stac_item"]
        stats["sentinel_resolution_m"]=satellite_meta["resolution_m"]
        stats["satellite_classified_extent_radius_m"]=satellite_meta["radius_m"]
        stats["sentinel_classified_columns"]=satellite_meta["known_surface_columns"]
        stats["satellite_license"]=satellite_meta["license"]
        stats["satellite_warning"]="10m broad vegetation vs bare ground, NOT exact 1m surface or 3D geometry; OSM supersedes pixels"
    if surface_meta is not None:
        stats["surface_source_sha256"]=surface_meta["raw_osm_api_sha256"]
        stats["osm_surface_radius_m"]=surface_meta["radius_m"]
        stats["ground_surface_osm_source_url"]=surface_meta.get("source_url")
        stats["source_classified_ground_columns"]=surface_meta["known_surface_columns"]
        stats["surface_class_count"]=surface_meta["materials"]
        stats["unknown_ground_columns"]=surface_meta["unmapped_surface_columns"]
        stats["surface_accuracy_note"]="Exact OSM tagged materials where available; LOW confidence urban landuse schematic elsewhere; no satellite photo pixels embedded."
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
        # Vanilla 1.21.1 accepts a literal integer. A nested provider
        # with "value" is invalid for this dimension codec; verified by
        # booting the OFFICIAL Mojang 1.21.1 dedicated server.
        "monster_spawn_light_level":7,
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

def package_world(root,out,surface_file=None,satellite_file=None,road_file=None,
                  add_crystal=False):
    world=root/WORLD
    if not (world/"level.dat").is_file():
        raise RuntimeError("No vanilla 1.21.1 generated level.dat. Bootstrap the server first.")
    stats=generate(world,surface_file,satellite_file,road_file,add_crystal)
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
    data["LevelName"]=nbtlib.String("Chaotianmen Chongqing "+str(RADIUS)+"m - TRUE OSM Prototype")
    file.save(world/"level.dat",gzipped=True)
    # Do not package Mojang runtime, server settings or private debug files.
    notice=(
      "CHONGQING CHAOTIANMEN -- INDEPENDENT MINECRAFT JAVA 1.21.1 WORLD\n"
      "No RealScale mod required; unzip this folder directly inside saves/.\n"
      "REAL DATA: © OpenStreetMap contributors (ODbL 1.0), pinned OSM way geometries.\n"
      "Source DEM: same checksum-verified elevation dataset as RealScale.\n"
      "GROUND: verified OSM surface/polygon landuse where available; Copernicus\n"
      "Sentinel-2 10m vegetation/bare-surface classification elsewhere.\n"
      "Satellite pixels have 10m resolution, NOT exact 1m photographed material.\n"
      "Some unknown ground remains grass: this is a research prototype.\n"
      "Open satellite source: Copernicus Sentinel-2 L2A, permitted to adapt.\n"
      "ROAD WIDTHS: OSM tagged real widths where present, otherwise\n"
      "provisional lane-count or road-class estimate, NOT surveyed edge.\n"
      "Bridge/tunnel/overpass roads are deferred, NOT grounded at river.\n"
      "Crystal: TRUE official OSM geographic footprint (way/661717188),\n"
      "min_height=250m and total height=276.5m; glass tubular architecture\n"
      "is a low-confidence schematic, absolute Y anchored to median tower\n"
      "rooftop, NOT a surveyed measured altitude or accurate glass count.\n"
      "Image source ID: "+str(stats.get("sentinel_source_stac_item","none"))+"\n"
      "Contains modified Copernicus Sentinel data [2025].\n"

      "Map XY: 1 metre = 1 block, X east, Z south, origin lat 29.5667 lon 106.5874.\n"
      "Y: true DEM elevation +96, custom vanilla datapack world height -64..959.\n"
      "Buildings are source-footprint schematics with hollow interior, NOT full facades.\n"
      "Source-height towers now use LOW-confidence generic white-framed blue\n"
      "glass facades: these are NOT surveyed window sizes or real elevations.\n"
      "The Crystal is NOW a 3D schematic: original real OSM XY/height tags,\n"
      "but absolute Y and curved glass facade remain estimated. All other\n"
      "multilevel road bridges, true river water heights and unknown structures\n"
      "are DEFERRED. Blue concrete is a WATER REFERENCE, not a river.\n"
      "Experimental "+str(RADIUS)+"m halfwidth square, outside = void. Use CREATIVE only.\n"
      "The real Copernicus 10m landcover mask covers full +/-"+str(RADIUS)+"m.\n"
      "Official OSM surface and road sources cover +/-"+
      str(stats.get("osm_surface_radius_m",500))+"m and +/-"+
      str(stats.get("osm_road_corridor_radius_m",500))+"m respectively.\n"
      "Core building polygons are from 500m archival OSM; verified T1\n"
      "added from real 600m extra geometry. Other building massing incomplete.\n"
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
    cli.add_argument("--extent-radius",type=int,choices=[500,600],
                     default=500,help="500m original or 600m map containing true OSM T1")
    cli.add_argument("--server-root",type=Path,required=True)
    cli.add_argument("--zip",type=Path)
    cli.add_argument("--osm-surface-mask",type=Path)
    cli.add_argument("--satellite-mask",type=Path)
    cli.add_argument("--osm-road-corridors",type=Path)
    cli.add_argument("--enable-crystal-schematic",action="store_true")
    args=cli.parse_args()
    global RADIUS,WORLD
    RADIUS=args.extent_radius
    WORLD=("Chaotianmen_600m_Standalone" if RADIUS==600
           else "Chaotianmen_500m_Standalone")
    root=args.server_root.resolve()
    root.mkdir(parents=True,exist_ok=True)
    if args.mode=="prepare":prepare_server(root)
    else:
        if args.zip is None:cli.error("--zip is required for packaging")
        package_world(root,args.zip.resolve(),args.osm_surface_mask,
                      args.satellite_mask,args.osm_road_corridors,
                      args.enable_crystal_schematic)

if __name__=="__main__":main()
