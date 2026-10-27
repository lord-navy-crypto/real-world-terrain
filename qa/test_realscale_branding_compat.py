"""Player-facing RealScale branding and historical world-save compatibility."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources"
WORLD=RES/"data/realscale/worldgen/world_preset"
OLD=[
    "tva_sacred_timeline_three_realms",
    "tva_alpine_three_realms",
    "tva_canyon_three_realms",
    "tva_measured_three_realms",
    "tva_wonders_invasion_three_realms",
    "tva_cave_ocean_ecology_test_three_realms",
]
NEW=[
    "natural_three_realms",
    "alpine_three_realms",
    "canyon_three_realms",
    "measured_three_realms",
    "wonders_invasion_three_realms",
    "cave_ocean_ecology_three_realms",
]
def read(path):
    return json.loads(path.read_text(encoding="utf-8"))
class RealScaleBrandingAndSaveCompatibility(unittest.TestCase):
    def test_new_menu_uses_only_new_realscale_preset_ids(self):
        menu=["realscale:natural_three_realms","realscale:usgs_big_sur"]
        for name in ("normal","extended"):
            tag=read(RES/"data/minecraft/tags/worldgen/world_preset"/(name+".json"))
            self.assertEqual(tag["values"],menu)
            self.assertEqual(len(set(menu)),len(menu))
    def test_new_presets_are_exact_aliases_not_regenerated_worlds(self):
        # Preserve all generator settings, biome sources and dimensions
        # exactly, rather than inadvertently resetting RealScale terrain.
        for old,new in zip(OLD,NEW):
            with self.subTest(preset=new):
                self.assertEqual(read(WORLD/(old+".json")),read(WORLD/(new+".json")))
    def test_legacy_world_presets_remain_loadable_resources(self):
        for old in OLD:
            self.assertTrue((WORLD/(old+".json")).is_file(),old)
    def test_player_visible_localizations_exclude_unrelated_brand(self):
        for name in ("en_us.json","zh_cn.json"):
            labels=read(RES/"assets/minecraft/lang"/name)
            for old,new in zip(OLD,NEW):
                self.assertEqual(labels["generator.realscale."+old],
                                 labels["generator.realscale."+new])
                self.assertNotIn("TVA",labels["generator.realscale."+new])
                self.assertIn("RealScale",labels["generator.realscale."+new])
    def test_mod_name_and_archive_name_are_realscale_only(self):
        gradle=(ROOT/"neoforge/build.gradle").read_text()
        toml=(RES/"META-INF/neoforge.mods.toml").read_text()
        self.assertIn("2.0.0-preview.2-test.6",gradle)
        self.assertIn('version="2.0.0-preview.2-test.6"',toml)
        self.assertIn("RealScale-NeoForge-1.21.1",gradle)
        self.assertNotIn("TVA",toml)
        self.assertNotIn("RealScale-TVA",gradle)
    def test_compat_module_is_standalone(self):
        addon=ROOT/"dh-chunky-guard"
        self.assertTrue((addon/"build.gradle").is_file())
        self.assertTrue((addon/"src/main/java/org/realscale/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java").is_file())
        main=read(WORLD/(NEW[0]+".json"))
        self.assertEqual(main["dimensions"]["minecraft:overworld"]["type"],
                         "realscale:realism_max_overworld")
        meta=(RES/"META-INF/neoforge.mods.toml").read_text()
        self.assertNotIn('modId="distanthorizons"',meta)
        self.assertNotIn('modId="chunky"',meta)
if __name__=="__main__":
    unittest.main()
