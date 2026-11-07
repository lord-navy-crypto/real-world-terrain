# RealScale Performance Phase 10: Exact Fade-Region Rejection

## Scope

The previous DEM transition-weight cache already skips its rectangle exterior.
This phase adds an exact, integer squared-distance test that rejects points
outside the **actual circular fade region** before calling `Math.hypot` or
looking up its per-worker weight cache.

Both axes are clamped to `[0,fadeMeters)` by the pre-existing rectangle test,
so signed 64-bit squared distances cannot overflow in this branch, even when
an integer coordinate is near the Java `int` limit. A point on the fade
radius has exactly zero weight under the unmodified smoothstep expression.

No measured elevation, source raster, spline coefficient, base density,
vertical gradient, cave function, chunk coordinate, or saved-world format is
changed. `RealDemSeamlessDensity` also reuses the already-read X/Z locals.

## Verification

- Extended exact-byte parity: 4 tile corners, 4 fade widths, grid of points
  inside/outside each circle, cardinal boundaries, signed coordinates,
  extreme Java ints, plus parallel threads.
- Added repeatable microbenchmark measuring the complete cached fade path
  versus the original unclipped formula. This is not FPS or end-to-end worldgen.
- Updated legacy basin-and-plateau static tests to unwrap the same outer
  `measured_horizontal_memo` wrapper used by all 3 smoothed profiles while
  still comparing every underlying parameter, climate selector and cave graph.
- CI must pass before considering merger.

## Other outstanding tests

The measured-world quality gates report 16-block and 4-block height seams
although the deterministic interpolation parity test passes. Those gates
remain enabled and their cause must be audited separately against `main`.
Do not suppress their failures as a performance workaround.

## Exact DEM source-corner reuse

In the exterior region beyond *both* edges of the DEM rectangle, the clamped
X/Z coordinate is one of exactly four fixed source-raster corners. The
original calculation reevaluated the same three bicubic samples (corner,
X-axis inward neighbor, Z-axis inward neighbor) for each distinct exterior
column. The current implementation precomputes those twelve immutable sample
values when loading the source raster. The extrapolated slopes, clamping,
tapering, vertical density formula and output arithmetic order are **unchanged**.

The corner shortcut is valid for both the original 16x16 pilot and a
SHA-validated 65/129/257/513 grid; edge strips and interior columns keep
their original interpolation. A frozen original reference verifies raw
IEEE-754 bit equality at fractional boundary offsets and random quadrant
coordinates. An independent microbenchmark reports its limited hot-path
speedup; it must not be interpreted as overall worldgen acceleration.
