#!/usr/bin/env python3
"""Boot NeoForge and verify licensed/original architecture through local RCON."""
from __future__ import annotations
import queue
import socket
import struct
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path.cwd()
LOG_PATH = ROOT / "run/architecture-pilot-server-smoke.log"


def prepare_nbt_diagnostics():
    """Create isolated one-block templates to pinpoint NBT/state failures."""
    sys.path.insert(0, str(ROOT.parent / "scripts"))
    from generate_original_architecture import Building, build_forest_ranger_lodge

    pack = ROOT / "run/architecture-58-smoke/datapacks/realscale_nbt_diagnostics"
    (pack / "data/realscale/structure/diagnostics").mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(
        '{"pack":{"pack_format":48,"description":"Temporary RealScale NBT diagnostics"}}',
        encoding="utf-8",
    )

    tiny = Building("tiny_stone", (3, 3, 3), "Temporary one-block NBT diagnostic.")
    tiny.set(1, 1, 1, "minecraft:stone")
    tiny.write(pack / "data/realscale/structure/diagnostics/tiny_stone.nbt")

    large = Building("large_single_stone", (13, 10, 13), "Temporary large one-block NBT diagnostic.")
    large.set(0, 0, 0, "minecraft:stone")
    large.write(pack / "data/realscale/structure/diagnostics/large_single_stone.nbt")

    forest = build_forest_ranger_lodge()
    states = sorted({state for state in forest.blocks.values() if state[0] != "minecraft:air"})
    ids = []
    for index, block_state in enumerate(states):
        name = f"state_{index:02d}"
        probe = Building(name, (1, 1, 1), f"Temporary block-state diagnostic: {block_state[0]}.")
        probe.set(0, 0, 0, block_state)
        probe.write(pack / f"data/realscale/structure/diagnostics/{name}.nbt")
        ids.append(name)
    print("NBT_DIAGNOSTIC_TEMPLATE_IDS:", ["tiny_stone", "large_single_stone", *ids], flush=True)
    return ids
RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASSWORD = "architecture-smoke-local-only"
TEMPLATES = (
    "modern_house", "glass_greenhouse", "city_library",
    "forest_ranger_lodge", "plains_farmstead", "alpine_waystation",
    "coastal_lighthouse", "desert_caravanserai",
    "woodland_cottage", "village_blacksmith", "schoolhouse",
    "healers_clinic", "brick_bakery", "two_storey_townhouse",
    "snowfield_shelter", "savanna_outpost", "marsh_stilt_house",
    "mesa_trading_post", "market_hall", "railway_station", "windmill",
    "fishing_hut", "coastal_pier", "forest_watchtower",
    "botanical_greenhouse", "orchard_farm", "stone_bridge", "watermill",
    "desert_well", "mountain_observatory", "ruined_watchpost",
    "garden_pavilion", "lumber_camp", "woodland_hamlet",
    "plains_market_square", "river_trade_hamlet",
    "alpine_watch_compound", "desert_oasis_hamlet",
    "stone_mason_workshop", "canal_lock", "monastery_chapel",
    "apiary_garden", "snowbound_storehouse", "desert_cistern",
    "tropical_boardwalk_house", "railway_signal_box", "quarry_shed",
    "charcoal_kiln", "vineyard_estate", "sheepfold_ranch",
    "charcoal_burners_camp", "fishing_village_compound", "hilltop_manor",
    "frontier_fort", "railway_depot", "orchard_cider_estate",
    "riverside_sawmill", "marsh_reed_hamlet",
)

def fail(message: str, proc=None, lines=None):
    if proc is not None and proc.poll() is None:
        try:
            if proc.stdin and not proc.stdin.closed:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                proc.wait(timeout=20)
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

def recv_exact(sock, count):
    data = bytearray()
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data.extend(chunk)
    return bytes(data)

def send_packet(sock, packet_id, packet_type, payload):
    body = struct.pack("<ii", packet_id, packet_type) + payload.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)

def recv_packet(sock):
    length = struct.unpack("<i", recv_exact(sock, 4))[0]
    body = recv_exact(sock, length)
    packet_id, packet_type = struct.unpack("<ii", body[:8])
    return packet_id, packet_type, body[8:-2].decode("utf-8", errors="replace")

def connect_rcon(timeout=45):
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

def rcon_command(sock, packet_id, command):
    send_packet(sock, packet_id, 2, command)
    response_id, _response_type, response = recv_packet(sock)
    if response_id != packet_id:
        raise RuntimeError(f"Unexpected RCON response id {response_id}: {response}")
    return response


def wait_for_test_bay_chunks(sock, packet_id, origin_x, origin_z=0, y=160, timeout=45):
    """Wait for every chunk in a 64x64 test bay to reach entity-ticking status."""
    pending = [
        (origin_x + 8 + dx * 16, origin_z + 8 + dz * 16)
        for dz in range(4)
        for dx in range(4)
    ]
    deadline = time.monotonic() + timeout
    while pending and time.monotonic() < deadline:
        still_pending = []
        for chunk_x, chunk_z in pending:
            probe = (
                f"execute if loaded {chunk_x} {y} {chunk_z} "
                "run time query daytime"
            )
            response = rcon_command(sock, packet_id, probe)
            packet_id += 1
            if "The time is" not in response:
                still_pending.append((chunk_x, chunk_z))
        pending = still_pending
        if pending:
            time.sleep(0.25)
    if pending:
        raise TimeoutError(
            f"Test bay chunks did not reach entity-ticking status: {pending}"
        )
    return packet_id


diagnostic_state_ids = prepare_nbt_diagnostics()

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

