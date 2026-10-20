"""Restore PR25 historical climate as separately selectable worlds, never overwrite sacred."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
FAMILIES=[
 ("tva_natural_smoothed","tva_sacred_timeline_three_realms","tva_pr25_natural"),
 ("tva_alpine_smoothed","tva_alpine_three_realms","tva_pr25_alpine"),
 ("tva_canyon_smoothed","tva_canyon_three_realms","tva_pr25_canyon"),
 ("usgs_big_sur_v2","tva_measured_three_realms","tva_pr25_measured")
]
def read(path):return json.loads(path.read_text())
class PR25CompleteClimateRecovery(unittest.TestCase):
    def test_four_pr25_routers_are_complete_and_opt_in(self):
        for original,preset,prefix in FAMILIES:
            with self.subTest(prefix=prefix):
                key=prefix+"_climate"
                temp=read(DATA/"density_function"/key/"biome_temperature.json")
                noise=read(DATA/"noise_settings"/(key+".json"))
                alternative=read(DATA/"world_preset"/(prefix+"_three_realms.json"))
                canon_noise=read(DATA/"noise_settings"/(original+".json"))
                canon_world=read(DATA/"world_preset"/(preset+".json"))
                self.assertEqual(noise["noise_router"]["temperature"],
                                 "realscale:"+key+"/biome_temperature")
                self.assertEqual(temp["type"],"realscale:climate_budget")
                basin=temp["adjusted"]
                self.assertEqual(basin["type"],"realscale:basin_temperature")
                self.assertEqual(basin["base"]["type"],"realscale:valley_temperature")
                self.assertEqual(basin["base"]["base"]["type"],
                                 "realscale:radial_latitude_temperature")
                self.assertTrue(basin["base"]["base"]["area_balanced"])
                self.assertEqual(alternative["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                                 "realscale:"+key)
                # Historical PR25 variants keep their old other-realm sources;
                # Alpine/Canyon player worlds now additionally restore realm
                # invasion and End accents without changing their terrain.
                for realm in ("minecraft:the_nether","minecraft:the_end"):
                    prior=alternative["dimensions"][realm]
                    current=canon_world["dimensions"][realm]
                    self.assertEqual(prior["type"],current["type"])
                    self.assertEqual(prior["generator"]["settings"],
                                     current["generator"]["settings"])
                    actual=current["generator"]["biome_source"]
                    original=prior["generator"]["biome_source"]
                    if realm=="minecraft:the_end" and actual["type"]=="realscale:end_accents":
                        actual=actual["source"]
                    if actual["type"]=="realscale:cross_realm_invasion":
                        actual=actual["source"]
                    self.assertEqual(actual,original)
                for field in ("final_density","depth","continents","erosion"):
                    self.assertEqual(noise["noise_router"][field],
                                     canon_noise["noise_router"][field])
                self.assertEqual(noise["sea_level"],canon_noise["sea_level"])
                # Sacred/default retains newer mountain basin climate and full caves.
                self.assertEqual(canon_noise["noise_router"]["temperature"]["type"],
                                 "realscale:climate_budget")
                self.assertEqual(canon_noise["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")

if __name__=="__main__":unittest.main()
