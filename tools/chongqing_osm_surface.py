#!/usr/bin/env python3
"""Verified OSM surface/land-use -> 1m Minecraft ground-material candidates.

This uses the OFFICIAL OpenStreetMap 0.6 API, not scraped Google Earth tiles.
The underlying raw XML SHA, URL, date, ODbL attribution and every contributing
OSM feature are recorded. Unmapped surface remains explicitly UNKNOWN.
Exact material tags have high confidence; commercial/residential inferred
hardscape has LOW confidence and is never called surveyed pavement.

This is an optional separate surface layer for the standalone VANILLA world:
it does not modify the pinned OSM V2 building GIS plan or TVA main branch.
"""
from __future__ import annotations
import argparse
import collections
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0,str(Path(__file__).resolve().parent))
from chongqing_osm_to_minecraft import Dem, DEFAULT_DEM, polygon_cells
from chongqing_fetch_osm_api import bounds

RADIUS=500
MAX_XML=8*1024*1024
FORMAT="REALSCALE_CHAOTIANMEN_OSM_SURFACE_V1"

# Surface mapping, NOT satellite RGB->exact real construction material.
SURFACES={
    "asphalt":("asphalt","high"),"paved":("paving","medium"),
    "concrete":("concrete","high"),"concrete:plates":("concrete","high"),
    "paving_stones":("paving","high"),"sett":("cobble","high"),
    "cobblestone":("cobble","high"),"compacted":("gravel","medium"),
    "fine_gravel":("gravel","high"),"gravel":("gravel","high"),
    "pebblestone":("gravel","medium"),"dirt":("dirt","high"),
    "earth":("dirt","high"),"ground":("dirt","high"),
    "grass":("grass","high"),"wood":("wooden_paving","medium"),
    "sand":("sand","high"),"mud":("mud","high")
}
AREA_CLASSES={
    ("leisure","park"):("grass","medium"),
    ("leisure","garden"):("grass","medium"),
    ("landuse","grass"):("grass","medium"),
    ("landuse","forest"):("grass","medium"),
    ("natural","grassland"):("grass","medium"),
    ("natural","wood"):("grass","medium"),
    ("natural","beach"):("sand","medium"),
    ("amenity","parking"):("asphalt","low"),
    ("highway","pedestrian"):("paving","low"),
    ("place","square"):("paving","low"),
    ("man_made","pier"):("concrete","low"),
    # The category means urban fabric, NOT that every tile is asphalt.
    ("landuse","commercial"):("urban_inferred","low"),
    ("landuse","retail"):("urban_inferred","low"),
    ("landuse","industrial"):("urban_inferred","low"),
    ("landuse","residential"):("urban_inferred","low")
}

def _tags(elt):
    return {x.get("k"):x.get("v") for x in elt.findall("tag")}

def _classify(tags):
    if tags.get("building") and tags["building"]!="no":
        return None
    # Never flatten a bridge/tunnel or elevated plaza into road pavement.
    if tags.get("bridge") not in (None,"no") or tags.get("tunnel") not in (None,"no"):
        return None
    if str(tags.get("layer","0")) not in ("0","0.0"):
        return None
    if tags.get("surface") in SURFACES:
        return SURFACES[tags["surface"]]
    for (k,v),value in AREA_CLASSES.items():
        if tags.get(k)==v:return value
    return None

def _raster_way(dem, nodes, way, radius):
    refs=[x.get("ref") for x in way.findall("nd")]
    if len(refs)<4 or refs[0]!=refs[-1]:
        return set()  # no line expansion and no invented widths
    if any(ref not in nodes for ref in refs):
        return set()
    try:
        ring=[dem.coords(*nodes[ref]) for ref in refs]
        return polygon_cells([ring],radius)
    except (ValueError,OverflowError):
        return set()

def classify_osm_xml(xml: bytes, dem: Dem, radius=RADIUS) -> dict:
    if radius not in (500,600):raise ValueError("Unverified OSM raster radius")
    if len(xml)>MAX_XML:raise ValueError("OSM source >8MiB")
    root=ET.fromstring(xml)
    if root.tag!="osm" or root.get("version")!="0.6":
        raise ValueError("Not OSM API 0.6 XML")
    if "odbl" not in (root.get("license") or "").lower():
        raise ValueError("OSM surface geometry lacks verifiable ODbL license")
    nodes={}
    for n in root.findall("node"):
        if n.get("lat") and n.get("lon"):
            nodes[n.get("id")]=(float(n.get("lon")),float(n.get("lat")))
    candidates=[]
    for way in root.findall("way"):
        tags=_tags(way)
        kind=_classify(tags)
        if kind is None:continue
        feature_id="way/"+str(way.get("id"))
        cells=_raster_way(dem,nodes,way,radius)
        if cells:
            candidates.append((feature_id,kind,cells))
    # Conservative overlap priority: exact tagged materials outrank an
    # inferred commercial land-use polygon; never cover a road/OSM building
    # because those are placed AFTER this ground pass in vanilla exporter.
    importance={"low":0,"medium":1,"high":2}
    target={}
    feature_counts=collections.Counter()
    for feature_id,(material,confidence),cells in sorted(
            candidates,key=lambda p:(importance[p[1][1]],p[0])):
        for x,z in cells:
            target[(x,z)]=(material,confidence,feature_id)
        feature_counts[material]+=1
    compressed=[]
    counts=collections.Counter()
    for z in range(-radius,radius+1):
        x=-radius
        while x<=radius:
            info=target.get((x,z))
            if info is None:
                x+=1
                continue
            end=x
            while end<radius and target.get((end+1,z))==info:
                end+=1
            compressed.append([z,x,end,*info])
            counts[info[0]]+=end-x+1
            x=end+1
    return {
        "format":FORMAT,"radius_m":radius,"resolution_m":1,
        "origin_lat":dem.lat,"origin_lon":dem.lon,
        "dem_source_raw_sha256":dem.sha256,
        "raw_osm_api_sha256":hashlib.sha256(xml).hexdigest(),
        "license":"ODbL-1.0","attribution":"© OpenStreetMap contributors",
        "known_surface_columns":len(target),
        "unmapped_surface_columns":(2*radius+1)**2-len(target),
        "materials":dict(counts),
        "source_feature_counts":dict(feature_counts),
        "rows":compressed
    }

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--input-xml",type=Path)
    ap.add_argument("--output",required=True,type=Path)
    ap.add_argument("--radius",type=int,default=500)
    args=ap.parse_args()
    if args.radius not in (500,600):
        ap.error("Only verified 500m/600m world bounds are supported")
    dem=Dem(DEFAULT_DEM)
    bbox,url=bounds(dem,args.radius,100)
    if args.input_xml:
        raw=args.input_xml.read_bytes()
        source="user_supplied_official_osm_xml"
    else:
        req=urllib.request.Request(url,headers={
            "User-Agent":"RealScale-Chaotianmen-Research/0.8 (ODbL attribution)",
            "Accept":"application/xml"})
        with urllib.request.urlopen(req,timeout=65) as response:
            raw=response.read(MAX_XML+1)
        source=url
    result=classify_osm_xml(raw,dem,args.radius)
    result["source_url"]=source
    result["query_bbox_wsen"]=bbox
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(gzip.compress(
        json.dumps(result,ensure_ascii=False,separators=(",",":"),
                   sort_keys=True).encode("utf8"),compresslevel=9,mtime=0))
    print("OSM_SOURCE_LANDCOVER",json.dumps(
        {k:v for k,v in result.items() if k not in ("rows",)},
        ensure_ascii=False))

if __name__=="__main__":main()