try:
    sock = connect_rcon()
    print("NEOFORGE_ARCHITECTURE_RCON_READY", flush=True)

    pack_id = "mod/realscale:resourcepacks/realscale_structures_architecture_pilot"
    available = rcon_command(sock, 2, "datapack list available")
    active_before = rcon_command(sock, 3, "datapack list enabled")
    print("RCON available packs:", available, flush=True)
    print("RCON enabled packs before selection:", active_before, flush=True)
    if pack_id not in available and pack_id not in active_before:
        raise RuntimeError(f"Optional architecture data pack was not discovered: {pack_id}")
    if pack_id in active_before:
        active = active_before
        print("Architecture pack was already enabled by the isolated test world.", flush=True)
    else:
        # Enabling a data pack performs a full resource reload; allow that
        # reload to complete before the RCON response is expected.
        sock.settimeout(90)
        enabled = rcon_command(sock, 4, f"datapack enable {pack_id}")
        sock.settimeout(8)
        print("RCON enable architecture pack:", enabled, flush=True)
        if "Enabled" not in enabled and "already enabled" not in enabled.lower():
            raise RuntimeError(f"Architecture data pack could not be enabled: {enabled}")
        active = rcon_command(sock, 5, "datapack list enabled")
    print("RCON enabled packs:", active, flush=True)
    if pack_id not in active:
        raise RuntimeError("Architecture data pack did not remain enabled after reload")

    command_id = 6
    for index, template in enumerate(TEMPLATES):
        # forceload uses two-dimensional block positions (x z), not x y z.
        # Load the complete 64x64 test bay before attempting placement so the
        # command cannot fail merely because its destination chunks are absent.
        x = index * 64
        region = f"{x} 0 {x + 63} 63"
        ticket = rcon_command(sock, command_id, f"forceload add {region}")
        print("RCON force-load:", ticket, flush=True)
        if "Marked" not in ticket and "already" not in ticket.lower():
            raise RuntimeError(f"Could not force-load the test bay for {template}: {ticket}")
        command_id += 1
        command_id = wait_for_test_bay_chunks(sock, command_id, x, 0)
        print("RCON: all 16 test-bay chunks are entity-ticking.", flush=True)

        if index == 0:
            # The vanilla baseline is already known to work. Probe a tiny and a
            # full-size one-block template, then every distinct non-air state
            # from the failing original build, before placing the full template.
            diagnostic_pack = "file/realscale_nbt_diagnostics"
            enabled_diag = rcon_command(sock, command_id, f"datapack list enabled")
            if diagnostic_pack not in enabled_diag:
                raise RuntimeError(f"NBT diagnostic pack was not loaded: {enabled_diag}")
            command_id += 1

            for diagnostic, px, py, pz in (
                ("tiny_stone", x + 32, 280, 48),
                ("large_single_stone", x + 16, 260, 48),
            ):
                probe = rcon_command(
                    sock, command_id,
                    f"place template realscale:diagnostics/{diagnostic} {px} {py} {pz}"
                )
                print(f"RCON NBT diagnostic {diagnostic}:", probe, flush=True)
                if f'Loaded template "realscale:diagnostics/{diagnostic}" at' not in probe:
                    raise RuntimeError(f"NBT diagnostic {diagnostic} failed: {probe}")
                command_id += 1

            for state_index, diagnostic in enumerate(diagnostic_state_ids):
                dx = (state_index % 4) * 16
                dz = (state_index // 4) * 16
                probe = rcon_command(
                    sock, command_id,
                    f"place template realscale:diagnostics/{diagnostic} {x + dx} 220 {dz}"
                )
                print(f"RCON NBT diagnostic {diagnostic}:", probe, flush=True)
                if f'Loaded template "realscale:diagnostics/{diagnostic}" at' not in probe:
                    raise RuntimeError(f"NBT block-state diagnostic {diagnostic} failed: {probe}")
                command_id += 1

            original_probe = rcon_command(
                sock, command_id,
                f"place template realscale:architecture/forest_ranger_lodge {x} 160 0"
            )
            print("RCON original-template baseline:", original_probe, flush=True)
            if 'Loaded template "realscale:architecture/forest_ranger_lodge" at' not in original_probe:
                raise RuntimeError(f"Original NBT template baseline failed: {original_probe}")
            command_id += 1

        command = f"place template realscale:architecture/{template} {x} 160 0"
        print(f">>> {command}", flush=True)
        response = rcon_command(sock, command_id, command)
        expected = f'Loaded template "realscale:architecture/{template}" at'
        print("RCON:", response, flush=True)
        if expected not in response:
            raise RuntimeError(f"Template placement did not succeed for {template}: {response}")
        command_id += 1

        released = rcon_command(sock, command_id, f"forceload remove {region}")
        print("RCON release:", released, flush=True)
        command_id += 1

    save_id = command_id
    print("RCON:", rcon_command(sock, save_id, "save-all flush"), flush=True)
    time.sleep(2)
    try:
        rcon_command(sock, save_id + 2, "stop")
    except Exception:
        # The server may close RCON immediately after accepting stop.
        pass
    sock.close()
except Exception as exc:
    try:
        if "sock" in locals():
            sock.close()
    except Exception:
        pass
    fail(f"Architecture RCON verification failed: {exc}", proc, captured)

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

for bad in (
    "Failed to place template", "There is no template with ID",
    "Unknown registry key", "Couldn't parse data file", "Unknown block",
):
    if bad in captured_text:
        fail(f"Architecture pilot server reported an error: {bad}", None, captured)

print("REAL_SCALE_ARCHITECTURE_TEMPLATE_PLACEMENT_OK: "
      f"all {len(TEMPLATES)} licensed/original templates loaded and placed")
