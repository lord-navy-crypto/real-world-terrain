# TVA Sacred Timeline — chunk generation CPU diagnostics

Scope: Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. This is an **opt-in profiler**, not a chunk-generation engine rewrite.

## Why new chunks are slow while FPS remains smooth

Client FPS and local block interaction chiefly describe rendering/main-loop
smoothness, not fresh chunk throughput. RealScale's extremely tall overworld
uses many density samples per column. The total latency can be affected by
density cave graphs, biome/ecotone sampling, biome feature placement, scheduled
chunk dependencies, IO, Java garbage collection or other mods.

A build compiling successfully does **not** prove new chunks generate faster.

## Turn on sampling for one short, disposable test

Add these JVM arguments to the actual Minecraft instance in HMCL / Prism
launch configuration; setting them on an unrelated Gradle CLI is insufficient:

```
-Drealscale.density.profile=true
-Drealscale.worldgen.profile=true
```

Use the same clean seed, world preset, view distance, simulation distance, other
mods, Java, RAM allocation and new-coordinate exploration path for baseline
and optimized runs. Close the game normally, then open `logs/latest.log`.

Search for:
- `[RealScale density profile] OVERWORLD_CAVE`
- `[RealScale density profile] NETHER_CAVE`
- `[RealScale density profile] ECOTONE_BIOME`
- `[RealScale density profile] SKY_BIOME`
- `[RealScale worldgen profile] RIVER`
- `[RealScale worldgen profile] GRAND_ECOLOGY`
- `[RealScale worldgen profile] SKY_ISLAND`

Density/biome samples are sampled **1 in 1024 calls per thread per stage**.
Reported mean and approximate p95 histogram bounds are **per selected call**,
not total chunk generation or MSPT. Different stages have different call
frequencies: mean_us alone is insufficient to identify total CPU bottlenecks.

```text
[RealScale density profile] OVERWORLD_CAVE samples=100 stride=1024 mean_us=... p95_bucket_upper_us=... max_us=...
```

Sampling is disabled by default. Turn both switches OFF for performance A/B
timings after recording which stages are most expensive.

## Required full-chunk acceptance measurements

1. Compare **newly generated** chunks, not regions already cached on disk.
2. Record time for a fixed route / fixed number of new chunks, server MSPT,
   p50/p95 chunk generation wall-clock and GC pause data.
3. Inspect matching saved Anvil blocks/heightmaps from separate seed-identical
   worlds; the new density fast paths must not alter any terrain.
4. Verify no dimension/biome/preset regression.
5. Run several trials and alternate test order to reduce JIT/warm-cache bias.

No FPS or percentage speedup is claimed until actual gameplay measurements
confirm it. Do not merge experimental PR #32 with incomplete QA.
