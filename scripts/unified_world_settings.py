"""Immutable, opt-in cross-realm settings shared by Sacred, Measured and Terrain Lab.

The source world preset is copied, never edited. Only a NEW world preset is
written into an exported datapack. Invasion is a biome-source wrapper stored
in that world's level.dat, not a mutable global/client config switch.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path

PRESETS = {
    "sacred": "tva_sacred_timeline_three_realms",
    "measured": "tva_measured_three_realms",
}
PRESET_DIR = Path("data/realscale/worldgen/world_preset")
DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")


def read_json(root: Path, relative: Path) -> dict:
    return json.loads((root / relative).read_text(encoding="utf-8"))


def invasion_preset(source_preset: dict) -> dict:
    """Wrap three existing biome sources without changing their noise settings."""
    world = copy.deepcopy(source_preset)
    dims = world.get("dimensions", {})
    if set(DIMENSIONS) - dims.keys():
        raise ValueError("Expected all three Minecraft dimensions in this world preset")
    for realm in DIMENSIONS:
        source = dims[realm]["generator"]["biome_source"]
        if realm == "minecraft:overworld":
            if source.get("type") != "realscale:sky_ocean":
                raise ValueError("Expected the original sky_ocean overworld wrapper")
            if source["source"].get("type") == "realscale:cross_realm_invasion":
                raise ValueError("This preset already has cross-realm invasion")
            # Preserve sky-island behavior and every underlying ecotone/DEM sampler.
            source["source"] = {
                "type": "realscale:cross_realm_invasion",
                "target": "overworld",
                "source": source["source"],
            }
        else:
            if source.get("type") == "realscale:cross_realm_invasion":
                raise ValueError("This preset already has cross-realm invasion")
            dims[realm]["generator"]["biome_source"] = {
                "type": "realscale:cross_realm_invasion",
                "source": source,
            }
    return world


def create_preset(root: Path, world_source: str,
                  enable_invasion: bool, lab_name: str | None = None
                  ) -> tuple[str, dict, dict[str, str]]:
    """Return (new ID suffix, world JSON, localized world-type labels).

    'terrain_lab' must supply the generated lab preset name. Fixed families
    Sacred and Measured retain their canonical unmodified height/noise signals.
    """
    if type(enable_invasion) is not bool:
        raise ValueError("enable_invasion must be a boolean")
    if world_source == "terrain_lab":
        if not lab_name or not lab_name.startswith("lab_"):
            raise ValueError("terrain_lab requires a generated lab profile")
        base_name = lab_name
        name = lab_name + "_invasion" if enable_invasion else lab_name
    elif world_source in PRESETS:
        base_name = PRESETS[world_source]
        name = "unified_" + world_source + (
            "_invasion_v1" if enable_invasion else "_standard_v1")
    else:
        raise ValueError("Unknown unified world source: " + str(world_source))

    world = read_json(root, PRESET_DIR / (base_name + ".json"))
    if enable_invasion:
        world = invasion_preset(world)
    names = {
        "en_us": ("Terrain Lab" if world_source == "terrain_lab"
                  else ("Sacred Timeline" if world_source == "sacred"
                        else "Measured / USGS"))
                 + (" + Dimension Invasion" if enable_invasion else " (Standard)"),
        "zh_cn": ("地形实验室" if world_source == "terrain_lab"
                  else ("神圣时间线" if world_source == "sacred" else "实测地形 / USGS"))
                 + (" + 跨维度入侵" if enable_invasion else "（标准）"),
    }
    return name, world, names
