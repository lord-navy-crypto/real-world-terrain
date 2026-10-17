"""Source QA for historical PR25 real world boot and named presets."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SCRIPT=(ROOT/"qa/neoforge/pr25_climate_world_boot.py").read_text()
WORKFLOW=(ROOT/".github/workflows/pr25-climate-worlds.yml").read_text()
class PR25LiveSmokeDesign(unittest.TestCase):
    def test_all_four_new_world_families_are_built(self):
        for name in ("natural","alpine","canyon","measured"):
            self.assertIn('"'+name+'":("tva_pr25_'+name+'_three_realms"',SCRIPT)
            self.assertIn(name,WORKFLOW)
        self.assertIn("Rcon(timeout=90)",SCRIPT)
        self.assertLess(SCRIPT.index('rcon.send("save-all flush")'),
                        SCRIPT.index('rcon.send("stop")'))
        self.assertIn("gzip.decompress",SCRIPT)
        self.assertIn("region/*.mca",SCRIPT)
        self.assertIn("PR25_CLIMATE_SAVED_ANVIL_PASS",SCRIPT)
    def test_no_default_world_or_graduation_bypass(self):
        self.assertNotIn('level-type=realscale:tva_sacred_timeline_three_realms',SCRIPT)
        self.assertIn("level-name=",SCRIPT)
        self.assertIn("max-parallel: 2",WORKFLOW)
if __name__=="__main__":unittest.main()
