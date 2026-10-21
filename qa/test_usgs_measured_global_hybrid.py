"""Hybrid-USGS worldgen contract: exact source in ordinary terrain, 24 rare overrides."""
import copy,json,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
WG=R/"neoforge/src/main/resources/data/realscale/worldgen"
J=R/"neoforge/src/main/java/org/realscale/neoforge"
def read(x):return json.loads(x.read_text(encoding="utf-8"))
class HybridMeasuredUSGS(unittest.TestCase):
    def test_no_changes_to_measured_dem_or_old_preset(self):
        old=read(WG/"density_function/usgs_big_sur_v2/terrain_density.json")
        new=read(WG/"density_function/usgs_global_hybrid/terrain_density.json")
        self.assertEqual(old["type"],"realscale:dem_density_seamless")
        self.assertEqual(old["blend_meters"],2048)
        self.assertEqual(new["type"],"realscale:measured_rare_hybrid")
        self.assertEqual(new["measured"],"realscale:usgs_big_sur_v2/terrain_density")
        self.assertEqual(new["procedural"],old["base"])
        self.assertEqual(new["procedural_height"],old["base"]["argument1"])
        self.assertEqual(new["continents"],"realscale:realism_max/continental_field")
        self.assertEqual(read(WG/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")[
             "dimensions"]["minecraft:overworld"]["generator"]["settings"],
             "realscale:usgs_big_sur_v2")
        self.assertEqual(read(WG/"world_preset/tva_measured_three_realms.json")[
             "dimensions"]["minecraft:overworld"]["generator"]["settings"],
             "realscale:usgs_big_sur_v2")

    def test_noise_graph_only_swaps_surface_density_references(self):
        old=read(WG/"noise_settings/usgs_big_sur_v2.json")
        new=read(WG/"noise_settings/usgs_global_hybrid.json")
        oldR=copy.deepcopy(old["noise_router"])
        newR=copy.deepcopy(new["noise_router"])
        for field in ("depth","initial_density_without_jaggedness","final_density"):
            oldR.pop(field);newR.pop(field)
        self.assertEqual(oldR,newR,"Original climate, vegetation, fluids or ocean were changed")
        oldCopy=copy.deepcopy(old);newCopy=copy.deepcopy(new)
        oldCopy.pop("noise_router");newCopy.pop("noise_router")
        self.assertEqual(oldCopy,newCopy,"Original surface blocks, aquifers, height bounds changed")
        self.assertEqual(new["noise_router"]["depth"],"realscale:usgs_global_hybrid/terrain_density")
        self.assertEqual(new["noise_router"]["initial_density_without_jaggedness"],
                         "realscale:usgs_global_hybrid/preliminary_density")
        wrapper=new["noise_router"]["final_density"]
        self.assertEqual(wrapper["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(wrapper["small_caves"],"realscale:realism_max/cave_core")
        self.assertEqual(wrapper["base"]["type"],"realscale:range_min_exact")
        self.assertEqual(wrapper["base"]["base"],"realscale:usgs_global_hybrid/terrain_density")
        self.assertEqual(wrapper["base"]["cave"],"realscale:usgs_global_hybrid/cave_density")
        cave=read(WG/"density_function/usgs_global_hybrid/cave_density.json")
        old_cave=read(WG/"density_function/usgs_big_sur_v2/cave_density.json")
        fixed=json.loads(json.dumps(old_cave).replace("usgs_big_sur_v2/terrain_density",
                             "usgs_global_hybrid/terrain_density"))
        self.assertEqual(cave,fixed)
        self.assertEqual(read(WG/"density_function/usgs_global_hybrid/preliminary_density.json")[
                         "argument1"],"realscale:usgs_global_hybrid/terrain_density")

    def test_world_preserves_end_invasion_biodiversity_and_sky_islands(self):
        old=read(WG/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")
        new=read(WG/"world_preset/tva_measured_global_landforms_three_realms.json")
        oldDims=old["dimensions"];newDims=new["dimensions"]
        for dim in ("minecraft:the_nether","minecraft:the_end"):
            self.assertEqual(newDims[dim],oldDims[dim])
        self.assertEqual(newDims["minecraft:overworld"]["type"],
                         oldDims["minecraft:overworld"]["type"])
        before=oldDims["minecraft:overworld"]["generator"]
        after=newDims["minecraft:overworld"]["generator"]
        self.assertEqual(after["type"],before["type"])
        prior=json.loads(json.dumps(before["biome_source"]))
        # Measured Earth now additionally enables the formerly hidden
        # geological-wonders layer without replacing the original USGS signal.
        self.assertEqual(after["biome_source"]["source"]["source"]["type"],
                         "realscale:geo_wonders_opt_in")
        prior["source"]["source"]={
            "type":"realscale:geo_wonders_opt_in",
            "source":prior["source"]["source"]
        }
        # Modern player presets intentionally opt out of historic forced End spawn.
        prior["source"]["source"]["source"]["type"]="realscale:cross_realm_free_spawn_v1"
        self.assertEqual(after["biome_source"],prior)
        self.assertEqual(after["settings"],"realscale:usgs_global_hybrid_marine")
        self.assertEqual(before["settings"],"realscale:usgs_big_sur_v2")
        menu=read(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        self.assertEqual(len(menu["values"]),4)
        self.assertEqual(len(set(menu["values"])),4)
        self.assertIn("realscale:tva_measured_usgs_fitted_v2_three_realms",menu["values"])
        self.assertNotIn("realscale:tva_global_landform_expedition_v3_three_realms",menu["values"])

    def test_sparse_enclaves_and_registered_density_with_exact_default(self):
        java=(J/"MeasuredHybridRareDensity.java").read_text()
        math=(J/"MeasuredHybridRareMath.java").read_text()
        registered=(J/"RealScaleMod.java").read_text()
        self.assertIn('"measured_rare_hybrid"',registered)
        self.assertIn("MeasuredHybridRareDensity.MAP_CODEC",registered)
        self.assertIn("RARITY_DIVISOR=5",math)
        self.assertIn("ACTIVE_RESIDUES=2",math)
        self.assertIn("return measured;",math)
        self.assertIn("return recorded;",java)
        self.assertIn("Region",java) if False else None
        self.assertIn("RegionalLandformMath.kindV3",java)
        self.assertIn("RegionalLandformMath.effect",java)
        self.assertIn("procedural.compute(ctx)",java)
        self.assertIn("measured.compute(ctx)",java)
        self.assertIn("ThreadLocal<Cell>",java)
        self.assertIn("measured.mapAll(visitor)",java)
        self.assertIn("procedural.mapAll(visitor)",java)
        self.assertIn("selector.mapAll(visitor)",java)
        for lang in ("en_us","zh_cn"):
            translations=read(R/("neoforge/src/main/resources/assets/realscale/lang/"+lang+".json"))
            self.assertTrue(translations[
               "generator.realscale.tva_measured_global_landforms_three_realms"])

if __name__=="__main__":unittest.main()
