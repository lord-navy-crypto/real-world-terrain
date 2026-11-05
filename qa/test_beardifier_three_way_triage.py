import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from triage_beardifier_three_worlds import classify,summarize_three_way


def chunk(block):
    return {
        "sections":[{"Y":0,
             "block_states":{"palette":[{"Name":"minecraft:"+block}]},
             "biomes":{"palette":["minecraft:plains"]}}],
        "Heightmaps":{"WORLD_SURFACE":[0]},
        "structures":{"starts":{}}
    }


class BeardifierThreeWayTriageTest(unittest.TestCase):
    def test_categories(self):
        self.assertEqual(classify("stone","stone","stone"),"unchanged")
        self.assertEqual(classify("stone","gold","stone"),"candidate_only")
        self.assertEqual(classify("stone","gold","gold"),"reference_variable")
        self.assertEqual(classify("stone","stone","gold"),"reference_variable")
        self.assertEqual(classify("stone","gravel","gold"),"third_value")

    def test_per_voxel_unique_candidate(self):
        triage=summarize_three_way(chunk("stone"),chunk("andesite"),chunk("stone"),(4096,-2048))
        self.assertEqual(triage["counts"]["blocks_candidate_only"],4096)
        self.assertTrue(triage["review_required"])
        self.assertEqual(triage["examples"]["blocks_candidate_only"][0]["position"],
                         {"x":4096,"y":0,"z":-2048})

    def test_reference_variation_is_not_automatically_candidate_bug(self):
        triage=summarize_three_way(chunk("stone"),chunk("andesite"),chunk("andesite"))
        self.assertEqual(triage["counts"]["blocks_reference_variable"],4096)
        self.assertFalse(triage["review_required"])

    def test_third_value_requires_investigation(self):
        triage=summarize_three_way(chunk("stone"),chunk("gravel"),chunk("andesite"))
        self.assertEqual(triage["counts"]["blocks_third_value"],4096)
        self.assertTrue(triage["review_required"])

    def test_heightmap_candidate_only(self):
        a,b,c=chunk("stone"),chunk("stone"),chunk("stone")
        b["Heightmaps"]["WORLD_SURFACE"]=[12]
        triage=summarize_three_way(a,b,c)
        self.assertEqual(triage["counts"]["Heightmaps_candidate_only"],1)
        self.assertTrue(triage["review_required"])

    def test_blocks_identical(self):
        triage=summarize_three_way(chunk("stone"),chunk("stone"),chunk("stone"))
        self.assertFalse(triage["review_required"])
        self.assertEqual(triage["counts"],{})


if __name__=="__main__":
    unittest.main()
