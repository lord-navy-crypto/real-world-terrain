#!/usr/bin/env python3
"""Real-world NeoForge smoke for ONE explicitly selected recovered PR25 climate.

Does not infer success from valid JSON: boot a new world, save via Minecraft
RCON, and inspect the on-disk level.dat and generated .mca chunk files.
Never touches the canonical sacred world preset or an existing save.
"""
import gzip
import os
import pathlib
import signal
import subprocess
import sys
import threading
import time
from rcon_worldgen_smoke import Rcon

FAMILIES={
    "natural":("tva_pr25_natural_three_realms","tva_pr25_natural_climate"),
    "alpine":("tva_pr25_alpine_three_realms","tva_pr25_alpine_climate"),
    "canyon":("tva_pr25_canyon_three_realms","tva_pr25_canyon_climate"),
    "measured":("tva_pr25_measured_three_realms","tva_pr25_measured_climate"),
}
if len(sys.argv)!=2 or sys.argv[1] not in FAMILIES:
    raise SystemExit("Usage: pr25_climate_world_boot.py natural|alpine|canyon|measured")
family=sys.argv[1]
preset,settings=FAMILIES[family]
root=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
world_name="pr25-climate-"+family
for name in ("run","run/server","run/gametestserver"):
    directory=root/name
    directory.mkdir(parents=True,exist_ok=True)
    (directory/"eula.txt").write_text("eula=true\n")
    (directory/"server.properties").write_text(
        "level-name="+world_name+"\n"+
        "level-type=realscale:"+preset+"\n"+
        "level-seed=785312\n"+
        "view-distance=3\n"+
        "simulation-distance=3\n"+
        "max-players=1\n"+
        "online-mode=true\n"+
        "server-ip=127.0.0.1\n"+
        "spawn-protection=0\n"+
        "max-tick-time=-1\n"+
        "enable-rcon=true\n"+
        "rcon.port=25575\n"+
        "rcon.password=realscale_ci_probe_only\n"
    )
process=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
    cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,
    bufsize=1,start_new_session=True)
lines=[]
ready=threading.Event()
def read_logs():
    for line in process.stdout:
        lines.append(line)
        if "Done (" in line and "For help" in line:
            ready.set()
thread=threading.Thread(target=read_logs,daemon=True)
thread.start()
log_path=root/"build"/("pr25-climate-"+family+"-server.log")
log_path.parent.mkdir(parents=True,exist_ok=True)
try:
    deadline=time.monotonic()+510
    while process.poll() is None and not ready.is_set() and time.monotonic()<deadline:
        time.sleep(1)
    if not ready.is_set():
        raise RuntimeError(f"PR25 {family} NeoForge server did not reach Done")
    rcon=None
    for attempt in range(30):
        try:
            rcon=Rcon(timeout=90)
            break
        except (ConnectionError,OSError,TimeoutError):
            time.sleep(1)
    if rcon is None:
        raise RuntimeError("PR25 server reached Done but RCON not accepting commands")
    try:
        ack=rcon.send("save-all flush")
        if "Saved" not in ack:
            raise AssertionError("No Minecraft world-save acknowledgment: "+ack)
        print("PR25_CLIMATE_WORLD_SAVED",family,repr(ack),flush=True)
        try:
            rcon.send("stop")
        except (EOFError,ConnectionResetError,BrokenPipeError):
            pass
    finally:
        rcon.close()
    process.wait(timeout=95)
finally:
    if process.poll() is None:
        os.killpg(process.pid,signal.SIGINT)
        try:process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGKILL)
            process.wait()
    thread.join(timeout=4)
    log_path.write_text("".join(lines),encoding="utf-8")
    print("PR25_CLIMATE_SERVER_LOG_TAIL",log_path.read_text()[-5000:],flush=True)

worlds=list((root/"run").glob("**/"+world_name+"/level.dat"))
if not worlds:raise AssertionError("PR25 world level.dat was not saved: "+world_name)
success=False
for level in worlds:
    data=gzip.decompress(level.read_bytes())
    required=(b"realscale:"+settings.encode(),b"realscale:sky_ocean",
              b"realscale:grand_nether",b"realscale:grand_end")
    if not all(name in data for name in required):
        continue
    regions=list(level.parent.glob("region/*.mca"))
    if not regions or not any(p.stat().st_size>=8192 for p in regions):
        raise AssertionError("PR25 persisted world has no generated Anvil region")
    success=True
    print("PR25_CLIMATE_SAVED_ANVIL_PASS",family,str(level),len(regions),flush=True)
if not success:raise AssertionError("PR25 world codec ID or dimension data missing")
