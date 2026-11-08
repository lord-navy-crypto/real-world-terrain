#!/usr/bin/env python3
"""Real NeoForge city-block acceptance: registry, /place template and Anvil.

This proves that the optional city datapack is loaded, each original NBT
places through vanilla commands and a rooftop beacon survives save-all.
It does not claim natural random-spread placement or terrain-fit safety.
"""
from __future__ import annotations

import gzip
import json
import os
import pathlib
import signal
import subprocess
import sys
import threading
import time

sys.path.insert(0,str(pathlib.Path(__file__).parent))
from rcon_worldgen_smoke import Rcon,await_full_chunk
from realscale_headless_qa import iter_region_chunks,get_block

ROOT=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
WORLD="realscale-urban-city-ci"
PACK_ID="mod/realscale:resourcepacks/realscale_structures_urban_districts"
# (id, worldX, worldZ, tower-top, local X of beacon, local Z)
BUILDINGS=(
    ("commercial_skyscrapers",0,0,94,9,9),
    ("civic_centre",128,0,58,9,9),
    ("residential_quarter",256,0,37,9,9),
    ("industrial_edge",384,0,34,9,37),
)
Y=220

config=ROOT/"run/config/realscale-terrain-lab.json"
config.parent.mkdir(exist_ok=True,parents=True)
config.write_text(json.dumps({
    "structureDensity":4,
    "dimensionStructureDensity":2,
    "architectureStructuresEnabled":True,
    "urbanDistrictsEnabled":True
},indent=2)+"\n")
for folder in ("run","run/server","run/gametestserver"):
    p=ROOT/folder
    p.mkdir(parents=True,exist_ok=True)
    (p/"eula.txt").write_text("eula=true\n")
    (p/"server.properties").write_text(
        f"level-name={WORLD}\n"
        "level-type=realscale:natural_three_realms\n"
        "level-seed=42\n"
        "spawn-chunk-radius=0\n"
        "spawn-protection=0\n"
        "view-distance=3\n"
        "simulation-distance=3\n"
        "max-players=1\n"
        "max-tick-time=-1\n"
        "online-mode=true\n"
        "server-ip=127.0.0.1\n"
        "enable-rcon=true\n"
        "rcon.port=25575\n"
        "rcon.password=realscale_ci_probe_only\n")
lines=[]
ready=threading.Event()
p=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
    cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,text=True,bufsize=1,start_new_session=True)
def consume():
    for line in p.stdout:
        lines.append(line)
        if "Done (" in line and "For help" in line:
            ready.set()
thread=threading.Thread(target=consume,daemon=True)
thread.start()
error=None
placed=[]
try:
    deadline=time.monotonic()+540
    while time.monotonic()<deadline and p.poll() is None and not ready.is_set():
        time.sleep(1)
    if not ready.is_set():
        raise RuntimeError("Urban world failed to boot; check log artifact")
    rcon=None
    for _ in range(20):
        try:
            rcon=Rcon(timeout=260)
            break
        except (ConnectionError,OSError,TimeoutError):
            time.sleep(1)
    if rcon is None:
        raise RuntimeError("Urban world RCON was unavailable")
    try:
        selected=rcon.send("datapack list enabled")
        print("URBAN_PACK_SELECTION",selected,flush=True)
        if PACK_ID not in selected:
            raise AssertionError("City pack missing from enabled datapacks")
        for name,x,z,top,roofX,roofZ in BUILDINGS:
            # Vanilla 1.21.1 template placement needs the entire 48x48
            # footprint loaded, not only the originating 16x16 chunk.
            area=f"{x} {z} {x+63} {z+63}"
            ack=rcon.send(f"forceload add {area}")
            if "Marked" not in ack and "already" not in ack.lower():
                raise AssertionError("Couldn't load urban block test bay "+name)
            await_full_chunk(rcon,x+24,z+24,timeout_seconds=250)
            deadline=time.monotonic()+280
            while True:
                corners=[
                    rcon.send(f"execute if loaded {xx} {Y} {zz} run seed")
                    for xx,zz in ((x+8,z+8),(x+40,z+8),
                                  (x+8,z+40),(x+40,z+40))]
                if all("Seed:" in reply for reply in corners):
                    break
                if time.monotonic()>deadline:
                    raise RuntimeError("Not all urban template chunks loaded "+name)
                time.sleep(2)
            ans=rcon.send(f"place template realscale:urban/{name} {x} {Y} {z}")
            if f'Loaded template "realscale:urban/{name}" at' not in ans:
                raise AssertionError("Vanilla failed to place city template "
                                     +name+": "+ans)
            roof=rcon.send(
                f"execute if block {x+roofX} {Y+top+1} {z+roofZ} "
                "minecraft:sea_lantern run seed")
            if "Seed:" not in roof:
                raise AssertionError("High-rise rooftop block not actually placed "+name)
            saved=rcon.send("save-all flush")
            if "Saved" not in saved and "saved" not in saved:
                raise RuntimeError("Minecraft did not save city template "+name)
            print("URBAN_BLOCK_PLACED_AND_SAVED",name,x,z,flush=True)
            placed.append(name)
            rcon.send("forceload remove "+area)
        try:rcon.send("stop")
        except (EOFError,BrokenPipeError,ConnectionResetError):
            pass
    finally:rcon.close()
    p.wait(timeout=120)
