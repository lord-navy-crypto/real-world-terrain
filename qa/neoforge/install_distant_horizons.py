#!/usr/bin/env python3
"""Pinned, checksum-verified official Distant Horizons NeoForge 1.21.1 jar.

No remote scripts or dependency injection: download only the matching jar
listed by Modrinth's version metadata, verify its SHA512, then copy it into
every possible Gradle development-server mods directory.
"""
import hashlib
import json
from pathlib import Path
import shutil
import sys
from urllib.parse import urlparse
from urllib.request import Request, urlopen

VERSION_ID = "9w34y8ai"  # Official Distant Horizons 3.3.3, 1.21.1 neo/fabric
PROJECT_ID = "uCdwusMi"
EXPECTED_MC = "1.21.1"
API_URL = f"https://api.modrinth.com/v2/version/{VERSION_ID}"
AGENT = "RealScale-DH-compat-QA/1.0 (GitHub Actions)"

def retrieve(url: str) -> bytes:
    host = urlparse(url).hostname
    if urlparse(url).scheme != "https" or host not in {
        "api.modrinth.com", "cdn.modrinth.com"
    }:
        raise ValueError("Unapproved DH download host: " + str(host))
    with urlopen(Request(url, headers={"User-Agent": AGENT}), timeout=90) as r:
        size = r.headers.get("Content-Length")
        if size is not None and int(size) > 60_000_000:
            raise ValueError("Distant Horizons download unexpectedly large")
        result = r.read(60_000_001)
        if len(result) > 60_000_000:
            raise ValueError("Distant Horizons download size limit exceeded")
        return result

def select_jar(metadata):
    if metadata.get("id") != VERSION_ID or metadata.get("project_id") != PROJECT_ID:
        raise ValueError("Unexpected Modrinth project/version")
    if EXPECTED_MC not in metadata.get("game_versions", []) or "neoforge" not in metadata.get("loaders", []):
        raise ValueError("DH release does not support NeoForge 1.21.1")
    files = [x for x in metadata.get("files", [])
             if x.get("filename", "").endswith(".jar")
             and "DistantHorizons" in x.get("filename", "")
             and x.get("hashes", {}).get("sha512")]
    if len(files) != 1:
        raise ValueError(f"Expected one signed-release-metadata jar, found {len(files)}")
    return files[0]

def install(root):
    meta = json.loads(retrieve(API_URL))
    release = select_jar(meta)
    blob = retrieve(release["url"])
    actual = hashlib.sha512(blob).hexdigest()
    expected = release["hashes"]["sha512"].lower()
    if actual != expected:
        raise ValueError("DH checksum mismatch: refusing jar installation")
    output = root / "build" / "dh-compat" / release["filename"]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(blob)
    for sub in ("run/mods", "run/server/mods", "run/gametestserver/mods"):
        dest = root / sub
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(output, dest / output.name)
    print("REALSCALE_DH_OFFICIAL_JAR_VERIFIED",
          json.dumps({"version": meta.get("version_number"),
                      "id": meta["id"], "sha512": actual,
                      "jar": release["filename"],
                      "bytes": len(blob)}, sort_keys=True), flush=True)

if __name__ == "__main__":
    install(Path(sys.argv[1]) if len(sys.argv) > 1 else Path("neoforge"))
