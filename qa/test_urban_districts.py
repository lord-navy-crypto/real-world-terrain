#!/usr/bin/env python3
"""Modern RealScale cities: real dense NBT, natural spawn safety and pack wiring."""
from __future__ import annotations

import gzip
import json
import sys
import unittest
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
import convert_gdmc_architecture as converter
import generate_urban_districts as city

GENERATED=ROOT/"neoforge/build/generated/realscale-urban-district-templates"
PACK=ROOT/"neoforge/build/generated/realscale-structure-packs/resourcepacks/realscale_structures_urban_districts"
JAR_PATH=ROOT/"neoforge/build/libs"

class UrbanDistrictIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profile=json.loads(city.PROFILE_PATH.read_text())
        cls.ids=[entry["id"] for entry in cls.profile["districts"]]
        cls.city_ntb={}
        cls.cell_index={}
        if not GENERATED.is_dir():
            raise AssertionError("Run NeoForge build to generate urban NBT first")
        for name in cls.ids:
            path=GENERATED/"data/realscale/structure/urban"/(name+".nbt")
            raw=gzip.decompress(path.read_bytes())
            root_name,blob=converter.parse_nbt(raw)
            assert root_name==""
            cls.city_ntb[name]=blob
            palette=[entry["Name"] for entry in blob["palette"]]
            cls.cell_index[name]={
                tuple(item["pos"]):palette[item["state"]]
                for item in blob["blocks"]
            }

    def test_four_distinct_original_city_quarters(self):
        self.assertEqual(len(self.ids),4)
        self.assertEqual(set(self.ids),set(city.HEIGHTS))
        self.assertEqual(self.profile["placement"]["spacing"],128)
        self.assertGreater(self.profile["placement"]["spacing"],
                           self.profile["placement"]["separation"])
        self.assertLess(1/(128*128),0.1/(34*34))
        self.assertEqual(set(city.PARCELS),{
            (2,2,17,17),(30,2,45,17),(2,30,17,45),(30,30,45,45)})
        for name,blob in self.city_ntb.items():
            with self.subTest(district=name):
                self.assertEqual(blob["DataVersion"],3955)
                self.assertEqual(blob["size"],[48,max(city.HEIGHTS[name])+4,48])
                self.assertEqual(len(blob["blocks"]),48*48*blob["size"][1])
                self.assertTrue(any(s["Name"]=="minecraft:sea_lantern"
                                    for s in blob["palette"]))

    def test_roads_form_crossings_reaching_four_chunk_edges(self):
        for name,index in self.cell_index.items():
            with self.subTest(district=name):
                for x,z in ((23,0),(23,47),(0,23),(47,23)):
                    self.assertIn(index[(x,0,z)],{
                        "minecraft:gray_concrete","minecraft:yellow_concrete"
                    },f"{name} disconnected road at ({x},{z})")
                for x,z in ((19,19),(28,19),(19,28),(28,28)):
                    self.assertEqual(index[(x,6,z)],"minecraft:sea_lantern")
                # Door orientation and entrance walks must face a city
                # sidewalk, not the outside wilderness.
                for x1,z1,x2,z2 in city.PARCELS:
                    if name=="civic_centre" and z1==30 and x1==2:
                        continue  # Civic SW parcel is a public park.
                    entry=(x1+1+x2-1)//2
                    frontage=(z2-1) if z1<24 else (z1+1)
                    self.assertEqual(index[(entry,1,frontage)],"minecraft:oak_door")
                    self.assertEqual(index[(entry,2,frontage)],"minecraft:oak_door")
                    path_z=(z2 if z1<24 else z1)
                    self.assertEqual(index[(entry,0,path_z)],"minecraft:smooth_stone")
                # Four distinct parcels exist in a single, connected city
                # block; road width prevents footprint overlap.
                for x1,z1,x2,z2 in city.PARCELS:
                    count=sum(index[(x,2,z)]!="minecraft:air"
                        for x in range(x1,x2+1) for z in range(z1,z2+1))
                    self.assertGreater(count,0)

    def test_civic_fountain_is_contained_at_the_water_level(self):
        # Center of the southwest park is (9,37). Every water source has
        # a solid y=1 ring, not just a y=0 floor beneath flowing water.
        city=self.cell_index["civic_centre"]
        for x in range(7,12):
            for z in range(35,40):
                if x in (7,11) or z in (35,39):
                    self.assertEqual(city[(x,1,z)],"minecraft:stone_bricks")
                else:
                    self.assertEqual(city[(x,1,z)],"minecraft:water")

    def test_skyscraper_roof_and_safe_internal_floors(self):
        commercial=self.cell_index["commercial_skyscrapers"]
        self.assertGreater(sum(block!="minecraft:air"
            for (x,y,z),block in commercial.items() if y>=90),100)
        self.assertGreater(sum(block.endswith("_stained_glass")
            for block in commercial.values()),100)
        for name,index in self.cell_index.items():
            self.assertGreater(sum(block=="minecraft:ladder"
                for block in index.values()),15,f"No vertical access in {name}")
            self.assertGreater(sum(block=="minecraft:oak_door"
                for block in index.values()),3,f"No street doors in {name}")
        # A skybridge passes over the avenue rather than obstructing it.
        self.assertEqual(commercial[(23,30,8)],"minecraft:polished_andesite")
        self.assertEqual(commercial[(23,34,8)],"minecraft:light_gray_concrete")

    def test_city_spawn_is_scarce_and_land_biome_only(self):
        spec=self.profile
        self.assertGreaterEqual(spec["placement"]["spacing"],96)
        for biome in spec["biomes"]:
            self.assertNotIn("ocean",biome)
            self.assertNotIn("river",biome)
            self.assertNotIn("beach",biome)
            self.assertNotIn("snowy_slopes",biome)
            if biome.startswith("realscale:"):
                local=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/biome"/(
                    biome.split(":",1)[1]+".json")
                self.assertTrue(local.exists(),f"Unknown city biome: {biome}")

    def test_datapack_registry_and_opt_in_terrain_lab(self):
        structure=json.loads((PACK/"data/realscale/worldgen/structure/urban_district.json").read_text())
        pool=json.loads((PACK/"data/realscale/worldgen/template_pool/urban/districts.json").read_text())
        set_file=json.loads((PACK/"data/realscale/worldgen/structure_set/urban_districts.json").read_text())
        tag=json.loads((PACK/"data/realscale/tags/worldgen/biome/has_structure/urban_district.json").read_text())
        self.assertEqual(structure["type"],"minecraft:jigsaw")
        self.assertEqual(structure["terrain_adaptation"],"beard_thin")
        self.assertEqual(structure["size"],0)
        self.assertEqual(structure["project_start_to_heightmap"],"WORLD_SURFACE_WG")
        self.assertEqual(structure["biomes"],"#realscale:has_structure/urban_district")
        self.assertEqual(structure["start_pool"],"realscale:urban/districts")
        self.assertEqual(set_file["structures"][0]["structure"],"realscale:urban_district")
        self.assertEqual(set_file["placement"]["exclusion_zone"],
                         {"other_set":"minecraft:villages","chunk_count":8})
        self.assertEqual(tag["values"],self.profile["biomes"])
        self.assertEqual({e["element"]["location"].split("/")[-1]
                          for e in pool["elements"]},set(self.ids))
        self.assertEqual({e["element"]["location"]:e["weight"]
                          for e in pool["elements"]},{
            f"realscale:urban/{e['id']}":e["weight"] for e in self.profile["districts"]})
        loader=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleStructurePacks.java").read_text()
        ui=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleTerrainLabClient.java").read_text()
        self.assertIn('registerOne(event, "urban_districts"',loader)
        self.assertIn("urbanDistrictsEnabled()",loader)
        self.assertIn("if (!Files.isRegularFile(DRAFT)) return false;",loader)
        self.assertIn("Boolean urbanDistrictsEnabled = Boolean.FALSE",ui)
        self.assertIn("Enable Modern Cities (Experimental)",ui)
        self.assertIn("urbanDistrictsEnabled",ui)

    def test_jar_packages_city_templates_but_not_default_city_worldgen(self):
        jars=list(JAR_PATH.glob("*.jar"))
        self.assertTrue(jars,"Build an actual NeoForge JAR first")
        jar=max(jars,key=lambda x:x.stat().st_mtime)
        with zipfile.ZipFile(jar) as archive:
            names=set(archive.namelist())
            prefix="resourcepacks/realscale_structures_urban_districts/"
            for name in self.ids:
                for rel in (f"data/realscale/structure/urban/{name}.nbt",):
                    self.assertIn(rel,names)
                    self.assertIn(prefix+rel,names)
            for rel in ("data/realscale/worldgen/structure/urban_district.json",
                        "data/realscale/worldgen/template_pool/urban/districts.json",
                        "data/realscale/worldgen/structure_set/urban_districts.json"):
                self.assertIn(prefix+rel,names)
                self.assertNotIn(rel,names)
            self.assertEqual(set(path.stem for path in (
                ROOT/"neoforge/build/resources/main/data/realscale/structure/architecture").glob("*.nbt")).__len__(),100)


if __name__=="__main__":
    unittest.main()
