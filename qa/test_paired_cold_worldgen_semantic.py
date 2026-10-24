"""Cold-worldgen A/B must compare actual decoded voxels, not palette order."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from paired_cold_worldgen_ab import (
    _digest_expanded_palette, block_payload_digest, saved_world_differences,
)

def packed(indices,bits):
    per_long=64//bits
    data=[0]*((len(indices)+per_long-1)//per_long)
    for i,index in enumerate(indices):
        data[i//per_long]|=index<<((i%per_long)*bits)
    # read_nbt() decodes signed Java longs; prove that remains transparent.
    return [x if x<(1<<63) else x-(1<<64) for x in data]


def saved_chunk(reverse_blocks=False,reverse_biomes=False,
                change_block=False,change_biome=False,
                change_height=False):
    block_palette=[{"Name":"minecraft:stone"},
                   {"Name":"minecraft:air"}]
    block_ids=[(i//17)&1 for i in range(4096)]
    biome_palette=["minecraft:plains","minecraft:forest"]
    biome_ids=[i&1 for i in range(64)]
    if change_block:
        block_ids[205]^=1
    if change_biome:
        biome_ids[37]^=1
    if reverse_blocks:
        block_palette.reverse()
        block_ids=[1-i for i in block_ids]
    if reverse_biomes:
        biome_palette.reverse()
        biome_ids=[1-i for i in biome_ids]
    heightmap=[0]*52
    if change_height:
        heightmap[4]=12
    return {
        "sections":[{
            "Y":0,
            "block_states":{"palette":block_palette,
                            "data":packed(block_ids,4)},
            "biomes":{"palette":biome_palette,
                      "data":packed(biome_ids,1)},
        }],
        "Heightmaps":{"WORLD_SURFACE":heightmap},
    }


def packed_compact(indices,bits):
    data=[0]*((len(indices)*bits+63)//64)
    for i,index in enumerate(indices):
        offset=i*bits
        word=offset//64
        shift=offset%64
        data[word]|=(index<<shift)&((1<<64)-1)
        if shift+bits>64:
            data[word+1]|=index>>(64-shift)
    return [x if x<(1<<63) else x-(1<<64) for x in data]


class TestDecodedAnvilSemanticParity(unittest.TestCase):
    def test_palette_order_is_storage_not_terrain(self):
        a=block_payload_digest(saved_chunk())
        b=block_payload_digest(saved_chunk(
            reverse_blocks=True,reverse_biomes=True))
        self.assertNotEqual(a["raw_storage_diagnostic"],
                            b["raw_storage_diagnostic"])
        for category in ("blocks","biomes","heightmaps"):
            self.assertEqual(a[category],b[category])
        self.assertEqual(saved_world_differences({"64,64":a},
                                                 {"64,64":b}),[])

    def test_identical_compact_and_padded_heightmap_values(self):
        a=saved_chunk()
        b=saved_chunk()
        # 12 bits cross 64-bit boundaries at voxel index 5, 10, ...
        vals=[((i*37)%2097) for i in range(256)]
        a["Heightmaps"]["WORLD_SURFACE"]=packed(vals,12)
        b["Heightmaps"]["WORLD_SURFACE"]=packed_compact(vals,12)
        self.assertEqual(len(a["Heightmaps"]["WORLD_SURFACE"]),52)
        self.assertEqual(len(b["Heightmaps"]["WORLD_SURFACE"]),48)
        pa=block_payload_digest(a)
        pb=block_payload_digest(b)
        self.assertEqual(pa["heightmaps"],pb["heightmaps"])
        self.assertEqual(saved_world_differences({"64,64":pa},
                                                 {"64,64":pb}),[])

    def test_changed_real_block_is_not_ignored(self):
        a=block_payload_digest(saved_chunk())
        b=block_payload_digest(saved_chunk(change_block=True))
        changes=saved_world_differences({"64,64":a},{"64,64":b})
        self.assertEqual(changes,[{
            "chunk":"64,64","category":"blocks","changed_section_Y":["0"]}])

    def test_changed_real_biome_is_not_ignored(self):
        a=block_payload_digest(saved_chunk())
        b=block_payload_digest(saved_chunk(change_biome=True))
        changes=saved_world_differences({"64,64":a},{"64,64":b})
        self.assertEqual(changes,[{
            "chunk":"64,64","category":"biomes","changed_section_Y":["0"]}])

    def test_heightmap_change_is_never_silenced(self):
        a=block_payload_digest(saved_chunk())
        b=block_payload_digest(saved_chunk(change_height=True))
        self.assertEqual(saved_world_differences({"64,64":a},{"64,64":b}),
                         [{"chunk":"64,64","category":"heightmaps"}])

    def test_missing_chunk_is_failure(self):
        a=block_payload_digest(saved_chunk())
        self.assertEqual(saved_world_differences({"64,64":a},{}),
                         [{"chunk":"64,64","category":"missing"}])

    def test_corrupted_palette_indices_must_fail(self):
        data={"palette":[{"Name":"minecraft:stone"},
                         {"Name":"minecraft:air"}],
              "data":packed([0]*4095+[7],4)}
        with self.assertRaisesRegex(AssertionError,"Illegal palette index"):
            _digest_expanded_palette(data,4096,4,{"Name":"minecraft:air"})


if __name__=="__main__":
    unittest.main()
