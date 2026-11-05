#!/usr/bin/env python3
"""Launch the real NeoForge server with RealScale + pinned DH + Chunky jars."""
from __future__ import annotations

import hashlib
import json
import queue
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path.cwd()
RUN = ROOT / "run"
LOG_PATH = RUN / "dh-chunky-runtime-smoke.log"
MODS = RUN / "mods"

# Immutable Modrinth version IDs. The API metadata supplies the official file
# URL and SHA-512; no third-party binaries are committed to this repository.
MODS_TO_INSTALL = (
    (
        "9w34y8ai",
        "uCdwusMi",
        "DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar",
    ),
    (
        "LuFhm4eU",
        "fALzjamp",
        "Chunky-NeoForge-1.4.23.jar",
    ),
)

HOOK_MARKER = "REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED"
BOUND_MARKER = "REALSCALE_DH_CHUNKY_GUARD_BOUND"
CRASH_MARKER = "IllegalStateException: Chunky is not loaded."


def download_and_verify_mods() -> None:
    MODS.mkdir(parents=True, exist_ok=True)
    for version_id, project_id, expected_filename in MODS_TO_INSTALL:
        request = urllib.request.Request(
            f"https://api.modrinth.com/v2/version/{version_id}",
            headers={"User-Agent": "RealScale-DH-Chunky-runtime-smoke/1.0"},
        )
        with urllib.request.urlopen(request, timeout=45) as response:
            metadata = json.load(response)

        if metadata.get("id") != version_id:
            raise RuntimeError(f"Unexpected Modrinth version ID for {expected_filename}")
        if metadata.get("project_id") != project_id:
            raise RuntimeError(f"Unexpected Modrinth project ID for {expected_filename}")
        if "1.21.1" not in metadata.get("game_versions", []):
            raise RuntimeError(f"{expected_filename} does not declare Minecraft 1.21.1")
        if "neoforge" not in metadata.get("loaders", []):
            raise RuntimeError(f"{expected_filename} does not declare NeoForge")

        candidates = [
            item for item in metadata.get("files", [])
            if item.get("filename") == expected_filename
        ]
        if len(candidates) != 1:
            raise RuntimeError(
                f"Expected exactly one official file named {expected_filename}; "
                f"found {len(candidates)}"
            )
        file_info = candidates[0]
        expected_sha512 = file_info.get("hashes", {}).get("sha512")
        if not expected_sha512:
            raise RuntimeError(f"Modrinth did not provide SHA-512 for {expected_filename}")

        request = urllib.request.Request(
            file_info["url"],
            headers={"User-Agent": "RealScale-DH-Chunky-runtime-smoke/1.0"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()

        actual_sha512 = hashlib.sha512(data).hexdigest()
        if actual_sha512.lower() != expected_sha512.lower():
            raise RuntimeError(f"SHA-512 mismatch for {expected_filename}")

        destination = MODS / expected_filename
        destination.write_bytes(data)
        print(
            f"VERIFIED_MOD: {expected_filename} "
            f"bytes={len(data)} sha512={actual_sha512}",
            flush=True,
        )

    # NeoForge ModDev versions have used both the run root and run/server as
    # server game directories. Mirror only the two verified test jars so the
    # same smoke test works with either run-directory convention.
    nested_mods = RUN / "server" / "mods"
    nested_mods.mkdir(parents=True, exist_ok=True)
    for source in MODS.glob("*.jar"):
        shutil.copy2(source, nested_mods / source.name)


def prepare_server() -> None:
    RUN.mkdir(parents=True, exist_ok=True)
    properties = (
        "server-port=25565\n"
        "level-name=dh-chunky-runtime-smoke\n"
        "level-type=minecraft:normal\n"
        "level-seed=3331423\n"
        "max-tick-time=-1\n"
        "view-distance=3\n"
        "simulation-distance=3\n"
        "online-mode=false\n"
        "enable-rcon=false\n"
        "spawn-protection=0\n"
    )
    for game_dir in (RUN, RUN / "server"):
        game_dir.mkdir(parents=True, exist_ok=True)
        (game_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (game_dir / "server.properties").write_text(properties, encoding="utf-8")


def save_log(lines: list[str]) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text("".join(lines), encoding="utf-8", errors="replace")


def stop_server(proc: subprocess.Popen[str] | None) -> None:
    if proc is None or proc.poll() is not None:
        return
    try:
        if proc.stdin and not proc.stdin.closed:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        proc.wait(timeout=35)
    except Exception:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=10)


def main() -> int:
    download_and_verify_mods()
    prepare_server()

    proc: subprocess.Popen[str] | None = None
    captured: list[str] = []
    events: queue.Queue[str] = queue.Queue()

    try:
        proc = subprocess.Popen(
            ["gradle", "--no-daemon", "runServer", "--stacktrace"],
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )

        def read_output() -> None:
            assert proc is not None and proc.stdout is not None
            for line in proc.stdout:
                captured.append(line)
                print(line, end="", flush=True)
                events.put(line)

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()

        def wait_for(fragment: str, timeout: int) -> None:
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None:
                    raise RuntimeError(
                        f"NeoForge exited before expected log marker: {fragment}"
                    )
                try:
                    line = events.get(timeout=0.5)
                except queue.Empty:
                    continue
                if fragment in line:
                    return
            raise TimeoutError(f"Timed out waiting for log marker: {fragment}")

        wait_for("Done (", 600)
        print("NEOFORGE_WITH_DH_AND_CHUNKY_SERVER_BOOT_OK", flush=True)

        # Force several distinct full-chunk events. If DH encountered the
        # known early-provider race, a later event must let its native bind retry.
        assert proc.stdin is not None
        for x in (0, 16, 32, 48, 64, 80, 96, 112):
            proc.stdin.write(f"forceload add {x} 0\n")
            proc.stdin.flush()
            time.sleep(0.4)
            if any(BOUND_MARKER in line for line in captured):
                break

        wait_for(HOOK_MARKER, 45)
        wait_for(BOUND_MARKER, 90)

        if any(CRASH_MARKER in line for line in captured):
            raise RuntimeError("The known ChunkyProvider startup exception appeared in the log")

        print("DH_CHUNKY_NATIVE_LISTENER_BIND_CONFIRMED", flush=True)
        print("REALSCALE_DH_CHUNKY_RUNTIME_SMOKE_PASS", flush=True)
        return 0
    except Exception as exc:
        print(f"DH_CHUNKY_RUNTIME_SMOKE_FAILED: {exc}", file=sys.stderr, flush=True)
        print("".join(captured[-240:]), file=sys.stderr, flush=True)
        return 1
    finally:
        stop_server(proc)
        save_log(captured)


if __name__ == "__main__":
    raise SystemExit(main())
