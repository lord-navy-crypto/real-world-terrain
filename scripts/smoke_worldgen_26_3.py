#!/usr/bin/env python3
"""Start actual modded dedicated server, require world-generation and clean shutdown."""
import pathlib,sys,subprocess,threading,time,os,signal,queue,re,socket,struct,secrets
module=pathlib.Path(sys.argv[1])
args=sys.argv[2:]
password=secrets.token_hex(16)
port=25575
for name in ("run","run/server"):
    d=module/name
    d.mkdir(parents=True,exist_ok=True)
    (d/"eula.txt").write_text("eula=true\n")
    (d/"server.properties").write_text("level-name=realscale-smoke\nlevel-type=realscale:natural_automate_three_realms\nlevel-seed=785312\nserver-ip=127.0.0.1\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nenable-rcon=true\nrcon.port="+str(port)+"\nrcon.password="+password+"\n")
def rcon_stop():
    def receive(sock, n):
        result=b""
        while len(result)<n:
            part=sock.recv(n-len(result))
            if not part: raise EOFError("RCON disconnected")
            result+=part
        return result
    def packet(sock, request_id, kind, message):
        body=struct.pack("<ii",request_id,kind)+message.encode()+b"\0\0"
        sock.sendall(struct.pack("<i",len(body))+body)
    def response(sock):
        length=struct.unpack("<i",receive(sock,4))[0]
        if not 10<=length<=1048576: raise ValueError("Invalid RCON response length")
        data=receive(sock,length)
        return struct.unpack("<ii",data[:8]),data[8:-2]
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
        packet(conn,2,2,"save-all flush")
        response(conn)
        packet(conn,3,2,"stop")
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
            rcon_stop()
            stop=True
    if not done or error or not stop: raise RuntimeError("World-generation smoke failed")
    if proc.poll() is None: proc.wait(timeout=60)
    if proc.returncode!=0: raise RuntimeError("Server exited unsuccessfully")
    if not list(module.glob("run/**/realscale-smoke/level.dat")): raise RuntimeError("Expected saved world was not generated")
    if not list(module.glob("run/**/realscale-smoke/region/*.mca")): raise RuntimeError("No persisted Overworld region files")
    print("REALSCALE_26_3_SERVER_SMOKE_OK")
finally:
    if proc.poll() is None:
        os.killpg(proc.pid,signal.SIGTERM)
