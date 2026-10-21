#!/usr/bin/env python3
"""Verified far-chunk NeoForge probe: command ACK != generated/saved chunk.

Uses AUTHENTICATED Minecraft RCON, never Gradle stdin (which did not forward
console commands in the previous USGS V2 CI). Generates exactly ONE distant
chunk ticket to avoid resource fanout on 2096-height terrain. Saves profiling,
diagnostics and real Anvil regions. Timeout is bounded, never reported as pass.
"""
import gzip
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import threading
import time

from rcon_worldgen_smoke import Rcon, await_full_chunk

WORLD="usgs-fitted-v2-world"
PROFILE="realscale:usgs_fitted_continuation_v2"
X,Z=2048,0

def java_thread_dump(path):
    try:
        listing=subprocess.run(["jcmd","-l"],capture_output=True,text=True,timeout=12)
        matches=[]
        for line in listing.stdout.splitlines():
            if "cpw.mods.modlauncher.Launcher" in line or "net.neoforged" in line:
                matches.append(int(line.split()[0]))
        out=[]
        for pid in matches[:3]:
            p=subprocess.run(["jcmd",str(pid),"Thread.print","-l"],
                 capture_output=True,text=True,timeout=15)
            out.append("PID "+str(pid)+"\n"+p.stdout+"\n"+p.stderr)
        path.write_text("\n".join(out) if out else "No live Minecraft Java PID in jcmd -l\n"+listing.stdout)
    except Exception as exc:
        path.write_text("Java stack dump unavailable: "+repr(exc))

def region_state(mod):
    files=list((mod/"run").glob("**/"+WORLD+"/region/r.*.*.mca"))
    chunks=0;far=0
    for file in files:
        if file.stat().st_size<4096:continue
        with file.open("rb") as stream: header=stream.read(4096)
        n=sum(any(header[i:i+4]) for i in range(0,len(header),4))
        chunks+=n
        if file.name=="r.4.0.mca":far+=n
    return {"anvil_files":len(files),"all_chunks":chunks,"far_region_chunks":far}

def main():
    mod=Path.cwd()
    logs=mod/"build"
    logs.mkdir(exist_ok=True)
    profile_file=logs/"usgs-fitted-v2-perf-report.json"
    log_file=logs/"usgs-fitted-v2-world.log"
    dump_file=logs/"usgs-fitted-v2-thread-dump.txt"
    started=time.monotonic()
    # Server is invoked via Gradle, but all actual game commands use RCON.
    p=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
         stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,
         stdin=subprocess.DEVNULL,start_new_session=True)
    output=[];ready=threading.Event()
    def drain():
        for line in p.stdout:
            output.append(line)
            if "Done (" in line and "For help" in line:ready.set()
    t=threading.Thread(target=drain,daemon=True)
    t.start()
    report={"profile":PROFILE,"target_block":[X,Z],"world_height":2096,
            "rcon_authenticated":False,"fully_loaded":False,
            "far_region_saved":False,"server_exit_clean":False}
    remote=None;error=None
    try:
        deadline=time.monotonic()+420
        while not ready.is_set() and p.poll() is None and time.monotonic()<deadline:
            time.sleep(0.5)
        if not ready.is_set():
            raise RuntimeError("Server never completed initial world generation")
        report["server_ready_seconds"]=round(time.monotonic()-started,3)
        remote=Rcon(password="realscale_fitted_v2_ci_only",timeout=100)
        report["rcon_authenticated"]=True
        # Explicit ACK; a queued force-load ticket alone does not pass QA.
        t0=time.monotonic()
        remote.send(f"execute in minecraft:overworld run forceload add {X} {Z}")
        report["ticket_ack_seconds"]=round(time.monotonic()-t0,3)
        # May require adjacent chunks for structure generation; do not
        # add more asynchronous tickets and overload 2096-height world.
        t0=time.monotonic()
        await_full_chunk(remote,X,Z,timeout_seconds=285)
        report["fully_loaded"]=True
        report["full_chunk_seconds"]=round(time.monotonic()-t0,3)
        t0=time.monotonic()
        saved=remote.send("save-all flush")
        if "Saved" not in saved:
            raise AssertionError("save-all flush had no successful ACK: "+saved)
        report["save_all_seconds"]=round(time.monotonic()-t0,3)
        before=region_state(mod)
        report.update(before)
        report["far_region_saved"]=before["far_region_chunks"]>0
        if not report["far_region_saved"]:
            raise AssertionError("No physical Anvil region r.4.0.mca for the far chunk")
        levels=list((mod/"run").glob("**/"+WORLD+"/level.dat"))
        if not levels:raise AssertionError("World level.dat was not saved")
        if PROFILE.encode() not in gzip.decompress(levels[0].read_bytes()):
            raise AssertionError("World does not persist V2 fitted terrain profile")
        print("FAR_CHUNK_ANVIL_SAVED: PASS",report,flush=True)
        remote.send(f"execute in minecraft:overworld run forceload remove {X} {Z}")
        try:remote.send("stop")
        except (ConnectionError,OSError,EOFError):pass
        p.wait(timeout=120)
        report["server_exit_clean"]=p.returncode==0
    except BaseException as exc:
        error=exc
        report["error"]=repr(exc)
        print("USGS_FITTED_V2_PROBE_FAILURE",repr(exc),flush=True)
        java_thread_dump(dump_file)
    finally:
        if remote is not None:
            try:remote.close()
            except OSError:pass
        if p.poll() is None:
            os.killpg(p.pid,signal.SIGINT)
            try:p.wait(timeout=14)
            except subprocess.TimeoutExpired:
                os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=9)
        t.join(timeout=6)
        log_file.write_text("".join(output),encoding="utf-8")
        report["total_seconds"]=round(time.monotonic()-started,3)
        report["server_log_tail"]=log_file.read_text(encoding="utf-8")[-2200:]
        profile_file.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
        print("USGS_FITTED_V2_REPORT",json.dumps(report,indent=2),flush=True)
    if error:raise SystemExit("Real far-chunk verification failed: "+repr(error))
    if not report["server_exit_clean"]:raise SystemExit("Server shutdown was not clean")
    print("USGS_FITTED_V2_REAL_SERVER_PASS",flush=True)

if __name__=="__main__":main()
