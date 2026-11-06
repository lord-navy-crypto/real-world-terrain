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

    def _detail_support_is_solid(self, block_name: str) -> bool:
        if block_name in {
            "minecraft:air", "minecraft:water", "minecraft:lava",
            "minecraft:glass", "minecraft:glass_pane", "minecraft:iron_bars",
            "minecraft:chain", "minecraft:rail", "minecraft:torch",
            "minecraft:redstone_torch", "minecraft:lantern",
        }:
            return False
        return not block_name.endswith((
            "_leaves", "_pane", "_fence", "_wall", "_stairs", "_slab",
            "_carpet", "_flower", "_sapling", "_door", "_trapdoor",
        ))

    def _detail_surface_candidates(self):
        candidates = []
        center_x, center_z = self.width // 2, self.length // 2
        for y in range(2, min(self.height - 1, 10)):
            for z in range(2, self.length - 2):
                for x in range(2, self.width - 2):
                    if self.blocks[(x, y, z)][0] != "minecraft:air":
                        continue
                    if self.blocks[(x, y + 1, z)][0] != "minecraft:air":
                        continue
                    support = self.blocks[(x, y - 1, z)][0]
                    if not self._detail_support_is_solid(support):
                        continue
                    edge_distance = min(
                        x - 1, self.width - 2 - x,
                        z - 1, self.length - 2 - z,
                    )
                    center_distance = abs(x - center_x) + abs(z - center_z)
                    candidates.append((
                        (abs(y - 2), edge_distance, -center_distance, y, z, x),
                        (x, y, z),
                    ))
        candidates.sort(key=lambda item: item[0])
        return [position for _, position in candidates]

    def _place_architecture_fixture(self, position, block_name: str):
        x, y, z = position
        if block_name == "minecraft:lantern":
            self.set(x, y, z, block_name, hanging="false")
        elif block_name == "minecraft:barrel":
            self.set(x, y, z, block_name, facing="up", open="false")
        elif block_name == "minecraft:chest":
            self.set(x, y, z, block_name, facing="north",
                     type="single", waterlogged="false")
        elif block_name == "minecraft:campfire":
            self.set(x, y, z, block_name, facing="north", lit="true",
                     signal_fire="false", waterlogged="false")
        elif block_name == "minecraft:bell":
            self.set(x, y, z, block_name, attachment="floor",
                     facing="north", powered="false")
        elif block_name == "minecraft:rail":
            self.set(x, y, z, block_name, shape="east_west",
                     powered="false", waterlogged="false")
        else:
            self.set(x, y, z, block_name)

    def _add_entrance_canopy(self):
        doors = []
        for (x, y, z), (block_name, properties) in self.blocks.items():
            if block_name.endswith("_door"):
                props = dict(properties)
                if props.get("half") == "lower":
                    doors.append((x, y, z, props.get("facing", "north")))
        if not doors:
            return

        palette = {block_name for block_name, _ in self.blocks.values()}
        wood = next((
            name.split(":")[-1].removesuffix("_log")
            for name in sorted(palette)
            if name.endswith("_log") and "stripped_" not in name
        ), "oak")
        path_block = next((
            name for name in (
                "minecraft:dirt_path", "minecraft:coarse_dirt",
                "minecraft:gravel", "minecraft:stone_bricks",
                "minecraft:cobblestone", "minecraft:sandstone",
                "minecraft:mud_bricks",
            ) if name in palette
        ), "minecraft:cobblestone")
        slab_block = f"minecraft:{wood}_slab"
        if slab_block not in palette:
            slab_block = "minecraft:stone_brick_slab"
        directions = {
            "north": (0, -1, 1, 0),
            "south": (0, 1, 1, 0),
            "west": (-1, 0, 0, 1),
            "east": (1, 0, 0, 1),
        }

        for x, y, z, facing in sorted(doors):
            if facing not in directions or y + 2 >= self.height:
                continue
            dx, dz, px, pz = directions[facing]
            front_x, front_z = x + dx, z + dz
            posts = (
                (front_x + px, front_z + pz),
                (front_x - px, front_z - pz),
            )
            canopy = (
                (front_x - px, front_z - pz),
                (front_x, front_z),
                (front_x + px, front_z + pz),
            )
            if any(
                not (1 <= sx < self.width - 1 and 1 <= sz < self.length - 1)
                for sx, sz in canopy
            ):
                continue
            if any(
                self.blocks[(sx, y, sz)][0] != "minecraft:air"
                or self.blocks[(sx, y + 1, sz)][0] != "minecraft:air"
                for sx, sz in posts
            ):
                continue
            if any(
                self.blocks[(sx, y + 2, sz)][0] != "minecraft:air"
                for sx, sz in canopy
            ):
                continue

            # Only add the canopy when it is genuinely missing; existing porches
            # and entrance halls are left intact.
            for sx, sz in posts:
                if self.blocks[(sx, y - 1, sz)][0] == "minecraft:air":
                    self.set(sx, y - 1, sz, path_block)
                self.log(sx, y, sz, wood)
                self.log(sx, y + 1, sz, wood)
            for sx, sz in canopy:
                self.set(sx, y + 2, sz, slab_block,
                         type="bottom", waterlogged="false")
            for step in (2, 3):
                px2, pz2 = x + dx * step, z + dz * step
                if not (1 <= px2 < self.width - 1 and 1 <= pz2 < self.length - 1):
                    break
                if self.blocks[(px2, y, pz2)][0] == "minecraft:air":
                    if self.blocks[(px2, y - 1, pz2)][0] == "minecraft:air":
                        self.set(px2, y - 1, pz2, path_block)
            return

    def _add_roof_vent(self):
        name = self.name.lower()
        heat_tokens = (
            "lodge", "farmstead", "barn", "inn", "blacksmith", "bakery",
            "clinic", "waystation", "camp", "shelter", "caravanserai",
            "kiln", "sawmill", "workshop", "cider", "storehouse",
            "cottage", "smith", "forge", "herbalist",
        )
        if not any(token in name for token in heat_tokens):
            return
        if any(
            block_name in ("minecraft:campfire", "minecraft:smoker",
                           "minecraft:blast_furnace")
            for block_name, _ in self.blocks.values()
        ) and "camp" not in name and "kiln" not in name and "forge" not in name:
            return

        palette = {block_name for block_name, _ in self.blocks.values()}
        chimney_block = next((
            name for name in (
                "minecraft:stone_bricks", "minecraft:bricks",
                "minecraft:cobblestone", "minecraft:deepslate_bricks",
            ) if name in palette
        ), "minecraft:stone_bricks")
        roof_positions = []
        for z in range(2, self.length - 2):
            for x in range(2, self.width - 2):
                top_y = -1
                top_name = "minecraft:air"
                for y in range(self.height - 2, 3, -1):
                    block_name = self.blocks[(x, y, z)][0]
                    if block_name != "minecraft:air":
                        top_y, top_name = y, block_name
                        break
                if top_y < 4 or top_y + 3 >= self.height:
                    continue
                if not top_name.endswith(("_stairs", "_slab", "_planks")):
                    continue
                if any(
                    self.blocks[(x, top_y + offset, z)][0] != "minecraft:air"
                    for offset in (1, 2, 3)
                ):
                    continue
                edge_distance = min(
                    x - 1, self.width - 2 - x,
                    z - 1, self.length - 2 - z,
                )
                roof_positions.append((
                    (-top_y, edge_distance, abs(x - self.width // 2), z, x),
                    (x, top_y, z),
                ))
        if not roof_positions:
            return
        roof_positions.sort(key=lambda item: item[0])
        x, top_y, z = roof_positions[0][1]
        self.set(x, top_y + 1, z, chimney_block)
        self.set(x, top_y + 2, z, chimney_block)
        self.set(x, top_y + 3, z, "minecraft:campfire",
                 facing="north", lit="true",
                 signal_fire="false", waterlogged="false")

    def enrich_architecture(self):
        """Add deterministic lighting, role-specific fixtures and missing entry details."""
        if getattr(self, "_architecture_details_applied", False):
            return self
        self._architecture_details_applied = True

        self._add_entrance_canopy()
        candidates = self._detail_surface_candidates()
        if not candidates:
            return self

        name = self.name.lower()
        if any(token in name for token in (
            "farm", "orchard", "grove", "apiary", "ranch", "sheepfold",
            "dairy", "cattle", "vineyard", "herbalist", "garden",
            "greenhouse", "seed", "granary", "agriculture",
        )):
            fixtures = (
                "minecraft:composter", "minecraft:barrel",
                "minecraft:hay_block", "minecraft:cauldron",
            )
        elif any(token in name for token in (
            "blacksmith", "forge", "workshop", "quarry", "mine", "ore",
            "sawmill", "kiln", "charcoal", "industrial", "obsidian",
            "mason", "lumber", "depot",
        )):
            fixtures = (
                "minecraft:anvil", "minecraft:blast_furnace",
                "minecraft:barrel", "minecraft:grindstone",
            )
        elif any(token in name for token in (
            "fishing", "boat", "coastal", "water", "canal", "river",
            "pier", "lighthouse", "ferry", "dock",
        )):
            fixtures = (
                "minecraft:barrel", "minecraft:smoker",
                "minecraft:cartography_table", "minecraft:lantern",
            )
        elif any(token in name for token in (
            "school", "clinic", "monastery", "chapel", "academy",
            "courthouse", "observatory", "library", "tea_house",
        )):
            fixtures = (
                "minecraft:lectern", "minecraft:bookshelf",
                "minecraft:flower_pot", "minecraft:cartography_table",
            )
        elif any(token in name for token in (
            "market", "trade", "inn", "caravan", "station", "store",
            "relay", "waystation",
        )):
            fixtures = (
                "minecraft:barrel", "minecraft:chest",
                "minecraft:loom", "minecraft:cartography_table",
            )
        elif any(token in name for token in (
            "watch", "fort", "ranger", "signal", "tower", "outpost",
        )):
            fixtures = (
                "minecraft:target", "minecraft:cartography_table",
                "minecraft:barrel", "minecraft:chest",
            )
        else:
            fixtures = (
                "minecraft:crafting_table", "minecraft:barrel",
                "minecraft:bookshelf", "minecraft:flower_pot",
            )

        existing = {block_name for block_name, _ in self.blocks.values()}
        seed = sum((index + 1) * ord(char) for index, char in enumerate(self.name))
        ordered_fixtures = fixtures[seed % len(fixtures):] + fixtures[:seed % len(fixtures)]
        used = set()

        lantern_count = sum(
            1 for block_name, _ in self.blocks.values()
            if block_name == "minecraft:lantern"
        )
        if lantern_count < 2:
            light_slots = candidates[:]
            if light_slots:
                first = light_slots.pop(0)
                self._place_architecture_fixture(first, "minecraft:lantern")
                used.add(first)
                if lantern_count == 0 and light_slots:
                    second = max(
                        (position for position in light_slots if position not in used),
                        key=lambda position: (
                            abs(position[0] - first[0]) + abs(position[2] - first[2]),
                            -position[1], position[2], position[0],
                        ),
                    )
                    self._place_architecture_fixture(second, "minecraft:lantern")
                    used.add(second)

        placed = 0
        for fixture in ordered_fixtures:
            if fixture in existing:
                continue
            position = next(
                (candidate for candidate in candidates
                 if candidate not in used
                 and self.blocks[candidate][0] == "minecraft:air"),
                None,
            )
            if position is None:
                break
            self._place_architecture_fixture(position, fixture)
            used.add(position)
            existing.add(fixture)
            placed += 1
            if placed >= 2:
                break

        self._add_roof_vent()
        return self

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
        self.enrich_architecture()
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
                        (9, "pos", int_list_payload((x, y, z))),
                        (3, "state", int_payload(indices[entry])),
                    ]))

        root = compound_payload([
            (3, "DataVersion", int_payload(DATA_VERSION)),
            (9, "size", int_list_payload((self.width, self.height, self.length))),
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


def int_list_payload(values):
    """Encode BlockPos/Vec3i coordinates as TAG_List<TAG_Int>, not TAG_Int_Array."""
    return list_payload(3, [int_payload(v) for v in values])


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
    b.set(8, 2, 2, "minecraft:orange_carpet")
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
    output = args.output / "data/realscale/structure/architecture"
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
