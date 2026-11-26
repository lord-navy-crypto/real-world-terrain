"""Run shared deterministic generators with Minecraft Java 1.21.1 NBT metadata."""
import runpy
import sys
from pathlib import Path

script = Path(sys.argv[1]).resolve()
sys.argv = [str(script), *sys.argv[2:]]
sys.path.insert(0, str(script.parent))

import generate_original_architecture as common

# Fabric 1.21.1 uses the current 1.21.1 block palette and DataVersion.
# Do not apply the NeoForge 1.20.1 compatibility substitutions here.
common.DATA_VERSION = 3955

if script.stem == "generate_original_architecture":
    common.main()
else:
    runpy.run_path(str(script), run_name="__main__")
