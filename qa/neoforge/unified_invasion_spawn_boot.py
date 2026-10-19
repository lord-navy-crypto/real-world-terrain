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
PROFILE="realscale:unified_"+SOURCE+"_invasion_v1_end3_spawn3"
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
        "invasionShowcase":False,"endBiomeAccents":True,
        "spawnEndHabitats":True
    }),encoding="utf-8")
    data=Path(temp)/"unified-preset.zip"
    produced,_=export(JAR,draft,data,include_resource_pack=False)
    if produced!=PROFILE:
        raise AssertionError("Unexpected exported preset ID: "+produced)
    # Inspect the *actual ZIP handed to NeoForge*, not only the Python
    # create_preset() result. Distinct codec IDs prevent silent fallback.
    with zipfile.ZipFile(data) as archive:
        path="data/realscale/worldgen/world_preset/"+PROFILE.split(":")[1]+".json"
        world_preset=json.loads(archive.read(path))
        source=world_preset["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["source"]
        if source.get("type")!="realscale:cross_realm_spawn3":
            raise AssertionError("Exported spawn3 codec missing: "+repr(source))
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
        for biome in ("end_chorus_gardens","end_crystal_barrens",
                      "end_shattered_glades"):
            response=rcon.send("execute in minecraft:overworld "
                "positioned 0 100 0 if biome ~ ~ ~ realscale:"+biome+
                " run seed")
            if "Seed:" in response:
                hits.append(biome)
        if len(hits)!=1:
            raise AssertionError("Expected exactly one of the THREE custom "
                                 "End invasion habitats at x=0,z=0: "+str(hits))
        for old in ("nether_wastes","end_highlands"):
            response=rcon.send("execute in minecraft:overworld "
                "positioned 0 100 0 if biome ~ ~ ~ minecraft:"+old+
                " run seed")
            if "Seed:" in response:
                raise AssertionError("Old Nether/vanilla End spawn regression: "+old)
        print("UNIFIED_INVASION_SPAWN_BIOME_PASS",SOURCE,hits[0],flush=True)
        # Verify the physical 9x9 arrival pad, not only a biome label.
        # Its spawn altitude is recorded in level.dat as an Int TAG_SpawnY.
        precheck=rcon.send("save-all flush")
        if "Saved" not in precheck:
            raise AssertionError("Could not save arrival platform: "+repr(precheck))
        saved_files=list((MOD/"run").glob("**/"+WORLD+"/level.dat"))
        if not saved_files:
            raise AssertionError("No saved level.dat to inspect initial spawn Y")
        nbt=gzip.decompress(saved_files[0].read_bytes())
        marker=bytes((3,0,6))+b"SpawnY"
        index=nbt.find(marker)
        if index<0:
            raise AssertionError("Missing SpawnY NBT Int tag")
        spawn_y=int.from_bytes(nbt[index+len(marker):index+len(marker)+4],
                               byteorder="big",signed=True)
        if spawn_y<60:
            raise AssertionError("Arrival platform event did not set a safe SpawnY: "+str(spawn_y))
        inlay={"end_chorus_gardens":"purpur_block",
               "end_crystal_barrens":"amethyst_block",
               "end_shattered_glades":"end_stone_bricks"}[hits[0]]
        check_blocks=[(0,spawn_y-1,0,inlay),
                      (0,spawn_y,0,"air"),
                      (4,spawn_y,0,"end_stone_brick_wall")]
        for dx in (-4,4):
            for dz in (-4,4):
                check_blocks.append((dx,spawn_y+1,dz,"sea_lantern"))
        for x,y,z,block in check_blocks:
            command=(f"execute in minecraft:overworld positioned {x} {y} {z} "
                     f"if block ~ ~ ~ minecraft:{block} run seed")
            reply=rcon.send(command)
            if "Seed:" not in reply:
                raise AssertionError(f"Starter platform missing {block} at "
                                     f"{x},{y},{z}: {reply!r}")
        print("UNIFIED_INVASION_9X9_SPAWN_PAD_PASS",SOURCE,spawn_y,flush=True)
        # /execute if biome reads only LOADED End chunks; this test world
        # has not yet visited the End. Locate via the generator's BiomeSource
        # instead, so we can verify ungenerated locations without forcing
        # enormous Grand End chunks into memory.
        names=("end_chorus_gardens","end_crystal_barrens","end_shattered_glades")
        for name in names:
            command=("execute in minecraft:the_end positioned 0 80 0 "
                     "run locate biome realscale:"+name)
            reply=rcon.send(command)
            if not reply.strip() or any(t in reply.lower() for t in (
                "could not find","unable to find","not found","unknown biome",
                "no biome")):
                raise AssertionError("End source did not locate "+name+": "+repr(reply))
            print("REAL_GRAND_END_BIOME_LOCATE_PASS",SOURCE,name,repr(reply),flush=True)
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
if b"realscale:cross_realm_spawn3" not in save:
    raise AssertionError("Dedicated spawn3 biome-source codec not persisted in level.dat")
if b"realscale:end_accents" not in save:
    raise AssertionError("Grand End three-biome wrapper missing from saved world")
if b"realscale:cross_realm_invasion" not in save:
    raise AssertionError("Invasion codec absent from saved world")
if b"realscale:grand_nether" not in save or b"realscale:grand_end" not in save:
    raise AssertionError("Grand Nether/End lost in new world")
print("UNIFIED_INVASION_WORLD_SAVED_PASS",SOURCE,worlds[0],flush=True)
