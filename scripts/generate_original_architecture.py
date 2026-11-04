#!/usr/bin/env python3
"""Build original RealScale architecture templates from deterministic design rules.

These are original block layouts authored in this repository, not downloads or
adaptations of third-party schematics. The generated NBT is embedded in the
optional architecture datapack and placed by vanilla random_spread structure
sets. No runtime network access or external Python packages are required.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import struct
from pathlib import Path

DATA_VERSION = 3955  # Minecraft Java 1.21.1
AIR = ("minecraft:air", ())


def state(name: str, **properties):
    return name, tuple(sorted((key, str(value).lower()) for key, value in properties.items()))


class Building:
    def __init__(self, name: str, size: tuple[int, int, int], description: str):
        self.name = name
        self.width, self.height, self.length = size
        self.description = description
        self.blocks = {
            (x, y, z): AIR
            for y in range(self.height)
            for z in range(self.length)
            for x in range(self.width)
        }

    def set(self, x, y, z, block, **properties):
        if not (0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length):
            raise ValueError(f"{self.name}: block outside bounds at {(x, y, z)}")
        if isinstance(block, str):
            block = state(block, **properties)
        self.blocks[(x, y, z)] = block

    def fill(self, x1, y1, z1, x2, y2, z2, block, **properties):
        block_state = state(block, **properties)
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                for x in range(x1, x2 + 1):
                    self.set(x, y, z, block_state)

    def shell(self, x1, y1, z1, x2, y2, z2, block):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                for x in range(x1, x2 + 1):
                    if x in (x1, x2) or z in (z1, z2):
                        self.set(x, y, z, block)

    def log(self, x, y, z, wood="spruce", axis="y"):
        self.set(x, y, z, f"minecraft:{wood}_log", axis=axis)

    def stair(self, x, y, z, block, facing, half="bottom"):
        self.set(x, y, z, f"minecraft:{block}_stairs",
                 facing=facing, half=half, shape="straight", waterlogged="false")

    def fence(self, x, y, z, wood="oak"):
        self.set(x, y, z, f"minecraft:{wood}_fence",
                 east="false", north="false", south="false",
                 waterlogged="false", west="false")

    def gable_roof(self, x1, x2, z1, z2, eave_y, peak_rise, wood="spruce"):
        """A symmetric, block-stepped roof with solid gable ends."""
        for x in range(x1, x2 + 1):
            rise = min(x - x1, x2 - x)
            rise = min(rise, peak_rise)
            roof_y = eave_y + rise
            facing = "east" if x <= (x1 + x2) // 2 else "west"
            for z in range(z1, z2 + 1):
                self.stair(x, roof_y, z, wood, facing)
            for y in range(eave_y, roof_y):
                self.set(x, y, z1, f"minecraft:{wood}_planks")
                self.set(x, y, z2, f"minecraft:{wood}_planks")

    def write(self, output: Path):
        palette = list(dict.fromkeys(self.blocks.values()))
        indices = {entry: i for i, entry in enumerate(palette)}
        palette_tags = []
        for name, properties in palette:
            fields = [(8, "Name", string_payload(name))]
            if properties:
                fields.append((10, "Properties", compound_payload([
                    (8, key, string_payload(value)) for key, value in properties
                ])))
            palette_tags.append(compound_payload(fields))

        block_tags = []
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    entry = self.blocks[(x, y, z)]
                    block_tags.append(compound_payload([
                        (11, "pos", int_array_payload((x, y, z))),
                        (3, "state", int_payload(indices[entry])),
                    ]))

        root = compound_payload([
            (3, "DataVersion", int_payload(DATA_VERSION)),
            (11, "size", int_array_payload((self.width, self.height, self.length))),
            (9, "palette", list_payload(10, palette_tags)),
            (9, "blocks", list_payload(10, block_tags)),
            (9, "entities", list_payload(10, [])),
        ])
        encoded = tag(10, "", root)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(gzip.compress(encoded, compresslevel=9, mtime=0))
        return {
            "id": self.name,
            "size": [self.width, self.height, self.length],
            "palette": len(palette),
            "blocks": len(block_tags),
            "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "description": self.description,
        }


def string_payload(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def int_payload(value):
    return struct.pack(">i", int(value))


def int_array_payload(values):
    return struct.pack(">i", len(values)) + b"".join(int_payload(v) for v in values)


def tag(tag_type, name, payload):
    return bytes([tag_type]) + string_payload(name) + payload


def compound_payload(entries):
    return b"".join(tag(kind, name, payload) for kind, name, payload in entries) + b"\x00"


def list_payload(subtype, values):
    return bytes([subtype]) + struct.pack(">i", len(values)) + b"".join(values)


def build_forest_ranger_lodge():
    b = Building("forest_ranger_lodge", (13, 10, 13),
                 "Timber ranger cabin with stone footing, porch, chimney and pitched roof.")
    b.fill(1, 0, 1, 11, 0, 11, "minecraft:cobblestone")
    b.fill(2, 1, 2, 10, 1, 10, "minecraft:spruce_planks")
    b.shell(2, 2, 2, 10, 4, 10, "minecraft:spruce_planks")
    for x in (2, 10):
        for z in (2, 10):
            for y in range(2, 6):
                b.log(x, y, z, "spruce")
    # Entrance and glazed windows.
    b.set(6, 2, 2, "minecraft:oak_door", facing="north", half="lower",
          hinge="left", open="false", powered="false")
    b.set(6, 3, 2, "minecraft:oak_door", facing="north", half="upper",
          hinge="left", open="false", powered="false")
    for x, z in ((4, 2), (8, 2), (2, 5), (2, 8), (10, 5), (10, 8)):
        b.set(x, 3, z, "minecraft:glass_pane", east="false", north="false",
              south="false", waterlogged="false", west="false")
    b.gable_roof(1, 11, 1, 11, 5, 3, "spruce")
    for y in range(5, 9):
        b.set(9, y, 9, "minecraft:stone_bricks")
    b.fill(4, 1, 0, 8, 1, 1, "minecraft:spruce_planks")
    b.fence(4, 2, 0, "spruce")
    b.fence(8, 2, 0, "spruce")
    b.set(5, 2, 6, "minecraft:lantern", hanging="false")
    b.set(7, 2, 6, "minecraft:crafting_table")
    b.set(8, 2, 8, "minecraft:barrel", facing="up", open="false")
    b.set(4, 2, 8, "minecraft:bookshelf")
    b.set(6, 2, 9, "minecraft:red_bed", facing="south", part="foot",
          occupied="false")
    return b


def build_plains_farmstead():
    b = Building("plains_farmstead", (17, 10, 17),
                 "Original farmhouse and timber barn with fenced crop plots and a water trough.")
    # Farmhouse.
    b.fill(1, 0, 3, 7, 0, 10, "minecraft:stone_bricks")
    b.fill(2, 1, 4, 6, 1, 9, "minecraft:oak_planks")
    b.shell(1, 2, 3, 7, 4, 10, "minecraft:oak_planks")
    for x in (1, 7):
        for z in (3, 10):
            for y in range(2, 6):
                b.log(x, y, z, "oak")
    b.set(4, 2, 3, "minecraft:oak_door", facing="north", half="lower",
          hinge="left", open="false", powered="false")
    b.set(4, 3, 3, "minecraft:oak_door", facing="north", half="upper",
          hinge="left", open="false", powered="false")
    for x, z in ((2, 3), (6, 3), (1, 6), (7, 6), (3, 10), (5, 10)):
        b.set(x, 3, z, "minecraft:glass_pane", east="false", north="false",
              south="false", waterlogged="false", west="false")
    b.gable_roof(0, 8, 2, 11, 5, 3, "oak")
    # Barn.
    b.fill(9, 0, 3, 15, 0, 10, "minecraft:coarse_dirt")
    b.fill(10, 1, 4, 14, 1, 9, "minecraft:spruce_planks")
    b.shell(9, 2, 3, 15, 4, 10, "minecraft:red_terracotta")
    for x in (9, 15):
        for z in (3, 10):
            for y in range(2, 6):
                b.log(x, y, z, "oak")
    for x in (11, 12, 13):
        b.set(x, 2, 3, "minecraft:air")
        b.set(x, 3, 3, "minecraft:air")
    b.set(12, 2, 3, "minecraft:oak_fence_gate", facing="north",
          open="true", powered="false", in_wall="false")
    b.gable_roof(8, 16, 2, 11, 5, 3, "spruce")
    b.set(10, 2, 8, "minecraft:hay_block", axis="y")
    b.set(11, 2, 8, "minecraft:hay_block", axis="y")
    b.set(12, 2, 8, "minecraft:hay_block", axis="y")
    # Crop garden: farmland and crops replace the ground surface intentionally.
    for x in range(2, 7):
        for z in range(12, 16):
            if x == 4:
                b.set(x, 0, z, "minecraft:water", level="0")
            else:
                b.set(x, 0, z, "minecraft:farmland", moisture="7")
                b.set(x, 1, z, "minecraft:wheat", age="7")
    # A low fence defines the garden and a composter sits by the gate.
    for x in range(1, 8):
        b.fence(x, 1, 11, "oak")
        b.fence(x, 1, 16, "oak")
    for z in range(11, 17):
        b.fence(1, 1, z, "oak")
        b.fence(7, 1, z, "oak")
    b.set(8, 1, 12, "minecraft:composter", level="0")
    b.set(9, 1, 12, "minecraft:water_cauldron", level="1")
    b.set(4, 2, 6, "minecraft:lantern", hanging="false")
    b.set(5, 2, 8, "minecraft:crafting_table")
    return b


def build_alpine_waystation():
    b = Building("alpine_waystation", (13, 11, 13),
                 "Compact alpine shelter built from stone, spruce beams and a steep weather roof.")
    b.fill(1, 0, 1, 11, 0, 11, "minecraft:stone_bricks")
    b.fill(2, 1, 2, 10, 1, 10, "minecraft:spruce_planks")
    b.shell(2, 2, 2, 10, 5, 10, "minecraft:cobblestone")
    for x in (2, 10):
        for z in (2, 10):
            for y in range(2, 7):
                b.log(x, y, z, "spruce")
    b.set(6, 2, 2, "minecraft:spruce_door", facing="north", half="lower",
          hinge="left", open="false", powered="false")
    b.set(6, 3, 2, "minecraft:spruce_door", facing="north", half="upper",
          hinge="left", open="false", powered="false")
    for x, z in ((4, 2), (8, 2), (2, 6), (10, 6), (4, 10), (8, 10)):
        b.set(x, 4, z, "minecraft:glass_pane", east="false", north="false",
              south="false", waterlogged="false", west="false")
    b.gable_roof(1, 11, 1, 11, 6, 3, "spruce")
    # Protected hearth and smoke outlet.
    b.fill(8, 6, 8, 9, 9, 9, "minecraft:stone_bricks")
    b.set(8, 2, 8, "minecraft:campfire", facing="north", lit="true",
          signal_fire="false", waterlogged="false")
    b.set(5, 2, 6, "minecraft:lantern", hanging="false")
    b.set(7, 2, 6, "minecraft:crafting_table")
    b.set(4, 2, 8, "minecraft:barrel", facing="up", open="false")
    b.set(6, 2, 9, "minecraft:red_bed", facing="south", part="foot",
          occupied="false")
    return b


def build_coastal_lighthouse():
    b = Building("coastal_lighthouse", (9, 20, 9),
                 "Original tapered coastal beacon with red-white bands, observation gallery and lantern room.")
    b.fill(0, 0, 0, 8, 0, 8, "minecraft:stone_bricks")
    b.fill(1, 1, 1, 7, 1, 7, "minecraft:stone_bricks")
    for y in range(1, 16):
        material = "minecraft:white_concrete" if (y // 3) % 2 == 0 else "minecraft:red_concrete"
        inset = 1 if y < 10 else 2
        for x in range(inset, 9 - inset):
            for z in range(inset, 9 - inset):
                if x in (inset, 8 - inset) or z in (inset, 8 - inset):
                    b.set(x, y, z, material)
        if y in (5, 10, 14):
            b.fill(inset + 1, y, inset + 1, 7 - inset, y, 7 - inset,
                   "minecraft:spruce_planks")
    b.set(4, 2, 1, "minecraft:oak_door", facing="north", half="lower",
          hinge="left", open="false", powered="false")
    b.set(4, 3, 1, "minecraft:oak_door", facing="north", half="upper",
          hinge="left", open="false", powered="false")
    for y in range(2, 15):
        b.set(4, y, 4, "minecraft:ladder", facing="north", waterlogged="false")
    for y in (4, 8, 12):
        b.set(2, y, 4, "minecraft:glass_pane", east="false", north="false",
              south="false", waterlogged="false", west="false")
        b.set(6, y, 4, "minecraft:glass_pane", east="false", north="false",
              south="false", waterlogged="false", west="false")
    for x in range(1, 8):
        for z in range(1, 8):
            if x in (1, 7) or z in (1, 7):
                b.set(x, 16, z, "minecraft:stone_brick_slab",
                      type="bottom", waterlogged="false")
                b.set(x, 17, z, "minecraft:iron_bars",
                      east="false", north="false", south="false",
                      waterlogged="false", west="false")
    b.fill(2, 16, 2, 6, 16, 6, "minecraft:stone_bricks")
    b.fill(2, 17, 2, 6, 18, 6, "minecraft:glass")
    b.set(4, 18, 4, "minecraft:sea_lantern")
    b.fill(1, 19, 1, 7, 19, 7, "minecraft:stone_brick_slab",
           type="top", waterlogged="false")
    return b


def build_desert_caravanserai():
    b = Building("desert_caravanserai", (17, 9, 17),
                 "Original sandstone courtyard inn with shaded arcade, corner towers and central cistern.")
    b.fill(0, 0, 0, 16, 0, 16, "minecraft:sandstone")
    # Outer defensive wall.
    for y in range(1, 6):
        for x in range(1, 16):
            for z in range(1, 16):
                if x in (1, 15) or z in (1, 15):
                    b.set(x, y, z, "minecraft:sandstone")
    # Four slightly raised corner towers.
    for x, z in ((1, 1), (1, 15), (15, 1), (15, 15)):
        b.fill(x, 1, z, x, 6, z, "minecraft:cut_sandstone")
        b.set(x, 7, z, "minecraft:sandstone_wall", east="none", north="none",
              south="none", up="true", waterlogged="false", west="none")
    # Broad entrance, framed by cut-sandstone columns.
    for x in (7, 9):
        b.fill(x, 1, 1, x, 4, 1, "minecraft:cut_sandstone")
    for x in range(7, 10):
        b.set(x, 5, 1, "minecraft:chiseled_sandstone")
    b.set(8, 1, 1, "minecraft:air")
    b.set(8, 2, 1, "minecraft:air")
    b.set(8, 3, 1, "minecraft:air")
    # Covered room arcade around the inner courtyard.
    for x in range(3, 14):
        for z in (3, 13):
            b.set(x, 1, z, "minecraft:smooth_sandstone")
            b.set(x, 2, z, "minecraft:sandstone")
            b.set(x, 3, z, "minecraft:sandstone")
            b.set(x, 4, z, "minecraft:sandstone_slab",
                  type="bottom", waterlogged="false")
    for z in range(3, 14):
        for x in (3, 13):
            b.set(x, 1, z, "minecraft:smooth_sandstone")
            b.set(x, 2, z, "minecraft:sandstone")
            b.set(x, 3, z, "minecraft:sandstone")
            b.set(x, 4, z, "minecraft:sandstone_slab",
                  type="bottom", waterlogged="false")
    # Pillars and lanterns.
    for x, z in ((3, 3), (13, 3), (3, 13), (13, 13),
                 (8, 3), (8, 13), (3, 8), (13, 8)):
        b.fill(x, 1, z, x, 4, z, "minecraft:cut_sandstone")
        b.set(x, 5, z, "minecraft:lantern", hanging="true")
    # Cistern and date-palm-inspired central canopy.
    b.fill(7, 1, 7, 9, 1, 9, "minecraft:cut_sandstone")
    b.fill(8, 2, 8, 8, 2, 8, "minecraft:water", level="0")
    for x, z in ((6, 6), (10, 6), (6, 10), (10, 10)):
        b.log(x, 2, z, "jungle")
        b.log(x, 3, z, "jungle")
    b.fill(6, 4, 6, 10, 4, 10, "minecraft:acacia_slab",
           type="bottom", waterlogged="false")
    # Storage and stable stalls on the east side.
    b.fill(11, 1, 5, 12, 1, 11, "minecraft:coarse_dirt")
    for z in (5, 8, 11):
        b.fence(11, 2, z, "acacia")
    b.set(5, 2, 5, "minecraft:barrel", facing="up", open="false")
    b.set(5, 2, 11, "minecraft:crafting_table")
    b.set(8, 2, 2, "minecraft:carpet")
    return b


BUILDINGS = (
    build_forest_ranger_lodge,
    build_plains_farmstead,
    build_alpine_waystation,
    build_coastal_lighthouse,
    build_desert_caravanserai,
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output / "data/realscale/structures/architecture"
    manifest = []
    for factory in BUILDINGS:
        building = factory()
        result = building.write(output / f"{building.name}.nbt")
        manifest.append(result)
        print("ORIGINAL_ARCHITECTURE_TEMPLATE", result)
    manifest_path = args.output / "original-architecture-manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"ORIGINAL_ARCHITECTURE_GENERATION_OK: {len(manifest)} original structures")


if __name__ == "__main__":
    main()
