#!/usr/bin/env python3
"""The live-server spawn check must never fabricate or silently skip a spawn."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent / "neoforge"))
import ensure_spawn_chunk as probe


class FakeRcon:
    def __init__(self):
        self.commands = []
        self.closed = False

    def send(self, command):
        self.commands.append(command)
        return "OK"

    def close(self):
        self.closed = True


class SpawnChunkProbeTests(unittest.TestCase):
    def test_flushes_actual_spawn_one_chunk_at_a_time(self):
        fake = FakeRcon()
        with patch.object(probe, "Rcon", return_value=fake), \
             patch.object(probe, "saved_spawn", return_value=((-128, 579, -112), Path("run/world/level.dat"))), \
             patch.object(probe, "await_full_chunk") as await_full:
            probe.ensure_saved_spawn_chunk(Path("run"), "realscale:realism_max")
        self.assertEqual(fake.commands, [
            "save-all flush",
            "execute in minecraft:overworld run forceload add -128 -112",
            "save-all flush",
            "execute in minecraft:overworld run forceload remove -128 -112",
        ])
        await_full.assert_called_once_with(fake, -128, -112, timeout_seconds=240)
        self.assertTrue(fake.closed)

    def test_rejects_uninitialized_spawn_instead_of_falsely_passing(self):
        fake = FakeRcon()
        with patch.object(probe, "Rcon", return_value=fake), \
             patch.object(probe, "saved_spawn", return_value=((0, 0, 0), Path("run/world/level.dat"))):
            with self.assertRaisesRegex(RuntimeError, "uninitialized"):
                probe.ensure_saved_spawn_chunk(Path("run"), "realscale:realism_max")
        self.assertEqual(fake.commands, ["save-all flush"])
        self.assertTrue(fake.closed)

    def test_correct_world_settings_are_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "world"
            p.mkdir()
            (p / "level.dat").write_bytes(b"placeholder")
            with patch.object(probe.gzip, "decompress", return_value=b"nbt"), \
                 patch.object(probe, "read_nbt", return_value={"Data": {
                     "SpawnX": 16, "SpawnY": 65, "SpawnZ": 16,
                     "WorldGenSettings": {"dimensions": {"minecraft:overworld": {
                         "generator": {"settings": "realscale:realism_max"}}}}}}):
                with self.assertRaisesRegex(RuntimeError, "No saved level.dat matches"):
                    probe.saved_spawn(Path(tmp), "realscale:lab_not_real")
