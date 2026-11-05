#!/usr/bin/env python3
"""RealScale 1.21.1 headless vanilla-engine smoke test (not a NeoForge binary test).

On a computer with internet and Java 21, run:
 python3 realscale_headless_qa.py --pack /path/to/RealScaleTerrain_DataPack_1.21.1_v0.5_GEO_EXPERIMENT.zip

Downloads Mojang's official 1.21.1 server and checks its SHA-1. Uses disposable
worlds only. Does not modify existing Minecraft installations or player saves.
"""
import argparse, gzip, hashlib, json, math, os, queue, re, shutil, struct, subprocess
import sys, tempfile, threading, time, urllib.request, zipfile, zlib
from pathlib import Path

SERVER_URL='https://piston-data.mojang.com/v1/objects/59353fb40c36d304f2035d51e7d6e6baa98dc05c/server.jar'
SERVER_SHA1='59353fb40c36d304f2035d51e7d6e6baa98dc05c'
MASK=(1<<64)-1

def read_nbt(data):
    p=0
    def take(n):
        nonlocal p
        if p+n>len(data): raise ValueError('truncated NBT')
        s=data[p:p+n]; p+=n; return s
    def uint(n):return int.from_bytes(take(n),'big',signed=False)
    def sint(n):return int.from_bytes(take(n),'big',signed=True)
    def string():return take(uint(2)).decode('utf-8','replace')
    def payload(t):
        if t==1:return sint(1)
        if t==2:return sint(2)
        if t==3:return sint(4)
        if t==4:return sint(8)
        if t==5:return struct.unpack('>f',take(4))[0]
        if t==6:return struct.unpack('>d',take(8))[0]
        if t==7:return take(sint(4))
        if t==8:return string()
        if t==9:
            subtype=uint(1); size=sint(4)
            if size<0 or size>1000000:raise ValueError('suspicious NBT list')
            return [payload(subtype) for _ in range(size)]
        if t==10:
            d={}
            while True:
                ty=uint(1)
                if ty==0:return d
                name=string();d[name]=payload(ty)
        if t==11:return [sint(4) for _ in range(sint(4))]
        if t==12:return [sint(8) for _ in range(sint(4))]
        raise ValueError('unknown NBT type: '+str(t))
    t=uint(1)
    if t!=10:raise ValueError('NBT root not compound')
    rootname=string()
    return payload(t)

def iter_region_chunks(region_file):
    blob=region_file.read_bytes()
    if len(blob)<8192:raise ValueError('bad region '+str(region_file))
    for i in range(1024):
        off=int.from_bytes(blob[i*4:i*4+3],'big')*4096
        sectors=blob[i*4+3]
        if not off or not sectors:continue
        size=int.from_bytes(blob[off:off+4],'big')
        if size<2 or off+4+size>len(blob):continue
        comp=blob[off+4]; raw=blob[off+5:off+4+size]
        if comp==2:raw=zlib.decompress(raw)
        elif comp==1:raw=gzip.decompress(raw)
        elif comp==3:pass
        else:raise ValueError('unknown region compression '+str(comp))
        yield read_nbt(raw)

def unpack_palette_index(data,index,bits):
    entries_per_long=64//bits
    n=index//entries_per_long
    if n>=len(data):return 0
    return ((data[n]&MASK)>>((index%entries_per_long)*bits))&((1<<bits)-1)

def surface_height(chunk,x,z,min_y=-64,height=2096):
    heights=chunk.get('Heightmaps',chunk.get('heightmaps',{}))
    longs=heights.get('WORLD_SURFACE',heights.get('world_surface',[]))
    if not longs:return None
    bits=max(1,(height+1).bit_length())
    idx=z*16+x
    return min_y+unpack_palette_index(longs,idx,bits)-1

def get_block(chunk,x,y,z):
    section_y=y//16
    section=next((s for s in chunk.get('sections',[]) if s.get('Y')==section_y),None)
    if not section:return 'minecraft:air'
    states=section.get('block_states',{})
    palette=states.get('palette',[])
    if not palette:return 'minecraft:air'
    if len(palette)==1:return palette[0].get('Name','?')
    bits=max(4,(len(palette)-1).bit_length())
    n=unpack_palette_index(states.get('data',[]),(y%16)*256+z*16+x,bits)
    return palette[n].get('Name','?') if n<len(palette) else 'INVALID_PALETTE_INDEX'

