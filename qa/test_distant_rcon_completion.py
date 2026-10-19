#!/usr/bin/env python3
"""No false-green far worldgen: a forceload ACK is NOT a finished chunk."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"qa"/"neoforge"))
from rcon_worldgen_smoke import await_full_chunk

class FakeRcon:
    def __init__(self, replies):
        self.replies=iter(replies)
        self.calls=[]
    def send(self, cmd):
        self.calls.append(cmd)
        return next(self.replies)

class FarChunkCompletionGate(unittest.TestCase):
    def test_loaded_chunk_ack_succeeds(self):
        fake=FakeRcon(["Seed: [2]"])
        await_full_chunk(fake,1024,1024,timeout_seconds=1)
        self.assertEqual(fake.calls,[
            "execute in minecraft:overworld if loaded 1024 128 1024 run seed"
        ])

    def test_forceload_ticket_only_never_counts_as_loaded(self):
        fake=FakeRcon(["Marked chunk [64, 64] in Overworld to be force loaded"])
        with patch("rcon_worldgen_smoke.time.monotonic",side_effect=[0.0,1.0]):
            with self.assertRaisesRegex(TimeoutError,"not fully loaded"):
                await_full_chunk(fake,1024,1024,timeout_seconds=0.25)
        self.assertFalse(fake.calls)

    def test_status_rcon_receive_is_capped_by_remaining_budget(self):
        class FakeSock:
            def __init__(self):
                self.timeout=180.0
                self.changes=[]
            def gettimeout(self):
                return self.timeout
            def settimeout(self,seconds):
                self.timeout=seconds
                self.changes.append(seconds)
        fake=FakeRcon(["Seed: [2]"])
        fake.sock=FakeSock()
        await_full_chunk(fake,1024,1024,timeout_seconds=1)
        self.assertGreater(len(fake.sock.changes),1)
        self.assertGreater(fake.sock.changes[0],0)
        self.assertLessEqual(fake.sock.changes[0],1)
        self.assertEqual(fake.sock.changes[-1],180.0)

    def test_distant_profile_requires_full_loaded_and_shared_180s_budget(self):
        src=(ROOT/"qa/neoforge/rcon_worldgen_smoke.py").read_text()
        # Two branches use this label: the earlier branch selects
        # commands and the LAST branch verifies their completion.
        distant=src.rsplit('elif profile=="distant":',1)[-1]
        self.assertIn('await_full_chunk(rcon,1024,1024,',distant)
        self.assertIn('remaining=180.0-(time.monotonic()-budget_start)',distant)
        self.assertIn("QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS",distant)
        self.assertNotIn("timeout_seconds=240",distant.split(
            'QA_DISTANT_CHUNK_FULLY_LOADED_WITHIN_180_SECONDS')[0])

if __name__=="__main__":
    unittest.main()
