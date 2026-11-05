# RealScale TVA — Performance-only phases 6–8

Scope: Minecraft 1.21.1, NeoForge, Java 21. **No new worldgen content.**

## Phase 6: Correctness-preserving hot path improvements

- RiverChannelFeature reuses the same biome holder and overhead block-state
  lookup when classifying each river column, and eliminates a per-call temporary
  integer array when reading the persistent river grade.
- GrandEcologyFeature stores immutable per-kind headroom and aquatic flags
  once at feature construction and reuses the origin fluid-state lookup.
- Prior DEM cache (Phase 3), Nether tunnel bend seed cache (Phase 4) and ecotone
  result cache (Phase 5) are inherited unchanged.

These are small reductions in redundant work. No percentage speedup is claimed.

## Phase 7: Opt-in worldgen feature profiling

**Off by default**; enable on the actual Minecraft JVM:

```sh
JAVA_TOOL_OPTIONS="-Drealscale.worldgen.profile=true" gradle --no-daemon runServer
```

The environment setting is inherited by child Java processes in common Gradle
launches. Check server logs for `[RealScale worldgen profile]` to confirm.
To disable, unset the environment variable and restart Minecraft.

For each category (river, grand ecology, sky islands), the profiler emits a
line after every 4096 feature placement calls:

```text
[RealScale worldgen profile] RIVER calls=4096 mean_us=42 p95_bucket_upper_us=127 max_us=300
```

Samples are **place() method durations**, not whole chunk generation times.
Histogram buckets are power-of-two microsecond intervals and their reported
p95 bound is approximate. Logging and sample collection are opt-in, with
constant-size process memory and low-frequency emissions.

For two equivalent fixed-seed exploration runs:

```sh
python3 scripts/compare_worldgen_profiles.py baseline.log optimized.log
```

Also profile the Minecraft tick/worker threads with spark and measure memory,
GC pauses, new chunks/sec and the p95 *full chunk* generation latency before
claiming complete worldgen speedup. Compare same hardware, Java version,
view distance, game rules, world preset and newly generated coordinate path.
A valid comparison must use an ungenerated route, not previously saved chunks.

## Phase 8: Release gates (must all pass before merging)

1. Source integrity, NeoForge Java 21 Gradle test/build.
2. USGS tile point fidelity, 4-side fade and old/new bit equality.
3. Nether cave geometry, roof/lava protection and old/new bit equality.
4. Sea floors, river source containment, structure and chunk seams.
5. Natural ecotones, APES ecology, sky archipelago, Nether/End server tests.
6. Four-family multi-seed cave/terrain quality on actual saved Anvil chunks.
7. Live memory/GC and full chunk throughput baseline comparisons.

Never lower acceptance thresholds, modify existing presets, or disable
previous functionality to obtain a faster benchmark.
Do not automatically merge a draft pull request with pending checks.

## Current release state

This file documents the performance work on its separate optimization branch,
not an already-shipped stable RealScale JAR. Actual merge and release require
the gate results above.
