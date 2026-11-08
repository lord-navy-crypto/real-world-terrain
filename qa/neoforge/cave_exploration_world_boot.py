#!/usr/bin/env python3
"""Actual NeoForge boot + persisted Anvil evidence for opt-in cave topology.

This checks real codec loading, an RCON-forced FULL chunk, three realm IDs,
and world save—not just a Python or Java geometry result. It does not assert
that a specific cave is accessible at the sampled surface coordinate.
"""
from __future__ import annotations
import gzip
import json
import os
import pathlib
import signal
import subprocess
import threading
import time

from rcon_worldgen_smoke import Rcon, await_full_chunk
from realscale_headless_qa import iter_region_chunks

ROOT=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
WORLD="cave-exploration-v2"
PRESET="realscale:cave_exploration_three_realms"
TARGET_X,TARGET_Z=384,384

for folder in ("run","run/server","run/gametestserver"):
    p=ROOT/folder
    p.mkdir(parents=True,exist_ok=True)
    (p/"eula.txt").write_text("eula=true\n")
    (p/"server.properties").write_text(
        f"level-name={WORLD}\n"
        f"level-type={PRESET}\n"
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
        "rcon.password=realscale_ci_probe_only\n"
    )

p=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
                   cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
                   stderr=subprocess.STDOUT,text=True,bufsize=1,
                   start_new_session=True)
log=[]
ready=threading.Event()
def read_lines():
    for line in p.stdout:
        log.append(line)
        if "Done (" in line and "For help" in line:
            ready.set()
reader=threading.Thread(target=read_lines,daemon=True)
reader.start()
error=None
try:
    deadline=time.monotonic()+540
    while time.monotonic()<deadline and p.poll() is None and not ready.is_set():
        time.sleep(1)
    if not ready.is_set():
        raise RuntimeError("Cave Exploration NeoForge did not finish startup")
    rcon=None
    for attempt in range(20):
        try:
            rcon=Rcon(timeout=260)
            break
        except (OSError,ConnectionError,TimeoutError):
            time.sleep(1)
    if rcon is None:
        raise RuntimeError("Cave Exploration local server RCON unavailable")
    try:
        reply=rcon.send(f"forceload add {TARGET_X} {TARGET_Z}")
        print("CAVE_V2_FORCELOAD_ACK",repr(reply),flush=True)
        await_full_chunk(rcon,TARGET_X,TARGET_Z,timeout_seconds=230)
        saved=rcon.send("save-all flush")
        print("CAVE_V2_SAVE_ACK",repr(saved),flush=True)
        if "Saved" not in saved and "saved" not in saved:
            raise RuntimeError("Minecraft did not acknowledge saved chunks")
        try:rcon.send("stop")
        except (EOFError,ConnectionResetError,BrokenPipeError):
            pass
    finally:rcon.close()
    try:p.wait(timeout=95)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Cave Exploration server did not stop after save")
except BaseException as exc:
    error=repr(exc)
finally:
    if p.poll() is None:
        os.killpg(p.pid,signal.SIGINT)
        try:p.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGKILL)
            p.wait()
    reader.join(timeout=4)
    logfile=ROOT/"build/cave-exploration-v2-server.log"
    logfile.parent.mkdir(exist_ok=True,parents=True)
    logfile.write_text("".join(log),encoding="utf-8")
    print("CAVE_V2_SERVER_READY",ready.is_set(),"TAIL:",logfile.read_text()[-9000:],flush=True)
if error is not None:
    raise RuntimeError(error)

worlds=list((ROOT/"run").glob("**/"+WORLD+"/level.dat"))
if not worlds:
    raise RuntimeError("Cave Exploration world has no saved level.dat")
saved_world=worlds[0].parent
raw=gzip.decompress(worlds[0].read_bytes())
for expected in (b"realscale:tva_cave_exploration",
                 b"realscale:grand_nether",b"realscale:grand_end"):
    if expected not in raw:
        raise AssertionError("Persisted level.dat lacks generator ID "+expected.decode())

target_chunk=(TARGET_X//16,TARGET_Z//16)
found=False
full_count=0
regions=list((saved_world/"region").glob("r.*.*.mca"))
for region in regions:
    for chunk in iter_region_chunks(region):
        if chunk.get("Status")!="minecraft:full":
            continue
        full_count+=1
        if (chunk.get("xPos"),chunk.get("zPos"))==target_chunk:
            found=True
if not found:
    raise RuntimeError("RCON target never became a persisted FULL Anvil chunk: "+repr(target_chunk))
report={
    "schema":"realscale:cave_exploration_server_proof/v1",
    "preset":PRESET,
    "noise_settings":"realscale:tva_cave_exploration",
    "seed":42,
    "target_full_chunk":list(target_chunk),
    "full_saved_chunks":full_count,
    "region_files":len(regions),
    "verified_three_realm_generator_ids":True,
    "proof":"real_Anvil_full_chunk_and_saved_level_dat_not_visual_cave_tour",
}
output=ROOT/"build/cave-exploration-v2-anvil-report.json"
output.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
print("REALSCALE_CAVE_EXPLORATION_ANVIL_PASS",json.dumps(report,sort_keys=True),flush=True)
