#!/usr/bin/env python3
"""Compare last recorded RealScale feature profiles; no synthetic speedup claims.

These are feature.place() timings, NOT total chunk generation time or FPS.
Usage:
  python3 scripts/compare_worldgen_profiles.py baseline.log optimized.log
"""
import re
import sys
from pathlib import Path

PATTERN = re.compile(
    r"\[RealScale worldgen profile\] (RIVER|GRAND_ECOLOGY|SKY_ISLAND)"
    r" calls=(\d+) mean_us=(\d+) p95_bucket_upper_us=(\d+) max_us=(\d+)"
)


def parse(path):
    observations = {}
    for line in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
        match = PATTERN.search(line)
        if match:
            section, calls, mean, p95, maximum = match.groups()
            item = (int(calls), int(mean), int(p95), int(maximum))
            if section not in observations or item[0] >= observations[section][0]:
                observations[section] = item
    return observations


def compare(before, after):
    lines = ["Feature           Baseline calls  Optimized calls  Mean before/after (us)  Ratio",
             "---------------------------------------------------------------------------"]
    for section in ("RIVER", "GRAND_ECOLOGY", "SKY_ISLAND"):
        a, b = before.get(section), after.get(section)
        if not a or not b:
            lines.append(f"{section:18}  missing comparable samples")
            continue
        ratio = (a[1] / b[1]) if b[1] else None
        result = f"{ratio:.2f}x" if ratio is not None else "n/a"
        lines.append(f"{section:18} {a[0]:14} {b[0]:16} "
                     f"{a[1]:>7}/{b[1]:<7} {result}")
        if abs(a[0] - b[0]) > min(a[0], b[0]) // 10:
            lines.append("  WARNING: Counts differ by >10%; compare equivalent seeded runs.")
    lines.append("CAUTION: Rounded means and logarithmic p95 bins are diagnostic, not")
    lines.append("proof of reduced full-chunk latency, FPS, or lower memory usage.")
    return "\n".join(lines)


def main(argv):
    if len(argv) != 3:
        print("Usage: compare_worldgen_profiles.py BASELINE.log OPTIMIZED.log", file=sys.stderr)
        return 2
    print(compare(parse(argv[1]), parse(argv[2])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
