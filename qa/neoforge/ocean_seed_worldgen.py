#!/usr/bin/env python3
"""Boot a disposable real NeoForge world with a preselected ocean seed.

Only near-spawn chunks are required. NO /locate biome and NO expensive
remote force-loading loop; inspect saved Anvil data offline after shutdown.
"""
import os
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
from skyland_rcon import command

def main():
    seed = os.environ.get("TVA_OCEAN_SEED", "unknown")
    log = Path("build") / ("ocean-seed-" + seed + ".log")
    log.parent.mkdir(parents=True, exist_ok=True)
    process = subprocess.Popen(
        ["gradle", "--no-daemon", "runServer", "--stacktrace"],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.PIPE,
        text=True, bufsize=1, start_new_session=True
    )
    lines = []
    ready = threading.Event()
    def drain():
        for line in process.stdout:
            lines.append(line)
            if "Done (" in line and "For help" in line:
                ready.set()
    thread = threading.Thread(target=drain, daemon=True)
    thread.start()
    try:
        deadline = time.monotonic() + 440
        while time.monotonic() < deadline and process.poll() is None and not ready.is_set():
            time.sleep(1)
        if not ready.is_set():
            raise RuntimeError("TVA ocean-seed Minecraft failed to start: " + seed)
        # World creation has finished. Spawn chunks are already produced by
        # vanilla server startup and will be saved on orderly shutdown.
        time.sleep(15)
        control = command("time query daytime", max_attempts=2, timeout_seconds=30)
        if "the time is " not in control.lower():
            raise RuntimeError("RCON server response missing after ocean seed start")
        command("save-all flush", max_attempts=3, timeout_seconds=40)
        try:
            command("stop", max_attempts=1, timeout_seconds=30)
        except RuntimeError as exc:
            print("Ocean-seed stop RCON disconnected; checking process:", str(exc), flush=True)
        try:
            process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            raise RuntimeError("TVA ocean-seed server did not shut down in 90 seconds")
        if process.returncode not in (0,):
            raise RuntimeError("TVA ocean-seed server exited " + str(process.returncode))
        print("TVA_OCEAN_SEED_WORLD_SAVED", seed, flush=True)
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGINT)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
        thread.join(timeout=8)
        log.write_text("".join(lines))
        print("TVA_OCEAN_SEED_SERVER_TAIL", log.read_text()[-4800:], flush=True)

if __name__ == "__main__":
    main()
