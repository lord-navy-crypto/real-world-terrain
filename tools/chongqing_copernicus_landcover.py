#!/usr/bin/env python3
"""Open Copernicus Sentinel-2 L2A 10-m georeferenced surface evidence.

Band B03 (green), B04 (red), B08 (near-IR) and scene classification
SCL are read from properly georeferenced GeoTIFF COGs (10/20m) via rasterio.

This is NOT a 1m photo: each 10-m measurement only supports broad spectral
vegetation or impervious/bare-ground candidates, *never* exact construction
materials, roads, windows, or 3D tower geometry. Water is held out (no
unverified river Y), cloud/shadow pixels are held out, and OSM remains
higher priority than this lower-resolution satellite-derived ground.

The caller must supply traceable original public Copernicus asset URLs and
STAC product ID. No Google Maps/Earth, no proprietary imagery scraping.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from chongqing_osm_to_minecraft import Dem,DEFAULT_DEM

FORMAT="REALSCALE_CHAOTIANMEN_S2_LANDCOVER_V1"
RESOLUTION_METRES=10
RADIUS=500
SIDE=101

def spectral_decision(green,red,nir,scl):
    """Two evidence-supported candidate classes, no fake urban-material claim."""
    if scl in (0,1,2,3,6,7,8,9,10,11):
        return None
    if not all(0<=v<65536 for v in (green,red,nir)):
        return None
    if red+nir<100:
        return None
    ndvi=(nir-red)/(nir+red)
    if scl==4 and ndvi>=0.30:
        return ("grass","medium")
    if scl==5 and ndvi<=0.18:
        return ("urban_inferred","low")
    return None

def to_surface_rows(green,red,nir,scl,item_id,dem,radius=RADIUS):
    import numpy as np
    if radius not in (500,600):
        raise ValueError("Only source-backed 500m or 600m crop")
    side=(radius*2)//RESOLUTION_METRES+1
    for a in (green,red,nir,scl):
        if a.shape!=(side,side):
            raise ValueError("Expected georeferenced 10m square satellite grid")
    # Each 10m Sentinel measurement can only fill a 10m world square.
    # The per-1m "pixels" are explicitly NOT 1m native spectral detail.
    source_id="sentinel-2-l2a/"+item_id
    rows=[];counts={}
    for z in range(-radius,radius+1):
        iz=min(side-1,max(0,(z+radius)//RESOLUTION_METRES))
        x=-radius
        while x<=radius:
            ix=min(side-1,max(0,(x+radius)//RESOLUTION_METRES))
            info=spectral_decision(int(green[iz,ix]),int(red[iz,ix]),
                                   int(nir[iz,ix]),int(scl[iz,ix]))
            if info is None:
                x+=1;continue
            until=min(radius,((ix+1)*RESOLUTION_METRES-radius-1))
            rows.append([z,x,until,info[0],info[1],source_id])
            counts[info[0]]=counts.get(info[0],0)+(until-x+1)
            x=until+1
    return {"format":FORMAT,"radius_m":radius,"resolution_m":RESOLUTION_METRES,
            "origin_lat":dem.lat,"origin_lon":dem.lon,
            "dem_source_raw_sha256":dem.sha256,
            "license":"Copernicus Sentinel free and open data",
            "attribution":"Contains modified Copernicus Sentinel-2 data",
            "source_stac_item":item_id,
            "known_surface_columns":sum(counts.values()),
            "unmapped_surface_columns":(2*radius+1)**2-sum(counts.values()),
            "materials":counts,"rows":rows,
            "interpretation":"low-confidence broad 10m classes; not observed 1m urban pavement or 3D"}

def raster_bands(green,red,nir,scl,dem,radius=RADIUS):
    import numpy as np
    import rasterio
    from rasterio.warp import reproject, Resampling
    from rasterio.transform import from_bounds
    # The exact same WGS84 tangent-plane mapping used for original RealScale
    # Chaotianmen ensures physical GIS and multispectral evidence align.
    if radius not in (500,600):
        raise ValueError("Only public 500m or 600m georeferenced crop")
    d=radius+5
    side=(2*radius)//RESOLUTION_METRES+1
    lo=dem.lon-d/dem.m_lon;hi=dem.lon+d/dem.m_lon
    south=dem.lat-d/dem.m_lat;north=dem.lat+d/dem.m_lat
    target=from_bounds(lo,south,hi,north,side,side)
    arrays=[]
    for path in (green,red,nir,scl):
        dest=np.zeros((side,side),dtype=np.uint16)
        with rasterio.Env(GDAL_DISABLE_READDIR_ON_OPEN="YES"):
            with rasterio.open(path) as dataset:
                if not dataset.crs:
                    raise ValueError("Satellite GeoTIFF missing real CRS")
                reproject(
                    source=rasterio.band(dataset,1),
                    destination=dest,
                    src_transform=dataset.transform,
                    src_crs=dataset.crs,
                    dst_transform=target,
                    dst_crs="EPSG:4326",
                    dst_nodata=0,
                    resampling=Resampling.nearest)
        arrays.append(dest)
    return arrays

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("green","red","nir","scl","stac-item","output"):
        p.add_argument("--"+name,required=True)
    args=p.parse_args()
    item=args.stac_item
    if len(item)<10 or len(item)>250 or "/" in item or ".." in item:
        raise ValueError("Invalid Sentinel STAC item ID")
    dem=Dem(DEFAULT_DEM)
    arrays=raster_bands(args.green,args.red,args.nir,args.scl,dem)
    result=to_surface_rows(*arrays,item,dem)
    if result["known_surface_columns"]<1000:
        raise ValueError("Scene is cloudy/unusable: refusing to replace urban ground")
    path=Path(args.output)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(
        json.dumps(result,ensure_ascii=False,separators=(",",":"),sort_keys=True)
        .encode(),compresslevel=9,mtime=0))
    print("TRUE_COPERNICUS_SPECTRAL_CLASSIFICATION",
          json.dumps({k:v for k,v in result.items() if k!="rows"}))

if __name__=="__main__":main()
