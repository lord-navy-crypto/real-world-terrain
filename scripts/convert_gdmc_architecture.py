#!/usr/bin/env python3
"""Convert three Apache-2.0 GDMC legacy schematics to Minecraft 1.21.1 NBT.

Only the explicitly listed source files are converted. Unknown legacy block
IDs fail the build. Block-entity payloads and entities are intentionally
omitted; see docs/architecture/ASSET_PROVENANCE.md.
"""
from __future__ import annotations
import argparse, gzip, hashlib, struct
from pathlib import Path

ASSETS = {
    "modern_house_1.schematic": ("modern_house", (12, 10, 12),
        "8febc4e110e8324e5955d6492ba8993c12285abc8e7696dffb9c50af30882e01"),
    "greenhouse_2.schematic": ("glass_greenhouse", (12, 7, 12),
        "c0097e5d1367e79a072b3282a565f562cdce0db3ac535de6be78c501e5a88155"),
    "library.schematic": ("city_library", (24, 10, 20),
        "1401f67f642a617d03427083959e4501c7f994614de4c522b626673644194ad0"),
}
DATA_VERSION = 3955  # Minecraft Java 1.21.1

def read_string(data, offset):
    length = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    return data[offset:offset + length].decode("utf-8"), offset + length

def read_payload(data, offset, tag_type):
    if tag_type == 1: return struct.unpack_from(">b", data, offset)[0], offset + 1
    if tag_type == 2: return struct.unpack_from(">h", data, offset)[0], offset + 2
    if tag_type == 3: return struct.unpack_from(">i", data, offset)[0], offset + 4
    if tag_type == 4: return struct.unpack_from(">q", data, offset)[0], offset + 8
    if tag_type == 5: return struct.unpack_from(">f", data, offset)[0], offset + 4
    if tag_type == 6: return struct.unpack_from(">d", data, offset)[0], offset + 8
    if tag_type == 7:
        length = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        return data[offset:offset + length], offset + length
    if tag_type == 8: return read_string(data, offset)
    if tag_type == 9:
        subtype = data[offset]
        length = struct.unpack_from(">i", data, offset + 1)[0]
        offset += 5
        values = []
        for _ in range(length):
            value, offset = read_payload(data, offset, subtype)
            values.append(value)
        return values, offset
    if tag_type == 10:
        result = {}
        while True:
            child_type = data[offset]
            offset += 1
            if child_type == 0: break
            name, offset = read_string(data, offset)
            value, offset = read_payload(data, offset, child_type)
            result[name] = value
        return result, offset
    if tag_type == 11:
        length = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        values = list(struct.unpack_from(">" + "i" * length, data, offset)) if length else []
        return values, offset + 4 * length
    if tag_type == 12:
        length = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        values = list(struct.unpack_from(">" + "q" * length, data, offset)) if length else []
        return values, offset + 8 * length
    raise ValueError(f"Unsupported NBT tag type {tag_type}")

def parse_nbt(data):
    if data[0] != 10:
        raise ValueError("Expected root TAG_Compound")
    root_name, offset = read_string(data, 1)
    root, offset = read_payload(data, offset, 10)
    if offset != len(data):
        raise ValueError("Trailing bytes after NBT root")
    return root_name, root

def read_schematic(path):
    _, root = parse_nbt(gzip.decompress(path.read_bytes()))
    if root.get("Materials") != "Alpha":
        raise ValueError(f"{path.name}: unsupported legacy Materials tag")
    return root

def state(name, **properties):
    return name, tuple(sorted((key, str(value)) for key, value in properties.items()))

