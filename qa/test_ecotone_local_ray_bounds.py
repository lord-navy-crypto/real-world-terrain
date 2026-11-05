"""Static regression: expensive climate rays must use each biome's rule width.
The width index is derived from the very same rules, preserving their priority.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge/NaturalEcotoneBiomeSource.java"

class LocalRayBoundsTest(unittest.TestCase):
    def test_rule_derived_bounds_and_no_global_ray_loop(self):
        source=JAVA.read_text()
        self.assertIn("MAX_WIDTH_BY_BIOME = buildWidths()",source)
        self.assertIn("widths.merge(id, rule.widthBlocks(), Math::max)",source)
        self.assertIn("MAX_WIDTH_BY_BIOME.getOrDefault(current, 0)",source)
        self.assertIn("step <= maxLocalQuarts; step++",source)
        self.assertIn("distanceSquared > 16 * maxLocalQuarts * maxLocalQuarts",source)
        self.assertIn("if (distanceSquared >= closestDistanceSquared) break;",source)
        self.assertIn("if (current.equals(next)) continue;",source)
        self.assertNotIn("step <= MAX_SEARCH_QUARTS; step++",source)

if __name__=="__main__":
    unittest.main()
