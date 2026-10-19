#!/usr/bin/env python3
"""Actual new-world NeoForge smoke: unified invasion biome at initial spawn.

Unlike static codecs, this boots a server with an exported custom world preset,
checks a real biome predicate via RCON, saves, and verifies the saved generator.
"""
import gzip
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

ROOT=Path(__file__).resolve().parents[2]
MOD=ROOT/"neoforge"
sys.path.insert(0,str(ROOT/"scripts"))
sys.path.insert(0,str(ROOT/"qa/neoforge"))
from export_terrain_lab_datapack import export
from rcon_worldgen_smoke import Rcon

SOURCE=os.environ.get("REALSCALE_UNIFIED_SPAWN_BASE","sacred")
if SOURCE not in ("sacred","measured"):
    raise SystemExit("Expected REALSCALE_UNIFIED_SPAWN_BASE=sacred|measured")
WORLD="unified-invasion-spawn-"+SOURCE
PROFILE="realscale:unified_"+SOURCE+"_invasion_v1_end3"
JARS=sorted((MOD/"build/libs").glob("RealScale-TVA-Ultimate*.jar"))
if not JARS:
    raise SystemExit("Build the real NeoForge mod JAR before this smoke test")
JAR=JARS[-1]
with tempfile.TemporaryDirectory(prefix="realscale-spawn-profile-") as temp:
    draft=Path(temp)/"config.json"
    draft.write_text(json.dumps({
        "preset":"landscape","reliefPercent":100,
        "elevation":0,"caves":50,"riverGrade":6,"inland":True,
        "worldSource":SOURCE,"dimensionalInvasion":True,
        "invasionShowcase":False,"endBiomeAccents":True
    }),encoding="utf-8")
    data=Path(temp)/"unified-preset.zip"
    produced,_=export(JAR,draft,data,include_resource_pack=False)
    if produced!=PROFILE:
        raise AssertionError("Unexpected exported preset ID: "+produced)
    server_dir=MOD/"run"
    for candidate in (server_dir,server_dir/"server",server_dir/"gametestserver"):
        candidate.mkdir(parents=True,exist_ok=True)
        (candidate/"eula.txt").write_text("eula=true\n")
        (candidate/"server.properties").write_text(
            "level-name="+WORLD+"\n"
            "level-type="+PROFILE+"\n"
            "level-seed=785312\n"
            "view-distance=3\n"
            "simulation-distance=3\n"
            "spawn-protection=0\n"
            "max-tick-time=-1\n"
            "online-mode=true\n"
            "enable-rcon=true\n"
            "rcon.password=realscale_unified_spawn_ci_only\n"
            "rcon.port=25575\n",encoding="utf-8")
        packdir=candidate/WORLD/"datapacks"
        packdir.mkdir(parents=True,exist_ok=True)
        (packdir/"unified-invasion.zip").write_bytes(data.read_bytes())

log=[]
ready=threading.Event()
p=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
                   cwd=MOD,stdin=subprocess.PIPE,stdout=subprocess.PIPE,
                   stderr=subprocess.STDOUT,text=True,bufsize=1,
                   start_new_session=True)

def drain():
    for line in p.stdout:
        log.append(line)
        if "Done (" in line and "For help" in line:
            ready.set()
thread=threading.Thread(target=drain,daemon=True)
thread.start()
try:
    deadline=time.monotonic()+650
    while not ready.is_set() and p.poll() is None and time.monotonic()<deadline:
        time.sleep(1)
    if not ready.is_set():
        raise RuntimeError("Real unified "+SOURCE+" server did not boot")
    # Query the guaranteed biome column, not an ungrounded /locate search.
    rcon=Rcon(timeout=120,password="realscale_unified_spawn_ci_only")
    try:
        hits=[]
        for biome in ("nether_wastes","end_highlands"):
            response=rcon.send("execute in minecraft:overworld "
                "positioned 0 100 0 if biome ~ ~ ~ minecraft:"+biome+
                " run seed")
            if "Seed:" in response:
                hits.append(biome)
        if len(hits)!=1:
            raise AssertionError("Expected exactly one genuine Nether/End biome "
                                 "at x=0,z=0: "+str(hits))
        print("UNIFIED_INVASION_SPAWN_BIOME_PASS",SOURCE,hits[0],flush=True)
        # Sample deterministic accent-patch centers in the ACTUAL End
        # BiomeSource: ensure each of the three new biome IDs is reachable
        # through a real NeoForge server without regenerating remote chunks.
        mask=(1<<64)-1
        def mix(v):
            v &= mask
            v ^= v>>30;v=(v*0xbf58476d1ce4e5b9)&mask
            v ^= v>>27;v=(v*0x94d049bb133111eb)&mask
            return v^(v>>31)
        candidate={0:[],1:[],2:[]}
        for gx in range(-16,17):
            for gz in range(-16,17):
                h=mix(gx*0x9e3779b97f4a7c15
                      ^gz*0xc2b2ae3d27d4eb4f^0x71a6e31d)
                if h%5:continue
                x=gx*512+256+((h>>12)%129)-64
                z=gz*512+256+((h>>27)%129)-64
                if x*x+z*z<1024*1024:continue
                candidate[(h>>54)%3].append((x*x+z*z,x,z))
        names=("end_chorus_gardens","end_crystal_barrens","end_shattered_glades")
        for kind,name in enumerate(names):
            found=None
            for _,x,z in sorted(candidate[kind])[:70]:
                out=rcon.send(
                    f"execute in minecraft:the_end positioned {x} 80 {z} "
                    f"if biome ~ ~ ~ realscale:{name} run seed")
                if "Seed:" in out:
                    found=(x,z)
                    break
            if found is None:
                raise AssertionError(
                    "No real End biome found in 70 candidate cells: "+name)
            print("REAL_GRAND_END_BIOME_PASS",SOURCE,name,found,flush=True)
        saved=rcon.send("save-all flush")
        if "Saved" not in saved:
            raise AssertionError("Missing world-save ACK: "+repr(saved))
        try:rcon.send("stop")
        except (EOFError, ConnectionResetError, BrokenPipeError):pass
    finally:
        rcon.close()
    p.wait(timeout=110)
finally:
    if p.poll() is None:
        os.killpg(p.pid,signal.SIGINT)
        try:p.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGKILL)
            p.wait()
    thread.join(timeout=4)
    path=MOD/"build"/("unified-invasion-spawn-"+SOURCE+".log")
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text("".join(log),encoding="utf-8")
    print("INVASION_SPAWN_LOG_TAIL",path.read_text()[-4000:],flush=True)
worlds=list((MOD/"run").glob("**/"+WORLD+"/level.dat"))
if not worlds:
    raise AssertionError("Unified invasion level.dat not written")
save=gzip.decompress(worlds[0].read_bytes())
if b"realscale:end_accents" not in save:
    raise AssertionError("Grand End three-biome wrapper missing from saved world")
if b"realscale:cross_realm_invasion" not in save:
    raise AssertionError("Invasion codec absent from saved world")
if b"realscale:grand_nether" not in save or b"realscale:grand_end" not in save:
    raise AssertionError("Grand Nether/End lost in new world")
print("UNIFIED_INVASION_WORLD_SAVED_PASS",SOURCE,worlds[0],flush=True)
