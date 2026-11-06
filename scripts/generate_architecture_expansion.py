#!/usr/bin/env python3
"""Generate 42 original RealScale architecture designs for the 100-template library.

All expansion structures are authored from deterministic block-layout rules.
They use vanilla 1.21.1 block states and the repository's NBT writer; no external
schematics, runtime downloads, or third-party assets are used.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from generate_original_architecture import Building


EXPANSION_DESIGNS = (
    dict(name="forest_logging_camp", size=(27, 15, 23), kind="camp",
         wall="minecraft:spruce_planks", floor="minecraft:stripped_spruce_log",
         roof="spruce", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:campfire", accent="minecraft:barrel",
         path="minecraft:coarse_dirt", wall_height=5,
         description="A timber-cutting camp with a bunkhouse, log racks, tool yard and a sheltered fire circle."),
    dict(name="birch_sap_harvest", size=(25, 15, 23), kind="farm",
         wall="minecraft:birch_planks", floor="minecraft:birch_planks",
         roof="birch", foundation="minecraft:stone_bricks", frame="birch",
         feature="minecraft:cauldron", accent="minecraft:barrel",
         path="minecraft:dirt_path", wall_height=5,
         description="A birch-grove sap and syrup yard with a small processing house, collection barrels and fenced tree rows."),
    dict(name="taiga_trapper_station", size=(27, 16, 25), kind="compound",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:smoker", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=5,
         description="A remote taiga trapping station with a main cabin, supply lean-to, smokehouse and snow-season storage."),
    dict(name="taiga_sled_depot", size=(29, 17, 23), kind="industrial",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="spruce",
         feature="minecraft:rail", accent="minecraft:hay_block",
         path="minecraft:gravel", wall_height=6,
         description="A timber sled depot with a long repair shed, loading apron, raised track guides and stacked winter supplies."),
    dict(name="glacial_fishing_station", size=(27, 15, 23), kind="waterfront",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:packed_ice", frame="spruce",
         feature="minecraft:barrel", accent="minecraft:packed_ice",
         path="minecraft:gravel", wall_height=5,
         description="A cold-water fishing station with a net loft, ice-storage bay, landing deck and sheltered smoke corner."),
    dict(name="reindeer_herders_camp", size=(29, 15, 25), kind="compound",
         wall="minecraft:spruce_planks", floor="minecraft:stripped_spruce_log",
         roof="spruce", foundation="minecraft:stone_bricks", frame="spruce",
         feature="minecraft:campfire", accent="minecraft:hay_block",
         path="minecraft:coarse_dirt", wall_height=5,
         description="A seasonal herders' camp with two cabins, a broad animal corral, hay storage and a central fire ring."),
    dict(name="alpine_shepherd_refuge", size=(25, 17, 23), kind="mountain",
         wall="minecraft:stone_bricks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:cobbled_deepslate", frame="spruce",
         feature="minecraft:hay_block", accent="minecraft:campfire",
         path="minecraft:gravel", wall_height=6,
         description="A high-elevation shepherd refuge built on stepped stone footings with a small stock pen and windbreak."),
    dict(name="cliffside_hermitage", size=(25, 19, 23), kind="cliff",
         wall="minecraft:stone_bricks", floor="minecraft:spruce_planks",
         roof="dark_oak", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:lantern", accent="minecraft:bookshelf",
         path="minecraft:stone_bricks", wall_height=6,
         description="A compact cliff hermitage with a projecting lookout, retaining walls, switchback steps and a quiet reading room."),
    dict(name="mountain_ore_survey", size=(27, 17, 25), kind="industrial",
         wall="minecraft:tuff_bricks", floor="minecraft:polished_andesite",
         roof="dark_oak", foundation="minecraft:cobbled_deepslate", frame="dark_oak",
         feature="minecraft:raw_iron_block", accent="minecraft:rail",
         path="minecraft:gravel", wall_height=6,
         description="A mountain ore-survey station with a stone assay house, timber winch frame, ore bins and a short mine approach."),
    dict(name="avalanche_shelter", size=(23, 16, 21), kind="mountain",
         wall="minecraft:stone_bricks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:stone_bricks", frame="spruce",
         feature="minecraft:campfire", accent="minecraft:packed_ice",
         path="minecraft:gravel", wall_height=5,
         description="A reinforced alpine shelter with a low profile roof, snow-break walls, emergency stores and a protected fire room."),
    dict(name="jungle_canopy_outpost", size=(29, 21, 27), kind="jungle",
         wall="minecraft:jungle_planks", floor="minecraft:jungle_planks",
         roof="jungle", foundation="minecraft:mossy_cobblestone", frame="jungle",
         feature="minecraft:jungle_leaves", accent="minecraft:bamboo_block",
         path="minecraft:moss_block", wall_height=5,
         description="An elevated canopy outpost with a raised house, tree supports, a lookout deck and a short suspended walkway."),
    dict(name="bamboo_craftsman_yard", size=(27, 17, 25), kind="compound",
         wall="minecraft:bamboo_planks", floor="minecraft:bamboo_planks",
         roof="jungle", foundation="minecraft:stone_bricks", frame="jungle",
         feature="minecraft:bamboo_block", accent="minecraft:loom",
         path="minecraft:coarse_dirt", wall_height=5,
         description="A bamboo craft yard with a work hall, covered drying racks, material stacks and a separate finishing shed."),
    dict(name="rainforest_herbalist_station", size=(27, 17, 25), kind="jungle",
         wall="minecraft:jungle_planks", floor="minecraft:jungle_planks",
         roof="jungle", foundation="minecraft:mossy_cobblestone", frame="jungle",
         feature="minecraft:flowering_azalea", accent="minecraft:barrel",
         path="minecraft:moss_block", wall_height=5,
         description="A rainforest herbalist station with a raised drying room, plant beds, storage shelves and a canopy bridge."),
    dict(name="mangrove_fishery", size=(29, 16, 25), kind="marsh",
         wall="minecraft:mangrove_planks", floor="minecraft:mangrove_planks",
         roof="mangrove", foundation="minecraft:mud_bricks", frame="mangrove",
         feature="minecraft:smoker", accent="minecraft:barrel",
         path="minecraft:mud", wall_height=5,
         description="A mangrove fishery on timber stilts with a net deck, smokehouse corner, raised boardwalk and storage pontoons."),
    dict(name="marsh_reed_market", size=(29, 16, 27), kind="market",
         wall="minecraft:mangrove_planks", floor="minecraft:mud_bricks",
         roof="mangrove", foundation="minecraft:mud", frame="mangrove",
         feature="minecraft:composter", accent="minecraft:hay_block",
         path="minecraft:mud", wall_height=5,
         description="A marsh market with reed-goods stalls, raised counters, covered walkways and a central produce exchange."),
    dict(name="peat_bog_timber_camp", size=(27, 15, 25), kind="camp",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:moss_block", frame="spruce",
         feature="minecraft:campfire", accent="minecraft:barrel",
         path="minecraft:coarse_dirt", wall_height=5,
         description="A peat-bog timber camp with a low bunkhouse, drying racks, raised log stacks and a fire-safe work clearing."),
    dict(name="desert_windcatcher_house", size=(27, 18, 25), kind="desert",
         wall="minecraft:sandstone", floor="minecraft:smooth_sandstone",
         roof="acacia", foundation="minecraft:sandstone", frame="acacia",
         feature="minecraft:cauldron", accent="minecraft:cut_sandstone",
         path="minecraft:sandstone", wall_height=6,
         description="A desert courtyard house with a windcatcher shaft, shaded arcade, cistern and thick sandstone walls."),
    dict(name="oasis_date_grove", size=(29, 16, 27), kind="farm",
         wall="minecraft:sandstone", floor="minecraft:cut_sandstone",
         roof="acacia", foundation="minecraft:sandstone", frame="acacia",
         feature="minecraft:cauldron", accent="minecraft:jungle_leaves",
         path="minecraft:sand", wall_height=5,
         description="An oasis date-grove estate with irrigation channels, a packing house, shaded grove rows and a small water court."),
    dict(name="badlands_saltworks", size=(29, 16, 25), kind="industrial",
         wall="minecraft:red_sandstone", floor="minecraft:red_sandstone",
         roof="acacia", foundation="minecraft:terracotta", frame="acacia",
         feature="minecraft:barrel", accent="minecraft:white_terracotta",
         path="minecraft:red_sand", wall_height=5,
         description="A badlands saltworks with evaporation beds, a processing shed, stacked salt stores and a loading yard."),
    dict(name="mesa_mine_headframe", size=(29, 19, 25), kind="badlands",
         wall="minecraft:orange_terracotta", floor="minecraft:red_sandstone",
         roof="acacia", foundation="minecraft:red_sandstone", frame="acacia",
         feature="minecraft:rail", accent="minecraft:gold_ore",
         path="minecraft:gravel", wall_height=6,
         description="A mesa mine headframe with a tall timber hoist, ore sorting bay, rail spur and terracotta retaining walls."),
    dict(name="savanna_cattle_kraal", size=(31, 16, 27), kind="savanna",
         wall="minecraft:acacia_planks", floor="minecraft:acacia_planks",
         roof="acacia", foundation="minecraft:coarse_dirt", frame="acacia",
         feature="minecraft:hay_block", accent="minecraft:composter",
         path="minecraft:coarse_dirt", wall_height=5,
         description="A savanna cattle kraal with a broad fenced pen, feed store, shade shelter and water trough court."),
    dict(name="steppe_grain_depot", size=(29, 19, 25), kind="steppe",
         wall="minecraft:stripped_acacia_log", floor="minecraft:acacia_planks",
         roof="acacia", foundation="minecraft:stone_bricks", frame="acacia",
         feature="minecraft:hay_block", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=6,
         description="A steppe grain depot with a raised granary, unloading ramp, silo and fenced wagon yard."),
    dict(name="prairie_seed_elevator", size=(27, 19, 25), kind="farm",
         wall="minecraft:oak_planks", floor="minecraft:oak_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="oak",
         feature="minecraft:composter", accent="minecraft:wheat",
         path="minecraft:dirt_path", wall_height=6,
         description="A prairie seed elevator with a tall grain bin, sorting room, crop rows and a compact loading platform."),
    dict(name="rural_dairy_barn", size=(31, 16, 27), kind="farm",
         wall="minecraft:birch_planks", floor="minecraft:birch_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="birch",
         feature="minecraft:hay_block", accent="minecraft:barrel",
         path="minecraft:dirt_path", wall_height=5,
         description="A rural dairy barn with a broad central aisle, fenced paddock, hay loft and milk-storage room."),
    dict(name="horse_relay_post", size=(29, 17, 25), kind="steppe",
         wall="minecraft:oak_planks", floor="minecraft:oak_planks",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="oak",
         feature="minecraft:target", accent="minecraft:hay_block",
         path="minecraft:gravel", wall_height=6,
         description="A horse relay post with a stable wing, changing room, fenced exercise yard and a covered supply platform."),
    dict(name="river_ferry_crossing", size=(33, 14, 17), kind="bridge",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:stone_bricks", frame="spruce",
         feature="minecraft:bell", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=5,
         description="A timber river ferry crossing with a raised bridge deck, stone piers, mooring posts and a small toll shelter."),
    dict(name="riverside_boathouse", size=(29, 16, 25), kind="waterfront",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:barrel", accent="minecraft:prismarine_bricks",
         path="minecraft:gravel", wall_height=5,
         description="A riverside boathouse with a covered slip, repair bay, mooring deck and stacked river-trade cargo."),
    dict(name="floodplain_granary", size=(29, 18, 27), kind="compound",
         wall="minecraft:oak_planks", floor="minecraft:oak_planks",
         roof="spruce", foundation="minecraft:stone_bricks", frame="oak",
         feature="minecraft:hay_block", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=6,
         description="A floodplain granary compound with a raised storehouse, drying barn, flood markers and a protected loading yard."),
    dict(name="delta_reed_market", size=(31, 16, 27), kind="market",
         wall="minecraft:mangrove_planks", floor="minecraft:mud_bricks",
         roof="mangrove", foundation="minecraft:mud", frame="mangrove",
         feature="minecraft:composter", accent="minecraft:barrel",
         path="minecraft:mud", wall_height=5,
         description="A delta reed market with raised stalls, woven-goods counters, boardwalk links and a central landing court."),
    dict(name="coastal_signal_station", size=(23, 23, 23), kind="tower",
         wall="minecraft:stone_bricks", floor="minecraft:stone_bricks",
         roof="spruce", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:bell", accent="minecraft:lantern",
         path="minecraft:gravel", wall_height=7,
         description="A coastal signal station with a compact stone beacon tower, bell platform, keeper room and exposed lookout deck."),
    dict(name="island_lighthouse_outpost", size=(23, 25, 23), kind="tower",
         wall="minecraft:white_terracotta", floor="minecraft:stone_bricks",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="dark_oak",
         feature="minecraft:sea_lantern", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=7,
         description="An island lighthouse outpost with a tapered light tower, keeper's quarters, supply shed and sea-facing platform."),
    dict(name="coastal_salvage_yard", size=(31, 16, 27), kind="waterfront",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="spruce",
         feature="minecraft:iron_bars", accent="minecraft:barrel",
         path="minecraft:gravel", wall_height=5,
         description="A coastal salvage yard with a sorting shed, crane frame, cargo racks, slipway and fenced salvage piles."),
    dict(name="cherry_grove_tea_house", size=(27, 15, 25), kind="civic",
         wall="minecraft:cherry_planks", floor="minecraft:cherry_planks",
         roof="cherry", foundation="minecraft:polished_diorite", frame="cherry",
         feature="minecraft:chiseled_bookshelf", accent="minecraft:cherry_leaves",
         path="minecraft:polished_diorite", wall_height=5,
         description="A cherry-grove tea house with a veranda, garden court, reading alcove and a stepped stone approach."),
    dict(name="mountain_monastery_scriptorium", size=(31, 19, 27), kind="civic",
         wall="minecraft:dark_oak_planks", floor="minecraft:stone_bricks",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="dark_oak",
         feature="minecraft:lectern", accent="minecraft:candle",
         path="minecraft:stone_bricks", wall_height=6,
         description="A mountain monastery scriptorium with a writing hall, cloister walk, bell court and sheltered archive room."),
    dict(name="town_clock_tower", size=(21, 25, 21), kind="tower",
         wall="minecraft:bricks", floor="minecraft:stone_bricks",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="dark_oak",
         feature="minecraft:bell", accent="minecraft:redstone_lamp",
         path="minecraft:stone_bricks", wall_height=7,
         description="A civic clock tower with a square brick shaft, upper belfry, clock-face panels and a paved public forecourt."),
    dict(name="roadside_inn", size=(29, 16, 25), kind="compound",
         wall="minecraft:oak_planks", floor="minecraft:oak_planks",
         roof="spruce", foundation="minecraft:cobblestone", frame="oak",
         feature="minecraft:barrel", accent="minecraft:lantern",
         path="minecraft:gravel", wall_height=5,
         description="A roadside inn with a guest house, stable annex, enclosed courtyard, hitching posts and a covered dining porch."),
    dict(name="civic_courthouse", size=(31, 19, 27), kind="civic",
         wall="minecraft:calcite", floor="minecraft:polished_andesite",
         roof="dark_oak", foundation="minecraft:stone_bricks", frame="dark_oak",
         feature="minecraft:lectern", accent="minecraft:bell",
         path="minecraft:polished_andesite", wall_height=6,
         description="A small civic courthouse with a columned entrance, raised hearing hall, archive wing and symmetrical public steps."),
    dict(name="pilgrim_waystation", size=(29, 16, 25), kind="desert",
         wall="minecraft:sandstone", floor="minecraft:cut_sandstone",
         roof="acacia", foundation="minecraft:sandstone", frame="acacia",
         feature="minecraft:barrel", accent="minecraft:campfire",
         path="minecraft:sandstone", wall_height=5,
         description="A desert pilgrim waystation with a shaded sleeping court, water store, covered portico and pack-animal pen."),
    dict(name="stone_aqueduct", size=(35, 16, 17), kind="aqueduct",
         wall="minecraft:stone_bricks", floor="minecraft:stone_bricks",
         roof="stone", foundation="minecraft:stone_bricks", frame="oak",
         feature="minecraft:sea_lantern", accent="minecraft:lantern",
         path="minecraft:gravel", wall_height=5,
         description="A stone aqueduct segment with repeated support arches, a raised water channel, inspection walk and end platform."),
    dict(name="canal_boatyard", size=(33, 16, 25), kind="waterfront",
         wall="minecraft:oak_planks", floor="minecraft:oak_planks",
         roof="spruce", foundation="minecraft:stone_bricks", frame="oak",
         feature="minecraft:barrel", accent="minecraft:prismarine_bricks",
         path="minecraft:gravel", wall_height=5,
         description="A canal boatyard with a covered repair shed, towpath, mooring bollards, cargo racks and a service quay."),
    dict(name="volcanic_obsidian_works", size=(29, 18, 27), kind="industrial",
         wall="minecraft:basalt", floor="minecraft:polished_blackstone",
         roof="dark_oak", foundation="minecraft:basalt", frame="dark_oak",
         feature="minecraft:obsidian", accent="minecraft:magma_block",
         path="minecraft:blackstone", wall_height=6,
         description="A volcanic obsidian works with a basalt forge hall, cooling yard, guarded material racks and a blackstone apron."),
    dict(name="forest_ranger_academy", size=(31, 18, 27), kind="compound",
         wall="minecraft:spruce_planks", floor="minecraft:spruce_planks",
         roof="spruce", foundation="minecraft:stone_bricks", frame="spruce",
         feature="minecraft:lectern", accent="minecraft:target",
         path="minecraft:coarse_dirt", wall_height=6,
         description="A forest ranger academy with a teaching lodge, map room, practice range, equipment store and a connected courtyard."),
)

# Explicit architectural forms prevent the biome-specific designs from collapsing
# into one generic cottage-with-a-yard layout. Each form has a distinct massing
# and circulation plan; materials and functional details still come from the
# individual design specification above.
DESIGN_FORMS = {
    "forest_logging_camp": "longhouse",
    "birch_sap_harvest": "greenhouse",
    "taiga_trapper_station": "longhouse",
    "taiga_sled_depot": "industrial_yard",
    "glacial_fishing_station": "dockyard",
    "reindeer_herders_camp": "radial_compound",
    "alpine_shepherd_refuge": "terraced",
    "cliffside_hermitage": "terraced",
    "mountain_ore_survey": "industrial_yard",
    "avalanche_shelter": "terraced",
    "jungle_canopy_outpost": "stilt_village",
    "bamboo_craftsman_yard": "workshop_court",
    "rainforest_herbalist_station": "greenhouse",
    "mangrove_fishery": "dockyard",
    "marsh_reed_market": "market_square",
    "peat_bog_timber_camp": "longhouse",
    "desert_windcatcher_house": "windcatcher_courtyard",
    "oasis_date_grove": "irrigated_garden",
    "badlands_saltworks": "industrial_yard",
    "mesa_mine_headframe": "industrial_yard",
    "savanna_cattle_kraal": "radial_compound",
    "steppe_grain_depot": "longhouse",
    "prairie_seed_elevator": "windmill_grain",
    "rural_dairy_barn": "longhouse",
    "horse_relay_post": "stable_courtyard",
    "river_ferry_crossing": "bridge_gate",
    "riverside_boathouse": "dockyard",
    "floodplain_granary": "stilt_granary",
    "delta_reed_market": "market_square",
    "coastal_signal_station": "tower_complex",
    "island_lighthouse_outpost": "lighthouse",
    "coastal_salvage_yard": "dockyard",
    "cherry_grove_tea_house": "garden_pavilion",
    "mountain_monastery_scriptorium": "cloister",
    "town_clock_tower": "tower_complex",
    "roadside_inn": "stable_courtyard",
    "civic_courthouse": "civic_hall",
    "pilgrim_waystation": "courtyard",
    "stone_aqueduct": "aqueduct",
    "canal_boatyard": "dockyard",
    "volcanic_obsidian_works": "industrial_yard",
    "forest_ranger_academy": "cloister",
}


def _door(b: Building, x: int, y: int, z: int, wood: str, facing: str = "north"):
    b.set(x, y, z, f"minecraft:{wood}_door", facing=facing, half="lower",
          hinge="left", open="false", powered="false")
    b.set(x, y + 1, z, f"minecraft:{wood}_door", facing=facing, half="upper",
          hinge="left", open="false", powered="false")


def _pane(b: Building, x: int, y: int, z: int):
    b.set(x, y, z, "minecraft:glass_pane", east="false", north="false",
          south="false", waterlogged="false", west="false")


def _lantern(b: Building, x: int, y: int, z: int, hanging: bool = False):
    b.set(x, y, z, "minecraft:lantern", hanging=str(hanging).lower())


def _feature_pair(b: Building, spec: dict, x: int, y: int, z: int):
    b.set(x, y, z, spec["feature"])
    ax = min(b.width - 2, x + 2)
    az = min(b.length - 2, z + 1)
    b.set(ax, y, az, spec["accent"])


def _add_house(
    b: Building, x1: int, z1: int, x2: int, z2: int, base_y: int,
    wall_top: int, wall: str, floor: str, roof: str, frame: str,
    spec: dict,
):
    if x1 < 2 or z1 < 2 or x2 > b.width - 3 or z2 > b.length - 3:
        raise ValueError(f"{b.name}: house footprint outside site bounds")
    b.fill(x1, base_y, z1, x2, base_y, z2, floor)
    b.shell(x1, base_y + 1, z1, x2, wall_top, z2, wall)
    for x in (x1, x2):
        for z in (z1, z2):
            for y in range(base_y + 1, wall_top + 1):
                b.log(x, y, z, frame)
    center = (x1 + x2) // 2
    _door(b, center, base_y + 1, z1, frame)
    window_y = min(wall_top - 1, base_y + 3)
    for x in (x1, x2):
        for z in range(z1 + 2, z2, 3):
            _pane(b, x, window_y, z)
    for x in range(x1 + 2, x2 - 1, 3):
        if x != center:
            _pane(b, x, window_y, z1)
            _pane(b, x, window_y, z2)
    b.gable_roof(x1 - 1, x2 + 1, z1 - 1, z2 + 1,
                 wall_top + 1, 2, roof)
    _feature_pair(b, spec, center, base_y + 1, (z1 + z2) // 2)
    _lantern(b, x1 + 1, base_y + 2, z1 + 1)
    _lantern(b, x2 - 1, base_y + 2, z2 - 1)


def _add_fence_pen(b: Building, x1: int, z1: int, x2: int, z2: int, wood: str):
    for x in range(x1, x2 + 1):
        b.fence(x, 2, z1, wood)
        b.fence(x, 2, z2, wood)
    for z in range(z1, z2 + 1):
        b.fence(x1, 2, z, wood)
        b.fence(x2, 2, z, wood)


def _build_tower(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    top = min(b.height - 7, 16)
    b.fill(cx - 4, 0, cz - 4, cx + 4, 0, cz + 4, spec["foundation"])
    b.fill(cx - 3, 1, cz - 3, cx + 3, 1, cz + 3, spec["floor"])
    b.shell(cx - 3, 2, cz - 3, cx + 3, top, cz + 3, spec["wall"])
    for x in (cx - 3, cx + 3):
        for z in (cz - 3, cz + 3):
            for y in range(2, top + 1):
                b.log(x, y, z, spec["frame"])
    for y in (6, 11):
        if y < top:
            b.fill(cx - 2, y, cz - 2, cx + 2, y, cz + 2, spec["floor"])
    for y in (4, 9, 13):
        if y < top:
            _pane(b, cx - 3, y, cz)
            _pane(b, cx + 3, y, cz)
            _pane(b, cx, y, cz - 3)
            _pane(b, cx, y, cz + 3)
    _door(b, cx, 2, cz - 3, spec["frame"])
    b.gable_roof(cx - 4, cx + 4, cz - 4, cz + 4, top + 1, 3, spec["roof"])
    _feature_pair(b, spec, cx, 2, cz)
    for x, z in ((cx - 5, cz - 5), (cx + 5, cz - 5),
                 (cx - 5, cz + 5), (cx + 5, cz + 5)):
        b.fill(x, 0, z, x, 1, z, spec["foundation"])
        _lantern(b, x, 2, z)


def _build_bridge(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    deck_y = 4
    b.fill(1, deck_y, cz - 2, b.width - 2, deck_y, cz + 2, spec["floor"])
    for x in range(1, b.width - 1, 5):
        for z in (cz - 3, cz + 3):
            for y in range(0, deck_y):
                b.set(x, y, z, spec["foundation"])
            b.fence(x, deck_y + 1, z, spec["frame"])
    for x in range(2, b.width - 2):
        b.fence(x, deck_y + 1, cz - 3, spec["frame"])
        b.fence(x, deck_y + 1, cz + 3, spec["frame"])
    hut_x1, hut_x2 = 2, 8
    hut_z1, hut_z2 = cz - 5, cz - 1
    _add_house(b, hut_x1, hut_z1, hut_x2, hut_z2, 1, 4,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _feature_pair(b, spec, cx, deck_y + 1, cz)
    for x in (cx - 2, cx + 2):
        _lantern(b, x, deck_y + 2, cz)


def _build_aqueduct(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    channel_y = 8
    for x in range(1, b.width - 1):
        b.fill(x, channel_y, cz - 1, x, channel_y, cz + 1, "minecraft:water")
        b.fill(x, channel_y - 1, cz - 2, x, channel_y - 1, cz + 2, spec["foundation"])
        b.fill(x, channel_y + 1, cz - 2, x, channel_y + 1, cz - 2, spec["wall"])
        b.fill(x, channel_y + 1, cz + 2, x, channel_y + 1, cz + 2, spec["wall"])
        if x % 4 == 1:
            for z in (cz - 2, cz + 2):
                for y in range(0, channel_y - 1):
                    b.set(x, y, z, spec["foundation"])
                b.fill(x - 1, channel_y - 2, z, x + 1, channel_y - 2, z, spec["wall"])
                b.set(x, 1, z, "minecraft:cracked_stone_bricks")
                b.set(x, 2, z, "minecraft:chiseled_stone_bricks")
                b.set(x, 3, z, "minecraft:polished_andesite")
    # Inspection platforms and stair access make this a traversable water
    # system, not just a floating line of water blocks.
    for z in (cz - 3, cz + 3):
        b.fill(1, channel_y - 1, z, 3, channel_y - 1, z, "minecraft:polished_andesite")
        b.fill(b.width - 4, channel_y - 1, z, b.width - 2, channel_y - 1, z,
               "minecraft:polished_andesite")
        for x in (3, b.width - 4):
            b.stair(x, channel_y - 2, z, "stone_brick", "north" if z < cz else "south")
    for x in range(2, b.width - 2, 4):
        _lantern(b, x, channel_y + 2, cz - 3)
        b.set(x, channel_y - 3, cz - 2, "minecraft:chain")
    b.set(cx, channel_y - 2, cz, "minecraft:chiseled_stone_bricks")
    _feature_pair(b, spec, cx, channel_y + 2, cz)


def _build_market(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    b.fill(2, 0, 2, b.width - 3, 0, b.length - 3, spec["foundation"])
    for index, x in enumerate((cx - 8, cx - 3, cx + 3, cx + 8)):
        x1, x2 = x - 2, x + 2
        z1, z2 = cz - 4, cz + 4
        b.fill(x1, 1, z1, x2, 1, z2, spec["floor"])
        for px in (x1, x2):
            for pz in (z1, z2):
                b.log(px, 2, pz, spec["frame"])
                b.log(px, 3, pz, spec["frame"])
                b.log(px, 4, pz, spec["frame"])
        awning = ("minecraft:red_wool", "minecraft:yellow_wool",
                  "minecraft:blue_wool", "minecraft:green_wool")[index]
        b.fill(x1, 5, z1, x2, 5, z2, awning)
        b.set(x, 2, cz, spec["feature"])
        b.set(x + 1, 2, cz + 1, spec["accent"])
        _lantern(b, x, 4, cz)
    b.fill(cx - 1, 1, 2, cx + 1, 1, b.length - 3, spec["path"])
    for x in (cx - 10, cx + 10):
        _lantern(b, x, 2, cz - 6)
        _lantern(b, x, 2, cz + 6)


def _build_jungle(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    base_y = 6
    for x, z in ((cx - 5, cz - 5), (cx + 5, cz - 5),
                 (cx - 5, cz + 5), (cx + 5, cz + 5)):
        for y in range(0, base_y + 1):
            b.log(x, y, z, spec["frame"])
    b.fill(cx - 7, base_y, cz - 7, cx + 7, base_y, cz + 7, spec["floor"])
    _add_house(b, cx - 5, cz - 4, cx + 5, cz + 4, base_y + 1,
               base_y + 5, spec["wall"], spec["floor"], spec["roof"],
               spec["frame"], spec)
    for x in range(cx - 7, cx + 8, 3):
        b.set(x, base_y, cz - 8, "minecraft:jungle_leaves")
        b.set(x, base_y, cz + 8, "minecraft:jungle_leaves")
    for x in range(cx - 5, cx + 6):
        b.fill(x, 3, cz - 1, x, 3, cz + 1, spec["floor"])
    _feature_pair(b, spec, cx, base_y + 2, cz)


def _build_marsh(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    base_y = 4
    for x, z in ((cx - 5, cz - 4), (cx + 5, cz - 4),
                 (cx - 5, cz + 4), (cx + 5, cz + 4)):
        for y in range(0, base_y + 1):
            b.log(x, y, z, spec["frame"])
    b.fill(cx - 7, base_y, cz - 6, cx + 7, base_y, cz + 6, spec["floor"])
    _add_house(b, cx - 5, cz - 4, cx + 5, cz + 4, base_y + 1,
               base_y + 5, spec["wall"], spec["floor"], spec["roof"],
               spec["frame"], spec)
    b.fill(cx - 2, 2, 0, cx + 2, 2, cz - 5, spec["floor"])
    for x in (cx - 2, cx + 2):
        for z in range(0, cz - 4, 3):
            b.log(x, 0, z, spec["frame"])
            b.fence(x, 3, z, spec["frame"])
    _feature_pair(b, spec, cx, base_y + 2, cz)


def _build_waterfront(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 5, cz - 5, cx + 5, cz + 3, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    dock_z1, dock_z2 = min(b.length - 2, cz + 5), b.length - 2
    b.fill(cx - 7, 1, dock_z1, cx + 7, 1, dock_z2, spec["floor"])
    for x in range(cx - 6, cx + 7, 3):
        for z in (dock_z1, dock_z2):
            for y in range(0, 3):
                b.log(x, y, z, spec["frame"])
            b.fence(x, 2, z, spec["frame"])
    for x in (cx - 5, cx + 5):
        _lantern(b, x, 3, dock_z1 + 1)
    _feature_pair(b, spec, cx, 2, cz)


def _build_cliff(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 5, cz - 5, cx + 4, cz + 3, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx + 5, 2, cz - 2, b.width - 2, 2, cz + 2, spec["foundation"])
    for x in range(cx + 5, b.width - 1, 3):
        for z in (cz - 3, cz + 3):
            b.log(x, 3, z, spec["frame"])
            b.fence(x, 4, z, spec["frame"])
    for step in range(6):
        x = max(1, cx - 7 + step)
        z = min(b.length - 2, cz + 7 - step)
        b.stair(x, 2 + step // 2, z, "stone_brick", "east")
    _feature_pair(b, spec, cx, 2, cz)


def _build_badlands(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 5, cz - 5, cx + 3, cz + 3, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    head_x = min(b.width - 6, cx + 8)
    head_z = cz
    for x in (head_x - 2, head_x + 2):
        for z in (head_z - 2, head_z + 2):
            for y in range(1, 13):
                b.log(x, y, z, spec["frame"])
    for x in (head_x - 2, head_x + 2):
        b.log(x, 13, head_z, spec["frame"], axis="x")
    b.log(head_x, 13, head_z, spec["frame"])
    b.set(head_x, 12, head_z, spec["feature"])
    for x in range(head_x - 3, head_x + 4):
        b.set(x, 1, head_z + 4, "minecraft:rail", shape="east_west")
    _feature_pair(b, spec, cx, 2, cz)


def _build_default_site(b: Building, spec: dict):
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 5, cz - 4, cx + 5, cz + 4, 1,
               1 + spec["wall_height"], spec["wall"], spec["floor"],
               spec["roof"], spec["frame"], spec)
    b.fill(cx - 1, 1, 0, cx + 1, 1, cz - 5, spec["path"])
    kind = spec["kind"]
    if kind in ("camp", "compound"):
        _add_house(b, 2, cz + 3, 8, cz + 8, 1, 4,
                   spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
        _add_fence_pen(b, b.width - 9, cz + 2, b.width - 3, b.length - 3, spec["frame"])
        b.set(b.width - 6, 1, b.length - 5, "minecraft:hay_block")
        b.set(b.width - 5, 1, b.length - 5, "minecraft:barrel")
    elif kind == "farm":
        for row in range(3):
            z = cz + 7 + row * 2
            if z < b.length - 2:
                b.fill(2, 1, z, b.width - 3, 1, z, "minecraft:farmland")
                for x in range(3, b.width - 3, 2):
                    b.set(x, 2, z, "minecraft:wheat", age="7")
        b.fill(2, 1, cz + 5, b.width - 3, 1, cz + 5, "minecraft:water")
        _add_fence_pen(b, 2, 2, 8, min(b.length - 3, cz + 7), spec["frame"])
    elif kind == "industrial":
        chimney_x, chimney_z = b.width - 5, 4
        b.fill(chimney_x - 1, 1, chimney_z - 1, chimney_x + 1, 1, chimney_z + 1, spec["foundation"])
        for y in range(2, min(b.height - 1, 12)):
            for x in (chimney_x - 1, chimney_x + 1):
                for z in (chimney_z - 1, chimney_z + 1):
                    b.set(x, y, z, spec["wall"])
        b.fill(chimney_x - 1, min(b.height - 2, 12), chimney_z - 1,
               chimney_x + 1, min(b.height - 2, 12), chimney_z + 1, spec["foundation"])
        b.set(chimney_x, 2, chimney_z + 3, "minecraft:blast_furnace")
        b.set(chimney_x - 3, 2, chimney_z + 3, "minecraft:anvil")
    elif kind == "mountain":
        for step in range(7):
            x = max(1, cx - 8 + step)
            z = min(b.length - 2, cz + 7 - step)
            b.stair(x, 2 + step // 2, z, "stone_brick", "east")
        for x in (2, b.width - 3):
            b.fill(x, 0, cz - 6, x, 2, cz + 5, spec["foundation"])
    elif kind == "desert":
        for x in range(2, b.width - 2):
            b.fill(x, 1, 2, x, 1, 2, spec["foundation"])
            b.fill(x, 1, b.length - 3, x, 1, b.length - 3, spec["foundation"])
        for z in range(2, b.length - 2):
            b.fill(2, 1, z, 2, 1, z, spec["foundation"])
            b.fill(b.width - 3, 1, z, b.width - 3, 1, z, spec["foundation"])
        b.fill(cx - 2, 1, cz + 6, cx + 2, 1, cz + 10, spec["foundation"])
        b.fill(cx - 1, 2, cz + 7, cx + 1, 2, cz + 9, "minecraft:water")
        for x in (cx - 2, cx + 2):
            b.log(x, 3, cz + 7, spec["frame"])
            b.log(x, 3, cz + 9, spec["frame"])
    elif kind == "savanna":
        _add_fence_pen(b, cx + 7, cz - 5, b.width - 3, b.length - 3, spec["frame"])
        for x in range(cx + 8, b.width - 3, 3):
            b.set(x, 1, b.length - 5, "minecraft:hay_block")
        b.set(cx + 9, 1, cz + 5, "minecraft:composter", level="0")
    elif kind == "steppe":
        silo_x, silo_z = b.width - 5, cz - 2
        b.fill(silo_x - 2, 1, silo_z - 2, silo_x + 2, 1, silo_z + 2, spec["foundation"])
        b.shell(silo_x - 2, 2, silo_z - 2, silo_x + 2, min(b.height - 3, 11), silo_z + 2, spec["wall"])
        b.gable_roof(silo_x - 3, silo_x + 3, silo_z - 3, silo_z + 3,
                     min(b.height - 2, 12), 2, spec["roof"])
    elif kind == "civic":
        for x in range(cx - 5, cx + 6, 3):
            for y in range(2, 6):
                b.log(x, y, 1, spec["frame"])
        b.fill(cx - 6, 6, 0, cx + 6, 6, 2, f"minecraft:{spec['roof']}_planks")
        for step in range(3):
            b.fill(cx - 5 + step, 1 + step, 0, cx + 5 - step, 1 + step, 0, spec["foundation"])
    elif kind == "cliff":
        b.fill(cx + 6, 1, cz - 3, b.width - 2, 1, cz + 3, spec["floor"])
    elif kind == "badlands":
        _build_badlands(b, spec)
    _feature_pair(b, spec, cx, 2, cz)


def _roof_planks(spec: dict) -> str:
    roof = spec["roof"]
    return roof if roof.startswith("minecraft:") else f"minecraft:{roof}_planks"


def _build_longhouse(b: Building, spec: dict):
    """Long-span communal hall with internal bays, loading porch and service yard."""
    cx, cz = b.width // 2, b.length // 2
    x1, x2 = max(2, cx - 9), min(b.width - 3, cx + 9)
    z1, z2 = max(2, cz - 4), min(b.length - 3, cz + 4)
    wall_top = min(7, b.height - 6)
    _add_house(b, x1, z1, x2, z2, 1, wall_top,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    # Structural bays and a deliberately open central working aisle.
    for x in range(x1 + 4, x2 - 2, 5):
        for y in range(2, wall_top):
            b.log(x, y, z1 + 1, spec["frame"])
            b.log(x, y, z2 - 1, spec["frame"])
        b.fill(x, 2, z1 + 2, x, wall_top - 1, cz - 2, spec["wall"])
        b.fill(x, 2, cz + 2, x, wall_top - 1, z2 - 2, spec["wall"])
    b.fill(x1 + 2, 1, z2 + 2, x2 - 2, 1, min(b.length - 2, z2 + 4), spec["path"])
    for x in range(x1 + 3, x2 - 2, 4):
        b.log(x, 2, z2 + 3, spec["frame"])
        b.log(x, 5, z2 + 3, spec["frame"], axis="x")
    b.set(x1 + 2, 2, z2 - 2, "minecraft:barrel", facing="up", open="false")
    b.set(x2 - 2, 2, z2 - 2, "minecraft:hay_block")


def _build_greenhouse(b: Building, spec: dict):
    """Glazed growing hall with irrigation, raised beds and a potting bay."""
    cx, cz = b.width // 2, b.length // 2
    x1, x2, z1, z2 = 3, b.width - 4, 4, b.length - 5
    b.fill(x1, 1, z1, x2, 1, z2, spec["floor"])
    for y in range(2, 5):
        for x in range(x1, x2 + 1):
            b.set(x, y, z1, "minecraft:glass")
            b.set(x, y, z2, "minecraft:glass")
        for z in range(z1, z2 + 1):
            b.set(x1, y, z, "minecraft:glass")
            b.set(x2, y, z, "minecraft:glass")
    for x in range(x1, x2 + 1, 4):
        for z in (z1, z2):
            for y in range(2, 6):
                b.log(x, y, z, spec["frame"])
    for z in range(z1, z2 + 1, 4):
        for x in (x1, x2):
            for y in range(2, 6):
                b.log(x, y, z, spec["frame"])
    # A glazed, shallow-pitched roof is unlike the solid gables used by the cabins.
    b.fill(x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, "minecraft:glass")
    b.fill(cx - 1, 6, z1 + 1, cx + 1, 6, z2 - 1, "minecraft:glass")
    _door(b, cx, 2, z1, spec["frame"])
    for z in range(z1 + 2, z2 - 1, 3):
        b.fill(x1 + 2, 1, z, x2 - 2, 1, z, "minecraft:farmland", moisture="7")
        for x in range(x1 + 3, x2 - 2, 2):
            b.set(x, 2, z, "minecraft:potatoes", age="7")
    b.fill(cx - 1, 1, z1 + 1, cx + 1, 1, z2 - 1, "minecraft:water")
    b.set(x2 - 2, 2, z2 - 2, "minecraft:composter", level="0")
    b.set(x1 + 2, 2, z2 - 2, "minecraft:barrel", facing="up", open="false")
    _lantern(b, x1 + 1, 3, z1 + 1)
    _lantern(b, x2 - 1, 3, z1 + 1)


def _build_courtyard(b: Building, spec: dict):
    """Three-sided courtyard compound with a formal entrance and central cistern."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 6, 2, cx + 6, 8, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, 2, cz - 2, 6, cz + 5, 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, b.width - 7, cz - 2, b.width - 3, cz + 5, 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 5, 1, cz - 1, cx + 5, 1, b.length - 4, spec["path"])
    b.fill(cx - 2, 1, cz + 1, cx + 2, 1, cz + 5, spec["foundation"])
    b.fill(cx - 1, 2, cz + 2, cx + 1, 2, cz + 4, "minecraft:water_cauldron", level="3")
    for x in (cx - 3, cx + 3):
        for y in range(2, 6):
            b.log(x, y, b.length - 3, spec["frame"])
    b.log(cx - 3, 6, b.length - 3, spec["frame"], axis="x")
    b.log(cx + 3, 6, b.length - 3, spec["frame"], axis="x")
    b.fill(cx - 2, 1, b.length - 2, cx + 2, 1, b.length - 2, spec["path"])
    _lantern(b, cx - 4, 3, cz + 1)
    _lantern(b, cx + 4, 3, cz + 1)


