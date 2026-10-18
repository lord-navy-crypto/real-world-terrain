# RealScale TVA — 2026-10-18 historical regression and release blocker register

**Status: NOT READY TO RELEASE.** This document is a fail-closed audit,
not a declaration that every historical defect is solved. Do not merge
Draft optimization PRs or publish until actual final-HEAD evidence exists.

## Evidence and classification

| Problem | Reproducible evidence | Correct test | Status |
| --- | --- | --- | --- |
| Distant worldgen with structures enabled | Four-family seed matrix in #32/#34/#37–#41, especially Measured 2/42, Sacred 2; 180s RCON timeouts | Fresh dedicated server, structures ON, 2,096-high Overworld, actual loaded chunk and saved Anvil proof | OPEN |
| Structure-location height query bottleneck | Consecutive thread dumps: `AlternateJigsawGenerator -> getBaseHeight -> NoiseChunk / spline/Perlin / GrandOverworldCaverns`; `Server thread` waits on chunk future | Compare same seed/position structures ON/OFF, JFR/async-profiler or sampled JVM stacks; report wall time + work duration | ROOT PATH OBSERVED, fix unproven |
| Measured preset / seed2 structure ON/OFF A/B | On former parent PR #40: structures ON `forceload` 180.1s timeout; structures OFF 17.4s RCON acknowledgement | **NEW** require the completed chunk after ACK, not ACK alone | Structure dependence strongly indicated; 17.4s is **not proof of finished generation** |
| Natural smoothing seed2 | PR #41's smoothing real-server A/B still timed out; stack includes `getBaseHeight` and Perlin noise, not necessarily Lithostitched | Exact preset and seed2, measure fully generated far chunk and verify noise configs | OPEN |
| Saved terrain: cliffs/seams, stone spikes | Earlier Alpine/Canyon/Sacred seed42; QA vegetation/hanging vine false positives fixed in PR #34 | Persisted-ground, 16/4 grid + hard rock cliff rejection and exact seed tests | Current 4x2 subset passed; retain as mandatory regression gate |
| Overworld giant caves, entrances and fast density | PR #40 120,050 bit-exact entrance cache parity and saved-world tests | Compare frozen uncached algorithm, seed and Anvil data, multiple workers | Unit parity PASS; whole-chunk speedup not yet established |
| Nether/End 512 height, climate/ecology/oceans, sky islands | Latest multi-workflow tests mostly green | Dedicated server saved-Anvil tests, same exact SHA and real mod dependency | Preserve all gates; never infer cross-version pass from another head |
| FPS, cave vs surface visibility | PR #36–#39 provide an opt-in diagnostic and client FPS interval logger, not GPU optimization | Same hardware, identical pre-generated seed/viewpoint; p50/p95 frame times and visual parity | REAL A/B NOT MEASURED |
| Optional upstream Lithostitched 1.8.0 | PR #41 upgrades from 1.6.0 to 1.8.0 for experimental density cache | True structures-on 4x2 matrix **and** complete geology/regression/saved-world parity | EXPERIMENTAL, NOT RELEASE APPROVED |
| QA false-green from diagnostic jobs | Structure ON RCON step failed but previous ablation job showed workflow `success` because of `continue-on-error: true` | Upload evidence, then fail the job when the underlying run failed | FIXED IN THIS BRANCH |
| QA false-green from `forceload` ACK | `distant` profile accepted ticket and waited 35 seconds, never checked `execute if loaded` status | Require true loaded-chunk status within the **same 180s total budget** | FIXED IN THIS BRANCH |

## Strict release conditions

1. **Same final commit, same pinned dependencies:** full CI green including four presets by two seeds, all separate experimental smoothing tests, server boots, Nether/End, ocean, ecology, sky, Anvil geology and Java21/JAR.
2. **No 180s timeout:** test fully generated loaded chunks with structures **enabled**, not merely forced-load tickets. Do not raise the 180s limit or silently disable structures.
3. **Stability + parity:** unchanged key worldgen heights, giant cave/structure topology, USGS edges/fallback, fixed seed world boundaries and existing worlds. Validate both terrain algorithms and persisted save outputs.
4. **Measured performance:** cold generation 3+ repeats with comparable hardware/seeds. Record wall times, chunks/sec, p95 tick latency, memory/GC. Distinguish CPU worldgen from client FPS. Only report percentage gains after actual data exists.
5. **Real GPU client:** PR #39 profiler is a measurement tool, not a renderer speedup. Compare existing scene for surface/open cave/giant cave/ravine/exit/sky islands with vs without supported Sodium and keep visuals intact.
6. **No premature branch mixing:** PRs #32 (core hot paths), #34 (distant seams), #40 (worldgen diagnosis), #41 (dependency experiment) and #36–#39 (client diagnostics) are distinct stacked/parallel Drafts. Do not treat their separate CI checks as a unified releasable artifact.

## Runtime diagnostic traces

- RCON: `qa/neoforge/rcon_worldgen_smoke.py` profile `distant`.
- Root cause: 3 `jcmd Thread.print` snapshots and
  `build/rcon_distant_stall_fingerprint.json`.
- Real client: `-Drealscale.client.fps.profile=true` in PR #39 only,
  and `qa/compare_client_fps.py` for matched A/B.
- Server is authoritative for brand-new chunk generation. Rendering mods
  do not fix `worldgen` task stalled before a chunk exists.

Release decision: **BLOCK** until all relevant cases are verified on the
actual final branch and client; a passing isolated test is not sufficient.
