#!/usr/bin/env python3
"""Persist and verify a real spawn chunk before stopping a disposable QA server.

A NeoForge "Done" message is not proof that a 2096-high spawn chunk has
reached FULL status or been saved. Do not invent a spawn when level.dat has
not initialized it; the later Anvil auditor remains the final authority.
"""
import gzip
from pathlib import Path

from realscale_headless_qa import read_nbt
from rcon_worldgen_smoke import Rcon, await_full_chunk


def saved_spawn(run_root, expected_settings):
    for level_file in sorted(Path(run_root).glob("**/level.dat")):
        if "DIM" in str(level_file):
            continue
        data = read_nbt(gzip.decompress(level_file.read_bytes()))
        data = data.get("Data", data)
        generator = (data.get("WorldGenSettings", {})
                     .get("dimensions", {}).get("minecraft:overworld", {})
                     .get("generator", {}))
        if generator.get("settings") != expected_settings:
            continue
        keys = ("SpawnX", "SpawnY", "SpawnZ")
        if not all(isinstance(data.get(k), int) for k in keys):
            raise RuntimeError("Matching level.dat lacks numeric world-spawn coordinates")
        return tuple(data[k] for k in keys), level_file
    raise RuntimeError("No saved level.dat matches worldgen settings " + expected_settings)


def ensure_saved_spawn_chunk(run_root, expected_settings):
    rcon = Rcon(timeout=180)
    try:
        # Flush metadata first: a freshly created level.dat can contain a
        # provisional (0,0,0) spawn until the engine persists the real spawn.
        rcon.send("save-all flush")
        (x, y, z), level_file = saved_spawn(run_root, expected_settings)
        print("QA_SAVED_SPAWN:", (x, y, z), "from", level_file, flush=True)
        if (x, y, z) == (0, 0, 0):
            raise RuntimeError(
                "World spawn remains uninitialized at (0,0,0) after save-all; "
                "do not certify this world as safe"
            )
        # Exactly one chunk: avoid allocating a large 2096-high chunk square.
        rcon.send(f"execute in minecraft:overworld run forceload add {x} {z}")
        await_full_chunk(rcon, x, z, timeout_seconds=240)
        rcon.send("save-all flush")
        rcon.send(f"execute in minecraft:overworld run forceload remove {x} {z}")
        print("QA_SPAWN_CHUNK_FLUSHED:", (x, z), flush=True)
    finally:
        rcon.close()