def check_pack(pack):
    with zipfile.ZipFile(pack) as z:
        if z.testzip():raise RuntimeError('corrupted ZIP')
        mc=json.loads(z.read('pack.mcmeta'))
        if mc['pack']['pack_format']!=48:raise RuntimeError('incorrect 1.21.1 pack format')
        p='data/realscale/worldgen/world_preset/georeal_ultimate.json'
        if p not in z.namelist():raise RuntimeError('ultimate world preset absent')
        ns=json.loads(z.read('data/realscale/worldgen/noise_settings/georeal_ultimate.json'))
        assert ns['sea_level']==63
        assert ns['noise']['min_y']==-64
        return ns['noise']['min_y'],ns['noise']['height']

def make_properties(seed):
    return ('# Disposable QA world, generated automatically\n'
            'level-name=world\nlevel-type=realscale:georeal_ultimate\n'
            f'level-seed={seed}\n'
            'initial-enabled-packs=vanilla,file/RealScaleTerrain_DataPack_1.21.1_v0.5_GEO_EXPERIMENT.zip\n'
            'online-mode=true\nserver-ip=127.0.0.1\nspawn-protection=0\n'
            'view-distance=3\nsimulation-distance=3\n'
            'max-players=1\nmax-tick-time=-1\n'
            'motd=RealScale Automated QA\n')

def download_server(dst):
    if dst.exists() and hashlib.sha1(dst.read_bytes()).hexdigest()==SERVER_SHA1:return
    print('Downloading official Mojang 1.21.1 server...',flush=True)
    with urllib.request.urlopen(SERVER_URL,timeout=45) as resp,dst.open('wb') as file:
        shutil.copyfileobj(resp,file)
    if hashlib.sha1(dst.read_bytes()).hexdigest()!=SERVER_SHA1:
        dst.unlink(missing_ok=True);raise RuntimeError('Minecraft server SHA1 mismatch')

def launch_server(world_dir,server_jar,timeout=420):
    logs=[]; q=queue.Queue()
    proc=subprocess.Popen(['java','-Xms512M','-Xmx4G','-jar',str(server_jar),'nogui'],
        cwd=world_dir,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
        text=True,bufsize=1)
    def reader():
        for line in proc.stdout:
            logs.append(line)
            q.put(line)
    threading.Thread(target=reader,daemon=True).start()
    try:
        deadline=time.time()+timeout;ready=False
        while time.time()<deadline:
            if proc.poll() is not None:break
            try:line=q.get(timeout=1)
            except queue.Empty:continue
            if 'Done (' in line and 'For help' in line:
                ready=True; break
            if 'Failed to load' in line or 'Error loading registry' in line:
                break
        if not ready:
            raise RuntimeError('server failed to start with preset; inspect server.log')
        # Sequential chunk requests: deliberately prevent simultaneous far-away loads.
        # The vanilla watchdog is disabled ONLY in this disposable server. A 60s
        # watchdog timeout was previously triggered by concurrent worldgen requests.
        commands=['forceload add 1024 -1024', 'forceload add -2048 2048']
        for cmd in commands:
            proc.stdin.write(cmd+'\n'); proc.stdin.flush()
            # Wait for the command to complete on the server thread before issuing
            # the next large terrain request. Fixed sleeps were not sufficient.
            expected='Marked chunk'
            deadline=time.time()+600
            acknowledged=False
            while time.time()<deadline:
                if proc.poll() is not None:
                    raise RuntimeError(f'server exited during {cmd}: {proc.returncode}')
                try: line=q.get(timeout=1)
                except queue.Empty: continue
                if expected in line:
                    acknowledged=True;break
                if 'Failed to load' in line or 'Exception' in line:
                    raise RuntimeError('error while generating chunks: '+line.strip())
            if not acknowledged:
                raise RuntimeError('Timed out waiting for '+cmd)
        proc.stdin.write('save-all flush\n');proc.stdin.flush()
        # Ask the vanilla server for a clean save + stop, then wait for process exit.
        # The log is retained so incomplete region files cannot be mistaken for
        # terrain corruption after a watchdog shutdown.
        time.sleep(8)
    finally:
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=90)
            except subprocess.TimeoutExpired:
                proc.kill();proc.wait()
        (world_dir/'server.log').write_text(''.join(logs),encoding='utf-8')
    return proc.returncode

