#!/usr/bin/env python3
"""Outside-500m real OSM Raffles Tower 1, no invented 3D footprint.

The prior standalone export clips at +/-500m; official pinned OSM way
748910920 (Raffles T1 / T1塔楼) lives at world X about -563..-525.
The 600m terrain extension allows that exact polygon to be rendered as
a schematic 250m tower. There is no arbitrary coordinate offset, no
fake seventh/eighth footprint, and no supposed surveyed facade.
"""
from __future__ import annotations
import math
from chongqing_crystal_3d import _source, _projected
from chongqing_osm_to_minecraft import Dem, DEFAULT_DEM, polygon_cells

T1_OSM=748910920
NEW_RADIUS=600
ORIGINAL_RADIUS=500

def build_real_outer_t1(dem=None, radius=NEW_RADIUS):
    if radius<580 or radius>650:
        raise ValueError("Expanded real-Osm T1 only for documented 600m pilot")
    dem=dem or Dem(DEFAULT_DEM)
    source=_source()
    t1=source.get(T1_OSM)
    if t1 is None or t1["tags"].get("building")!="yes":
        raise ValueError("Authentic real Raffles City T1 OSM polygon absent")
    tags=t1["tags"]
    if abs(float(tags.get("height","0"))-250)>0.1:
        raise ValueError("Real OSM T1 building height no longer 250m")
    polygon=_projected(dem,t1)
    cells=polygon_cells([polygon],radius)
    outside={p for p in cells if abs(p[0])>ORIGINAL_RADIUS or
             abs(p[1])>ORIGINAL_RADIUS}
    if not (1000<=len(outside)<6500) or outside!=cells:
        raise ValueError("Real T1 overlaps old 500m plan or incomplete source")
    perimeter={
        (x,z) for x,z in cells
        if any((x+dx,z+dz) not in cells
               for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
    }
    if not perimeter or len(perimeter)>=len(cells):
        raise ValueError("Real T1 perimeter polygon malformed")
    # ids 4=light_gray_concrete, 5=gray_concrete match old pinned towers.
    # A distinct roof band on exact source perimeter is an art decision
    # with LOW architectural confidence and no pretend window precision.
    writes=[]
    for x,z in sorted(cells,key=lambda v:(v[1],v[0])):
        surface=dem.block_y(x,z)
        writes.append((x,surface,z,4))
        if (x,z) in perimeter:
            for y in range(surface+1,surface+250):
                writes.append((x,y,z,4))
        writes.append((x,surface+250,z,5))
    meta={
        "osm_source":"way/748910920",
        "name":tags.get("name"),
        "source_footprint_1m_columns":len(cells),
        "source_perimeter_1m_columns":len(perimeter),
        "source_height_m":250,
        "world_extent_m":radius,
        "original_export_limit_m":ORIGINAL_RADIUS,
        "bbox_xz":[min(x for x,z in cells),min(z for x,z in cells),
                    max(x for x,z in cells),max(z for x,z in cells)],
        "status":"true OSM footprint and tagged height; low-detail neutral 3D block shell",
        "source_json_hash":"c666afaf7e8455acd3c565e9a8921950af0b555d1d6a38285c01c7d64bfc603b",
        "vertical_anchor":"DEM-based local foundation per column, not architectural foundation survey"
    }
    return writes,meta

if __name__=="__main__":
    writes,metadata=build_real_outer_t1()
    print(metadata, "minecraft_block_writes",len(writes))
