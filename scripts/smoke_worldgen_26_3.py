#!/usr/bin/env python3
"""Start actual modded dedicated server, require world-generation and clean shutdown."""
import pathlib,sys,subprocess,threading,time,os,signal,queue,re
module=pathlib.Path(sys.argv[1])
args=sys.argv[2:]
for name in ("run","run/server"):
    d=module/name
    d.mkdir(parents=True,exist_ok=True)
    (d/"eula.txt").write_text("eula=true\n")
    (d/"server.properties").write_text("level-name=realscale-smoke\nlevel-type=realscale:natural_automate_three_realms\nlevel-seed=785312\nserver-ip=127.0.0.1\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\n")
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
            proc.stdin.write("save-all flush\nstop\n");proc.stdin.flush()
            stop=True
    if not done or error or not stop: raise RuntimeError("World-generation smoke failed")
    if proc.poll() is None: proc.wait(timeout=60)
    if proc.returncode!=0: raise RuntimeError("Server exited unsuccessfully")
    if not list(module.glob("run/**/realscale-smoke/level.dat")): raise RuntimeError("Expected saved world was not generated")
    print("REALSCALE_26_3_SERVER_SMOKE_OK")
finally:
    if proc.poll() is None:
        os.killpg(proc.pid,signal.SIGTERM)
