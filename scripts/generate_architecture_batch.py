#!/usr/bin/env python3
"""Batch-generate original RealScale structures from reusable design primitives."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from generate_original_architecture import Building


def door(b, x, y, z, wood="oak", facing="north"):
    b.set(x, y, z, f"minecraft:{wood}_door", facing=facing, half="lower",
          hinge="left", open="false", powered="false")
    b.set(x, y + 1, z, f"minecraft:{wood}_door", facing=facing, half="upper",
          hinge="left", open="false", powered="false")


def pane(b, x, y, z):
    b.set(x, y, z, "minecraft:glass_pane", east="false", north="false",
          south="false", waterlogged="false", west="false")


def slab(b, x, y, z, material="oak", half="bottom"):
    b.set(x, y, z, f"minecraft:{material}_slab",
          type=half, waterlogged="false")


def stairs(b, x, y, z, material="oak", facing="north", half="bottom"):
    b.stair(x, y, z, material, facing, half)


def lantern(b, x, y, z, hanging=False):
    b.set(x, y, z, "minecraft:lantern", hanging=str(hanging).lower())


def gabled_house(name, size, wall_top, wall, floor, roof, foundation,
                 frame, feature, porch=True, second_floor=False):
    """Reusable house plan; material, room layout and roof proportions vary by archetype."""
    w, h, d = size
    b = Building(name, size, f"Original {name.replace('_', ' ')} with a distinct room plan and regional material palette.")
    b.fill(1, 0, 1, w - 2, 0, d - 2, foundation)
    b.fill(2, 1, 2, w - 3, 1, d - 3, floor)
    b.shell(2, 2, 2, w - 3, wall_top, d - 3, wall)
    for x in (2, w - 3):
        for z in (2, d - 3):
            for y in range(2, wall_top + 1):
                b.log(x, y, z, frame)
    center = w // 2
    door(b, center, 2, 2, "spruce" if frame == "spruce" else "oak")
    for z in (4, d - 4):
        pane(b, 2, min(3, wall_top - 1), z)
        pane(b, w - 3, min(3, wall_top - 1), z)
    for x in range(4, w - 4, 3):
        if x == center:
            continue
        pane(b, x, min(3, wall_top - 1), 2)
        pane(b, x, min(3, wall_top - 1), d - 3)
    if second_floor:
        b.fill(2, 6, 2, w - 3, 6, d - 3, floor)
        for x in (3, w - 4):
            for z in (3, d - 4):
                pane(b, x, 8, z)
        for x in range(center - 2, center + 3):
            b.fence(x, 8, 1, frame)
        for y in range(2, 6):
            stairs(b, 3, y, 5 + (y - 2), "spruce", "south")
        eave = wall_top + 1
    else:
        eave = wall_top + 1
    b.gable_roof(1, w - 2, 1, d - 2, eave, 3, roof)
    if porch:
        b.fill(center - 2, 1, 0, center + 2, 1, 1, floor)
        for x in (center - 2, center + 2):
            b.log(x, 2, 0, frame)
            b.log(x, 3, 0, frame)
            b.fence(x, 3, 1, frame)
        b.fill(center - 2, 4, 0, center + 2, 4, 0, f"minecraft:{roof}_slab",
               type="bottom", waterlogged="false")
    lantern(b, center - 2, 2, d // 2)
    lantern(b, center + 2, 2, d // 2)
    feature(b, center)
    return b


def feature_blacksmith(b, c):
    b.fill(3, 2, 5, 4, 2, 6, "minecraft:stone_bricks")
    b.set(3, 3, 5, "minecraft:campfire", facing="north", lit="true",
          signal_fire="false", waterlogged="false")
    b.set(4, 3, 5, "minecraft:iron_bars", east="false", north="false",
          south="false", waterlogged="false", west="false")
    b.set(c + 1, 2, 7, "minecraft:anvil", facing="north")
    b.set(c - 2, 2, 7, "minecraft:blast_furnace", facing="north", lit="false")


def feature_school(b, c):
    b.fill(4, 2, 6, 8, 2, 6, "minecraft:dark_oak_slab",
           type="bottom", waterlogged="false")
    b.fill(4, 3, 9, 8, 4, 9, "minecraft:black_concrete")
    for x in (4, 6, 8):
        b.set(x, 2, 4, "minecraft:oak_slab", type="bottom", waterlogged="false")
        b.set(x, 2, 5, "minecraft:oak_slab", type="bottom", waterlogged="false")
    lantern(b, c, 5, 6, True)


def feature_clinic(b, c):
    b.fill(3, 2, 6, 4, 2, 8, "minecraft:white_wool")
    b.fill(8, 2, 6, 9, 2, 8, "minecraft:lime_wool")
    b.set(6, 2, 7, "minecraft:brewing_stand")
    b.set(c, 2, 9, "minecraft:cauldron")


def feature_bakery(b, c):
    b.set(3, 2, 7, "minecraft:smoker", facing="north", lit="false")
    b.set(4, 2, 7, "minecraft:furnace", facing="north", lit="false")
    b.set(c + 2, 2, 8, "minecraft:hay_block", axis="y")
    b.set(c + 1, 2, 8, "minecraft:barrel", facing="up", open="false")


def feature_townhouse(b, c):
    b.set(3, 2, 7, "minecraft:crafting_table")
    b.set(4, 2, 7, "minecraft:bookshelf")
    b.set(c + 2, 7, 7, "minecraft:lantern", hanging="true")
    b.set(c - 2, 7, 7, "minecraft:lantern", hanging="true")


def feature_snow(b, c):
    b.set(4, 2, 7, "minecraft:campfire", facing="north", lit="true",
          signal_fire="false", waterlogged="false")
    b.set(c + 2, 2, 7, "minecraft:barrel", facing="up", open="false")
    b.fill(5, 2, 8, 7, 2, 8, "minecraft:carpet")


def feature_savanna(b, c):
    b.fill(3, 2, 7, 5, 2, 7, "minecraft:coarse_dirt")
    b.fence(c + 2, 2, 8, "acacia")
    b.set(c - 2, 2, 8, "minecraft:composter", level="0")


def build_woodland_cottage():
    return gabled_house("woodland_cottage", (13, 11, 13), 5,
                        "minecraft:oak_planks", "minecraft:oak_planks",
                        "spruce", "minecraft:cobblestone", "oak",
                        lambda b, c: (b.set(3, 2, 7, "minecraft:bookshelf"),
                                      b.set(c + 2, 2, 7, "minecraft:crafting_table")))


def build_village_blacksmith():
    b = gabled_house("village_blacksmith", (15, 12, 13), 6,
                     "minecraft:stone_bricks", "minecraft:spruce_planks",
                     "spruce", "minecraft:stone_bricks", "spruce",
                     feature_blacksmith)
    b.fill(10, 2, 4, 11, 5, 5, "minecraft:stone_bricks")
    b.set(10, 6, 4, "minecraft:campfire", facing="north", lit="true",
          signal_fire="false", waterlogged="false")
    return b


def build_schoolhouse():
    return gabled_house("schoolhouse", (17, 12, 13), 6,
                        "minecraft:oak_planks", "minecraft:birch_planks",
                        "dark_oak", "minecraft:stone_bricks", "oak",
                        feature_school)


def build_healers_clinic():
    return gabled_house("healers_clinic", (15, 11, 13), 5,
                        "minecraft:white_terracotta", "minecraft:birch_planks",
                        "birch", "minecraft:smooth_stone", "birch",
                        feature_clinic)


def build_brick_bakery():
    return gabled_house("brick_bakery", (15, 11, 13), 5,
                        "minecraft:bricks", "minecraft:oak_planks",
                        "spruce", "minecraft:stone_bricks", "oak",
                        feature_bakery)


def build_two_storey_townhouse():
    return gabled_house("two_storey_townhouse", (15, 15, 13), 9,
                        "minecraft:bricks", "minecraft:dark_oak_planks",
                        "dark_oak", "minecraft:stone_bricks", "dark_oak",
                        feature_townhouse, second_floor=True)


def build_snowfield_shelter():
    return gabled_house("snowfield_shelter", (13, 12, 13), 6,
                        "minecraft:spruce_planks", "minecraft:spruce_planks",
                        "spruce", "minecraft:stone_bricks", "spruce",
                        feature_snow)


def build_savanna_outpost():
    return gabled_house("savanna_outpost", (15, 11, 13), 5,
                        "minecraft:acacia_planks", "minecraft:acacia_planks",
                        "acacia", "minecraft:cut_sandstone", "acacia",
                        feature_savanna)


def build_marsh_stilt_house():
    b = Building("marsh_stilt_house", (15, 12, 15),
                 "Raised timber dwelling with stilts, a waterside deck and reed-like roof trim.")
    for x in (2, 12):
        for z in (2, 12):
            for y in range(0, 6):
                b.log(x, y, z, "oak")
    b.fill(2, 5, 2, 12, 5, 12, "minecraft:spruce_planks")
    b.shell(2, 6, 2, 12, 8, 12, "minecraft:oak_planks")
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        for y in range(6, 10):
            b.log(x, y, z, "oak")
    door(b, 7, 6, 2, "oak")
    for x in (4, 10):
        pane(b, x, 7, 2)
        pane(b, x, 7, 12)
    b.gable_roof(1, 13, 1, 13, 9, 2, "spruce")
    b.fill(5, 5, 0, 9, 5, 2, "minecraft:oak_planks")
    for x in (5, 9):
        b.log(x, 6, 0, "oak")
    b.set(7, 6, 6, "minecraft:barrel", facing="up", open="false")
    lantern(b, 7, 7, 8)
    return b


def build_mesa_trading_post():
    b = Building("mesa_trading_post", (17, 10, 15),
                 "Badlands trading post with terracotta rooms, red-sand yard and a shaded market awning.")
    b.fill(1, 0, 1, 15, 0, 13, "minecraft:red_sandstone")
    b.fill(2, 1, 2, 9, 1, 10, "minecraft:smooth_sandstone")
    b.shell(2, 2, 2, 9, 5, 10, "minecraft:orange_terracotta")
    for x in (2, 9):
        for z in (2, 10):
            for y in range(2, 7):
                b.log(x, y, z, "acacia")
    door(b, 5, 2, 2, "acacia")
    for x in (3, 8):
        pane(b, x, 3, 2)
        pane(b, x, 3, 10)
    b.fill(11, 1, 3, 14, 1, 10, "minecraft:red_sand")
    for x in (11, 14):
        for z in (3, 10):
            b.log(x, 2, z, "acacia")
            b.log(x, 3, z, "acacia")
    b.fill(11, 4, 3, 14, 4, 10, "minecraft:orange_carpet")
    b.set(12, 2, 6, "minecraft:barrel", facing="up", open="false")
    b.set(13, 2, 6, "minecraft:crafting_table")
    b.set(5, 2, 7, "minecraft:loom", facing="north")
    lantern(b, 12, 3, 6)
    return b


def build_market_hall():
    b = Building("market_hall", (21, 12, 17),
                 "Open civic market hall with timber arcade, separate stalls and colored awnings.")
    b.fill(1, 0, 1, 19, 0, 15, "minecraft:stone_bricks")
    for x in (2, 6, 10, 14, 18):
        for z in (2, 14):
            for y in range(1, 8):
                b.log(x, y, z, "dark_oak")
    for z in (2, 14):
        for x in range(2, 19):
            b.log(x, 8, z, "dark_oak", axis="x")
    for x in (2, 18):
        for z in range(2, 15):
            b.log(x, 8, z, "dark_oak", axis="z")
    b.gable_roof(1, 19, 1, 15, 8, 3, "dark_oak")
    for x, wool in ((4, "red"), (8, "yellow"), (12, "blue"), (16, "green")):
        # Counter, stocked barrels and a raised colored awning for each stall.
        b.fill(x - 1, 1, 6, x + 1, 1, 9, "minecraft:oak_planks")
        b.set(x, 2, 7, "minecraft:barrel", facing="up", open="false")
        b.set(x, 2, 8, "minecraft:crafting_table")
        for sx in (x - 1, x + 1):
            for sz in (5, 10):
                b.log(sx, 2, sz, "dark_oak")
                b.log(sx, 3, sz, "dark_oak")
        b.fill(x - 1, 4, 5, x + 1, 4, 10, f"minecraft:{wool}_wool")
    for x in (3, 9, 15):
        lantern(b, x, 7, 8, True)
    return b


def build_railway_station():
    b = Building("railway_station", (21, 11, 13),
                 "Regional rail stop with covered platform, ticket room and two-track arrival area.")
    b.fill(0, 0, 0, 20, 0, 12, "minecraft:stone_bricks")
    b.fill(1, 1, 2, 19, 1, 10, "minecraft:polished_andesite")
    b.fill(2, 1, 3, 18, 1, 9, "minecraft:gravel")
    for x in range(2, 19, 4):
        b.log(x, 2, 2, "spruce")
        b.log(x, 2, 10, "spruce")
        b.log(x, 6, 2, "spruce")
        b.log(x, 6, 10, "spruce")
    b.fill(2, 7, 2, 18, 7, 10, "minecraft:spruce_planks")
    b.fill(3, 8, 3, 17, 8, 9, "minecraft:dark_oak_slab",
           type="bottom", waterlogged="false")
    b.fill(1, 2, 0, 6, 5, 2, "minecraft:bricks")
    door(b, 3, 2, 2)
    pane(b, 2, 3, 2)
    pane(b, 5, 3, 2)
    for x in range(1, 20):
        b.set(x, 1, 5, "minecraft:rail", shape="east_west")
        b.set(x, 1, 7, "minecraft:rail", shape="east_west")
    for x in (4, 10, 16):
        lantern(b, x, 6, 6, True)
    return b


def build_windmill():
    b = Building("windmill", (15, 20, 15),
                 "Four-storey stone windmill with timber cap, external sails and grain-processing floor.")
    b.fill(2, 0, 2, 12, 0, 12, "minecraft:stone_bricks")
    for y in range(1, 13):
        inset = 2 if y < 8 else 3
        for x in range(inset, 15 - inset):
            for z in range(inset, 15 - inset):
                if x in (inset, 14 - inset) or z in (inset, 14 - inset):
                    b.set(x, y, z, "minecraft:cobblestone")
    for y in (4, 8, 11):
        pane(b, 7, y, 3)
    b.fill(4, 13, 4, 10, 13, 10, "minecraft:spruce_planks")
    b.gable_roof(3, 11, 3, 11, 14, 3, "spruce")
    # Four broad timber sails on the front face; the hub is decorative and static.
    b.log(7, 9, 1, "oak", axis="z")
    for x in range(4, 11):
        b.set(x, 9, 0, "minecraft:oak_planks")
    for y in range(3, 16):
        b.set(7, y, 0, "minecraft:oak_planks")
    for x in (5, 6, 8, 9):
        b.set(x, 9, 0, "minecraft:white_wool")
    for y in (5, 6, 12, 13):
        b.set(7, y, 0, "minecraft:white_wool")
    door(b, 7, 1, 3)
    b.set(5, 2, 7, "minecraft:barrel", facing="up", open="false")
    b.set(9, 2, 7, "minecraft:grindstone", face="wall", facing="north")
    return b


def build_fishing_hut():
    b = Building("fishing_hut", (15, 10, 15),
                 "Small shingled fisher cottage joined to a working wooden landing and storage deck.")
    b.fill(2, 0, 2, 10, 0, 10, "minecraft:cobblestone")
    b.fill(3, 1, 3, 9, 1, 9, "minecraft:spruce_planks")
    b.shell(3, 2, 3, 9, 5, 9, "minecraft:spruce_planks")
    for x in (3, 9):
        for z in (3, 9):
            for y in range(2, 7):
                b.log(x, y, z, "spruce")
    door(b, 6, 2, 3, "spruce")
    for x in (4, 8):
        pane(b, x, 3, 3)
        pane(b, x, 3, 9)
    b.gable_roof(2, 10, 2, 10, 6, 2, "spruce")
    b.fill(5, 1, 0, 9, 1, 4, "minecraft:oak_planks")
    for x in (5, 9):
        for z in range(0, 5, 2):
            b.log(x, 0, z, "oak")
            b.fence(x, 2, z, "oak")
    b.set(7, 2, 7, "minecraft:barrel", facing="up", open="false")
    b.set(8, 2, 7, "minecraft:campfire", facing="north", lit="false",
          signal_fire="false", waterlogged="false")
    lantern(b, 7, 3, 1)
    return b


def build_coastal_pier():
    b = Building("coastal_pier", (23, 8, 11),
                 "Long timber pier with regular piles, mooring posts and sheltered end platform.")
    for x in range(1, 22):
        for z in range(3, 8):
            b.fill(x, 2, z, x, 2, z, "minecraft:spruce_planks")
        if x % 3 == 1:
            for z in (2, 8):
                for y in range(0, 3):
                    b.log(x, y, z, "spruce")
                b.fence(x, 3, z, "spruce")
    for x in (2, 20):
        lantern(b, x, 4, 5, True)
    b.fill(18, 2, 1, 22, 2, 9, "minecraft:dark_oak_planks")
    for x in (18, 22):
        for z in (1, 9):
            b.log(x, 3, z, "dark_oak")
            b.log(x, 4, z, "dark_oak")
    return b


def build_forest_watchtower():
    b = Building("forest_watchtower", (11, 19, 11),
                 "Tall timber lookout tower with stone footings, a railed observation deck and lantern crown.")
    b.fill(2, 0, 2, 8, 1, 8, "minecraft:stone_bricks")
    for x in (3, 7):
        for z in (3, 7):
            for y in range(2, 14):
                b.log(x, y, z, "spruce")
    for y in (5, 9, 13):
        for x in range(3, 8):
            b.log(x, y, 3, "spruce", axis="x")
            b.log(x, y, 7, "spruce", axis="x")
        for z in range(3, 8):
            b.log(3, y, z, "spruce", axis="z")
            b.log(7, y, z, "spruce", axis="z")
    b.fill(2, 14, 2, 8, 14, 8, "minecraft:spruce_planks")
    for x in range(2, 9):
        b.fence(x, 15, 2, "spruce")
        b.fence(x, 15, 8, "spruce")
    for z in range(3, 8):
        b.fence(2, 15, z, "spruce")
        b.fence(8, 15, z, "spruce")
    b.gable_roof(2, 8, 2, 8, 16, 2, "spruce")
    for y in range(2, 14):
        b.set(5, y, 2, "minecraft:ladder", facing="north", waterlogged="false")
    lantern(b, 5, 17, 5, True)
    return b


def build_botanical_greenhouse():
    b = Building("botanical_greenhouse", (19, 11, 15),
                 "Large glasshouse with central aisle, raised beds and a glazed ridge roof.")
    b.fill(1, 0, 1, 17, 0, 13, "minecraft:stone_bricks")
    b.fill(2, 1, 2, 16, 1, 12, "minecraft:dirt")
    for x in (2, 16):
        for z in (2, 12):
            b.log(x, 2, z, "oak")
            b.log(x, 3, z, "oak")
            b.log(x, 4, z, "oak")
    for x in range(3, 16):
        for y in (2, 3, 4, 5):
            pane(b, x, y, 2)
            pane(b, x, y, 12)
    for z in range(3, 12):
        for y in (2, 3, 4, 5):
            pane(b, 2, y, z)
            pane(b, 16, y, z)
    b.gable_roof(2, 16, 2, 12, 6, 3, "oak")
    # Crop beds are separated by a clear walking aisle and water channels.
    for x in (4, 6, 12, 14):
        for z in range(4, 11):
            b.set(x, 1, z, "minecraft:farmland", moisture="7")
            b.set(x, 2, z, "minecraft:carrots", age="7")
    for z in range(4, 11):
        b.set(9, 1, z, "minecraft:water", level="0")
    lantern(b, 9, 5, 7, True)
    return b


def build_orchard_farm():
    b = Building("orchard_farm", (21, 10, 19),
                 "Mixed fruit orchard with irrigation furrows, storage shed and orderly tree rows.")
    b.fill(1, 0, 1, 19, 0, 17, "minecraft:grass_block", snowy="false")
    b.fill(2, 1, 2, 8, 1, 7, "minecraft:coarse_dirt")
    b.shell(2, 2, 2, 8, 4, 7, "minecraft:oak_planks")
    for x in (2, 8):
        for z in (2, 7):
            b.log(x, 2, z, "oak")
            b.log(x, 3, z, "oak")
            b.log(x, 4, z, "oak")
    door(b, 5, 2, 2)
    b.gable_roof(1, 9, 1, 8, 5, 2, "oak")
    for x in (12, 16):
        for z in (4, 8, 12, 16):
            b.log(x, 1, z, "oak")
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for dy in (2, 3):
                        b.set(x + dx, dy, z + dz, "minecraft:oak_leaves",
                              distance="7", persistent="true", waterlogged="false")
    for x in (10, 14, 18):
        for z in range(3, 17):
            if z % 4:
                b.set(x, 0, z, "minecraft:farmland", moisture="7")
            else:
                b.set(x, 0, z, "minecraft:water", level="0")
    b.set(6, 2, 5, "minecraft:composter", level="0")
    return b


def build_stone_bridge():
    b = Building("stone_bridge", (25, 10, 11),
                 "Broad masonry crossing with two stone arches, raised parapets and a paved deck.")
    b.fill(0, 3, 3, 24, 3, 7, "minecraft:stone_bricks")
    for x in range(0, 25):
        b.fill(x, 4, 3, x, 4, 3, "minecraft:stone_brick_slab",
               type="top", waterlogged="false")
        b.fill(x, 4, 7, x, 4, 7, "minecraft:stone_brick_slab",
               type="top", waterlogged="false")
    for x in range(0, 25, 2):
        b.fill(x, 4, 2, x, 5, 2, "minecraft:stone_bricks")
        b.fill(x, 4, 8, x, 5, 8, "minecraft:stone_bricks")
    for x in (5, 12, 19):
        b.fill(x - 1, 0, 3, x + 1, 3, 7, "minecraft:stone_bricks")
        b.fill(x - 2, 2, 3, x + 2, 2, 7, "minecraft:stone_brick_slab",
               type="bottom", waterlogged="false")
    for x in (2, 22):
        lantern(b, x, 6, 5)
    return b


def build_watermill():
    b = Building("watermill", (19, 14, 17),
                 "Riverside mill with a stone wheel housing, timber workshop and static paddle wheel.")
    b.fill(2, 0, 3, 14, 0, 13, "minecraft:stone_bricks")
    b.fill(3, 1, 4, 13, 1, 12, "minecraft:spruce_planks")
    b.shell(3, 2, 4, 13, 6, 12, "minecraft:cobblestone")
    for x in (3, 13):
        for z in (4, 12):
            for y in range(2, 8):
                b.log(x, y, z, "spruce")
    door(b, 8, 2, 4, "spruce")
    for x in (5, 11):
        pane(b, x, 4, 4)
        pane(b, x, 4, 12)
    b.gable_roof(2, 14, 3, 13, 7, 3, "spruce")
    # Wheel lies outside the east wall and uses a central axle with broad paddles.
    b.log(15, 5, 8, "oak", axis="x")
    for z in range(5, 12):
        b.set(16, 5, z, "minecraft:oak_planks")
    for y in range(2, 9):
        b.set(16, y, 8, "minecraft:oak_planks")
    for z in (4, 12):
        b.set(16, 5, z, "minecraft:oak_slab", type="bottom", waterlogged="false")
        b.set(16, 8, z, "minecraft:oak_slab", type="bottom", waterlogged="false")
    b.set(7, 2, 8, "minecraft:barrel", facing="up", open="false")
    b.set(10, 2, 8, "minecraft:crafting_table")
    return b


def build_desert_well():
    b = Building("desert_well", (9, 9, 9),
                 "Compact sandstone well with a covered timber winch and a stone water basin.")
    b.fill(1, 0, 1, 7, 0, 7, "minecraft:sandstone")
    b.fill(2, 1, 2, 6, 1, 6, "minecraft:sandstone")
    b.fill(3, 1, 3, 5, 1, 5, "minecraft:water", level="0")
    for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
        b.fill(x, 2, z, x, 4, z, "minecraft:cut_sandstone")
    b.fill(1, 5, 1, 7, 5, 7, "minecraft:spruce_planks")
    b.gable_roof(1, 7, 1, 7, 6, 2, "spruce")
    b.log(4, 3, 2, "oak", axis="x")
    b.fence(4, 4, 2, "oak")
    lantern(b, 4, 4, 4)
    return b


def build_mountain_observatory():
    b = Building("mountain_observatory", (17, 16, 17),
                 "High-altitude stone observatory with an open viewing ring and copper-colored dome.")
    b.fill(2, 0, 2, 14, 0, 14, "minecraft:stone_bricks")
    for y in range(1, 9):
        inset = 2 if y < 6 else 3
        for x in range(inset, 17 - inset):
            for z in range(inset, 17 - inset):
                if x in (inset, 16 - inset) or z in (inset, 16 - inset):
                    b.set(x, y, z, "minecraft:stone_bricks")
    for x, z in ((4, 4), (12, 4), (4, 12), (12, 12)):
        for y in range(2, 11):
            b.log(x, y, z, "spruce")
    for x in range(4, 13):
        for z in range(4, 13):
            if x in (4, 12) or z in (4, 12):
                b.set(x, 9, z, "minecraft:cut_copper")
                b.set(x, 10, z, "minecraft:glass_pane",
                      east="false", north="false", south="false",
                      waterlogged="false", west="false")
    b.fill(5, 9, 5, 11, 9, 11, "minecraft:smooth_stone")
    for x, z in ((5, 5), (11, 5), (5, 11), (11, 11)):
        b.set(x, 10, z, "minecraft:lightning_rod", facing="up", waterlogged="false")
    b.fill(5, 11, 5, 11, 11, 11, "minecraft:glass")
    b.fill(6, 12, 6, 10, 12, 10, "minecraft:cut_copper")
    b.fill(7, 13, 7, 9, 13, 9, "minecraft:cut_copper")
    b.set(8, 14, 8, "minecraft:lightning_rod", facing="up", waterlogged="false")
    door(b, 8, 2, 2, "spruce")
    lantern(b, 8, 8, 8, True)
    return b


def build_ruined_watchpost():
    b = Building("ruined_watchpost", (15, 10, 15),
                 "Weathered stone watchpost with deliberately broken walls, rubble and an open courtyard.")
    b.fill(1, 0, 1, 13, 0, 13, "minecraft:mossy_cobblestone")
    for x in range(2, 13):
        for y in range(1, 6):
            if not (x in (4, 8, 10) and y in (2, 4)):
                b.set(x, y, 2, "minecraft:mossy_cobblestone")
    for z in range(2, 13):
        for y in range(1, 5):
            if not (z in (5, 9) and y in (2, 3)):
                b.set(2, y, z, "minecraft:mossy_cobblestone")
    for x in (10, 11, 12):
        b.fill(x, 1, 10, x, 3 + (x % 2), 10, "minecraft:cracked_stone_bricks")
    b.fill(8, 1, 8, 9, 1, 9, "minecraft:gravel")
    b.fill(5, 1, 5, 6, 1, 6, "minecraft:coarse_dirt")
    b.set(7, 1, 7, "minecraft:campfire", facing="north", lit="false",
          signal_fire="false", waterlogged="false")
    b.set(3, 1, 3, "minecraft:stone_brick_stairs", facing="east",
          half="bottom", shape="straight", waterlogged="false")
    return b


def build_garden_pavilion():
    b = Building("garden_pavilion", (15, 9, 15),
                 "Open octagonal-inspired garden pavilion with four entrances, benches and a layered roof.")
    b.fill(2, 0, 2, 12, 0, 12, "minecraft:moss_block")
    b.fill(3, 1, 3, 11, 1, 11, "minecraft:polished_diorite")
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11),
                 (7, 3), (7, 11), (3, 7), (11, 7)):
        b.log(x, 2, z, "oak")
        b.log(x, 3, z, "oak")
        b.log(x, 4, z, "oak")
    b.fill(2, 5, 2, 12, 5, 12, "minecraft:oak_planks")
    b.gable_roof(1, 13, 1, 13, 6, 2, "oak")
    for x, z in ((5, 5), (9, 5), (5, 9), (9, 9)):
        b.fill(x - 1, 2, z, x + 1, 2, z, "minecraft:oak_slab",
               type="bottom", waterlogged="false")
    for x, z in ((7, 5), (7, 9), (5, 7), (9, 7)):
        lantern(b, x, 5, z, True)
    return b


def build_lumber_camp():
    b = Building("lumber_camp", (19, 10, 17),
                 "Forest-edge timber camp with bunkhouse, stacked logs, sawing station and covered store.")
    b.fill(1, 0, 1, 17, 0, 15, "minecraft:coarse_dirt")
    b.fill(2, 1, 2, 8, 1, 8, "minecraft:spruce_planks")
    b.shell(2, 2, 2, 8, 4, 8, "minecraft:spruce_planks")
    for x in (2, 8):
        for z in (2, 8):
            for y in range(2, 6):
                b.log(x, y, z, "spruce")
    door(b, 5, 2, 2, "spruce")
    b.gable_roof(1, 9, 1, 9, 5, 2, "spruce")
    for x in (11, 14, 17):
        for y in range(1, 4):
            for z in (3, 5, 7):
                b.log(x, y, z, "oak", axis="x")
    b.fill(10, 1, 10, 17, 1, 14, "minecraft:spruce_planks")
    for x in (10, 17):
        b.log(x, 2, 10, "spruce")
        b.log(x, 2, 14, "spruce")
    b.fill(10, 4, 10, 17, 4, 14, "minecraft:spruce_planks")
    b.set(12, 2, 12, "minecraft:crafting_table")
    b.set(14, 2, 12, "minecraft:stonecutter", facing="north")
    lantern(b, 5, 3, 6)
    return b


def merge_building(target, source, offset_x, offset_y=0, offset_z=0):
    """Compose an original building into a larger settlement without copying air."""
    for (x, y, z), block_state in source.blocks.items():
        if block_state[0] != "minecraft:air":
            target.set(offset_x + x, offset_y + y, offset_z + z, block_state)


def build_woodland_hamlet():
    b = Building("woodland_hamlet", (41, 15, 39),
                 "Three-building woodland hamlet with a cottage, healer clinic, schoolhouse, shared well and footpaths.")
    merge_building(b, build_woodland_cottage(), 2, 0, 2)
    merge_building(b, build_healers_clinic(), 26, 0, 2)
    merge_building(b, build_schoolhouse(), 12, 0, 22)
    # Shared paths join all three entrances without cutting through interiors.
    b.fill(8, 0, 15, 8, 0, 20, "minecraft:dirt_path")
    b.fill(33, 0, 15, 33, 0, 20, "minecraft:dirt_path")
    b.fill(20, 0, 15, 20, 0, 21, "minecraft:dirt_path")
    b.fill(8, 0, 17, 33, 0, 17, "minecraft:dirt_path")
    # A shared well and a small common garden make this a settlement, not a row of houses.
    b.fill(17, 0, 14, 23, 0, 20, "minecraft:coarse_dirt")
    b.fill(19, 1, 15, 21, 1, 17, "minecraft:stone_bricks")
    b.fill(20, 1, 16, 20, 1, 16, "minecraft:water", level="0")
    for x, z in ((18, 15), (22, 15), (18, 19), (22, 19)):
        b.log(x, 1, z, "oak")
    b.fill(18, 2, 15, 22, 2, 15, "minecraft:oak_slab",
           type="bottom", waterlogged="false")
    for x in (5, 36):
        for z in range(4, 14, 3):
            b.fence(x, 1, z, "oak")
    b.fill(3, 1, 17, 5, 1, 19, "minecraft:farmland", moisture="7")
    b.set(4, 2, 18, "minecraft:carrots", age="7")
    return b


def build_plains_market_square():
    b = Building("plains_market_square", (49, 15, 43),
                 "Compact civic square combining a roofed market hall, bakery, blacksmith, central well and paved pedestrian lanes.")
    merge_building(b, build_market_hall(), 14, 0, 2)
    merge_building(b, build_brick_bakery(), 2, 0, 26)
    merge_building(b, build_village_blacksmith(), 32, 0, 26)
    merge_building(b, build_desert_well(), 20, 0, 25)
    b.fill(18, 0, 19, 30, 0, 24, "minecraft:stone_bricks")
    b.fill(8, 0, 20, 40, 0, 20, "minecraft:dirt_path")
    b.fill(10, 0, 24, 10, 0, 26, "minecraft:dirt_path")
    b.fill(38, 0, 24, 38, 0, 26, "minecraft:dirt_path")
    # Benches and lantern posts define the shared civic space.
    for x in (18, 30):
        b.log(x, 1, 21, "oak")
        b.set(x, 2, 21, "minecraft:lantern", hanging="true")
    for x in (19, 29):
        b.set(x, 1, 23, "minecraft:oak_stairs", facing="north",
              half="bottom", shape="straight", waterlogged="false")
    return b


def build_river_trade_hamlet():
    b = Building("river_trade_hamlet", (51, 16, 35),
                 "Riverside working hamlet with a watermill, fishing cottage, masonry bridge and timber landing.")
    merge_building(b, build_watermill(), 2, 0, 2)
    merge_building(b, build_fishing_hut(), 30, 0, 2)
    # A shallow engineered channel makes the bridge and landing visually read
    # as riverside infrastructure even when the start biome is a floodplain edge.
    b.fill(0, 0, 23, 50, 0, 25, "minecraft:gravel")
    b.fill(0, 1, 23, 50, 1, 25, "minecraft:water", level="0")
    merge_building(b, build_stone_bridge(), 2, 0, 21)
    merge_building(b, build_coastal_pier(), 27, 0, 22)
    b.fill(20, 0, 17, 28, 0, 19, "minecraft:coarse_dirt")
    b.fill(24, 0, 16, 24, 0, 22, "minecraft:dirt_path")
    b.fill(24, 0, 19, 30, 0, 19, "minecraft:dirt_path")
    # Mooring and storage area beside the landing.
    for x in (29, 35, 41, 47):
        b.log(x, 1, 19, "spruce")
        b.set(x, 2, 19, "minecraft:lantern", hanging="true")
    return b


def build_alpine_watch_compound():
    b = Building("alpine_watch_compound", (41, 22, 41),
                 "Highland outpost combining a snow shelter, timber lookout tower and copper-domed mountain observatory.")
    merge_building(b, build_snowfield_shelter(), 2, 0, 2)
    merge_building(b, build_forest_watchtower(), 28, 0, 2)
    merge_building(b, build_mountain_observatory(), 12, 0, 22)
    b.fill(12, 0, 15, 28, 0, 18, "minecraft:gravel")
    b.fill(20, 0, 18, 20, 0, 22, "minecraft:gravel")
    for x, z in ((10, 18), (30, 18), (10, 21), (30, 21)):
        b.set(x, 1, z, "minecraft:campfire", facing="north", lit="false",
              signal_fire="false", waterlogged="false")
    return b


def build_desert_oasis_hamlet():
    b = Building("desert_oasis_hamlet", (45, 15, 39),
                 "Arid settlement with a terracotta trading post, shaded outpost, central well and irrigated date-grove courtyard.")
    merge_building(b, build_mesa_trading_post(), 2, 0, 2)
    merge_building(b, build_desert_well(), 19, 0, 5)
    merge_building(b, build_savanna_outpost(), 28, 0, 2)
    b.fill(16, 0, 21, 29, 0, 34, "minecraft:sand")
    b.fill(20, 0, 22, 20, 0, 33, "minecraft:water", level="0")
    for x in (17, 23, 27):
        for z in (24, 30):
            b.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, "minecraft:coarse_dirt")
            b.log(x, 1, z, "jungle")
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    b.set(x + dx, 2, z + dz, "minecraft:jungle_leaves",
                          distance="7", persistent="true", waterlogged="false")
    b.fill(18, 0, 18, 27, 0, 18, "minecraft:cut_sandstone")
    b.fill(22, 0, 18, 22, 0, 22, "minecraft:cut_sandstone")
    return b


def build_stone_mason_workshop():
    b = Building("stone_mason_workshop", (21, 12, 17),
                 "Stoneworking yard with an enclosed mason workshop, open cutting bay, block stacks and tool benches.")
    b.fill(1, 0, 1, 19, 0, 15, "minecraft:cobblestone")
    b.fill(2, 1, 2, 10, 1, 13, "minecraft:stone_bricks")
    b.shell(2, 2, 2, 10, 5, 13, "minecraft:stone_bricks")
    for x in (2, 10):
        for z in (2, 13):
            for y in range(2, 7):
                b.log(x, y, z, "spruce")
    door(b, 6, 2, 2, "spruce")
    b.gable_roof(1, 11, 1, 14, 6, 3, "spruce")
    # Open-sided cutting bay and sheltered stone stock.
    for x in (12, 19):
        for z in (3, 13):
            for y in range(1, 7):
                b.log(x, y, z, "spruce")
    b.gable_roof(11, 20, 2, 14, 6, 2, "dark_oak")
    b.set(14, 2, 7, "minecraft:stonecutter", facing="north")
    b.set(16, 2, 7, "minecraft:grindstone", face="wall", facing="north")
    b.set(18, 2, 7, "minecraft:anvil", facing="north")
    b.fill(13, 1, 10, 14, 2, 11, "minecraft:stone_bricks")
    b.fill(16, 1, 10, 18, 3, 11, "minecraft:polished_andesite")
    lantern(b, 6, 4, 8, True)
    return b


def build_canal_lock():
    b = Building("canal_lock", (33, 12, 17),
                 "Engineered canal lock with stone chamber walls, timber gates, towpaths and a small lock-keeper cabin.")
    b.fill(0, 0, 0, 32, 0, 16, "minecraft:gravel")
    b.fill(0, 1, 5, 32, 1, 11, "minecraft:water", level="0")
    b.fill(0, 1, 4, 32, 3, 4, "minecraft:stone_bricks")
    b.fill(0, 1, 12, 32, 3, 12, "minecraft:stone_bricks")
    b.fill(9, 1, 5, 9, 3, 11, "minecraft:stone_bricks")
    b.fill(23, 1, 5, 23, 3, 11, "minecraft:stone_bricks")
    for x in (9, 23):
        for z in range(5, 12):
            b.fence(x, 2, z, "spruce")
        b.log(x, 4, 5, "spruce")
        b.log(x, 4, 11, "spruce")
        b.log(x, 5, 5, "spruce")
        b.log(x, 5, 11, "spruce")
    # Lock-keeper cabin sits on the dry towpath.
    b.fill(2, 0, 1, 8, 0, 3, "minecraft:stone_bricks")
    b.shell(2, 1, 1, 8, 4, 3, "minecraft:oak_planks")
    for x in (2, 8):
        for z in (1, 3):
            b.log(x, 1, z, "oak")
            b.log(x, 2, z, "oak")
            b.log(x, 3, z, "oak")
            b.log(x, 4, z, "oak")
    door(b, 5, 1, 1)
    b.gable_roof(1, 9, 0, 4, 5, 2, "spruce")
    b.fill(1, 0, 13, 31, 0, 15, "minecraft:dirt_path")
    lantern(b, 7, 4, 2, True)
    lantern(b, 25, 4, 13)
    return b


def build_monastery_chapel():
    b = Building("monastery_chapel", (23, 15, 25),
                 "Quiet stone chapel with a bell tower, enclosed cloister garden, arcaded walk and central courtyard.")
    b.fill(1, 0, 1, 21, 0, 23, "minecraft:stone_bricks")
    # Chapel nave.
    b.fill(5, 1, 7, 17, 1, 21, "minecraft:polished_andesite")
    b.shell(5, 2, 7, 17, 7, 21, "minecraft:stone_bricks")
    for x in (5, 17):
        for z in (7, 21):
            for y in range(2, 9):
                b.log(x, y, z, "spruce")
    door(b, 11, 2, 7, "spruce")
    b.gable_roof(4, 18, 6, 22, 8, 3, "dark_oak")
    for x in (7, 15):
        for z in (7, 14, 20):
            pane(b, x, 4, z)
    # Bell tower and cross-shaped roof finial.
    b.fill(3, 1, 3, 7, 1, 7, "minecraft:stone_bricks")
    b.shell(3, 2, 3, 7, 10, 7, "minecraft:stone_bricks")
    for y in range(2, 12):
        b.log(3, y, 3, "spruce")
        b.log(7, y, 3, "spruce")
        b.log(3, y, 7, "spruce")
        b.log(7, y, 7, "spruce")
    b.gable_roof(2, 8, 2, 8, 11, 2, "spruce")
    b.log(5, 13, 5, "oak")
    b.log(5, 14, 5, "oak")
    # Cloister walls and planted quadrangle.
    for x in range(2, 21):
        b.fill(x, 2, 2, x, 3, 2, "minecraft:stone_bricks")
        b.fill(x, 2, 22, x, 3, 22, "minecraft:stone_bricks")
    for z in range(2, 23):
        b.fill(2, 2, z, 2, 3, z, "minecraft:stone_bricks")
        b.fill(20, 2, z, 20, 3, z, "minecraft:stone_bricks")
    b.fill(8, 0, 3, 15, 0, 5, "minecraft:moss_block")
    for x, z in ((9, 4), (14, 4), (11, 3), (12, 5)):
        b.set(x, 1, z, "minecraft:flowering_azalea")
    lantern(b, 11, 4, 16, True)
    return b


def build_apiary_garden():
    b = Building("apiary_garden", (25, 12, 25),
                 "Small beekeeper's cottage and fenced apiary with timber hive stands, flowering beds and a honey workbench.")
    b.fill(1, 0, 1, 23, 0, 23, "minecraft:coarse_dirt")
    b.fill(2, 0, 2, 9, 0, 9, "minecraft:stone_bricks")
    b.shell(2, 1, 2, 9, 4, 9, "minecraft:oak_planks")
    for x in (2, 9):
        for z in (2, 9):
            for y in range(1, 6):
                b.log(x, y, z, "oak")
    door(b, 5, 1, 2)
    b.gable_roof(1, 10, 1, 10, 5, 2, "spruce")
    b.set(4, 2, 6, "minecraft:barrel", facing="up", open="false")
    b.set(6, 2, 6, "minecraft:crafting_table")
    # Three orderly hive rows with clear access between them.
    for x in (13, 17, 21):
        for z in (5, 11, 17):
            b.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, "minecraft:oak_planks")
            b.log(x, 2, z, "oak")
            b.set(x, 3, z, "minecraft:beehive", facing="south", honey_level="0")
            lantern(b, x, 2, z + 1)
    # Flower beds border the apiary without blocking the working lanes.
    for x in range(12, 23):
        for z in (3, 20):
            b.set(x, 1, z, "minecraft:oxeye_daisy" if x % 2 else "minecraft:cornflower")
    for z in range(4, 21):
        for x in (11, 23):
            b.fence(x, 1, z, "oak")
    b.fill(11, 0, 21, 23, 0, 23, "minecraft:grass_block", snowy="false")
    return b


def build_snowbound_storehouse():
    b = Building("snowbound_storehouse", (17, 11, 15),
                 "Compact alpine provisions store with deep stone footing, spruce frame, loading porch and snow-covered eaves.")
    b.fill(1, 0, 1, 15, 0, 13, "minecraft:stone_bricks")
    b.fill(2, 1, 2, 14, 1, 12, "minecraft:spruce_planks")
    b.shell(2, 2, 2, 14, 5, 12, "minecraft:spruce_planks")
    for x in (2, 14):
        for z in (2, 12):
            for y in range(2, 7):
                b.log(x, y, z, "spruce")
    door(b, 8, 2, 2, "spruce")
    b.gable_roof(1, 15, 1, 13, 6, 3, "spruce")
    for x in (4, 6, 10, 12):
        b.set(x, 7, 4, "minecraft:snow")
    b.set(5, 2, 7, "minecraft:barrel", facing="up", open="false")
    b.set(10, 2, 7, "minecraft:barrel", facing="up", open="false")
    b.fill(6, 1, 0, 10, 1, 1, "minecraft:spruce_planks")
    for x in (6, 10):
        b.log(x, 2, 0, "spruce")
        b.log(x, 3, 0, "spruce")
    lantern(b, 8, 3, 5, True)
    return b


def build_desert_cistern():
    b = Building("desert_cistern", (19, 13, 19),
                 "Sunken sandstone cistern with covered draw well, water reservoir and shaded rest alcove.")
    b.fill(1, 0, 1, 17, 0, 17, "minecraft:sandstone")
    b.fill(4, 0, 4, 14, 0, 14, "minecraft:water", level="0")
    for y in (1, 2, 3):
        b.fill(3, y, 3, 15, y, 3, "minecraft:cut_sandstone")
        b.fill(3, y, 15, 15, y, 15, "minecraft:cut_sandstone")
        b.fill(3, y, 3, 3, y, 15, "minecraft:cut_sandstone")
        b.fill(15, y, 3, 15, y, 15, "minecraft:cut_sandstone")
    b.fill(7, 1, 7, 11, 1, 11, "minecraft:sandstone")
    b.fill(8, 2, 8, 10, 2, 10, "minecraft:water", level="0")
    for x, z in ((6, 6), (12, 6), (6, 12), (12, 12)):
        b.log(x, 1, z, "acacia")
        b.log(x, 5, z, "acacia")
    b.fill(6, 5, 6, 12, 5, 12, "minecraft:cut_sandstone")
    b.fill(8, 6, 8, 10, 6, 10, "minecraft:air")
    b.set(9, 1, 9, "minecraft:water", level="0")
    b.fill(1, 0, 16, 17, 0, 17, "minecraft:sand")
    lantern(b, 9, 5, 9, True)
    return b


def build_tropical_boardwalk_house():
    b = Building("tropical_boardwalk_house", (19, 15, 19),
                 "Raised jungle timber home with open undercroft, steep roof, fenced veranda and boardwalk over wet ground.")
    b.fill(1, 0, 1, 17, 0, 17, "minecraft:mud")
    for x in (3, 15):
        for z in (3, 15):
            for y in range(1, 6):
                b.log(x, y, z, "jungle")
    b.fill(2, 5, 2, 16, 5, 16, "minecraft:jungle_planks")
    b.shell(2, 6, 2, 16, 9, 16, "minecraft:jungle_planks")
    for x in (2, 16):
        for z in (2, 16):
            for y in range(6, 11):
                b.log(x, y, z, "jungle")
    door(b, 9, 6, 2, "jungle")
    b.gable_roof(1, 17, 1, 17, 10, 4, "jungle")
    for x in range(5, 14, 3):
        pane(b, x, 8, 2)
        pane(b, x, 8, 16)
    b.fill(7, 4, 0, 11, 4, 2, "minecraft:jungle_planks")
    for x in (7, 11):
        b.fence(x, 5, 0, "jungle")
    b.fill(8, 1, 15, 10, 1, 18, "minecraft:jungle_planks")
    for x in (8, 10):
        b.log(x, 0, 16, "jungle")
        b.log(x, 0, 18, "jungle")
    return b


def build_railway_signal_box():
    b = Building("railway_signal_box", (15, 14, 13),
                 "Elevated railway signal cabin with timber frame, exterior ladder, semaphore-style mast and trackside lamps.")
    b.fill(1, 0, 1, 13, 0, 11, "minecraft:gravel")
    for x in (3, 11):
        for z in (3, 9):
            for y in range(1, 6):
                b.log(x, y, z, "spruce")
    b.fill(2, 5, 2, 12, 5, 10, "minecraft:spruce_planks")
    b.shell(2, 6, 2, 12, 9, 10, "minecraft:oak_planks")
    door(b, 7, 6, 2, "spruce")
    b.gable_roof(1, 13, 1, 11, 10, 3, "dark_oak")
    for x in (4, 10):
        pane(b, x, 8, 2)
        pane(b, x, 8, 10)
    for y in range(1, 6):
        b.stair(4, y, 1, "spruce", "north")
    # Signal mast and two static signal lamps.
    for y in range(1, 12):
        b.log(12, y, 11, "dark_oak")
    b.set(11, 10, 11, "minecraft:redstone_lamp")
    b.set(13, 8, 11, "minecraft:redstone_lamp")
    b.set(7, 6, 7, "minecraft:cartography_table")
    lantern(b, 7, 8, 7, True)
    return b


def build_quarry_shed():
    b = Building("quarry_shed", (21, 11, 17),
                 "Rugged quarry service shed with covered tool bay, stone sorting bins and stacked cut-stone blocks.")
    b.fill(1, 0, 1, 19, 0, 15, "minecraft:gravel")
    b.fill(2, 1, 2, 10, 1, 12, "minecraft:cobblestone")
    b.shell(2, 2, 2, 10, 5, 12, "minecraft:oak_planks")
    for x in (2, 10):
        for z in (2, 12):
            for y in range(2, 7):
                b.log(x, y, z, "spruce")
    door(b, 6, 2, 2)
    b.gable_roof(1, 11, 1, 13, 6, 2, "spruce")
    for x in (13, 18):
        for z in (3, 12):
            b.log(x, 1, z, "spruce")
            b.log(x, 5, z, "spruce")
    b.gable_roof(12, 19, 2, 13, 6, 2, "dark_oak")
    b.fill(13, 1, 5, 15, 3, 7, "minecraft:stone_bricks")
    b.fill(16, 1, 9, 18, 2, 11, "minecraft:polished_andesite")
    b.set(14, 2, 9, "minecraft:stonecutter", facing="north")
    b.set(17, 2, 5, "minecraft:grindstone", face="wall", facing="north")
    lantern(b, 6, 4, 8, True)
    return b


def build_charcoal_kiln():
    b = Building("charcoal_kiln", (17, 10, 17),
                 "Remote charcoal-burning station with stone kiln mound, covered fuel stacks and a small operator shelter.")
    b.fill(1, 0, 1, 15, 0, 15, "minecraft:coarse_dirt")
    b.fill(3, 1, 3, 11, 1, 11, "minecraft:stone_bricks")
    b.fill(4, 2, 4, 10, 4, 10, "minecraft:stone_bricks")
    b.fill(5, 5, 5, 9, 6, 9, "minecraft:stone_bricks")
    b.fill(6, 7, 6, 8, 7, 8, "minecraft:stone_bricks")
    b.set(7, 2, 3, "minecraft:campfire", facing="north", lit="false",
          signal_fire="false", waterlogged="false")
    b.fill(2, 1, 12, 12, 1, 14, "minecraft:spruce_planks")
    for x in (3, 11):
        for z in (12, 14):
            for y in range(2, 5):
                b.log(x, y, z, "spruce")
    b.gable_roof(2, 12, 11, 15, 5, 2, "spruce")
    for x in (13, 14):
        for z in (3, 5, 7):
            b.log(x, 1, z, "oak", axis="x")
            b.log(x, 2, z, "oak", axis="x")
    lantern(b, 7, 3, 13)
    return b


def build_vineyard_estate():
    b = Building("vineyard_estate", (43, 14, 29),
                 "Vineyard estate with a timber farmhouse, terraced trellis rows, a press yard and a small storage court.")
    merge_building(b, build_woodland_cottage(), 2, 0, 2)
    b.fill(17, 0, 2, 41, 0, 26, "minecraft:coarse_dirt")
    # Repeated trellis rows are deliberately separated by walking lanes.
    for z in (5, 10, 15, 20, 25):
        for x in range(18, 41, 4):
            b.fence(x, 1, z, "oak")
            b.fence(x, 2, z, "oak")
            b.set(x, 3, z, "minecraft:oak_leaves", distance="7",
                  persistent="true", waterlogged="false")
        for x in range(19, 40):
            b.set(x, 1, z + 1, "minecraft:sweet_berry_bush", age="3")
    b.fill(17, 0, 2, 17, 0, 26, "minecraft:dirt_path")
    b.fill(29, 0, 3, 39, 0, 8, "minecraft:stone_bricks")
    b.set(32, 1, 5, "minecraft:barrel", facing="up", open="false")
    b.set(35, 1, 5, "minecraft:barrel", facing="up", open="false")
    b.set(37, 1, 5, "minecraft:crafting_table")
    return b


def build_sheepfold_ranch():
    b = Building("sheepfold_ranch", (41, 13, 29),
                 "Working sheep ranch with farmhouse and barn, fenced grazing paddocks, hay stores and a long water trough.")
    merge_building(b, build_plains_farmstead(), 2, 0, 2)
    for x in range(22, 39):
        b.fence(x, 1, 3, "oak")
        b.fence(x, 1, 24, "oak")
    for z in range(3, 25):
        b.fence(22, 1, z, "oak")
        b.fence(38, 1, z, "oak")
    b.fill(25, 0, 8, 35, 0, 9, "minecraft:water", level="0")
    b.fill(26, 1, 14, 29, 1, 17, "minecraft:hay_block", axis="y")
    b.fill(32, 1, 14, 35, 1, 17, "minecraft:hay_block", axis="y")
    b.fill(24, 0, 21, 36, 0, 22, "minecraft:dirt_path")
    return b


def build_charcoal_burners_camp():
    b = Building("charcoal_burners_camp", (39, 13, 27),
                 "Forest charcoal camp with a timber bunkhouse, two stone kilns, stacked fuelwood and a covered work area.")
    merge_building(b, build_lumber_camp(), 2, 0, 2)
    for cx, cz in ((27, 7), (33, 7)):
        b.fill(cx - 3, 0, cz - 3, cx + 3, 0, cz + 3, "minecraft:coarse_dirt")
        b.fill(cx - 2, 1, cz - 2, cx + 2, 1, cz + 2, "minecraft:stone_bricks")
        b.fill(cx - 2, 2, cz - 2, cx + 2, 4, cz + 2, "minecraft:stone_bricks")
        b.fill(cx - 1, 5, cz - 1, cx + 1, 6, cz + 1, "minecraft:stone_bricks")
        b.set(cx, 2, cz - 2, "minecraft:campfire", facing="north", lit="false",
              signal_fire="false", waterlogged="false")
    for x in range(25, 36, 2):
        b.log(x, 1, 17, "spruce", axis="x")
        b.log(x, 2, 17, "spruce", axis="x")
    b.fill(24, 0, 20, 36, 0, 23, "minecraft:gravel")
    return b


def build_fishing_village_compound():
    b = Building("fishing_village_compound", (49, 14, 27),
                 "Coastal working hamlet combining a fisher cottage, long timber pier, smokehouse, net-drying racks and a shared quay.")
    merge_building(b, build_fishing_hut(), 2, 0, 2)
    merge_building(b, build_coastal_pier(), 24, 0, 12)
    b.fill(29, 0, 2, 43, 0, 10, "minecraft:cobblestone")
    b.shell(30, 1, 3, 42, 5, 9, "minecraft:spruce_planks")
    for x in (30, 42):
        for z in (3, 9):
            for y in range(1, 7):
                b.log(x, y, z, "spruce")
    door(b, 36, 1, 3, "spruce")
    b.gable_roof(29, 43, 2, 10, 6, 3, "spruce")
    b.set(33, 2, 6, "minecraft:smoker", facing="north", lit="false")
    b.set(36, 2, 6, "minecraft:barrel", facing="up", open="false")
    b.set(39, 2, 6, "minecraft:barrel", facing="up", open="false")
    for x in (30, 34, 38, 42):
        for y in (1, 3):
            b.log(x, y, 11, "oak")
        b.fence(x, 2, 11, "oak")
    return b


def build_hilltop_manor():
    b = Building("hilltop_manor", (41, 20, 35),
                 "Defensible hilltop manor with a two-storey residence, corner lookout, enclosed garden and stone approach court.")
    merge_building(b, build_two_storey_townhouse(), 2, 0, 2)
    merge_building(b, build_forest_watchtower(), 27, 0, 2)
    for x in range(1, 40):
        b.fill(x, 0, 1, x, 2, 1, "minecraft:stone_bricks")
        b.fill(x, 0, 33, x, 2, 33, "minecraft:stone_bricks")
    for z in range(1, 34):
        b.fill(1, 0, z, 1, 2, z, "minecraft:stone_bricks")
        b.fill(39, 0, z, 39, 2, z, "minecraft:stone_bricks")
    b.fill(15, 0, 16, 25, 0, 25, "minecraft:grass_block", snowy="false")
    for x, z in ((17, 18), (23, 18), (17, 23), (23, 23)):
        b.set(x, 1, z, "minecraft:flowering_azalea")
    b.fill(19, 0, 1, 21, 0, 8, "minecraft:stone_bricks")
    return b


def build_frontier_fort():
    b = Building("frontier_fort", (41, 16, 35),
                 "Timber frontier fort with a palisade perimeter, four raised corner platforms, barracks and a central supply yard.")
    b.fill(1, 0, 1, 39, 0, 33, "minecraft:coarse_dirt")
    for x in range(2, 39):
        for y in range(1, 7):
            b.log(x, y, 2, "spruce")
            b.log(x, y, 32, "spruce")
    for z in range(2, 33):
        for y in range(1, 7):
            b.log(2, y, z, "spruce")
            b.log(38, y, z, "spruce")
    # Four corner platforms with simple railed decks.
    for cx, cz in ((4, 4), (36, 4), (4, 30), (36, 30)):
        b.fill(cx - 2, 6, cz - 2, cx + 2, 6, cz + 2, "minecraft:spruce_planks")
        for x in range(cx - 2, cx + 3):
            b.fence(x, 7, cz - 2, "spruce")
            b.fence(x, 7, cz + 2, "spruce")
        for z in range(cz - 2, cz + 3):
            b.fence(cx - 2, 7, z, "spruce")
            b.fence(cx + 2, 7, z, "spruce")
    # Gate opening and barracks.
    for y in range(1, 5):
        b.set(20, y, 2, "minecraft:air")
        b.set(21, y, 2, "minecraft:air")
    b.fill(12, 0, 9, 28, 0, 15, "minecraft:stone_bricks")
    b.shell(12, 1, 9, 28, 5, 15, "minecraft:spruce_planks")
    for x in (12, 28):
        for z in (9, 15):
            for y in range(1, 7):
                b.log(x, y, z, "spruce")
    door(b, 20, 1, 9, "spruce")
    b.gable_roof(11, 29, 8, 16, 6, 3, "spruce")
    b.fill(17, 0, 20, 23, 0, 26, "minecraft:gravel")
    b.set(20, 1, 22, "minecraft:campfire", facing="north", lit="false",
          signal_fire="false", waterlogged="false")
    return b


def build_railway_depot():
    b = Building("railway_depot", (49, 16, 29),
                 "Regional rail depot combining a passenger station, signal cabin, freight warehouse and two gravel service tracks.")
    merge_building(b, build_railway_station(), 2, 0, 2)
    merge_building(b, build_railway_signal_box(), 30, 0, 2)
    b.fill(30, 0, 16, 46, 0, 27, "minecraft:gravel")
    b.shell(31, 1, 17, 45, 6, 26, "minecraft:bricks")
    for x in (31, 45):
        for z in (17, 26):
            for y in range(1, 8):
                b.log(x, y, z, "dark_oak")
    b.gable_roof(30, 46, 16, 27, 7, 3, "dark_oak")
    for x in (35, 41):
        b.set(x, 2, 17, "minecraft:barrel", facing="up", open="false")
        b.set(x, 2, 25, "minecraft:barrel", facing="up", open="false")
    b.fill(1, 0, 14, 47, 0, 15, "minecraft:gravel")
    b.fill(1, 0, 27, 47, 0, 28, "minecraft:gravel")
    return b


def build_orchard_cider_estate():
    b = Building("orchard_cider_estate", (47, 14, 29),
                 "Fruit-growing estate combining an orchard farmhouse, bakery-style press house, barrel store and orderly tree rows.")
    merge_building(b, build_orchard_farm(), 2, 0, 2)
    merge_building(b, build_brick_bakery(), 27, 0, 2)
    b.fill(28, 0, 17, 44, 0, 26, "minecraft:coarse_dirt")
    for x in (31, 35, 39, 43):
        for z in (18, 22, 26):
            b.log(x, 1, z, "oak")
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    b.set(x + dx, 2, z + dz, "minecraft:oak_leaves",
                          distance="7", persistent="true", waterlogged="false")
    for x, z in ((29, 19), (29, 23), (29, 26), (44, 19), (44, 23)):
        b.set(x, 1, z, "minecraft:barrel", facing="up", open="false")
    b.fill(26, 0, 15, 45, 0, 16, "minecraft:dirt_path")
    return b


def build_riverside_sawmill():
    b = Building("riverside_sawmill", (45, 15, 27),
                 "Working riverside sawmill combining a water-powered mill, timber bunkhouse, log sorting yard and loading channel.")
    merge_building(b, build_watermill(), 2, 0, 2)
    merge_building(b, build_lumber_camp(), 24, 0, 2)
    b.fill(0, 0, 21, 44, 0, 24, "minecraft:gravel")
    b.fill(0, 1, 21, 44, 1, 24, "minecraft:water", level="0")
    for x in range(27, 43, 3):
        b.log(x, 2, 19, "spruce", axis="x")
        b.log(x, 3, 19, "spruce", axis="x")
    b.fill(25, 0, 18, 43, 0, 20, "minecraft:coarse_dirt")
    return b


def build_marsh_reed_hamlet():
    b = Building("marsh_reed_hamlet", (43, 16, 31),
                 "Wetland hamlet with two raised timber homes, shared boardwalks, landing platforms and reed-edge gardens.")
    merge_building(b, build_marsh_stilt_house(), 2, 0, 2)
    merge_building(b, build_marsh_stilt_house(), 25, 0, 2)
    b.fill(7, 0, 16, 35, 0, 20, "minecraft:mud")
    b.fill(7, 1, 17, 35, 1, 19, "minecraft:oak_planks")
    for x in range(7, 36, 4):
        b.log(x, 0, 18, "oak")
        b.log(x, 1, 18, "oak")
        b.fence(x, 2, 17, "oak")
        b.fence(x, 2, 19, "oak")
    b.fill(3, 0, 22, 39, 0, 23, "minecraft:coarse_dirt")
    for x in (5, 10, 15, 28, 33, 38):
        b.set(x, 1, 25, "minecraft:flowering_azalea")
    return b


BUILDINGS = (
    build_woodland_cottage,
    build_village_blacksmith,
    build_schoolhouse,
    build_healers_clinic,
    build_brick_bakery,
    build_two_storey_townhouse,
    build_snowfield_shelter,
    build_savanna_outpost,
    build_marsh_stilt_house,
    build_mesa_trading_post,
    build_market_hall,
    build_railway_station,
    build_windmill,
    build_fishing_hut,
    build_coastal_pier,
    build_forest_watchtower,
    build_botanical_greenhouse,
    build_orchard_farm,
    build_stone_bridge,
    build_watermill,
    build_desert_well,
    build_mountain_observatory,
    build_ruined_watchpost,
    build_garden_pavilion,
    build_lumber_camp,
    build_woodland_hamlet,
    build_plains_market_square,
    build_river_trade_hamlet,
    build_alpine_watch_compound,
    build_desert_oasis_hamlet,
    build_stone_mason_workshop,
    build_canal_lock,
    build_monastery_chapel,
    build_apiary_garden,
    build_snowbound_storehouse,
    build_desert_cistern,
    build_tropical_boardwalk_house,
    build_railway_signal_box,
    build_quarry_shed,
    build_charcoal_kiln,
    build_vineyard_estate,
    build_sheepfold_ranch,
    build_charcoal_burners_camp,
    build_fishing_village_compound,
    build_hilltop_manor,
    build_frontier_fort,
    build_railway_depot,
    build_orchard_cider_estate,
    build_riverside_sawmill,
    build_marsh_reed_hamlet,
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
        print("BATCH_ARCHITECTURE_TEMPLATE", result)
    manifest_path = args.output / "batch-architecture-manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"ORIGINAL_ARCHITECTURE_BATCH_OK: {len(manifest)} new structures")


if __name__ == "__main__":
    main()
