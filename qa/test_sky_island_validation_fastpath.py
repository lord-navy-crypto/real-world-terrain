"""Sky-island read-only position reuse and chunk-clipped geometry parity.

The mutable probe must never be handed to WorldGenLevel.setBlock or stored in a
collection. This is a source-level safety gate combined with an independent
enumeration of old and new candidate coordinates, not an FPS benchmark.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SKY = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/SkyIslandFeature.java").read_text()

class SkyIslandReadProbeTests(unittest.TestCase):
    def test_probe_is_local_and_used_for_read_only_preflight(self):
        start = SKY.index("final BlockPos.MutableBlockPos validationPos")
        end = SKY.index("// A glowing violet beacon", start)
        scope = SKY[start:end]
        self.assertEqual(scope.count("new BlockPos.MutableBlockPos()"), 1)
        self.assertIn("validationPos.set(x,y,z);", scope)
        self.assertIn("BlockState existing=world.getBlockState(validationPos);", scope)
        self.assertIn("if (!world.getFluidState(validationPos).isEmpty())", scope)
        self.assertIn("validationPos.set(x,top+1,z);", scope)
        self.assertIn("BlockState overhead=world.getBlockState(validationPos);", scope)
        self.assertNotIn("world.setBlock(validationPos", scope)
        self.assertIn("world.setBlock(new BlockPos(x, y, z), state, 2)", scope)
        self.assertIn("new BlockPos(x, top + 1, z)", scope)
        self.assertIn("if (!clear) continue;", scope)

    def test_preflight_reads_and_block_formula_remain_identical(self):
        start = SKY.index("for (int dx=minDx; dx<=maxDx; dx++)")
        end = SKY.index("// A glowing violet beacon", start)
        scope = SKY[start:end]
        original_steps = [
            "double ratio = (double)(dx * dx + dz * dz) / (r * r);",
            "if (ratio > 1.0) continue;",
            "int top = surface(site, dx, dz, ratio);",
            "int thickness = thickness(ratio);",
            "BlockState existing=world.getBlockState(validationPos);",
            "if (!existing.isAir() && !isIslandMaterial(existing))",
            "if (!world.getFluidState(validationPos).isEmpty())",
            "BlockState overhead=world.getBlockState(validationPos);",
            "if (!overhead.isAir() && !isIslandDecoration(overhead)) clear=false;",
            "if (!clear) continue;",
            "for (int y = top - thickness; y <= top; ++y)",
            "world.setBlock(new BlockPos(x, y, z), state, 2);",
        ]
        locations = [scope.index(part) for part in original_steps]
        self.assertEqual(locations, sorted(locations))
        self.assertIn("ratio < .65", scope)
        self.assertIn("ratio < .70", scope)
        self.assertIn("((dx * 7 + dz * 13) & 3) == 0", scope)
        self.assertIn("((x * 31 ^ z * 17) & 15) == 3", scope)
        self.assertIn("if (plant.canSurvive(world, flower))", scope)
        self.assertIn("if (!placed) return false;", SKY)
        self.assertLess(SKY.index("if (!placed) return false;"),
                        SKY.index("BlockPos beacon = new BlockPos("))
        self.assertIn("if (placed && Math.floorDiv(beacon.getX(),16)==chunkX", SKY)

    def test_clipped_chunk_columns_match_previous_full_radius_scan(self):
        # The established chunk clipping itself is unchanged in Test.13.
        # Confirm old full-radius and clipped enumeration agree, including
        # negative coordinates and islands spanning multiple chunk borders.
        sites = [(-48, 47), (32, 47), (-128, 47), (-48, -33),
                 (0, 0), (17, -17), (4095, -8192), (-4097, 8193)]
        for x, z in sites:
            for r in (7, 10, 11, 14):
                for chunk_x in {x // 16 - 1, x // 16, x // 16 + 1}:
                    for chunk_z in {z // 16 - 1, z // 16, z // 16 + 1}:
                        full = [
                            (dx, dz) for dx in range(-r, r + 1)
                            for dz in range(-r, r + 1)
                            if chunk_x * 16 <= x + dx <= chunk_x * 16 + 15
                            and chunk_z * 16 <= z + dz <= chunk_z * 16 + 15
                            and dx * dx + dz * dz <= r * r
                        ]
                        min_dx = max(-r, chunk_x * 16 - x)
                        max_dx = min(r, chunk_x * 16 + 15 - x)
                        min_dz = max(-r, chunk_z * 16 - z)
                        max_dz = min(r, chunk_z * 16 + 15 - z)
                        clipped = [
                            (dx, dz) for dx in range(min_dx, max_dx + 1)
                            for dz in range(min_dz, max_dz + 1)
                            if dx * dx + dz * dz <= r * r
                        ]
                        self.assertEqual(full, clipped, (x, z, r, chunk_x, chunk_z))

if __name__ == "__main__":
    unittest.main()
