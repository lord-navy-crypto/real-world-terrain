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
    "alpine_three_realms": "tva_alpine_fitted",
    "canyon_three_realms": "tva_canyon_fitted",
    "glacial_three_realms": "tva_glacial_fitted",
    "volcanic_three_realms": "tva_volcanic_fitted",
    "karst_three_realms": "tva_karst_fitted",
    "dune_three_realms": "tva_dune_fitted",
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
    def test_stable_world_menu_has_only_natural_and_usgs_v2(self):
        # Vanilla entries remain available because the tag does not replace
        # Minecraft's list; only these two RealScale entries are added.
        menu = ["realscale:natural_three_realms", "realscale:usgs_big_sur_v2"]
        for name in ("normal", "extended"):
            tag = read(TAG_ROOT / (name + ".json"))
            self.assertFalse(tag["replace"])
            self.assertEqual(tag["values"], menu)
            self.assertEqual(len(set(tag["values"])), 2)

        natural = read(WORLD / "natural_three_realms.json")
        usgs = read(WORLD / "usgs_big_sur_v2.json")
        self.assertEqual(
            natural["dimensions"]["minecraft:overworld"]["generator"]["settings"],
            "realscale:tva_natural_smoothed",
        )
        self.assertEqual(
            usgs["dimensions"]["minecraft:overworld"]["generator"]["settings"],
            "realscale:usgs_big_sur_v2",
        )
        self.assertEqual(
            usgs["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["type"],
            "minecraft:multi_noise",
        )

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

    def test_six_fitted_modes_change_only_overworld_noise_settings(self):
        reference = None
        for name in (
            "alpine_three_realms",
            "canyon_three_realms",
            "glacial_three_realms",
            "volcanic_three_realms",
            "karst_three_realms",
            "dune_three_realms",
        ):
            with self.subTest(preset=name):
                world = read(WORLD / (name + ".json"))
                dimensions = world["dimensions"]
                self.assertEqual(set(dimensions), {
                    "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
                })
                self.assertEqual(
                    dimensions["minecraft:overworld"]["generator"]["biome_source"]["type"],
                    "realscale:sky_ocean",
                )
                comparable = json.loads(json.dumps(dimensions))
                comparable["minecraft:overworld"]["generator"]["settings"] = "__FITTED_ARCHETYPE__"
                if reference is None:
                    reference = comparable
                else:
                    self.assertEqual(
                        comparable,
                        reference,
                        "terrain presets must share universal biome/structure and cross-realm wiring",
                    )

    def test_experimental_and_legacy_presets_hidden_not_deleted(self):
        for tag_name in ("normal", "extended"):
            menu = read(TAG_ROOT / (tag_name + ".json"))["values"]
            for name in (
                "alpine_three_realms", "canyon_three_realms",
                "glacial_three_realms", "volcanic_three_realms",
                "karst_three_realms", "dune_three_realms",
                "measured_three_realms",
                "wonders_invasion_three_realms", "cave_ocean_ecology_three_realms",
                *OLD,
            ):
                with self.subTest(tag=tag_name, preset=name):
                    self.assertTrue((WORLD / (name + ".json")).is_file(), name)
                    self.assertNotIn("realscale:" + name, menu)

    def test_six_fitted_profiles_have_real_source_provenance_and_no_raw_grid(self):
        profile_root = ROOT / "research/v1.1/profiles"
        provenance = read(profile_root / "terrain-profile-provenance.json")
        self.assertEqual(provenance["profile_count"], 6)
        self.assertFalse(provenance["raw_dem_grids_embedded"])
        targets = {
            "everest_himalaya": "alpine",
            "grand_canyon": "canyon",
            "yosemite_glacial_valley": "glacial",
            "hawaii_volcanic": "volcanic",
            "guilin_karst": "karst",
            "namib_dunes": "dune",
        }
        for target, family in targets.items():
            with self.subTest(target=target):
                profile = read(profile_root / (target + ".json"))
                self.assertEqual(profile["family"], family)
                self.assertEqual(profile["source"]["source_dataset"], "Copernicus DEM GLO-90")
                self.assertEqual(profile["source"]["vertical_datum"], "EGM2008")
                self.assertEqual(len(profile["source"]["sha256"]), 64)
                self.assertEqual(len(profile["source"]["source_tile_sha256"]), 64)
                self.assertFalse(profile["generation_contract"]["raw_elevation_samples_embedded"])
                self.assertIn("relief_gain", profile["fit_parameters"])
                self.assertGreater(profile["observed_statistics"]["relief_p95_minus_p05_m"], 0)

    def test_runtime_density_kernels_use_the_fitted_profile_coefficients(self):
        profile_targets = {
            "alpine_three_realms": ("everest_himalaya", "tva_alpine_fitted"),
            "canyon_three_realms": ("grand_canyon", "tva_canyon_fitted"),
            "glacial_three_realms": ("yosemite_glacial_valley", "tva_glacial_fitted"),
            "volcanic_three_realms": ("hawaii_volcanic", "tva_volcanic_fitted"),
            "karst_three_realms": ("guilin_karst", "tva_karst_fitted"),
            "dune_three_realms": ("namib_dunes", "tva_dune_fitted"),
        }
        profiles_root = ROOT / "research/v1.1/profiles"
        density_root = RES / "data/realscale/worldgen/density_function"
        for preset, (target, settings_id) in profile_targets.items():
            with self.subTest(preset=preset):
                profile = read(profiles_root / (target + ".json"))
                density = read(density_root / settings_id / "height_signal.json")
                fit = profile["fit_parameters"]
                self.assertEqual(density["family"], profile["family"])
                self.assertEqual(density["dominant_wavelength_m"], fit["dominant_wavelength_m"])
                self.assertEqual(density["relief_gain"], fit["relief_gain"])
                self.assertAlmostEqual(density["dune_axis_radians"], fit["dune_axis_radians"], places=5)
                self.assertEqual(density["macro_gain"], fit["macro_gain"])
                self.assertEqual(density["cliff_index"], fit["cliff_index"])
                self.assertEqual(density["valley_index"], fit["valley_index"])

    def test_legacy_world_preset_ids_remain_loadable(self):
        for old in OLD:
            self.assertTrue((WORLD / (old + ".json")).is_file(), old)

    def test_player_visible_localizations_exclude_unrelated_brand(self):
        for name in ("en_us.json", "zh_cn.json"):
            labels = read(RES / "assets/minecraft/lang" / name)
            # Legacy labels remain available for saved-world compatibility;
            # canonical fitted presets intentionally add their reference region.
            for old in OLD:
                self.assertIn("RealScale", labels["generator.realscale." + old])
                self.assertNotIn("TVA", labels["generator.realscale." + old])
            for preset in CANONICAL:
                self.assertIn("RealScale", labels["generator.realscale." + preset])
                self.assertNotIn("TVA", labels["generator.realscale." + preset])

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
