import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from summarize_worldgen_jfr import summarize_events
def sample(methods):
    return {"type":"jdk.ExecutionSample","values":{"stackTrace":{"frames":[
        {"method":{"type":{"name":owner},"name":member}}
        for owner,member in methods]}}}
class JfrTests(unittest.TestCase):
    def test_leaf_is_not_counted_as_inclusive_cpu_time(self):
        world="org.realscale.neoforge.GrandOverworldCaverns"
        vanilla="net.minecraft.world.level.levelgen.NoiseChunk"
        events=[sample([(vanilla,"compute"),(world,"compute")]) for _ in range(6)]
        events.extend(sample([("java.lang.Thread","run")]) for _ in range(5))
        report=summarize_events(events)
        self.assertEqual(report["execution_samples"],11)
        self.assertEqual(report["stacks_with_minecraft_or_realscale"],6)
        self.assertEqual(report["nearest_relevant_stack_category"]
                         ["Minecraft terrain and structure generation"],6)
        self.assertEqual(report["leaf_methods"][0]["samples"],6)
        self.assertEqual(len(report["inclusive_worldgen_methods"]),2)
    def test_jvm_slash_names_are_normalized_before_hotspot_classification(self):
        # Faithful to a REAL JFR JSON recording: Java bytecode owner names
        # use net/minecraft/..., not a Python dotted package spelling.
        vanilla="net/minecraft/world/level/levelgen/Beardifier"
        cave="org/realscale/neoforge/GrandOverworldCaverns"
        events=[sample([(vanilla,"compute"),(cave,"compute")]) for _ in range(12)]
        report=summarize_events(events)
        self.assertEqual(report["execution_samples"],12)
        self.assertEqual(report["stacks_with_minecraft_or_realscale"],12)
        self.assertEqual(report["nearest_relevant_stack_category"]
                         ["Minecraft terrain and structure generation"],12)
        self.assertEqual(report["leaf_methods"][0]["method"],
                         "net.minecraft.world.level.levelgen.Beardifier.compute")
        self.assertTrue(any(r["method"]=="org.realscale.neoforge.GrandOverworldCaverns.compute"
                            and r["samples"]==12 for r in report["inclusive_worldgen_methods"]))

    def test_many_samples_must_include_genuine_game_call_stacks(self):
        with self.assertRaisesRegex(ValueError,"implausibly few game frames"):
            summarize_events([sample([("java.lang.Thread","run")])
                              for _ in range(200)])

    def test_minimum_samples(self):
        with self.assertRaises(ValueError):
            summarize_events([sample([("java.lang.Thread","run")])])
if __name__=="__main__":unittest.main()
