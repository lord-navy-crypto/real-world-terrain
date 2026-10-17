#!/usr/bin/env python3
"""Opt-in Geo Wonders real NeoForge boot and persisted level.dat codec proof."""
import gzip
import pathlib
import signal
import os
from rcon_worldgen_smoke import Rcon
import subprocess
import threading
import time

ROOT=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
# Both opt-ins must pass actual server codec, RCON save and Anvil checks.
# A dedicated world directory prevents mixed evidence between matrix jobs.
PROFILE=os.environ.get("REALSCALE_WONDERS_PROFILE","natural-wonders")
PRESETS={
    "natural-wonders":("tva_natural_wonders_experimental_three_realms","geo-wonders-lab"),
    "woven-wonders-invasion":("tva_wonders_invasion_three_realms","tva-wonders-invasion-lab"),
}
if PROFILE not in PRESETS:
    raise SystemExit("Unknown opt-in world QA profile: "+PROFILE)
preset_name,world_name=PRESETS[PROFILE]
PRESET="realscale:"+preset_name
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
    if not ready.is_set(): raise RuntimeError("Experimental Geo Wonders server boot failed")
    # Gradle runServer does not reliably forward stdin to the actual server.
    # Save via authenticated Minecraft RCON, then request a clean shutdown.
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
        try:p.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGKILL)
            p.wait()
    t.join(timeout=4)
    dest=ROOT/"build"/("geo-wonders-optin-"+PROFILE+"-server.log")
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text("".join(log))
    print("GEO_WONDERS_LOG_TAIL:",dest.read_text()[-5000:],flush=True)
worlds=list((ROOT/"run").glob("**/"+world_name+"/level.dat"))
if not worlds: raise AssertionError("No persisted Geo Wonders world")
text=gzip.decompress(worlds[0].read_bytes())
required=[b"realscale:geo_wonders_opt_in",b"realscale:sky_ocean",
          b"realscale:grand_nether",b"realscale:grand_end"]
if PROFILE=="woven-wonders-invasion":
    required.append(b"realscale:cross_realm_invasion")
for expected in required:
    if expected not in text:
        raise AssertionError("Experimental source missing from persisted world: "+expected.decode())
regions=list(worlds[0].parent.glob("region/*.mca"))
if not regions or not any(p.stat().st_size>=8192 for p in regions):
    raise AssertionError("Experimental world has no persisted real Anvil chunks")
print("PASS: opt-in world persisted and source codecs verified",
      PROFILE,worlds[0],len(regions),flush=True)
