"""Unified Sacred/Measured/Terrain Lab invasion export regression tests.

Covers immutable base worlds, three dimension wrappers, optional near-origin
showcase, backward compatibility of old drafts and standalone JAR export.
"""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from unified_world_settings import PRESET_DIR, create_preset, invasion_preset
from export_terrain_lab_datapack import _unified_choices, export

RESOURCES = ROOT / "neoforge/src/main/resources"
DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")


class UnifiedRealmInvasionTests(unittest.TestCase):
    def test_both_canonical_worlds_keep_noise_settings_and_unwrapped_sources(self):
        for source in ("sacred", "measured"):
            with self.subTest(source=source):
                name, combined, names = create_preset(RESOURCES, source, True)
                base = json.loads((RESOURCES / PRESET_DIR /
                    (("tva_sacred_timeline_three_realms" if source == "sacred"
                      else "tva_measured_three_realms") + ".json")
                    ).read_text(encoding="utf-8"))
                self.assertEqual(name, "unified_" + source + "_invasion_v1")
                self.assertEqual(combined["dimensions"]["minecraft:overworld"]
                    ["generator"]["settings"],
                    base["dimensions"]["minecraft:overworld"]["generator"]["settings"])
                for dim in DIMENSIONS:
                    got = combined["dimensions"][dim]["generator"]
                    original = base["dimensions"][dim]["generator"]
                    self.assertEqual(got["settings"], original["settings"])
                    self.assertEqual(got["type"], original["type"])
                    if dim == "minecraft:overworld":
                        self.assertEqual(got["biome_source"]["type"], "realscale:sky_ocean")
                        invaded = got["biome_source"]["source"]
                        self.assertEqual(invaded["target"], "overworld")
                        self.assertEqual(invaded["source"], original["biome_source"]["source"])
                    else:
                        invaded = got["biome_source"]
                        self.assertEqual(invaded["source"], original["biome_source"])
                    self.assertEqual(invaded["type"], "realscale:cross_realm_invasion")
                self.assertIn("Dimension Invasion", names["en_us"])
                self.assertIn("跨维度入侵", names["zh_cn"])
                # Caller receives new data; base JSON never modified.
                self.assertEqual(base, json.loads(
                    (RESOURCES / PRESET_DIR /
                     ("tva_" + ("sacred_timeline" if source == "sacred"
                      else "measured") + "_three_realms.json")).read_text()))

    def test_showcase_wraps_all_three_realms_at_the_outermost_level(self):
        for source in ("sacred", "measured"):
            name, combined, _ = create_preset(RESOURCES, source, True, showcase=True)
            self.assertEqual(name, "unified_" + source + "_invasion_showcase_v1")
            for dim in DIMENSIONS:
                outer = combined["dimensions"][dim]["generator"]["biome_source"]
                self.assertEqual(outer["type"], "realscale:cross_realm_showcase")
                if dim == "minecraft:overworld":
                    self.assertEqual(outer["source"]["type"], "realscale:sky_ocean")
                    self.assertEqual(outer["source"]["source"]["type"],
                                     "realscale:cross_realm_invasion")
                else:
                    self.assertEqual(outer["source"]["type"],
                                     "realscale:cross_realm_invasion")

    def test_off_mode_keeps_world_identical(self):
        name, combined, _ = create_preset(RESOURCES, "measured", False)
        original = json.loads((RESOURCES / PRESET_DIR /
                               "tva_measured_three_realms.json").read_text())
        self.assertEqual(combined, original)
        self.assertEqual(name, "unified_measured_standard_v1")
        with self.assertRaises(ValueError):
            create_preset(RESOURCES, "sacred", False, showcase=True)

    def test_lab_profile_wraps_without_touching_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = root / PRESET_DIR
            directory.mkdir(parents=True)
            seed = json.loads((RESOURCES / PRESET_DIR /
                               "tva_sacred_timeline_three_realms.json").read_text())
            (directory / "lab_example.json").write_text(json.dumps(seed))
            name, result, _ = create_preset(root, "terrain_lab", True,
                                           lab_name="lab_example")
            self.assertEqual(name, "lab_example_invasion")
            self.assertEqual(result["dimensions"]["minecraft:overworld"]
                             ["generator"]["biome_source"]["source"]["type"],
                             "realscale:cross_realm_invasion")
            self.assertEqual(json.loads((directory / "lab_example.json").read_text()), seed)

    def test_new_unified_ui_always_on_and_first_spawn_is_invasion(self):
        java = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
        ui = (java / "RealScaleTerrainLabClient.java").read_text()
        source = (java / "CrossRealmBiomeSource.java").read_text()
        sky = (java / "SkyOceanBiomeSource.java").read_text()
        init = (java / "InvasionInitialSpawn.java").read_text()
        self.assertIn('String worldSource = "sacred";', ui)
        self.assertIn("boolean dimensionalInvasion = true;", ui)
        self.assertIn("dimensionalInvasion = true;", ui)
        self.assertIn("Invasion Always ON", ui)
        self.assertIn("isSpawnInvasionZone(quartX, quartZ)", source)
        self.assertIn("SPAWN_INVASION_RADIUS_SQUARED=192L*192L;", source)
        self.assertIn("return (biome.hashCode() & 1) == 0 ? netherWastes : endHighlands;", source)
        self.assertIn("CrossRealmBiomeSource.isSpawnInvasionZone(quartX,quartZ)", sky)
        self.assertIn("LevelEvent.CreateSpawnPosition", init)
        self.assertIn("event.getSettings().setSpawn(spawn,0.0F);", init)
        self.assertIn("event.setCanceled(true);", init)
        self.assertIn("Blocks.BLACKSTONE.defaultBlockState()", init)
        self.assertIn("level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0)", init)

        with tempfile.TemporaryDirectory() as tmp:
            draft = Path(tmp) / "new-profile.json"
            draft.write_text(json.dumps({
                "worldSource": "sacred", "dimensionalInvasion": True,
                "invasionShowcase": False,
            }))
            self.assertEqual(_unified_choices(draft), ("sacred", True, False))
            draft.write_text(json.dumps({
                "worldSource": "measured", "dimensionalInvasion": False,
            }))
            with self.assertRaisesRegex(ValueError, "requires dimensionalInvasion=true"):
                _unified_choices(draft)

    def test_legacy_drafts_default_to_safe_off(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "legacy.json"
            path.write_text(json.dumps({"preset": "landscape"}))
            self.assertEqual(_unified_choices(path), ("terrain_lab", False, False))
            for bad in (
                {"worldSource": "vanilla"},
                {"worldSource": 4},
                {"dimensionalInvasion": "true"},
                {"invasionShowcase": True},
                {"dimensionalInvasion": True, "invasionShowcase": 1},
            ):
                path.write_text(json.dumps({"preset": "landscape", **bad}))
                with self.assertRaises(ValueError):
                    _unified_choices(path)

    def test_additive_end_biome_layer_preserves_canonical_end_and_other_realms(self):
        from unified_world_settings import create_preset
        for source in ("sacred","measured"):
            with self.subTest(source=source):
                orig_name, base, _ = create_preset(RESOURCES, source, True)
                name, update, labels = create_preset(RESOURCES, source, True,
                                                    end_biomes=True)
                self.assertEqual(name, orig_name+"_end3")
                self.assertIn("Three End Biomes", labels["en_us"])
                for dim in ("minecraft:overworld","minecraft:the_nether"):
                    self.assertEqual(update["dimensions"][dim],base["dimensions"][dim])
                original_end=base["dimensions"]["minecraft:the_end"]["generator"]
                changed_end=update["dimensions"]["minecraft:the_end"]["generator"]
                self.assertEqual(changed_end["settings"],original_end["settings"])
                self.assertEqual(changed_end["type"],original_end["type"])
                self.assertEqual(changed_end["biome_source"],{
                    "type":"realscale:end_accents",
                    "source":original_end["biome_source"]
                })
                # Old unified world preset remains an independent ID.
                self.assertNotEqual(name,orig_name)

    def test_real_jar_resource_export_contains_only_new_preset_and_additive_tags(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar, draft = root / "mod.jar", root / "settings.json"
            # A minimal but genuine subset of RealScale JAR worldgen resources.
            required = (
                "data/realscale/worldgen/noise_settings/realism_max.json",
                "data/realscale/worldgen/world_preset/realism_max.json",
                "data/realscale/worldgen/world_preset/tva_sacred_timeline_three_realms.json",
                "data/realscale/worldgen/world_preset/tva_measured_three_realms.json",
                "assets/minecraft/lang/en_us.json",
                "assets/minecraft/lang/zh_cn.json",
            )
            with zipfile.ZipFile(jar, "w") as archive:
                for name in required:
                    archive.write(RESOURCES / name, name)
            for source in ("sacred", "measured"):
                for showcase in (False, True):
                    with self.subTest(source=source, showcase=showcase):
                        draft.write_text(json.dumps({
                            "preset": "landscape", "reliefPercent": 100,
                            "elevation": 0, "caves": 50, "riverGrade": 6,
                            "inland": True, "worldSource": source,
                            "dimensionalInvasion": True,
                            "invasionShowcase": showcase,
                            "endBiomeAccents": False,  # preserve legacy export test
                        }))
                        output = root / (source + ("showcase" if showcase else "normal") + ".zip")
                        profile, language = export(jar, draft, output)
                        self.assertEqual(profile,
                            "realscale:unified_" + source +
                            ("_invasion_showcase_v1" if showcase else "_invasion_v1"))
                        with zipfile.ZipFile(output) as archive:
                            preset_files = [name for name in archive.namelist()
                                            if "/world_preset/" in name
                                            and name.endswith(".json")
                                            and name.startswith("data/realscale/")]
                            self.assertEqual(preset_files, [
                                "data/realscale/worldgen/world_preset/" +
                                profile.split(":")[1] + ".json"])
                            preset = json.loads(archive.read(preset_files[0]))
                            original = json.loads((RESOURCES / PRESET_DIR /
                                ("tva_sacred_timeline_three_realms.json"
                                 if source == "sacred" else "tva_measured_three_realms.json")
                                ).read_text())
                            for dim in DIMENSIONS:
                                self.assertEqual(
                                    preset["dimensions"][dim]["generator"]["settings"],
                                    original["dimensions"][dim]["generator"]["settings"])
                            for tag in ("normal", "extended"):
                                path = "data/minecraft/tags/worldgen/world_preset/" + tag + ".json"
                                info = json.loads(archive.read(path))
                                self.assertEqual(info, {"replace": False, "values": [profile]})
                        self.assertIsNotNone(language)
                        with zipfile.ZipFile(language) as archive:
                            info = json.loads(archive.read("assets/minecraft/lang/en_us.json"))
                            self.assertIn("generator.realscale." + profile.split(":")[1], info)


if __name__ == "__main__":
    unittest.main()
