from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "neoforge/src/main/java/org/realscale/neoforge/VisibleSurfaceProxyGenerator.java"
REGISTRY = ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/SurfaceFeatureProxyRegistry.java"


class GeometryClipmapCompileContractTests(unittest.TestCase):
    def test_registry_collection_entry_point_is_public_across_packages(self):
        source = REGISTRY.read_text(encoding="utf-8")
        self.assertRegex(
            source,
            r"(?m)^\s*public\s+static\s+void\s+collect\s*\(",
            "VisibleSurfaceProxyGenerator is in a different package, so the "
            "registry collection entry point must be public.",
        )

    def test_canopy_sampling_step_stays_in_the_long_numeric_domain(self):
        source = GENERATOR.read_text(encoding="utf-8")
        self.assertRegex(
            source,
            r"(?m)^\s*long\s+step\s*=\s*Math\.max\(\s*64L,\s*"
            r"Math\.multiplyExact\(\s*level\.cellSpacingBlocks\(\),\s*8L\s*"
            r"\)\s*\)\s*;",
            "Clipmap spacing is a long; the canopy sampling step must remain long "
            "and must not narrow to int.",
        )


    def test_visual_quality_shaders_and_meshes_are_wired(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        layout = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapLayout.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("ClipmapRingMesh.indices(", renderer)
        self.assertIn("ClipmapRingMesh.vertices(", renderer)
        self.assertIn("uSkirtDepth", renderer)
        self.assertIn("uNoiseOrigin", renderer)
        self.assertIn("dFdx(vPosition)", renderer)
        self.assertIn("uNearClipBlocks", renderer)
        self.assertIn("nearRadius - cameraOffsetAllowance", layout)


if __name__ == "__main__":
    unittest.main()
