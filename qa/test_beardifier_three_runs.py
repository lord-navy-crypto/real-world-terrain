import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from compare_beardifier_three_runs import three_run

def make(path,preset,seconds):
    content={"preset":preset,"strict_seconds_per_waypoint":180,
        "waypoints":[{"x":x,"z":z,"loaded_seconds":sec,
            "saved":True,"unloaded_ticket":True}
            for (x,z),sec in zip(((1024,1024),(-2048,1536),(4096,-2048)),seconds)]}
    path.write_text(json.dumps(content))
    return str(path)

class TriRunBenchmark(unittest.TestCase):
    def test_comparison_against_both_reference_servers(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            a=make(root/"vanilla.json","realscale:tva_sacred_timeline_three_realms",(30,20,40))
            b=make(root/"candidate.json","realscale:tva_sacred_timeline_three_realms",(25,16,31))
            c=make(root/"control.json","realscale:tva_sacred_timeline_three_realms",(28,18,39))
            result=three_run(a,b,c)
            self.assertEqual(result["vanilla_first_total_seconds"],90)
            self.assertEqual(result["vanilla_control_total_seconds"],85)
            self.assertEqual(result["experimental_total_seconds"],72)
            self.assertAlmostEqual(result["conservative_speedup_vs_faster_vanilla_pct"],15.29)
            self.assertTrue(result["candidate_faster_than_both_controls"])
    def test_candidate_faster_than_first_but_slower_than_control_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            a=make(root/"vanilla.json","p",(35,35,30))
            b=make(root/"candidate.json","p",(30,30,30))
            c=make(root/"control.json","p",(25,25,25))
            result=three_run(a,b,c)
            self.assertEqual(result["speedup_vs_first_vanilla_pct"],10)
            self.assertLess(result["conservative_speedup_vs_faster_vanilla_pct"],0)
            self.assertFalse(result["candidate_faster_than_both_controls"])
    def test_mixed_presets_cannot_be_compared(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            a=make(root/"a","sacred",(1,1,1))
            b=make(root/"b","other",(1,1,1))
            c=make(root/"c","sacred",(1,1,1))
            with self.assertRaises(ValueError):
                three_run(a,b,c)

if __name__=="__main__":unittest.main()
