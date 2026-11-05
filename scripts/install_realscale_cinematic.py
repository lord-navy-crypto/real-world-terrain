#!/usr/bin/env python3
"""Install high-quality shader and MIT 512x/PBR packs in one RealScale test client.

By default Complementary Unbound is used (free, custom-license, NOT OSS).
Use --oss for Firmament (MIT), retaining XsRealism base and PBR (both MIT).
Only the selected isolated RealScale dev client's run directory is touched.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile

HEADERS = {"User-Agent":"RealScaleTerrain-VisualInstaller/2.0"}
BASE_NAME = "RealScale-XsRealism-512x-MIT.zip"
PBR_NAME = "RealScale-XsRealism-PBR-MIT.zip"
BLOCKED_PATHS = {"..", "."}

def get_url(url, output, limit=1_000_000_000):
    request=urllib.request.Request(url,headers=HEADERS)
    count=0
    with urllib.request.urlopen(request,timeout=90) as response, output.open("wb") as f:
        while True:
            piece=response.read(1024*1024)
            if not piece:break
            count+=len(piece)
            if count>limit:raise RuntimeError("Download exceeded size limit")
            f.write(piece)
    if count<256:raise RuntimeError("Suspiciously small download: "+url)
    return output

def pack_repo(repo, name, temp, target):
    url="https://codeload.github.com/"+repo+"/zip/refs/heads/main"
    archive=get_url(url,temp/(name+".source.zip"))
    output=temp/name
    files=0
    with zipfile.ZipFile(archive) as inp, zipfile.ZipFile(output,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=5) as out:
        for entry in inp.infolist():
            if entry.is_dir():continue
            path="/".join(entry.filename.split("/")[1:])
            if not path:continue
            if any(x in BLOCKED_PATHS for x in Path(path).parts):raise RuntimeError("Unsafe archive path")
            if path!="pack.mcmeta" and path!="pack.png" and path!="LICENSE" and not path.startswith("assets/"):
                continue
            if path=="pack.mcmeta":
                metadata=json.loads(inp.read(entry))
                metadata.setdefault("pack",{})["pack_format"]=34  # Minecraft Java 1.21.1
                out.writestr(path,json.dumps(metadata,ensure_ascii=False))
            else:out.writestr(path,inp.read(entry))
            files+=1
    with zipfile.ZipFile(output) as verify:
        paths=verify.namelist()
        if "pack.mcmeta" not in paths or not any(n.startswith("assets/minecraft/textures/") for n in paths):
            raise RuntimeError("Not a Minecraft texture pack: "+repo)
    target.parent.mkdir(parents=True,exist_ok=True)
    os.replace(output,target)
    print("Installed",target.name,"with",files,"files",flush=True)

def get_modrinth_project(project, game="1.21.1", loader=None):
    qs={"game_versions":json.dumps([game])}
    if loader:qs["loaders"]=json.dumps([loader])
    url="https://api.modrinth.com/v2/project/"+project+"/version?"+urllib.parse.urlencode(qs)
    with urllib.request.urlopen(urllib.request.Request(url,headers=HEADERS),timeout=35) as f:
        versions=json.load(f)
    available=[v for v in versions if game in v.get("game_versions",[]) and (not loader or loader in v.get("loaders",[]))]
    available.sort(key=lambda v:v.get("date_published",""),reverse=True)
    if not available:raise RuntimeError("No compatible Modrinth "+game+" version for "+project)
    return next((v for v in available if v.get("version_type")=="release"),available[0])

def install_modrinth(project,suffix,folder,temp,loader=None):
    version=get_modrinth_project(project,loader=loader)
    files=[f for f in version["files"] if f["filename"].endswith(suffix)]
    files.sort(key=lambda f:not f.get("primary",False))
    if not files:raise RuntimeError("No "+suffix+" file for "+project)
    selected=files[0]
    if not selected["url"].startswith("https://cdn.modrinth.com/"):
        raise RuntimeError("Unexpected Modrinth download location")
    name=selected["filename"]
    local=get_url(selected["url"],temp/name,70_000_000)
    expected=selected["hashes"].get("sha512")
    if not expected or hashlib.sha512(local.read_bytes()).hexdigest()!=expected:
        raise RuntimeError("Failed Modrinth SHA512 validation: "+name)
    if suffix==".zip":
        with zipfile.ZipFile(local) as pack:
            if not any(f.startswith("shaders/") for f in pack.namelist()):
                raise RuntimeError("Shaderpack has no shaders directory: "+name)
    folder.mkdir(parents=True,exist_ok=True)
    final=folder/name
    shutil.copy2(local,final)
    print("Verified",project,version.get("version_number"),name,flush=True)
    return final

def find_realscale():
    specified=os.getenv("REALSCALE_DIR")
    if specified:
        root=Path(specified).expanduser().resolve()
        if (root/"neoforge/build.gradle").is_file():return root
        raise RuntimeError("REALSCALE_DIR must be a RealScale repository root")
    found=[]
    for parent in [Path.home()/"Desktop",Path.home()/"Desktop/MinecraftDev"]:
        for glob in ["RealScale-Fixed-*","RealScale-Test-*"]:
            found.extend(x for x in parent.glob(glob) if (x/"neoforge/build.gradle").is_file())
    for root in (Path.cwd(),Path.cwd().parent):
        if (root/"neoforge/build.gradle").is_file():found.append(root)
    if not found:raise RuntimeError("No RealScale test installation found; set REALSCALE_DIR")
    # Prefer instance containing the existing Iris/Sodium installation.
    return max(found,key=lambda r:(any((r/"neoforge/run/mods").glob("*iris*.jar")),r.stat().st_mtime))

def config_iris(config,pack):
    config.parent.mkdir(parents=True,exist_ok=True)
    lines=config.read_text().splitlines() if config.exists() else []
    lines=[line for line in lines if not line.startswith(("shaderPack=","enableShaders="))]
    lines+=["shaderPack="+pack,"enableShaders=true"]
    config.write_text("\n".join(lines)+"\n")

def config_packs(options):
    options.parent.mkdir(parents=True,exist_ok=True)
    lines=options.read_text().splitlines() if options.exists() else []
    target=["file/"+BASE_NAME,"file/"+PBR_NAME]
    for i,line in enumerate(lines):
        if not line.startswith("resourcePacks:"):continue
        try:existing=json.loads(line.partition(":")[2])
        except Exception:
            print("Please enable both new texture packs in Video Settings > Resource Packs")
            return
        existing=[x for x in existing if not x.startswith("file/XsRealism-256x") and x not in target]
        lines[i]="resourcePacks:"+json.dumps(existing+target,separators=(",",":"))
        break
    else:lines.append("resourcePacks:"+json.dumps(["vanilla"]+target,separators=(",",":")))
    options.write_text("\n".join(lines)+"\n")

def main():
    mode="firmament-shaders" if "--oss" in sys.argv else "complementary-unbound"
    root=find_realscale()
    run=root/"neoforge/run"
    print("Selected RealScale client:",run,flush=True)
    print("Shader:",mode,"| 512x+PBR textures: MIT licensed",flush=True)
    moddir=run/"mods"
    shaders=run/"shaderpacks"
    textures=run/"resourcepacks"
    with tempfile.TemporaryDirectory(prefix="realscale-cinematic-") as scratch:
        temp=Path(scratch)
        if not any(moddir.glob("*iris*.jar")):
            install_modrinth("iris",".jar",moddir,temp,loader="neoforge")
        if not any(moddir.glob("*sodium*.jar")):
            install_modrinth("sodium",".jar",moddir,temp,loader="neoforge")
        shader=install_modrinth(mode,".zip",shaders,temp,loader="iris")
        pack_repo("darkyboys/XsRealism-512x",BASE_NAME,temp,textures/BASE_NAME)
        pack_repo("darkyboys/XsRealism-pbr",PBR_NAME,temp,textures/PBR_NAME)
    config_iris(run/"config/iris.properties",shader.name)
    config_packs(run/"options.txt")
    print("\nDONE. New visuals installed in:",run,flush=True)
    print("Shader:",shader.name,flush=True)
    print("Textures:",BASE_NAME,"and",PBR_NAME,flush=True)
    if mode=="complementary-unbound":
        print("License: Complementary is free-to-use with a CUSTOM license, NOT open source.")
    else:print("Shader and texture packs have MIT licenses.")
    print("Existing saves and terrain generator files were not modified.")
    print("Launch: cd",root/"neoforge","&& gradle --no-daemon runClient")

if __name__=="__main__":
    try:main()
    except Exception as e:
        print("INSTALL ERROR:",str(e),file=sys.stderr)
        sys.exit(1)
