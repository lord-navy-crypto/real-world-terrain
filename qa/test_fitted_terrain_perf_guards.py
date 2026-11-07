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

    def test_measured_dem_density_uses_one_combined_column_lookup(self):
        density=(JAVA/"RealDemSeamlessDensity.java").read_text(encoding="utf-8")
        math=(JAVA/"RealDemSeamlessMath.java").read_text(encoding="utf-8")
        self.assertIn("cachedColumnSample(x,z,blendMeters)",density)
        self.assertNotIn("cachedWeightOutside(x,z,blendMeters)",density)
        self.assertNotIn("cachedExtendedHeight(x,z)",density)
        self.assertIn("COLUMN_SAMPLE_CACHE_SIZE = 4096",math)
        self.assertIn("cache.lastX==x && cache.lastZ==z",math)
        self.assertIn("cache.lastFadeWidth==fadeMeters",math)

    def test_giant_cave_mask_cache_reuses_the_last_horizontal_column(self):
        src=(JAVA/"GrandOverworldCaveNetwork.java").read_text(encoding="utf-8")
        self.assertIn("boolean lastColumnValid;",src)
        self.assertIn("long lastColumnMask;",src)
        self.assertIn("lastColumnValid=false;",src)
        self.assertIn("if(c.lastColumnValid && c.lastColumnX==x && c.lastColumnZ==z)",src)
        self.assertIn("c.lastColumnMask=c.columnComponents[first];",src)
        self.assertIn("c.lastColumnMask=c.columnComponents[second];",src)

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

    def test_real_dem_far_field_exits_before_allocating_column_cache(self):
        density=(JAVA/"RealDemSeamlessDensity.java").read_text(encoding="utf-8")
        math=(JAVA/"RealDemSeamlessMath.java").read_text(encoding="utf-8")
        self.assertLess(
            density.index("outsideFadeRegion(x,z,blendMeters)"),
            density.index("cachedColumnSample(x,z,blendMeters)"))
        self.assertIn("static boolean outsideFadeRegion(int x,int z,int fadeMeters)",math)
        self.assertIn("dx*dx+dz*dz >= (long)fadeMeters*fadeMeters",math)

    def test_density_profiler_covers_real_dem_and_fitted_archetype_paths(self):
        sampler=(JAVA/"DensityPerfSampler.java").read_text(encoding="utf-8")
        self.assertIn("REAL_DEM_LEGACY",sampler)
        self.assertIn("REAL_DEM_SEAMLESS",sampler)
        self.assertIn("TERRAIN_ARCHETYPE",sampler)
        for filename,stage in (
            ("RealDemDensity.java","REAL_DEM_LEGACY"),
            ("RealDemSeamlessDensity.java","REAL_DEM_SEAMLESS"),
            ("TerrainArchetypeDensity.java","TERRAIN_ARCHETYPE"),
        ):
            src=(JAVA/filename).read_text(encoding="utf-8")
            self.assertIn(f"DensityPerfSampler.Stage.{stage}",src)
            self.assertIn("if(!DensityPerfSampler.ENABLED)",src)

    def test_virtual_underground_boundary_is_used_conservatively(self):
        boundary=(JAVA/"VirtualUndergroundBoundary.java").read_text(encoding="utf-8")
        client=(JAVA/"RealScaleAdaptiveVisibilityClient.java").read_text(encoding="utf-8")
        self.assertIn("conservativeBoundaryY(h0,h1,h2,h3,h4)",client)
        self.assertIn("isBelowBoundary(eye.getY(),boundaryY)",client)
        self.assertIn("MAX_LOCAL_SURFACE_SPREAD_BLOCKS",boundary)
        self.assertIn("MIN_ROOF_CLEARANCE_BLOCKS",boundary)
        self.assertIn("Integer.MIN_VALUE",boundary)
        self.assertNotIn("setBlock(",client)

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
        # Glacial geometry consumes the immutable precomputed width directly;
        # it intentionally no longer creates a per-sample local width value.
        self.assertIn("glacialWidth*0.22*Math.sin(",src)
        self.assertIn("smoothstep(glacialWidth*0.28,glacialWidth,across)",src)
        self.assertIn("double radius = volcanicRadius;",src)
        self.assertIn("double towerRadius = karstTowerRadius;",src)
        self.assertIn("double sinkRadius = karstSinkRadius;",src)
        # Preserve the original kernel mathematics; only fixed scale lookup
        # locations and mathematically exact zero-support guards may change.
        self.assertIn("Math.hypot(x1 - x2, z1 - z2)",src)
        self.assertIn("Math.pow(radial / 0.12, 2.0)",src)


    def test_glacial_reference_benchmarks_retain_exact_fitted_width_expression(self):
        expected="double width=Math.max(180.0,Math.min(1600.0,9800.0*0.035));"
        for path in (
            ROOT/"neoforge/src/test/java/org/realscale/neoforge/TerrainArchetypeSiteCacheTest.java",
            ROOT/"neoforge/src/test/java/org/realscale/neoforge/TerrainArchetypeHorizontalCacheBenchmark.java",
        ):
            with self.subTest(path=path.name):
                self.assertIn(expected,path.read_text(encoding="utf-8"))


    def test_dune_kernel_bypasses_horizontal_cache_when_direct_math_is_cheaper(self):
        src=(JAVA/"TerrainArchetypeMath.java").read_text(encoding="utf-8")
        start=src.index("private double duneRidges(")
        end=src.index("private double ridgeGain(",start)
        dune=src[start:end]
        self.assertIn("TAU*orientedAcross(x,z)/dominantWavelengthM",dune)
        self.assertNotIn("horizontalShapesAt",dune)
        self.assertNotIn("horizontalShapeCache.get()",dune)
        cache_start=src.index("private void horizontalShapesAt(")
        cache_end=src.index("private double alpine(",cache_start)
        self.assertNotIn('case "dune"',src[cache_start:cache_end])
        benchmark=(JAVA/"TerrainArchetypeHorizontalCacheBenchmark.java").read_text(encoding="utf-8")
        self.assertIn("runProduction",benchmark)
        self.assertNotIn("runCached",benchmark)
        self.assertIn("production_median_ms",benchmark)


if __name__=="__main__":
    unittest.main()
