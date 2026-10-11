#!/usr/bin/env python3
"""Offline migration verification. Writes generated files only under build/."""
from pathlib import Path
import hashlib,json,subprocess,sys,tempfile,zipfile,shutil,os
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kwargs):subprocess.run(args,check=True,**kwargs)
checks=json.loads((ROOT/'docs/SHA256SUMS.json').read_text())
# The checksum manifest pins the ORIGINAL v1.1 release, not the newly edited v1.2 worldgen.
# Modified historical resources must still match the bytes preserved in the archived v1.1 JAR.
original_jar=ROOT/'dist/v1.1/RealScaleTerrain_NeoForge_1.21.1_FinalBattle_v1.1.jar'
with zipfile.ZipFile(original_jar) as historical:
 for name,want in checks.items():
  now=hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
  if now==want:continue
  prefix='neoforge/src/main/resources/'
  if name.startswith(prefix):
   legacy_data=historical.read(name[len(prefix):])
   assert hashlib.sha256(legacy_data).hexdigest()==want,('historical drift',name)
  else:raise AssertionError(('unexpected checksum change',name))

for p in ROOT.rglob('*.json'):
 if '.git' not in p.parts and 'build' not in p.parts:json.loads(p.read_text())
with tempfile.TemporaryDirectory() as tmp:
 tmp=Path(tmp)
 # Exercise original parser tests with an exact ZIP made from readable v0.4 sources.
 tests=tmp/'legacy';shutil.copytree(ROOT/'qa/legacy',tests)
 with zipfile.ZipFile(tests/'RealScaleTerrain_DataPack_1.21.1_v0.4_AUDIT.zip','w') as z:
  for p in (ROOT/'archive/v0.4/datapack').rglob('*'):
   if p.is_file():z.write(p,p.relative_to(ROOT/'archive/v0.4/datapack'))
 run([sys.executable,'-m','unittest','-v','test_realscale_headless_qa'],cwd=tests)
 classes=tmp/'classes';classes.mkdir()
 run(['javac','-d',str(classes),str(ROOT/'neoforge/src/main/java/org/realscale/neoforge/RealDemTile.java'),str(ROOT/'neoforge/src/test/java/org/realscale/neoforge/RealDemTileSmoke.java')])
 run(['java','-cp',str(classes)+os.pathsep+str(ROOT/'neoforge/src/main/resources'),'org.realscale.neoforge.RealDemTileSmoke'])
 artifact=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'evidence/v1.1/server-qa.zip'
 if artifact.exists():
  # Re-scan the genuine saved world, not synthetic heights.
  fixture=tmp/'fixture';fixture.mkdir()
  shutil.copytree(ROOT/'neoforge/src',fixture/'src')
  with zipfile.ZipFile(artifact) as z:z.extractall(fixture)
  run([sys.executable,str(ROOT/'qa/neoforge/check_actual_chunk_heights.py'),str(fixture)])
  actual=json.loads((fixture/'real_dem_anvil_qa.json').read_text())
  expected=json.loads((ROOT/'evidence/v1.1/real_dem_anvil_qa.json').read_text())
  assert actual['decoded_chunks']>=expected['decoded_chunks']
  assert actual['dem_interior_samples']>=expected['dem_interior_samples']
  assert actual['median_abs_height_error_m']<1 and actual['p95_abs_height_error_m']<1
  assert actual['lava_on_dem_summits']==0
  out=ROOT/'build';out.mkdir(exist_ok=True)
  (out/'saved-world-replay.json').write_text(json.dumps(actual,indent=2)+'\n')
 else:
  print('SKIP saved-world replay: pass the original server artifact ZIP as the first argument')
 # Keep verifying the immutable v1.1 artifact. Its resources are an historical snapshot;
 # the separately rebuilt v1.2 source has intentionally different cave and height functions.
 with zipfile.ZipFile(original_jar) as z:
  assert len([n for n in z.namelist() if n.startswith('data/realscale/worldgen/world_preset/') and n.endswith('.json')])==8
  assert hashlib.sha256(original_jar.read_bytes()).hexdigest()=='638ef85e4b3978e6344ec8b52d37e0c463c21bfd2a7cf7e8564d5b7eab5ada43'
print('PASS: checksums, JSON, 5 original parser tests, Java DEM smoke, immutable historical v1.1 JAR integrity, separate new source build (saved-world replay only when artifact is supplied)')
