#!/usr/bin/env python3
"""Prevent USGS RCON overload and prove a ticket is not a completed chunk."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
import rcon_worldgen_smoke as worldgen


class FakeRcon:
    def __init__(self,replies):
        self.replies=iter(replies)
        self.commands=[]
    def send(self,cmd):
        self.commands.append(cmd)
        return next(self.replies)


class SerialUsgsTests(unittest.TestCase):
    def test_waits_until_actual_full_chunk_not_just_forceload_ticket(self):
        rcon=FakeRcon(["Test failed","Test failed","Seed: [785312]"])
        with patch.object(worldgen.time,"sleep",return_value=None):
            worldgen.await_full_chunk(rcon,-245,8,timeout_seconds=30)
        self.assertEqual(len(rcon.commands),3)
        self.assertEqual(rcon.commands[0],
                         "execute in minecraft:overworld if loaded -245 128 8 run seed")

    def test_rcon_must_not_accept_empty_success(self):
        rcon=FakeRcon([""])
        with patch.object(worldgen.time,"sleep",return_value=None),patch.object(
                worldgen.time,"monotonic",side_effect=[0,0,2]):
            with self.assertRaisesRegex(TimeoutError,"not fully loaded"):
                worldgen.await_full_chunk(rcon,240,0,timeout_seconds=1)
        self.assertEqual(len(rcon.commands),1,"An empty RCON response must actually be probed")

    def test_profile_forceloads_serially_with_flush_and_release(self):
        source=(Path(__file__).resolve().parent/"neoforge/rcon_worldgen_smoke.py").read_text()
        usgs=source.split('if profile=="usgs":',1)[1].split('elif profile=="distant":',1)[0]
        self.assertNotIn("forceload add -32 -32 32 32",usgs)
        self.assertIn("(-245,8),(-235,8)",usgs)
        self.assertIn("(233,8),(245,8)",usgs)
        self.assertIn("(8,-245),(8,-235)",usgs)
        self.assertIn("(8,233),(8,245)",usgs)
        self.assertIn("(-2352,8),(2352,8)",usgs)
        self.assertIn("(8,-896),(8,896)",usgs)
        self.assertIn("(8,-2352),(8,2352)",usgs)
        loop=source.split('if profile=="usgs":',2)[2].split('else:',1)[0]
        self.assertIn("await_full_chunk(rcon,x,z)",loop)
        self.assertIn('rcon.send("save-all flush")',loop)
        self.assertIn("forceload remove {x} {z}",loop)


if __name__=="__main__":
    unittest.main()
