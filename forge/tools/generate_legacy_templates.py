"""Run shared deterministic generators using the Forge 1.20.1 NBT dialect."""
import runpy
import sys
from pathlib import Path

script = Path(sys.argv[1]).resolve()
sys.argv = [str(script), *sys.argv[2:]]
sys.path.insert(0, str(script.parent))

import generate_original_architecture as common

# Minecraft 1.20.1 DataVersion; generated templates have no entity data.
common.DATA_VERSION = 3465
REPLACEMENTS = {
    "minecraft:tuff_bricks": "minecraft:deepslate_bricks",
    "minecraft:short_grass": "minecraft:grass",
    "minecraft:carpet": "minecraft:white_carpet",
}
original_enrich = common.Building.enrich_architecture

def legacy_enrich(self):
    result = original_enrich(self)
    for pos, (name, properties) in self.blocks.items():
        if name in REPLACEMENTS:
            self.blocks[pos] = (REPLACEMENTS[name], properties)
    return result

common.Building.enrich_architecture = legacy_enrich
if script.stem == "generate_original_architecture":
    common.main()
else:
    runpy.run_path(str(script), run_name="__main__")
