#!/usr/bin/env python3
"""Prove the opt-in three-End-biome layer preserves foreign ID delegation.

Registry/JSON/source contracts only. Runtime spawn/End-sampling checks are
handled by the separate NeoForge RCON workflow.
"""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
sys.path.insert(0,str(ROOT/"scripts"))
from export_terrain_lab_datapack import export, _end_biome_choices
from unified_world_settings import create_preset

BIOMES=("end_chorus_gardens","end_crystal_barrens","end_shattered_glades")

class GrandEndThreeBiomeTests(unittest.TestCase):
    def test_registry_names_and_biome_source_foreign_passthrough(self):
        src=(JAVA/"EndAccentBiomeSource.java").read_text()
        registrar=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('id.equals("minecraft:end_highlands")',src)
        self.assertIn('id.equals("minecraft:end_midlands")',src)
        self.assertIn('id.equals("minecraft:small_end_islands")',src)
        self.assertIn('if (!eligibleBaseBiome(id)) return original;',src)
        self.assertIn('DRAGON_ISLAND_EXCLUSION = 1024L * 1024L',src)
        self.assertIn('if (y < 32 || y > 384) return original;',src)
        self.assertIn('"end_accents"',registrar)
        self.assertIn('EndAccentBiomeSource.CODEC',registrar)
        for key in BIOMES:
            with self.subTest(biome=key):
                self.assertIn('"' + key + '"',src)
                self.assertIn('"' + key + '"',registrar)

    def test_biomes_decoration_and_chunk_safety(self):
        impl=(JAVA/"EndAccentFeature.java").read_text()
        self.assertIn('Blocks.END_STONE',impl)
        self.assertIn('world.getBiome(above).is(kind.biome)',impl)
        self.assertIn('Math.floorDiv(above.getX(),16)!=chunkX',impl)
        self.assertIn('!world.getBlockState(above).isAir()',impl)
        self.assertNotIn('Blocks.LAVA',impl)
        for name in BIOMES:
            with self.subTest(name=name):
                biome=json.loads((RES/f"data/realscale/worldgen/biome/{name}.json").read_text())
                self.assertEqual(len(biome["features"]),11)
                self.assertEqual(biome["spawners"],{})
                self.assertEqual(biome["spawn_costs"],{})
                feature=json.loads((RES/f"data/realscale/worldgen/configured_feature/{name}.json").read_text())
                placed=json.loads((RES/f"data/realscale/worldgen/placed_feature/{name}.json").read_text())
                modifier=json.loads((RES/f"data/realscale/neoforge/biome_modifier/{name}.json").read_text())
                self.assertEqual(feature,{"type":"realscale:"+name,"config":{}})
                self.assertEqual(placed["feature"],"realscale:"+name)
                self.assertEqual(placed["placement"][0],
                                 {"type":"minecraft:count","count":2})
                self.assertEqual(placed["placement"][-1]["type"],"minecraft:biome")
                self.assertEqual(modifier["type"],"neoforge:add_features")
                self.assertEqual(modifier["biomes"],"realscale:"+name)
                self.assertEqual(modifier["features"],"realscale:"+name)
                self.assertEqual(modifier["step"],"vegetal_decoration")

    def test_composition_preserves_all_original_dimension_noise(self):
        for base in ("sacred","measured"):
            original, old, _ = create_preset(RES, base, True)
            name, updated, labels=create_preset(RES,base,True,end_biomes=True)
            self.assertEqual(name,original+"_end3")
            self.assertIn("Three End Biomes",labels["en_us"])
            for dim in ("minecraft:overworld","minecraft:the_nether"):
                self.assertEqual(updated["dimensions"][dim],old["dimensions"][dim])
            a=updated["dimensions"]["minecraft:the_end"]["generator"]
            b=old["dimensions"]["minecraft:the_end"]["generator"]
            self.assertEqual(a["settings"],b["settings"])
            self.assertEqual(a["biome_source"],{
                "type":"realscale:end_accents","source":b["biome_source"]})
            show, with_show, _ = create_preset(RES,base,True,showcase=True,end_biomes=True)
            self.assertTrue(show.endswith("_end3"))
            self.assertEqual(with_show["dimensions"]["minecraft:the_end"]
                  ["generator"]["biome_source"]["type"],"realscale:cross_realm_showcase")
            self.assertEqual(with_show["dimensions"]["minecraft:the_end"]
                  ["generator"]["biome_source"]["source"]["type"],"realscale:end_accents")

    def test_exporter_legacy_off_and_unified_on_opt_out(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"draft.json"
            p.write_text('{"preset":"landscape"}')
            self.assertFalse(_end_biome_choices(p))
            p.write_text('{"preset":"landscape","worldSource":"sacred"}')
            self.assertTrue(_end_biome_choices(p))
            p.write_text('{"preset":"landscape","worldSource":"sacred","endBiomeAccents":false}')
            self.assertFalse(_end_biome_choices(p))
            p.write_text('{"preset":"landscape","worldSource":"sacred","endBiomeAccents":"true"}')
            with self.assertRaises(ValueError):
                _end_biome_choices(p)

    def test_full_zip_export(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            jar=root/"RealScale.jar"
            draft=root/"draft.json"
            with zipfile.ZipFile(jar,"w") as zip:
                for prefix in ("data/realscale/worldgen",
                               "data/minecraft/tags/worldgen/world_preset",
                               "assets/minecraft/lang"):
                    for file in (RES/prefix).rglob("*.json"):
                        zip.write(file,file.relative_to(RES).as_posix())
            for family in ("sacred","measured"):
                draft.write_text(json.dumps({
                    "preset":"landscape","reliefPercent":100,
                    "elevation":0,"caves":50,"riverGrade":6,"inland":True,
                    "worldSource":family,"dimensionalInvasion":True,
                    "endBiomeAccents":True}))
                path=root/(family+".zip")
                profile,_=export(jar,draft,path,include_resource_pack=False)
                self.assertTrue(profile.endswith("_invasion_v1_end3"))
                with zipfile.ZipFile(path) as out:
                    obj=json.loads(out.read("data/realscale/worldgen/world_preset/"+
                                            profile.split(":")[1]+".json"))
                    self.assertEqual(obj["dimensions"]["minecraft:the_end"]
                                    ["generator"]["biome_source"]["type"],
                                     "realscale:end_accents")
                    self.assertNotIn("data/realscale/worldgen/world_preset/"+
                                     family+".json",out.namelist())

if __name__=="__main__":
    unittest.main()
