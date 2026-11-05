# RealScale Integrated TEST MAIN — 2026-10-22

**Branch:** `test/main-optimization-integration-20261022`  
**Distinct test build:** `2.0.0-tva-preview.2-test.1`, Minecraft 1.21.1, NeoForge 21.1.249 / Java 21.  
**Parent stable main:** `0d03e228044ceb513adfc06b357253a6e3878d61`. Production main is unchanged.

## Included experimental modules (ALL OFF BY DEFAULT)

1. **Beardifier Y256 conservative vertical shortcut** ([PR #82](https://github.com/lord-navy-crypto/real-world-terrain/pull/82)): `-Drealscale.experimental.beardifier.ycull=true`. The original-vs-Y256 A/B/A showed +12.56% Measured and +19.06% Sacred in *one sample per preset*, with vanilla call zero-replay checks passing. Crucially, candidate-only Anvil block differences remain unexplained: **DO NOT ENABLE ON VALUABLE SAVES OR CLAIM PRODUCTION PARITY**. Verification mode is `-Drealscale.experimental.beardifier.verify=true` and must be OFF during actual speed benchmarks.
2. **Client vanilla chunk mesh upload pacing** ([PR #79](https://github.com/lord-navy-crypto/real-world-terrain/pull/79)): `-Drealscale.experimental.client.uploadbudget=true`. Client-only; FIFO original upload Runnables, per-frame soft time limit and 8-task default. Loading/menu and synchronous blocking drains use vanilla logic. Unverified on real physical client so FPS improvements are **not established**. Diagnostic without changing behavior: `-Drealscale.client.chunkqueue.profile=true`. Existing independent FPS profiler: `-Drealscale.client.fps.profile=true`.
3. **Read-only vanilla Aquifer instrumentation** ([PR #83](https://github.com/lord-navy-crypto/real-world-terrain/pull/83)): `-Drealscale.experimental.aquifer.audit=true`. Samples 1/256 original returns, density sign, fluid state and original fluid-update flag; does not cache or modify actual fluid generation. **This is NOT a speed optimization** and adds profiling overhead.

All three Mixin configurations are registered in the test build, but their plugin guards prevent applying patches by default. Stable minecraft vanilla behavior is the intended default; actual mod compatibility still needs regression testing.

## Specifically NOT merged

- Rejected Perlin 64-slot cache [PR #80](https://github.com/lord-navy-crypto/real-world-terrain/pull/80): measured slower twice and inconsistent Sacred improvement.
- Rejected single-last-Perlin cache [PR #81](https://github.com/lord-navy-crypto/real-world-terrain/pull/81): zero reuse out of ~442–465 million actual calls on EACH world preset and net slowdown.
- Rejected-for-now Beardifier Y64 [PR #78](https://github.com/lord-navy-crypto/real-world-terrain/pull/78): benefit inconsistent, saved-world divergence unexplained.

The historical experiment branches remain intact and accessible for diagnosis. This branch integrates only selected independently opt-in candidates.

## Tests and distribution

Workflow: `.github/workflows/integrated-test-main.yml`. The CI must:

- Verify source integrity, test-version labels, Mixin opt-in safeguards, missing rejected Perlin and Y64 behavior, three saved-world diagnostic unit tests.
- Execute actual standalone Java 21 smoke for Y256 geometry, client upload scheduler, Aquifer observational sampler.
- Build complete NeoForge mod and verify three configs and all classes are in the JAR.
- Upload `RealScale-Integrated-TEST-MAIN-NeoForge-1.21.1-JAR`, a clearly named downloadable CI artifact.
- Complete independent REAL NeoForge 2096-tall saved-chunk runs on Measured seed 2 and Sacred seed 42, each once with all flags disabled and once with Beardifier Y256 + Aquifer read-only analysis enabled. Require direct original vanilla Beardifier +0.0 replay checks and Aquifer diagnostics when enabled.
- Preserve per-server logs, timings and Anvil evidence.

Server tests cannot validate a physical GPU, shaders, Sodium/Embeddium compatibility, or client frame rate; **physical client testing remains mandatory**.

## Release and user safety policy

1. Use the test JAR only with fresh worlds or backed-up saves. Never overwrite main-version saves for trial validation.
2. Do not merge this integrated test branch to production main until reproducible saved-Anvil blocks/biomes/heightmaps/structure parity and independent rendering tests are verified. A green CI run does not waive these release blockers.
3. A further version bump and explicit release go/no-go are needed before a stable main merge; no experiment is enabled by default as part of test integration.
