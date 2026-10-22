import unittest
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from compare_beardifier_saved_regions import ids_from_palette,summarize_mismatch

class AnvilSemanticParityTest(unittest.TestCase):
    def test_blockstate_palette_permutation_does_not_change_blocks(self):
        # Two block palettes encode the same 4096 blocks but with opposite
        # palette indices; only voxel zero is stone and 4095 are air.
        first={"palette":[{"Name":"minecraft:air"},{"Name":"minecraft:stone"}],
               "data":[1]+[0]*255}
        second={"palette":[{"Name":"minecraft:stone"},{"Name":"minecraft:air"}],
                "data":[0x1111111111111110]+[0x1111111111111111]*255}
        self.assertEqual(ids_from_palette(first,True),ids_from_palette(second,True))
        self.assertEqual(ids_from_palette(first,True)[0],("minecraft:stone",()))
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
