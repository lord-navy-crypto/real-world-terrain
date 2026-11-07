# Phase 26 — Measured before/after Nether cave cache A/B

The 2026-11-08 Phase 25 change introduced per-worker 8-entry region caches
and two-way per-region column masks. Phase 26 adds a frozen, exact copy of
the pre-Phase-25 production evaluator, and benchmarks both versions on the
same deterministic coordinate sequence in one JVM process.

Workloads:
- `region_alternating`: four distant 256-block cave regions revisited
  repeatedly, with seven height samples per column.
- `column_hash_collision`: two interior Nether columns intentionally
  colliding in the former one-way column-mask index.

Both paths use `strengthFromColumn`, the real noise-warp-safe CPU evaluator.
The test compares raw IEEE-754 bits for all input probes before measurement.
After warmup it alternates the benchmark order and reports the median of seven
rounds of 48,000 samples. Output includes `pre_phase25_ns_per_sample`,
`phase25_ns_per_sample`, and `speedup`.

The script is a CPU microbenchmark, not an end-to-end new chunk generation,
GPU frame-time or Minecraft FPS measurement. No minimum speedup is required
to pass CI: correctness is gated, performance is reported as diagnostic
evidence and should be assessed across repeated runners.

This frozen baseline only lives in `src/test`; production worldgen remains
exactly as in Phase 25. The tall 2096-block Overworld, real measured DEM,
cave geometry, DH/Chunky settings, architecture templates and save format
are untouched.