except BaseException as exc:
    error=repr(exc)
finally:
    if p.poll() is None:
        os.killpg(p.pid,signal.SIGINT)
        try:p.wait(timeout=30)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGKILL)
            p.wait()
    thread.join(timeout=5)
    output=ROOT/"build/urban-city-server.log"
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text("".join(lines),encoding="utf-8")
    print("URBAN_SERVER_READY",ready.is_set(),"EXIT",p.returncode,
          "LOG_TAIL",output.read_text()[-14000:],flush=True)
if error is not None:
    raise RuntimeError(error)
if p.returncode!=0:
    raise RuntimeError("NeoForge urban server exited nonzero: "+str(p.returncode))
if len(placed)!=len(BUILDINGS):
    raise AssertionError("All four districts must be placed")

worlds=list((ROOT/"run").glob("**/"+WORLD+"/level.dat"))
if not worlds:
    raise RuntimeError("No saved urban world level.dat")
world=worlds[0].parent
level=gzip.decompress(worlds[0].read_bytes())
if b"realscale:natural_three_realms" not in level and b"realscale:tva_natural_smoothed" not in level:
    raise AssertionError("Urban save does not retain RealScale natural Overworld")
if b"realscale_structures_urban_districts" not in level:
    raise AssertionError("Urban datapack absent from saved world")
chunks={}
regions=list((world/"region").glob("r.*.*.mca"))
for region in regions:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status")=="minecraft:full":
            chunks[(chunk["xPos"],chunk["zPos"])]=chunk

roof_verified=[]
for name,x,z,top,roofX,roofZ in BUILDINGS:
    bx,bz=x+roofX,z+roofZ
    loc=(bx//16,bz//16)
    chunk=chunks.get(loc)
    if chunk is None:
        raise AssertionError("City rooftop chunk not persisted FULL: "+repr(loc))
    actual=get_block(chunk,bx%16,Y+top+1,bz%16)
    if actual!="minecraft:sea_lantern":
        raise AssertionError("City rooftop missing from Anvil for "+name+": "+actual)
    roof_verified.append(name)
report={
    "schema":"realscale:urban_districts_placement_proof/v1",
    "world_preset":"realscale:natural_three_realms",
    "urban_pack_enabled":True,
    "landscape_seed":42,
    "districts_placed":placed,
    "roofs_verified_in_anvil":roof_verified,
    "full_saved_chunks":len(chunks),
    "region_files":len(regions),
    "limitations":["no_natural_city_start_verified",
                   "no_slopes_water_collision_checked",
                   "no_physical_client_fps_or_DH_proof"]
}
out=ROOT/"build/urban-city-anvil-report.json"
out.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
print("REALSCALE_URBAN_CITY_ANVIL_PASS",json.dumps(report,sort_keys=True),flush=True)
