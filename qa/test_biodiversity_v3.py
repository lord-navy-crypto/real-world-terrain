"""Experimental v3 biome diversity: strict append-only regression guards."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources/data/realscale"
BIOMES=RES/"worldgen/biome"
PRESETS=RES/"worldgen/world_preset"
MOD=RES/"neoforge/biome_modifier"
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge/NaturalEcotoneBiomeSource.java"
REG=ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java"
TAGS=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
NAMES=("mediterranean_chaparral","temperate_tallgrass_prairie",
       "monsoon_woodland","montane_cloud_scrub",
       "brackish_tidal_marsh","coastal_pine_barrens",
       "frost_heath_moor","arid_rocksteppe")
BASE=("tva_sacred_timeline_three_realms","tva_measured_three_realms")
COLORS=("grass_color","foliage_color","water_color")
def read(p):return json.loads(p.read_text(encoding="utf-8"))


class BiomeDiversityV3(unittest.TestCase):
    def test_all_eight_are_actual_wired_biomes_with_unique_climates_and_palettes(self):
        climates=set()
        for name in NAMES:
            with self.subTest(name=name):
                biome=read(BIOMES/(name+".json"))
                self.assertEqual(len(biome["features"]),11)
                self.assertTrue(biome["features"][9])
                self.assertTrue(biome["spawners"]["creature"])
                self.assertTrue(isinstance(biome["carvers"]["air"],list))
                self.assertTrue(0<=biome["downfall"]<=1)
                key=(biome["temperature"],biome["downfall"])
                self.assertNotIn(key,climates)
                climates.add(key)
                effects=biome["effects"]
                self.assertTrue(all(isinstance(effects[color],int) for color in COLORS))
                self.assertGreaterEqual(len({effects[c] for c in COLORS}),2)
                mod=read(MOD/("diverse_v3_"+name+"_undergrowth.json"))
                self.assertEqual(mod["type"],"neoforge:add_features")
                self.assertEqual(mod["biomes"],["realscale:"+name])
                self.assertEqual(mod["step"],"vegetal_decoration")
                self.assertTrue(mod["features"].startswith("realscale:ecotone_"))

    def test_versioned_codec_keeps_old_v1_v2_unchanged(self):
        java=SRC.read_text(encoding="utf-8")
        reg=REG.read_text(encoding="utf-8")
        start=java.index("private static final List<Rule> V3_RULES")
        end=java.index("private static final List<Rule> ALL_V3_RULES",start)
        section=java[start:end]
        for name in NAMES:
            self.assertIn('rule("'+name+'"',section)
        self.assertEqual(section.count('rule("'),8)
        self.assertIn('return diverse ? DIVERSE_CODEC : CODEC;',java)
        self.assertIn('for (Rule rule : diverse ? ALL_V2_RULES : RULES)',java)
        self.assertIn('if (expandedV3) return DIVERSE_V3_CODEC;',java)
        self.assertIn('natural_ecotones_diverse_v3',reg)
        self.assertIn('NaturalEcotoneBiomeSource.DIVERSE_V3_CODEC',reg)
        self.assertIn('V3_RULE_BY_BIOME',java)
        self.assertIn('V3_CANDIDATE_IDS',java)
        self.assertIn('V3_MAX_WIDTH_BY_BIOME',java)
        self.assertIn('protectedWater(current)',java)

    def test_historical_versions_are_not_player_facing(self):
        normal=read(TAGS/"normal.json")["values"]
        extended=read(TAGS/"extended.json")["values"]
        expected=[
            "realscale:tva_natural_hypsometry_v1_three_realms",
            "realscale:tva_measured_usgs_fitted_v1_three_realms",
            "realscale:tva_alpine_three_realms",
            "realscale:tva_canyon_three_realms"
        ]
        self.assertEqual(normal,expected)
        self.assertEqual(extended,expected)
        # Historical v2/v3 files remain valid as test fixtures and for
        # old exported profiles, not as duplicate menu entries.
        for family,legacy in zip(("sacred","measured"),BASE):
            v2="tva_"+family+"_biodiversity_end3_spawn3_v2"
            v3="tva_"+family+"_biodiversity_end3_spawn3_v3"
            self.assertNotIn("realscale:"+v2,normal)
            old=read(PRESETS/(legacy+".json"))
            prev=read(PRESETS/(v2+".json"))
            new=read(PRESETS/(v3+".json"))
            self.assertEqual(new["dimensions"]["minecraft:the_nether"],prev["dimensions"]["minecraft:the_nether"])
            self.assertEqual(new["dimensions"]["minecraft:the_end"],prev["dimensions"]["minecraft:the_end"])
            for realm in old["dimensions"]:
                expected_setting=old["dimensions"][realm]["generator"]["settings"]
                # Featured Sacred v3 is now a new versioned macro noise profile.
                # This must not mutate any historical Sacred setting/fixture.
                if family=="sacred" and realm=="minecraft:overworld":
                    expected_setting="realscale:tva_cinematic_integrated_v1"
                self.assertEqual(new["dimensions"][realm]["generator"]["settings"],
                                 expected_setting)
                self.assertEqual(new["dimensions"][realm]["type"],old["dimensions"][realm]["type"])
            p=prev["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
            q=new["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
            self.assertEqual(q["type"],"realscale:sky_ocean")
            self.assertEqual(q["source"]["type"],"realscale:rare_earth_biomes_v1")
            invasion=q["source"]["source"]
            if family=="sacred":
                # Geological wonders from the removed menu option now remain
                # reachable in the featured Natural Exploration preset.
                self.assertEqual(invasion["type"],"realscale:geo_wonders_opt_in")
                invasion=invasion["source"]
            self.assertEqual(invasion["type"],
                "realscale:cross_realm_free_spawn_v1" if family=="sacred"
                else "realscale:cross_realm_spawn3")
            # Archived v2 keeps its persisted forced-spawn codec unchanged.
            self.assertEqual(p["source"]["source"]["type"],"realscale:cross_realm_spawn3")
            self.assertEqual(invasion["target"],"overworld")
            self.assertEqual(invasion["source"]["type"],"realscale:natural_ecotones_diverse_v3")
            self.assertEqual(p["source"]["source"]["source"]["type"],"realscale:natural_ecotones_diverse_v2")
            self.assertEqual(invasion["source"]["source"],
                             p["source"]["source"]["source"]["source"])

    def test_unique_labels_for_world_and_biomes(self):
        for lang in ("en_us","zh_cn"):
            labels=read(ROOT/"neoforge/src/main/resources/assets/realscale/lang"/(lang+".json"))
            for biome in NAMES:
                self.assertTrue(labels["biome.realscale."+biome])
            for family in ("sacred","measured"):
                self.assertTrue(labels["generator.realscale.tva_"+family+"_biodiversity_end3_spawn3_v3"])

    def test_all_biome_ids_preserve_legacy_catalog_and_rare_ecology(self):
        ids={p.stem for p in BIOMES.glob("*.json")}
        rare={"geothermal_sulfur_basin","socotra_dragonblood_woodland",
              "white_dune_lagoon","sundarbans_tidal_mangrove",
              "congo_peat_swamp_forest","steppe_saline_lake",
              "acidic_geothermal_mudflat","montane_mist_forest",
              "permafrost_fen","limestone_island_scrub",
              "namib_fog_desert","serpentine_barrens","socotra_endemic_scrub"}
        self.assertEqual(len(ids),97)
        self.assertTrue(rare <= ids)
        self.assertEqual(len(ids-set(NAMES)-rare),76)


if __name__=="__main__":unittest.main()
