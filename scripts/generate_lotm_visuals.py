#!/usr/bin/env python3
"""Deterministic, ORIGINAL 16x16 pixel textures for the RealScale LOTM pilot.

No downloaded assets or third-party graphics. Standard library only.
The resource pack is generated under the Gradle build directory.
"""
from __future__ import annotations

import hashlib
import struct
import sys
import zlib
from pathlib import Path

ITEMS = [
    "lotm_star_crystal", "lotm_cursed_crystal", "lotm_grave_crystal",
    "lotm_lavas_squid_essence", "lotm_nightmare_eye_shard",
    "lotm_night_owl_token", "lotm_black_spotted_frog_token",
    "lotm_empty_vial", "lotm_spirit_focus", "lotm_seer_potion",
    "lotm_mystery_pryer_potion", "lotm_sleepless_potion",
    "lotm_corpse_collector_potion", "lotm_shroudman_crystal",
    "lotm_divination_pendulum",
]
BLOCKS = [
    "lotm_star_crystal_ore", "lotm_cursed_crystal_ore",
    "lotm_grave_crystal_ore", "lotm_midnight_bloom",
]
PALETTE = [
    (86, 170, 225), (149, 77, 178), (115, 156, 174),
    (65, 160, 173), (177, 99, 182), (160, 169, 103),
    (69, 175, 105), (120, 170, 182), (110, 195, 185),
    (70, 122, 212), (174, 83, 180), (51, 63, 150),
    (92, 119, 131), (143, 166, 183), (202, 183, 119),
]
TRANSPARENT = (0, 0, 0, 0)


def rgba(rgb: tuple[int, int, int], alpha: int = 255) -> tuple[int, int, int, int]:
    return (*rgb, alpha)


def hashbyte(text: str) -> int:
    return hashlib.sha256(text.encode("utf-8")).digest()[0]


def pixel(name: str, x: int, y: int) -> tuple[int, int, int, int]:
    key = name if name in ITEMS else name.removesuffix("_ore")
    base = PALETTE[ITEMS.index(key)] if key in ITEMS else (136, 81, 180)
    light = tuple(min(255, v + 67) for v in base)
    noise = hashbyte(f"{name}/{x}/{y}")
    ox, oy = x - 7.5, y - 7.5

    if name.endswith("_ore"):
        stone = (85 + noise % 17, 88 + noise % 19, 94 + noise % 19)
        if abs(x - 5) + abs(y - 6) <= 4 or abs(x - 12) + abs(y - 12) <= 3:
            return rgba(light if noise % 4 == 0 else base)
        return rgba(stone)

    if name == "lotm_midnight_bloom":
        if y >= 7 and x in (7, 8):
            return (61, 125, 75, 255)
        if y in (10, 11) and x in (5, 6, 9, 10):
            return (84, 153, 94, 255)
        if abs(ox) + abs(y - 4) <= 4:
            return rgba((230, 191, 242) if noise % 6 == 0 else (111, 67, 168))
        return TRANSPARENT

    if name.endswith("_potion") or name == "lotm_empty_vial":
        neck = 6 <= x <= 9 and 2 <= y <= 6
        bottle = 4 <= x <= 11 and 6 <= y <= 14
        if not (neck or bottle):
            return TRANSPARENT
        if x in (4, 11) or y in (2, 14):
            return (189, 217, 228, 255)
        if name.endswith("_potion") and y >= 9:
            return rgba(light if y == 9 or noise % 9 == 0 else base)
        return (145, 199, 215, 75)

    if name == "lotm_divination_pendulum":
        if y <= 7 and x in (7, 8):
            return (214, 190, 131, 255)
        if 8 <= y <= 14 and abs(x - 7.5) <= (14 - y) // 2:
            return rgba(light if y == 10 else base)
        return TRANSPARENT

    if name == "lotm_spirit_focus":
        distance = ox * ox + oy * oy
        if 17 <= distance <= 37:
            return rgba(light if noise % 4 == 0 else base)
        if (abs(ox) < 0.7 or abs(oy) < 0.7) and abs(ox) + abs(oy) < 6:
            return rgba(light)
        return TRANSPARENT

    # Distinct original crystal / essence silhouettes.
    seed = hashbyte(name)
    shape = seed % 4
    inside = (
        abs(ox) + abs(oy) <= 7 if shape == 0 else
        ox * ox + oy * oy <= 34 if shape == 1 else
        abs(ox) < 5 and abs(oy) < 5 if shape == 2 else
        abs(ox) < 4 and abs(oy) < 6
    )
    if not inside:
        return TRANSPARENT
    return rgba(light if noise % 7 == 0 or (x == 6 and y < 7) else base)


def png_bytes(name: str) -> bytes:
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(
            ">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    raw = b"".join(
        b"\x00" + bytes(channel for x in range(16)
                          for channel in pixel(name, x, y))
        for y in range(16))
    ihdr = struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, level=9))
            + chunk(b"IEND", b""))


def generate(output: Path) -> None:
    for kind, names in (("item", ITEMS), ("block", BLOCKS)):
        destination = output / "assets" / "realscale" / "textures" / kind
        destination.mkdir(parents=True, exist_ok=True)
        for name in names:
            (destination / f"{name}.png").write_bytes(png_bytes(name))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate_lotm_visuals.py OUTPUT_RESOURCE_ROOT")
    generate(Path(sys.argv[1]))