def legacy_state(block_id, meta):
    if block_id == 0: return state("minecraft:air")
    if block_id == 1:
        variants = ["stone", "granite", "polished_granite", "diorite",
                    "polished_diorite", "andesite", "polished_andesite"]
        return state("minecraft:" + (variants[meta] if meta < len(variants) else "stone"))
    if block_id == 2: return state("minecraft:grass_block", snowy="false")
    if block_id == 3:
        return state("minecraft:" + {0:"dirt", 1:"coarse_dirt", 2:"podzol"}.get(meta, "dirt"))
    if block_id == 5:
        names = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]
        return state(f"minecraft:{names[meta & 7] if (meta & 7) < len(names) else 'oak'}_planks")
    if block_id == 8: return state("minecraft:water", level=str(min(meta, 15)))
    if block_id == 17:
        names = ["oak", "spruce", "birch", "jungle"]
        axis = {0:"y", 4:"x", 8:"z", 12:"y"}[meta & 12]
        return state(f"minecraft:{names[meta & 3]}_log", axis=axis)
    if block_id == 18:
        names = ["oak", "spruce", "birch", "jungle"]
        return state(f"minecraft:{names[meta & 3]}_leaves", distance="7", persistent="true")
    if block_id == 20: return state("minecraft:glass")
    if block_id == 26:
        facing = {0:"south", 1:"west", 2:"north", 3:"east"}[meta & 3]
        return state("minecraft:red_bed", facing=facing,
                     part="head" if meta & 8 else "foot", occupied="false")
    if block_id == 38:
        flowers = ["poppy", "blue_orchid", "allium", "azure_bluet",
                   "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"]
        return state("minecraft:" + (flowers[meta] if meta < len(flowers) else "poppy"))
    if block_id == 43:
        names = ["stone", "sandstone", "oak_planks", "cobblestone",
                 "bricks", "stone_bricks", "nether_bricks", "quartz_block"]
        return state("minecraft:" + names[meta & 7])
    if block_id == 44:
        names = ["stone", "sandstone", "petrified_oak", "cobblestone",
                 "brick", "stone_brick", "nether_brick", "quartz"]
        return state(f"minecraft:{names[meta & 7]}_slab",
                     type="top" if meta & 8 else "bottom", waterlogged="false")
    if block_id == 45: return state("minecraft:bricks")
    if block_id == 47: return state("minecraft:bookshelf")
    if block_id == 50:
        if meta == 5: return state("minecraft:torch")
        facing = {1:"east", 2:"west", 3:"south", 4:"north"}.get(meta, "north")
        return state("minecraft:wall_torch", facing=facing)
    if block_id in (53, 109, 134, 135, 164):
        names = {53:"oak", 109:"stone_brick", 134:"spruce", 135:"birch", 164:"acacia"}
        facing = {0:"east", 1:"west", 2:"south", 3:"north"}[meta & 3]
        return state(f"minecraft:{names[block_id]}_stairs", facing=facing,
                     half="top" if meta & 4 else "bottom",
                     shape="straight", waterlogged="false")
    if block_id == 54:
        facing = {2:"north", 3:"south", 4:"west", 5:"east"}.get(meta, "north")
        return state("minecraft:chest", facing=facing, type="single", waterlogged="false")
    if block_id == 59: return state("minecraft:wheat", age=str(min(meta, 7)))
    if block_id == 60: return state("minecraft:farmland", moisture=str(min(meta, 7)))
    if block_id in (64, 193):
        name = {64:"oak", 193:"spruce"}[block_id]
        if meta & 8:
            return state(f"minecraft:{name}_door", facing="south", half="upper",
                         hinge="left", open="false", powered="false")
        facing = {0:"east", 1:"south", 2:"west", 3:"north"}[meta & 3]
        return state(f"minecraft:{name}_door", facing=facing, half="lower",
                     hinge="left", open="false", powered="false")
    if block_id == 68:
        facing = {2:"north", 3:"south", 4:"west", 5:"east"}.get(meta, "north")
        return state("minecraft:oak_wall_sign", facing=facing, waterlogged="false")
    if block_id == 69: return state("minecraft:lever", face="wall", facing="north", powered="false")
    if block_id == 72: return state("minecraft:oak_pressure_plate", powered="false")
    if block_id == 85:
        return state("minecraft:oak_fence", east="false", north="false",
                     south="false", waterlogged="false", west="false")
    if block_id == 95:
        colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime",
                  "pink", "gray", "light_gray", "cyan", "purple", "blue",
                  "brown", "green", "red", "black"]
        return state(f"minecraft:{colors[meta & 15]}_stained_glass")
    if block_id == 96:
        facing = {0:"south", 1:"north", 2:"east", 3:"west"}[meta & 3]
        return state("minecraft:oak_trapdoor", facing=facing,
                     half="top" if meta & 8 else "bottom",
                     open="true" if meta & 4 else "false",
                     powered="false", waterlogged="false")
    if block_id == 123: return state("minecraft:redstone_lamp", lit="false")
    if block_id == 124: return state("minecraft:redstone_lamp", lit="true")
    if block_id == 125:
        names = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]
        return state(f"minecraft:{names[meta & 7]}_planks")
    if block_id == 126:
        names = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]
        return state(f"minecraft:{names[meta & 7]}_slab",
                     type="top" if meta & 8 else "bottom", waterlogged="false")
    if block_id == 139:
        return state("minecraft:cobblestone_wall", east="none", north="none",
                     south="none", up="true", waterlogged="false", west="none")
    if block_id == 140: return state("minecraft:flower_pot")
    if block_id == 152: return state("minecraft:redstone_block")
    if block_id == 155:
        if meta in (2, 3, 4):
            return state("minecraft:quartz_pillar", axis={2:"y", 3:"x", 4:"z"}[meta])
        return state("minecraft:chiseled_quartz_block" if meta == 1 else "minecraft:quartz_block")
    if block_id == 160:
        colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime",
                  "pink", "gray", "light_gray", "cyan", "purple", "blue",
                  "brown", "green", "red", "black"]
        return state(f"minecraft:{colors[meta & 15]}_stained_glass_pane",
                     east="false", north="false", south="false",
                     waterlogged="false", west="false")
    if block_id == 169: return state("minecraft:sea_lantern")
    if block_id == 171:
        colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime",
                  "pink", "gray", "light_gray", "cyan", "purple", "blue",
                  "brown", "green", "red", "black"]
        return state(f"minecraft:{colors[meta & 15]}_carpet")
    if block_id == 178: return state("minecraft:daylight_detector", power="0")
    if block_id in (183, 184, 185, 186, 187):
        names = {183:"spruce", 184:"birch", 185:"jungle", 186:"dark_oak", 187:"acacia"}
        facing = {0:"south", 1:"west", 2:"north", 3:"east"}[meta & 3]
        return state(f"minecraft:{names[block_id]}_fence_gate", facing=facing,
                     open="false", powered="false", in_wall="false")
    if block_id in (188, 189, 190, 191, 192):
        names = {188:"spruce", 189:"birch", 190:"jungle", 191:"dark_oak", 192:"acacia"}
        return state(f"minecraft:{names[block_id]}_fence",
                     east="false", north="false", south="false",
                     waterlogged="false", west="false")
    if block_id == 208: return state("minecraft:dirt_path")
    raise ValueError(f"Unsupported legacy block ID {block_id}:{meta}")

