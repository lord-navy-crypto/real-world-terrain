#!/usr/bin/env python3
"""Offline validation for opt-in client visual pack installer."""
import ast
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parents[1] / "scripts/install_realscale_cinematic.py"

class CinematicInstallerTests(unittest.TestCase):
    def test_compiles_with_stock_python(self):
        content = SRC.read_text()
        compile(content, str(SRC), "exec")
        ast.parse(content)

    def test_official_upstream_and_version_target(self):
        content = SRC.read_text()
        for phrase in (
            "https://api.modrinth.com/v2/project/",
            "https://cdn.modrinth.com/",
            "sha512",
            "1.21.1",
            "complementary-unbound",
            "firmament-shaders",
            "darkyboys/XsRealism-512x",
            "darkyboys/XsRealism-pbr",
            "pack_format",
            "34",
            "REALSCALE_DIR",
            "shaderpacks",
            "resourcepacks",
            "iris.properties",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, content)

if __name__ == "__main__":
    unittest.main()
