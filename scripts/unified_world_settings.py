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


def invasion_preset(source_preset: dict, spawn_end_habitats: bool = False) -> dict:
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
                "type": ("realscale:cross_realm_spawn3" if spawn_end_habitats
                         else "realscale:cross_realm_invasion"),
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



def recover_terrain_lab_ecology(source_preset: dict) -> dict:
    """Restore current shared habitats in a newly exported unified Lab world.

    Legacy saved profiles and the mod's historical source JSON are untouched.
    Do not change existing fixed-ID unified Sacred/Measured export variants.
    """
    world = copy.deepcopy(source_preset)
    overworld = world["dimensions"]["minecraft:overworld"]["generator"]
    biome = overworld["biome_source"]
    if biome.get("type") == "realscale:cross_realm_showcase":
        biome = biome["source"]
    if biome.get("type") != "realscale:sky_ocean":
        raise ValueError("Unified Lab ecology requires an outer sky_ocean source")
    source = biome["source"]
    wrappers = {"realscale:rare_earth_biomes_v1", "realscale:geo_wonders_opt_in"}
    while source.get("type") in wrappers:
        source = source["source"]
    if source.get("type") not in ("realscale:cross_realm_spawn3",
                                  "realscale:cross_realm_invasion"):
        raise ValueError("Unified Lab ecology requires existing invasion")
    invasion = copy.deepcopy(source)
    baseline = invasion["source"]
    while baseline.get("type") in wrappers:
        baseline = baseline["source"]
    if baseline.get("type") in ("realscale:natural_ecotones",
                                "realscale:natural_ecotones_diverse_v2"):
        baseline = {"type": "realscale:natural_ecotones_diverse_v3",
                    "source": baseline["source"]}
    elif baseline.get("type") != "realscale:natural_ecotones_diverse_v3":
        raise ValueError("Cannot preserve unknown Lab ecotone source: "
                         + str(baseline.get("type")))
    invasion["source"] = baseline
    # Geo-wonders must stay OUTSIDE invasion, rare-Earth outside wonders,
    # with sky islands at the outermost sampled overworld level.
    # These positions are also required by SkyOcean's feature guards.
    biome["source"] = {
        "type": "realscale:rare_earth_biomes_v1",
        "source": {
            "type": "realscale:geo_wonders_opt_in",
            "source": invasion
        }
    }
    return world


def create_preset(root: Path, world_source: str,
                  enable_invasion: bool, lab_name: str | None = None,
                  showcase: bool = False, end_biomes: bool = False,
                  spawn_end_habitats: bool = False
                  ) -> tuple[str, dict, dict[str, str]]:
    """Return (new ID suffix, world JSON, localized world-type labels).

    'terrain_lab' must supply the generated lab preset name. Fixed families
    Sacred and Measured retain their canonical unmodified height/noise signals.
    """
    if type(enable_invasion) is not bool or type(showcase) is not bool or type(end_biomes) is not bool:
        raise ValueError("Invasion, showcase and End biomes settings must be boolean")
    if showcase and not enable_invasion:
        raise ValueError("Showcase requires Dimension Invasion enabled")
    if type(spawn_end_habitats) is not bool:
        raise ValueError("spawn_end_habitats must be boolean")
    if spawn_end_habitats and not enable_invasion:
        raise ValueError("Three-End-biome spawn requires Dimension Invasion")
    if world_source == "terrain_lab":
        if not lab_name or not lab_name.startswith("lab_"):
            raise ValueError("terrain_lab requires a generated lab profile")
        base_name = lab_name
        name = (lab_name + ("_invasion_showcase" if showcase else "_invasion")
                if enable_invasion else lab_name)
    elif world_source in PRESETS:
        base_name = PRESETS[world_source]
        name = "unified_" + world_source + (
            "_invasion_showcase_v1" if showcase else
            "_invasion_v1" if enable_invasion else "_standard_v1")
    else:
        raise ValueError("Unknown unified world source: " + str(world_source))

    world = read_json(root, PRESET_DIR / (base_name + ".json"))
    if enable_invasion:
        world = invasion_preset(world, spawn_end_habitats=spawn_end_habitats)
    if end_biomes:
        # Explicit opt-in, with NEW stable world-ID: old saved presets and
        # canonical sacred/measured registries must never change silently.
        end = world["dimensions"]["minecraft:the_end"]["generator"]
        old = end["biome_source"]
        if old.get("type") == "realscale:end_accents":
            raise ValueError("Duplicate End accent wrapper")
        end["biome_source"] = {"type": "realscale:end_accents", "source": old}
        name = name + "_end3"
    if spawn_end_habitats:
        # A NEW stable ID prevents changing existing *_end3 saved worlds,
        # whose codecs had no spawn_end_habitats field.
        name += "_spawn3"
    if showcase:
        # Gallery mode deliberately lives only in a NEW opt-in world preset.
        # Near X/Z 0/0 or 176/0 it exposes all six invasion directions.
        for realm in DIMENSIONS:
            generator = world["dimensions"][realm]["generator"]
            generator["biome_source"] = {
                "type": "realscale:cross_realm_showcase",
                "source": generator["biome_source"],
            }
    names = {
        "en_us": ("Terrain Lab" if world_source == "terrain_lab"
                  else ("Sacred Timeline" if world_source == "sacred"
                        else "Measured / USGS"))
                 + (" + Invasion Showcase" if showcase else
                    " + Dimension Invasion" if enable_invasion else " (Standard)"),
        "zh_cn": ("地形实验室" if world_source == "terrain_lab"
                  else ("神圣时间线" if world_source == "sacred" else "实测地形 / USGS"))
                 + (" + 入侵展示世界" if showcase else
                    " + 跨维度入侵" if enable_invasion else "（标准）"),
    }
    if spawn_end_habitats:
        names["en_us"] += " + End Spawn Invasion"
        names["zh_cn"] += " + 三选一末地群系出生区"
    if end_biomes:
        names["en_us"] += " + Three End Biomes"
        names["zh_cn"] += " + 三种末地生物群系"
    return name, world, names
