import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from compare_perlin_three_runs import compare_three

def make(path,preset,values):
    path.write_text(json.dumps({"preset":preset,"strict_seconds_per_waypoint":180,
        "waypoints":[{"x":x,"z":z,"loaded_seconds":s,"saved":True,
        "unloaded_ticket":True}for (x,z),s in zip(
        ((1024,1024),(-2048,1536),(4096,-2048)),values)]}))
    return str(path)

class PerlinThreeRunTest(unittest.TestCase):
    def test_must_beat_both_controls(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            a=make(r/"a","p",(20,20,20))
            b=make(r/"b","p",(10,10,10))
            c=make(r/"c","p",(18,18,18))
            result=compare_three(a,b,c)
            self.assertTrue(result["candidate_faster_than_both"])
            self.assertAlmostEqual(result["conservative_gain_pct"],44.44)
    def test_drift_exposes_bad_candidate(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            a=make(r/"a","p",(30,30,30))
            b=make(r/"b","p",(28,28,28))
            c=make(r/"c","p",(22,22,22))
            result=compare_three(a,b,c)
            self.assertFalse(result["candidate_faster_than_both"])
            self.assertLess(result["conservative_gain_pct"],0)
    def test_wrong_preset_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            a=make(r/"a","p",(1,1,1))
            b=make(r/"b","q",(1,1,1))
            c=make(r/"c","p",(1,1,1))
            with self.assertRaises(ValueError):compare_three(a,b,c)

if __name__=="__main__":unittest.main()
