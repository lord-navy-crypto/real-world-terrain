#!/usr/bin/env python3
"""Ensure paired far-route results are valid complete 180s RCON measurements."""
import json
import pathlib
import sys
import tempfile
import unittest
ROOT=pathlib.Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/"neoforge"))
from compare_paired_far_route import compare

class PairedComparisonTests(unittest.TestCase):
    def test_strict_same_coordinates_and_complete_saves(self):
        with tempfile.TemporaryDirectory() as d:
            base=pathlib.Path(d)
            expected={"preset":"realscale:tva_sacred_timeline_three_realms",
                "strict_seconds_per_waypoint":180,
                "waypoints":[{"x":x,"z":z,"loaded_seconds":sec,
                              "saved":True,"unloaded_ticket":True}
                    for x,z,sec in [(1024,1024,60),(-2048,1536,20),(4096,-2048,40)]]}
            candidate=json.loads(json.dumps(expected))
            candidate["waypoints"][0]["loaded_seconds"]=30
            a,b=base/"old.json",base/"new.json"
            a.write_text(json.dumps(expected));b.write_text(json.dumps(candidate))
            result=compare(a,b)
            self.assertAlmostEqual(result["total_speedup_percent"],25.0)
            candidate["waypoints"][1]["saved"]=False
            b.write_text(json.dumps(candidate))
            with self.assertRaises(ValueError):compare(a,b)
            candidate["waypoints"][1]["saved"]=True
            candidate["waypoints"][1]["z"]+=1
            b.write_text(json.dumps(candidate))
            with self.assertRaises(ValueError):compare(a,b)
if __name__=="__main__":unittest.main()
