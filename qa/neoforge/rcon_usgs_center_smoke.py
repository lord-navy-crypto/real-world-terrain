#!/usr/bin/env python3
"""Boot NeoForge and persist the central USGS DEM sample using authenticated RCON.

The Gradle runServer task does not reliably forward stdin to Minecraft's
console. RCON provides explicit command acknowledgements and lets this gate
distinguish a loaded chunk from a mere forceload ticket.
"""
from __future__ import annotations

import gzip
import pathlib
import signal
import subprocess
import sys
import threading
import time

from rcon_worldgen_smoke import Rcon

EXPECTED_PRESET = b"realscale:usgs_big_sur"
CENTER_CHUNK_SAMPLES = ((0, 0), (-56, -56), (56, -56), (-56, 56), (56, 56))


def main(root: pathlib.Path) -> None:
    log = root / "build" / "neoforge-server-smoke.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    proc = subprocess.Popen(
        ["gradle", "--no-daemon", "runServer", "--stacktrace"],
        cwd=root,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        start_new_session=True,
    )
    lines: list[str] = []
    ready = threading.Event()

    def drain() -> None:
        assert proc.stdout is not None
        for line in proc.stdout:
            lines.append(line)
            if "Done (" in line and "For help" in line:
                ready.set()

    reader = threading.Thread(target=drain, daemon=True)
    reader.start()
    rcon = None
    failure: Exception | None = None
    try:
        deadline = time.monotonic() + 570
        while not ready.is_set() and proc.poll() is None and time.monotonic() < deadline:
            time.sleep(1)
        if not ready.is_set():
            raise RuntimeError(
                "NeoForge server failed to reach Done; exit=" + str(proc.poll())
            )
        print("NEOFORGE_SERVER_BOOT_OK: reached Done milestone", flush=True)

        last_error = None
        for _ in range(30):
            try:
                rcon = Rcon(timeout=180)
                break
            except (ConnectionError, OSError, TimeoutError) as exc:
                last_error = exc
                time.sleep(1)
        if rcon is None:
            raise RuntimeError("Could not authenticate to Minecraft RCON: " + repr(last_error))

        reply = rcon.send(
            "execute in minecraft:overworld run forceload add -64 -64 63 63"
        )
        if "marked" not in reply.lower() and "already" not in reply.lower():
            raise RuntimeError("Central USGS forceload was not acknowledged: " + reply)
        print("USGS_DEM_CENTER_FORCELOAD_ACK:", reply, flush=True)

        # The DEM audit samples the entire central 8x8-chunk square.
        # Wait for every target chunk, not only the four corners: a forceload
        # acknowledgement alone is not proof that generation has completed.
        pending = [
            (chunk_x * 16 + 8, chunk_z * 16 + 8)
            for chunk_z in range(-4, 4)
            for chunk_x in range(-4, 4)
        ]
        deadline = time.monotonic() + 240
        while pending:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(
                    "USGS center sample chunks were not all generated: "
                    + repr(pending[:12]) + f" (remaining={len(pending)})"
                )
            next_pending = []
            for x, z in pending:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    next_pending.append((x, z))
                    continue
                rcon.sock.settimeout(min(15.0, remaining))
                try:
                    sample_reply = rcon.send(
                        f"execute in minecraft:overworld if loaded {x} 128 {z} run seed"
                    )
                except TimeoutError:
                    next_pending.append((x, z))
                    continue
                if "Seed:" in sample_reply:
                    print("USGS_CENTER_CHUNK_READY:", x, z, flush=True)
                else:
                    next_pending.append((x, z))
            pending = next_pending
            if pending:
                time.sleep(1.0)

        save_reply = rcon.send("save-all flush")
        print("USGS_CENTER_SAVE_ACK:", save_reply, flush=True)
        stop_reply = rcon.send("stop")
        print("USGS_SERVER_STOP_ACK:", stop_reply, flush=True)
        try:
            proc.wait(timeout=120)
        except subprocess.TimeoutExpired:
            raise RuntimeError("NeoForge did not stop cleanly after RCON stop")
    except Exception as exc:
        failure = exc
        print("USGS_RCON_QA_FAILURE:", repr(exc), flush=True)
    finally:
        if rcon is not None:
            try:
                rcon.close()
            except OSError:
                pass
        if proc.poll() is None:
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os_kill = getattr(signal, "SIGINT")
                try:
                    import os
                    os.killpg(proc.pid, os_kill)
                    proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    import os
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait()
        reader.join(timeout=3)
        log.write_text("".join(lines), encoding="utf-8")
        text = log.read_text(encoding="utf-8")
        print("SERVER_LOG_TAIL:", text[-8000:], flush=True)
        print("NEOFORGE_DONE:", ready.is_set(), "LOG_LINES:", len(lines), flush=True)

    worlds = list((root / "run").glob("**/level.dat"))
    valid = False
    for path in worlds:
        try:
            data = gzip.decompress(path.read_bytes())
            if EXPECTED_PRESET in data:
                valid = True
                print("CORRECT_USGS_GENERATOR_IN_LEVEL_DAT:", path, flush=True)
        except Exception as exc:
            print("LEVEL_DAT_READ_FAILURE:", str(path), repr(exc), flush=True)
    if failure is not None:
        raise SystemExit("USGS NeoForge RCON smoke failed: " + repr(failure))
    if not ready.is_set() or not valid:
        raise SystemExit("USGS NeoForge worldgen was not verified in saved level.dat")
    print("USGS_NEOFORGE_RCON_WORLDGEN_SMOKE_PASS", flush=True)


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else pathlib.Path.cwd())
