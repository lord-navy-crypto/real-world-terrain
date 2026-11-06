#!/usr/bin/env python3
"""Launch the real NeoForge server with RealScale + pinned DH + Chunky jars."""
from __future__ import annotations

import hashlib
import json
import queue
import re
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
        "enable-rcon=true\n"
        "rcon.password=realscale-dhchunky-smoke-local-only\n"
        "rcon.port=25575\n"
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


RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASSWORD = "realscale-dhchunky-smoke-local-only"


def recv_exact(sock, count: int) -> bytes:
    data = bytearray()
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data.extend(chunk)
    return bytes(data)


def send_packet(sock, packet_id: int, packet_type: int, payload: str) -> None:
    body = (
        packet_id.to_bytes(4, "little", signed=True)
        + packet_type.to_bytes(4, "little", signed=True)
        + payload.encode("utf-8")
        + b"\x00\x00"
    )
    sock.sendall(len(body).to_bytes(4, "little", signed=True) + body)


def recv_packet(sock):
    length = int.from_bytes(recv_exact(sock, 4), "little", signed=True)
    body = recv_exact(sock, length)
    packet_id = int.from_bytes(body[:4], "little", signed=True)
    packet_type = int.from_bytes(body[4:8], "little", signed=True)
    return packet_id, packet_type, body[8:-2].decode("utf-8", errors="replace")


def connect_rcon(timeout: int = 45):
    import socket

    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            sock = socket.create_connection((RCON_HOST, RCON_PORT), timeout=3)
            sock.settimeout(8)
            send_packet(sock, 1, 3, RCON_PASSWORD)
            packet_id, packet_type, _ = recv_packet(sock)
            if packet_id != 1 or packet_type != 2:
                sock.close()
                raise PermissionError("RCON authentication was rejected")
            return sock
        except Exception as exc:
            last_error = exc
            time.sleep(1)
    raise TimeoutError(f"RCON did not become ready: {last_error}")


def rcon_command(sock, packet_id: int, command: str) -> str:
    send_packet(sock, packet_id, 2, command)
    response_id, _response_type, response = recv_packet(sock)
    if response_id != packet_id:
        raise RuntimeError(f"Unexpected RCON response id {response_id}: {response}")
    return response


def run_chunky_pregen(sock, packet_id: int, captured: list[str], marker: str) -> int:
    """Exercise Chunky's real pre-generation command path on a small disposable area."""
    for command in (
        "chunky center 512 512",
        "chunky radius 32",
        "chunky shape square",
    ):
        response = rcon_command(sock, packet_id, command)
        packet_id += 1
        print(f"CHUNKY_COMMAND: {command} -> {response}", flush=True)
        if re.search(r"unknown command|unknown argument|no permission|not permitted|invalid", response, re.I):
            raise RuntimeError(f"Chunky rejected its selection command: {command}: {response}")

    task_log_start = len(captured)
    start_response = rcon_command(sock, packet_id, "chunky start")
    packet_id += 1
    print(f"CHUNKY_START: {start_response}", flush=True)
    if re.search(r"unknown command|no permission|not permitted|failed to start", start_response, re.I):
        raise RuntimeError(f"Chunky could not start its pre-generation task: {start_response}")
    if not re.search(r"start|task|pre.?generat", start_response, re.I) and not any(
        re.search(r"Task started|pre.?generation task", line, re.I) for line in captured
    ):
        raise RuntimeError(f"Chunky did not confirm task start: {start_response}")

    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if any(CRASH_MARKER in line for line in captured):
            raise RuntimeError("The DH-Chunky provider exception appeared during Chunky pre-generation")
        progress = rcon_command(sock, packet_id, "chunky progress")
        packet_id += 1
        print(f"CHUNKY_PROGRESS: {progress}", flush=True)
        task_log = "".join(captured[task_log_start:])
        finished = re.search(
            r"\[Chunky\] Task finished for minecraft:overworld\. "
            r"Processed:\s*([\d,]+)\s+chunks\s+\(100(?:\.0+)?%\)",
            task_log,
            re.I,
        )
        dh_paused = "Chunky running. Disabling DH world gen" in task_log
        dh_resumed = "Chunky no longer running. Re-enabling DH world gen" in task_log
        if finished and dh_paused and dh_resumed:
            completed_chunks = int(finished.group(1).replace(",", ""))
            if completed_chunks <= 0:
                raise RuntimeError("Chunky reported a completed task without processing chunks")
            print(f"{marker}: chunks={completed_chunks}; DH pause/resume confirmed", flush=True)
            return packet_id
        time.sleep(0.75)

    raise TimeoutError("Chunky did not complete the bounded pre-generation task within 120 seconds")


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

        sock = connect_rcon()
        try:
            # Force distinct full-chunk events. If DH saw the provider too early,
            # a later real chunk event must let its native listener bind retry.
            packet_id = 2
            for x in (0, 16, 32, 48, 64, 80, 96, 112):
                rcon_command(sock, packet_id, f"forceload add {x} 0")
                packet_id += 1
                time.sleep(0.4)
                if any(BOUND_MARKER in line for line in captured):
                    break

            if not any(HOOK_MARKER in line for line in captured):
                wait_for(HOOK_MARKER, 45)
            if not any(BOUND_MARKER in line for line in captured):
                wait_for(BOUND_MARKER, 90)

            packet_id = run_chunky_pregen(
                sock, packet_id, captured, "REALSCALE_DH_CHUNKY_PREGEN_PASS"
            )

            if any(CRASH_MARKER in line for line in captured):
                raise RuntimeError(
                    "The known ChunkyProvider startup exception appeared in the log"
                )

            time.sleep(1)
            if proc.poll() is not None:
                raise RuntimeError("NeoForge exited after the DH-Chunky listener bind")
            print("DH_CHUNKY_NATIVE_LISTENER_BIND_CONFIRMED", flush=True)
            print("REALSCALE_DH_CHUNKY_RUNTIME_SMOKE_PASS", flush=True)
            rcon_command(sock, packet_id, "stop")
            packet_id += 1
        finally:
            sock.close()

        proc.wait(timeout=35)
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
