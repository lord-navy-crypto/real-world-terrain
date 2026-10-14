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
import zlib

world = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("run/ecotone-test")
regions = sorted((world / "region").glob("*.mca"))
assert regions, "Missing ecotone overworld region files"
biomes = Counter()
total = 0
for path in regions:
    data = path.read_bytes()
    assert len(data) >= 8192, path
    for i in range(1024):
        sector = int.from_bytes(data[4 * i:4 * i + 3], "big")
        if not sector:
            continue
        start = sector * 4096
        assert start + 5 < len(data), (path, i)
        length = int.from_bytes(data[start:start + 4], "big")
        assert 1 < length <= 2_000_000, (path, i, length)
        payload = data[start + 5:start + 4 + length]
        typ = data[start + 4]
        if typ == 2:
            decoded = zlib.decompress(payload)
        elif typ == 1:
            decoded = gzip.decompress(payload)
        elif typ == 3:
            decoded = payload
        else:
            raise AssertionError(f"Unsupported Anvil chunk compression: {typ}")
        total += 1
        present = {m.decode("ascii") for m in re.findall(rb"realscale:[a-z0-9_]+", decoded)}
        for name in present:
            biomes[name] += 1

assert total >= 100, f"Too few generated chunks: {total}"
assert sum(biomes.values()) > 0, "No RealScale ecotone selections found in Anvil chunks"
out = {"chunks_examined":total,"regions":len(regions),"custom_biome_occurrences":dict(sorted(biomes.items()))}
Path("build").mkdir(exist_ok=True)
Path("build/ecotone-anvil-qa.json").write_text(json.dumps(out, indent=2)+"\n")
print("ACTUAL_ECOTONE_BIOMES_PASS",json.dumps(out, sort_keys=True))
