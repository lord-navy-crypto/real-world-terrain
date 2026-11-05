# Vanilla Beardifier Y-culling — tested experimental branch

**Scope:** ONLY `experiment/vanilla-beardifier-height-cull`. Minecraft 1.21.1
NeoForge, Sacred Timeline seed 42, Measured seed 2. Default mixin OFF.
Production `main` has not received any part of this experiment.

## Confirmed changes

- New opt-in Mixin checks if the block Y is outside a conservative support
  envelope of all local vanilla structure bounding boxes and jigsaw junctions.
- If the envelope metadata is missing or untrusted, execution falls back to
  vanilla computation instead of skipping.
- When `-Drealscale.experimental.beardifier.verify=true`, every 4096th
  eligible skip re-executes the untouched vanilla compute in exactly the same
  live context and requires bitwise **+0.0**, or raises an error.
- Explicit per-run vanilla/experiment/vanilla-control dedicated server jobs;
  persistent Anvil semantic comparison is diagnostic only because vanilla
  parallel decorations themselves are not always byte-for-byte reproducible.

## Real server result: successful run 39611368665

| Preset | Vanilla first | Optimized, verifier on | Vanilla repeat | Advantage vs faster vanilla |
|---|---:|---:|---:|---:|
| Measured seed 2 | 87.095s | 76.735s | 84.838s | 9.55% |
| Sacred Timeline seed 42 | 94.317s | 73.175s | 81.874s | 10.63% |

Measured exact-candidate-skip *same-context* vanilla replays:
**42,089 samples** over **172,402,944 eligible calls** (all zero).
Sacred: **44,865 samples** over **183,769,344 eligible calls** (all zero).

> Do not present these as guaranteed machine-independent improvements. Runs
> varied even between two identical vanilla fresh server launches; future
> randomized A/B/A orders and parity near real structures are required.

Original trial
https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39605501964
and validated same-call run
https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39611368665 .

## Stability constraints

Keep 2096 world height, all structure sets enabled, original noise/density,
terrain shape, cave and underground fluids. Independent experiment **does not**
modify main. Persisted chunk byte identity is NOT a complete correctness oracle:
in actual virgin worlds even vanilla-vs-vanilla showed a few snow/fluids/ore/
leaf differences, presumably from interleaved generation/decorations. The
experiment is not approved for a mainline merge on this evidence alone.
