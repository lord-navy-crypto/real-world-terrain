# Experimental exact original PerlinNoise memo for Minecraft 1.21.1

Branch experiment/vanilla-perlin-exact-memo. Main unchanged. CPU optimization candidate, not a released speed result.

Previous JFR seed 42 original 2096-high distant worldgen had 14,544 ExecutionSamples: Improved/Perlin ~29.2%, Beardifier ~27.9%, aquifer ~11.7%. Leaf samples are approximate not exact CPU time.

Mixin opt-in `-Drealscale.experimental.perlin.instrument=true` targets six-argument getValue(DDDDDZ)D; cache separately enabled by `-Drealscale.experimental.perlin.cache=true` and sampled original replay by `-Drealscale.experimental.perlin.verify=true`. 64-slot per-worker cache requires original object identity, raw bits of all five doubles and identical boolean; collisions only evict and NEVER approximate a result. Strict raw-bit comparison includes -0.0 and NaN. Cache OFF by default.

Performance: four clean full-height real NeoForge servers per seed: vanilla, cache, vanilla repeated as drift reference; fourth cache with sampled original replays for correctness only. Presets Measured seed 2 and Sacred seed 42. Full chunk completion and saved chunks required, not RCON ACK. Nonzero real hit/replay evidence is required. Compare both vanilla controls; repeat with randomized order before considering promotion.

Aquifer remains entirely vanilla. Its NoiseBasedAquifer.computeSubstance mutates fluid tick scheduling state; caching a returned BlockState without reconstructing every side effect risks fluid updates and caves. Use independent JFR method-stack classification before considering any aquifer changes.

No structures, octaves, density formula, aquifer, cave, water, rendering or world height changed. No speed claim before actual runs.

## Independent saved-block attribution (additional protection)

New `triage_vanilla_three_worlds.py` decodes persisted Anvil palettes and checks the same actual voxel, biome quart, Heightmap and structure metadata at all three virgin distant waypoints for original vanilla #1, Perlin memo candidate, original vanilla #2. It distinguishes differences already seen between vanilla/vanilla controls from candidate-only or third-value divergences, records real block coordinates and JSON reports, and **does not wave through candidate-only changes**. Saved-world difference analysis is diagnostic and non-gating until natural feature-order nondeterminism is properly understood. A release still requires deterministic structural parity and no unexplained candidate-only fluid/block changes.

## Alternate candidate 2026-10-22: single-last-value strategy
Branch: `experiment/vanilla-perlin-last-value`, stacked on the separate experimental Perlin work, never main. This REPLACES the 64-slot hash cache with **one cached result per worldgen thread**. If the next vanilla Perlin invocation is not precisely the same noise instance and five raw-double arguments plus boolean, execute vanilla normally. It requires no hash, index, 64-slot array probe or eviction logic, only an identity test, raw scalar equality, and thread-local access. All previous bit-for-bit replay rules and 4-server A/B/A controls remain active.

The former 64-slot Perlin memo (PR #80) showed 23–26% cache hits but **failed reliable performance**: Measured both runs slower (0.35% and 3.07% against the faster reference), Sacred +5.28% once but -4.60% in another run. Candidate-only Anvil differences also require review. Thus the new last-call strategy should be rejected immediately if the actual hit rate is too low, if neither preset reliably beats both controls, or if any new block/biome/fluid mismatches remain unexplained. The strategy is explicitly provisional, NOT a speed increase claim.
