"""Player-facing RealScale branding, canonical presets and legacy compatibility."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge/src/main/resources"
WORLD = RES / "data/realscale/worldgen/world_preset"
TAG_ROOT = RES / "data/minecraft/tags/worldgen/world_preset"

OLD = [
    "tva_sacred_timeline_three_realms",
    "tva_alpine_three_realms",
    "tva_canyon_three_realms",
    "tva_measured_three_realms",
    "tva_wonders_invasion_three_realms",
    "tva_cave_ocean_ecology_test_three_realms",
]
NEW = [
    "natural_three_realms",
    "alpine_three_realms",
    "canyon_three_realms",
    "measured_three_realms",
    "wonders_invasion_three_realms",
    "cave_ocean_ecology_three_realms",
]
CANONICAL = {
    "natural_three_realms": "tva_natural_smoothed",
    "alpine_three_realms": "tva_alpine_smoothed",
    "canyon_three_realms": "tva_canyon_smoothed",
    "measured_three_realms": "usgs_big_sur_v2",
}
SHARED_BIOME_CHAIN = [
    "realscale:sky_ocean",
    "realscale:geo_wonders_opt_in",
    "realscale:natural_ecotones",
    "realscale:cave_depth_habitats",
    "minecraft:multi_noise",
]


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def source_chain(source):
    chain = []
    while isinstance(source, dict):
        chain.append(source.get("type"))
        if "source" not in source:
            break
        source = source["source"]
    return chain


class RealScaleBrandingAndSaveCompatibility(unittest.TestCase):
    def test_new_world_menu_has_only_four_canonical_presets(self):
        menu = ["realscale:" + name for name in CANONICAL]
        for name in ("normal", "extended"):
            tag = read(TAG_ROOT / (name + ".json"))
            self.assertEqual(tag["values"], menu)
            self.assertEqual(len(set(menu)), 4)

    def test_canonical_presets_share_universal_biome_layers(self):
        for name, settings_id in CANONICAL.items():
            with self.subTest(preset=name):
                world = read(WORLD / (name + ".json"))
                overworld = world["dimensions"]["minecraft:overworld"]
                self.assertEqual(overworld["generator"]["settings"], "realscale:" + settings_id)
                self.assertEqual(
                    source_chain(overworld["generator"]["biome_source"]),
                    SHARED_BIOME_CHAIN,
                )
                self.assertEqual(overworld["type"], "realscale:realism_max_overworld")
                self.assertIn("minecraft:the_nether", world["dimensions"])
                self.assertIn("minecraft:the_end", world["dimensions"])

    def test_optional_special_presets_are_hidden_not_deleted(self):
        menu = read(TAG_ROOT / "normal.json")["values"]
        for name in ("wonders_invasion_three_realms", "cave_ocean_ecology_three_realms"):
            self.assertTrue((WORLD / (name + ".json")).is_file(), name)
            self.assertNotIn("realscale:" + name, menu)

    def test_legacy_world_preset_ids_remain_loadable(self):
        for old in OLD:
            self.assertTrue((WORLD / (old + ".json")).is_file(), old)

    def test_player_visible_localizations_exclude_unrelated_brand(self):
        for name in ("en_us.json", "zh_cn.json"):
            labels = read(RES / "assets/minecraft/lang" / name)
            for old, new in zip(OLD, NEW):
                self.assertEqual(
                    labels["generator.realscale." + old],
                    labels["generator.realscale." + new],
                )
                self.assertNotIn("TVA", labels["generator.realscale." + new])
                self.assertIn("RealScale", labels["generator.realscale." + new])

    def test_mod_name_and_archive_name_are_realscale_only(self):
        gradle = (ROOT / "neoforge/build.gradle").read_text()
        toml = (RES / "META-INF/neoforge.mods.toml").read_text()
        self.assertIn("2.0.0-preview.2-test.6", gradle)
        self.assertIn('version="2.0.0-preview.2-test.6"', toml)
        self.assertIn("RealScale-NeoForge-1.21.1", gradle)
        self.assertNotIn("TVA", toml)
        self.assertNotIn("RealScale-TVA", gradle)

    def test_compat_module_is_standalone(self):
        addon = ROOT / "dh-chunky-guard"
        self.assertTrue((addon / "build.gradle").is_file())
        self.assertTrue(
            (addon / "src/main/java/org/realscale/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java").is_file()
        )
        main = read(WORLD / "natural_three_realms.json")
        self.assertEqual(
            main["dimensions"]["minecraft:overworld"]["type"],
            "realscale:realism_max_overworld",
        )
        meta = (RES / "META-INF/neoforge.mods.toml").read_text()
        self.assertNotIn('modId="distanthorizons"', meta)
        self.assertNotIn('modId="chunky"', meta)


if __name__ == "__main__":
    unittest.main()
