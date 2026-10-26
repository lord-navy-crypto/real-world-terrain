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

# Three virgin far regions with separate chunk neighborhoods, including
# negative coordinates. No preloading/ticket batching between waypoints.
FAR_ROUTE_SITES=((1024,1024),(-2048,1536),(4096,-2048))

class Rcon:
    def __init__(self, host="127.0.0.1", port=PORT, password=PASSWORD, timeout=25):
        self.sock=socket.create_connection((host,port),timeout=12)
        self.sock.settimeout(timeout)
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
        start=time.monotonic()
        try:
            reply=self._command(2,command)
        except TimeoutError as exc:
            raise TimeoutError(f"RCON timed out after {time.monotonic()-start:.1f}s while executing {command}") from exc
        print("RCON:",command,"=>",reply[:260],f"elapsed={time.monotonic()-start:.1f}s",flush=True)
        if any(t in reply.lower() for t in ("unknown command","incorrect argument","no chunks were marked","commands.generic.exception","not a valid")):
            raise RuntimeError("RCON command rejected: "+command+": "+reply)
        return reply

    def close(self):
        self.sock.close()


def await_full_chunk(rcon,x,z,timeout_seconds=240):
    """After forceload, distinguish command ACK from actual generated chunk."""
    deadline=time.monotonic()+timeout_seconds
    # /seed always returns an explicit success result, unlike /say
    # whose RCON reply often omits the spoken message itself.
    command=(f"execute in minecraft:overworld if loaded {x} 128 {z} "
             "run seed")
    attempts=0
    # The deadline must also cap each SOCKET receive. Otherwise the first
    # check after a 170s forceload ACK may wait an ADDITIONAL 180 seconds,
    # silently defeating the strict overall 180-second distant-world gate.
    sock=getattr(rcon,"sock",None)
    previous_timeout=sock.gettimeout() if sock is not None else None
    try:
        while True:
            remaining=deadline-time.monotonic()
            if remaining<=0:
                break
            if sock is not None:
                sock.settimeout(min(previous_timeout,remaining)
                    if previous_timeout is not None else remaining)
            attempts+=1
            reply=rcon.send(command)
            if "Seed:" in reply:
                print("QA_CHUNK_GENERATED:",x,z,"attempts",attempts,
                      flush=True)
                return
            time.sleep(min(3.0,max(0.0,deadline-time.monotonic())))
    finally:
        if sock is not None:
            sock.settimeout(previous_timeout)
    raise TimeoutError(
        f"Chunk at {x},{z} not fully loaded within {timeout_seconds:.1f}s "
        "despite forceload ACK; inspect RCON and Minecraft server logs")


