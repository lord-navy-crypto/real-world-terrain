#!/usr/bin/env python3
"""Build the 3D Raffles City Crystal from its TRUE pinned OSM polygon.

Actual OSM way/661717188 has min_height=250m, height=276.5m and 43
geodetic polygon vertices. This is the 300m elevated landmark previously
DEFERRED by the 2D GIS exporter. It is now built as a schematic glass
corridor with source polygon XY, source height span and a coarse curved roof.

HEIGHT CAVEAT: OSM min_height is RELATIVE, not an absolute surveyed
altitude. The absolute world Y is aligned to the MEDIAN simulated roof
Y of the four source-backed 250m towers, not to external LiDAR. This
is a LOW-confidence Y anchor explicitly recorded in the save manifest.
Do not claim this is surveyed architectural glazing, structural steel,
or true multi-entry vertical circulation.
"""
from __future__ import annotations
import base64
import collections
import gzip
import hashlib
import json
import math
import statistics
from pathlib import Path

from chongqing_osm_to_minecraft import (
    Dem,DEFAULT_DEM,polygon_cells)

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"chongqing/data/chaotianmen_osm_api_20261019_r500_b100.json.gz.b64"
PINNED_RAW_OSM_XML_HASH="7a2e4d777de05efe5fc46b889fca3d86ff47981d6d9bab2cf940188790022d31"
PINNED_SOURCE_JSON_HASH="9816d76beb4579e8c7289cce52fdd3f216e323c2e2a320562654005717494657"
CRYSTAL_OSM=661717188
ANCHOR_TOWERS=(661717190,661717191,748910918,748910919)
RADIUS=500

def _source():
    encoded=SOURCE.read_bytes().strip()
    parsed=gzip.decompress(base64.b64decode(encoded,validate=True))
    if hashlib.sha256(parsed).hexdigest()!=PINNED_SOURCE_JSON_HASH:
        raise ValueError("Authentic Crystal OSM source SHA mismatch")
    source=json.loads(parsed)
    if source.get("realscale_source",{}).get("raw_xml_sha256")!=PINNED_RAW_OSM_XML_HASH:
        raise ValueError("Crystal geometry does not match archived official API XML")
    if source["realscale_source"]["license"]!="ODbL-1.0":
        raise ValueError("Source license mismatch")
    return {int(e["id"]):e for e in source["elements"]
            if e.get("type")=="way"}

def _projected(dem,source_way):
    pts=source_way["geometry"]
    if not pts or pts[0]!=pts[-1]:
        raise ValueError("Authentic OSM polygon is not closed")
    return [dem.coords(float(p["lon"]),float(p["lat"])) for p in pts]

def _tower_roof(dem,way):
    pts=_projected(dem,way)
    center_x=sum(x for x,z in pts[:-1])/(len(pts)-1)
    center_z=sum(z for x,z in pts[:-1])/(len(pts)-1)
    true_tag=float(way["tags"]["height"])
    if abs(true_tag-250)>1e-3:
        raise ValueError("Anchor tower height no longer matches OSM source")
    return dem.block_y(round(center_x),round(center_z))+round(true_tag)

def build_crystal(dem=None):
    if dem is None:dem=Dem(DEFAULT_DEM)
    elements=_source()
    crystal=elements.get(CRYSTAL_OSM)
    if not crystal:
        raise ValueError("Authentic The Crystal OSM way missing")
    tags=crystal["tags"]
    if (tags.get("layer")!="1"
            or tags.get("building")!="yes"
            or abs(float(tags.get("min_height","0"))-250)>0.01
            or abs(float(tags.get("height","0"))-276.5)>0.01):
        raise ValueError("OSM The Crystal 3D height tags changed unexpectedly")
    vertices=_projected(dem,crystal)
    footprint=polygon_cells([vertices],RADIUS)
    if not (1000 <=len(footprint)<=30000):
        raise ValueError("The Crystal source polygon clipped or abnormal")
    rooftops=[_tower_roof(dem,elements[id]) for id in ANCHOR_TOWERS]
    # Median avoids dragging entire skybridge to a hillside rooftop.
    floor_y=round(statistics.median(rooftops))
    height=round(float(tags["height"])-float(tags["min_height"]))
    if not (20<=height<=30):
        raise ValueError("The Crystal floor-to-roof height outside tag range")
    if floor_y+height>=960:
        raise ValueError("Source bridge outside this standalone world height")
    # BFS city-block distance to genuine OSM footprint boundary gives an
    # arch approximation: not a simulated or measured structural survey.
    edges={p for p in footprint
           if any((p[0]+dx,p[1]+dz) not in footprint
                  for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))}
    distances={p:0 for p in edges}
    todo=collections.deque(edges)
    while todo:
        x,z=todo.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            n=(x+dx,z+dz)
            if n in footprint and n not in distances:
                distances[n]=distances[(x,z)]+1
                todo.append(n)
    peak=max(distances.values())
    if peak<2:
        raise ValueError("Crystal too narrow for a source-based glass enclosure")
    # Vanilla block identifiers in the independent Anvil world exporter.
    # 16 = white concrete frame; 17 = light blue stained glass;
    # 18 = quartz smooth walkway/floor.
    frame,glass,floor=16,17,18
    placements=[]
    # A very simple structural mullion rhythm is an approximation only.
    # Exact horizontal XY (outline) and total source height are preserved.
    for x,z in sorted(footprint,key=lambda t:(t[1],t[0])):
        dist=distances[(x,z)]
        roof_y=floor_y+min(height, max(8,
            round(8+(height-8)*math.sin(
                (math.pi/2)*min(1,dist/max(peak,1))))))
        placements.append((x,floor_y,z,floor))
        if (x,z) in edges:
            for y in range(floor_y+1,roof_y):
                placements.append((x,y,z,frame if (x+z)%7==0 or
                                   (y-floor_y)%7==0 else glass))
        placements.append((x,roof_y,z,
                           frame if (x+z)%9==0 else glass))
    evidence={
        "osm_source_id":"way/661717188",
        "source_name":tags.get("name"),
        "source_polygon_vertex_count":len(vertices),
        "source_polygon_1m_cells":len(footprint),
        "source_height_m":float(tags["height"]),
        "source_min_height_m":float(tags["min_height"]),
        "source_vertical_span_m":height,
        "lower_arch_edge_y":floor_y+8,
        "upper_arch_center_y":floor_y+height,
        "anchored_floor_y":floor_y,
        "adjacent_real_osm_towers":list(ANCHOR_TOWERS),
        "simulated_tower_roof_ys":rooftops,
        "vertical_registration_confidence":"LOW: median of four model tower rooftops, NOT absolute surveyed GIS 3D",
        "source_json_sha256":PINNED_SOURCE_JSON_HASH,
        "source_xml_sha256":PINNED_RAW_OSM_XML_HASH,
        "architecture_note":"OSM XY + height-tag shell, approximation of tubular facade/ribs; no exact glass or building layout"
    }
    return placements,evidence

if __name__=="__main__":
    points,metrics=build_crystal()
    print("SOURCE_BACKED_RAFFLES_CRYSTAL_3D_PILOT",
          json.dumps({"placed_blocks":len(points),**metrics},ensure_ascii=False))
