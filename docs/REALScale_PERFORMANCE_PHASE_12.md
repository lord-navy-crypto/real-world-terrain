# Performance Phase 12 — Region geometry reuse for Fitted karst and volcano

The six Fitted terrain profiles already reuse horizontal height signals.
However, each *new* X/Z column in the Karst and Volcanic morphology kernels
still recomputed its fixed 2048×2048 region's seeded center coordinates.
Karst additionally recomputed the seeded sink-center coordinates.

A per-kernel, per-worldgen-worker, fixed-size one-region cache now remembers
only `cellX/cellZ`, center X/Z and sink X/Z. A region miss uses exactly the
same original site hash, offsets, casts and floating-point operation order.
A cache hit reuses the immutable source values. There are no cross-worker
shared writes, no infinite world-state cache, and no block reads/loads.

Volcanic continues to use its existing X/Z-specific shape cache. This new
region cache is independent and helps when generating new columns in the same
2048-block region. The last-region policy is an optimization only; even a
pathological oscillating region workload returns exact original values.

The JUnit test copies the original un-cached regional formulas and checks raw
IEEE-754 bits across region boundaries, positive and negative coordinates,
reordered calls, multiple continent gates and four worker threads.

Actual throughput must be judged from the genuine CI worldgen logs and
microbenchmarks, not inferred from these source-level changes.

## Square-boundary rejection before circular radial sampling

Volcanic and karst morphology originally called `Math.hypot` for every
horizontal column, including regions outside the possible lava shield,
karst tower and sinkhole footprints.

For positive, codec-clamped footprint radii, `abs(dx) >= radius` or
`abs(dz) >= radius` proves that the original circular smoothstep
contribution is exactly zero. We now evaluate the expensive `Math.hypot`
only when *both* differences lie within the bounding square, and still
compute the original circular distance for those candidates.

The karst ambient micro-noise remains active even outside all towers and
sinkholes, so its contribution is deliberately preserved. The volcanic
off-site output remains the original density. No terrain features are
removed. Additional raw-bit tests sample footprint edges and neighbors
on both axes and all signed quadrants.
