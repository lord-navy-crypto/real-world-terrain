#!/usr/bin/env python3
"""Contract tests: stalled chunk is not automatically a rendering problem."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from diagnose_worldgen_threads import parse_threads,diagnose_dumps

SAMPLE_1='''2026-10-18 14:00:00
"Server thread" #42 prio=5 os_prio=0 tid=0x00 nid=42 WAITING
   java.lang.Thread.State: WAITING (parking)
        at java.base/java.util.concurrent.CompletableFuture.join(CompletableFuture.java:2117)
        at net.minecraft.server.level.ServerChunkCache.getChunk(ServerChunkCache.java:120)

"Worker-Main-3" #57 prio=5 os_prio=0 tid=0x01 nid=57 RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at org.realscale.neoforge.MountainReliefDensity.compute(MountainReliefDensity.java:62)
        at net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.getBaseHeight(NoiseBasedChunkGenerator.java:320)
        at net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.addPieces(JigsawPlacement.java:83)

"RCON Listener #1" #81 WAITING
   java.lang.Thread.State: WAITING
        at java.base/java.net.SocketInputStream.read(SocketInputStream.java:10)
'''
SAMPLE_2='''"Server thread" #42 WAITING
   java.lang.Thread.State: WAITING
        at java.base/java.util.concurrent.CompletableFuture.join(CompletableFuture.java:2117)

"Worker-Main-3" #57 RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at org.realscale.neoforge.MountainReliefDensity.compute(MountainReliefDensity.java:62)
        at net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.getBaseHeight(NoiseBasedChunkGenerator.java:320)
        at net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.addPieces(JigsawPlacement.java:83)
'''
class ThreadDiagnosticsTest(unittest.TestCase):
    def test_parser(self):
        got=parse_threads(SAMPLE_1)
        self.assertEqual(len(got),3)
        self.assertEqual(got[0]["name"],"Server thread")
        self.assertEqual(got[1]["state"],"RUNNABLE")
        self.assertEqual(len(got[1]["frames"]),3)

    def test_repeated_worldgen_stack_and_future_wait(self):
        result=diagnose_dumps([SAMPLE_1,SAMPLE_2])
        self.assertEqual(result["samples"],2)
        self.assertEqual(result["marker_observations"]["height_query"],2)
        self.assertEqual(result["marker_observations"]["structure_jigsaw"],2)
        self.assertEqual(result["marker_observations"]["realscale_density"],2)
        self.assertEqual(result["marker_observations"]["chunk_future_wait"],2)
        self.assertIn("hypotheses_not_proven_causes",result)
        self.assertGreaterEqual(len(result["sampled_threads"]),4)

    def test_empty_data_and_ordinary_threads_are_handled(self):
        with self.assertRaisesRegex(ValueError,"No thread snapshots"):
            diagnose_dumps([])
        with self.assertRaisesRegex(ValueError,"No Java thread headers"):
            diagnose_dumps(["invalid"])
        data=diagnose_dumps(['''"Render thread" #21 RUNNABLE
java.lang.Thread.State: RUNNABLE
at example.Render.draw(Render.java:1)
"Server thread" #4 RUNNABLE
java.lang.Thread.State: RUNNABLE
at net.minecraft.server.MinecraftServer.tick(MinecraftServer.java:1)'''])
        self.assertEqual(data["matched_thread_observations"],1)
        self.assertEqual(data["marker_observations"],{})

if __name__=="__main__":
    unittest.main()
