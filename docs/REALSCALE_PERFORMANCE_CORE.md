# RealScale Performance Core

This is an opt-in control layer for RealScale's own bounded world-generation caches and the existing guarded client upload experiments.

## Profiles

Set one JVM property:

```text
-Drealscale.performance.profile=balanced
```

| Profile | Cache capacity | Client behavior |
|---|---|---|
| `balanced` | Existing capacities | Existing defaults; experimental client hooks stay off |
| `low_memory` | One quarter of each major cache, minimum 16 slots | No client render hook is enabled |
| `smooth_render` | Existing capacities | Enables the existing mode-aware FIFO upload budget, frame-pressure governor and optional DH cave-LOD adapter |
| `low_memory_smooth_render` | One quarter of each major cache | Combines the two opt-in behaviors |

The default is `balanced`. The profile never changes worldgen equations, noise, DEM samples, terrain height, cave geometry, biome selection, registries or saved-world data.

## Memory behavior

The compact profile resizes only memoization tables:

- Real DEM column cache: 4096 → 1024 slots
- Measured horizontal memo: 8192 → 2048 slots
- Ecotone base-biome cache: 8192 → 2048 slots
- Ecotone resolved-biome cache: 2048 → 512 slots
- Mountain site cache: 64 → 16 slots
- Sky-island site cache: 256 → 64 slots

A cache miss or eviction always falls back to the original deterministic calculation. The compact profile therefore trades some cache hit rate and possible world-generation CPU time for lower RealScale-owned cache-array memory. It is not a GPU-memory setting.

For an approximate one-set cache-array estimate, add:

```text
-Drealscale.performance.report=true
```

The estimate excludes value objects, JVM object headers, additional density-function instances and caches owned by Minecraft or other mods. It is not a measurement of total process memory.

## Client rendering behavior

`smooth_render` reuses the existing conservative mechanisms rather than installing a new renderer or changing Sodium/Embeddium internals:

- mode-aware upload pacing on the original vanilla FIFO upload queue;
- adaptive reduction of the extra upload allowance after repeated slow frames;
- Distant Horizons cave-LOD configuration through its optional API, only while the camera is confidently classified as open surface;
- automatic fail-open behavior for unknown states, cave mouths, ravines, teleports, unsupported dimensions and world changes.

The upload budget changes the timing of already-built mesh uploads. It does not reduce mesh compilation, GPU draw calls, visible Section count, or GPU memory. It can delay the appearance of distant terrain, so compare it against the balanced profile on the same pre-generated world before keeping it enabled.

Predictive FIFO pacing remains separately opt-in:

```text
-Drealscale.experimental.client.uploadbudget.predictive=true
```

## Diagnostics

The existing client queue report now also records average/peak JVM heap usage and the configured maximum heap alongside pending compile/upload queues and minimum free mesh buffers. Heap figures are JVM heap measurements, not GPU VRAM measurements.

Enable queue reporting without enabling the upload Mixin:

```text
-Drealscale.client.chunkqueue.profile=true
```

The ordinary balanced profile adds no continuous cache profiler or render-frame sampler.

## Correctness and acceptance

- All cache sizes remain powers of two and bounded.
- Cache collisions and evictions only cause recomputation.
- Tests exercise reduced-capacity eviction paths and compare original deterministic values.
- NeoForge build and existing terrain/Anvil acceptance workflows remain mandatory.
- Client A/B must use identical renderer, shader, GPU, view distance, resolution, save and camera path. Compare p95/p99 frame intervals, queue backlog, heap usage and terrain appearance latency.
- No FPS improvement is claimed by unit tests or dedicated-server CI alone.

This module is a safe first layer, not a substitute for a renderer-supported Section visibility integration. RealScale continues to avoid Y-only underground clipping and private Sodium/Embeddium renderer mixins.
