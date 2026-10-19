#!/usr/bin/env python3
"""Unit tests and on-disk QA of independent vanilla 1.21.1 real GIS map."""
import io
import json
import math
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_export_standalone_world as exporter


def get_chunk(world,cx,cz):
    path=world/"region"/f"r.{cx//32}.{cz//32}.mca"
    with path.open("rb") as stream:
        index=(cz%32)*32+(cx%32)
        stream.seek(index*4)
        entry=struct.unpack(">I",stream.read(4))[0]
        assert entry,"Missing real Anvil chunk at "+str((cx,cz))
        offset=entry>>8
        stream.seek(offset*4096)
        length=struct.unpack(">I",stream.read(4))[0]
        assert stream.read(1)==b"\x02","Expected vanilla zlib region chunk"
        raw=zlib.decompress(stream.read(length-1))
    import nbtlib
    return nbtlib.File.parse(io.BytesIO(raw))


def get_block(world,x,y,z):
    chunk=get_chunk(world,x//16,z//16)
    section=next((s for s in chunk["sections"]
                  if int(s["Y"])==y//16),None)
    if section is None:return "minecraft:air"
    states=section["block_states"]
    palette=[str(p["Name"]) for p in states["palette"]]
    if len(palette)==1:return palette[0]
    bits=max(4,(len(palette)-1).bit_length())
    index=((y&15)<<8)|((z&15)<<4)|(x&15)
    entries=64//bits
    offset=index//entries;shift=(index%entries)*bits
    value=int(states["data"][offset]) & ((1<<64)-1)
    local=(value>>shift)&((1<<bits)-1)
    return palette[local]


class StandaloneVanillaWorldTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.grid,cls.plan=exporter.read_inputs()

    def test_is_standalone_compatible_with_mods_absent(self):
        self.assertEqual(exporter.WORLD,"Chaotianmen_500m_Standalone")
        self.assertEqual(exporter.DATA_VERSION,3955)
        self.assertEqual(exporter.HEIGHT,1024)
        self.assertEqual(exporter.MIN_Y,-64)
        self.assertEqual(exporter.PLAN_HASH,
            "04a380b144d3f002c0157d841edefb68d5a5bfacf6ca07149c43e8f03a858980")
        self.assertEqual(self.plan["planned_block_writes"],494988)

    def test_true_first_air_y_and_terrain_shell_bounds(self):
        for x,z in ((0,0),(17,19),(-500,-500),(500,500)):
            surface=exporter.elevation(self.grid,x,z)
            ground=math.ceil(surface)-1
            bottom=math.floor(surface-exporter.SHELL)+1
            self.assertTrue(bottom<=ground)
            self.assertTrue(exporter.MIN_Y<=bottom<ground<960)

    def test_chunk_palettes_use_valid_vanilla_blocks_not_mods(self):
        vox=bytearray(4096)
        vox[0]=1;vox[1]=3;vox[2]=5;vox[4095]=7
        out=exporter.chunk_nbt(0,0,{5:vox},[100]*256)
        self.assertGreater(len(out),100)
        self.assertIn(b"minecraft:stone",out)
        self.assertIn(b"minecraft:blue_concrete",out)
        self.assertIn(b"minecraft:plains",out)
        self.assertNotIn(b"realscale:",out)

    def test_negative_chunk_and_gis_operation_accounting(self):
        chunk_idx=exporter.overlay_index(self.plan)
        rows=0;counts=0
        for (cx,cz),segments in chunk_idx.items():
            for row,a,b in segments:
                self.assertEqual(cx,a//16)
                self.assertEqual(cx,b//16)
                self.assertEqual(cz,row["z"]//16)
                rows+=b-a+1
                counts+=(b-a+1)*(1+(
                    row["height"] if row["shell"]=="facade"
                    else 1 if row["shell"]=="roof" else 0))
        self.assertGreater(rows,50000)
        self.assertEqual(counts,494988)


def verify_world(world,surface_mask=None,satellite_mask=None,roads_mask=None):
    import nbtlib
    if not (world/"level.dat").is_file():
        raise AssertionError("Missing independent vanilla level.dat")
    level=nbtlib.load(world/"level.dat")
    data=level["Data"]
    assert int(data["GameType"])==1
    assert int(data["SpawnX"])==0 and int(data["SpawnZ"])==0
    assert int(data["SpawnY"])>120
    d=world/"datapacks"/"realscale_standalone_height_1024"/"data"/"minecraft"/"dimension_type"/"overworld.json"
    dimension=json.loads(d.read_text())
    assert dimension["min_y"]==-64 and dimension["height"]==1024
    assert dimension["logical_height"]==1024
    report=json.loads((world/"realscale-standalone-export-metrics.json").read_text())
    assert report["actual_overlay_writes"]==494988
    assert report["chunks"]>=3900
    assert report["regions"]==4  # 64×64 chunks over [-500,500] = 2×2 Anvil regions
    assert report["dem_terrain_columns"]==1001*1001

    # Inspect actual NBT saved chunks, not a manifest or PNG stand-in.
    grid,plan=exporter.read_inputs()
    ground_surface={}
    satellite_surface={}
    if satellite_mask is not None:
        satellite_surface,satellite_meta=exporter.read_copernicus_mask(
            Path(satellite_mask),plan["dem_source_raw_sha256"])
        assert report["sentinel_source_stac_item"]==satellite_meta["source_stac_item"]
        assert report["sentinel_classified_columns"]==len(satellite_surface)
        assert report["sentinel_resolution_m"]==10
        assert report["satellite_evidence_used_after_exact_osm_precedence"]>1000
    if surface_mask is not None:
        ground_surface,surface_meta=exporter.read_surface_mask(
            Path(surface_mask),plan["dem_source_raw_sha256"])
        assert report["source_classified_ground_columns"]==len(ground_surface)
        assert report["surface_source_sha256"]==surface_meta["raw_osm_api_sha256"]
        assert report["source_qualified_non_grass_ground_columns"]>0
    protected={(x,r["z"]) for r in plan["rows"]
               if r["kind"]!="road"
               for x in range(r["x0"],r["x1"]+1)}
    verified_roads=0
    if roads_mask is not None:
        corridors,roads_meta=exporter.read_road_corridors(
            Path(roads_mask),plan["dem_source_raw_sha256"])
        assert report["road_corridor_cells_source"]==len(corridors)
        assert report["osm_road_corridor_sha256"]==roads_meta["raw_osm_api_sha256"]
        allowed={pos:block for pos,block in corridors.items() if pos not in protected}
        assert report["road_corridor_new_surface_writes"]==len(allowed)
        assert len(allowed)>100
        # Saved Anvil palette must contain actual buffered *road widths*,
        # not only the original one-block centerlines.
        points=sorted(allowed.items())
        for (x,z),material in points[::max(1,len(points)//32)]:
            y=math.ceil(exporter.elevation(grid,x,z))
            expected=exporter.NAME[material]
            original=next((r for r in plan["rows"] if r["z"]==z
                           and r["x0"]<=x<=r["x1"]),None)
            if original is not None:
                expected=exporter.NAME[exporter.MATERIAL[original["kind"]]]
            assert get_block(world,x,y,z)==expected,(
                    "actual OSM road corridor block mismatch",x,y,z,expected)
            verified_roads+=1
        assert verified_roads>=20
    footprint={(x,z) for r in plan["rows"] for x in range(r["x0"],r["x1"]+1)
               for z in (r["z"],)}
    verified_ground=0
    for x,z in ((0,0),(64,64),(-64,64),(192,-128),(-500,-500),(500,500),
                (401,401),(-401,401),(401,-401),(-401,-401)):
        y=math.ceil(exporter.elevation(grid,x,z))-1
        if (x,z) not in footprint:
            expected=exporter.NAME[ground_surface.get(
                (x,z),satellite_surface.get((x,z),3))]
            assert get_block(world,x,y,z)==expected,(x,y,z,expected)
            verified_ground+=1
        assert get_block(world,x,y-4,z) in ("minecraft:stone","minecraft:dirt")
    assert verified_ground>=4

    crystal_verified=0
    if report.get("crystal_3d_evidence"):
        from chongqing_crystal_3d import build_crystal
        full_3d,provenance=build_crystal()
        assert report["crystal_3d_evidence"]==provenance
        assert report["crystal_schematic_block_writes"]==len(full_3d)
        assert provenance["source_min_height_m"]==250
        assert provenance["source_vertical_span_m"]==26
        for x,y,z,material in full_3d[::max(1,len(full_3d)//30)]:
            assert get_block(world,x,y,z)==exporter.NAME[material],(
                "REAL pinned OSM Crystal structure not saved at correct 3D Y",
                x,y,z,material)
            crystal_verified+=1
        assert crystal_verified>=20
    geometry_rows=plan["rows"]
    tested=0
    for row in geometry_rows[::max(1,len(geometry_rows)//20)]:
        x=row["x0"];y=row["y"];z=row["z"]
        expected=exporter.NAME[exporter.MATERIAL[row["kind"]]]
        assert get_block(world,x,y,z)==expected,(row,"surface block mismatch")
        if row["height"]:
            assert get_block(world,x,y+row["height"],z)=="minecraft:gray_concrete"
            if row["shell"]=="roof" and row["height"]>3:
                assert get_block(world,x,y+2,z)=="minecraft:air"
        tested+=1
    print("INDEPENDENT_VANILLA_ANVIL_WORLD_VERIFY_PASS",
          json.dumps({"chunks":report["chunks"],"regions":report["regions"],
              "1m_dem_terrain_columns":report["dem_terrain_columns"],
              "genuine_osm_block_writes":report["actual_overlay_writes"],
              "actual_NBT_blocks_checked":tested+12,
              "classified_surface_columns":len(ground_surface),
              "satellite_ground_evidence_columns":len(satellite_surface),
              "road_corridor_actual_blocks_checked":verified_roads,
              "crystal_3d_real_anvil_samples":crystal_verified,
              "spawn_y":int(data["SpawnY"])}))


if __name__=="__main__":
    if len(sys.argv) in (2,3,4,5):
        verify_world(Path(sys.argv[1]).resolve(),
                     Path(sys.argv[2]).resolve() if len(sys.argv)>=3 else None,
                     Path(sys.argv[3]).resolve() if len(sys.argv)>=4 else None,
                     Path(sys.argv[4]).resolve() if len(sys.argv)>=5 else None)
    else:
        unittest.main()
