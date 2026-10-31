#!/usr/bin/env python3
"""Offline minimal-PII graphics/LOD log triage for RealScale, Iris and Sodium.

No network access, no configuration changes, and no raw log content in output.
Shader-pack support and GPU-visible LOD cannot be deduced from installed jars.
"""
from pathlib import Path
import json
import re
import sys

MODS={
  "realscale":r"RealScale[^\s]*\.jar|realscale@",
  "iris":r"iris-neoforge[^\s]*\.jar|iris@",
  "sodium":r"sodium-neoforge[^\s]*\.jar|sodium@",
}
def analyze(log:str)->dict:
    mods={name:bool(re.search(pattern,log,re.I)) for name,pattern in MODS.items()}
    gl_error=bool(re.search(r"GL_INVALID_(?:OPERATION|VALUE)|OpenGL(?:Exception| error)|ShaderCompileException",log,re.I))
    tall_error=bool(re.search(r"(?:height|section|Y coordinate).{0,80}(?:out of bounds|out of range|outside)",log,re.I))
    findings=[]
    if gl_error:findings.append("Graphics/OpenGL error; isolate active shader pack and renderer")
    if tall_error:findings.append("Possible high-altitude boundary error; inspect actual trace")
    if mods["iris"]:findings.append("Iris present; shaderpack compatibility is not automatically known")
    return {
        "mods_detected":mods,
        "graphics_error_seen":gl_error,
        "potential_vertical_error":tall_error,
        "shader_pack_support":"unverified",
        "gpu_lod_visible":"unverified",
        "findings":findings
    }

if __name__=="__main__":
    if len(sys.argv) not in (2,3):
        raise SystemExit("Usage: python3 audit_lod_stack.py latest.log [audit.json]")
    result=analyze(Path(sys.argv[1]).read_text(errors="replace"))
    output=json.dumps(result,sort_keys=True,ensure_ascii=False,indent=2)+"\n"
    if len(sys.argv)==3:Path(sys.argv[2]).write_text(output,encoding="utf-8")
    print(output)
