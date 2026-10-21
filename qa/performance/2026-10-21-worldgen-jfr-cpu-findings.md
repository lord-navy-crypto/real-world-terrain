# RealScale 2096-high distant worldgen — JFR CPU evidence and safe optimisation decisions

Date: 2026-10-21. Production branch: Java 21, NeoForge 1.21.1.
Report based on **actual** Sacred Timeline worldgen, seed 42, normal structures,
three virgin coordinates (1024,1024), (-2048,1536), (4096,-2048).
Strict 180-second complete-load checks and the original 2096 height remain.

## 1. Genuine CPU hotspots (not speculative caches)

Real server JFR `jdk.ExecutionSample` recording contained **14,544
execution samples**, overwhelmingly on the actual generation workers.
The raw 4 MB recording is attached to [Actions run 39594119359](
https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39594119359)
as `RealScale-Actual-2096-Sacred42-JFR`.

Exclusive/top-leaf *sample* classification over all 14,544 samples
(rounded; categories below are mutually exclusive):

| Closest actual CPU leaf family | Samples | Approx share of captured samples |
|---|---:|---:|
| Vanilla Improved/Perlin/noise internals | 4,243 | 29.2% |
| Vanilla structure blending `Beardifier` | 4,052 | 27.9% |
| Vanilla aquifer computation | 1,707 | 11.7% |
| Other vanilla terrain / chunk work | 1,971 | 13.6% |
| Other RealScale methods | 948 | 6.5% |
| Other server code | 705 | 4.8% |
| Biome methods | 547 | 3.8% |
| RealScale custom cave wrapper/network | 371 | 2.6% |

Top individual JVM leaves:
`ImprovedNoise.p` 2,814;
`Beardifier.compute` 2,743;
`Aquifer.NoiseBasedAquifer.computeSubstance` 1,624;
`PerlinNoise.getValue` 1,408;
`Beardifier.getBeardContribution` 1,254;
`DensityFunctions.PureTransformer.compute` 1,006;
`ThreadLocalMap.getEntry` 561;
`Climate.RTree.SubTree.search` 434.

**Careful:** leaf categories are sampled call-stack labels, not precise
CPU seconds. Parent calls such as `Beardifier.compute` and its child contribution
overlap if using inclusive stacks: DO NOT sum inclusive calls. Sampling includes
some boot/spawn CPU, not an isolated single chunk microbenchmark. Repeated,
time-window-scoped runs would strengthen the inference.

## 2. Critical counterexample: Do NOT remove the outer Sacred XZ memo

JFR contains ~1,296 **inclusive** stack samples touching
`MeasuredHorizontalMemoDensity.compute`. Its `ThreadLocal` access looks
expensive. Removing the wrapper while retaining `minecraft:cache_2d`
was experimentally tested on the same runner, with normal structures.

[Same-runner A/B run 39596008761](
https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39596008761)
produced for Sacred seed 42:

| Fresh remote point | Stable double memo | Cache2D alone |
|---|---:|---:|
| (1024,1024) | 17.749s | 51.665s |
| (-2048,1536) | 12.996s | 13.378s |
| (4096,-2048) | 19.909s | 46.608s |
| **Total** | **50.654s** | **111.651s** |

In this paired run, **+120.42% slower overall** (two points over twice as slow).
Thus the outer memo prevents very expensive repeated height/spline evaluations
that Minecraft's native 2D cache alone does not retain during these queries.
The experiment was **reverted in [commit d930a8e](
https://github.com/lord-navy-crypto/real-world-terrain/commit/d930a8ebb38021e895c9e0477b413721f2cdc3d5)**.
Comparing `a52dfe6...d930a8e` confirmed **ZERO resulting production-source changes**.
The reverted commit passed source integrity, dedicated server, measured 180s,
and Sacred/Natural far-distance tests.

A single baseline-first A/B pair cannot prove the precise general slowdown,
but this is a strong rejection of the candidate, not a positive optimization.
Never remove the Sacred outer XZ memo merely because ThreadLocal appears hot
in JFR.

## 3. Highest-impact next work

**Vanilla structure density fusion** (`Beardifier`) and Perlin/Aquifer are the
dominant places to investigate. Inspect the exact Minecraft 1.21.1 implementation
and isolate terrain/noise/structure code with repeatable JFR before implementing
conservative optimizations. An early-out in `Beardifier` must prove that **no
structure piece or jigsaw junction can contribute** to the current density at
every applicable coordinate. Never disable structures, set world height lower,
skip aquifers, alter seeded noise, or silently lose terrain/biome parity.

Potential separate validation on **experimental branches**: per-block and
heightmap NBT parity for all tested regions, multiple seeds/negative coordinates,
A/B/A repeat sequences, comparable JFR capture windows and saved-world audits.

**Immediate safe improvement in this report:** fix JFR class-name decoding:
JVM `jfr print --json` emits class owners as `net/minecraft/...`, not
`net.minecraft...`. Earlier parser included only ~2,343 of 14,544 relevant
stacks when attempting dotted-prefix classification. The updated parser
normalizes both and adds a real-format regression test; the raw JFR sample
counts above were independently checked with normalized names.

The key outcome is **knowing which optimizations NOT to ship**, and now
having an evidence-based CPU bottleneck list. No claimed general percentage
speedup beyond a measured, reproducible comparison.
