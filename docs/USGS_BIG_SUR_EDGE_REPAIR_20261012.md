# USGS Big Sur v2 — high-relief seam and generation-load repair

**2026-10-12 experimental only; use a brand-new world.** This change alters worldgen geometry for the existing v2 ID; already generated v2 chunks cannot be retroactively fixed. Do not open a valuable existing v2 save with the new branch without a backup.

## Player-reported failure

Measured elevation terrain looked duplicated/cut off on one side and fractured into a precipitous drop on another. Previous testing of USGS 96 m legacy blend and v2 768 m exterior blend was insufficient for these visual failures.

## Evidence

A failed v2 CI world (seed 785312) saved only 43 full chunks of the spawn region before a large forceload request timed out. Chunk surface elevation was roughly Y=1221..1302 in that sampled area, confirming high terrain where blending to unrelated procedural height can produce severe relief. The old CI RCON requested 25 chunks in its first single command; server reported a "Can't keep up!" delay of ~16.8 seconds and then timed out after 25 seconds. The worldgen geometry itself has NOT been proven correct from this incomplete sample.

## Fixes in experimental v2 (NOT legacy)

1. **Blend distance: 768 -> 2048 blocks outside the 480 m DEM source**. All original sampled heights inside the measured square remain unchanged, with a smoothstep influence weight outside the source; full procedural fallback beyond 2048 m of distance from the nearest measured point.
2. **Noise horizontal size: 2 -> 4** (8 m to 16 m noise interpolation cell). Significantly reduces the expensive 2096-high-chunk interpolation workload; trade-off: this is NOT a finer DEM interpolation. The source still contains 16x16 original points at 32 m spacing, no new measurements.
3. **Bounded 256-entry ThreadLocal DEM elevation cache**, exact X/Z and stored value compared. DEM's 2D bicubic spline should not be recomputed across many different Y density samples.
4. **CI generation requests one chunk at a time** at representative points on each of four edges, in the transition zone and outside the 2048 m outer boundary. No large 25-chunk RCON request.
5. **Anvil audit** now records extreme near-edge neighbor steps, checks saved heights do not drop to the world floor near DEM, and insists on mid-transition plus exterior fallback coverage.
6. **World selection:** historical 96 m legacy USGS preset remains accessible in extended listing for old saved worlds and explicit comparisons; for NEW worlds, normal menu points at v2 rather than recommending the old cut-edge mode. Preset registry ID and old world resources are retained.

## Not yet proven

- The fallback terrain might be very far below the measured Big Sur source, so the extended 2048 m slope may still appear as a large terrain transition. Only saved Anvil heights and in-game screenshots establish whether it's acceptable.
- True cross-chunk load testing and rendered terrain quality are still pending the new CI.
- No guarantee of fixing abnormal previously generated terrain. Changing v2's generation parameters can CREATE a new boundary with old v2 chunks; test on a new save.
- The longer 2048 m DEM reach changes the location of the natural procedural fallback; tests checking older 1008 m fallback must be updated.

To test manually: create a new world with realscale:usgs_big_sur_v2, then inspect X/Z ~0, +/-240, +/-896 and +/-2352 along all cardinal directions in spectator mode. Use seeds 785312 and 20261012 for comparison. Do not interpret teleports to Y=1500 as real elevation.

Run the suite: Python QA + NeoForge Gradle build + .github/workflows/usgs-bigsur-v2-server.yml, and inspect usgs_v2_edge_qa.json.
