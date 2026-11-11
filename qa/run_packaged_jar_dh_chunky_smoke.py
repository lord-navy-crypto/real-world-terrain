#!/usr/bin/env python3
"""Test the built RealScale JAR in a real NeoForge server with DH and Chunky."""
from __future__ import annotations

import os
import queue
import re
import shutil
import socket
import subprocess
import threading
import time
from pathlib import Path

ROOT = Path.cwd()
TEST_ROOT = ROOT / "build" / "packaged-dh-chunky-smoke"
LOG_PATH = TEST_ROOT / "packaged-dh-chunky-runtime-smoke.log"
RCON_HOST = "127.0.0.1"
RCON_PORT = 25576
RCON_PASSWORD = "realscale-packaged-smoke-local-only"

HOOK_MARKER = "REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED"
BOUND_MARKER = "REALSCALE_DH_CHUNKY_GUARD_BOUND"
CRASH_MARKER = "IllegalStateException: Chunky is not loaded."


def exact_read(sock: socket.socket, count: int) -> bytes:
    data = bytearray()
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data.extend(chunk)
    return bytes(data)


def send_packet(sock: socket.socket, packet_id: int, packet_type: int, payload: str) -> None:
    body = (
        packet_id.to_bytes(4, "little", signed=True)
        + packet_type.to_bytes(4, "little", signed=True)
        + payload.encode("utf-8")
        + b"\x00\x00"
    )
    sock.sendall(len(body).to_bytes(4, "little", signed=True) + body)


def receive_packet(sock: socket.socket):
    length = int.from_bytes(exact_read(sock, 4), "little", signed=True)
    body = exact_read(sock, length)
    return (
        int.from_bytes(body[:4], "little", signed=True),
        int.from_bytes(body[4:8], "little", signed=True),
        body[8:-2].decode("utf-8", errors="replace"),
    )


def connect_rcon(timeout: int = 60) -> socket.socket:
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            sock = socket.create_connection((RCON_HOST, RCON_PORT), timeout=3)
            sock.settimeout(8)
            send_packet(sock, 1, 3, RCON_PASSWORD)
            packet_id, packet_type, _ = receive_packet(sock)
            if packet_id != 1 or packet_type != 2:
                sock.close()
                raise PermissionError("RCON authentication rejected")
            return sock
        except Exception as exc:
            last_error = exc
            time.sleep(1)
    raise TimeoutError(f"Packaged NeoForge server RCON did not become ready: {last_error}")


def command(sock: socket.socket, packet_id: int, value: str) -> str:
    send_packet(sock, packet_id, 2, value)
    response_id, _response_type, response = receive_packet(sock)
    if response_id != packet_id:
        raise RuntimeError(f"Unexpected RCON response id {response_id}: {response}")
    return response


def wait_for(events: queue.Queue[str], proc: subprocess.Popen[str],
             captured: list[str], fragment: str, timeout: int):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(
                f"Packaged NeoForge server exited before marker {fragment!r}:\n"
                + "".join(captured[-160:])
            )
        try:
            line = events.get(timeout=min(1.0, max(0.05, deadline - time.monotonic())))
        except queue.Empty:
            continue
        if fragment in line:
            return
    raise TimeoutError(f"Timed out waiting for packaged-server marker: {fragment}")


