#!/usr/bin/env python3
"""NeoForge built-in structure pack and Mod > RealScale > Config gates."""
import json
import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import make_structure_density_packs as candidates

JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
GENERATED = ROOT / "neoforge/build/generated/realscale-structure-packs/resourcepacks"

PROFILES = {
    **{f"overworld_{m}": candidates.pack_resources(m) for m in (2, 3, 4, 6)},
    **{f"dimensions_{m}": candidates.dimension_pack_resources(m) for m in (2, 3)},
}


class BuiltinStructureTests(unittest.TestCase):
    def test_config_entry_and_nonmandatory_pack_registration(self):
        bootstrap=(JAVA / "RealScaleMod.java").read_text()
        loader=(JAVA / "RealScaleStructurePacks.java").read_text()
        ui=(JAVA / "RealScaleTerrainLabClient.java").read_text()
        self.assertIn("bus.addListener(RealScaleStructurePacks::register)", bootstrap)
        self.assertIn("registerExtensionPoint(IConfigScreenFactory.class",ui)
        self.assertIn("Built-in Vanilla Structures",ui)
        self.assertIn("dimensionStructureDensity",ui)
        self.assertIn("structureDensity",ui)
        self.assertIn("DRAFT",loader)
        self.assertIn("PackType.SERVER_DATA",loader)
        self.assertIn("PackSource.DEFAULT",loader)
        self.assertIn("PackSource.BUILT_IN",loader)
        self.assertIn("false, // Never mandatory",loader)
        self.assertIn("return new Selection(4, 2)",loader)
        self.assertIn("current.overworld() == ratio",loader)
        self.assertIn("current.otherDimensions() == ratio",loader)
        self.assertIn('for (int ratio : new int[]{2, 3, 4, 6})',loader)
        self.assertIn('for (int ratio : new int[]{2, 3})',loader)

    def test_generated_packs_match_independently_validated_python_baselines(self):
        self.assertTrue(GENERATED.is_dir(),
            "Run Gradle processResources or build before this integration test")
        expected_names={f"realscale_structures_{p}" for p in PROFILES}
        self.assertEqual({x.name for x in GENERATED.iterdir() if x.is_dir()},
                         expected_names)
        for profile, expected in PROFILES.items():
            with self.subTest(profile=profile):
                location=GENERATED / f"realscale_structures_{profile}"
                actual={}
                for f in location.rglob("*.json"):
                    actual[str(f.relative_to(location)).replace("\\", "/")] = json.loads(f.read_text())
                self.assertEqual(set(actual),set(expected))
                self.assertEqual(actual["pack.mcmeta"]["pack"]["pack_format"],48)
                for name in expected:
                    if name!="pack.mcmeta":
                        self.assertEqual(actual[name],expected[name],
                            "Built-in structure_set must match the validated standalone file")

    def test_jar_contains_every_pack_without_overwriting_base_structure_sets(self):
        jars=list((ROOT/"neoforge/build/libs").glob("*.jar"))
        self.assertTrue(jars,"NeoForge jar must be built before validation")
        jar=max(jars,key=lambda x:x.stat().st_mtime)
        with zipfile.ZipFile(jar) as archive:
            names=set(archive.namelist())
            for profile, expected in PROFILES.items():
                prefix=f"resourcepacks/realscale_structures_{profile}/"
                for inner, entry in expected.items():
                    name=prefix+inner
                    self.assertIn(name, names)
                    got=json.loads(archive.read(name))
                    if inner=="pack.mcmeta":
                        self.assertEqual(got["pack"]["pack_format"],48)
                    else:
                        self.assertEqual(got,entry)
            self.assertFalse(
                any(n.startswith("data/minecraft/worldgen/structure_set/")
                    for n in names),
                "Structure override must only be in selectable built-in packs")


if __name__ == "__main__":
    unittest.main()
