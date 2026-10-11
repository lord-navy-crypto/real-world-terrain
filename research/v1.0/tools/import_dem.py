#!/usr/bin/env python3
"""Import an independently obtained real-world DEM GeoTIFF to a 1-block=1m world-data tile.

No imagery, game blocks, or third-party mods are bundled; this conversion requires a
real local DEM file and retains provenance. Optional rasters: mean ANNUAL rainfall,
rock resistance. Scientific caveat: sampling a 30m DEM at 1m isn't 1m measurement.
"""
import argparse, gzip, hashlib, io, json, math, struct
from pathlib import Path
import numpy as np
import rasterio
from rasterio.crs import CRS
from rasterio.enums import Resampling
from rasterio.transform import from_origin
from rasterio.warp import reproject
from scipy.ndimage import gaussian_filter

MAGIC=b'RGT1'

def estimated_source_spacing_m(src,lat,crs=None):
    t=src.transform
    crs=crs or src.crs
    if crs and crs.is_geographic:
        return [round(math.hypot(t.a*111320*math.cos(math.radians(lat)),t.d*111132),2),
                round(math.hypot(t.b*111320*math.cos(math.radians(lat)),t.e*111132),2)]
    if crs and crs.is_projected:
        factor=crs.linear_units_factor[1] if crs.linear_units_factor else 1.0
        return [round(math.hypot(t.a,t.d)*factor,2),round(math.hypot(t.b,t.e)*factor,2)]
    return None

def reproject_raster(filename,grid,north_lat,center_lon,cell_m,kind='continuous',source_crs_override=None):
    from rasterio.warp import reproject
    n=grid
    dst=np.full((n,n),np.nan,dtype='float32')
    local=CRS.from_proj4(f'+proj=aeqd +lat_0={north_lat} +lon_0={center_lon} +datum=WGS84 +units=m +no_defs')
    transform=from_origin(-n*cell_m/2,n*cell_m/2,cell_m,cell_m)
    with rasterio.open(filename) as src:
        if src.count<1 or (src.crs is None and not source_crs_override):raise ValueError('Source raster needs CRS metadata; for ESRI ASCII use --source-crs EPSG:4326')
        actual_crs=CRS.from_string(source_crs_override) if source_crs_override else src.crs
        if actual_crs.is_projected and actual_crs.linear_units not in ('metre','meter','foot','US survey foot'):
            raise ValueError('Unsupported projected linear units: '+str(actual_crs.linear_units))
        reproject(source=rasterio.band(src,1) if src.crs is not None else src.read(1),destination=dst,
          src_transform=src.transform,src_crs=actual_crs,src_nodata=src.nodata,
          dst_transform=transform,dst_crs=local,dst_nodata=np.nan,init_dest_nodata=True,
          resampling=Resampling.nearest if kind=='categorical' else Resampling.bilinear)
        resolution=estimated_source_spacing_m(src,north_lat,actual_crs)
        source_crs=actual_crs.to_string()
    if not np.isfinite(dst).all():
        valid=int(np.count_nonzero(np.isfinite(dst)))
        raise ValueError(f'Insufficient source DEM coverage: {valid}/{n*n} grid samples. Choose a smaller region or a broader input mosaic.')
    return dst,resolution,source_crs

def inspect_array(arr,step):
    z,x=np.gradient(arr.astype('float64'),step,step)
    slope=np.degrees(np.arctan(np.hypot(x,z)))
    relief=arr-gaussian_filter(arr.astype('float64'),sigma=max(1,int(round(1000/step))))
    return {'height_min_m':round(float(arr.min()),3),
            'height_p05_m':round(float(np.percentile(arr,5)),3),
            'height_median_m':round(float(np.median(arr)),3),
            'height_p95_m':round(float(np.percentile(arr,95)),3),
            'height_max_m':round(float(arr.max()),3),
            'slope_median_degrees':round(float(np.median(slope)),3),
            'slope_p95_degrees':round(float(np.percentile(slope,95)),3),
            'km_scale_relief_std_m':round(float(np.std(relief)),3)}

def encode_tile(out,rows,origin_x,origin_z,cell_m,sea_y,vertical_offset,elevation,
                rainfall=None,resistance=None,channel=None):
    rows=int(rows)
    assert elevation.shape==(rows,rows)
    if rainfall is None:rainfall=np.full((rows,rows),np.nan,dtype='float32')
    if resistance is None:resistance=np.full((rows,rows),np.nan,dtype='float32')
    if channel is None:channel=np.zeros((rows,rows),dtype='uint8')
    raw=io.BytesIO()
    raw.write(MAGIC)
    raw.write(struct.pack('>5i2d',rows,rows,int(origin_x),int(origin_z),int(sea_y),float(cell_m),float(vertical_offset)))
    for ar in (elevation,rainfall,resistance):
        raw.write(np.asarray(ar,dtype='>f4').tobytes())
    raw.write(np.asarray(channel,dtype=np.uint8).tobytes())
    with open(out,'wb') as f:
        with gzip.GzipFile(fileobj=f,mode='wb',filename='',mtime=0,compresslevel=6) as g:
            g.write(raw.getvalue())
    return hashlib.sha256(Path(out).read_bytes()).hexdigest()