def _build_windcatcher_courtyard(b: Building, spec: dict):
    _build_courtyard(b, spec)
    cx, cz = b.width // 2, b.length // 2
    for x, z in ((cx - 4, 4), (cx + 4, 4), (cx - 4, cz - 1), (cx + 4, cz - 1)):
        for y in range(2, min(b.height - 3, 10)):
            b.shell(x - 1, y, z - 1, x + 1, y, z + 1, spec["wall"])
        b.fill(x - 1, min(b.height - 3, 10), z - 1,
               x + 1, min(b.height - 3, 10), z + 1, _roof_planks(spec)
               if spec["roof"].startswith("minecraft:") else f"minecraft:{spec['roof']}_planks")
        b.set(x, 3, z, "minecraft:iron_bars")


def _build_garden_pavilion(b: Building, spec: dict):
    _build_courtyard(b, spec)
    cx, cz = b.width // 2, b.length // 2
    # Garden paths, reflecting pool and a light pergola create a retreat rather than a house lot.
    b.fill(cx - 3, 1, cz + 1, cx + 3, 1, cz + 5, "minecraft:water")
    b.fill(cx - 4, 1, cz + 6, cx + 4, 1, cz + 6, spec["path"])
    for x in range(cx - 5, cx + 6, 2):
        b.log(x, 2, cz + 7, spec["frame"])
        b.log(x, 5, cz + 7, spec["frame"], axis="x")
        b.set(x, 4, cz + 8, "minecraft:cherry_leaves")
    for x, z in ((cx - 4, cz - 2), (cx + 4, cz - 2),
                 (cx - 4, cz + 7), (cx + 4, cz + 7)):
        b.log(x, 2, z, spec["frame"])
        b.log(x, 3, z, spec["frame"])
        b.set(x, 4, z, "minecraft:cherry_leaves")
    for x in (cx - 2, cx + 2):
        b.set(x, 2, cz + 3, "minecraft:chiseled_bookshelf")


