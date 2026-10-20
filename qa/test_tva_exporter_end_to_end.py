#!/usr/bin/env python3
"""Exercise real compiled TVA .jar -> YACL draft -> self-contained new-world pack.

Must expose four curated player worlds while preserving historical worldgen and Grand dimensions.
"""
import json
from pathlib import Path
import sys
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from export_terrain_lab_datapack import export

def require(condition, message):
    if not condition:
        raise AssertionError(message)

def main():
    jars=sorted((ROOT/"neoforge/build/libs").glob("*.jar"))
    require(len(jars)>0,"Gradle did not produce a TVA client/server JAR")
    jar=jars[-1]
    with zipfile.ZipFile(jar) as source:
        baseline=json.loads(source.read(
            "data/realscale/worldgen/world_preset/tva_sacred_timeline_three_realms.json"))
        core=json.loads(source.read(
            "data/minecraft/tags/worldgen/world_preset/normal.json"))
    player_modes=[
        "realscale:tva_sacred_biodiversity_end3_spawn3_v3",
        "realscale:tva_measured_global_landforms_three_realms",
        "realscale:tva_alpine_three_realms",
        "realscale:tva_canyon_three_realms",
    ]
    require(core["replace"] is False and core["values"]==player_modes,
            "Compiled TVA must expose only four player worlds while keeping archived presets")
    with tempfile.TemporaryDirectory(prefix="tva-lab-export-") as temporary:
        directory=Path(temporary)
        draft=directory/"profile.json"
        draft.write_text(json.dumps({
            "preset":"landscape","reliefPercent":110,"elevation":12,
            "caves":65,"riverGrade":6,"inland":True,
            "blendPercent":25,"ridgePercent":10,"erosionPercent":5,
            "strataPercent":3,"climatePercent":60,"valleyPercent":15,
            "basinPercent":10,"latitudeMode":"radial_balanced",
            "latitudeStrength":65,"equatorZ":0,"radialCenterX":0,
            "coldLimitPercent":60,"fantasyDimensions":True,
            "lithostitchedBlend":False,"lithostitchedGeology":False
        },indent=2),encoding="utf-8")
        output=directory/"TVA-Lab-Test.zip"
        profile,resource_pack=export(jar,draft,output)
        require(output.is_file() and resource_pack and resource_pack.is_file(),
                "Expected both custom datapack and language resource pack")
        key=profile.split(":",1)[1]
        with zipfile.ZipFile(output) as archive:
            custom=json.loads(archive.read(
                "data/realscale/worldgen/world_preset/"+key+".json"))
            for dim in ("minecraft:the_nether","minecraft:the_end"):
                require(custom["dimensions"][dim]==baseline["dimensions"][dim],
                        "Export downgraded Grand Nether / End: "+dim)
            require(custom["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]==
                    baseline["dimensions"]["minecraft:overworld"]["generator"]["biome_source"],
                    "Export discarded sky-ocean/ecotone wrapper")
            custom_tag=json.loads(archive.read(
                "data/minecraft/tags/worldgen/world_preset/normal.json"))
            require(custom_tag["values"]==[profile],
                    "Portable datapack should add one custom ID, not duplicate stable or experimental choices")
        with zipfile.ZipFile(resource_pack) as lang_archive:
            labels=json.loads(lang_archive.read("assets/minecraft/lang/en_us.json"))
            require("generator.realscale."+key in labels,
                    "Missing user-facing custom world name")
        try:
            export(jar,draft,output)
        except FileExistsError:
            pass
        else:
            raise AssertionError("Exporter unexpectedly overwrote a pre-existing pack")
    print("TVA_EXPORTER_E2E_PASS: compiled jar, 3-realm Lab pack, four curated player choices and immutable single-world export")

if __name__=="__main__":
    main()
