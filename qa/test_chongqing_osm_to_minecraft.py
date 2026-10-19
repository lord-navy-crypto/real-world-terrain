#!/usr/bin/env python3
"""Real DEM/OSM coordinate parity + synthetic-vector raster control tests.

TEST INPUT GEOMETRIES BELOW ARE SYNTHETIC, never bundled as city facts.
"""
import importlib.util
import json
import math
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location(
    "chongqing_osm_to_minecraft",ROOT/"tools/chongqing_osm_to_minecraft.py")
gis=importlib.util.module_from_spec(spec)
spec.loader.exec_module(gis)

def ll(dem,x,z):
    return [dem.lon+x/dem.m_lon,dem.lat-z/dem.m_lat]

def feature(fid,tags,kind,coords):
    return {"type":"Feature","id":fid,"properties":tags,
            "geometry":{"type":kind,"coordinates":coords}}

class ChongqingGISTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=gis.Dem(gis.DEFAULT_DEM)

    def test_geodesy_matches_original_chongqing_dem_without_utm_drift(self):
        dem=self.dem
        self.assertEqual(dem.coords(dem.lon,dem.lat),(0,0))
        for x,z in ((0,0),(-500,500),(500,-500),(300,350)):
            a,b=dem.coords(*ll(dem,x,z))
            self.assertAlmostEqual(x,a,places=6)
            self.assertAlmostEqual(z,b,places=6)
            self.assertTrue(math.isfinite(dem.height(x,z)))
        self.assertEqual(dem.n,129)
        self.assertEqual(dem.step,64)
        self.assertEqual(dem.min_x,-4096)
        self.assertEqual(dem.min_z,-4096)
        with self.assertRaisesRegex(ValueError,"coverage"):
            dem.height(5000,0)

    def test_polygon_with_real_geometry_holes_and_exact_no_footprint_guess(self):
        dem=self.dem
        outer=[ll(dem,x,z) for x,z in
               ((0,0),(10,0),(10,10),(0,10),(0,0))]
        inner=[ll(dem,x,z) for x,z in
               ((3,3),(7,3),(7,7),(3,7),(3,3))]
        data={"type":"FeatureCollection","features":[
            feature("synthetic-osm-test-1",{"building":"yes"},
                    "Polygon",[outer,inner])]}
        shapes=gis.osm_features(data)
        pixels,stat=gis.prepare(dem,shapes,100)
        self.assertIn((1,1),pixels)
        self.assertNotIn((5,5),pixels)
        self.assertEqual(pixels[(1,1)][0],"building_footprint")
        self.assertIsNone(pixels[(1,1)][2],
                          "Unknown height MUST NOT become fabricated tower")
        self.assertGreaterEqual(stat["buildings_footprint_only"],1)

    def test_exact_height_tag_and_levels_estimation_are_separate(self):
        dem=self.dem
        def poly(x):
            return [[ll(dem,a,b) for a,b in (
                (x,0),(x+3,0),(x+3,3),(x,3),(x,0))]]
        source={"type":"FeatureCollection","features":[
            feature("osm/1",{"building":"yes","height":"25 m"},
                    "Polygon",poly(0)),
            feature("osm/2",{"building":"yes","building:levels":"4"},
                    "Polygon",poly(10))]}
        cells,stats=gis.prepare(dem,gis.osm_features(source),100)
        self.assertEqual(cells[(1,1)][2],25)
        self.assertEqual(cells[(11,1)][2],12)
        self.assertEqual(stats["buildings_estimated_height_from_levels"],1)

    def test_roads_line_are_centerline_only_when_width_unknown(self):
        dem=self.dem
        line=[ll(dem,-20,0),ll(dem,20,0)]
        source={"type":"FeatureCollection","features":[
            feature("way/road1",{"highway":"primary"},
                    "LineString",line)]}
        cells,stats=gis.prepare(dem,gis.osm_features(source),100)
        self.assertIn((0,0),cells)
        self.assertNotIn((0,5),cells)
        self.assertEqual(stats["roads_missing_or_unsupported_width"],1)
        self.assertTrue(all(item[0]=="road" for item in cells.values()))

    def test_water_without_polygon_cannot_fake_true_bank_geometry(self):
        dem=self.dem
        source={"type":"FeatureCollection","features":[
            feature("osm/waterway",{"waterway":"river"},
                    "LineString",[ll(dem,0,0),ll(dem,20,20)])]}
        self.assertRaisesRegex(ValueError,"No real geometries",
             gis.prepare,dem,gis.osm_features(source),100)
        # A real water POLYGON can make a blue *reference* overlay, but
        # it must not silently pour Minecraft flowing water onto cliffs.
        poly=[[ll(dem,x,z) for x,z in
                ((0,0),(4,0),(4,4),(0,4),(0,0))]]
        water={"type":"FeatureCollection","features":[
            feature("osm/riverbank",{"natural":"water"},"Polygon",poly)]}
        cells,_=gis.prepare(dem,gis.osm_features(water),100)
        self.assertEqual(cells[(1,1)][0],"water_reference")

    def test_actual_overpass_way_geom_format_is_supported(self):
        dem=self.dem
        coords=[ll(dem,x,0) for x in (0,10)]
        data={"elements":[{"type":"way","id":123,
                           "tags":{"highway":"service"},
                           "geometry":[{"lon":lon,"lat":lat}
                                       for lon,lat in coords]}]}
        shapes=gis.osm_features(data)
        self.assertEqual(shapes[0]["id"],"way/123")
        self.assertEqual(shapes[0]["kind"],"LineString")
        cells,_=gis.prepare(dem,shapes,100)
        self.assertIn((5,0),cells)
        self.assertRaisesRegex(ValueError,"no complete",
            gis.osm_features,{"elements":[{"type":"relation","id":1}]})

    def test_real_osm_relation_fragments_and_holes_are_stitched_without_guessing(self):
        dem=self.dem
        def points(*coords):
            return [{"lon":lon,"lat":lat} for lon,lat in
                    [ll(dem,x,z) for x,z in coords]]
        part1=points((0,0),(10,0),(10,10))
        part2=points((10,10),(0,10),(0,0))
        inner=points((3,3),(7,3),(7,7),(3,7),(3,3))
        rel={"type":"relation","id":994,"tags":{
            "type":"multipolygon","natural":"water"},
            "members":[
                {"type":"way","role":"outer","geometry":part1},
                {"type":"way","role":"outer","geometry":part2},
                {"type":"way","role":"inner","geometry":inner}
            ]}
        shapes=gis.osm_features({"elements":[rel]})
        self.assertEqual(shapes[0]["kind"],"MultiPolygon")
        pixels,_=gis.prepare(dem,shapes,100)
        self.assertEqual(pixels[(1,1)][0],"water_reference")
        self.assertNotIn((5,5),pixels)
        # An incomplete riverside polygon must NOT be closed by inventing
        # a shoreline across the Yangtze/Jialing intersection.
        broken=dict(rel,members=[rel["members"][0]])
        with self.assertRaisesRegex(ValueError,"no complete"):
            gis.osm_features({"elements":[broken]})

    def test_3d_chongqing_bridges_tunnels_skipped_without_real_deck_y(self):
        dem=self.dem
        line=[ll(dem,0,0),ll(dem,20,0)]
        raw={"type":"FeatureCollection","features":[
            feature("osm/bridge",{"highway":"primary","bridge":"yes"},
                    "LineString",line),
            feature("osm/ground",{"highway":"path"},"LineString",
                    [ll(dem,0,3),ll(dem,20,3)])]}
        blocks,stat=gis.prepare(dem,gis.osm_features(raw),100)
        self.assertEqual(stat["multilevel_lines_deferred_no_3d_y"],1)
        self.assertIn((10,3),blocks)
        self.assertNotIn((10,0),blocks)

    def test_datapack_staged_bounded_and_has_immutable_source_hash(self):
        dem=self.dem
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            source=root/"source.geojson"
            ring=[[ll(dem,x,z) for x,z in
                   ((-3,-3),(3,-3),(3,3),(-3,3),(-3,-3))]]
            source.write_text(json.dumps({"type":"FeatureCollection",
                "features":[feature("osm/test",{"building":"yes",
                    "height":"18"},"Polygon",ring)]}),encoding="utf-8")
            blocks,stats=gis.prepare(dem,gis.osm_features(
                json.loads(source.read_text())),100)
            out=root/"pack"
            result=gis.export_datapack(blocks,stats,source,dem,
                         out,100,"© OpenStreetMap contributors (ODbL); synthetic test geometry")
            self.assertEqual(result["stages"],1)
            self.assertEqual(result["dem_source_raw_sha256"],dem.sha256)
            self.assertEqual(result["native_ui_plan"],"chongqing-gis-plan.json")
            native=json.loads((out/"chongqing-gis-plan.json").read_text())
            self.assertEqual(native["format"],"REALSCALE_CHAOTIANMEN_PLACEMENT_V1")
            self.assertEqual(native["dem_source_raw_sha256"],dem.sha256)
            self.assertEqual(native["row_count"],len(native["rows"]))
            self.assertEqual(native["planned_block_writes"],sum(
                (r["x1"]-r["x0"]+1)*(r["height"]+1) for r in native["rows"]))
            self.assertTrue(native["source_unverified"])
            self.assertTrue(all(-100<=r["x0"]<=r["x1"]<=100
                                and -100<=r["z"]<=100 for r in native["rows"]))
            commands=(out/"data/realscale_chongqing/function/tile_0000.mcfunction").read_text()
            self.assertIn("minecraft:light_gray_concrete",commands)
            self.assertIn("fill ",commands)
            self.assertNotIn("minecraft:water",commands)
            self.assertTrue((out/"pack.mcmeta").is_file())
            self.assertEqual(json.loads((out/"pack.mcmeta").read_text())["pack"]["pack_format"],48)
            with self.assertRaisesRegex(ValueError,"empty"):
                gis.export_datapack(blocks,stats,source,dem,out,100,"OSM ODbL")

    def test_overpass_turbo_incomplete_geojson_is_not_rasterized(self):
        dem=self.dem
        geometry=[ll(dem,0,0),ll(dem,10,0)]
        source={"type":"FeatureCollection","generator":"overpass-turbo",
                "features":[
                    feature("way/incomplete",
                            {"highway":"primary","@tainted":True},
                            "LineString",geometry),
                    feature("way/complete",{"highway":"path"},
                            "LineString",[ll(dem,0,3),ll(dem,10,3)])]}
        shapes=gis.osm_features(source)
        self.assertEqual([f["id"] for f in shapes],["way/complete"])
        pixels,_=gis.prepare(dem,shapes,100)
        self.assertNotIn((5,0),pixels)
        self.assertIn((5,3),pixels)
        with self.assertRaisesRegex(ValueError,"No supported"):
            gis.osm_features(dict(source,features=source["features"][:1]))

    def test_closed_road_and_rail_loops_remain_centerlines_not_filled_areas(self):
        # A closed OSM highway/railway is a loop, NOT necessarily an area.
        dem = self.dem
        ring = [ll(dem,x,z) for x,z in
                ((0,0),(20,0),(20,20),(0,20),(0,0))]
        src = {"elements":[
            {"type":"way","id":90001,"tags":{"highway":"residential"},
             "geometry":[{"lon":p[0],"lat":p[1]} for p in ring]},
            {"type":"way","id":90002,"tags":{"railway":"rail"},
             "geometry":[{"lon":p[0]+50/dem.m_lon,"lat":p[1]}
                         for p in ring]}]}
        ways = gis.osm_features(src)
        self.assertTrue(all(f["kind"]=="LineString" for f in ways))
        cells,_ = gis.prepare(dem,ways,100)
        self.assertIn((0,10),cells)
        self.assertNotIn((10,10),cells,
                         "Closed highway loop must not fill inner city blocks")
        self.assertNotIn((60,10),cells,
                         "Closed rail loop must not turn into a concrete plaza")

    def test_real_area_yes_road_can_still_fill_polygon(self):
        dem = self.dem
        ring = [ll(dem,x,z) for x,z in
                ((0,0),(10,0),(10,10),(0,10),(0,0))]
        raw = {"elements":[{"type":"way","id":90003,
                "tags":{"highway":"pedestrian","area":"yes"},
                "geometry":[{"lon":p[0],"lat":p[1]} for p in ring]}]}
        cells,_ = gis.prepare(dem,gis.osm_features(raw),100)
        self.assertIn((5,5),cells)

    def test_big_water_rows_are_compressed_with_safe_stage_volume(self):
        # Synthetic geometry: stress only exporter, NOT actual Yangtze data.
        dem = self.dem
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root/"synthetic.json"
            source.write_text('{"synthetic_qa_only":true}',encoding="utf-8")
            # A 1,000-block single row previously needed 1,000 setblocks.
            layers = {(x,0):("water_reference",100,None,"way/QA")
                      for x in range(-500,500)}
            out = root/"datapack"
            m = gis.export_datapack(
                layers,{},source,dem,out,500,
                "Synthetic exporter unit test — NOT geographic source")
            self.assertEqual(m["staged_commands"],1)
            self.assertEqual(m["staged_block_writes"],1000)
            self.assertEqual(m["stage_block_writes"],[1000])
            commands = (out/"data/realscale_chongqing/function/tile_0000.mcfunction").read_text()
            self.assertIn("fill -500 100 0 499 100 0 minecraft:blue_concrete",commands)
            self.assertTrue(all(v<=20000 for v in m["stage_block_writes"]))

    def test_tall_building_run_splits_vertical_fill_volume_safely(self):
        dem = self.dem
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source=root/"test.geojson"
            source.write_text("{}",encoding="utf-8")
            layers={(x,0):("building_footprint",300,300,"way/height-QA")
                    for x in range(20)}
            m=gis.export_datapack(
                layers,{},source,dem,root/"datapack",100,
                "Synthetic height QA, not geographic data")
            self.assertEqual(m["block_counts"]["building_mass_columns"],20)
            self.assertEqual(m["staged_block_writes"],20*301)
            self.assertTrue(all(v<=gis.MAX_BLOCKS_PER_FUNCTION
                                for v in m["stage_block_writes"]))
            for i in range(m["stages"]):
                path=root/"datapack/data/realscale_chongqing/function"/f"tile_{i:04d}.mcfunction"
                for command in path.read_text().splitlines():
                    if command.startswith("fill "):
                        parts=command.split()
                        x0,y0,z0=map(int,parts[1:4])
                        x1,y1,z1=map(int,parts[4:7])
                        volume=(abs(x1-x0)+1)*(abs(y1-y0)+1)*(abs(z1-z0)+1)
                        self.assertLessEqual(volume,gis.MAX_BLOCKS_PER_COMMAND)

if __name__=="__main__":
    unittest.main()
