#!/usr/bin/env python3
"""Verify exact USGS 3DEP EPQS JSON answers against transported ESRI ASCII DEM."""
import argparse,hashlib,json,math
from pathlib import Path
import numpy as np
import rasterio

def validate(data):
    data=Path(data)
    points=json.loads((data/'USGS_3DEP_EPQS_raw_records.json').read_text())
    manifest=json.loads((data/'provenance.json').read_text())
    raster=data/'USGS_3DEP_EPQS_12x12_ASCII_EPSG4326.asc'
    assert len(points)==144 and manifest['response_count']==144
    assert hashlib.sha256((data/'USGS_3DEP_EPQS_raw_records.json').read_bytes()).hexdigest()==manifest['source_sha256']
    with rasterio.open(raster) as f: a=f.read(1)
    assert a.shape==(12,12)
    discrepancies=[];resolutions=[]
    for p in points:
        r,c=p['row'],p['col'];v=float(p['response']['value'])
        assert p['elevation_m']==v
        # ESRI ASCII stores six decimal digits, so rounding tolerance is 1e-6m.
        if abs(float(a[r,c])-v)>0.0005:discrepancies.append([r,c,float(a[r,c])-v])
        assert p['response']['location']['x']==p['lon']
        assert p['response']['location']['y']==p['lat']
        assert p['request_url'].startswith('https://epqs.nationalmap.gov/v1/json?')
        resolutions.append(p['response'].get('resolution'))
    if discrepancies:raise ValueError(f'144-record raw-vs-raster mismatch: {discrepancies[:6]}')
    assert math.isclose(float(a.min()),manifest['elevation_min_m'],abs_tol=.001)
    assert math.isclose(float(a.max()),manifest['elevation_max_m'],abs_tol=.001)
    return {'real_source':'USGS 3DEP EPQS','raw_points_verified':len(points),'ascii_cells_verified':a.size,
       'raw_and_ascii_height_agreement':'<= 0.0005 meters of rounding',
       'source_sha256_verified':True,
       'source_resolution_field_values':{str(k):resolutions.count(k) for k in set(resolutions)},
       'grid_elevation_range_m':[float(a.min()),float(a.max())],
       'warning':'EPQS serves interpolated elevations, not original lidar point observations'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('data_dir');a=p.parse_args()
    print(json.dumps(validate(a.data_dir),ensure_ascii=False,indent=2))
