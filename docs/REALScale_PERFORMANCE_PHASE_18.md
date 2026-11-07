# Performance Phase 18 — Exact DEM far-field rejection and virtual underground boundary

## Real-data calculation path

The seamless USGS DEM density now checks whether a column is outside the exact circular fade support before touching its per-worker column cache. The rejection uses integer squared distances after a bounded rectangle check. It is exact at the cardinal and circular fade boundary: every rejected column has the same zero influence as the original smoothstep calculation.

This matters because the procedural world extends far beyond the small measured DEM region. Previously, every density worker that evaluated the seamless DEM wrapper could initialize a 4096-slot per-worker cache even when it was generating terrain thousands of blocks away. Far-field columns now return to the unchanged procedural base before cache allocation, hashing, or interpolation.

The cache and interpolation path remains unchanged for all columns inside the DEM or its nonzero transition ring. Tests compare the support guard with the original fade weight over the full test grid, four fade widths, signed coordinates, and integer extremes.

## Performance attribution

The optional realscale.density.profile sampler now reports separate sampled timings for:

- Legacy bundled real-DEM density
- Seamless measured real-DEM density
- Fitted procedural terrain archetype density
- Existing cave, biome, and mountain-relief stages

It samples one in 1024 calls per worker and reports mean, coarse P95 bucket and maximum sampled call time. These are density-function call measurements, not chunk completion time, FPS or GPU time. Profiling remains disabled by default.

## Virtual underground boundary

The client-side DH cave-LOD classifier now derives a virtual local surface boundary from five already-loaded MOTION_BLOCKING_NO_LEAVES heightmap columns. The boundary is the minimum sampled surface height, not an average, so all five local columns must provide at least 12 blocks of roof clearance.

If the five heights span more than 24 blocks, or if the player is not at least 12 blocks below the conservative boundary, the classifier returns to TRANSITION and fails open. Existing sky probes, ravine checks, bounded exterior sightlines, teleport invalidation, and no-forced-load rules remain in place.

This boundary is computed in memory only. It creates no marker blocks, block entities, extra dimension, chunk data or saved-world migration.

## Rendering scope and limitations

The virtual boundary improves conservative classification for the existing opt-in Distant Horizons cave-LOD setting. It does not yet suppress vanilla nearby section meshes, skip underground chunk generation, or replace Distant Horizons' own LOD system. The optional vanilla upload budget remains a separate, opt-in scheduler for already-built upload tasks.

## Verification required

- Gradle 8.14.3 and 9.8 NeoForge/JUnit test suites
- Raw-bit DEM fade parity and exact far-field support tests
- Fitted terrain morphology raw-bit reference tests
- Virtual underground boundary unit tests
- Existing cave/visibility policy tests
- Seven fitted-world saved-heightmap comparison at identical coordinates (still a separate CI test-design blocker)
- Pinned Distant Horizons 3.3.3 + Chunky 1.4.23 in-game validation before enabling the feature by default
