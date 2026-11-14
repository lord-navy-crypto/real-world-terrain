#!/usr/bin/env python3
"""Deterministic, ORIGINAL 16x16 pixel textures for the RealScale LOTM pilot.

No downloaded assets or third-party graphics. Standard library only.
The resource pack is generated under the Gradle build directory.
"""
from __future__ import annotations

import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path

ITEMS = [
    "lotm_star_crystal", "lotm_cursed_crystal", "lotm_grave_crystal",
    "lotm_lunar_mica", "lotm_echo_quartz", "lotm_abyssal_salt",
    "lotm_lavas_squid_essence", "lotm_nightmare_eye_shard",
    "lotm_night_owl_token", "lotm_black_spotted_frog_token",
    "lotm_empty_vial", "lotm_spirit_focus", "lotm_seer_potion",
    "lotm_mystery_pryer_potion", "lotm_sleepless_potion",
    "lotm_corpse_collector_potion", "lotm_shroudman_crystal",
    "lotm_divination_pendulum",
    "lotm_stability_sigil",
    "lotm_hornacis_goat_horn", "lotm_ironarm_spine", "lotm_four_eyed_turtle_heart", "lotm_crimson_howler_voice", "lotm_death_crow_eye", "lotm_mist_treant_root", "lotm_evil_pattern_panther_essence", "lotm_stag_beast_antler", "lotm_grey_grandmother_eye", "lotm_dream_crow_heart", "lotm_nightmare_shadow_core", "lotm_spectral_cat_paw", "lotm_spirit_quartz", "lotm_occult_catalyst", "lotm_seer_s9_characteristic", "lotm_seer_s8_characteristic", "lotm_seer_s8_potion", "lotm_seer_s7_characteristic", "lotm_seer_s7_potion", "lotm_mystery_pryer_s9_characteristic", "lotm_mystery_pryer_s8_characteristic", "lotm_mystery_pryer_s8_potion", "lotm_mystery_pryer_s7_characteristic", "lotm_mystery_pryer_s7_potion", "lotm_sleepless_s9_characteristic", "lotm_sleepless_s8_characteristic", "lotm_sleepless_s8_potion", "lotm_sleepless_s7_characteristic", "lotm_sleepless_s7_potion", "lotm_corpse_collector_s9_characteristic", "lotm_corpse_collector_s8_characteristic", "lotm_corpse_collector_s8_potion", "lotm_corpse_collector_s7_characteristic", "lotm_corpse_collector_s7_potion", "lotm_spirit_ember",
    "lotm_seer_skill_manual_1", "lotm_seer_skill_manual_2",
    "lotm_mystery_pryer_skill_manual_1", "lotm_mystery_pryer_skill_manual_2",
    "lotm_sleepless_skill_manual_1", "lotm_sleepless_skill_manual_2",
    "lotm_corpse_collector_skill_manual_1", "lotm_corpse_collector_skill_manual_2",
]
BLOCKS = [
    "lotm_star_crystal_ore", "lotm_cursed_crystal_ore",
    "lotm_grave_crystal_ore", "lotm_lunar_mica_ore",
    "lotm_echo_quartz_ore", "lotm_abyssal_salt_ore", "lotm_midnight_bloom",
    "lotm_face_rose", "lotm_soul_bellflower", "lotm_blood_rose",
    "lotm_spirit_quartz_ore", "lotm_relic_cache", "lotm_alchemy_apparatus",
]
ABILITIES = [
    "lotm_seer_primary", "lotm_seer_secondary",
    "lotm_mystery_pryer_primary", "lotm_mystery_pryer_secondary",
    "lotm_sleepless_primary", "lotm_sleepless_secondary",
    "lotm_corpse_collector_primary", "lotm_corpse_collector_secondary",
]

def _cube(origin, size, uv=(0, 0)):
    return {"origin": list(origin), "size": list(size), "uv": list(uv)}


def _bone(name, parent, pivot, origin=None, size=None, uv=(0, 0)):
    result = {"name": name, "pivot": list(pivot)}
    if parent is not None:
        result["parent"] = parent
    if origin is not None and size is not None:
        result["cubes"] = [_cube(origin, size, uv)]
    return result


