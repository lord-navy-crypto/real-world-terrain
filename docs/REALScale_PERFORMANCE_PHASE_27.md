# Phase 27 — Fix misleading real worldgen acceptance coordinates, reduce startup overhead

## Fitted seven-preset validation

The prior CI generated real Anvil chunks for all seven fitted terrain
presets, but selected a fresh nearest-plains / nearby land center **inside
each preset separately**. The aggregate heightmap script correctly refused
to compare unrelated absolute X/Z chunks; the gate was red for data-quality
reasons, despite all seven individual boots succeeding.

Fix the benchmark design rather than weakening the gate: all modes use the
same seed-42 absolute anchor **(-784,-496)**. This anchor comes from the
previous successful natural mode saved heightmap window, centered at chunk
(-49,-31) near plains. Every mode still must force-load and persist the same
5×5 chunk footprint and have >=20 full heightmapped chunks for the report.

The aggregate still requires >=100 common columns and each fitted mode
to change at least four saved OCEAN_FLOOR heights against the natural
baseline. Nothing is silently accepted if some mode is not active at the
new common anchor. If land-gated profiles do not activate there, future QA
must gather more fixed, shared anchor sites; it must not compare different
coordinates or claim success based on separate natural terrain.

Removing repeated preset-specific nearest biome searches also reduces
unnecessary CI worldgen sampling overhead; this is NOT a production
terrain-generation algorithm change.

## APES world boot

Previous APES gallery CI stalled in its initial 2096-high spawn preparation
and ended without the server-ready marker. Its persistent level.dat existed,
but the outpost generation was never confirmed. Add the same
`spawn-chunk-radius=0` setting used by fitted smoke worlds and increase
the local ready allowance from 420 to 540 seconds (still under the 20-minute
job cap). All RCON and persisted 16-biome Anvil gates are preserved.

## Scope

These are diagnostic/acceptance improvements in test workflows, not game
client FPS, block generation or save-format modifications. A green outcome
requires the new actual server boot and fixed-coordinate Anvil evidence.
