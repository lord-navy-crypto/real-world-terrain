#!/usr/bin/env python3
"""Read actual NeoForge-produced Anvil chunks; compare center of DEM with source altitude.
Uses saved WORLD_SURFACE height maps; these include vegetation and structures, so
small deltas can be expected. Only full DEM-weight area (>=96m in from edge).
Never calls game APIs or trusts a preprinted predicted height.
"""
import hashlib, gzip, struct, json, sys, statistics
from pathlib import Path
from realscale_headless_qa import iter_region_chunks, surface_height, get_block

def read_dem(path):
    raw=gzip.decompress(path.read_bytes())
    assert raw[:4]==b'RGT1'
    w,h,x0,z0,sea=struct.unpack_from('>iiiii',raw,4)
    cell,off=struct.unpack_from('>dd',raw,24)
    assert (w,h,sea,cell)==(16,16,63,32.0)
    elevations=struct.unpack_from('>'+('f'*(w*h)),raw,40)
    def at(x,z):
        xx=(x-x0)/cell;zz=(z-z0)/cell
        assert 0<=xx<=w-1 and 0<=zz<=h-1,(x,z)
        i=min(w-2,int(xx));j=min(h-2,int(zz))
        fx=xx-i;fz=zz-j
        n=j*w+i
        return sea+off+(1-fz)*((1-fx)*elevations[n]+fx*elevations[n+1])+fz*((1-fx)*elevations[n+w]+fx*elevations[n+w+1])
    return at, (x0,z0,x0+(w-1)*cell,z0+(h-1)*cell)

def scan(root):
    tiles=list(root.glob('src/main/resources/data/realscale/dem/*.rgt.gz'))
    assert len(tiles)==1, tiles
    predict,bounds=read_dem(tiles[0])
    regions=list(root.glob('run/**/region/r.*.*.mca'))
    # verify dimensions, but avoid accidentally checking overworld from Nether or The End.
    regions=[p for p in regions if 'DIM' not in str(p)]
    assert regions, 'no Minecraft Anvil region data generated'
    count=0; samples=[]; all_lava=0; all_cols=0
    for region in regions:
      if region.stat().st_size<8192:continue
      for chunk in iter_region_chunks(region):
        count+=1
        cx=chunk.get('xPos');cz=chunk.get('zPos')
        if not isinstance(cx,int) or not isinstance(cz,int):continue
        for dz in range(16):
         for dx in range(16):
          x=cx*16+dx;z=cz*16+dz
          if not (-60<=x<=60 and -60<=z<=60):continue
          h=surface_height(chunk,dx,dz,-64,2096)
          if h is None:continue
          expected=predict(x,z)
          name=get_block(chunk,dx,h,dz)
          samples.append({'x':x,'z':z,'actual_surface_y':h,'expected_from_USGS_y':round(expected,3),'difference_m':round(h-expected,3),'top_block':name})
          if name=='minecraft:lava':all_lava+=1
          all_cols+=1
    errors=sorted(abs(x['difference_m']) for x in samples)
    vals=sorted(x['actual_surface_y'] for x in samples)
    report={'minecraft_region_files':len(regions),'decoded_chunks':count,'dem_interior_samples':len(samples),
            'median_abs_height_error_m':round(statistics.median(errors),3) if errors else None,
            'p95_abs_height_error_m':round(errors[int(.95*(len(errors)-1))],3) if errors else None,
            'range_actual_surface_y':(min(vals),max(vals)) if vals else None,
            'lava_on_dem_summits':all_lava,'source_expected_height_range':(round(min(predict(x,z) for x,z in [(0,0),(-64,-64),(64,64)]),2),round(max(predict(x,z) for x,z in [(0,0),(-64,-64),(64,64)]),2)),
            'examples':samples[:16]}
    report['dem_drives_real_chunks']=(len(samples)>=30 and report['median_abs_height_error_m']<30 and report['p95_abs_height_error_m']<70 and not all_lava)
    (root/'real_dem_anvil_qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False,indent=2))
    if not report['dem_drives_real_chunks']:
        raise SystemExit('Real DEM was NOT verified as driving block heights')
if __name__=='__main__':scan(Path(sys.argv[1]))