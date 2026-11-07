# Performance Phase 13

See `REALScale_PERFORMANCE_PHASE_12.md` for the preceding cache and DEM optimization phases. This phase adds per-column karst geometry reuse, a volcanic last-column fast path, and a fitted dune relief correction with raw-bit and one-block-equivalent tests.


## Phase 15 — Cache horizontal fitted morphology across vertical samples

Alpine ridge bands, canyon trough/shoulder profiles, glacial valley troughs,
and the base dune phase depend only on X/Z and immutable fitted coefficients.
They were being recomputed for every vertical density sample. A single
per-worker, bounded 1024-slot cache now stores those horizontal terms using
the full raw IEEE-754 X/Z bit patterns. The Dune selector offset remains
per-sample; Alpine's highland gate and selector contribution remain
per-sample; Canyon's signal-dependent cliff term remains per-sample. No
Y-dependent input is cached.

A new raw-bit reference test repeats columns through ten different vertical
samples, changing original density, continent gate and selector signal for
each family. The original uncached formulas are reproduced independently in
the test. This phase reduces repeated trigonometry/smoothstep work without
altering terrain output.