# Ten original species: each has a distinct silhouette, texture palette and motion profile.
CREATURES = {
    "starlit_squid": {
        "palette": ((37, 88, 114), (74, 171, 195), (15, 37, 55), (180, 237, 232)), "pattern": "glow",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-4, 3, -4), (8, 10, 8)),
            _bone("head", "body", (0, 10, -3), (-3, 9, -6), (6, 5, 4), (16, 0)),
            _bone("tentacle_left", "root", (-3, 4, 0), (-4, -4, -1), (2, 8, 2), (0, 16)),
            _bone("tentacle_right", "root", (3, 4, 0), (2, -4, -1), (2, 8, 2), (8, 16)),
            _bone("tentacle_back_left", "root", (-2, 4, 3), (-3, -5, 3), (2, 9, 2), (16, 16)),
            _bone("tentacle_back_right", "root", (2, 4, 3), (1, -5, 3), (2, 9, 2), (24, 16)),
        ],
        "walk_bones": ["tentacle_left", "tentacle_right", "tentacle_back_left", "tentacle_back_right"],
        "attack_bones": ["head", "tentacle_left", "tentacle_right"],
    },
    "nightmare_eye": {
        "palette": ((74, 35, 92), (155, 64, 150), (25, 13, 39), (237, 154, 219)), "pattern": "eye",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-6, 2, -6), (12, 12, 12)),
            _bone("head", "body", (0, 8, -5), (-4, 6, -8), (8, 7, 3), (16, 0)),
            _bone("iris", "head", (0, 9, -8), (-3, 7, -9), (6, 6, 1), (32, 0)),
            _bone("pupil", "iris", (0, 10, -9), (-1, 8, -10), (2, 4, 1), (40, 0)),
            _bone("tendril_left", "root", (-4, 4, -2), (-5, -2, -3), (2, 7, 2), (0, 16)),
            _bone("tendril_right", "root", (4, 4, 2), (3, -3, 1), (2, 8, 2), (8, 16)),
        ],
        "walk_bones": ["tendril_left", "tendril_right"],
        "attack_bones": ["head", "pupil", "tendril_left", "tendril_right"],
    },
    "night_owl": {
        "palette": ((84, 68, 51), (184, 151, 93), (35, 32, 30), (229, 205, 151)), "pattern": "feathers",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-3, 4, -3), (6, 8, 6)),
            _bone("head", "body", (0, 12, -2), (-3, 10, -4), (6, 6, 5), (16, 0)),
            _bone("beak", "head", (0, 12, -4), (-1, 11, -6), (2, 2, 3), (32, 0)),
            _bone("wing_left", "body", (-3, 9, 0), (-7, 6, -2), (4, 8, 4), (0, 16)),
            _bone("wing_right", "body", (3, 9, 0), (3, 6, -2), (4, 8, 4), (16, 16)),
            _bone("tail", "body", (0, 5, 3), (-2, 2, 3), (4, 5, 2), (32, 16)),
            _bone("leg_left", "body", (-2, 4, 0), (-2, 0, 0), (1, 4, 1), (40, 16)),
            _bone("leg_right", "body", (2, 4, 0), (1, 0, 0), (1, 4, 1), (48, 16)),
        ],
        "walk_bones": ["wing_left", "wing_right", "leg_left", "leg_right"],
        "attack_bones": ["head", "wing_left", "wing_right"],
    },
    "black_spotted_frog": {
        "palette": ((40, 85, 49), (104, 151, 66), (17, 39, 27), (186, 220, 92)), "pattern": "spots",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 5, 0), (-4, 2, -4), (8, 5, 8)),
            _bone("head", "body", (0, 7, -3), (-4, 6, -6), (8, 5, 5), (16, 0)),
            _bone("eye_left", "head", (-3, 10, -5), (-4, 9, -6), (2, 2, 2), (32, 0)),
            _bone("eye_right", "head", (3, 10, -5), (2, 9, -6), (2, 2, 2), (40, 0)),
            _bone("leg_front_left", "body", (-3, 4, -2), (-4, 0, -3), (2, 3, 3), (0, 16)),
            _bone("leg_front_right", "body", (3, 4, -2), (2, 0, -3), (2, 3, 3), (8, 16)),
            _bone("leg_back_left", "body", (-3, 3, 2), (-5, -1, 1), (3, 3, 4), (16, 16)),
            _bone("leg_back_right", "body", (3, 3, 2), (2, -1, 1), (3, 3, 4), (24, 16)),
        ],
        "walk_bones": ["leg_front_left", "leg_front_right", "leg_back_left", "leg_back_right"],
        "attack_bones": ["head", "leg_back_left", "leg_back_right"],
    },
    "shroudman": {
        "palette": ((76, 78, 88), (157, 153, 143), (27, 28, 37), (202, 199, 180)), "pattern": "rags",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 11, 0), (-4, 4, -3), (8, 12, 6)),
            _bone("head", "body", (0, 16, -1), (-3, 14, -4), (6, 6, 6), (16, 0)),
            _bone("hood", "head", (0, 18, 0), (-5, 17, -4), (10, 4, 8), (32, 0)),
            _bone("arm_left", "body", (-4, 13, 0), (-7, 7, -2), (3, 8, 4), (0, 16)),
            _bone("arm_right", "body", (4, 13, 0), (4, 7, -2), (3, 8, 4), (16, 16)),
            _bone("robe_left", "body", (-2, 7, 0), (-4, 0, -3), (4, 7, 6), (32, 16)),
            _bone("robe_right", "body", (2, 7, 0), (0, 0, -3), (4, 7, 6), (40, 16)),
        ],
        "walk_bones": ["arm_left", "arm_right", "robe_left", "robe_right"],
        "attack_bones": ["arm_left", "arm_right", "head"],
    },
    "hornacis_goat": {
        "palette": ((126, 115, 92), (190, 177, 148), (51, 46, 38), (222, 214, 185)), "pattern": "fur",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-5, 4, -3), (10, 7, 6)),
            _bone("head", "body", (0, 12, -3), (-3, 10, -6), (6, 6, 5), (16, 0)),
            _bone("horn_left", "head", (-2, 15, -3), (-3, 14, -4), (1, 5, 1), (32, 0)),
            _bone("horn_right", "head", (2, 15, -3), (2, 14, -4), (1, 5, 1), (40, 0)),
            _bone("leg_left", "body", (-3, 5, 0), (-4, -2, -2), (2, 7, 2), (0, 16)),
            _bone("leg_right", "body", (3, 5, 0), (2, -2, -2), (2, 7, 2), (8, 16)),
            _bone("tail", "body", (0, 8, 3), (-1, 6, 3), (2, 4, 2), (16, 16)),
        ],
        "walk_bones": ["leg_left", "leg_right"],
        "attack_bones": ["head", "horn_left", "horn_right"],
    },
    "ironarm_beast": {
        "palette": ((80, 92, 103), (173, 185, 194), (28, 37, 47), (215, 163, 86)), "pattern": "plates",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 12, 0), (-6, 6, -4), (12, 12, 8)),
            _bone("head", "body", (0, 18, -2), (-3, 17, -5), (6, 6, 6), (16, 0)),
            _bone("plate", "body", (0, 14, -4), (-5, 11, -5), (10, 7, 1), (32, 0)),
            _bone("arm_left", "body", (-6, 12, 0), (-8, 5, -3), (3, 9, 6), (0, 16)),
            _bone("arm_right", "body", (6, 12, 0), (5, 5, -3), (3, 9, 6), (16, 16)),
            _bone("leg_left", "root", (-3, 6, 0), (-4, -2, -3), (4, 8, 6), (32, 16)),
            _bone("leg_right", "root", (3, 6, 0), (0, -2, -3), (4, 8, 6), (40, 16)),
        ],
        "walk_bones": ["leg_left", "leg_right", "arm_left", "arm_right"],
        "attack_bones": ["arm_left", "arm_right", "plate"],
    },
    "four_eyed_turtle": {
        "palette": ((62, 102, 71), (137, 160, 92), (25, 48, 38), (203, 188, 118)), "pattern": "shell",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 5, 0), (-5, 1, -4), (10, 4, 8)),
            _bone("shell", "body", (0, 7, 0), (-6, 4, -5), (12, 6, 10), (16, 0)),
            _bone("head", "body", (0, 6, -4), (-2, 4, -8), (4, 4, 5), (32, 0)),
            _bone("eye_front_left", "head", (-1, 7, -7), (-2, 6, -8), (1, 1, 1), (40, 0)),
            _bone("eye_front_right", "head", (1, 7, -7), (1, 6, -8), (1, 1, 1), (44, 0)),
            _bone("eye_back_left", "shell", (-3, 8, 2), (-4, 7, 3), (1, 1, 1), (48, 0)),
            _bone("eye_back_right", "shell", (3, 8, 2), (3, 7, 3), (1, 1, 1), (52, 0)),
            _bone("leg_left", "body", (-4, 3, -2), (-5, 0, -3), (3, 3, 3), (0, 16)),
            _bone("leg_right", "body", (4, 3, 2), (2, 0, 1), (3, 3, 3), (8, 16)),
        ],
        "walk_bones": ["leg_left", "leg_right"],
        "attack_bones": ["head", "shell"],
    },
    "crimson_howler": {
        "palette": ((101, 35, 46), (173, 67, 67), (34, 16, 24), (225, 143, 95)), "pattern": "stripes",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-5, 4, -3), (10, 7, 6)),
            _bone("head", "body", (0, 11, -3), (-3, 9, -6), (6, 6, 6), (16, 0)),
            _bone("jaw", "head", (0, 9, -5), (-3, 7, -7), (6, 2, 4), (32, 0)),
            _bone("ear_left", "head", (-2, 14, -2), (-3, 13, -3), (2, 3, 2), (40, 0)),
            _bone("ear_right", "head", (2, 14, -2), (1, 13, -3), (2, 3, 2), (48, 0)),
            _bone("leg_left", "body", (-3, 5, 0), (-4, -2, -2), (2, 7, 2), (0, 16)),
            _bone("leg_right", "body", (3, 5, 0), (2, -2, -2), (2, 7, 2), (8, 16)),
            _bone("tail", "body", (0, 7, 3), (-1, 5, 3), (2, 3, 7), (16, 16)),
        ],
        "walk_bones": ["leg_left", "leg_right"],
        "attack_bones": ["head", "jaw", "tail"],
    },
    "death_crow": {
        "palette": ((44, 46, 58), (115, 127, 149), (17, 19, 29), (190, 183, 211)), "pattern": "feathers",
        "bones": [
            _bone("root", None, (0, 0, 0)),
            _bone("body", "root", (0, 8, 0), (-3, 4, -3), (6, 8, 6)),
            _bone("head", "body", (0, 12, -2), (-2, 10, -4), (4, 5, 4), (16, 0)),
            _bone("beak", "head", (0, 11, -4), (-1, 10, -6), (2, 2, 3), (32, 0)),
            _bone("wing_left", "body", (-3, 9, 0), (-7, 6, -2), (4, 8, 4), (0, 16)),
            _bone("wing_right", "body", (3, 9, 0), (3, 6, -2), (4, 8, 4), (16, 16)),
            _bone("tail", "body", (0, 5, 3), (-2, 1, 3), (4, 5, 2), (32, 16)),
            _bone("leg_left", "body", (-2, 4, 0), (-2, 0, 0), (1, 4, 1), (40, 16)),
            _bone("leg_right", "body", (2, 4, 0), (1, 0, 0), (1, 4, 1), (48, 16)),
        ],
        "walk_bones": ["wing_left", "wing_right", "leg_left", "leg_right"],
        "attack_bones": ["head", "beak", "wing_left", "wing_right"],
    },
}