def tag(tag_type, name, payload):
    encoded = name.encode("utf-8")
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + payload

def string_payload(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded

def int_payload(value): return struct.pack(">i", value)

def int_list_payload(values):
    """Encode BlockPos/Vec3i coordinates as TAG_List<TAG_Int>, not TAG_Int_Array."""
    return list_payload(3, [int_payload(v) for v in values])

def compound_payload(entries):
    return b"".join(tag(tag_type, name, payload) for tag_type, name, payload in entries) + b"\x00"

def list_payload(subtype, values):
    return bytes([subtype]) + struct.pack(">i", len(values)) + b"".join(values)

def convert_schematic(source, output):
    compressed = source.read_bytes()
    digest = hashlib.sha256(compressed).hexdigest()
    name, expected_size, expected_digest = ASSETS[source.name]
    if digest != expected_digest:
        raise ValueError(f"{source.name}: source digest changed ({digest})")
    root = read_schematic(source)
    width, height, length = root["Width"], root["Height"], root["Length"]
    if (width, height, length) != expected_size:
        raise ValueError(f"{source.name}: unexpected dimensions {(width, height, length)}")
    blocks, data = root["Blocks"], root["Data"]
    if "AddBlocks" in root:
        raise ValueError(f"{source.name}: AddBlocks legacy IDs are not supported")
    if len(blocks) != width * height * length or len(data) != len(blocks):
        raise ValueError(f"{source.name}: block/data array length mismatch")

    palette, palette_index, entries = [], {}, []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                index = x + z * width + y * width * length
                block_id, meta = blocks[index] & 0xff, data[index] & 0xff
                block_state = legacy_state(block_id, meta)
                if block_state not in palette_index:
                    palette_index[block_state] = len(palette)
                    palette.append(block_state)
                entries.append((x, y, z, palette_index[block_state]))

    palette_payloads = []
    for block_name, properties in palette:
        fields = [(8, "Name", string_payload(block_name))]
        if properties:
            fields.append((10, "Properties", compound_payload([
                (8, key, string_payload(value)) for key, value in properties
            ])))
        palette_payloads.append(compound_payload(fields))

    block_payloads = []
    for x, y, z, state_index in entries:
        block_payloads.append(compound_payload([
            (9, "pos", int_list_payload([x, y, z])),
            (3, "state", int_payload(state_index)),
        ]))

    root_fields = [
        (3, "DataVersion", int_payload(DATA_VERSION)),
        (9, "size", int_list_payload([width, height, length])),
        (9, "palette", list_payload(10, palette_payloads)),
        (9, "blocks", list_payload(10, block_payloads)),
        (9, "entities", list_payload(10, [])),
    ]
    encoded = tag(10, "", compound_payload(root_fields))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(gzip.compress(encoded, compresslevel=9, mtime=0))
    return {"template": name, "size": expected_size, "palette": len(palette),
            "blocks": len(entries), "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    for source_name in ASSETS:
        source = args.source / source_name
        if not source.is_file():
            raise SystemExit(f"Missing licensed source asset: {source}")
        target_name = ASSETS[source_name][0]
        target = args.output / "data/realscale/structure/architecture" / f"{target_name}.nbt"
        print("ARCHITECTURE_TEMPLATE", convert_schematic(source, target))
    print("GDMC_ARCHITECTURE_CONVERSION_OK: 3 Apache-2.0 templates, Minecraft 1.21.1")

if __name__ == "__main__":
    main()
