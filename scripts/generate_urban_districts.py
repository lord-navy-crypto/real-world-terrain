#!/usr/bin/env python3
"""Original RealScale city-quarter building layouts for Minecraft 1.21.1.

Four 48x48 road-connected city sectors, each with multiple distinct buildings,
accessible tower stairwells (ladder cores), public space, footpaths and street
lighting. All geometry, block states and byte ordering are deterministic.

No third-party schematics, game runtime network access or external packages.
The four templates are opt-in through a separate built-in city datapack.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from generate_original_architecture import Building

ROOT = Path(__file__).resolve().parent
PROFILE_PATH = ROOT / "urban_district_profiles.json"
WIDTH = LENGTH = 48

# One sector covers a contiguous 3x3-chunk footprint. Cardinal arterials
# deliberately reach all four edges for later aligned multi-sector city builds.
PARCELS = ((2, 2, 17, 17), (30, 2, 45, 17),
           (2, 30, 17, 45), (30, 30, 45, 45))
MATERIALS = (
    ("minecraft:white_concrete", "minecraft:light_blue_stained_glass", "minecraft:polished_andesite"),
    ("minecraft:light_gray_concrete", "minecraft:gray_stained_glass", "minecraft:stone_bricks"),
    ("minecraft:smooth_sandstone", "minecraft:cyan_stained_glass", "minecraft:smooth_sandstone"),
    ("minecraft:gray_concrete", "minecraft:blue_stained_glass", "minecraft:polished_blackstone_bricks"),
)
# Each row is one self-contained *district* with four distinct city parcels.
# A few towers are deliberately significantly taller than vanilla villages.
HEIGHTS = {
    "commercial_skyscrapers": (94, 72, 53, 61),
    "civic_centre": (58, 36, 0, 42),
    "residential_quarter": (37, 43, 30, 34),
    "industrial_edge": (17, 21, 34, 15),
}
DESCRIPTIONS = {
    "commercial_skyscrapers": "Original modern skyline with a 94-block skyscraper, offices, shops and an enclosed skybridge.",
    "civic_centre": "Original civic square with municipal towers, a park, plaza, and connected avenues.",
    "residential_quarter": "Original dense residential district with four distinct furnished multi-storey blocks.",
    "industrial_edge": "Original industrial city edge with warehouses, a tall utility tower and workshops.",
}


def door(b, x, z, facade="north"):
    b.set(x, 1, z, "minecraft:oak_door", facing=facade, half="lower",
          hinge="left", open="false", powered="false")
    b.set(x, 2, z, "minecraft:oak_door", facing=facade, half="upper",
          hinge="left", open="false", powered="false")


def city_streets(b):
    # An avenue in BOTH cardinal directions. When sectors are tiled at
    # 48-block offsets, traffic/sidewalk connections remain flush at seams.
    b.fill(0, 0, 0, WIDTH - 1, 0, LENGTH - 1, "minecraft:stone_bricks")
    for x in range(WIDTH):
        for z in range(LENGTH):
            if 21 <= x <= 26 or 21 <= z <= 26:
                b.set(x, 0, z, "minecraft:gray_concrete")
            elif 18 <= x <= 29 or 18 <= z <= 29:
                b.set(x, 0, z, "minecraft:smooth_stone")
            else:
                b.set(x, 0, z, "minecraft:grass_block", snowy="false")
    for pos in range(WIDTH):
        # Dashed lane dividers and pedestrian crosswalks at 3-block spacing.
        if pos % 6 < 3:
            for x in (23, 24):
                b.set(x, 0, pos, "minecraft:yellow_concrete")
            for z in (23, 24):
                b.set(pos, 0, z, "minecraft:yellow_concrete")
    for x, z in ((19,19), (28,19), (19,28), (28,28)):
        for y in range(1,6):
            b.set(x,y,z,"minecraft:iron_bars")
        b.set(x,6,z,"minecraft:sea_lantern")
    for x in (20,27):
        for z in (6, 13, 34, 41):
            b.set(x,1,z,"minecraft:stone_brick_wall")


def city_tower(b, box, top, palette_idx, *, apartments=False):
    """A roofed, illuminated, internally floored tower with a usable ladder core."""
    x1,z1,x2,z2=box
    x1+=1;z1+=1;x2-=1;z2-=1
    wall, glass, flooring = MATERIALS[palette_idx]
    b.fill(x1,0,z1,x2,0,z2,"minecraft:polished_andesite")
    b.shell(x1,1,z1,x2,top-2,z2,wall)
    for y in range(1,top-1):
        if y == 1 or (y-1)%6 == 0:
            b.fill(x1+1,y,z1+1,x2-1,y,z2-1,flooring)
        if y < 3 or y >= top-3 or (y-1)%6==0:
            continue
        for x in range(x1+2,x2-1,2):
            b.set(x,y,z1,glass)
            b.set(x,y,z2,glass)
        for z in range(z1+2,z2-1,2):
            b.set(x1,y,z,glass)
            b.set(x2,y,z,glass)
    b.fill(x1,top-1,z1,x2,top-1,z2,wall)
    b.fill(x1+2,top,z1+2,x2-2,top,z2-2,"minecraft:polished_andesite")
    # Roof railing around accessible maintenance deck.
    for x in range(x1+1,x2):
        b.set(x,top,z1+1,"minecraft:iron_bars")
        b.set(x,top,z2-1,"minecraft:iron_bars")
    for z in range(z1+1,z2):
        b.set(x1+1,top,z,"minecraft:iron_bars")
        b.set(x2-1,top,z,"minecraft:iron_bars")
    entry=(x1+x2)//2
    # Each building faces the central east-west avenue. North-side
    # parcels open south; south-side parcels open north. A short paved
    # pedestrian link reaches the 3-block sidewalk instead of grass.
    if z1<24:
        door(b,entry,z2,"south")
        b.fill(entry,0,z2+1,entry,0,20,"minecraft:smooth_stone")
    else:
        door(b,entry,z1,"north")
        b.fill(entry,0,27,entry,0,z1-1,"minecraft:smooth_stone")
    # Ladder is supported by z1 wall and traverses every internal floor.
    for y in range(2,top):
        b.set(x1+2,y,z1+1,"minecraft:ladder", facing="north", waterlogged="false")
    for y in range(2,top-2,6):
        b.set(x2-2,y,z2-2,"minecraft:sea_lantern")
    if apartments:
        for y in range(2,top-5,6):
            b.set(x1+4,y,z2-3,"minecraft:crafting_table")
            b.set(x1+5,y,z2-3,"minecraft:barrel",facing="up",open="false")
    # Distinctive rooftop beacons without entities or ticking block data.
    b.set((x1+x2)//2,top+1,(z1+z2)//2,"minecraft:sea_lantern")


def civic_park(b, box, fountain=False):
    x1,z1,x2,z2=box
    b.fill(x1,0,z1,x2,0,z2,"minecraft:grass_block",snowy="false")
    cx,cz=(x1+x2)//2,(z1+z2)//2
    b.fill(cx-1,0,z1,cx+1,0,z2,"minecraft:stone_bricks")
    b.fill(x1,0,cz-1,x2,0,cz+1,"minecraft:stone_bricks")
    if fountain:
        b.fill(cx-2,0,cz-2,cx+2,0,cz+2,"minecraft:polished_andesite")
        # Surround water at the SAME Y as its sources; a floor-only basin
        # spills into the streets when fluid ticks begin in a live world.
        for bx in range(cx-2,cx+3):
            for bz in range(cz-2,cz+3):
                if bx in (cx-2,cx+2) or bz in (cz-2,cz+2):
                    b.set(bx,1,bz,"minecraft:stone_bricks")
        b.fill(cx-1,1,cz-1,cx+1,1,cz+1,"minecraft:water",level="0")
        b.set(cx,2,cz,"minecraft:sea_lantern")
    else:
        for x,z in ((x1+3,z1+3),(x2-3,z2-3)):
            for y in range(1,5):
                b.set(x,y,z,"minecraft:oak_log",axis="y")
            b.fill(x-2,4,z-2,x+2,5,z+2,"minecraft:oak_leaves",
                   distance="1",persistent="true",waterlogged="false")
    for x,z in ((x1+1,cz),(x2-1,cz)):
        b.set(x,1,z,"minecraft:lantern",hanging="false")


def draw_workshop(b, box, top, material_index):
    x1,z1,x2,z2=box
    x1+=1;z1+=1;x2-=1;z2-=1
    material,glass,_=MATERIALS[material_index]
    b.fill(x1,0,z1,x2,0,z2,"minecraft:stone_bricks")
    b.shell(x1,1,z1,x2,top-2,z2,material)
    for x in range(x1+2,x2-1,3):
        for z in (z1,z2):
            for y in (4,5):
                b.set(x,y,z,glass)
    for z in range(z1+2,z2-1,3):
        for x in (x1,x2):
            b.set(x,4,z,glass)
    b.fill(x1,top-1,z1,x2,top-1,z2,"minecraft:gray_concrete")
    entry=(x1+x2)//2
    if z1<24:
        door(b,entry,z2,"south")
        b.fill(entry,0,z2+1,entry,0,20,"minecraft:smooth_stone")
    else:
        door(b,entry,z1,"north")
        b.fill(entry,0,27,entry,0,z1-1,"minecraft:smooth_stone")
    for x,z in ((x1+3,z1+3),(x2-3,z2-3)):
        b.set(x,1,z,"minecraft:furnace",facing="north",lit="false")
    for y in range(top,top+3):
        b.set(x2-2,y,z2-2,"minecraft:stone_bricks")


def skybridge(b):
    # A weatherproof crossover connecting two skyscraper second-floor lobbies.
    for x in range(17,31):
        for z in range(7,11):
            b.set(x,30,z,"minecraft:polished_andesite")
            b.set(x,34,z,"minecraft:light_gray_concrete")
            for y in range(31,34):
                if z in (7,10):
                    b.set(x,y,z,"minecraft:light_blue_stained_glass")
    for x in (17,30):
        for z in range(8,10):
            for y in range(31,34):
                b.set(x,y,z,"minecraft:air")


def build_district(name):
    if name not in HEIGHTS:
        raise ValueError("Unknown urban profile: "+name)
    levels=HEIGHTS[name]
    height=max(levels)+4
    b=Building(name,(WIDTH,height,LENGTH),DESCRIPTIONS[name])
    city_streets(b)
    for i,(parcel,top) in enumerate(zip(PARCELS,levels)):
        if top==0:
            civic_park(b,parcel,fountain=True)
        elif name=="industrial_edge" and i!=2:
            draw_workshop(b,parcel,top,i)
        else:
            city_tower(b,parcel,top,i,apartments=name=="residential_quarter")
    if name=="commercial_skyscrapers":
        skybridge(b)
    if name=="civic_centre":
        # A small public square faces the larger civic gardens.
        for x in range(20,28):
            for z in range(18,21):
                b.set(x,0,z,"minecraft:polished_andesite")
    return b


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    profile=json.loads(PROFILE_PATH.read_text(encoding="utf-8"))
    names=[item["id"] for item in profile["districts"]]
    if len(names)!=4 or len(set(names))!=4 or set(names)!=set(HEIGHTS):
        raise SystemExit("Expected exactly four distinct urban district profiles")
    target=args.output/"data/realscale/structure/urban"
    manifest=[]
    for name in names:
        building=build_district(name)
        built=building.write(target/(name+".nbt"))
        manifest.append(built)
        print("REALSCALE_URBAN_DISTRICT_TEMPLATE",name,built["size"],
              "non_air",sum(v[0]!="minecraft:air" for v in building.blocks.values()),
              flush=True)
    (args.output/"urban-district-manifest.json").write_text(
        json.dumps(manifest,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("REALSCALE_URBAN_DISTRICTS_BUILT",len(manifest),flush=True)


if __name__=="__main__":
    main()
