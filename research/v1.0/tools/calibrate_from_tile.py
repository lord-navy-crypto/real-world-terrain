#!/usr/bin/env python3
"""Empirical DEM roughness & directional semivariograms; NEVER physics inferred from one raster."""
import sys,struct,gzip,json,math
from pathlib import Path
import numpy as np

def read_tile(path):
    b=gzip.decompress(Path(path).read_bytes())
    if b[:4]!=b'RGT1':raise ValueError('Unknown tile')
    w,h,x0,z0,sea,cell,off=struct.unpack_from('>5i2d',b,4)
    if w<8 or h<8 or w*h>4_000_000: raise ValueError('Invalid tile dimensions')
    n=w*h; start=4+5*4+2*8
    e=np.frombuffer(b,dtype='>f4',count=n,offset=start).reshape((h,w)).astype('float64')
    return e,cell

def analyze(a,cell):
    out={'observed_data_only':True,'grid_spacing_m':cell,'observed_range_m':[float(a.min()),float(a.max())],
         'height_p05_p50_p95_m':[float(np.percentile(a,p)) for p in (5,50,95)],
         'interpretation':'Empirical correlation/anisotropy and slope only. NOT tectonic cause or measured 1m truth.'}
    gx,gz=np.gradient(a,cell,cell)
    slope=np.degrees(np.arctan(np.hypot(gx,gz)))
    out['slope_p50_p95_degrees']=[float(np.percentile(slope,p)) for p in (50,95)]
    semis=[]
    for lag in (1,2,4,8,16,32):
        if lag>=min(a.shape):continue
        dx=a[:,lag:]-a[:,:-lag];dz=a[lag:,:]-a[:-lag,:]
        vx=.5*float(np.mean(dx*dx));vz=.5*float(np.mean(dz*dz))
        semis.append({'lag_m':lag*cell,'semi_x_m2':vx,'semi_z_m2':vz,
                      'anisotropy_ratio_x_to_z':vx/max(vz,1e-12)})
    out['empirical_directional_semivariograms']=semis
    return out

if __name__=='__main__':
    if len(sys.argv)<3:raise SystemExit('Usage: calibrate_from_tile.py input.rgt.gz output.json')
    a,cell=read_tile(sys.argv[1]);d=analyze(a,cell)
    Path(sys.argv[2]).write_text(json.dumps(d,indent=2)+'\n');print(json.dumps(d,indent=2))
