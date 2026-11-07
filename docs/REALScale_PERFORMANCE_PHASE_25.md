# Phase 25 — Reuse prior Grand Overworld cave locality optimizations in the Nether

## Existing mechanisms preserved

- USGS Big Sur 3DEP source grid, bicubic interpolation and measured terrain
  fade remain unchanged.
- The 2,096-block Overworld height and climate WGS84/1:20 model are unchanged.
- The 4-layer Grand Overworld galleries, warp-safe 47-component column masks,
  entrance logic and caches are unchanged.
- The Grand Nether 2-layer halls, 19-component mask, corridors and satellites
  have exactly the same deterministic seed/hash/tunnel formulas.
- Distant Horizons, Chunky, client upload experiments and all architecture
  generation remain unchanged.

## New optimization

The Grand Nether's previous `ThreadLocal<CellCache>` held only the last
256-block cave region. Alternating structure-generation height queries
could rebuild halls, satellites and bend keys whenever they moved from
region A to region B and back.

Borrow the already validated Grand Overworld design:

1. Bounded 8-entry, two-way per-worker region cache. A/B/A stays resident.
2. Keep independent 512-slot, two-way column masks per region snapshot,
   each with epoch tags; geometry is never shared across region identities.
3. Add a last-exact-column shortcut for repeated vertical samples of the
   same X/Z column, avoiding hash and table probes.
4. Count cache *rebuilds* (only on misses) for deterministic smoke evidence.

All replacements merely cause recomputation. No density results, geological
structure, Minecraft blocks or saved data can change.

## Validation

- Frozen original Nether cave density reference: strict raw IEEE-754
  parity around halls, galleries, corridors, signed boundaries.
- Source-vs-masked cave evaluator parity for allowed noise warp.
- A/B/A region alternation and two colliding columns verify no repeated
  re-evaluation of their immutable geometries after warmup.
- CI pure Java smoke, Python guard, Gradle/NeoForge build and dedicated-server
  tests.

Any reported locality or kernel speedup is an isolated CPU result, not FPS,
measured full-chunk generation throughput, or evidence of client visibility.
