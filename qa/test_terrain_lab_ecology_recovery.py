"""Only newly exported unified Terrain Lab profiles gain all recovered ecosystems."""
import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from unified_world_settings import invasion_preset, recover_terrain_lab_ecology

PRESETS=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/world_preset"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

def read(name):
    return json.loads((PRESETS/(name+".json")).read_text(encoding="utf-8"))

def chain(source):
    result=[]
    while isinstance(source,dict) and "type" in source:
        result.append(source["type"])
        source=source.get("source")
    return result

class TerrainLabEcologyRecovery(unittest.TestCase):
    def test_recovery_keeps_noise_realm_settings_and_archival_source(self):
        for name in ("tva_sacred_timeline_three_realms",
                     "tva_alpine_three_realms",
                     "tva_canyon_three_realms"):
            with self.subTest(name=name):
                archive=read(name)
                before=copy.deepcopy(archive)
                invaded=invasion_preset(archive,spawn_end_habitats=True)
                recovered=recover_terrain_lab_ecology(invaded)
                dims=recovered["dimensions"]
                self.assertEqual(archive,before)
                for dim in dims:
                    self.assertEqual(dims[dim]["generator"]["settings"],
                                     archive["dimensions"][dim]["generator"]["settings"])
                self.assertEqual(dims["minecraft:the_nether"],
                                 invaded["dimensions"]["minecraft:the_nether"])
                self.assertEqual(dims["minecraft:the_end"],
                                 invaded["dimensions"]["minecraft:the_end"])
                outer=dims["minecraft:overworld"]["generator"]["biome_source"]
                self.assertEqual(chain(outer),[
                    "realscale:sky_ocean",
                    "realscale:rare_earth_biomes_v1",
                    "realscale:geo_wonders_opt_in",
                    "realscale:cross_realm_spawn3",
                    "realscale:natural_ecotones_diverse_v3",
                    "minecraft:multi_noise",
                ])
                self.assertEqual(recover_terrain_lab_ecology(recovered),recovered)

    def test_existing_invasion_kept_without_spawning_new_mode(self):
        base=read("tva_sacred_timeline_three_realms")
        world=recover_terrain_lab_ecology(invasion_preset(base))
        types=chain(world["dimensions"]["minecraft:overworld"]["generator"]["biome_source"])
        self.assertIn("realscale:cross_realm_invasion",types)
        self.assertNotIn("realscale:cross_realm_spawn3",types)

    def test_showcase_outer_layer_remains(self):
        base=invasion_preset(read("tva_sacred_timeline_three_realms"))
        original=base["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        base["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]={
            "type":"realscale:cross_realm_showcase","source":original}
        saved=recover_terrain_lab_ecology(base)
        actual=chain(saved["dimensions"]["minecraft:overworld"]["generator"]["biome_source"])
        self.assertEqual(actual[:4],[
            "realscale:cross_realm_showcase",
            "realscale:sky_ocean",
            "realscale:rare_earth_biomes_v1",
            "realscale:geo_wonders_opt_in",
        ])

    def test_unsupported_sources_rejected_without_mutation(self):
        source=read("tva_sacred_timeline_three_realms")
        old=copy.deepcopy(source)
        with self.assertRaisesRegex(ValueError,"existing invasion"):
            recover_terrain_lab_ecology(source)
        self.assertEqual(old,source)

    def test_scenery_requires_natural_foundation_and_source_water(self):
        impl=(JAVA/"ScenicLandscapeFeature.java").read_text()
        self.assertIn("blocks.containsKey(p.below())",impl)
        self.assertIn("natural(w.getBlockState(p.below()))",impl)
        self.assertIn("w.getFluidState(p).isSource()",impl)
        self.assertIn("owns(chunk,p)",impl)

if __name__=="__main__":unittest.main()