def structure_targets(expected_preset):
    """Keep all three vanilla structure probes; serialize worldgen per hit.

    The old test marked 25 far-away chunks per located target without
    waiting for any to finish, overwhelming the 2096-height server before
    the third (village) locate. Coverage must not silently disappear.
    """
    return ("minecraft:trial_chambers", "minecraft:ruined_portal",
            "minecraft:village_taiga")


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
                rcon=Rcon(timeout=180 if profile in ("usgs","distant","distant_route","structures") else 75)
                break
            except (ConnectionError,OSError,TimeoutError):
                time.sleep(1)

        if rcon is None:raise RuntimeError("Cannot authenticate to enabled RCON")
        jfr_pid=None
        if os.getenv("REALSCALE_JFR_DIAGNOSTIC")=="1":
            listing=subprocess.run(["jcmd","-l"],capture_output=True,text=True,timeout=15,check=True)
            candidates=[]
            for line in listing.stdout.splitlines():
                if not any(name in line.lower() for name in ("net.neoforged","minecraft","bootstraplauncher")):
                    continue
                pid=line.split()[0]
                if pid.isdigit():
                    candidates.append(pid)
            if len(candidates)!=1:
                raise RuntimeError("JFR profile requires exactly one active game server, got "+
                                   str(candidates)+": "+listing.stdout[-2400:])
            jfr_pid=candidates[0]
            cmd=["jcmd",jfr_pid,"JFR.start","name=realscale_worldgen",
                 "settings=profile","disk=true","maxsize=128m"]
            start_result=subprocess.run(cmd,capture_output=True,text=True,
                                        timeout=30,check=True)
            print("REALSCALE_JFR_STARTED:",jfr_pid,start_result.stdout.strip(),flush=True)
        try:
            if profile=="usgs":
                # Previous QA requested 25 + 9*9 chunks at once in a
                # 2096-block world. That overloaded CI and stalled RCON.
                # One-chunk samples straddle EACH measured tile edge;
                # include transition and the fully procedural fallback
                # beyond v2's 2048-block exterior blending radius.
                sites=[
                    (-8,-8),(8,8),         # DEM measured interior
                    (-245,8),(-235,8),      # west edge (x=-240)
                    (233,8),(245,8),       # east edge (x=+240)
                    (8,-245),(8,-235),     # north edge (z=-240)
                    (8,233),(8,245),       # south edge (z=+240)
                    (-896,8),(896,8),      # west/east mid-transition
                    (8,-896),(8,896),      # north/south mid-transition
                    (-2352,8),(2352,8),   # west/east exterior fallback
                    (8,-2352),(8,2352)    # north/south exterior fallback
                ]
                commands=[
                    f"execute in minecraft:overworld run forceload add {x} {z}"
                    for x,z in sites
                ]
            elif profile=="distant":
                # Spawn already loads many chunks. Avoid starting two
                # expensive remote regions concurrently in high-relief maps.
                commands=["execute in minecraft:overworld run forceload add 1024 1024"]
            elif profile=="distant_route":
                commands=[]  # verified sequentially below, never enqueued as a batch
            elif profile=="packs":
                # A real NeoForge server must load ALL THREE optional datapacks.
                # Static JSON/ZIP tests alone cannot establish registry or
                # Feature codec compatibility.
                reply=rcon.send("datapack list enabled")
                expected_pack_names=(
                    "RealScale-GeoEcology-Winter-EXPERIMENTAL.zip",
                    "RealScale-Vanilla-Habitat-Structures-EXPERIMENTAL.zip",
                    "RealScale-Vanilla-Structures-2x-EXPERIMENTAL.zip",
                )
                missing=[p for p in expected_pack_names if p not in reply]
                if missing:
                    raise RuntimeError(
                        "NeoForge did not enable experimental data packs: "+str(missing)
                        +" in "+reply
                    )
                print("NEOFORGE_LOADED_WINTER_HABITAT_AND_STRUCTURE_PACKS: PASS",flush=True)
                commands=["execute in minecraft:overworld run forceload add 64 64"]
            elif profile=="structures":
                # Locate chooses natural structure candidates; forceload will
                # then generate real Anvil chunks around returned coordinates.
                # The separate Anvil auditor decides whether starts were saved.
                rcon.sock.settimeout(180)
                queries=structure_targets(expected_preset)
                observations=[]
                # Persist intermediate candidates even if a later expensive
                # /locate or chunk generation times out.
                evidence_path=root/"vanilla_structure_locate_qa.json"
                for target in queries:
                    reply=rcon.send("execute in minecraft:overworld run locate structure "+target)
                    hit=re.search(r"\[\s*(-?\d+)\s*,\s*(?:~|-?\d+)\s*,\s*(-?\d+)\s*\]",reply)
                    if not hit:
                        observations.append({"structure":target,"located":False,"response":reply})
                        evidence_path.write_text(json.dumps(observations,indent=2))
                        continue
                    x,z=map(int,hit.groups())
                    observations.append({"structure":target,"located":True,"x":x,"z":z})
                    evidence_path.write_text(json.dumps(observations,indent=2))
                    # Load ONE candidate-center chunk, not 25 high chunks.
                    # Anvil QA still requires a real, FULL structure start.
                    rcon.send(f"execute in minecraft:overworld run forceload add {x} {z}")
                    await_full_chunk(rcon,x,z,timeout_seconds=240)
                    rcon.send("save-all flush")
                    rcon.send(f"execute in minecraft:overworld run forceload remove {x} {z}")
                evidence_path.write_text(json.dumps(observations,indent=2))
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
            if profile=="usgs":
                # The RCON 'Marked chunk' reply only confirms a ticket,
                # NOT that a 2096-high chunk has finished worldgen.
                # Sequential full-chunk checks prevent many far-apart
                # USGS regions from saturating the server simultaneously.
                for command,(x,z) in zip(commands,sites):
                    rcon.send(command)
                    await_full_chunk(rcon,x,z)
                    rcon.send("save-all flush")
                    rcon.send(f"execute in minecraft:overworld run forceload remove {x} {z}")
            elif profile=="distant":
                # A successful /forceload response means "ticket added",
                # NOT "2096-high target chunk is finished." This false green
                # was masking incomplete remote worldgen for some seeds.
                # Keep ONE overall 180s budget covering both the command
                # and the loaded-block acknowledgement: no timeout raising.
                budget_start=time.monotonic()
                for command in commands:
                    rcon.send(command)
                    remaining=180.0-(time.monotonic()-budget_start)
                    if remaining<=0:
                        raise TimeoutError(
                            "Distant 180s budget exhausted after forceload ACK"
                            " but before fully-loaded chunk verification")
                    await_full_chunk(rcon,1024,1024,
                                     timeout_seconds=remaining)
                elapsed_seconds = time.monotonic() - budget_start
                timing_path = root/"build"/"rcon_distant_timing.json"
                timing_path.write_text(json.dumps({
                    "preset": expected_preset,
                    "target_x": 1024,
                    "target_z": 1024,
                    "elapsed_seconds": round(elapsed_seconds, 3),
                    "limit_seconds": 180,
                    "fully_loaded": True
                }, indent=2) + "\n", encoding="utf-8")
                print("QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS: PASS",
                      f"elapsed_seconds={elapsed_seconds:.3f}", flush=True)
                # Standalone DH-Chunky v0.3 command is ONLY required for the
                # new triple-mod patched test arms. All other worldgen tests
                # (including vanilla, no-addon, USGS, etc.) stay unaffected.
                if os.getenv("REALSCALE_DH_CHUNKY_REQUIRE_STATUS")=="1":
                    status=rcon.send("dhchunkyguard status")
                    if not (
                        "DHChunkyGuard v0.3" in status
                        and "nativeBindsSinceLaunch=" in status
                        and "probes=" in status
                        and "GPU_LOD=unverified" in status
                    ):
                        raise RuntimeError("DH-Chunky bridge status missing or incomplete")
                    # Report only the bounded status string, no credentials,
                    # command arguments containing paths or player identifiers.
                    print("REALSCALE_DH_CHUNKY_STATUS_COMMAND_OK:",status,
                          flush=True)
                    advice=rcon.send("dhchunkyguard advice")
                    if not advice.startswith("DHChunkyGuard:"):
                        raise RuntimeError("DH-Chunky status advice not available")
            elif profile=="distant_route":
                results=[]
                result_file=root/"build"/"rcon_distant_route_timing.json"
                for x,z in FAR_ROUTE_SITES:
                    started=time.monotonic()
                    command=f"execute in minecraft:overworld run forceload add {x} {z}"
                    rcon.send(command)
                    remaining=180.0-(time.monotonic()-started)
                    if remaining<=0:
                        raise TimeoutError(
                            f"Far route {x},{z}: ticket ACK exhausted strict 180s budget")
                    await_full_chunk(rcon,x,z,timeout_seconds=remaining)
                    full_seconds=time.monotonic()-started
                    if full_seconds>180.0:
                        raise TimeoutError(
                            f"Far route {x},{z}: fully loaded after strict 180s limit")
                    # Save the real Anvil blocks and release the completed
                    # ticket BEFORE visiting the next distant region.
                    rcon.send("save-all flush")
                    rcon.send(
                        f"execute in minecraft:overworld run forceload remove {x} {z}")
                    results.append({
                        "x":x, "z":z, "loaded_seconds":round(full_seconds,3),
                        "saved":True, "unloaded_ticket":True})
                    result_file.write_text(json.dumps({
                        "preset":expected_preset,"waypoints":results,
                        "strict_seconds_per_waypoint":180
                    },indent=2)+"\n",encoding="utf-8")
                    print("QA_DISTANT_ROUTE_FULL_CHUNK_PASS:",x,z,
                          f"seconds={full_seconds:.3f}",flush=True)
                if len(results)!=len(FAR_ROUTE_SITES):
                    raise AssertionError("Far route did not complete every waypoint")
                print("QA_DISTANT_ROUTE_ALL_SAVED_PASS:",len(results),
                      "waypoints",flush=True)
            else:
                for command in commands:
                    rcon.send(command)
            if jfr_pid is not None:
                recording=root/"build"/"realscale_worldgen_cpu.jfr"
                capture=subprocess.run(
                    ["jcmd",jfr_pid,"JFR.dump","name=realscale_worldgen",
                     "filename="+str(recording)],
                    capture_output=True,text=True,timeout=45,check=True)
                print("REALSCALE_JFR_DUMP:",recording,
                      "bytes=",recording.stat().st_size,flush=True)
                print("REALSCALE_JFR_CAPTURE_MESSAGE:",capture.stdout.strip(),
                      flush=True)
                subprocess.run(["jcmd",jfr_pid,"JFR.stop","name=realscale_worldgen"],
                               capture_output=True,text=True,timeout=30,check=True)
            time.sleep(5 if profile=="structures" else (100 if profile=="fantasy" else (5 if profile=="usgs" else 35)))
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
        # An unanswered RCON request can mean a worldgen deadlock or simply
        # a very expensive chunk. Preserve the running JVM thread stacks
        # BEFORE teardown so we can fix the actual hotspot.
        if isinstance(exc,TimeoutError) and proc.poll() is None:
            try:
                dump=subprocess.run(
                    ["jcmd","-l"],capture_output=True,text=True,timeout=8)
                print("JVM_PROCESS_LIST:",dump.stdout[-3000:],flush=True)
                for entry in dump.stdout.splitlines():
                    if "net.neoforged" not in entry and "minecraft" not in entry.lower():
                        continue
                    pid=entry.split()[0]
                    if not pid.isdigit():continue
                    # A single end-of-timeout dump is ambiguous: a worker
                    # can be RUNNABLE inside a height probe, or WAITING on
                    # another chunk's future. Compare three short snapshots
                    # and print a bounded summary directly to Actions logs.
                    # Preserve all raw JVM frames as artifacts for review.
                    snapshots=[]
                    dump_paths=[]
                    for sample in range(3):
                        threads=subprocess.run(
                            ["jcmd",pid,"Thread.print"],capture_output=True,
                            text=True,timeout=12)
                        if threads.returncode!=0:
                            print("RCON_JCMD_FAILED:",threads.stderr[-500:],
                                  flush=True)
                            break
                        dump_path=root/"build"/(
                            "rcon_"+profile+"_thread_dump_"+str(sample)+".txt")
                        dump_path.write_text(threads.stdout+threads.stderr,
                                             encoding="utf-8")
                        dump_paths.append(dump_path)
                        snapshots.append(threads.stdout)
                        print("RCON_TIMEOUT_THREAD_DUMP:",dump_path,flush=True)
                        if sample<2:
                            time.sleep(1)
                    if snapshots:
                        from diagnose_worldgen_threads import diagnose_dumps
                        report=diagnose_dumps(snapshots)
                        report_path=root/"build"/(
                            "rcon_"+profile+"_stall_fingerprint.json")
                        report_path.write_text(json.dumps(report,indent=2),
                                               encoding="utf-8")
                        print("RCON_STALL_FINGERPRINT:",
                              json.dumps({
                                "samples":report["samples"],
                                "thread_states":report["thread_states"],
                                "marker_observations":report["marker_observations"],
                                "top_leaf_frames":report["top_leaf_frames"],
                                "hypotheses_not_proven_causes":
                                    report["hypotheses_not_proven_causes"]
                              },sort_keys=True),flush=True)
                        for thread in report["sampled_threads"][:8]:
                            print("RCON_STALL_THREAD:",json.dumps({
                                "sample":thread["sample"],
                                "name":thread["thread"],
                                "state":thread["state"],
                                "markers":thread["markers"],
                                "frames":thread["frames"][:7]
                            }),flush=True)
                        print("RCON_STALL_FINGERPRINT_FILE:",report_path,
                              flush=True)
            except (OSError,subprocess.TimeoutExpired) as dump_error:
                print("RCON_TIMEOUT_DIAGNOSTIC_UNAVAILABLE:",repr(dump_error),flush=True)
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
    # LOD conversion and persistence are NOT guaranteed by a successful
    # Minecraft /forceload. Collect independent SQLite persistence evidence
    # after the server has exited. This is a read-only audit that never
    # mutates or assumes the DH GPU renderer worked. Unknown/empty is
    # reported, not silently promoted to success.
    if profile=="distant":
        from audit_dh_lod_sqlite import scan
        scan_root=root/"run"
        if scan_root.is_dir():
            storage=scan(scan_root)
            destination=root/"build"/"dh-chunky-lod-storage.json"
            destination.parent.mkdir(parents=True,exist_ok=True)
            destination.write_text(
                json.dumps(storage,sort_keys=True,indent=2)+"\n",
                encoding="utf-8")
            print("REALSCALE_DH_LOD_PERSISTENCE_AFTER_STOP:",
                  json.dumps({k:v for k,v in storage.items() if k!="files"},
                             sort_keys=True),flush=True)
        else:
            print("REALSCALE_DH_LOD_PERSISTENCE_AFTER_STOP: run directory unavailable",
                  flush=True)
    if failed is not None:raise failed
    if not ready.is_set():raise RuntimeError("Server did not boot")
    if profile=="fantasy":
        nether=list(root.glob("run/**/DIM-1/region/*.mca"))
        end=list(root.glob("run/**/DIM1/region/*.mca"))
        print("NETHER_PERSISTED_REGION_FILES:",list(map(str,nether)))
        print("END_PERSISTED_REGION_FILES:",list(map(str,end)))
        if not nether or not end:
            raise RuntimeError("Fantasy Nether/End still has no persisted Anvil regions")

    # Canonical TVA world preset IDs are *not* noise-settings IDs: the
    # four menu presets point to separate, stable base terrain settings.
    # Derive the expected NBT settings from the actual preset JSON, then
    # also validate 512-block Grand dimensions for TVA menu worlds.
    json_root=pathlib.Path(__file__).resolve().parents[2]/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
    preset_file=json_root/(expected_preset.removeprefix("realscale:")+".json")
    preset_json=json.loads(preset_file.read_text()) if preset_file.is_file() else None
    expected_overworld=(preset_json["dimensions"]["minecraft:overworld"]["generator"]["settings"]
                        if preset_json is not None else expected_preset)
    levels=list(root.glob("run/**/level.dat"))
    matched=False
    from realscale_headless_qa import read_nbt
    for path in levels:
        nbt=read_nbt(gzip.decompress(path.read_bytes()))
        data=nbt.get("Data",nbt)
        dimensions=data.get("WorldGenSettings",{}).get("dimensions",{})
        overworld=dimensions.get("minecraft:overworld",{})
        actual=overworld.get("generator",{}).get("settings")
        if actual!=expected_overworld:
            print("Worldgen does not match requested terrain settings:",path,actual,
                  "expected",expected_overworld,flush=True)
            continue
        if expected_preset.startswith("realscale:tva_"):
            required=preset_json["dimensions"]
            for dimension in ("minecraft:the_nether","minecraft:the_end"):
                target=required[dimension]["generator"]["settings"]
                found=dimensions.get(dimension,{}).get("generator",{}).get("settings")
                if found!=target:
                    raise RuntimeError(f"TVA Grand dimension regression {dimension}: {found} != {target}")
            source=overworld.get("generator",{}).get("biome_source",{})
            if not isinstance(source,dict) or source.get("type")!="realscale:sky_ocean":
                raise RuntimeError("TVA sky/ocean biome source lost in persisted new world")
        if profile=="packs":
            enabled=data.get("DataPacks",{}).get("Enabled",[])
            for packname in ("RealScale-GeoEcology-Winter-EXPERIMENTAL.zip",
                             "RealScale-Vanilla-Habitat-Structures-EXPERIMENTAL.zip",
                             "RealScale-Vanilla-Structures-2x-EXPERIMENTAL.zip"):
                if not any(packname in pack for pack in enabled):
                    raise RuntimeError(f"Saved level.dat missing enabled data pack {packname}: {enabled}")
            print("Saved NBT confirms three opt-in test data packs",flush=True)
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
        raise SystemExit("usage: rcon_worldgen_smoke.py neoForgeRoot preset usgs|distant|distant_route|fantasy|structures|packs")
    main(pathlib.Path(sys.argv[1]).resolve(),sys.argv[2],sys.argv[3])
