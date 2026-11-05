"""Ensure experimental world's game server, not Gradle stdin, is stopped."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=(ROOT/"qa/neoforge/geo_wonders_optin_boot.py").read_text()
class GeoWondersRconShutdownTest(unittest.TestCase):
    def test_server_has_authenticated_rcon_and_flushes_before_stop(self):
        self.assertIn('"enable-rcon=true',SRC)
        self.assertIn('"rcon.password=realscale_ci_probe_only',SRC)
        self.assertIn("rcon=Rcon(timeout=90)",SRC)
        self.assertLess(SRC.index('rcon.send("save-all flush")'),
                        SRC.index('rcon.send("stop")'))
        self.assertIn("GEO_WONDERS_RCON_SAVE_ACK",SRC)
        self.assertNotIn('p.stdin.write("stop',SRC)
        self.assertIn("level.dat",SRC)
        self.assertIn('b"realscale:geo_wonders_opt_in"',SRC)
if __name__=="__main__":unittest.main()
