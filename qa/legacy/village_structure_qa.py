#!/usr/bin/env python3
"""Real Minecraft 1.21.1 village grounding inspection for an isolated QA world.

This is an automated structure sampler, not a proof that every structure is grounded.
It uses vanilla server /locate and /forceload to generate a nearby village,
then reads actual block states below village piece bounding boxes.
"""
import argparse, json, queue, re, subprocess, threading, time
from pathlib import Path
import realscale_headless_qa as core

SOLID_EXCLUDE={'minecraft:air','minecraft:cave_air','minecraft:void_air',
 'minecraft:water','minecraft:lava'}


def generate_village(root,timeout=650):
    server=root/'minecraft_server_1.21.1.jar'
    assert server.exists(),server
    log=[]; q=queue.Queue()
    proc=subprocess.Popen(['java','-Xms512M','-Xmx4G','-jar',str(server),'nogui'],
       cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
       text=True,bufsize=1)
    def reader():
        for s in proc.stdout:
            log.append(s)
            q.put(s)
    threading.Thread(target=reader,daemon=True).start()
    def wait_for(check,seconds):
        deadline=time.time()+seconds
        while time.time()<deadline:
            if proc.poll() is not None:raise RuntimeError(f'Server exited with {proc.returncode}')
            try: line=q.get(timeout=1)
            except queue.Empty:continue
            if check(line):return line
        raise RuntimeError('Timed out waiting for server response; see village_server.log')
    village=None
    try:
        wait_for(lambda s:'Done (' in s and 'For help' in s,180)
        proc.stdin.write('execute positioned 0 120 0 run locate structure #minecraft:village\n');proc.stdin.flush()
        line=wait_for(lambda s:('nearest' in s.lower() and ' at [' in s.lower()) or 'could not find' in s.lower() or 'unknown' in s.lower(),300)
        m=re.search(r'\[\s*(-?\d+)\s*,\s*(?:~|-?\d+)\s*,\s*(-?\d+)\s*\]',line)
        if m is None:raise RuntimeError('Village locate failed: '+line.strip())
        x,z=map(int,m.groups());village={'x':x,'z':z,'locate_response':line.strip()}
        cmd=f'forceload add {x-32} {z-32} {x+32} {z+32}'
        proc.stdin.write(cmd+'\n');proc.stdin.flush()
        wait_for(lambda s:'Marked' in s and 'chunk' in s.lower(),300)
        # Let asynchronously scheduled region/structure tasks finish before save.
        time.sleep(45)
        proc.stdin.write('save-all flush\n');proc.stdin.flush()
        wait_for(lambda s:'Saved the game' in s or 'Saved' in s,120)
    finally:
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=150)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
        (root/'village_server.log').write_text(''.join(log))
    if proc.returncode:raise RuntimeError('Minecraft server exited with '+str(proc.returncode))
    return village


def audit_village(root,target):
    region_path=root/'world'/'region'
    found=[]; samples=[]
    for p in region_path.glob('r.*.*.mca'):
        if p.stat().st_size < 8192:
            continue  # Empty region placeholder, not a saved Anvil header.
        for chunk in core.iter_region_chunks(p):
            starts=chunk.get('structures',{}).get('starts',{})
            for name,obj in starts.items():
                if not name.startswith('minecraft:village') or not isinstance(obj,dict):continue
                if obj.get('id') in ('INVALID','minecraft:empty'):continue
                bb=obj.get('BB',[])
                if len(bb)!=6:continue
                children=[c for c in obj.get('Children',[]) if len(c.get('BB',[]))==6]
                found.append({'name':name,'BB':bb,'pieces':len(children)})
                for child in children:
                    b=child['BB']; xmin,ymin,zmin,xmax,ymax,zmax=b
                    if xmax<xmin or zmax<zmin:continue
                    candidates=[((xmin+xmax)//2,(zmin+zmax)//2), (xmin,zmin),(xmax,zmax)]
                    for x,z in candidates:
                        samples.append({'x':x,'z':z,'floor_y':ymin-1,'piece':str(child.get('id','?')),'bb':b})
    # Reparse only chunk columns containing sample locations, with O(N) region work.
    pending={((s['x']//16),(s['z']//16)) for s in samples}
    chunk_cache={}
    for p in region_path.glob('r.*.*.mca'):
        if p.stat().st_size < 8192:
            continue
        for c in core.iter_region_chunks(p):
            k=(c.get('xPos'),c.get('zPos'))
            if k in pending:chunk_cache[k]=c
    checked=0;unsupported=0;missing=0;examples=[]
    for s in samples:
        x,z,y=s['x'],s['z'],s['floor_y']
        c=chunk_cache.get((x//16,z//16))
        if c is None:missing+=1;continue
        checked+=1
        # 12m vertical support window: conservative warning rather than a
        # definitive classification of naturally stilted or floating structures.
        supported=any(core.get_block(c,x%16,y-d,z%16) not in SOLID_EXCLUDE for d in range(12))
        if not supported:
            unsupported+=1
            if len(examples)<20:examples.append({'x':x,'z':z,'foundation_y':y,'piece':s['piece']})
    return {'locate':target,'village_starts':found,'piece_samples':len(samples),
      'checked_support_samples':checked,'missing_samples':missing,
      'unsupported_in_12m':unsupported,'unsupported_examples':examples,
      'structure_test_conclusive':bool(found and checked>=10 and missing==0),
      'notes':'A conservative possible-floating-pieces detector; villages can intentionally use pillars, docks and steep-site placement.'}


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--world',required=True,type=Path)
    args=ap.parse_args()
    root=args.world
    rep={'structure_test_conclusive':False}
    try:
        target=generate_village(root)
        rep=audit_village(root,target)
    except Exception as exc:rep['error']=str(exc)
    (root/'village_qa_result.json').write_text(json.dumps(rep,indent=2))
    print(json.dumps(rep,indent=2))
    if not rep.get('structure_test_conclusive'):raise SystemExit(1)

if __name__=='__main__':main()