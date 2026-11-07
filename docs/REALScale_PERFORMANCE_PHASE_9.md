# RealScale Performance Optimization — Phase 9

This phase targets measured/interpolated terrain cost without changing the
terrain signal, vertical density gradient, cave graph, world height, or chunk
coordinates.

## Changes in this branch

### Horizontal height-signal reuse

- The Natural/Sacred profile already uses an exact per-worker horizontal memo
  and native `cache_2d`; that path is retained.
- The six production fitted profiles already place
  `realscale:measured_horizontal_memo` around their height signal in
  `terrain_density`. This branch deliberately keeps that single wrapper and
  does not add a second cache around the same fitted signal.
- Alpine Smoothed and Canyon Smoothed did not previously have the horizontal
  memo. Their unchanged `mountain_relief` height signal is now wrapped once.
- The wrapper is only valid because the wrapped inputs are horizontal:
  `MountainReliefDensity` and `TerrainArchetypeDensity` use X/Z coordinates,
  their base and continental signals resolve to flat-cached horizontal graphs,
  and the erosion selector uses `y_scale: 0`.
- The Y-dependent `y_clamped_gradient` remains outside the memo. Final density,
  cave density, aquifers, and the 2096-block world bounds are not cached or
  rewritten.

The memo uses the existing bounded, per-mapped-density, per-worker cache.
Evictions only cause recomputation. Cache hits return the exact original
`double`; no interpolation grid, sample location, coefficient, or height is
approximated.

### Memo diagnostics

When `-Drealscale.density.profile=true` is enabled, the profiler now reports
horizontal memo hits, misses, and hit rate. With the property absent, the
atomic counters are not touched. The fitted-worldgen CI smoke enables this
diagnostic only for its isolated test server and records the summary at
shutdown.

### USGS bicubic interpolation

- Select the clamped source row once per horizontal interpolation instead of
  re-clamping the same row and four columns for each spline corner.
- At the north/south raster edges, do not evaluate the one row whose result is
  discarded by the existing endpoint extrapolation.
- Preserve the same source values, endpoint extrapolation, spline arithmetic,
  and interpolation order.

A JUnit regression compares the optimized row and complete measured-height
results against the frozen original implementation using raw IEEE-754 bits.
A same-process microbenchmark first checks every sample for raw-bit equality,
then reports the median interpolation-only speed ratio. It is not presented as
full chunk-generation throughput or FPS.

## Optional Distant Horizons cave LOD culling

The branch adds a first, deliberately limited implementation of the underground
visibility idea for **Distant Horizons' distant fake-chunk cave geometry**.
It is disabled by default and requires:

```text
-Drealscale.experimental.client.underground_lod_culling=true
```

When enabled:

- A confidently classified `OPEN_SURFACE` may enable DH's
  `caveCullingEnabled` option.
- `SEALED_CAVE` and every `TRANSITION`/unknown state disable that culling,
  revealing cave LOD geometry again.
- The classifier samples more frequently (0.5 seconds) while this option is on.
- Open cuts/ravines are treated conservatively using nearby heightmap probes.
  If any required neighboring chunk is not already loaded, the classifier
  fails open; it never force-loads a chunk to decide visibility.
- The adapter uses DH's public config API reflectively, only takes control if
  the config is API-overridable and no other API override is present, and
  clears its override when leaving a world so the user's saved DH setting is
  restored. It does not change `caveCullingHeight`.

This affects **DH distant LOD cave visibility only**. It does not delay vanilla
nearby section mesh builds, alter world generation, or remove underground
blocks. The relevant API contracts are documented in the
[Distant Horizons delayed API config](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/DhApi.Delayed.html),
[graphics config](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/interfaces/config/client/IDhApiGraphicsConfig.html),
and [config value interface](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/interfaces/config/IDhApiConfigValue.html).

## Vanilla underground section deferral: status and safe implementation boundary

The existing `AdaptiveVisibilityBudget` priority scores and
`ClientChunkUploadBudget` are not yet connected to a per-section task scheduler.
The DH LOD controller above does not solve that separate problem.

A simple Y-threshold or blanket "do not render underground" switch is unsafe:
caves can be visible through ravines, skylights, shafts, cliff openings,
water-filled passages, and connected cave mouths. Delaying a section that is
actually visible would create holes or late pop-in. World generation and saved
block data must never be skipped as part of this rendering optimization.

The safe target is section-level *mesh scheduling*, not terrain generation:

1. Profile section compilation, upload queues, and visible-section work while
   the player is on open surface, in a confirmed sealed cave, and in a
   transition area.
2. Identify each queued section and consult only already-loaded local
   visibility/occlusion data. Never request or force-load a chunk to classify it.
3. Keep player-visible sections, camera-changed sections, cave exits, and
   uncertain sections at normal/high priority. Only positively occluded
   underground sections may be delayed as background work.
4. Keep every deferred task queued; use bounded per-frame work and starvation
   protection. Unknown data, portals, skylights, teleports, underwater views,
   and unsupported dimensions must immediately fail open to vanilla behavior.
5. Start behind an opt-in flag and verify vanilla, Distant Horizons 3.3.3, and
   Chunky 1.4.23 together before considering broader renderer compatibility.

No section-culling mixin is enabled by this phase. A renderer integration will
be added only after its target API and per-section task identity are verified;
the existing advisory classifier is not sufficient evidence to hide geometry.
