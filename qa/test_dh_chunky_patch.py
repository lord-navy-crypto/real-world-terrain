#!/usr/bin/env python3
"""Regression tests for the optional, embedded DH/Chunky lifecycle guard."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMPAT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/compat/dhchunky"
SOURCE = COMPAT / "mixin/ChunkyLifecycleSetupMixin.java"
PLUGIN = COMPAT / "mixin/DhChunkyMixinConfigPlugin.java"
CONFIG = ROOT / "neoforge/src/main/resources/realscale.dhchunky.mixins.json"
TOML = ROOT / "neoforge/src/main/resources/META-INF/neoforge.mods.toml"


class DhChunkyGuardTests(unittest.TestCase):
    def test_patch_only_defers_the_confirmed_first_time_binding(self):
        code = SOURCE.read_text(encoding="utf-8")
        self.assertIn('tryRunFirstTimeSetup()V', code)
        self.assertIn("if (listenerBound) return;", code)
        self.assertIn("ci.cancel();", code)
        self.assertIn('"Chunky is not loaded."', code)
        self.assertNotIn("@Overwrite", code)
        self.assertNotIn("catch (Throwable", code)

    def test_mixin_is_optional_and_version_gated(self):
        config = json.loads(CONFIG.read_text(encoding="utf-8"))
        self.assertFalse(config["required"])
        self.assertEqual(config["mixins"], ["ChunkyLifecycleSetupMixin"])
        self.assertIn("DhChunkyMixinConfigPlugin", config["plugin"])
        plugin = PLUGIN.read_text(encoding="utf-8")
        self.assertIn('hasVersion("distanthorizons", "3.3.3")', plugin)
        self.assertIn('hasVersion("chunky", "1.4.23")', plugin)
        self.assertIn("FMLLoader.getLoadingModList()", plugin)
        self.assertIn("loadingModList.getMods()", plugin)
        self.assertIn("ModInfo::getVersion", plugin)
        self.assertNotIn("import net.neoforged.fml.ModList;", plugin)
        self.assertNotIn("ModList.get(", plugin)

    def test_dh_and_chunky_are_optional_not_hard_dependencies(self):
        toml = TOML.read_text(encoding="utf-8")
        for mod_id in ("distanthorizons", "chunky"):
            blocks = [b for b in toml.split("[[dependencies.realscale]]")
                      if f'modId="{mod_id}"' in b]
            self.assertEqual(len(blocks), 1)
            self.assertIn('type="optional"', blocks[0])
        self.assertIn('config="realscale.dhchunky.mixins.json"', toml)
        legacy = [b for b in toml.split("[[dependencies.realscale]]")
                  if 'modId="dhchunkyguard"' in b]
        self.assertEqual(len(legacy), 1)
        self.assertIn('type="incompatible"', legacy[0])

    def test_compatibility_code_is_inside_the_real_scale_project(self):
        self.assertTrue(SOURCE.is_file())
        self.assertFalse((ROOT / "dh-chunky-guard/build.gradle").exists())
        self.assertFalse((ROOT / "dh-chunky-guard/settings.gradle").exists())
        main = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
        self.assertIn("DhChunkyGuardCommands::registerCommands", main)

    def test_game_code_never_reduces_realscale_height_or_rewrites_worldgen(self):
        code = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("2096", code)
        self.assertNotIn("Biome", code)
        self.assertNotIn("Noise", code)


    def test_provider_probe_uses_bounded_backoff_and_caches_success(self):
        code = SOURCE.read_text(encoding="utf-8")
        metrics = (COMPAT / "ChunkyBridgeMetrics.java").read_text(encoding="utf-8")
        self.assertIn("AtomicLong", code)
        self.assertIn("realscale$MIN_PROBE_INTERVAL_NANOS = 50_000_000L", code)
        self.assertIn("realscale$MAX_PROBE_INTERVAL_NANOS = 1_000_000_000L", code)
        self.assertIn("realscale$nextProbeNanos.compareAndSet", code)
        self.assertIn("realscale$probeBackoffNanos.updateAndGet", code)
        self.assertIn("realscale$providerReady.set(true)", code)
        self.assertIn("ChunkyBridgeMetrics.backoffSkipped();", code)
        self.assertIn("backoffSkips=", metrics)

    def test_runtime_smokes_execute_a_bounded_chunky_pregeneration_task(self):
        runtime = (ROOT / "qa/run_dh_chunky_runtime_smoke.py").read_text(encoding="utf-8")
        packaged = (ROOT / "qa/run_packaged_jar_dh_chunky_smoke.py").read_text(encoding="utf-8")
        for required in (
            "def run_chunky_pregen(",
            '"chunky center 512 512"',
            '"chunky radius 32"',
            '"chunky shape square"',
            '"chunky start"',
            '"chunky progress"',
            "Task finished",
            "REALSCALE_DH_CHUNKY_PREGEN_PASS",
        ):
            self.assertIn(required, runtime)
        self.assertIn("from run_dh_chunky_runtime_smoke import run_chunky_pregen", packaged)
        self.assertIn("PACKAGED_JAR_DH_CHUNKY_PREGEN_PASS", packaged)
        self.assertIn("run_chunky_pregen(", packaged)

    def test_ci_runs_the_packaged_jar_server_smoke(self):
        workflow = (ROOT / ".github/workflows/dh-chunky-integrated.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "Verify the packaged RealScale JAR in an official NeoForge server with DH and Chunky",
            workflow,
        )
        self.assertIn(
            "python3 ../qa/run_packaged_jar_dh_chunky_smoke.py",
            workflow,
        )
        self.assertIn(
            "packaged-dh-chunky-runtime-smoke.log",
            workflow,
        )

if __name__ == "__main__":
    unittest.main()