def install_official_neoforge_server() -> Path:
    build_text = (ROOT / "build.gradle").read_text(encoding="utf-8")
    match = re.search(r"neoForge\s*\{\s*version\s*=\s*'([^']+)'", build_text)
    if not match:
        raise RuntimeError("Could not determine the exact NeoForge version from build.gradle")
    version = match.group(1)

    TEST_ROOT.mkdir(parents=True, exist_ok=True)
    installer = TEST_ROOT / f"neoforge-{version}-installer.jar"
    url = (
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
        f"{version}/neoforge-{version}-installer.jar"
    )
    if not installer.is_file():
        import urllib.request
        request = urllib.request.Request(
            url, headers={"User-Agent": "RealScale-packaged-server-smoke/1.0"}
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            installer.write_bytes(response.read())

    result = subprocess.run(
        ["java", "-jar", str(installer), "--installServer"],
        cwd=TEST_ROOT,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=900,
    )
    print(result.stdout[-12000:], flush=True)
    if result.returncode != 0:
        raise RuntimeError(f"Official NeoForge server installation failed: {result.returncode}")

    run_script = TEST_ROOT / "run.sh"
    if not run_script.is_file():
        raise RuntimeError("NeoForge installer completed without creating run.sh")
    run_script.chmod(run_script.stat().st_mode | 0o111)
    return run_script


def prepare_mods() -> Path:
    # Reuse the same Modrinth API metadata and SHA-512 verification used by
    # the dev-server integration smoke. These are external mods, not bundled.
    import sys
    sys.path.insert(0, str(ROOT.parent / "qa"))
    from run_dh_chunky_runtime_smoke import download_and_verify_mods

    download_and_verify_mods()
    source_mods = ROOT / "run" / "mods"
    mods = TEST_ROOT / "mods"
    mods.mkdir(parents=True, exist_ok=True)

    jars = sorted((ROOT / "build" / "libs").glob("*.jar"))
    if len(jars) != 1:
        raise RuntimeError(f"Expected exactly one built RealScale JAR, found: {jars}")
    shutil.copy2(jars[0], mods / jars[0].name)

    for name in (
        "geckolib-neoforge-1.21.1-4.9.3.jar",
        "DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar",
        "Chunky-NeoForge-1.4.23.jar",
    ):
        source = source_mods / name
        if not source.is_file():
            raise RuntimeError(f"SHA-512-verified external mod is missing: {source}")
        shutil.copy2(source, mods / name)

    print("PACKAGED_REALScale_JAR:", jars[0].name, flush=True)
    print("PACKAGED_SERVER_MODS:", sorted(p.name for p in mods.glob("*.jar")), flush=True)
    return mods


def prepare_properties():
    (TEST_ROOT / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (TEST_ROOT / "user_jvm_args.txt").write_text("-Xms1G\n-Xmx3G\n", encoding="utf-8")
    properties = (
        "server-port=25566\n"
        "level-name=packaged-dh-chunky-smoke\n"
        "level-type=minecraft:normal\n"
        "level-seed=3331423\n"
        "max-tick-time=-1\n"
        "view-distance=3\n"
        "simulation-distance=3\n"
        "online-mode=false\n"
        "enable-rcon=true\n"
        f"rcon.password={RCON_PASSWORD}\n"
        f"rcon.port={RCON_PORT}\n"
        "spawn-protection=0\n"
    )
    (TEST_ROOT / "server.properties").write_text(properties, encoding="utf-8")


def stop_server(proc: subprocess.Popen[str] | None):
    if proc is None or proc.poll() is not None:
        return
    try:
        proc.terminate()
        proc.wait(timeout=25)
    except Exception:
        proc.kill()
        proc.wait(timeout=10)


def main() -> int:
    proc = None
    captured: list[str] = []
    events: queue.Queue[str] = queue.Queue()
    TEST_ROOT.mkdir(parents=True, exist_ok=True)

    try:
        run_script = install_official_neoforge_server()
        prepare_mods()
        prepare_properties()
        import sys
        sys.path.insert(0, str(ROOT.parent / "qa"))
        from run_dh_chunky_runtime_smoke import run_chunky_pregen

        proc = subprocess.Popen(
            ["bash", str(run_script), "nogui"],
            cwd=TEST_ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )

        def read_output():
            assert proc is not None and proc.stdout is not None
            for line in proc.stdout:
                captured.append(line)
                print(line, end="", flush=True)
                events.put(line)

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()

        wait_for(events, proc, captured, "Done (", 900)
        print("PACKAGED_REALScale_NEOFORGE_SERVER_BOOT_OK", flush=True)

        sock = connect_rcon()
        try:
            packet_id = 2
            for x in (0, 16, 32, 48, 64, 80, 96, 112):
                response = command(sock, packet_id, f"forceload add {x} 0")
                packet_id += 1
                print("PACKAGED_SERVER_CHUNK_EVENT:", response, flush=True)
                time.sleep(0.35)

            if not any(HOOK_MARKER in line for line in captured):
                wait_for(events, proc, captured, HOOK_MARKER, 60)
            if not any(BOUND_MARKER in line for line in captured):
                wait_for(events, proc, captured, BOUND_MARKER, 120)

            packet_id = run_chunky_pregen(
                sock, packet_id, captured, "PACKAGED_JAR_DH_CHUNKY_PREGEN_PASS"
            )

            if any(CRASH_MARKER in line for line in captured):
                raise RuntimeError("The known ChunkyProvider initialization exception appeared")

            if proc.poll() is not None:
                raise RuntimeError("Packaged NeoForge server exited after DH listener binding")

            print("PACKAGED_JAR_DH_CHUNKY_NATIVE_BIND_OK", flush=True)
            print("PACKAGED_JAR_DH_CHUNKY_RUNTIME_PASS", flush=True)
            command(sock, packet_id, "stop")
            packet_id += 1
        finally:
            sock.close()

        proc.wait(timeout=40)
        print("PACKAGED_NEOFORGE_SERVER_SHUTDOWN_OK", flush=True)
        return 0
    except Exception as exc:
        print(f"PACKAGED_JAR_DH_CHUNKY_SMOKE_FAILED: {exc}", flush=True)
        print("".join(captured[-240:]), flush=True)
        return 1
    finally:
        stop_server(proc)
        TEST_ROOT.mkdir(parents=True, exist_ok=True)
        LOG_PATH.write_text("".join(captured), encoding="utf-8", errors="replace")


if __name__ == "__main__":
    raise SystemExit(main())
