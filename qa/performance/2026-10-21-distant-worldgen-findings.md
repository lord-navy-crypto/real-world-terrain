# RealScale distant generation: performance decisions — 2026-10-21

## Testing contract

All timings below come from real NeoForge dedicated servers run on the same
GitHub Actions runner per preset, using **fresh 2096-high worlds**, normal
structure generation, seed 42 (Sacred Timeline) and seed 2 (Measured).
Each sample is the sum of three complete chunk-load times at (1024,1024),
(-2048,1536), and (4096,-2048); `/forceload` ACK time alone never counts.
Reported changes are **single, baseline-first A/B pairs**, not stable averages.
The dedicated far-route workflows separately check saved ground/cave continuity.

## Decisions

| Experiment | Measured before → after | Sacred before → after | Decision |
|---|---:|---:|---|
| Remove 2048-entry fully resolved ecotone cache | 92.657s → 93.447s (+0.85%) | 88.858s → 92.683s (+4.30%) | REVERT: no observed benefit |
| Distance-first ecotone rays, stable heading ties | 110.865s → 111.912s (+0.94%) | 71.678s → 81.145s (+13.21%) | REVERT: slower |
| Expand underlying climate sample cache 8192 → 32768 | 88.616s → 88.320s (−0.33%) | 82.359s → 81.186s (−1.42%) | REVERT: marginal gain vs ~0.3MB/worker overhead |

A positive sign is not proof of stable improvement; each A/B job ran once,
with baseline first. Do not cite percentages as guaranteed speedups.

## Diagnostics from actual Sacred Timeline seed 42

- **Stable 8192-entry underlying climate cache:** 68,913,805 hits,
  24,092,630 misses (74.10% hit rate), about 4,949,232 fully resolved
  ecotone queries; whole-result cache had only 1,434 hits (0.03%).
- **No whole-result cache:** 24,092,583 underlying misses — essentially
  unchanged from stable.
- **Distance-first rays:** 24,001,307 underlying misses (only 0.38% fewer
  than stable). Synthetic boundary-rich fixtures saved more probes, but actual
  long-distance generation did not.
- **32768-entry underlying cache:** 24,013,451 underlying misses (only
  0.33% fewer); hit rate 74.18%.

These counters are real server call counts, not sampled extrapolations.
Density-stage mean nanoseconds use 1/1024 sampling; nested inclusive
stage costs cannot be summed as independent CPU percentages.

## Verifiable runs

- Whole-result cache removal paired A/B:
  https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39588336663
- Distance-first rays paired A/B:
  https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39589626298
- Expanded underlying cache paired A/B:
  https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39590789148
- Stable climate hit-rate diagnostic:
  https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39584982740
- 32768-slot climate hit-rate diagnostic:
  https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39590789253

## Next optimization target

The profiler exposes about 24 million **actual, mostly unique** vanilla
climate computations for this three-stop route, despite a useful 74% base
cache hit rate. Cache shuffling, higher capacity and reordered ray loops do
not remove enough source computations to shift end-to-end latency.

Before changing output semantics or world height, profile the actual vanilla
`Climate.Sampler.sample` and `MultiNoiseBiomeSource.getNoiseBiome`
call stacks (e.g., opt-in JFR in a real dedicated-server run). Look for
safe, exact cross-height noise reuse or expensive repeated density lookups.
Retain worldgen formulae, cave generation, ocean biomes, seed determinism,
and structure generation; any new optimization needs output parity and
same-runner server timings. 
