"""Safety checks for lower-allocation underground/marine habitat patches.

This is a source regression gate, not a substitute for saved-world replay.
"""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/HabitatPatchFeature.java").read_text()
FLOOR=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/HabitatFloorSearch.java").read_text()

class HabitatPatchLowAllocation(unittest.TestCase):
    def test_cave_search_reuses_local_probe_and_keeps_original_order(self):
        section=SRC[SRC.index("private static BlockPos nearbyCaveFloor("):
                    SRC.index("@Override public boolean place(")]
        self.assertIn("BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();",section)
        self.assertIn("y -> dryCaveFloor(w, probe.set(x, y, z), dripstone)",section)
        self.assertNotIn("new BlockPos(origin.getX(), y, origin.getZ())",section)
        self.assertIn("HabitatFloorSearch.nearest(origin.getY(), min, max,",section)
        self.assertIn("HIGHLAND_SCAN_RADIUS_Y : CAVE_SCAN_RADIUS_Y",section)
        self.assertIn("surface - MIN_ROOF_DEPTH",section)
        self.assertIn("if (max < min) return null;",section)
        self.assertIn("for (int delta = 0; delta <= radius; ++delta)",FLOOR)
        self.assertIn("if (delta == 0) continue;",FLOOR)

    def test_local_cave_y_offsets_order_and_rng_unchanged(self):
        self.assertIn("private static final int[] FLOOR_OFFSETS = {0, 1, -1};",SRC)
        self.assertIn("for (int step : FLOOR_OFFSETS)",SRC)
        self.assertNotIn("new int[]{0, 1, -1}",SRC)
        self.assertIn("context.random().nextInt(4) == 0",SRC)
        self.assertIn("if (dx * dx + dz * dz > 5",SRC)
        self.assertIn("if (target == null) continue;",SRC)

    def test_dry_cave_floor_short_circuit_unchanged(self):
        start=SRC.index("private static boolean dryCaveFloor(")
        end=SRC.index("private static BlockPos nearbyCaveFloor(",start)
        code=SRC[start:end]
        order=[
            "w.getBlockState(pos).isAir()",
            "BlockPos above = pos.above();",
            "w.getBlockState(above).isAir()",
            "w.getFluidState(pos).isEmpty()",
            "w.getFluidState(above).isEmpty()",
            "caveSupport(w.getBlockState(pos.below()), requireDripstone)",
        ]
        positions=[code.index(x) for x in order]
        self.assertEqual(positions,sorted(positions))
        self.assertEqual(code.count("pos.above()"),1)

    def test_marine_floor_position_reused_after_original_water_checks(self):
        code=SRC[SRC.index("} else {\n                // Require a submerged column"):
                 SRC.index("return changed > 0;")]
        checks=[
            "w.getFluidState(p).is(FluidTags.WATER)",
            "BlockPos above = p.above();",
            "w.getFluidState(above).is(FluidTags.WATER)",
            "BlockPos below = p.below();",
            "w.getBlockState(below)",
            "context.random().nextBoolean()",
            "w.setBlock(below, rock, 2)",
        ]
        offsets=[code.index(k) for k in checks]
        self.assertEqual(offsets,sorted(offsets))
        self.assertEqual(code.count("p.below()"),1)
        self.assertIn("!(floor.is(Blocks.SAND) || floor.is(Blocks.GRAVEL)",code)

if __name__=="__main__":
    unittest.main()
