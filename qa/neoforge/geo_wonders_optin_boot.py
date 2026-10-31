#!/usr/bin/env python3
"""Opt-in Geo Wonders real NeoForge boot and persisted smart-spawn proof."""
import gzip
import os
import pathlib
import re
import signal
import subprocess
import threading
import time

from rcon_worldgen_smoke import Rcon
from realscale_headless_qa import read_nbt

ROOT=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
PROFILE=os.environ.get("REALSCALE_WONDERS_PROFILE","natural-wonders")
if PROFILE != "natural-wonders":
    raise SystemExit("Only the independent natural-wonders profile is supported")
world_name="geo-wonders-lab"
PRESET="realscale:tva_natural_wonders_experimental_three_realms"
for folder in ("run","run/server","run/gametestserver"):
    directory=ROOT/folder
    directory.mkdir(parents=True,exist_ok=True)
    (directory/"eula.txt").write_text("eula=true\n")
    (directory/"server.properties").write_text(
        "level-name="+world_name+"\n"
        "level-type="+PRESET+"\n"
        "level-seed=785312\n"
        "view-distance=3\n"
        "simulation-distance=3\n"
        "spawn-protection=0\n"
        "max-tick-time=-1\n"
        "online-mode=true\n"
        "enable-rcon=true\n"
        "rcon.password=realscale_ci_probe_only\n"
        "rcon.port=25575\n"
    )
p=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
    cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
    text=True,bufsize=1,start_new_session=True)
log=[]
ready=threading.Event()
def drain():
    for line in p.stdout:
        log.append(line)
        if "Done (" in line and "For help" in line:
            ready.set()
t=threading.Thread(target=drain,daemon=True)
t.start()
try:
    deadline=time.monotonic()+490
    while time.monotonic()<deadline and not ready.is_set() and p.poll() is None:
        time.sleep(1)
    if not ready.is_set():
        raise RuntimeError("Experimental Geo Wonders server boot failed")
    rcon=Rcon(timeout=90)
    try:
        result=rcon.send("save-all flush")
        if "Saved" not in result:
            raise AssertionError("Minecraft did not acknowledge the world save: "+result)
        print("GEO_WONDERS_RCON_SAVE_ACK",repr(result),flush=True)
        try:
            rcon.send("stop")
        except (EOFError,ConnectionResetError,BrokenPipeError):
            print("GEO_WONDERS_RCON_STOP_DISCONNECTED",flush=True)
    finally:
        rcon.close()
    p.wait(timeout=100)
finally:
    if p.poll() is None:
        os.killpg(p.pid,signal.SIGINT)
        try:
            p.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGKILL)
            p.wait()
    t.join(timeout=4)
    dest=ROOT/"build"/("geo-wonders-optin-"+PROFILE+"-server.log")
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text("".join(log))
    print("GEO_WONDERS_LOG_TAIL:",dest.read_text()[-5000:],flush=True)

worlds=list((ROOT/"run").glob("**/"+world_name+"/level.dat"))
if not worlds:
    raise AssertionError("No persisted Geo Wonders world")
level_bytes=gzip.decompress(worlds[0].read_bytes())
level_data=read_nbt(level_bytes).get("Data",{})
spawn_x=int(level_data.get("SpawnX",0))
spawn_y=int(level_data.get("SpawnY",-9999))
spawn_z=int(level_data.get("SpawnZ",0))
log_text="".join(log)
spawn_match=re.search(
    r"RealScale selected initial spawn x=(-?\d+) y=(-?\d+) z=(-?\d+) targetY=(-?\d+) relief=(\d+)",
    log_text,
)
if not spawn_match:
    raise AssertionError("NeoForge did not report selecting a RealScale initial spawn")
logged_x,logged_y,logged_z,target_y,relief=map(int,spawn_match.groups())
if (spawn_x,spawn_y,spawn_z)!=(logged_x,logged_y,logged_z):
    raise AssertionError("Persisted spawn differs from the RealScale-selected position")
if not (-62<spawn_y<2031):
    raise AssertionError("RealScale initial spawn is outside generator height bounds: "+str((spawn_x,spawn_y,spawn_z)))
if spawn_x*spawn_x+spawn_z*spawn_z>2048*2048+64:
    raise AssertionError("RealScale initial spawn is outside the bounded Natural search radius: "+str((spawn_x,spawn_y,spawn_z)))
if abs(spawn_y-target_y)>256:
    raise AssertionError("Selected spawn is too far from the preset's low-to-mid target quantile")
if relief>128:
    raise AssertionError("Selected spawn is on an excessively steep local slope: "+str(relief))
print("REALSCALE_SMART_SPAWN_PERSISTED",spawn_x,spawn_y,spawn_z,
      "target",target_y,"relief",relief,flush=True)

required=[b"realscale:geo_wonders_opt_in",b"realscale:sky_ocean",
          b"realscale:grand_nether",b"realscale:grand_end"]
for expected in required:
    if expected not in level_bytes:
        raise AssertionError("Experimental source missing from persisted world: "+expected.decode())
regions=list(worlds[0].parent.glob("region/*.mca"))
if not regions or not any(path.stat().st_size>=8192 for path in regions):
    raise AssertionError("Experimental world has no persisted real Anvil chunks")
print("PASS: opt-in world, smart spawn and source codecs verified",
      PROFILE,worlds[0],len(regions),flush=True)