def _build_stilt_village(b: Building, spec: dict):
    """Elevated house, braced timber piles, suspended boardwalk and landing."""
    cx, cz = b.width // 2, b.length // 2
    base_y = 4
    supports = ((cx - 6, cz - 5), (cx + 6, cz - 5),
                (cx - 6, cz + 5), (cx + 6, cz + 5))
    for x, z in supports:
        for y in range(0, base_y + 1):
            b.log(x, y, z, spec["frame"])
        b.log(x, 2, z - 1, spec["frame"], axis="z")
        b.log(x, 2, z + 1, spec["frame"], axis="z")
    b.fill(cx - 7, base_y, cz - 6, cx + 7, base_y, cz + 6, spec["floor"])
    _add_house(b, cx - 5, cz - 4, cx + 5, cz + 4, base_y + 1, 10,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 2, base_y, 0, cx + 2, base_y, cz - 6, spec["floor"])
    for z in range(1, cz - 5, 3):
        for x in (cx - 2, cx + 2):
            for y in range(0, base_y):
                b.log(x, y, z, spec["frame"])
    b.stair(cx, base_y - 1, cz + 7, "spruce", "north")
    for x in (cx - 7, cx + 7):
        b.fence(x, base_y + 1, cz - 4, spec["frame"])
        b.fence(x, base_y + 1, cz + 4, spec["frame"])
    _lantern(b, cx - 6, base_y + 2, cz)
    _lantern(b, cx + 6, base_y + 2, cz)


