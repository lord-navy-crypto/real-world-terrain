# Phase 28 — Cave Exploration: opt-in four-tier loop and grotto system

## Product design

The existing Grand Overworld cave network is deliberately preserved: 768×768
cells, four major geological elevation bands, large halls, two satellite
galleries per band, tall shafts, three-cell arteries and rare highland entrances.

An **additional experimental world preset**
`realscale:cave_exploration_three_realms` is deliberately not added to the default/extended world creation menus, which
must preserve the stable seven existing options. Select this explicit world
preset ID in a new test world via a server's `level-type` property, using its
own saved noise settings ID
`realscale:tva_cave_exploration`.

It enables `"gallery_v2": true` only inside that new overworld noise router.
The canonical `GrandOverworldCaverns` codec defaults that field to false when
omitted, so existing terrain presets, save data and chunk-to-chunk topology
remain unchanged. Do not add the new field to old saved worlds; switching it
on for an already-created world will intentionally change newly generated
chunks and can expose underground seams.

The new opt-in adds four geological families to the *existing network*:

- Tier 0: wide, low-profile cave rooms linked by gentle side tunnels;
- Tier 1: moderately tall branching grotto chambers;
- Tier 2: tall, narrow vertical rifts with nonuniform passages;
- Tier 3: broad airy upper-altitude alcoves beneath the preserved roof.

Every tier adds a satellite-to-satellite loop, plus a further naturally curved
connector from a deterministic satellite to a terminal chamber. These are
horizontal interconnections and do not replace the existing cross-tier shafts.
The same canonical `GrandOverworldCaveNetwork.tunnelStrength` is reused, so
bends, smooth walls and cross-chunk continuity remain consistent.

## Real-system constraints

- Additional geometry stays >24 blocks inside its own 768-block parent cell.
  It does not rely on generation order, block writes or neighboring chunks.
- A conservative X/Z/Y AABB precheck accounts for the existing ±6 X, ±5 Z,
  ±2 Y seeded noise warp before skipping any new cave work.
- Top-level `final_density` still enforces the original solid-rock guard
  `original > 0.24` and the original highland-entrance logic. It does not
  directly remove underwater surface roofs, alter oceans, or modify aquifers.
- New geometry remains in Y 0…1600; legacy Overworld still spans -64…2031.
- Cached per-worker geometry is deterministic and bounded (8 region snapshots).
  No mutable global random generator, persisted cache or dynamic world loading.
- The existing 47-component Grand Overworld mask, full legacy density
  evaluator, DEM, climates, cave biomes, Nether/End realms, structures, DH and
  Chunky compatibility settings are untouched.



## Default world-picker compatibility and real server acceptance

The existing seven normal and extended menu entries remain unchanged. To
create the experimental server world set `level-type=realscale:cave_exploration_three_realms`
on a NEW world before its first generation.

`.github/workflows/cave-exploration-world.yml` starts a real NeoForge
dedicated server with that preset and seed 42, disables needless spawn radius
pre-generation, RCON-force-loads one target chunk at (384,384), waits for
`minecraft:full`, issues `save-all flush`, and inspects both the generated
`level.dat` codec IDs and saved Anvil target chunk. This proves server
compatibility and actual chunk persistence; it is NOT yet a physical player
cave traversal or old/new block-array A/B.

## Validation

`GrandOverworldCaveDesignV2Smoke` checks 324 new terminal chambers,
walkable loop segments, signed cell positions, bounds at cell seams, conservative
noise-warp X/Z column masks, and worker-thread determinism. The legacy
`GrandOverworldCaveNetworkSmoke`, bit-parity and broad-phase tests still run
unaltered. Python source guards verify only the new preset selects the new
density option and that legacy saves remain in their previous configuration.

This is a REAL, intentionally changed voxel topology in the optional world.
Do not describe its original density as bitwise equal to the new world:
bitwise preservation applies only to legacy presets. Actual dedicated-server
Anvil chunk generation and a physical-client cave-tour screenshot/FPS run are
separate release gates, not implied by pure Java tests.


## Phase 29 — exact per-column feature-mask memoization

The V2 evaluator previously repeated a four-tier combined AABB check at each
vertical density sample, then called both curved tunnels and the annex hall
even when only one of those three features could intersect the sample.

The new path adds a bounded 2,048-slot, two-way, worker-local X/Z column memo.
Its 12-bit mask independently selects each tier's loop, spur, and terminal
annex. A height-aware test rejects the selected features outside their exact
vertical support before noise evaluation. The final evaluator then calls only
the selected tunnel/hall kernels.

The mask bounds mirror the exact conservative bounds in
`GrandOverworldCaveNetwork.tunnelStrength` (`radius+17` horizontally and
`radius*verticalScale+16` vertically) and `hallStrength` (the exact ellipsoid
radii). It includes the declared +/-6 X, +/-5 Z and +/-2 Y noise warp. Any
future caller outside that support or across a 768-cell boundary falls back
to the full reference evaluator. The unmasked evaluator remains available for
bitwise parity tests.

This also removes an over-aggressive local-edge rejection inherited from the
first V2 broad phase: the old `localX<24` / `localZ<24` test did not account
for a positive density warp toward the interior. The feature AABB mask now
decides each edge column conservatively, so valid near-edge geometry cannot
be hidden by a coarse local-coordinate threshold.

Opt-in `-Drealscale.density.profile=true` now reports V2 column-mask hit/miss
counts and exact feature-kernel evaluation counts. These are diagnostics only;
they do not alter production worldgen. CI checks bit-for-bit equality against
the original full evaluator at positive and negative coordinates, all
nine combinations of the legal X/Z warp extrema, vertical warp extrema,
cell-edge probes, cache collisions, and parallel workers.


## Phase 30 — measured cache-capacity A/B

The first actual server profile recorded 2,574,916 X/Z column-cache hits and
850,681 misses (75.17% hit rate) across 3,425,597 probes. The last-column
fast slot was already effective, but the bounded two-way table was still
evicting useful columns during interleaved vertical sampling.

The table capacity is raised from 512 to 2,048 entries per worker. This adds
only a few tens of kilobytes of bounded primitive storage per active worldgen
worker and does not change any mask, cave geometry, noise, or density output.
The same seed-42 dedicated-server profile is rerun to compare the hit rate
under the same target-chunk and server setup. Keep this increase only if the
real-world profile confirms a useful hit-rate improvement; a larger cache is
not assumed to be faster merely because it is larger.
