#!/usr/bin/env python3
"""Check that the 2096-high baseline does not import an OPTIONAL jigsaw mod.

Lithostitched is required for *explicit* Terrain Lab codec exports only.
Its 21.1 JigsawStructureMixin globally redirects vanilla jigsaw placement
when present on a NeoForge server and calls getFirstFreeHeight repeatedly.
The canonical four RealScale world presets do not use its data codecs.
"""
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MOD=ROOT/"neoforge"
SRC=MOD/"src/main"
WORLD=SRC/"resources/data/realscale/worldgen"
BUILD=(MOD/"build.gradle").read_text(encoding="utf-8")

class OptionalLithostitchedIsolation(unittest.TestCase):
    def test_library_is_available_for_opt_in_but_not_installed_by_default(self):
        active=re.sub(r"/\*.*?\*/","",BUILD,flags=re.S)
        active="\n".join(line.split("//",1)[0] for line in active.splitlines())
        self.assertRegex(active,r"(?m)^\s*compileOnly\s+['\"]maven\.modrinth:XaDC71GB:sPmtDq1X['\"]")
        self.assertNotRegex(active,r"(?m)^\s*(?:implementation|runtimeOnly|modRuntimeOnly)\s+['\"]maven\.modrinth:XaDC71GB:")
        metadata=(SRC/"resources/META-INF/neoforge.mods.toml").read_text()
        self.assertNotIn('modId="lithostitched"',metadata)

    def test_all_default_terrain_preset_configs_use_only_own_worldgen(self):
        for n in ("tva_sacred_timeline_three_realms","tva_alpine_three_realms",
                  "tva_canyon_three_realms","tva_measured_three_realms"):
            with self.subTest(preset=n):
                data=json.loads((WORLD/"world_preset"/(n+".json")).read_text())
                for dim in data["dimensions"].values():
                    self.assertTrue(dim["generator"]["settings"].startswith("realscale:"))
                resource=json.dumps(data,sort_keys=True)
                self.assertNotIn("lithostitched:",resource)
        for n in ("usgs_big_sur_v2","tva_natural_smoothed",
                  "tva_alpine_smoothed","tva_canyon_smoothed"):
            with self.subTest(noise=n):
                noise=(WORLD/"noise_settings"/(n+".json")).read_text()
                self.assertNotIn("lithostitched:",noise)

    def test_no_main_java_imports_lithostitched_runtime_classes(self):
        for java in (SRC/"java").rglob("*.java"):
            content=java.read_text(encoding="utf-8")
            self.assertNotRegex(content,r"(?m)^\s*(?:import|package)\s+dev\.worldgen\.lithostitched",
                                str(java))

if __name__=="__main__":
    unittest.main()
