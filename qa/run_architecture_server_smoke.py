#!/usr/bin/env python3
"""Boot NeoForge and place licensed plus original RealScale architecture templates."""
from __future__ import annotations
import queue
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path.cwd()
LOG_PATH = ROOT / "run/architecture-pilot-server-smoke.log"
TEMPLATES = (
    "modern_house", "glass_greenhouse", "city_library",
    "forest_ranger_lodge", "plains_farmstead", "alpine_waystation",
    "coastal_lighthouse", "desert_caravanserai",
)

def fail(message: str, proc=None, lines=None):
    if proc is not None and proc.poll() is None:
        try:
            if proc.stdin and not proc.stdin.closed:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                proc.wait(timeout=30)
        except Exception:
            try:
                proc.terminate()
                proc.wait(timeout=10)
            except Exception:
                try:
                    proc.kill()
                    proc.wait(timeout=10)
                except Exception:
                    pass
    captured = "".join(lines or [])
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text(captured, encoding="utf-8", errors="replace")
    print(captured[-16000:], file=sys.stderr)
    raise SystemExit(message)

proc = subprocess.Popen(
    ["gradle", "--no-daemon", "runServer", "--stacktrace"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, text=True, bufsize=1,
)
events = queue.Queue()
captured = []

def read_output():
    for line in proc.stdout:
        captured.append(line)
        print(line, end="", flush=True)
        events.put(line)

reader = threading.Thread(target=read_output, daemon=True)
reader.start()

def wait_for(fragment: str, timeout: int):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            fail(f"NeoForge exited before expected output: {fragment}", proc, captured)
        try:
            line = events.get(timeout=min(1.0, max(0.05, deadline - time.monotonic())))
        except queue.Empty:
            continue
        if fragment in line:
            return line
    fail(f"Timed out waiting for server output: {fragment}", proc, captured)

wait_for("Done (", 600)
print("NEOFORGE_ARCHITECTURE_SERVER_BOOT_OK", flush=True)
proc.stdin.write("forceload add 0 0 256 32\n")
proc.stdin.flush()
time.sleep(2)

for index, template in enumerate(TEMPLATES):
    x = index * 32
    command = f"place template realscale:architecture/{template} {x} 160 0"
    expected = f'Loaded template "realscale:architecture/{template}" at'
    print(f">>> {command}", flush=True)
    proc.stdin.write(command + "\n")
    proc.stdin.flush()
    wait_for(expected, 45)

proc.stdin.write("save-all flush\n")
proc.stdin.flush()
time.sleep(3)
proc.stdin.write("forceload remove all\n")
proc.stdin.write("stop\n")
proc.stdin.flush()
try:
    proc.wait(timeout=120)
except subprocess.TimeoutExpired:
    fail("NeoForge server did not stop cleanly after template placement.", proc, captured)

reader.join(timeout=10)
captured_text = "".join(captured)
LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
LOG_PATH.write_text(captured_text, encoding="utf-8", errors="replace")
if proc.returncode != 0:
    fail(f"NeoForge server exited with code {proc.returncode}.", None, captured)

for template in TEMPLATES:
    expected = f'Loaded template "realscale:architecture/{template}" at'
    if expected not in captured_text:
        fail(f"Template placement success was not reported for {template}.", None, captured)
for bad in (
    "Failed to place template", "There is no template with ID",
    "Unknown registry key", "Couldn't parse data file", "Unknown block",
):
    if bad in captured_text:
        fail(f"Architecture pilot server reported an error: {bad}", None, captured)

print("REAL_SCALE_ARCHITECTURE_TEMPLATE_PLACEMENT_OK: "
      f"all {len(TEMPLATES)} licensed/original templates loaded and placed")
