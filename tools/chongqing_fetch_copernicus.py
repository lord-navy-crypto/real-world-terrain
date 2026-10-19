#!/usr/bin/env python3
"""Acquire genuine open Copernicus S2 L2A coverage via public STAC + signed COG.

No Google/Mapbox/Esri tiles, no private service accounts. Pinned source
item identifiers are stored in output attribution. 10m is not 1m accuracy.
This script is optional and is only invoked in its separate QA workflow.
"""
import argparse
import gzip
import json
from pathlib import Path
from datetime import datetime, timezone

from chongqing_osm_to_minecraft import Dem,DEFAULT_DEM
from chongqing_copernicus_landcover import (
    raster_bands,to_surface_rows)

STAC="https://planetarycomputer.microsoft.com/api/stac/v1"

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output",type=Path,required=True)
    ap.add_argument("--extent-radius",type=int,choices=(500,600),default=500)
    ap.add_argument("--max-cloud-cover",type=float,default=25)
    args=ap.parse_args()
    if not (0<=args.max_cloud_cover<=50):
        ap.error("Cloud fraction limit 0..50%")
    from pystac_client import Client
    import planetary_computer
    dem=Dem(DEFAULT_DEM)
    b=0.012
    search=Client.open(STAC).search(
        collections=["sentinel-2-l2a"],
        bbox=[dem.lon-b,dem.lat-b,dem.lon+b,dem.lat+b],
        datetime="2025-01-01/2026-10-19",
        query={"eo:cloud_cover":{"lt":args.max_cloud_cover}},
        max_items=50)
    scenes=list(search.items())
    # Prefer clearer scenes, not just newer images which may be obscured
    # by persistent Yangtze valley fog.
    scenes.sort(key=lambda t:(t.properties.get("eo:cloud_cover",100),
                              -(t.datetime.timestamp() if t.datetime else 0)))
    if not scenes:raise RuntimeError("No public cloud-free Sentinel imagery")
    errors=[]
    for scene in scenes[:5]:
        try:
            signed=planetary_computer.sign(scene)
            urls=[signed.assets[band].href for band in ("B03","B04","B08","SCL")]
            arrays=raster_bands(*urls,dem,radius=args.extent_radius)
            result=to_surface_rows(*arrays,scene.id,dem,
                                   radius=args.extent_radius)
            if result["known_surface_columns"]<1000:
                errors.append(scene.id+": <1000 cloud-free classified columns")
                continue
            result["stac_collection"]="sentinel-2-l2a"
            result["source_datetime_utc"]=scene.datetime.isoformat()
            result["source_cloud_cover_pct"]=scene.properties.get("eo:cloud_cover")
            result["source_stac_endpoint"]=STAC
            result["band_names"]=["B03","B04","B08","SCL"]
            result["source_note"]="© ESA Copernicus: 10m broad classification, not city facade or surveyed sidewalk"
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.write_bytes(gzip.compress(
                json.dumps(result,ensure_ascii=False,sort_keys=True,
                           separators=(",",":")).encode(),
                compresslevel=9,mtime=0))
            print("COPERNICUS_TRUE_SCENE_PASS",json.dumps(
                {k:v for k,v in result.items() if k!="rows"},
                ensure_ascii=False))
            return
        except Exception as e:
            errors.append(scene.id+":"+str(e)[:280])
    raise RuntimeError("Unable to classify official Sentinel-2 source: "+
                       "; ".join(errors)[:1800])

if __name__=="__main__":main()
