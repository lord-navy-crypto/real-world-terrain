#!/usr/bin/env python3
"""Start a modded dedicated server, force a real Overworld chunk, and verify persistence."""
import pathlib,sys,subprocess,threading,time,os,signal,queue,socket,struct,secrets

module=pathlib.Path(sys.argv[1])
args=sys.argv[2:]
password=secrets.token_hex(16)
port=25575
for name in ("run","run/server"):
    d=module/name
    d.mkdir(parents=True,exist_ok=True)
    (d/"eula.txt").write_text("eula=true\\n")
    (d/"server.properties").write_text("level-name=realscale-smoke\\nlevel-type=realscale:natural_automate_three_realms\\nlevel-seed=785312\\nserver-ip=127.0.0.1\\nonline-mode=false\\nview-distance=2\\nsimulation-distance=2\\nmax-tick-time=-1\\nenable-rcon=true\\nrcon.port="+str(port)+"\\nrcon.password="+password+"\\n")

def rcon_generate_and_stop():
    def receive(sock, n):
        result=b""
        while len(result)<n:
            part=sock.recv(n-len(result))
            if not part: raise EOFError("RCON disconnected")
            result+=part
        return result
    def packet(sock, request_id, kind, message):
        body=struct.pack("<ii",request_id,kind)+message.encode()+b"\\0\\0"
        sock.sendall(struct.pack("<i",len(body))+body)
    def response(sock):
        length=struct.unpack("<i",receive(sock,4))[0]
        if not 10<=length<=1048576: raise ValueError("Invalid RCON response length")
        data=receive(sock,length)
        return struct.unpack("<ii",data[:8]),data[8:-2]
    def command(sock, request_id, message):
        packet(sock,request_id,2,message)
        (response_id,_),payload=response(sock)
        if response_id!=request_id:
            raise RuntimeError("Unexpected RCON response id "+str(response_id)+" for "+str(request_id))
        return payload.decode("utf-8",errors="replace").strip()

    for attempt in range(30):
        try:
            conn=socket.create_connection(("127.0.0.1",port),timeout=5)
            break
        except OSError:
            time.sleep(1)
    else: raise RuntimeError("Local RCON not ready")

    with conn:
        conn.settimeout(60)
        packet(conn,1,3,password)
        while True:
            (request_id,kind),_=response(conn)
            if request_id==-1: raise RuntimeError("RCON authentication failed")
            if request_id==1 and kind==2: break

        # Minecraft 1.21.9+ removed fixed spawn chunks. Explicitly force-load
        # an Overworld chunk so this smoke test exercises real chunk generation.
        forced=command(conn,2,"execute in minecraft:overworld run forceload add 0 0")
        print("[smoke] Overworld forceload request: "+forced,flush=True)

        loaded=False
        last=""
        request_id=3
        load_deadline=time.monotonic()+60
        while time.monotonic()<load_deadline:
            last=command(
                conn,request_id,
                "execute in minecraft:overworld if loaded 0 64 0 run time query daytime"
            )
            request_id+=1
            if "the time is" in last.lower():
                loaded=True
                break
            time.sleep(0.5)
        if not loaded:
            raise RuntimeError("Timed out waiting for forced Overworld chunk 0,0; last RCON response: "+last)
        print("[smoke] Confirmed Overworld chunk 0,0 is fully loaded: "+last,flush=True)

        saved=command(conn,request_id,"save-all flush")
        request_id+=1
        print("[smoke] save-all flush: "+saved,flush=True)
        packet(conn,request_id,2,"stop")
        print("[smoke] Requested graceful server shutdown",flush=True)

log=module/"server-26.3-smoke.log"
proc=subprocess.Popen(["gradle","--no-daemon","-p",str(module),*args,"runServer"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,bufsize=1,start_new_session=True)
q=queue.Queue()
def read():
    with log.open("w") as f:
        for line in proc.stdout:
            f.write(line);f.flush();q.put(line)
    q.put(None)
threading.Thread(target=read,daemon=True).start()
done=False; stop=False; error=False; deadline=time.monotonic()+420
try:
    while time.monotonic()<deadline:
        try: line=q.get(timeout=1)
        except queue.Empty: continue
        if line is None: break
        print(line,end="",flush=True)
        if ("Failed to load registries" in line or "Errors in registry" in line or "Registry Loading" in line or "Failed to load datapacks" in line):error=True
        if "Done (" in line and "For help" in line:
            done=True
            rcon_generate_and_stop()
            stop=True
    if not done or error or not stop: raise RuntimeError("World-generation smoke failed")
    if proc.poll() is None: proc.wait(timeout=60)
    if proc.returncode!=0: raise RuntimeError("Server exited unsuccessfully")
    worlds=list(module.glob("run/**/realscale-smoke/level.dat"))
    if not worlds: raise RuntimeError("Expected saved world was not generated")
    regions=list(module.glob("run/**/realscale-smoke/region/*.mca"))
    if not regions:
        files=[str(p.relative_to(module)) for p in module.glob("run/**/realscale-smoke/**") if p.is_file()]
        raise RuntimeError("No persisted Overworld region files after forceload + save-all; world files: "+", ".join(files[:40]))
    print("REALSCALE_26_3_SERVER_SMOKE_OK")
finally:
    if proc.poll() is None:
        os.killpg(proc.pid,signal.SIGTERM)
