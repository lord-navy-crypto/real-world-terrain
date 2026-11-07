#!/usr/bin/env python3
"""Guard only *mathematically exact* fitted terrain hot-path exclusions."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"


class FittedTerrainPerfGuards(unittest.TestCase):
    def test_alpine_lowland_skips_continental_noise_before_sampling(self):
        src=(JAVA/"TerrainArchetypeDensity.java").read_text(encoding="utf-8")
        part=src[src.index("public double compute(DensityFunction.FunctionContext ctx)"):]
        self.assertLess(part.index("double original = base.compute(ctx);"),
                        part.index("if (alpineFamily && original <= 0.65) return original;"))
        self.assertLess(part.index("if (alpineFamily && original <= 0.65) return original;"),
                        part.index("double continent = continents.compute(ctx);"))
        self.assertLess(part.index("if (continent <= -0.10) return original;"),
                        part.index("selectorFreeFamily ? 0.0 : selector.compute(ctx)"))
        self.assertIn('this.selectorFreeFamily = "glacial".equals(family) || "volcanic".equals(family);',src)

    def test_horizontal_morphology_kernels_reuse_exact_column_shapes(self):
        src=(JAVA/"TerrainArchetypeMath.java").read_text(encoding="utf-8")
        self.assertIn("private static final int HORIZONTAL_CACHE_SLOTS = 1024;",src)
        self.assertIn("private final ThreadLocal<HorizontalShapeCache> horizontalShapeCache",src)
        self.assertIn("private void horizontalShapesAt(HorizontalShapeCache cache,double x,double z)",src)
        self.assertIn("cache.lastXBits==xBits && cache.lastZBits==zBits",src)
        self.assertIn('case "alpine" ->',src)
        self.assertIn('case "canyon" ->',src)
        self.assertIn('case "glacial" ->',src)
        self.assertIn('case "dune" ->',src)

    def test_karst_and_volcano_cache_horizontal_shape_before_repeated_y_samples(self):
        src=(JAVA/"TerrainArchetypeMath.java").read_text(encoding="utf-8")
        self.assertIn("private static final int KARST_CACHE_SLOTS = 1024;",src)
        self.assertIn("private final ThreadLocal<KarstShapeCache> karstShapeCache",src)
        self.assertIn("if (cache.lastValid && cache.lastKey == key) return cache.lastShape;",src)
        self.assertIn("private void karstShapesAt(KarstShapeCache cache, int x, int z)",src)
        self.assertIn("cache.lastTower",src)
        self.assertIn("cache.lastSink",src)
        self.assertIn("Math.max(macroGain, 0.12)",src)
        self.assertIn("0.28 * mesoGain",src)  # canyon retains its own fitted meso-scale coefficient

    def test_wavelength_constants_computed_once_outside_each_density_sample(self):
        src=(JAVA/"TerrainArchetypeMath.java").read_text(encoding="utf-8")
        expected={
            "glacialWidth":"fittedScale(0.035, 180.0, 1600.0)",
            "volcanicRadius":"fittedScale(0.025, 250.0, 800.0)",
            "karstTowerRadius":"fittedScale(0.025, 80.0, 450.0)",
            "karstSinkRadius":"fittedScale(0.012, 40.0, 200.0)",
        }
        for field,expression in expected.items():
            with self.subTest(field=field):
                self.assertIn(f"this.{field} = {expression};",src)
                self.assertIn(f"private final double {field};",src)
                self.assertIn(f"double radius = {field};" if field=="volcanicRadius" else (
                    f"double width = {field};" if field=="glacialWidth" else
                    f"double {'towerRadius' if field=='karstTowerRadius' else 'sinkRadius'} = {field};"),src)
        # Preserve the original kernel mathematics; only fixed scale lookup
        # locations and the outer zero-effect check are allowed to change.
        self.assertIn("Math.hypot(x1 - x2, z1 - z2)",src)
        self.assertIn("Math.pow(radial / 0.12, 2.0)",src)
        self.assertIn("smoothstep(width * 0.28, width, across)",src)


if __name__=="__main__":
    unittest.main()
