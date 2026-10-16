"""Check sky-island geometry writes remain within the decorating chunk."""
import unittest
import json
from pathlib import Path

JAVA=Path(__file__).resolve().parents[1]/"neoforge/src/main/java/org/realscale/neoforge/SkyIslandFeature.java"

class SkyLocalChunkGeneration(unittest.TestCase):
    def test_neighbor_lattice_and_local_footprint(self):
        source=JAVA.read_text()
        self.assertIn("for (int gx=-1;gx<=1;gx++)",source)
        self.assertIn("for(int gz=-1;gz<=1;gz++)",source)
        self.assertIn("placeIsland(world,island,chunkX,chunkZ)",source)
        self.assertIn("int referenceX=chunkX*16+8;",source)
        self.assertIn("int referenceZ=chunkZ*16+8;",source)
        self.assertNotIn("origin.getX()+gx*SkyIslandLayout.GRID",source)
        self.assertEqual(source.count("Math.floorDiv(site.x()+dx,16)!=chunkX"),2)
        self.assertEqual(source.count("Math.floorDiv(site.z()+dz,16)!=chunkZ"),2)
        self.assertEqual(source.count("for (int dx = -r; dx <= r; dx++)"),2)
        self.assertIn("world.setBlock(new BlockPos(x, y, z), state, 2)",source)
        self.assertIn("Math.floorDiv(beacon.getX(),16)==chunkX",source)
        self.assertIn("Math.floorDiv(beacon.getZ(),16)==chunkZ",source)
    def test_deep_ocean_chunks_can_trigger_sky_feature(self):
        root=Path(__file__).resolve().parents[1]
        path=root/"neoforge/src/main/resources/data/realscale/neoforge/biome_modifier/sky_blossom_island.json"
        modifier=json.loads(path.read_text())
        self.assertEqual(modifier["features"],"realscale:sky_blossom_island")
        self.assertIn("minecraft:deep_ocean",modifier["biomes"])
        self.assertIn("realscale:sky_blossom_isles",modifier["biomes"])
        java=JAVA.read_text()
        self.assertIn("instanceof SkyOceanBiomeSource",java)
        self.assertIn("world.getBiome(new BlockPos(site.x(), site.topY(), site.z())).is(SKY)",java)

if __name__=="__main__":unittest.main()