def creature_pixel(creature_id: str, x: int, y: int) -> tuple[int, int, int, int]:
    spec = CREATURES[creature_id]
    base, accent, dark, glow = spec["palette"]
    noise = hashbyte(f"{creature_id}/{x}/{y}")
    tx, ty = x % 16, y % 16
    color = dark if tx in (0, 15) or ty in (0, 15) else base
    if tx in (1, 2) or ty in (1, 2):
        color = accent
    pattern = spec["pattern"]
    if pattern == "glow" and (x * 3 + y * 5) % 29 < 3:
        color = glow
    elif pattern == "eye" and 12 <= ((x - 32) ** 2 + (y - 32) ** 2) ** 0.5 <= 24:
        color = accent if noise % 3 else glow
    elif pattern == "feathers" and (y + (x // 4) * 3) % 11 < 3:
        color = accent
    elif pattern == "spots" and (x * 7 + y * 11) % 37 < 4:
        color = glow if noise % 2 else dark
    elif pattern == "rags" and (x // 5) % 3 == 0:
        color = dark if noise % 4 else accent
    elif pattern == "fur" and (x * 3 + y * 5) % 23 < 5:
        color = accent
    elif pattern == "plates" and (tx in (3, 12) or ty in (3, 12)):
        color = accent
    elif pattern == "shell" and (tx in (0, 8) or ty in (0, 8)):
        color = dark
    elif pattern == "stripes" and (y + x // 3) % 9 < 3:
        color = accent
    if noise % 41 == 0:
        color = glow
    return rgba(color)


def hashbyte(value: str) -> int:
    """Stable one-byte hash; unlike hash(), this is reproducible across processes."""
    return hashlib.sha256(value.encode("utf-8")).digest()[0]


def rgba(rgb: tuple[int, int, int]) -> tuple[int, int, int, int]:
    return (rgb[0], rgb[1], rgb[2], 255)


MINERAL_TEXTURE_PALETTES = {
    "lotm_star_crystal": ((46, 55, 89), (101, 133, 213), (223, 232, 255)),
    "lotm_star_crystal_ore": ((66, 71, 96), (93, 112, 178), (207, 222, 255)),
    "lotm_cursed_crystal": ((67, 38, 74), (151, 72, 139), (245, 191, 228)),
    "lotm_cursed_crystal_ore": ((77, 61, 79), (132, 70, 122), (225, 170, 215)),
    "lotm_grave_crystal": ((49, 66, 61), (103, 146, 113), (205, 232, 198)),
    "lotm_grave_crystal_ore": ((67, 78, 72), (92, 132, 101), (197, 225, 183)),
    "lotm_lunar_mica": ((50, 65, 85), (148, 172, 213), (228, 229, 250)),
    "lotm_lunar_mica_ore": ((75, 83, 95), (125, 164, 203), (225, 238, 255)),
    "lotm_echo_quartz": ((41, 66, 73), (86, 179, 172), (205, 248, 232)),
    "lotm_echo_quartz_ore": ((75, 86, 86), (82, 152, 145), (223, 252, 237)),
    "lotm_abyssal_salt": ((38, 63, 91), (84, 151, 211), (214, 238, 253)),
    "lotm_abyssal_salt_ore": ((67, 75, 88), (74, 128, 189), (240, 245, 254)),
}


def mineral_pixel(name: str, x: int, y: int) -> tuple[int, int, int, int]:
    """Original, repeatable ore-vein texture and faceted crystal icon palettes."""
    base, vein, sparkle = MINERAL_TEXTURE_PALETTES[name]
    noise = hashbyte(f"{name}:{x // 2}:{y // 2}")
    if name.endswith("_ore"):
        line = (3 * x + 2 * y + noise // 13) % 11
        color = sparkle if line == 0 and noise % 5 == 0 else vein if line < 3 else base
        return rgba(color)
    # Faceted drop silhouette: leave air around the material icon.
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    if dx + dy > 9.0:
        return (0, 0, 0, 0)
    color = sparkle if (x + 2 * y + noise // 17) % 7 < 2 else vein if dx + dy < 6 else base
    return rgba(color)


def _manual_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    """A readable miniature book silhouette with a pathway-specific rune."""
    dark, mid, bright, paper = palette
    if x <= 2 or x >= 14 or y <= 1 or y >= 14:
        return rgba(dark)
    if x == 3:
        return rgba(mid)
    if 4 <= x <= 12 and 2 <= y <= 13:
        # Encode the eight pathway/sequence manuals with a distinct three-bit
        # page mark so each 16x16 manual icon has its own readable identity.
        manual_index = ITEMS.index(name) - ITEMS.index("lotm_seer_skill_manual_1")
        if y == 12 and x in (5, 7, 9):
            bit = (x - 5) // 2
            return rgba(bright if (manual_index & (1 << bit)) else mid)
        rune_seed = hashbyte(name)
        dx, dy = abs(x - 8), abs(y - 7)
        if (dx == 0 and dy <= 3) or (dy == 0 and dx <= 3) or (dx == dy and dx <= 2):
            return rgba(bright)
        if (x + y + rune_seed) % 9 == 0:
            return rgba(mid)
        return rgba(paper if (x + y) % 5 else bright)
    return (0, 0, 0, 0)


def _potion_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    """Small glass flask with a colored fill, neck, shoulders and stopper."""
    dark, mid, bright, paper = palette
    if y <= 1 and 6 <= x <= 9:
        return rgba(bright)
    if not ((6 <= x <= 9 and 2 <= y <= 3)
            or (4 <= x <= 11 and 4 <= y <= 13 and abs(x - 7.5) + max(0, 4 - y) <= 8)):
        return (0, 0, 0, 0)
    if x in (4, 11) or y == 13 or (y == 4 and x in (5, 10)):
        return rgba(dark)
    fill = mid if y >= 8 else paper
    if (x + y + hashbyte(name)) % 7 == 0:
        fill = bright
    return rgba(fill)


def _flora_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    """Petal, stem and leaf silhouette for botanical drops and block textures."""
    dark, mid, bright, paper = palette
    dx, dy = abs(x - 7.5), abs(y - 6.0)
    if dx <= 2 and dy <= 2 and (dx + dy <= 4):
        return rgba(bright if (x + y) % 4 == 0 else paper)
    if x == 7 and y >= 8:
        return rgba(mid)
    if (y in (9, 10) and x in (4, 5, 9, 10)) or (y == 11 and x in (3, 11)):
        return rgba(dark if y == 11 else mid)
    return (0, 0, 0, 0)


def _heart_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    dark, mid, bright, paper = palette
    shape = ((y <= 5 and (x in (4, 5, 10, 11) or (y <= 3 and x in (3, 12))))
             or (6 <= y <= 12 and abs(x - 7.5) <= (13 - y) * 0.72))
    if not shape:
        return (0, 0, 0, 0)
    if x in (4, 11) or y == 12:
        return rgba(dark)
    return rgba(bright if (x + y + hashbyte(name)) % 5 == 0 else mid)


def _horn_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    dark, mid, bright, paper = palette
    center = 13 - y // 2
    thickness = max(1, 3 - y // 6)
    if y < 2 or y > 14 or abs(x - center) > thickness:
        return (0, 0, 0, 0)
    return rgba(bright if x == center and y < 7 else mid if (x + y) % 3 else dark)


def _token_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    dark, mid, bright, paper = palette
    dx, dy = x - 7.5, y - 7.5
    distance = dx * dx + dy * dy
    if distance > 43:
        return (0, 0, 0, 0)
    if distance > 31 or (abs(dx) < 1 and abs(dy) < 4):
        return rgba(dark)
    return rgba(bright if (x + y + hashbyte(name)) % 6 == 0 else mid)


def _artifact_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    dark, mid, bright, paper = palette
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    if name.endswith("_pendulum"):
        if (dx <= 1 and 1 <= y <= 8) or (dy <= 2 and 5 <= x <= 10 and y >= 9):
            return rgba(bright if y in (1, 13) else mid)
        return (0, 0, 0, 0)
    if name.endswith("_sigil"):
        if 3.5 <= dx + dy <= 6.5 or (dx <= 1 and dy <= 1):
            return rgba(bright if (x + y) % 3 == 0 else mid)
        return (0, 0, 0, 0)
    if dx <= 4 and dy <= 4 and (dx == 0 or dy == 0 or dx == dy):
        return rgba(bright if (x + y) % 3 == 0 else mid)
    return (0, 0, 0, 0)


def _ability_pixel(name: str, x: int, y: int, palette) -> tuple[int, int, int, int]:
    dark, mid, bright, paper = palette
    seed = hashbyte(name)
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    if dx + dy > 8:
        return (0, 0, 0, 0)
    if dx == dy or dx == 0 or dy == 0:
        return rgba(bright)
    if (x * 3 + y * 5 + seed) % 7 < 2:
        return rgba(mid)
    return rgba(dark)


def pixel(name: str, x: int, y: int) -> tuple[int, int, int, int]:
    """Deterministic original pixel art with item silhouettes and distinct mineral veins."""
    if name in MINERAL_TEXTURE_PALETTES:
        return mineral_pixel(name, x, y)
    seed = hashbyte(name)
    palettes = (
        ((34, 44, 58), (77, 117, 142), (161, 204, 216), (216, 232, 224)),
        ((52, 38, 58), (111, 68, 112), (184, 119, 177), (233, 191, 218)),
        ((38, 54, 42), (77, 111, 69), (145, 172, 101), (216, 221, 163)),
        ((62, 48, 34), (126, 92, 52), (190, 149, 82), (231, 205, 145)),
    )
    palette = palettes[seed % len(palettes)]
    if "skill_manual" in name:
        return _manual_pixel(name, x, y, palette)
    if name.endswith("_potion") or name.endswith("_vial"):
        return _potion_pixel(name, x, y, palette)
    if any(part in name for part in ("bloom", "rose", "bellflower")):
        return _flora_pixel(name, x, y, palette)
    if "ability" in name or name.endswith("_primary") or name.endswith("_secondary"):
        return _ability_pixel(name, x, y, palette)
    if "heart" in name:
        return _heart_pixel(name, x, y, palette)
    if "horn" in name or "antler" in name:
        return _horn_pixel(name, x, y, palette)
    if "token" in name:
        return _token_pixel(name, x, y, palette)
    if any(part in name for part in ("focus", "pendulum", "sigil", "catalyst")):
        return _artifact_pixel(name, x, y, palette)
    if "eye" in name:
        dark, mid, bright, paper = palette
        dx, dy = abs(x - 7.5), abs(y - 7.5)
        if dx / 6.0 + dy / 3.5 > 1.0:
            return (0, 0, 0, 0)
        if dx <= 1 and dy <= 1:
            return rgba(dark)
        return rgba(bright if dy <= 1 else mid)
    if "spine" in name or "root" in name:
        if abs(x - 7.5) <= 1 or (y % 4 == 0 and 4 <= x <= 11):
            return rgba(palette[2] if y % 4 == 0 else palette[1])
        return (0, 0, 0, 0)
    if "essence" in name or "shard" in name or "crystal" in name or "quartz" in name or "ember" in name:
        dx, dy = abs(x - 7.5), abs(y - 7.5)
        if dx + dy > 8:
            return (0, 0, 0, 0)
        return rgba(palette[2] if (x + y + seed) % 5 == 0 else palette[1] if dx + dy < 5 else palette[0])
    if "apparatus" in name:
        if 2 <= y <= 5 and 3 <= x <= 12:
            return rgba(palette[2] if y == 2 else palette[1])
        if 6 <= y <= 13 and (x in (3, 12) or y in (6, 13)):
            return rgba(palette[0])
        if 7 <= y <= 12 and 4 <= x <= 11:
            return rgba(palette[1] if (x + y) % 4 else palette[2])
        return (0, 0, 0, 0)
    if "cache" in name:
        seam = y in (3, 7, 11) or (x + 4) % 8 == 0
        return rgba(palette[0] if seam else palette[1] if (x + y) % 5 else palette[2])
    tile = (x // 4 + y // 4 + seed) % 4
    border = x in (0, 15) or y in (0, 15)
    diagonal = (x + y + seed) % 7 == 0
    return rgba(palette[0] if border else palette[3] if diagonal else palette[tile])


def _png_bytes(width: int, height: int, pixel_fn) -> bytes:
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(
            ">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(
        b"\x00" + bytes(channel for x in range(width) for channel in pixel_fn(x, y))
        for y in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, level=9))
            + chunk(b"IEND", b""))


def _geometry_payload(creature_id: str, spec: dict) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.realscale.lotm_{creature_id}",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 3.5,
                "visible_bounds_height": 3.0,
                "visible_bounds_offset": [0, 1.0, 0],
            },
            "bones": spec["bones"],
        }],
    }


def _animation_payload(creature_id: str, spec: dict) -> dict:
    prefix = f"animation.{creature_id}."
    # Each species uses its own signature bones and a motion axis suited to its silhouette.
    special_poses = {
        "glow": (0, 0, 28),
        "eye": (0, 22, 0),
        "feathers": (0, 0, 36),
        "spots": (30, 0, 0),
        "rags": (0, 0, 32),
        "fur": (0, 0, 28),
        "plates": (0, 0, 22),
        "shell": (0, 0, 18),
        "stripes": (0, 0, 30),
    }
    special_pose = special_poses.get(spec["pattern"], (0, 0, 24))
    walk_tracks = {}
    for index, bone_name in enumerate(spec["walk_bones"]):
        swing = 20 if index % 2 == 0 else -20
        walk_tracks[bone_name] = {
            "rotation": {
                "0.0": [swing, 0, 0],
                "0.4": [-swing, 0, 0],
                "0.8": [swing, 0, 0],
            }
        }
    attack_tracks = {}
    for index, bone_name in enumerate(spec["attack_bones"]):
        swing = -26 if index % 2 == 0 else 26
        attack_tracks[bone_name] = {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.18": [swing, 0, 0],
                "0.45": [0, 0, 0],
            }
        }
    special_tracks = {}
    for index, bone_name in enumerate(spec["attack_bones"]):
        direction = 1 if index % 2 == 0 else -1
        peak = [component * direction for component in special_pose]
        settle = [round(component * direction * -0.35, 2) for component in special_pose]
        special_tracks[bone_name] = {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.16": peak,
                "0.38": settle,
                "0.62": [0, 0, 0],
            }
        }
    return {
        "format_version": "1.8.0",
        "animations": {
            prefix + "idle": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "body": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 2, 0], "2.0": [0, 0, 0]}},
                    "head": {"rotation": {"0.0": [0, 0, 0], "1.0": [2, 0, 0], "2.0": [0, 0, 0]}},
                },
            },
            prefix + "walk": {
                "loop": True,
                "animation_length": 0.8,
                "bones": walk_tracks,
            },
            prefix + "attack": {
                "loop": False,
                "animation_length": 0.45,
                "bones": attack_tracks,
            },
            prefix + "special": {
                "loop": False,
                "animation_length": 0.62,
                "bones": special_tracks,
            },
            prefix + "hurt": {
                "loop": False,
                "animation_length": 0.35,
                "bones": {
                    "body": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, 0, 10], "0.35": [0, 0, 0]}}
                },
            },
            prefix + "death": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "root": {
                        "rotation": {"0.0": [0, 0, 0], "0.9": [0, 0, 78]},
                        "position": {"0.0": [0, 0, 0], "0.9": [0, -4, 0]},
                    }
                },
            },
        },
    }


