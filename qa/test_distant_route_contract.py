#!/usr/bin/env python3
"""Guard sequential far route QA from false-positive chunk-ticket ACK tests."""
import ast
import unittest
from pathlib import Path

SOURCE=Path(__file__).resolve().parent/"neoforge"/"rcon_worldgen_smoke.py"
TREE=ast.parse(SOURCE.read_text(encoding="utf-8"))

class RouteContract(unittest.TestCase):
    def test_three_distant_waypoints_include_negative_and_positive(self):
        sites=None
        for n in TREE.body:
            if isinstance(n,ast.Assign):
                if any(isinstance(t,ast.Name) and t.id=="FAR_ROUTE_SITES" for t in n.targets):
                    sites=ast.literal_eval(n.value)
        self.assertIsNotNone(sites)
        self.assertEqual(len(sites),3)
        self.assertEqual(len(set(sites)),3)
        self.assertTrue(any(x<0 for x,z in sites))
        self.assertTrue(any(x>0 for x,z in sites))
        self.assertTrue(all(abs(x)>=1024 or abs(z)>=1024 for x,z in sites))

    def test_route_observes_full_chunk_and_saves_before_next_waypoint(self):
        src=SOURCE.read_text(encoding="utf-8")
        start=src.index('            elif profile in ("distant_route","distant_refresh"):\n                results=[]')
        end=src.index('            else:\n                for command in commands:',start)
        route=src[start:end]
        self.assertIn("for x,z in FAR_ROUTE_SITES:",route)
        self.assertIn("remaining=180.0-",route)
        self.assertIn("await_full_chunk(rcon,x,z,timeout_seconds=remaining)",route)
        self.assertLess(route.index('await_full_chunk(rcon,x,z'),
                        route.index('rcon.send("save-all flush")'))
        self.assertLess(route.index('rcon.send("save-all flush")'),
                        route.index("forceload remove"))
        self.assertIn("result_file.write_text",route)
        self.assertIn("len(results)!=len(FAR_ROUTE_SITES)",route)

if __name__=="__main__":
    unittest.main()
