#!/usr/bin/env python3
"""Actual cross-branch regression: former YACL Lab exports must inherit TVA's
four player choices while retaining archival presets, and three enlarged dimensions without old-ID changes.
"""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT/"neoforge/src/main/resources"
spec=importlib.util.spec_from_file_location("terrain_lab",ROOT/"scripts/terrain_lab.py")
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
TVA={
 "landscape":"tva_sacred_timeline_three_realms",
 "alpine":"tva_alpine_three_realms",
 "canyon":"tva_canyon_three_realms",
}
PLAYER_MENU=(
 "realscale:tva_natural_hypsometry_v1_three_realms",
 "realscale:tva_measured_usgs_fitted_v2_three_realms",
 "realscale:tva_alpine_three_realms",
 "realscale:tva_canyon_three_realms",
)

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

class TVARecoveredTerrainLab(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        for folder in ("data/realscale/worldgen",
                       "data/minecraft/tags/worldgen/world_preset",
                       "assets/minecraft/lang"):
            shutil.copytree(SOURCES/folder,self.root/folder)

    def test_four_original_menu_items_and_additive_opt_in(self):
        for key in ("normal","extended"):
            j=read(self.root/"data/minecraft/tags/worldgen/world_preset"/(key+".json"))
            self.assertEqual(tuple(j["values"]),PLAYER_MENU)

    def test_three_family_exports_keep_512_grand_dimensions_and_biome_sources(self):
        canonical={k:read(self.root/"data/realscale/worldgen/world_preset"/(v+".json"))
                   for k,v in TVA.items()}
        for family in TVA:
            with self.subTest(family=family):
                name,_,paths=lab.apply(self.root,family,1.0,0,50,6)
                path=self.root/"data/realscale/worldgen/world_preset"/(name+".json")
                new=read(path)
                original=canonical[family]
                self.assertEqual(new["dimensions"]["minecraft:the_nether"],
                                 original["dimensions"]["minecraft:the_nether"])
                self.assertEqual(new["dimensions"]["minecraft:the_end"],
                                 original["dimensions"]["minecraft:the_end"])
                self.assertEqual(new["dimensions"]["minecraft:overworld"]["generator"]["biome_source"],
                                 original["dimensions"]["minecraft:overworld"]["generator"]["biome_source"])
                self.assertEqual(new["dimensions"]["minecraft:the_nether"]["generator"]["settings"],
                                 "realscale:grand_nether")
                self.assertEqual(new["dimensions"]["minecraft:the_end"]["generator"]["settings"],
                                 "realscale:grand_end")
                self.assertTrue(paths)

    def test_legacy_fantasy_switch_cannot_downgrade_512_to_256(self):
        name,_,_=lab.apply(self.root,"landscape",1.0,0,50,6,
                           fantasy_dimensions=True)
        j=read(self.root/"data/realscale/worldgen/world_preset"/(name+".json"))
        self.assertEqual(j["dimensions"]["minecraft:the_nether"]["generator"]["settings"],
                         "realscale:grand_nether")
        self.assertEqual(j["dimensions"]["minecraft:the_end"]["generator"]["settings"],
                         "realscale:grand_end")
        self.assertEqual(tuple(read(SOURCES/"data/minecraft/tags/worldgen/world_preset/normal.json")["values"]),PLAYER_MENU)

if __name__=="__main__":
    unittest.main()
