#!/usr/bin/env python3
"""Exercise real compiled RealScale .jar -> YACL draft -> self-contained new-world pack.

Must preserve four original menu choices plus additive experiments and Grand dimensions.
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
    require(len(jars)>0,"Gradle did not produce a RealScale client/server JAR")
    jar=jars[-1]
    with zipfile.ZipFile(jar) as source:
        baseline=json.loads(source.read(
            "data/realscale/worldgen/world_preset/natural_three_realms.json"))
        core=json.loads(source.read(
            "data/minecraft/tags/worldgen/world_preset/normal.json"))
        cave_test=("data/realscale/worldgen/world_preset/"
            "cave_ocean_ecology_three_realms.json") in source.namelist()
    original=[
        "realscale:natural_three_realms",
        "realscale:alpine_three_realms",
        "realscale:canyon_three_realms",
        "realscale:measured_three_realms",
    ]
    optional=["realscale:wonders_invasion_three_realms"]
    if cave_test:
        optional.append("realscale:cave_ocean_ecology_three_realms")
    require(core["replace"] is False and core["values"]==original+optional,
            "RealScale built mod must retain four original presets plus explicit opt-in experiments")
    with tempfile.TemporaryDirectory(prefix="realscale-lab-export-") as temporary:
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
        output=directory/"RealScale-Lab-Test.zip"
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
    print("REALSCALE_EXPORTER_E2E_PASS: compiled jar, 3-realm Lab pack, 4 stable + permitted optional test menu and immutable export")

if __name__=="__main__":
    main()