def _build_terraced(b: Building, spec: dict):
    """Retaining terraces, switchback stairs, an elevated hall and lookout."""
    cx, cz = b.width // 2, b.length // 2
    b.fill(1, 0, 1, b.width - 2, 0, b.length - 2, spec["foundation"])
    b.fill(2, 1, cz - 4, b.width - 3, 1, b.length - 4, spec["foundation"])
    b.fill(4, 2, cz - 3, b.width - 5, 2, b.length - 5, spec["wall"])
    _add_house(b, cx - 5, cz - 4, cx + 4, cz + 3, 3, 8,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    for step in range(7):
        x = max(1, cx - 8 + step)
        z = min(b.length - 2, b.length - 3 - step)
        b.stair(x, 1 + step // 2, z, "stone_brick", "north")
    lookout_x, lookout_z = b.width - 6, 4
    for y in range(3, min(b.height - 2, 9)):
        b.log(lookout_x, y, lookout_z, spec["frame"])
        b.log(lookout_x - 2, y, lookout_z, spec["frame"])
    b.fill(lookout_x - 2, min(b.height - 2, 9), lookout_z - 2,
           lookout_x, min(b.height - 2, 9), lookout_z + 2, spec["floor"])
    _lantern(b, lookout_x - 1, min(b.height - 3, 10), lookout_z)


def _build_industrial_yard(b: Building, spec: dict):
    """Separated production shed, tall stack/hoist, rail spur and material bays."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, 2, cz - 5, cx + 1, cz + 3, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    stack_x, stack_z = b.width - 6, cz - 3
    stack_top = min(b.height - 3, 13)
    for x in (stack_x - 1, stack_x + 1):
        for z in (stack_z - 1, stack_z + 1):
            for y in range(1, stack_top):
                b.set(x, y, z, spec["wall"])
    b.fill(stack_x - 2, stack_top, stack_z - 2,
           stack_x + 2, stack_top, stack_z + 2, spec["foundation"])
    b.fill(2, 1, b.length - 4, b.width - 3, 1, b.length - 4, spec["path"])
    for x in range(3, b.width - 3, 2):
        b.set(x, 2, b.length - 4, "minecraft:rail", shape="east_west")
    for x in (b.width - 11, b.width - 7):
        z = min(b.length - 5, cz + 5)
        b.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, spec["foundation"])
        b.set(x, 2, z, "minecraft:barrel", facing="up", open="false")
    crane_x = b.width - 10
    for z in (cz + 2, min(b.length - 3, cz + 8)):
        for y in range(2, min(b.height - 3, 10)):
            b.log(crane_x, y, z, spec["frame"])
    b.log(crane_x, min(b.height - 3, 10), cz + 2, spec["frame"], axis="z")
    b.set(crane_x, min(b.height - 4, 9), cz + 5, "minecraft:chain")
    b.set(cx, 2, cz + 4, "minecraft:blast_furnace", facing="north")
    b.set(cx + 3, 2, cz + 4, "minecraft:anvil", facing="north")


def _build_dockyard(b: Building, spec: dict):
    """Warehouse, pile-supported quay, crane, cargo racks and boat slip."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 7, 2, cx + 5, cz + 1, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    dock_z1, dock_z2 = cz + 3, b.length - 2
    b.fill(cx - 7, 1, dock_z1, cx + 7, 1, dock_z2, spec["floor"])
    for x in range(cx - 6, cx + 7, 3):
        for z in (dock_z1, dock_z2):
            for y in range(0, 2):
                b.log(x, y, z, spec["frame"])
            b.fence(x, 2, z, spec["frame"])
    crane_x = min(b.width - 4, cx + 8)
    for x in (crane_x - 2, crane_x + 1):
        for y in range(2, min(b.height - 3, 10)):
            b.log(x, y, dock_z1 + 1, spec["frame"])
    b.log(crane_x - 2, min(b.height - 3, 10), dock_z1 + 1, spec["frame"], axis="x")
    b.log(crane_x + 1, min(b.height - 3, 10), dock_z1 + 1, spec["frame"], axis="x")
    b.set(crane_x - 1, min(b.height - 4, 9), dock_z1 + 1, "minecraft:chain")
    for x in (cx - 5, cx + 5):
        b.set(x, 2, dock_z2 - 1, "minecraft:barrel", facing="up", open="false")
        b.fence(x, 3, dock_z2 - 2, spec["frame"])
    b.fill(cx - 2, 1, dock_z2 - 1, cx + 2, 1, dock_z2, spec["path"])
    _lantern(b, cx - 6, 3, dock_z1 + 1)
    _lantern(b, cx + 6, 3, dock_z1 + 1)


def _build_market_square(b: Building, spec: dict):
    """Four independent covered stalls around a public square and central well."""
    cx, cz = b.width // 2, b.length // 2
    b.fill(2, 1, 2, b.width - 3, 1, b.length - 3, spec["path"])
    awnings = ("minecraft:red_wool", "minecraft:yellow_wool",
               "minecraft:blue_wool", "minecraft:green_wool")
    for index, (x, z) in enumerate(((cx - 7, cz - 6), (cx + 7, cz - 6),
                                    (cx - 7, cz + 6), (cx + 7, cz + 6))):
        x1, x2, z1, z2 = x - 2, x + 2, z - 2, z + 2
        b.fill(x1, 1, z1, x2, 1, z2, spec["floor"])
        for px in (x1, x2):
            for pz in (z1, z2):
                for y in range(2, 5):
                    b.log(px, y, pz, spec["frame"])
        b.fill(x1, 5, z1, x2, 5, z2, awnings[index])
        b.fill(x1 + 1, 2, z1 + 1, x2 - 1, 2, z1 + 1, spec["accent"])
        b.set(x, 2, z, spec["feature"])
        _lantern(b, x, 4, z2)
    b.fill(cx - 2, 1, cz - 2, cx + 2, 1, cz + 2, spec["foundation"])
    b.set(cx, 2, cz, "minecraft:bell")
    b.set(cx, 2, cz + 1, "minecraft:barrel", facing="up", open="false")


def _build_tower_complex(b: Building, spec: dict):
    """A dominant tower with two separate service wings and a forecourt."""
    _build_tower(b, spec)
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, 2, cz - 4, 5, cz + 3, 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, b.width - 6, cz - 4, b.width - 3, cz + 3, 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 4, 1, b.length - 4, cx + 4, 1, b.length - 2, spec["path"])
    for x in (cx - 4, cx + 4):
        b.log(x, 2, b.length - 4, spec["frame"])
        _lantern(b, x, 3, b.length - 4)


def _build_lighthouse(b: Building, spec: dict):
    """Tapered beacon tower with lantern chamber, keeper's room and sea platform."""
    cx, cz = b.width // 2, b.length // 2
    b.fill(cx - 5, 0, cz - 5, cx + 5, 0, cz + 5, spec["foundation"])
    for y in range(1, 15):
        radius = 4 if y < 6 else 3 if y < 11 else 2
        b.shell(cx - radius, y, cz - radius, cx + radius, y, cz + radius, spec["wall"])
    for y in (1, 6, 11, 14):
        radius = 4 if y == 1 else 3 if y == 6 else 2
        b.fill(cx - radius + 1, y, cz - radius + 1,
               cx + radius - 1, y, cz + radius - 1, spec["floor"])
    _door(b, cx, 2, cz - 4, spec["frame"])
    for y in (5, 9, 13):
        _pane(b, cx - 4 if y == 5 else cx - 3 if y == 9 else cx - 2, y, cz)
        _pane(b, cx + 4 if y == 5 else cx + 3 if y == 9 else cx + 2, y, cz)
    b.fill(cx - 2, 15, cz - 2, cx + 2, 15, cz + 2, spec["floor"])
    b.shell(cx - 2, 16, cz - 2, cx + 2, 17, cz + 2, "minecraft:glass")
    b.set(cx, 16, cz, spec["feature"])
    b.gable_roof(cx - 3, cx + 3, cz - 3, cz + 3, 18, 2, spec["roof"])
    _add_house(b, 2, cz - 3, 6, cz + 3, 1, 4,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 7, 1, cz + 6, cx + 7, 1, b.length - 2, spec["floor"])
    for x in range(cx - 6, cx + 7, 3):
        b.log(x, 0, b.length - 3, spec["frame"])


def _build_radial_compound(b: Building, spec: dict):
    """Central communal lodge with four spoke paths and separately fenced pens."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 4, cz - 4, cx + 4, cz + 4, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    pens = (
        (2, 2, cx - 6, cz - 6),
        (cx + 6, 2, b.width - 3, cz - 6),
        (2, cz + 6, cx - 6, b.length - 3),
        (cx + 6, cz + 6, b.width - 3, b.length - 3),
    )
    for index, (x1, z1, x2, z2) in enumerate(pens):
        _add_fence_pen(b, x1, z1, x2, z2, spec["frame"])
        px, pz = (x1 + x2) // 2, (z1 + z2) // 2
        for dx in (-1, 1):
            for dz in (-1, 1):
                b.log(px + dx, 2, pz + dz, spec["frame"])
                b.log(px + dx, 3, pz + dz, spec["frame"])
        b.fill(px - 2, 4, pz - 2, px + 2, 4, pz + 2,
               "minecraft:hay_block" if index % 2 == 0 else _roof_planks(spec))
        b.set(px, 2, pz, spec["feature"])
    b.fill(cx - 1, 1, 2, cx + 1, 1, cz - 5, spec["path"])
    b.fill(cx - 1, 1, cz + 5, cx + 1, 1, b.length - 3, spec["path"])
    b.fill(2, 1, cz - 1, cx - 5, 1, cz + 1, spec["path"])
    b.fill(cx + 5, 1, cz - 1, b.width - 3, 1, cz + 1, spec["path"])


def _build_windmill_grain(b: Building, spec: dict):
    """Vertical-sailed windmill, grain hall and a dedicated loading apron."""
    cx, cz = b.width // 2, b.length // 2
    b.fill(cx - 4, 0, cz - 4, cx + 4, 0, cz + 4, spec["foundation"])
    b.fill(cx - 3, 1, cz - 3, cx + 3, 1, cz + 3, spec["floor"])
    b.shell(cx - 3, 2, cz - 3, cx + 3, 12, cz + 3, spec["wall"])
    for x in (cx - 3, cx + 3):
        for z in (cz - 3, cz + 3):
            for y in range(2, 13):
                b.log(x, y, z, spec["frame"])
    _door(b, cx, 2, cz - 3, spec["frame"])
    b.gable_roof(cx - 4, cx + 4, cz - 4, cz + 4, 13, 2, spec["roof"])
    blade_z = cz - 5
    for x in range(cx - 8, cx + 9):
        b.log(x, 11, blade_z, spec["frame"], axis="x")
    for y in range(4, 18):
        b.log(cx, y, blade_z, spec["frame"])
    b.fill(cx - 6, 9, blade_z - 1, cx - 4, 10, blade_z - 1, "minecraft:white_wool")
    b.fill(cx + 4, 12, blade_z - 1, cx + 6, 13, blade_z - 1, "minecraft:white_wool")
    b.fill(cx - 1, 4, blade_z - 1, cx + 1, 6, blade_z - 1, "minecraft:white_wool")
    b.fill(cx - 1, 15, blade_z - 1, cx + 1, 17, blade_z - 1, "minecraft:white_wool")
    _add_house(b, 2, cz + 4, 8, min(b.length - 3, cz + 10), 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(2, 1, b.length - 3, b.width - 3, 1, b.length - 3, spec["path"])
    b.set(cx + 5, 2, cz + 5, "minecraft:barrel", facing="up", open="false")


def _build_stable_courtyard(b: Building, spec: dict):
    """Guest lodge and stable wing face a shared hitching yard and paddock."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, 2, 2, cx - 2, cz + 2, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, cx + 3, 2, b.width - 3, cz + 2, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(3, 1, cz + 4, b.width - 4, 1, b.length - 3, spec["path"])
    _add_fence_pen(b, 3, cz + 6, b.width - 4, b.length - 3, spec["frame"])
    stable_x1, stable_x2 = cx + 5, b.width - 5
    for x in range(stable_x1 + 1, stable_x2, 3):
        b.fill(x, 2, 4, x, 4, cz, spec["wall"])
        b.set(x, 2, 4, "minecraft:air")
    for x in range(4, b.width - 4, 4):
        b.log(x, 2, cz + 5, spec["frame"])
        b.log(x, 3, cz + 5, spec["frame"])
        b.set(x, 2, cz + 7, "minecraft:hay_block")
    for x in (cx - 3, cx + 3):
        b.log(x, 2, cz + 4, spec["frame"])
        b.log(x, 3, cz + 4, spec["frame"])
        b.set(x, 2, cz + 5, "minecraft:hay_block")


def _build_cloister(b: Building, spec: dict):
    """Teaching or monastic hall attached to a four-sided open cloister."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 6, 2, cx + 6, 8, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    x1, x2, z1, z2 = cx - 7, cx + 7, cz - 1, b.length - 4
    b.fill(x1 + 1, 1, z1 + 1, x2 - 1, 1, z2 - 1, spec["path"])
    for z in range(z1, z2 + 1, 3):
        for x in (x1, x2):
            for y in range(2, 6):
                b.log(x, y, z, spec["frame"])
    for x in range(x1, x2 + 1, 3):
        for z in (z1, z2):
            for y in range(2, 6):
                b.log(x, y, z, spec["frame"])
    b.fill(x1, 6, z1, x2, 6, z1, spec["roof"] + "_planks")
    b.fill(x1, 6, z2, x2, 6, z2, spec["roof"] + "_planks")
    b.fill(x1, 6, z1, x1, 6, z2, spec["roof"] + "_planks")
    b.fill(x2, 6, z1, x2, 6, z2, spec["roof"] + "_planks")
    b.fill(cx - 2, 1, cz + 3, cx + 2, 1, cz + 6, spec["foundation"])
    b.set(cx, 2, cz + 4, "minecraft:flower_pot")
    _lantern(b, x1, 7, z1)
    _lantern(b, x2, 7, z2)


def _build_civic_hall(b: Building, spec: dict):
    """Symmetrical public hall with columned portico, raised hearing room and steps."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 8, cz - 4, cx + 8, cz + 4, 1, 7,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    for x in range(cx - 7, cx + 8, 3):
        for y in range(2, 8):
            b.log(x, y, cz - 6, spec["frame"])
        b.log(x, 8, cz - 6, spec["frame"], axis="x")
    for step in range(4):
        b.fill(cx - 7 + step, 1 + step, 0, cx + 7 - step, 1 + step, 1, spec["foundation"])
    for x in (cx - 6, cx + 6):
        _add_house(b, x - 2, cz + 6, x + 2, min(b.length - 3, cz + 10), 1, 4,
                   spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 4, 1, cz + 5, cx + 4, 1, cz + 7, spec["path"])


def _build_stilt_granary(b: Building, spec: dict):
    """Raised grain store with pile foundations, ventilated floor and loading ramp."""
    cx, cz = b.width // 2, b.length // 2
    for x in (cx - 5, cx + 5):
        for z in (cz - 5, cz + 5):
            for y in range(0, 5):
                b.log(x, y, z, spec["frame"])
            b.log(x, 2, z, spec["frame"], axis="x")
    b.fill(cx - 6, 4, cz - 6, cx + 6, 4, cz + 6, spec["floor"])
    _add_house(b, cx - 4, cz - 4, cx + 4, cz + 4, 5, 10,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    for step in range(4):
        b.stair(cx, 3 - step // 2, cz + 7 + step, "spruce", "north")
    for x in (cx - 8, cx + 8):
        b.fill(x - 1, 1, cz - 2, x + 1, 1, cz + 2, spec["foundation"])
        b.set(x, 2, cz, "minecraft:barrel", facing="up", open="false")
    _lantern(b, cx - 5, 6, cz)
    _lantern(b, cx + 5, 6, cz)


def _build_workshop_court(b: Building, spec: dict):
    """Three work buildings around a shared fabrication and drying yard."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, cx - 6, 2, cx + 6, cz - 3, 1, 6,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, 2, cz - 1, 7, min(b.length - 3, cz + 6), 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    _add_house(b, b.width - 8, cz - 1, b.width - 3, min(b.length - 3, cz + 6), 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    b.fill(cx - 4, 1, cz - 1, cx + 4, 1, b.length - 3, spec["path"])
    for x in range(cx - 5, cx + 6, 2):
        b.log(x, 2, cz + 2, spec["frame"])
        b.log(x, 4, cz + 2, spec["frame"], axis="x")
        b.set(x, 2, cz + 4, spec["accent"])
    b.set(cx - 2, 2, cz + 6, "minecraft:crafting_table")
    b.set(cx + 2, 2, cz + 6, "minecraft:loom")
    _lantern(b, cx, 5, cz + 2)


def _build_bridge_gate(b: Building, spec: dict):
    """Bridge with two monumental entry arches and a separate toll shelter."""
    _build_bridge(b, spec)
    cx, cz = b.width // 2, b.length // 2
    for gate_x in (cx - 8, cx + 8):
        for z in (cz - 3, cz + 3):
            for y in range(5, min(b.height - 2, 10)):
                b.fill(gate_x - 1, y, z, gate_x + 1, y, z, spec["wall"])
        b.fill(gate_x - 1, min(b.height - 2, 10), cz - 3,
               gate_x + 1, min(b.height - 2, 10), cz + 3, spec["frame"])
        b.set(gate_x, 6, cz, spec["feature"])
        _lantern(b, gate_x, 7, cz - 3)
        _lantern(b, gate_x, 7, cz + 3)


def _build_irrigated_garden(b: Building, spec: dict):
    """Packing house, orchard rows and a branching water-channel layout."""
    cx, cz = b.width // 2, b.length // 2
    _add_house(b, 2, 2, cx + 1, cz - 2, 1, 5,
               spec["wall"], spec["floor"], spec["roof"], spec["frame"], spec)
    channel_x = cx + 3
    b.fill(channel_x, 1, 2, channel_x, 1, b.length - 3, "minecraft:water")
    b.fill(channel_x + 5, 1, 2, channel_x + 5, 1, b.length - 3, "minecraft:water")
    for z in range(4, b.length - 3, 5):
        b.fill(channel_x, 1, z, b.width - 3, 1, z, "minecraft:water")
    for x in range(channel_x + 2, b.width - 3, 4):
        for z in range(4, b.length - 3, 5):
            b.log(x, 2, z + 1, "oak")
            b.log(x, 3, z + 1, "oak")
            b.log(x, 4, z + 1, "oak")
            b.fill(x - 1, 5, z, x + 1, 5, z + 2, "minecraft:oak_leaves")
    b.fill(2, 1, b.length - 3, cx + 1, 1, b.length - 3, spec["path"])
    b.set(cx, 2, cz + 2, "minecraft:barrel", facing="up", open="false")


def _build_expansion_form(b: Building, spec: dict):
    form = DESIGN_FORMS[spec["name"]]
    builders = {
        "longhouse": _build_longhouse,
        "greenhouse": _build_greenhouse,
        "courtyard": _build_courtyard,
        "windcatcher_courtyard": _build_windcatcher_courtyard,
        "garden_pavilion": _build_garden_pavilion,
        "stilt_village": _build_stilt_village,
        "terraced": _build_terraced,
        "industrial_yard": _build_industrial_yard,
        "dockyard": _build_dockyard,
        "market_square": _build_market_square,
        "tower_complex": _build_tower_complex,
        "lighthouse": _build_lighthouse,
        "radial_compound": _build_radial_compound,
        "windmill_grain": _build_windmill_grain,
        "stable_courtyard": _build_stable_courtyard,
        "cloister": _build_cloister,
        "civic_hall": _build_civic_hall,
        "stilt_granary": _build_stilt_granary,
        "workshop_court": _build_workshop_court,
        "bridge_gate": _build_bridge_gate,
        "irrigated_garden": _build_irrigated_garden,
        "aqueduct": _build_aqueduct,
    }
    builders[form](b, spec)


def build_expansion_design(spec: dict) -> Building:
    b = Building(spec["name"], spec["size"], spec["description"])
    cx, cz = b.width // 2, b.length // 2
    form = DESIGN_FORMS[spec["name"]]
    # Non-raised forms receive a compact foundation apron. Elevated structures
    # build their own piles and walkways instead of receiving a solid ground pad.
    if form not in ("stilt_village", "stilt_granary", "bridge_gate", "aqueduct"):
        b.fill(cx - 6, 0, cz - 6, cx + 6, 0, cz + 6, spec["foundation"])
    _build_expansion_form(b, spec)
    return b


def _factory(spec: dict):
    def build():
        return build_expansion_design(spec)
    build.__name__ = "build_" + spec["name"]
    return build


BUILDINGS = tuple(_factory(spec) for spec in EXPANSION_DESIGNS)


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
        print("EXPANSION_ARCHITECTURE_TEMPLATE", result)
    manifest_path = args.output / "expansion-architecture-manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"ORIGINAL_ARCHITECTURE_EXPANSION_OK: {len(manifest)} new structures")


if __name__ == "__main__":
    main()
