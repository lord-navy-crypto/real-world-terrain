# RealScale TVA — unified release candidate, 18 October 2026

## Source timeline
This branch is stacked on experimental **PR #44** (which includes terrain,
ecosystem, caves, the 2096-height world, USGS/Measured fused density, strict
server test gates and Lithostitched 1.8.0 experiment from PR #41).
It imports the **CLIENT-ONLY, OPT-IN** observational detector, sample
cost profiler, bounded saved-world Section audit and FPS frame-interval
benchmark from **PR #36–#39** by exact source blob SHA, preserving the
worldgen branch's additional QA workflow gates.

This is ONE consolidated **Draft release candidate**, not the main branch.
No render queue, Sodium internals, cave placement or block counts changed
by the client feature import.

## Verified on exact parent HEAD before this import
- PR #44: Sacred Timeline seeds 2 and 42 **PASS** strict 180-second
  `forceload + execute if loaded` probes. Saved chunk loaded in
  approximately 15 attempts for seed 2 and 14 for seed 42 (not instantaneous).
- PR #44: Measured/USGS seeds 2 and 42 **FAIL**, so the critical far-worldgen
  defect is not resolved. Natural smoothing real-server CI **FAIL**.
- PR #43 (without Sacred cache): Sacred seed 2 **FAIL**.
- PR #45 (optional Lithostitched default runtime isolation alone):
  Sacred seeds 2 and 42 **FAIL**, and Natural smoothing **FAIL**.
  The PR #45 `compileOnly` change was **NOT IMPORTED INTO PR #46**.
  This daughter isolation candidate now tests that change explicitly, against
  PR #46's existing unmodified structure-enabled baseline. It remains
  experimental until strict 4×2 seed parity and full chunk loading pass.
- PR #39: client telemetry and FPS-measurement code built successfully,
  but this is **not** evidence of actual FPS uplift. Its parent matrix
  still failed worldgen tests.

These are historical data for a **different commit** and do not authorize
release of the combined HEAD. New CI must run here from scratch.

## Adaptive rendering strategy
- OPEN_SURFACE: advisory prioritization of distant terrain/horizon.
- SEALED_CAVE: advisory prioritization of near/forward cave geometry.
- TRANSITION/UNKNOWN: no speculative hiding around openings, shafts,
  ravines, skylights, glass, fluid, unloaded chunks or fast movement.
- Fail-open, hysteresis, opt-in sampler default **OFF**.
- FPS measurements default **OFF**. Optional JVM flags:
  `-Drealscale.client.visibility.profile=true`;
  `-Drealscale.client.fps.profile=true`;
  `-Drealscale.client.fps.scene=enclosed_cave`.
- **No renderer tasks are reprioritized yet.** Sodium/VSync/GPU A/B is still
  required. Current client code does not remove underground work or improve
  frame time by itself. It measures and classifies.

## Mandatory release gates
1. Same SHA: source integrity, Java21 NeoForge, full dedicated server,
   exact worldgen; full 4 terrain presets × 2 seeds,
   plus Natural/Alpine/Canyon smoothing 2, marine biome integrity,
   APES ecology, sky islands, Nether and End.
2. `generate-structures=true` AND truly loaded distant chunk within
   the SAME 180-second request/acknowledgement limit. No ticket-only
   success and no `continue-on-error` fake-green.
3. Saved Anvil basin, mesa, geological spikes, cave networks, oceans,
   USGS fade seams, dimensions and structures pass; no silent terrain
   or saved-world incompatibility.
4. Cold-generation A/B (same machine/seed) and measured p95 latency.
5. Actual client 3×30s per scene per candidate; controlled FPS/frametime
   plus cave mouth, skylight, deep ravine and archipelago visual validation.
6. If any gate fails: **keep Draft, do not merge to main**.

Related PRs: #32 #34 #36 #37 #38 #39 #40 #41 #42 #43 #44 #45.

## Default optional Lithostitched — separate daughter experiment

This exact branch (NOT parent PR #46) replaces the default runtime
`implementation` dependency for Lithostitched 1.8.0 with `compileOnly`,
reusing PR #45's runtime isolation strategy and test assertions. Normal
RealScale world presets should use native Minecraft jigsaw without loading
Lithostitched by default. Users who explicitly export Lithostitched Terrain Lab
codecs must install it in their modpack.

This is **not** proven to cure 180-second stalls; PR #45 in isolation
previously failed Sacred and Natural smoothing. Here the test is on TOP OF
the Sacred `cache_2d` version that passed both Sacred seeds. Compare this
daughter head directly to PR #46, with generated structures ON, the same
180-second fully-loaded-chunk test, saved Anvil quality and exact world height.

A compile-only dependency is not a way to prevent a *player's own installed*
Lithostitched from modifying vanilla structures. That compatibility case
remains separate. No changes to the original main branch are approved.
