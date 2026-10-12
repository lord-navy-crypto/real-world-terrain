#!/usr/bin/env python3
"""Run actual NeoForge server and request distant chunks over authenticated RCON.

Unlike piping commands to the Gradle runServer process, RCON delivers commands
to Minecraft's dedicated-server command dispatcher and returns acknowledgments.
Save-all flush and actual saved Anvil block inspection remain separate gates.
"""
import gzip
import json
import os
import re
import pathlib
import signal
import socket
import struct
import subprocess
import sys
import threading
import time

PORT=25575
PASSWORD="realscale_ci_probe_only"
HEADER=struct.Struct("<iii")

class Rcon:
    def __init__(self, host="127.0.0.1", port=PORT, password=PASSWORD):
        self.sock=socket.create_connection((host,port),timeout=12)
        self.sock.settimeout(25)
        self.sequence=12400
        self._command(3,password,authenticate=True)

    def _packet(self,request_type,body):
        self.sequence+=1
        req=self.sequence
        payload=struct.pack("<ii",req,request_type)+body.encode("utf-8")+b"\x00\x00"
        self.sock.sendall(struct.pack("<i",len(payload))+payload)
        return req

    def _receive(self):
        raw=self._read_exact(4)
        length=struct.unpack("<i",raw)[0]
        if not 10<=length<=100000:
            raise RuntimeError("Invalid RCON packet length "+str(length))
        content=self._read_exact(length)
        req,typ=struct.unpack_from("<ii",content,0)
        return req,typ,content[8:-2].decode("utf-8",errors="replace")

    def _read_exact(self,size):
        parts=[]
        remaining=size
        while remaining>0:
            buf=self.sock.recv(remaining)
            if not buf:raise EOFError("Server closed RCON socket")
            parts.append(buf)
            remaining-=len(buf)
        return b"".join(parts)

    def _command(self,kind,body,authenticate=False):
        identifier=self._packet(kind,body)
        for _ in range(5):
            req,typ,response=self._receive()
            if req==-1:raise RuntimeError("RCON authentication denied")
            if req!=identifier:continue
            if authenticate:
                if typ!=2:continue
            return response
        raise RuntimeError("Missing RCON response to "+body[:64])

    def send(self,command):
        reply=self._command(2,command)
        print("RCON:",command,"=>",reply[:260],flush=True)
        if any(t in reply.lower() for t in ("unknown command","incorrect argument","no chunks were marked","commands.generic.exception","not a valid")):
            raise RuntimeError("RCON command rejected: "+command+": "+reply)
        return reply

    def close(self):
        self.sock.close()


