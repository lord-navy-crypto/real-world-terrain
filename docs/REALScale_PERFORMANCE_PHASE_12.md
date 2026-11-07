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
