#!/usr/bin/env python3
"""Special Chongqing real-DEM source provenance and fail-closed worldgen contract."""
import base64
import gzip
import hashlib
import json
import struct
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORLD=ROOT/"neoforge/src/main/resources/data/realscale"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/assets/realscale/terrain/chongqing_chaotianmen_dem_v1.properties"

def props(path=DATA):
    result={}
    for line in path.read_text().splitlines():
        if not line or line.startswith("#"):continue
        k,sep,value=line.partition("=")
        assert sep, "Malformed source metadata"
        result[k]=value
    return result

class ChongqingCityWorldgen(unittest.TestCase):
    def test_dem_is_real_bundled_and_cryptographically_verified(self):
        p=props()
        self.assertEqual(p["format"],"CQDEM1")
        self.assertEqual((int(p["width"]),int(p["spacing_m"])),(129,64))
        self.assertEqual((int(p["min_x"]),int(p["min_z"])),(-4096,-4096))
        raw=gzip.decompress(base64.b64decode(p["heights_gzip_base64"],validate=True))
        self.assertEqual(len(raw),129*129*2)
        self.assertEqual(hashlib.sha256(raw).hexdigest(),
                         "1db079896cd35c5dd795a1f121ac11d4b7afc5b7f788688f9be2cd7e3baaf6bc")
        elevations=struct.unpack(">16641h",raw)
        self.assertEqual((min(elevations),max(elevations)),(145,664))
        self.assertEqual(p["source_zoom"],"13")
        self.assertEqual(p["source_license"],
                         "AWS_elevation_tiles_Mapzen_source_attribution_required")

    def test_world_preset_is_opt_in_and_has_no_false_subterranean(self):
        preset=json.loads((WORLD/"worldgen/world_preset/special_chongqing_sky_city.json").read_text())
        main=preset["dimensions"]["minecraft:overworld"]
        self.assertEqual(main["type"],"realscale:chongqing_sky_overworld")
        self.assertEqual(main["generator"]["settings"],"realscale:special_chongqing_sky")
        self.assertEqual(main["generator"]["biome_source"],
                         {"type":"minecraft:fixed","biome":"minecraft:the_void"})
        noise=json.loads((WORLD/"worldgen/noise_settings/special_chongqing_sky.json").read_text())
        self.assertEqual(noise["noise"],{
            "min_y":-64,"height":1024,
            "size_horizontal":4,"size_vertical":1})
        self.assertFalse(noise["aquifers_enabled"])
        self.assertFalse(noise["ore_veins_enabled"])
        self.assertEqual(noise["default_fluid"]["Name"],"minecraft:air")
        self.assertEqual(noise["default_block"]["Name"],"minecraft:stone")
        self.assertLessEqual(noise["sea_level"],-63)
        self.assertEqual(noise["noise_router"]["final_density"],
                         {"type":"realscale:chongqing_floating",
                          "shell_blocks":40,"vertical_offset":96})
        for item in ("barrier","fluid_level_floodedness","lava","vein_toggle"):
            self.assertEqual(noise["noise_router"][item],0)
        dimension=json.loads((WORLD/"dimension_type/chongqing_sky_overworld.json").read_text())
        self.assertEqual((dimension["min_y"],dimension["height"],
                          dimension["logical_height"]),(-64,1024,1024))

    def test_old_tva_presets_and_caves_unchanged(self):
        previous=json.loads((WORLD/"worldgen/noise_settings/tva_natural_smoothed.json").read_text())
        self.assertEqual(previous["noise"]["height"],2096)
        self.assertEqual(previous["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        normal=json.loads((ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json").read_text())
        self.assertIn("realscale:special_chongqing_sky_city",normal["values"])
        self.assertIn("realscale:tva_sacred_timeline_three_realms",normal["values"])
        density=(SOURCE/"ChongqingFloatingDensity.java").read_text()
        self.assertIn('if(!Double.isFinite(realMetres))return -8.0;',density)
        self.assertIn('Math.min((surface-y)/8.0,(y-bottom)/8.0)',density)
        self.assertNotIn("GrandOverworldCaverns",density)
        registry=(SOURCE/"RealScaleMod.java").read_text()
        self.assertIn('"chongqing_floating"), ChongqingFloatingDensity.MAP_CODEC',registry)

if __name__=="__main__":
    unittest.main()
