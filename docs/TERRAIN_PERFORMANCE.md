# RealScale terrain-generation optimization (experimental branch)

Branch: `feature/terrain-generation-optimization`. **Not a release and not merged into main.**

## Scope and correctness contract

This first iteration optimizes the USGS Big Sur DEM density function only. It does not
claim to accelerate every one of the eight world presets, nor does it alter
the original DEM input, procedural base density, 96-block transition, biome
placement, dimension height, or saved-world schema.

- For world columns outside or exactly on the DEM boundary, immediately
  delegate to the original procedural density, without creating a cache entry.
- Inside the measured region, cache the **X/Z-only** elevation and smoothstep
  weight. Minecraft samples many different Y positions for the same X/Z.
- Cache entries are keyed by signed X, signed Z and `blendMeters`, bounded to
  1024 direct-mapped slots **per generation worker thread**, not an unbounded
  world-wide cache. Collisions replace entries and cannot change correctness.
- Preserve the original order of floating-point operations in density blending.
  Identical world settings and sampled coordinates should yield bit-identical
  density values. Multithreaded sampling never shares mutable cache state.
- `RealDemTile.java` and the archived v1.1 binaries remain untouched.

The optimization adds a small constant thread-local memory cost. Cache misses
can be slower due to hashing/allocation. Real server benchmarking is essential
before claiming any speedup.

## Validation

Use Java 21 and Gradle 8.14.3.

```bash
git checkout feature/terrain-generation-optimization
python3 qa/verify_repository.py
mkdir -p build/dem-cache-smoke
javac -d build/dem-cache-smoke \
  neoforge/src/main/java/org/realscale/neoforge/RealDemTile.java \
  neoforge/src/main/java/org/realscale/neoforge/RealDemColumnCache.java \
  neoforge/src/test/java/org/realscale/neoforge/RealDemColumnCacheSmoke.java
java -cp build/dem-cache-smoke:neoforge/src/main/resources org.realscale.neoforge.RealDemColumnCacheSmoke
cd neoforge
gradle --no-daemon build
```

The standalone smoke test compares the optimized column samples and synthetic
density blends **bit-for-bit** with the unchanged original formulas, across DEM
boundaries, different blend widths, cache collisions, and concurrent workers.
This is not a substitute for testing actual generated Minecraft chunks.

For real-world validation, build JARs from both `main` and this branch, using
the **same** Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21 configuration.
Use separate fresh, disposable worlds (seed `785312`, preset
`realscale:usgs_big_sur`). Compare new chunk blocks and heightmaps at fixed
coordinates: `(0,0)`, `(±96,0)`, `(±200,0)`, `(±240,0)`,
and points beyond the 480 m footprint. Inspect seam continuity, caves,
structures and generated trees. Also test `realscale:realism_max` as the
outside-DEM regression baseline.

## Performance benchmark plan

1. Warm both JVMs with an equivalent workload, and alternate test order to
   reduce JIT and file-cache bias.
2. Compare **fresh-world** chunk generation with identical seeds, render/view
   distances, hardware, server configuration, mod lists and JVM heap limits.
3. Measure median and p95 chunk timings, wall-clock time to produce a fixed
   number of chunks, allocations/GC pauses and peak memory using a profiler
   such as spark or Java Flight Recorder.
4. Benchmark both DEM interiors and the much larger region **outside** the
   DEM separately. Watch for cache misses and allocation overhead.
5. Run at least three trials per version. Reject the change if any block-level
   regression, concurrency error, or meaningful performance regression is found.

No speedup percentage is asserted until real NeoForge benchmark evidence exists.

## Future iterations

Profile the unmodified Realism MAX noise-function graph before adding any
procedural-noise cache or thread scheduling changes. Avoid global mutable
caches, removing legitimate terrain, or modifying vanilla world-generation
threading. Rendering optimizations remain the domain of optional Sodium/Iris
and other client performance mods.
