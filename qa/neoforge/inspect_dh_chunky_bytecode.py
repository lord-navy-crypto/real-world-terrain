#!/usr/bin/env python3
"""Inspect exact official 1.21.1 NeoForge binaries; do not republish their code.

Purpose: avoid blindly guessing a foreign Mixin target method descriptor or
assuming that a class is initialized just because its JAR is on classpath.
"""
import hashlib
import json
import pathlib
import subprocess
import urllib.request
from urllib.parse import urlparse

OUT = pathlib.Path("build/dh-chunky-contract")
OUT.mkdir(parents=True, exist_ok=True)
VERSIONS = {
    "DH": ("9w34y8ai", "uCdwusMi", "DistantHorizons"),
    "Chunky": ("LuFhm4eU", "fALzjamp", "Chunky-NeoForge"),
}

def download(url):
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in ("api.modrinth.com","cdn.modrinth.com"):
        raise RuntimeError("Rejected remote host")
    with urllib.request.urlopen(urllib.request.Request(
            url,headers={"User-Agent":"RealScale-QA-DHChunky/1.0"}), timeout=80) as req:
        buf=req.read(55_000_000)
    if len(buf)>=55_000_000:
        raise RuntimeError("Unexpectedly large mod JAR")
    return buf

for name,(version_id,project_id,needle) in VERSIONS.items():
    v=json.loads(download("https://api.modrinth.com/v2/version/"+version_id))
    if v.get("id") != version_id or v.get("project_id")!=project_id or (
            "1.21.1" not in v.get("game_versions",[]) or "neoforge" not in v.get("loaders",[])):
        raise RuntimeError("Incorrect "+name+" upstream version metadata: "+repr(v)[:200])
    files=[f for f in v["files"] if needle in f["filename"] and f["filename"].endswith(".jar")
           and f.get("hashes",{}).get("sha512")]
    if len(files)!=1:
        raise RuntimeError("Nonunique upstream jar candidate: "+name)
    f=files[0]
    data=download(f["url"])
    sha=hashlib.sha512(data).hexdigest()
    if sha!=f["hashes"]["sha512"]:
        raise RuntimeError("SHA512 validation failed: "+name)
    dest=OUT/f["filename"]
    dest.write_bytes(data)
    print("EXACT_OFFICIAL_BINARY_VERIFIED",name,str(dest),sha,flush=True)

dh=next(OUT.glob("DistantHorizons*.jar"))
chunky=next(OUT.glob("Chunky-NeoForge*.jar"))
classes={
    "DH Chunky bridge":(str(dh),"com.seibel.distanthorizons.neoforge.wrappers.modAccessor.ChunkyAccessor"),
    "DH abstract bridge":(str(dh),"com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractChunkyAccessor"),
    "Chunky provider":(str(chunky),"org.popcraft.chunky.ChunkyProvider")
}
for name,(classpath,klass) in classes.items():
    cmd=["javap","-p","-c","-classpath",classpath,klass]
    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (OUT/(name.lower().replace(" ","-")+".javap.txt")).write_text(proc.stdout,encoding="utf-8")
    print("BYTECODE_CONTRACT",name,proc.returncode,flush=True)
    print(proc.stdout[-18000:],flush=True)
    if proc.returncode:
        raise RuntimeError("javap failed for "+name)
