#!/usr/bin/env python3
"""Export a saved RealScale YACL draft to installable 1.21.1 data/resource packs.

Works with an ordinary installed RealScale JAR and Python 3.9+, without Gradle,
a source checkout, Java execution, or modifying a saved Minecraft world.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

from terrain_lab import apply
from unified_world_settings import create_preset, PRESET_DIR

SOURCE_PREFIXES = (
    "data/realscale/worldgen/",
    "data/minecraft/tags/worldgen/world_preset/",
    "assets/minecraft/lang/",
)
DATA_FORMAT = 48  # Minecraft Java 1.21.1
RESOURCE_FORMAT = 34


def _copy_jar_resources(jar: Path, root: Path) -> None:
    """Extract only trusted worldgen inputs; never extract arbitrary JAR paths."""
    total = 0
    with zipfile.ZipFile(jar) as archive:
        for member in archive.infolist():
            path = PurePosixPath(member.filename)
            if member.is_dir() or path.is_absolute() or ".." in path.parts:
                continue
            name = str(path)
            if not name.startswith(SOURCE_PREFIXES):
                continue
            if not name.endswith(".json"):
                continue
            total += member.file_size
            if member.file_size > 4_000_000 or total > 40_000_000:
                raise ValueError("JAR worldgen resources exceed the safe size limit")
            destination = root.joinpath(*path.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(member))
    needed = (
        "data/realscale/worldgen/noise_settings/realism_max.json",
        "data/realscale/worldgen/world_preset/realism_max.json",
        "assets/minecraft/lang/en_us.json",
        "assets/minecraft/lang/zh_cn.json",
    )
    if not all((root / item).is_file() for item in needed):
        raise ValueError("Not a compatible RealScale 1.21.1 JAR: missing worldgen resources")


def _draft_options(path: Path) -> dict:
    draft = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(draft, dict):
        raise ValueError("The YACL draft must be a JSON object")
    required = {"preset": str, "reliefPercent": int, "elevation": int,
                "caves": int, "riverGrade": int, "inland": bool}
    for key, klass in required.items():
        if type(draft.get(key)) is not klass:
            raise ValueError("The YACL draft has an invalid or missing " + key)
    optional = {
        "blendPercent": 25, "ridgePercent": 0, "erosionPercent": 0,
        "strataPercent": 0, "climatePercent": 0, "valleyPercent": 0,
        "latitudeMode": "off", "latitudeStrength": 70, "equatorZ": 0,
        "basinPercent": 0, "radialCenterX": 0, "coldLimitPercent": 60,
        "fantasyDimensions": False, "lithostitchedBlend": False,
        "lithostitchedGeology": False,
    }
    for key, default in optional.items():
        val = draft.get(key, default)
        if type(val) is not type(default):
            raise ValueError("Invalid type for " + key)
        optional[key] = val
    return dict(
        preset=draft["preset"], relief=draft["reliefPercent"] / 100.0,
        elevation=draft["elevation"], caves=draft["caves"],
        river_grade=draft["riverGrade"], inland=draft["inland"],
        blend_percent=optional["blendPercent"], ridge=optional["ridgePercent"],
        erosion=optional["erosionPercent"], strata=optional["strataPercent"],
        climate=optional["climatePercent"], valley=optional["valleyPercent"],
        latitude=optional["latitudeMode"],
        latitude_strength=optional["latitudeStrength"],
        equator_z=optional["equatorZ"], basin=optional["basinPercent"],
        center_x=optional["radialCenterX"],
        cold_limit=optional["coldLimitPercent"],
        fantasy_dimensions=optional["fantasyDimensions"],
        lithostitched_blend=optional["lithostitchedBlend"],
        lithostitched_geology=optional["lithostitchedGeology"],
    )


def _unified_choices(path: Path) -> tuple[str, bool, bool]:
    """Legacy drafts default to Terrain Lab with invasions disabled."""
    draft = json.loads(path.read_text(encoding="utf-8"))
    source = draft.get("worldSource", "terrain_lab")
    invasion = draft.get("dimensionalInvasion", False)
    showcase = draft.get("invasionShowcase", False)
    if source not in ("terrain_lab", "sacred", "measured") or type(source) is not str:
        raise ValueError("worldSource must be terrain_lab, sacred or measured")
    if type(invasion) is not bool:
        raise ValueError("dimensionalInvasion must be true or false")
    if type(showcase) is not bool or (showcase and not invasion):
        raise ValueError("invasionShowcase requires dimensionalInvasion=true")
    return source, invasion, showcase


def _pack_meta(format_number: int, description: str) -> bytes:
    return (json.dumps({"pack": {"pack_format": format_number,
                                 "description": description}},
                       indent=2) + "\n").encode("utf-8")


def export(jar: Path, draft: Path, output: Path,
           include_resource_pack: bool = True) -> tuple[str, Path | None]:
    jar, draft, output = Path(jar), Path(draft), Path(output)
    if output.suffix.lower() != ".zip":
        raise ValueError("--output must be a .zip datapack file")
    lang_output = output.with_name(output.stem + "-Language.zip") if include_resource_pack else None
    if output.exists() or (lang_output is not None and lang_output.exists()):
        raise FileExistsError("Refusing to overwrite an existing exported profile")
    options = _draft_options(draft)
    with tempfile.TemporaryDirectory(prefix="realscale_export_") as temporary:
        root = Path(temporary)
        _copy_jar_resources(jar, root)
        source, invasion, showcase = _unified_choices(draft)
        labels = None
        if source == "terrain_lab":
            # Preserve byte-identical legacy exports when invasion is off.
            name, saved_options, files = apply(root, **options)
            generated = sorted((Path(item) for item in files
                                if str(item).startswith("data/realscale/worldgen/")
                                or str(item).startswith("data/realscale/lithostitched/fast_noise_config/")),
                               key=str)
            if invasion:
                original_name = name
                name, preset, labels = create_preset(root, source, True, lab_name=name, showcase=showcase)
                # Do not export two selectable world IDs for the same draft.
                generated = [p for p in generated
                             if p != PRESET_DIR / (original_name + ".json")]
                new_preset = PRESET_DIR / (name + ".json")
                (root / new_preset).write_text(
                    json.dumps(preset, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")
                generated.append(new_preset)
        else:
            # Sacred and Measured reuse their original immutable noise settings.
            # No terrain retuning or DEM conversion is performed.
            name, preset, labels = create_preset(root, source, invasion, showcase=showcase)
            new_preset = PRESET_DIR / (name + ".json")
            (root / new_preset).write_text(
                json.dumps(preset, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8")
            generated = [new_preset]
        profile_id = "realscale:" + name
        if labels is not None:
            for language, label in labels.items():
                lang_path = root / "assets/minecraft/lang" / (language + ".json")
                data = json.loads(lang_path.read_text(encoding="utf-8"))
                data["generator.realscale." + name] = label
                lang_path.write_text(
                    json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")
        if not generated or not any(str(p).endswith("/world_preset/" + name + ".json")
                                     for p in generated):
            raise ValueError("Unified generator did not produce a world preset")
        output.parent.mkdir(parents=True, exist_ok=True)
        # ZipFile(mode='x') refuses to silently replace a user's existing pack.
        with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("pack.mcmeta", _pack_meta(
                DATA_FORMAT, "RealScale Terrain Lab custom world type " + name))
            for item in generated:
                archive.write(root / item, str(item))
            # Merge a single new ID into vanilla's two additive tags. Never
            # redistribute or override the other bundled world types.
            for tag in ("normal", "extended"):
                path = "data/minecraft/tags/worldgen/world_preset/" + tag + ".json"
                archive.writestr(path, json.dumps(
                    {"replace": False, "values": [profile_id]},
                    indent=2) + "\n")
        if lang_output is not None:
            with zipfile.ZipFile(lang_output, "x", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("pack.mcmeta", _pack_meta(
                    RESOURCE_FORMAT, "RealScale Terrain Lab world-type names"))
                for language in ("en_us", "zh_cn"):
                    lang_path = root / "assets/minecraft/lang" / (language + ".json")
                    labels = json.loads(lang_path.read_text(encoding="utf-8"))
                    key = "generator.realscale." + name
                    if key not in labels:
                        raise ValueError("Missing generated " + language + " world-type label")
                    archive.writestr(
                        "assets/minecraft/lang/" + language + ".json",
                        json.dumps({key: labels[key]}, ensure_ascii=False, indent=2) + "\n")
        return profile_id, lang_output


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Convert RealScale YACL unified settings into a custom NEW-WORLD datapack, without Gradle.")
    parser.add_argument("--jar", type=Path, required=True,
                        help="Installed RealScale Terrain 1.21.1 NeoForge mod JAR")
    parser.add_argument("--draft", type=Path, required=True,
                        help="YACL config/realscale-terrain-lab.json from the Minecraft instance")
    parser.add_argument("--output", type=Path, required=True,
                        help="Destination .zip for the Create World > Data Packs screen")
    parser.add_argument("--no-resource-pack", action="store_true",
                        help="Skip the optional language-name resource pack")
    args = parser.parse_args(argv)
    try:
        profile_id, resource = export(
            args.jar, args.draft, args.output, not args.no_resource_pack)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        parser.exit(1, "Cannot export RealScale config: " + str(exc) + "\n")
    print("Created new world type:", profile_id)
    print("Enable this datapack on CREATE NEW WORLD:", args.output)
    if resource is not None:
        print("Optional language resource pack (Options > Resource Packs):", resource)
    print("Keep the original RealScale JAR installed. Retain this datapack with the saved world.")
    print("No existing world or JAR has been changed. Do not apply a new profile to an old save.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
