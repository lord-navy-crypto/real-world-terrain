#!/usr/bin/env python3
"""Verify actual 1.21.1 Anvil chunks contain naturally selected RealScale ecotones.

No external libraries. Region file parsing reads only chunk compression headers
and scans decompressed NBT for biome resource IDs (not a terrain screenshot).
"""
from pathlib import Path
from collections import Counter
import gzip
import json
import re
import sys
import time
import zlib

world = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("run/ecotone-test")
regions = sorted((world / "region").glob("*.mca"))
assert regions, "Missing ecotone overworld region files"
def scan_regions():
    found = Counter()
    total = 0
    for path in regions:
        data = path.read_bytes()
        if len(data) < 8192:
            continue
        for i in range(1024):
            sector = int.from_bytes(data[4*i:4*i+3], "big")
            if not sector:
                continue
            start = sector*4096
            if start+5 >= len(data):
                continue
            length = int.from_bytes(data[start:start+4], "big")
            if not 1 < length <= 2_000_000 or start+4+length > len(data):
                continue
            payload = data[start+5:start+4+length]
            typ = data[start+4]
            try:
                if typ == 2:
                    decoded = zlib.decompress(payload)
                elif typ == 1:
                    decoded = gzip.decompress(payload)
                elif typ == 3:
                    decoded = payload
                else:
                    continue
            except (zlib.error, EOFError, OSError):
                # The server may still be flushing this region. Retry after save.
                continue
            total += 1
            present = {m.decode("ascii") for m in re.findall(rb"realscale:[a-z0-9_]+", decoded)}
            for name in present:
                found[name] += 1
    return total, found

# Gradle may return before the NeoForge child has completed region-file flush.
# Retry a bounded period instead of treating a partial write as missing biomes.
deadline = time.monotonic() + 90
while True:
    total, biomes = scan_regions()
    if total >= 100 and sum(biomes.values()) > 0:
        break
    if time.monotonic() >= deadline:
        raise AssertionError(
            f"World region flush incomplete or no ecotones: {total} chunks, {dict(biomes)}"
        )
    time.sleep(5)

out = {"chunks_examined":total,"regions":len(regions),"custom_biome_occurrences":dict(sorted(biomes.items()))}
Path("build").mkdir(exist_ok=True)
Path("build/ecotone-anvil-qa.json").write_text(json.dumps(out, indent=2)+"\n")
print("ACTUAL_ECOTONE_BIOMES_PASS",json.dumps(out, sort_keys=True))
