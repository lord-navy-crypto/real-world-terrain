import unittest,subprocess,sys,gzip,struct,json,tempfile
from pathlib import Path
import numpy as np
import rasterio
from rasterio.transform import from_origin
from importlib.machinery import SourceFileLoader
ROOT=Path(__file__).resolve().parents[1]
M=SourceFileLoader('dem_tool',str(ROOT/'tools/import_dem.py')).load_module()
C=SourceFileLoader('calibration',str(ROOT/'tools/calibrate_from_tile.py')).load_module()
class GeoRealTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
  path=self.root/'SYNTHETIC_NOT_REAL.tif'
  arr=120+np.arange(80,dtype='float32')[:,None]*.5+np.arange(80,dtype='float32')[None,:]*.7
  with rasterio.open(path,'w',driver='GTiff',height=80,width=80,count=1,dtype='float32',crs='EPSG:4326',
              transform=from_origin(-121.04,36.04,.001,.001)) as dst:dst.write(arr,1)
  self.src=path; self.out=self.root/'region.rgt.gz'
 def tearDown(self):self.tmp.cleanup()
 def run_import(self,*extra):
  return subprocess.run([sys.executable,str(ROOT/'tools/import_dem.py'),'--dem',str(self.src),
    '--source','SYNTHETIC fixture, not a USGS measurement','--source-url','https://example.invalid/synthetic',
    '--vertical-datum','synthetic-test-datum','--center-lat','36','--center-lon','-121',
    '--cells','16','--cell-m','64','--out',str(self.out),*extra],capture_output=True,text=True)
 def test_import_and_metadata(self):
  p=self.run_import();self.assertEqual(p.returncode,0,p.stderr)
  b=gzip.decompress(self.out.read_bytes());self.assertEqual(b[:4],b'RGT1')
  w,h,x,z,sea,cell,offset=struct.unpack_from('>5i2d',b,4)
  self.assertEqual((w,h,x,z,sea,cell,offset),(16,16,0,0,63,64.0,0.0))
  alt=np.frombuffer(b,dtype='>f4',count=256,offset=40)
  self.assertTrue((alt>120).all());self.assertTrue((alt<230).all())
  climate=np.frombuffer(b,dtype='>f4',count=256,offset=40+1024)
  self.assertTrue(np.isnan(climate).all())
  meta=json.loads(Path(str(self.out)+'.json').read_text());self.assertEqual(meta['source_name'],'SYNTHETIC fixture, not a USGS measurement');self.assertFalse(meta['measured_at_1m'])
  self.assertEqual(meta['source_sha256'],__import__('hashlib').sha256(self.src.read_bytes()).hexdigest())
 def test_calibration_variogram(self):
  self.assertEqual(self.run_import().returncode,0)
  arr,cell=C.read_tile(self.out)
  m=C.analyze(arr,cell);self.assertEqual(cell,64)
  self.assertEqual(len(m['empirical_directional_semivariograms']),4)
  self.assertGreater(m['slope_p50_p95_degrees'][1],0)
 def test_missing_dem(self):
  self.src=self.root/'missing.tif';p=self.run_import();self.assertNotEqual(p.returncode,0)
 def test_exceed_game_height_rejected(self):
  p=self.run_import('--vertical-offset-m','3000');self.assertNotEqual(p.returncode,0)
  self.assertIn('Refusing silent scaling or clipping',p.stderr)
 def test_deterministic_rgt(self):
  self.assertEqual(self.run_import().returncode,0)
  a=self.out.read_bytes();self.assertEqual(self.run_import().returncode,0);self.assertEqual(a,self.out.read_bytes())
 def test_precip_monthly_12_rasters(self):
  monthly=[]
  for i in range(12):
   src=self.root/f'rain_{i+1:02d}.tif'
   with rasterio.open(src,'w',driver='GTiff',height=80,width=80,count=1,dtype='float32',crs='EPSG:4326',
              transform=from_origin(-121.04,36.04,.001,.001)) as dst:
    dst.write(np.full((80,80),12,dtype='float32'),1)
   monthly.append(str(src))
  proc=self.run_import('--monthly-rainfall',*monthly)
  self.assertEqual(proc.returncode,0,proc.stderr)
  data=gzip.decompress(self.out.read_bytes())
  rain=np.frombuffer(data,dtype='>f4',count=256,offset=40+256*4)
  self.assertTrue(np.allclose(rain,.144,atol=1e-6))
  meta=json.loads(Path(str(self.out)+'.json').read_text())
  self.assertIn('12 provider monthly',meta['climate'])
 def test_actual_usgs_records_match_epqs(self):
  source=ROOT/'data/USGS_3DEP_BigSur_20261011'
  if not source.is_dir():self.skipTest('Actual official-data artifact not available')
  verifier=SourceFileLoader('verify_source',str(ROOT/'tools/verify_usgs_source.py')).load_module()
  d=verifier.validate(source)
  self.assertEqual(d['raw_points_verified'],144)
  self.assertEqual(d['ascii_cells_verified'],144)
  tile=source/'BigSur_Observed_16x16_32m.rgt.gz'
  self.assertTrue(tile.is_file())
  values,cell=C.read_tile(tile)
  self.assertEqual(cell,32.)
  self.assertTrue(np.isfinite(values).all())
  self.assertGreater(float(values.max()-values.min()),200.)
 def test_bad_geotiff_crs(self):
  self.assertEqual(self.run_import().returncode,0)
  self.assertEqual(C.read_tile(self.out)[0].shape,(16,16))
if __name__=='__main__':unittest.main(verbosity=2)