def main():
    p=argparse.ArgumentParser(description='Convert real DEM GeoTIFF to RealScale measured-data grid')
    p.add_argument('--dem',required=True,type=Path,help='Real bare-earth elevation GeoTIFF from a data provider')
    p.add_argument('--source',required=True,help='Data provider and product name, e.g. USGS 3DEP 1m')
    p.add_argument('--source-url',required=True,help='Exact official dataset or file provenance URL')
    p.add_argument('--vertical-datum',required=True,help='e.g. NAVD88, EGM2008, local-MSL')
    p.add_argument('--source-crs',help='Override CRS when local ESRI ASCII has no .prj, e.g. EPSG:4326')
    p.add_argument('--center-lat',type=float,required=True)
    p.add_argument('--center-lon',type=float,required=True)
    p.add_argument('--cells',type=int,default=128,help='Sample grid size, each side; min 8')
    p.add_argument('--cell-m',type=float,default=64,help='Meters between measured/derived macro samples; not game block size')
    p.add_argument('--world-x0',type=int,default=0)
    p.add_argument('--world-z0',type=int,default=0)
    p.add_argument('--sea-y',type=int,default=63)
    p.add_argument('--vertical-offset-m',type=float,default=0,help='User-supplied datum conversion offset; never auto-guessed')
    p.add_argument('--rainfall-annual',type=Path,help='Observed climatic rainfall GeoTIFF, not optional source of DEM')
    p.add_argument('--rainfall-unit',choices=['mm-year','m-year'],default='mm-year')
    p.add_argument('--monthly-rainfall',nargs=12,type=Path,metavar='MONTH_TIF',help='WorldClim-style 12 monthly precipitation GeoTIFFs in mm/month; sums to annual m/year')
    p.add_argument('--rock-resistance',type=Path,help='Already calibrated dimensionless resistance GeoTIFF')
    p.add_argument('--elevation-unit',choices=['meter','foot'],default='meter')
    p.add_argument('--out',required=True,type=Path)
    a=p.parse_args()
    if not 8<=a.cells<=2048 or a.cell_m<=0 or abs(a.center_lat)>90 or abs(a.center_lon)>180:
        p.error('Invalid region dimensions or coordinates')
    if not a.dem.is_file():p.error('DEM file not found: '+str(a.dem))
    elevation,res,crs=reproject_raster(a.dem,a.cells,a.center_lat,a.center_lon,a.cell_m,source_crs_override=a.source_crs)
    if a.elevation_unit=='foot':elevation*=.3048
    projected=a.sea_y+elevation+a.vertical_offset_m
    if float(projected.min())<-64 or float(projected.max())>2031:
        raise ValueError(f'1:1 exceeds game build bounds [-64,2031]; actual sample Y=[{float(projected.min()):.1f}, {float(projected.max()):.1f}]. Refusing silent scaling or clipping.')
    if a.rainfall_annual and a.monthly_rainfall:raise ValueError('Use either annual rainfall or 12 monthly rasters, not both')
    rain=None
    if a.monthly_rainfall:
        rain=np.zeros((a.cells,a.cells),dtype='float32')
        for monthly in a.monthly_rainfall:
            if not monthly.is_file():raise ValueError('Missing monthly precipitation: '+str(monthly))
            month,_,_=reproject_raster(monthly,a.cells,a.center_lat,a.center_lon,a.cell_m,source_crs_override=a.source_crs)
            if float(month.min())<0:raise ValueError('Monthly precipitation must be >= 0 mm')
            rain+=month/1000.0
    if a.rainfall_annual:
        rain,_,_=reproject_raster(a.rainfall_annual,a.cells,a.center_lat,a.center_lon,a.cell_m,source_crs_override=a.source_crs)
        if a.rainfall_unit=='mm-year':rain/=1000.0
        if float(rain.min())<0:raise ValueError('Negative annual rainfall')
    rock=None
    if a.rock_resistance:
        rock,_,_=reproject_raster(a.rock_resistance,a.cells,a.center_lat,a.center_lon,a.cell_m,source_crs_override=a.source_crs)
        if float(rock.min())<=0:raise ValueError('Rock resistance must be strictly positive')
    a.out.parent.mkdir(parents=True,exist_ok=True)
    sha=encode_tile(a.out,a.cells,a.world_x0,a.world_z0,a.cell_m,a.sea_y,a.vertical_offset_m,elevation,rain,rock)
    meta={
     'format':'RealScale-RGT1','tile':a.out.name,'tile_sha256':sha,
     'source_name':a.source,'source_url':a.source_url,'source_file':a.dem.name,
     'source_sha256':hashlib.sha256(a.dem.read_bytes()).hexdigest(),
     'source_crs':crs,'estimated_native_spacing_m':res,
     'origin_latitude':a.center_lat,'origin_longitude':a.center_lon,
     'aeqd_projection':'WGS84 local azimuthal equidistant',
     'world_x0':a.world_x0,'world_z0':a.world_z0,
     'cell_m':a.cell_m,'cells':a.cells,'world_sea_y':a.sea_y,
     'vertical_datum':a.vertical_datum,'vertical_offset_m':a.vertical_offset_m,
     'height_policy':'strict; 1 block/meter and no silent clipping',
     'measured_at_1m': bool(res and max(res)<=1.1 and a.cell_m<=1.1),
     'climate':('annual precipitation calculated from 12 provider monthly rasters' if a.monthly_rainfall else ('observed annual raster' if a.rainfall_annual else 'NOT SUPPLIED; Java fallback explicitly marked synthetic')),
     'rock_resistance':'calibrated provided raster' if a.rock_resistance else 'NOT SUPPLIED; model uses constant 1.0',
     'values_interpolated':True,
     'geographic_scope':'fixed local rectangle; not a globally valid procedural tile',
     'warning':'Reprojection/interpolation at fine resolution never increases the information of the native DEM; vertical datums must be reconciled externally',
     'elevation_stats':inspect_array(elevation,a.cell_m)}
    manifest=a.out.with_suffix(a.out.suffix+'.json')
    manifest.write_text(json.dumps(meta,indent=2,ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'tile':str(a.out),'metadata':str(manifest),'stats':meta['elevation_stats'],'sha256':sha},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