def main(root,expected_preset,profile):
    log=root/"build"/("rcon_"+profile+"_server.log")
    log.parent.mkdir(parents=True,exist_ok=True)
    proc=subprocess.Popen(["gradle","--no-daemon","runServer","--stacktrace"],
        cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
        text=True,bufsize=1,start_new_session=True)
    lines=[]
    ready=threading.Event()
    def drain():
        for line in proc.stdout:
            lines.append(line)
            if "Done (" in line and "For help" in line:
                ready.set()
    t=threading.Thread(target=drain,daemon=True)
    t.start()
    failed=None
    try:
        deadline=time.monotonic()+570
        while not ready.is_set() and proc.poll() is None and time.monotonic()<deadline:
            time.sleep(1)
        if not ready.is_set():
            raise RuntimeError("Server failed to reach Done; return code "+str(proc.poll()))
        rcon=None
        for attempt in range(12):
            try:
                rcon=Rcon()
                break
            except (ConnectionError,OSError,TimeoutError):
                time.sleep(1)
        if rcon is None:raise RuntimeError("Cannot authenticate to enabled RCON")
        try:
            if profile=="usgs":
                commands=[
                    "execute in minecraft:overworld run forceload add -32 -32 32 32",
                    "execute in minecraft:overworld run forceload add 224 -16 256 16",
                    "execute in minecraft:overworld run forceload add -256 -16 -224 16",
                    "execute in minecraft:overworld run forceload add -16 224 16 256",
                    "execute in minecraft:overworld run forceload add -16 -256 16 -224",
                    "execute in minecraft:overworld run forceload add 384 -16 416 16",
                    "execute in minecraft:overworld run forceload add -416 -16 -384 16",
                    "execute in minecraft:overworld run forceload add 1016 0",
                    "execute in minecraft:overworld run forceload add -1016 0"
                ]
            elif profile=="distant":
                commands=[
                    "execute in minecraft:overworld run forceload add 1024 1024",
                    "execute in minecraft:overworld run forceload add -1024 -1024"
                ]
            elif profile=="structures":
                # Locate chooses natural structure candidates; forceload will
                # then generate real Anvil chunks around returned coordinates.
                # The separate Anvil auditor decides whether starts were saved.
                rcon.sock.settimeout(180)
                queries=["minecraft:trial_chambers",
                         "minecraft:ruined_portal",
                         "minecraft:village_taiga"]
                observations=[]
                for target in queries:
                    reply=rcon.send("execute in minecraft:overworld run locate structure "+target)
                    hit=re.search(r"\[\s*(-?\d+)\s*,\s*(?:~|-?\d+)\s*,\s*(-?\d+)\s*\]",reply)
                    if not hit:
                        observations.append({"structure":target,"located":False,"response":reply})
                        continue
                    x,z=map(int,hit.groups())
                    observations.append({"structure":target,"located":True,"x":x,"z":z})
                    rcon.send(f"execute in minecraft:overworld run forceload add {x-32} {z-32} {x+32} {z+32}")
                (root/"vanilla_structure_locate_qa.json").write_text(json.dumps(observations,indent=2))
                if not any(x["located"] for x in observations):
                    raise RuntimeError("NO structures were located; cannot validate vanilla structure starts")
                commands=[]
            elif profile=="fantasy":
                commands=[
                    "execute in minecraft:the_nether run forceload add 0 0",
                    "execute in minecraft:the_nether run forceload add 512 0",
                    "execute in minecraft:the_end run forceload add 0 0",
                    "execute in minecraft:the_end run forceload add 2048 0",
                    "execute in minecraft:the_end run forceload add 3072 0",
                    "execute in minecraft:the_end run forceload add 4096 0",
                    "execute in minecraft:the_end run forceload add 0 3072"
                ]
            else:
                raise ValueError("Unknown QA profile "+profile)
            for command in commands:
                rcon.send(command)
            time.sleep(120 if profile=="structures" else (100 if profile=="fantasy" else (75 if profile=="usgs" else 35)))
            rcon.send("save-all flush")
            time.sleep(5)
            try:
                rcon.send("stop")
            except (EOFError, ConnectionResetError, BrokenPipeError):
                print("RCON stop closed connection as expected",flush=True)
            proc.wait(timeout=125)
        finally:
            rcon.close()
    except Exception as exc:
        failed=exc
        print("RCON WORLDGEN FAILURE:",repr(exc),flush=True)
    finally:
        if proc.poll() is None:
            try:
                os.killpg(proc.pid,signal.SIGINT)
                proc.wait(timeout=15)
            except (OSError,subprocess.TimeoutExpired):
                if proc.poll() is None:
                    os.killpg(proc.pid,signal.SIGKILL)
                    proc.wait()
        t.join(timeout=4)
        log.write_text("".join(lines),encoding="utf-8")
        print("SERVER LOG:",log,flush=True)
        print("SERVER LOG TAIL:",log.read_text(encoding="utf-8")[-4500:],flush=True)
    if failed is not None:raise failed
    if not ready.is_set():raise RuntimeError("Server did not boot")
    if profile=="fantasy":
        nether=list(root.glob("run/**/DIM-1/region/*.mca"))
        end=list(root.glob("run/**/DIM1/region/*.mca"))
        print("NETHER_PERSISTED_REGION_FILES:",list(map(str,nether)))
        print("END_PERSISTED_REGION_FILES:",list(map(str,end)))
        if not nether or not end:
            raise RuntimeError("Fantasy Nether/End still has no persisted Anvil regions")

    levels=list(root.glob("run/**/level.dat"))
    matched=False
    from realscale_headless_qa import read_nbt
    for path in levels:
        nbt=read_nbt(gzip.decompress(path.read_bytes()))
        data=nbt.get("Data",nbt)
        dimensions=data.get("WorldGenSettings",{}).get("dimensions",{})
        overworld=dimensions.get("minecraft:overworld",{})
        actual=overworld.get("generator",{}).get("settings")
        if actual!=expected_preset:
            print("Worldgen does not match requested preset:",path,actual,flush=True)
            continue
        if profile=="fantasy":
            checks={
                "minecraft:the_nether":"realscale:fantasy_nether_v2",
                "minecraft:the_end":"realscale:fantasy_end_v2",
            }
            for dimension,expected in checks.items():
                actual=dimensions.get(dimension,{}).get("generator",{}).get("settings")
                if actual!=expected:
                    raise RuntimeError(f"Fantasy dimension {dimension} did not load correct custom noise settings: {actual!r} != {expected!r}")
            nether_type=dimensions.get("minecraft:the_nether",{}).get("type")
            if nether_type!="realscale:fantasy_nether_v1":
                raise RuntimeError("Expanded Nether dimension type missing: "+str(nether_type))
            print("Saved NBT confirms both fantasy generation settings",flush=True)
        matched=True
        print("Correct worldgen preset in saved NBT:",path,flush=True)
    if not matched:raise RuntimeError("Saved level.dat does not use "+expected_preset)
    print("NEOFORGE_RCON_WORLDGEN_BOOT_AND_COMMANDS: PASS",flush=True)


if __name__=="__main__":
    if len(sys.argv)!=4:
        raise SystemExit("usage: rcon_worldgen_smoke.py neoForgeRoot preset usgs|distant|fantasy|structures")
    main(pathlib.Path(sys.argv[1]).resolve(),sys.argv[2],sys.argv[3])
