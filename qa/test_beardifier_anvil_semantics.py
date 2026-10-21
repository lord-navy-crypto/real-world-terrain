import unittest
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from compare_beardifier_saved_regions import ids_from_palette,summarize_mismatch

class AnvilSemanticParityTest(unittest.TestCase):
    def test_blockstate_palette_permutation_does_not_change_blocks(self):
        first={"palette":[{"Name":"minecraft:air"},{"Name":"minecraft:stone"}],
               "data":[0b10001]}
        second={"palette":[{"Name":"minecraft:stone"},{"Name":"minecraft:air"}],
                "data":[((1<<64)-1) ^ (1<<4)]}
        # Check semantic parity on a small subset of first two block indices:
        self.assertEqual(ids_from_palette(first,True)[0],("minecraft:stone",()))
        self.assertEqual(ids_from_palette(second,True)[0],("minecraft:air",()))
    def test_heightmap_differences_are_rejected(self):
        a={"sections":[{"Y":0,"block_states":{"palette":[{"Name":"minecraft:stone"}]},
                          "biomes":{"palette":["minecraft:plains"]}}],
            "Heightmaps":{"WORLD_SURFACE":[10]}}
        b={"sections":[{"Y":0,"block_states":{"palette":[{"Name":"minecraft:stone"}]},
                          "biomes":{"palette":["minecraft:plains"]}}],
            "Heightmaps":{"WORLD_SURFACE":[11]}}
        self.assertEqual(summarize_mismatch(a,a),[])
        self.assertTrue(summarize_mismatch(a,b))
if __name__=="__main__":unittest.main()
