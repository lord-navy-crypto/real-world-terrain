# Phase 28 — Cave Exploration: opt-in four-tier loop and grotto system

## Product design

The existing Grand Overworld cave network is deliberately preserved: 768×768
cells, four major geological elevation bands, large halls, two satellite
galleries per band, tall shafts, three-cell arteries and rare highland entrances.

An **additional experimental world preset**
`realscale:cave_exploration_three_realms` is now available in the Minecraft
extended world preset list, using its own saved noise settings ID
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