def audit_generated(world_dir,min_y,height):
    regions=list((world_dir/'world'/'region').glob('r.*.*.mca'))
    report={'region_files':len(regions),'chunks':0,'columns':0,'lava_at_surface':0,
            'water_at_surface':0,'surface_y_min':None,'surface_y_max':None,
            'surface_y_values':[],'structures':{},'bad_palette_indices':0}
    for region in regions:
        for chunk in iter_region_chunks(region):
            report['chunks']+=1
            for name,start in chunk.get('structures',{}).get('starts',{}).items():
                if isinstance(start,dict) and start.get('id') not in ('INVALID','minecraft:empty'):
                    report['structures'][name]=report['structures'].get(name,0)+1
            for z in range(16):
                for x in range(16):
                    y=surface_height(chunk,x,z,min_y,height)
                    if y is None:continue
                    report['columns']+=1
                    report['surface_y_min']=y if report['surface_y_min'] is None else min(y,report['surface_y_min'])
                    report['surface_y_max']=y if report['surface_y_max'] is None else max(y,report['surface_y_max'])
                    if len(report['surface_y_values'])<500000:report['surface_y_values'].append(y)
                    block=get_block(chunk,x,y,z)
                    if block=='minecraft:lava':report['lava_at_surface']+=1
                    if block=='minecraft:water':report['water_at_surface']+=1
                    if block=='INVALID_PALETTE_INDEX':report['bad_palette_indices']+=1
    if report['surface_y_values']:
        vals=sorted(report.pop('surface_y_values'))
        report['median_surface_y']=vals[len(vals)//2]
        report['p95_surface_y']=vals[int((len(vals)-1)*.95)]
        report['p05_surface_y']=vals[int((len(vals)-1)*.05)]
    else:report.pop('surface_y_values')
    report['pass_world_created']=report['chunks']>0 and report['columns']>0
    report['pass_no_lava_ocean']=report['columns']>0 and report['lava_at_surface']/report['columns']<0.001
    report['pass_height_bounds']=report['surface_y_min'] is not None and report['surface_y_min']>=min_y and report['surface_y_max']<min_y+height
    report['structures_verified']=False # not proof that structures are properly grounded
    return report

def main():
    ap=argparse.ArgumentParser(description='Offline headless Minecraft 1.21.1 world generation smoke test')
    ap.add_argument('--pack',required=True,type=Path)
    ap.add_argument('--seed',type=int,default=20261011)
    ap.add_argument('--workspace',type=Path,default=Path.home()/'Desktop'/'RealScaleQA')
    ap.add_argument('--skip-server',action='store_true',help='Only validate package, no server simulation')
    ap.add_argument('--accept-eula',action='store_true',help='Explicitly accept Minecraft EULA for automated CI run')
    a=ap.parse_args()
    low,ht=check_pack(a.pack)
    print('Static pack check: PASS; height:',low,ht)
    if a.skip_server:return
    a.workspace.mkdir(parents=True,exist_ok=True)
    jar=a.workspace/'minecraft_server_1.21.1.jar'
    try:download_server(jar)
    except Exception as e:
        print('SERVER UNAVAILABLE: '+str(e));sys.exit(3)
    if not a.accept_eula and (not sys.stdin.isatty() or input('Confirm you accept Minecraft EULA for the disposable test server (type YES): ').strip()!='YES'):
        print('No server started.');sys.exit(2)
    root=a.workspace/f'qa_seed_{a.seed}'
    if root.exists():
        print('Test directory already exists; not overwriting:',root)
        sys.exit(4)
    root.mkdir()
    shutil.copy2(jar,root/'minecraft_server_1.21.1.jar')
    dp=root/'world'/'datapacks';dp.mkdir(parents=True)
    shutil.copy2(a.pack,dp/'RealScaleTerrain_DataPack_1.21.1_v0.5_GEO_EXPERIMENT.zip')
    (root/'server.properties').write_text(make_properties(a.seed))
    (root/'eula.txt').write_text('eula=true\n')
    try:
        code=launch_server(root,root/'minecraft_server_1.21.1.jar')
        if code not in (0,None):raise RuntimeError(f'Minecraft server exited unexpectedly: {code}')
        level_file=root/'world'/'level.dat'
        if not level_file.exists():
            raise RuntimeError('World level.dat not created')
        if level_file.exists():
            world=read_nbt(gzip.decompress(level_file.read_bytes()))
            dims=world.get('Data',{}).get('WorldGenSettings',{}).get('dimensions',{})
            gen=dims.get('minecraft:overworld',{}).get('generator',{})
            print('Saved world generator settings:',gen.get('settings'))
            if gen.get('settings')!='realscale:georeal_ultimate':
                raise RuntimeError('WRONG PRESET! engine did not select Ultimate')
        rep=audit_generated(root,low,ht)
    except Exception as ex:
        rep={'error':str(ex),'pass_world_created':False,'pass_no_lava_ocean':False,'pass_height_bounds':False}
    (root/'qa_result.json').write_text(json.dumps(rep,indent=2,ensure_ascii=False))
    print(json.dumps(rep,indent=2,ensure_ascii=False))
    print('Full report:',root/'qa_result.json')
    if not rep.get('pass_world_created') or not rep.get('pass_no_lava_ocean') or not rep.get('pass_height_bounds'):
        sys.exit(1)

if __name__=='__main__':main()