#!/usr/bin/env python3
"""Static regression for optional geology/ecology & 2x/3x structures ZIPs."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
import make_geoecology_suite as e
import make_vanilla_ecology_pack as old
import make_structure_density_packs as structures

class GeoEcologyTests(unittest.TestCase):
    def test_previous_microhabitats_retained_unchanged(self):
        original=old.resources()
        new=e.resources()
        for p,val in original.items():
            if p=="pack.mcmeta":continue
            self.assertEqual(new[p],val)
        e.validate(new)

    def test_cold_snow_and_cave_lithology_separate_from_existing_worldgen(self):
        data=e.resources()
        self.assertIn("alpine_dry_powder_snow",e.PATCHES)
        self.assertIn("alpine_wind_snow",e.PATCHES)
        self.assertIn("winter_taiga_snow",e.PATCHES)
        self.assertIn("karst_calcite_lenses",e.POCKETS)
        self.assertIn("deep_tuff_lenses",e.POCKETS)
        self.assertTrue(all("/noise_settings/" not in p and
                            "/density_function/" not in p and
                            "/world_preset/" not in p and
                            "/structure_set/" not in p
                            for p in data))
        for name,p in e.PATCHES.items():
            self.assertTrue(all(i.startswith("minecraft:") for i in p["biomes"]))
            self.assertLessEqual(p["count"],2)
            self.assertLessEqual(p["tries"],16)
            self.assertEqual(data[f"data/{e.NS}/neoforge/biome_modifier/{name}.json"]["step"],
                             p["step"])
        for name,p in e.POCKETS.items():
            self.assertLessEqual(p["size"],9)
            r=data[f"data/{e.NS}/worldgen/configured_feature/{name}.json"]
            self.assertEqual(r["config"]["targets"][0]["target"]["block"],
                             "minecraft:stone")
            self.assertIn(p["block"],("minecraft:calcite","minecraft:tuff"))

    def test_desert_outcrops_support_non_sandy_arid_surfaces(self):
        source=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/GrandEcologyFeature.java").read_text()
        start=source.index("private static boolean desertOutcrop(")
        end=source.index("private static boolean arcticStones(",start)
        method=source[start:end]
        for material in ("GRAVEL","STONE","TERRACOTTA","COARSE_DIRT",
                         "SAND","RED_SAND"):
            self.assertIn("ground.is(Blocks."+material+")",method)
        self.assertIn("Blocks.COBBLESTONE.defaultBlockState()",method)
        self.assertIn("Blocks.TERRACOTTA.defaultBlockState()",method)
        self.assertIn("!level.getFluidState(base.below()).isEmpty()",method)
        self.assertIn("!level.getBlockState(pos).isAir()",method)

    def test_real_ecology_zip_readable(self):
        with tempfile.TemporaryDirectory() as t:
            filename=Path(t)/"eco.zip"
            e.build(filename)
            with ZipFile(filename) as z:
                self.assertIsNone(z.testzip())
                self.assertEqual(set(z.namelist()),set(e.resources()))
                for path,expected in e.resources().items():
                    self.assertEqual(json.loads(z.read(path)),expected)

class VanillaStructureDensityTests(unittest.TestCase):
    def test_densities_keep_only_spacing_changes(self):
        for multiplier in (2,3):
            data=structures.pack_resources(multiplier)
            structures.validate(multiplier,data)
            self.assertEqual(len(data),len(structures.VANILLA)+1)
            self.assertNotIn("data/minecraft/worldgen/structure_set/strongholds.json",data)
            self.assertNotIn("data/minecraft/worldgen/structure_set/pillager_outposts.json",data)
            self.assertNotIn("data/minecraft/worldgen/structure_set/ruined_portals.json",data)
            for name in structures.VANILLA:
                p=f"data/minecraft/worldgen/structure_set/{name}.json"
                original=structures.original_set(name)
                modified=data[p]
                self.assertEqual(original["structures"],modified["structures"])
                self.assertEqual(original["placement"]["salt"],
                                 modified["placement"]["salt"])
                self.assertEqual(original["placement"]["separation"],
                                 modified["placement"]["separation"])
                self.assertLess(modified["placement"]["spacing"],
                                original["placement"]["spacing"])

    def test_target_village_candidate_density(self):
        original=structures.original_set("villages")["placement"]["spacing"]
        two=structures.pack_resources(2)[
            "data/minecraft/worldgen/structure_set/villages.json"]["placement"]["spacing"]
        three=structures.pack_resources(3)[
            "data/minecraft/worldgen/structure_set/villages.json"]["placement"]["spacing"]
        self.assertEqual((original,two,three),(34,24,20))
        self.assertAlmostEqual((original/two)**2,2,delta=0.2)
        self.assertAlmostEqual((original/three)**2,3,delta=0.2)

    def test_independent_zip_products(self):
        with tempfile.TemporaryDirectory() as t:
            out=structures.build(Path(t))
            self.assertEqual(len(out),6)
            self.assertEqual(len(set(path.name for path in out)),6)
            for target in out:
                with ZipFile(target) as z:
                    self.assertIsNone(z.testzip())
                    target_length = (len(structures.DIMENSIONAL) + 1
                        if "Nether-End" in target.name
                        else len(structures.VANILLA) + 1)
                    self.assertEqual(len(z.namelist()),target_length)
                    self.assertEqual(json.loads(z.read("pack.mcmeta"))["pack"]["pack_format"],48)

if __name__=="__main__":
    unittest.main()
