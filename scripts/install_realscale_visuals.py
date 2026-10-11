#!/usr/bin/env python3
"""Install optional client-only shader visuals into an isolated RealScale dev run.

Sources: official Modrinth Iris/Sodium binary hashes; official upstream GitHub
shader/resource pack repositories. No Java mods are installed into src/main.
The official Sodium runtime dependency is source-available PolyForm Shield,
not OSI open-source. Iris is LGPL; MakeUp LGPL; XsRealism MIT.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

IRIS_VERSION = "t3ruzodq"       # Iris 1.8.12, NeoForge Minecraft 1.21.1
SODIUM_VERSION = "Pb3OXVqC"     # Sodium 0.6.13, NeoForge Minecraft 1.21.1
SHADER_NAME = "MakeUp-UltraFast-OpenSource.zip"
TEXTURE_NAME = "XsRealism-256x-MIT.zip"
HEADERS = {"User-Agent": "RealScale-Visuals-Installer/1.0"}

def get(url, dst, limit=900_000_000):
    print("Downloading", url.split("?")[0].rsplit("/", 1)[-1], flush=True)
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=90) as response, dst.open("wb") as out:
        total = 0
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise RuntimeError("Download exceeded safe size limit")
            out.write(chunk)
    return dst

def meta(version):
    url = "https://api.modrinth.com/v2/version/" + version
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as response:
        result = json.load(response)
    if "1.21.1" not in result.get("game_versions", []) or "neoforge" not in result.get("loaders", []):
        raise RuntimeError("Pinned shader loader is not NeoForge 1.21.1 compatible: " + version)
    return result

def mod_download(version, folder, temp):
    info = meta(version)
    files = [f for f in info["files"] if f["filename"].endswith(".jar")]
    if len(files) != 1:
        raise RuntimeError("Unexpected Modrinth JAR list: " + version)
    entry = files[0]
    url = entry["url"]
    if not url.startswith("https://cdn.modrinth.com/"):
        raise RuntimeError("Unexpected Modrinth download host")
    downloaded = get(url, temp / entry["filename"], 25_000_000)
    digest = entry["hashes"].get("sha512")
    if not digest or hashlib.sha512(downloaded.read_bytes()).hexdigest() != digest:
        raise RuntimeError("Modrinth SHA-512 checksum mismatch: " + entry["filename"])
    folder.mkdir(parents=True, exist_ok=True)
    dest = folder / entry["filename"]
    shutil.copy2(downloaded, dest)
    print("Verified:", dest.name)
    return dest

def source_pack(url, out, kind, temp):
    source = get(url, temp / (kind + "-upstream.zip"))
    with zipfile.ZipFile(source) as upstream, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, 5) as pack:
        copied = 0
        for file in upstream.infolist():
            if file.is_dir():
                continue
            parts = file.filename.split("/")
            if len(parts) < 2:
                continue
            relative = "/".join(parts[1:])
            if kind == "shader":
                include = relative.startswith("shaders/") or relative in ("LICENSE", "README.md")
            else:
                include = relative.startswith("assets/") or relative in ("LICENSE", "pack.mcmeta", "pack.png")
            if not include:
                continue
            if kind == "texture" and relative == "pack.mcmeta":
                # Minecraft 1.21.1 uses resource pack format 34.
                packmeta = json.loads(upstream.read(file))
                packmeta.setdefault("pack", {})["pack_format"] = 34
                pack.writestr(relative, json.dumps(packmeta, ensure_ascii=False))
            else:
                pack.writestr(relative, upstream.read(file))
            copied += 1
    with zipfile.ZipFile(out) as check:
        names = set(check.namelist())
        required = "shaders/" if kind == "shader" else "assets/"
        if not any(x.startswith(required) for x in names):
            raise RuntimeError("No actual files in " + kind + " zip")
        if kind == "texture" and "pack.mcmeta" not in names:
            raise RuntimeError("Texture resource pack metadata is absent")
    print("Packed", copied, "source files:", out.name)

def find_project():
    explicit = os.environ.get("REALSCALE_DIR", "").strip()
    if explicit:
        path = Path(explicit).expanduser().resolve()
        if (path / "neoforge/build.gradle").is_file():
            return path
        raise SystemExit("REALSCALE_DIR must point to the RealScale repository root")
    desktop = Path.home() / "Desktop"
    roots = (desktop, desktop / "MinecraftDev")
    choices = []
    for root in roots:
        for pattern in ("RealScale-Fixed-*", "RealScale-Test-*"):
            choices += [x for x in root.glob(pattern) if (x / "neoforge/build.gradle").is_file()]
    here = Path.cwd().resolve()
    for candidate in (here, here.parent):
        if (candidate / "neoforge/build.gradle").is_file():
            choices.append(candidate)
    if not choices:
        raise SystemExit("No local RealScale-Test/RealScale-Fixed directory found; set REALSCALE_DIR")
    return max(choices, key=lambda x: x.stat().st_mtime)

def set_property(path, key, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    old = path.read_text() if path.exists() else ""
    rows = [r for r in old.splitlines() if not r.startswith(key + "=")]
    rows.append(key + "=" + value)
    path.write_text("\n".join(rows) + "\n")

def activate_texture(path):
    value = "file/" + TEXTURE_NAME
    text = path.read_text() if path.exists() else ""
    rows = text.splitlines()
    found = False
    for i, line in enumerate(rows):
        if line.startswith("resourcePacks:"):
            found = True
            try:
                packs = json.loads(line.split(":", 1)[1])
                if value not in packs:
                    packs.append(value)
                rows[i] = "resourcePacks:" + json.dumps(packs, separators=(",", ":"))
            except (ValueError, TypeError):
                print("Existing resourcePacks setting was not changed; activate pack in Minecraft Options.")
            break
    if not found:
        rows.append("resourcePacks:" + json.dumps(["vanilla", value], separators=(",", ":")))
    path.write_text("\n".join(rows) + "\n")

def main():
    project = find_project()
    run = project / "neoforge" / "run"
    mods, shaders, textures = [run / name for name in ("mods", "shaderpacks", "resourcepacks")]
    for folder in (mods, shaders, textures):
        folder.mkdir(parents=True, exist_ok=True)
    print("RealScale instance:", run, flush=True)
    with tempfile.TemporaryDirectory(prefix="realscale-visuals-") as scratch:
        temp = Path(scratch)
        iris = mod_download(IRIS_VERSION, mods, temp)
        sodium = mod_download(SODIUM_VERSION, mods, temp)
        backup = mods / "old_visual_mods_backup"
        for file in mods.glob("*.jar"):
            if file.name not in (iris.name, sodium.name) and file.name.lower().startswith(("iris-", "sodium-")):
                backup.mkdir(exist_ok=True)
                shutil.move(str(file), str(backup / file.name))
        source_pack(
            "https://codeload.github.com/javiergcim/MakeUpUltraFast/zip/refs/heads/master",
            shaders / SHADER_NAME, "shader", temp
        )
        source_pack(
            "https://codeload.github.com/darkyboys/XsRealism-256x/zip/refs/heads/main",
            textures / TEXTURE_NAME, "texture", temp
        )
    set_property(run / "config/iris.properties", "shaderPack", SHADER_NAME)
    set_property(run / "config/iris.properties", "enableShaders", "true")
    activate_texture(run / "options.txt")
    print("\nSUCCESS: Iris, Sodium, MakeUp and XsRealism installed in this test client.")
    print("Shaders:", shaders)
    print("Resource pack:", textures / TEXTURE_NAME)
    print("Sodium dependency uses source-available PolyForm Shield, not an OSI open-source license.")
    print("Mac shader GPU compatibility is not guaranteed; disable shaders in Iris settings if needed.")
    if "--install-only" in sys.argv:
        print("Start client: cd", project / "neoforge", "&& gradle --no-daemon runClient")
        return
    java_home_tool = Path("/usr/libexec/java_home")
    env = os.environ.copy()
    if java_home_tool.is_file():
        result = subprocess.run([str(java_home_tool), "-v", "21"], capture_output=True, text=True)
        if result.returncode == 0:
            env["JAVA_HOME"] = result.stdout.strip()
            env["PATH"] = str(Path(env["JAVA_HOME"]) / "bin") + os.pathsep + env.get("PATH", "")
    gradle = shutil.which("gradle", path=env.get("PATH"))
    if gradle:
        print("\nLaunching RealScale Minecraft dev client...", flush=True)
        sys.exit(subprocess.call([gradle, "--no-daemon", "--console=plain", "runClient"],
                                 cwd=project / "neoforge", env=env))
    print("Gradle not found. Run runClient with your installed Gradle version.")

if __name__ == "__main__":
    main()
