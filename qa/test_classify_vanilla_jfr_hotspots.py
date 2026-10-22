import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from classify_vanilla_jfr_hotspots import analyze,family

def frame(owner,method):
    return {"method":{"type":{"name":owner},"name":method}}

def event(owners):
    return {"type":"jdk.ExecutionSample","values":{
        "stackTrace":{"frames":[frame(owner,name) for owner,name in owners]}}}

class VanillaJfrHotspotClasses(unittest.TestCase):
    def test_jvm_slash_and_nested_aquifer(self):
        self.assertEqual(
            family("net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer.computeSubstance"),
            "aquifer")
    def test_partition_and_overlapping_inclusive(self):
        samples=[]
        for _ in range(5):
            samples.append(event([
                ("net/minecraft/world/level/levelgen/synth/ImprovedNoise","p"),
                ("net/minecraft/world/level/levelgen/synth/PerlinNoise","getValue"),
                ("net/minecraft/world/level/levelgen/Beardifier","compute")]))
        for _ in range(3):
            samples.append(event([
                ("net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer",
                 "computeSubstance")]))
        for _ in range(2):
            samples.append(event([
                ("net/minecraft/world/level/levelgen/Beardifier","compute")]))
        report=analyze(samples)
        counts=report["exclusive_leaf_families"]
        self.assertEqual(report["execution_samples"],10)
        self.assertEqual(counts["perlin_improved_noise"]["samples"],5)
        self.assertEqual(counts["aquifer"]["samples"],3)
        self.assertEqual(counts["beardifier"]["samples"],2)
        self.assertEqual(sum(a["samples"] for a in counts.values()),10)
        self.assertEqual(report["inclusive_stack_presence"]["beardifier"],7)
    def test_too_small_rejected(self):
        with self.assertRaises(ValueError):analyze([])

if __name__=="__main__":unittest.main()
