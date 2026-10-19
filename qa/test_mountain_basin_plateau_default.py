#!/usr/bin/env python3
"""Assert world presets actually use highland basin/plateau relief + basin climate."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main"
DATA=ROOT/"resources/data/realscale/worldgen"
JAVA=ROOT/"java/org/realscale/neoforge"

PRESETS={
    "tva_natural_smoothed":("realism_max","realism_max/continental_field"),
    "tva_alpine_smoothed":("himalayan","himalayan/continents"),
    "tva_canyon_smoothed":("grand_canyon","grand_canyon/continents"),
}
def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


class MountainBasinPlateauTest(unittest.TestCase):
    def test_public_terrain_graphs_keep_original_height_and_add_relief(self):
        for preset,(family,continental) in PRESETS.items():
            with self.subTest(preset=preset):
                root=DATA/"density_function"/preset
                original=read(root/"original_height_signal.json")
                self.assertEqual(original,{
                    "type":"minecraft:interpolated",
                    "argument":f"realscale:{family}/height_signal"})
                shape=read(root/"height_signal.json")
                if preset=="tva_natural_smoothed":
                    # Native 2D cache is only an outer wrapper; every basin
                    # and plateau parameter remains exactly as before.
                    self.assertEqual(shape["type"],"minecraft:cache_2d")
                    shape=shape["argument"]
                self.assertEqual(shape["type"],"realscale:mountain_relief")
                self.assertEqual(shape["base"],f"realscale:{preset}/original_height_signal")
                self.assertEqual(shape["continents"],f"realscale:{continental}")
                self.assertEqual(shape["selector"],{
                    "type":"minecraft:noise",
                    "noise":"minecraft:erosion",
                    "xz_scale":0.006,"y_scale":0
                })
                self.assertGreater(shape["basin_depth"],0)
                self.assertGreater(shape["plateau_rise"],0)
                terrain=read(root/"terrain_density.json")
                self.assertEqual(terrain["argument1"],f"realscale:{preset}/height_signal")
                noise=read(DATA/"noise_settings"/(preset+".json"))
                self.assertEqual(noise["noise_router"]["depth"],
                                 f"realscale:{preset}/terrain_density")
                self.assertEqual(noise["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")
                self.assertEqual(noise["noise_router"]["final_density"]["base"],
                                 f"realscale:{preset}/terrain_density")
                self.assertEqual(noise["sea_level"],63)

    def test_basins_reuse_same_seeded_layout_as_relief_and_climate(self):
        for preset in PRESETS:
            noise=read(DATA/"noise_settings"/(preset+".json"))
            temperature=noise["noise_router"]["temperature"]
            self.assertEqual(temperature["type"],"realscale:climate_budget")
            adjusted=temperature["adjusted"]
            self.assertEqual(adjusted["type"],"realscale:mountain_basin_temperature")
            self.assertEqual(adjusted["height_signal"],
                             f"realscale:{preset}/original_height_signal")
            base=adjusted["base"]
            self.assertEqual(base["type"],"realscale:radial_latitude_temperature")
            self.assertTrue(base["area_balanced"])
            self.assertTrue(base["balanced"])
            self.assertGreater(base["altitude_cooling"],0)
            landform=read(DATA/"density_function"/preset/"height_signal.json")
            if preset=="tva_natural_smoothed":
                self.assertEqual(landform["type"],"minecraft:cache_2d")
                landform=landform["argument"]
            self.assertEqual(adjusted["selector"],landform["selector"])
            self.assertEqual(adjusted["continents"],landform["continents"])
            self.assertGreater(adjusted["warmth"],0)
            self.assertLessEqual(adjusted["warmth"],0.25)

    def test_measured_usgs_preserved_and_old_worlds_unchanged(self):
        usgs=read(DATA/"density_function/usgs_big_sur_v2/terrain_density.json")
        self.assertEqual(usgs["type"],"realscale:dem_density_seamless")
        self.assertEqual(usgs["blend_meters"],2048)
        usgs_noise=read(DATA/"noise_settings/usgs_big_sur_v2.json")
        self.assertEqual(usgs_noise["noise_router"]["temperature"]["adjusted"]["type"],
                         "realscale:radial_latitude_temperature")
        self.assertEqual(usgs_noise["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        for old in ("realism_max","himalayan","grand_canyon"):
            self.assertNotEqual(
                read(DATA/"density_function"/old/"height_signal.json").get("type"),
                "realscale:mountain_relief")

    def test_mod_codec_and_bounded_site_cache(self):
        main=(JAVA/"RealScaleMod.java").read_text()
        terrain=(JAVA/"MountainReliefDensity.java").read_text()
        site_cache=(JAVA/"MountainReliefSiteCache.java").read_text()
        temp=(JAVA/"MountainBasinTemperatureDensity.java").read_text()
        pure=(JAVA/"MountainReliefMath.java").read_text()
        self.assertIn('"mountain_relief"',main)
        self.assertIn('"mountain_basin_temperature"',main)
        self.assertIn("ThreadLocal.withInitial",terrain)
        self.assertIn("ThreadLocal.withInitial",temp)
        self.assertIn("Math.floorDiv",terrain)
        # The cache is keyed by horizontal region only; a Y-dependent anchor
        # would make site selection depend on whichever vertical sample runs first.
        self.assertIn("new DensityFunction.SinglePointContext(",terrain)
        self.assertIn("Math.toIntExact((long)site.centerX),0,",terrain)
        self.assertIn("Math.toIntExact((long)site.centerZ)",terrain)
        self.assertIn("double regionalSignal=selector.compute(anchor);",terrain)
        self.assertNotIn("new DensityFunction.SinglePointContext(centerX, ctx.blockY(), centerZ)",terrain)
        self.assertIn("Math.floorDiv",temp)
        # Terrain and basin-climate MUST classify the same horizontal site.
        self.assertIn("Math.toIntExact((long)site.x),0,Math.toIntExact((long)site.z)",temp)
        self.assertIn("site.kind=MountainReliefMath.kind(site.hash,region);",temp)
        self.assertNotIn("Math.toIntExact((long)s.x),ctx.blockY(),Math.toIntExact((long)s.z)",temp)
        self.assertIn("int kind(long h, double seedResolvedSignal)",pure)
        self.assertIn("inlandUplandGate",pure)
        self.assertIn("return original;",terrain)
        # Expensive continental-noise evaluation must occur after every
        # mathematically safe exclusion in the 2096-block structure height path.
        self.assertIn("if (original <= 0.65) return original;",terrain)
        self.assertLess(terrain.index("if (original <= 0.65) return original;"),
                        terrain.index("MountainReliefSiteCache.Site s = site(ctx);"))
        self.assertLess(terrain.index("if (s.definitelyOutside(dx,dz))"),
                        terrain.index("double continent = continents.compute(ctx);"))
        self.assertIn("double conservative=radius*1.100000001;",site_cache)
        self.assertIn("return dx*dx+dz*dz>=envelopeSquared;",site_cache)
        self.assertIn("new Site(gx,gz);",site_cache)
        self.assertIn("private final Site[] sites=new Site[SLOTS];",site_cache)
        self.assertLess(terrain.index("if (s.type == MountainReliefMath.NONE) return original;"),
                        terrain.index("double continent = continents.compute(ctx);"))
        # The seeded selector must be evaluated only INSIDE the hash-defined
        # influence radius, and only once per worldgen worker/site.
        self.assertIn("boolean classified;",site_cache)
        self.assertIn("private final ThreadLocal<MountainReliefSiteCache> current",terrain)
        self.assertIn("ThreadLocal.withInitial(MountainReliefSiteCache::new)",terrain)
        self.assertIn("site.classified=true;",terrain)
        self.assertIn("private void classify(MountainReliefSiteCache.Site site)",terrain)
        self.assertIn("site.type=MountainReliefMath.kind(site.hash,regionalSignal);",terrain)
        self.assertIn("return current.get().get(gx,gz);",terrain)

        climate=temp  # Source already loaded from the canonical JAVA path above.
        self.assertIn("private void classify(Site site)",climate)
        self.assertIn("boolean classified;",climate)
        self.assertIn("site.classified=true;",climate)
        self.assertLess(climate.index("if(exposedLand<=0.0)return original;"),
                        climate.index("double continent=continents.compute(ctx);"))
        self.assertLess(climate.index("if(dx*dx+dz*dz>=limit*limit)return original;"),
                        climate.index("classify(s);"))
        # The basin site footprint must precede BOTH expensive noise reads.
        self.assertLess(climate.index("if(dx*dx+dz*dz>=limit*limit)return original;"),
                        climate.index("double continent=continents.compute(ctx);"))
        self.assertLess(climate.index("if(dx*dx+dz*dz>=limit*limit)return original;"),
                        climate.index("heightSignal.compute(ctx)"))
        self.assertLess(climate.index("classify(s);"),
                        climate.index("if(s.kind!=MountainReliefMath.BASIN)return original;"))

        self.assertLess(terrain.index("if (s.definitelyOutside(dx,dz))"),
                        terrain.index("classify(s);"))
        self.assertLess(terrain.index("classify(s);"),
                        terrain.index("double continent = continents.compute(ctx);"))

        self.assertIn("return original;",temp)
        self.assertIn("if(exposedLand<=0.0)return original;",temp)
        self.assertIn("if(highlandGate<=0.0)return original;",temp)
        # Basin-core geometry must now reject cheap non-candidates BEFORE
        # costly highland height and continentalness sampling.
        self.assertLess(temp.index("Site s=site(ctx);"),
                        temp.index("if(highlandGate<=0.0)return original;"))


if __name__=="__main__":
    unittest.main()