def generate_creature_assets(output: Path) -> None:
    assets = output / "assets" / "realscale"
    texture_dir = assets / "textures" / "entity"
    geometry_dir = assets / "geo"
    animation_dir = assets / "animations"
    for directory in (texture_dir, geometry_dir, animation_dir):
        directory.mkdir(parents=True, exist_ok=True)

    for creature_id, spec in CREATURES.items():
        (texture_dir / f"lotm_{creature_id}.png").write_bytes(
            _png_bytes(64, 64, lambda x, y, cid=creature_id: creature_pixel(cid, x, y)))
        (geometry_dir / f"lotm_{creature_id}.geo.json").write_text(
            json.dumps(_geometry_payload(creature_id, spec), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        (animation_dir / f"lotm_{creature_id}.animation.json").write_text(
            json.dumps(_animation_payload(creature_id, spec), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


def png_bytes(name: str) -> bytes:
    return _png_bytes(16, 16, lambda x, y: pixel(name, x, y))


def generate(output: Path) -> None:
    for kind, names in (("item", ITEMS), ("block", BLOCKS), ("ability", ABILITIES)):
        destination = output / "assets" / "realscale" / "textures" / kind
        destination.mkdir(parents=True, exist_ok=True)
        for name in names:
            (destination / f"{name}.png").write_bytes(png_bytes(name))
    generate_creature_assets(output)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate_lotm_visuals.py OUTPUT_RESOURCE_ROOT")
    generate(Path(sys.argv[1]))
