#!/usr/bin/env python3
"""Boot NeoForge with the architecture pack enabled and place all pilot templates."""
from __future__ import annotations

import re
import selectors
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path.cwd()
LOG_PATH = ROOT / "run/architecture-pilot-server-smoke.log"
COMMANDS = [
    "forceload add 0 0 96 32",
    "place template realscale:architecture/modern_house 0 160 0",
    "place template realscale:architecture/glass_greenhouse 32 160 0",
    "place template realscale:architecture/city_library 64 160 0",
    "save-all flush",
]

def fail(message: str, proc=None, captured=""):
    if proc is not None and proc.poll() is None:
        proc.terminate()
        try:
            captured += proc.communicate(timeout=30)[0] or ""
        except subprocess.TimeoutExpired:
            proc.kill()
            captured += proc.communicate()[0] or ""
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text(captured, encoding="utf-8", errors="replace")
    print(captured[-12000:], file=sys.stderr)
    raise SystemExit(message)

proc = subprocess.Popen(
    ["gradle", "--no-daemon", "runServer", "--stacktrace"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    bufsize=1,
)
selector = selectors.DefaultSelector()
selector.register(proc.stdout, selectors.EVENT_READ)
captured = []
deadline = time.monotonic() + 600
booted = False

while time.monotonic() < deadline:
    if proc.poll() is not None:
        break
    for _key, _events in selector.select(timeout=1):
        line = proc.stdout.readline()
        if not line:
            continue
        captured.append(line)
        print(line, end="", flush=True)
        if "Done (" in line:
            booted = True
            break
    if booted:
        break

if not booted:
    fail("NeoForge architecture-pilot server did not reach the Done marker.", proc, "".join(captured))

for command in COMMANDS:
    print(f">>> {command}", flush=True)
    proc.stdin.write(command + "\n")
    proc.stdin.flush()
    time.sleep(2)

time.sleep(3)
proc.stdin.write("forceload remove all\n")
proc.stdin.write("stop\n")
proc.stdin.flush()
proc.stdin.close()

try:
    proc.wait(timeout=120)
except subprocess.TimeoutExpired:
    fail("NeoForge server did not stop cleanly after template placement.", proc, "".join(captured))

remaining = proc.stdout.read() or ""
captured_text = "".join(captured) + remaining
LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
LOG_PATH.write_text(captured_text, encoding="utf-8", errors="replace")

if proc.returncode != 0:
    fail(f"NeoForge server exited with code {proc.returncode}.", None, captured_text)

for template in ("modern_house", "glass_greenhouse", "city_library"):
    expected = f'Loaded template "realscale:architecture/{template}" at'
    if expected not in captured_text:
        fail(f"Template placement success was not reported for {template}.", None, captured_text)

for bad in ("Failed to place template", "There is no template with ID",
            "Unknown registry key", "Couldn't parse data file"):
    if bad in captured_text:
        fail(f"Architecture pilot server reported an error: {bad}", None, captured_text)

print("REAL_SCALE_ARCHITECTURE_TEMPLATE_PLACEMENT_OK: all three templates loaded and placed")
