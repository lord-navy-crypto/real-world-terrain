#!/usr/bin/env python3
"""Opt-in Geo Wonders real NeoForge boot and persisted level.dat codec proof."""
import gzip
import pathlib
import signal
import subprocess
import threading
import time

ROOT=pathlib.Path(__file__).resolve().parents[2]/"neoforge"
PRESET="realscale:tva_natural_wonders_experimental_three_realms"
for folder in ("run","run/server","run/gametestserver"):
    directory=ROOT/folder
    directory.mkdir(parents=True,exist_ok=True)
    (directory/"eula.txt").write_text("eula=true\n")
    (directory/"server.properties").write_text(
        "level-name=geo-wonders-lab\n"
        "level-type="+PRESET+"\n"
        "level-seed=785312\n"
        "view-distance=3\n"
        "simulation-distance=3\n"
        "spawn-protection=0\n"
        "max-tick-time=-1\n"
        "online-mode=true\n"
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
    time.sleep(12)
    p.stdin.write("stop\n")
    p.stdin.flush()
    p.wait(timeout=75)
finally:
    if p.poll() is None:
        try:
            p.stdin.write("stop\n")
            p.stdin.flush()
            p.wait(timeout=20)
        except (OSError,subprocess.TimeoutExpired):
            if p.poll() is None:
                import os
                os.killpg(p.pid,signal.SIGINT)
                try:p.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(p.pid,signal.SIGKILL)
                    p.wait()
    t.join(timeout=4)
    dest=ROOT/"build/geo-wonders-optin-server.log"
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text("".join(log))
    print("GEO_WONDERS_LOG_TAIL:",dest.read_text()[-5000:],flush=True)
worlds=list((ROOT/"run").glob("**/geo-wonders-lab/level.dat"))
if not worlds: raise AssertionError("No persisted Geo Wonders world")
text=gzip.decompress(worlds[0].read_bytes())
for expected in (b"realscale:geo_wonders_opt_in",b"realscale:sky_ocean",
                 b"realscale:grand_nether",b"realscale:grand_end"):
    if expected not in text:raise AssertionError("Experimental source missing from persisted world: "+expected.decode())
print("PASS: experimental geological world preset decoded, booted and persisted",worlds[0])
