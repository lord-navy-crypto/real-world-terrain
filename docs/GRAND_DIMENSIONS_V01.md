# RealScale Grand Dimensions — Phase I (Minecraft Java 1.21.1 / NeoForge)

**Status:** Opt-in experimental PR #7. The normal Realism MAX, Natural Ecotones and existing USGS presets are unchanged, and PR #5 optimization / PR #6 Skylands remain independent.

## World preset
Choose `realscale:realism_max_three_realms` while creating a NEW world.
- **Overworld**: exactly the existing `realscale:realism_max` generator, noise and dimension type (Y -64 to 2031).
- **Nether Grand**: `realscale:grand_nether` dimension type and noise settings; **Y 0–511**, noise generates for the *full 512 block span*, with the original 1.21.1 top-ceiling taper extended from Y 104–128 to Y 448–496. Nether lava sea remains Y32, floor and roof bedrock surface rules are preserved, same five vanilla Nether biomes and original `minecraft:nether` multi-noise preset, unchanged `coordinate_scale: 8`, ultrawarm/piglin/respawn-anchor flags.
- **End Grand**: `realscale:grand_end` dimension type and full-height noise, **Y 0–511**. Dragon/central End arena radius 1200 (around X=0/Z=0) uses the exact existing vanilla `minecraft:end` density below Y128, with pure void above the vanilla generation cap. Beyond 1200, the new registered `realscale:grand_end_density` blends in two remote end-stone island strata (typical centers around Y195 and Y356), fully enabled after radius 1700. These are *real noise-density blocks* and not only a biome color/label effect.

## Vanilla compatibility decisions
- The dimension keys remain **minecraft:the_nether** and **minecraft:the_end** (only the world preset's dimension *types* and noise settings are customized). This is important for portal and dragon-fight machinery.
- Nether 8-to-1 coordinate scale, lava, netherrack, structure registries, all original Nether biome features and ceiling bedrock surface rule are retained.
- The End still uses `minecraft:the_end` BiomeSource. Vanilla End cities and ships remain registered; **their real placement success is not guaranteed until playtested**.
- The central dragon arena, obsidian pillars, gateway/exit platform and vanilla outer-island density underneath Y128 are not intentionally modified.
- A new 512-block dimension height cannot safely be retrofitted into existing saves. Create a **new world** and back up saves.

## Exact 1.21.1 reference sources
The two noise settings and dimension types are derived from Mojang's vanilla generator data (via [misode/mcmeta](https://github.com/misode/mcmeta), tag `1.21.1-data`), rather than a newer snapshot. The Grand Nether retains its native original 1.21.1 surface sequence rather than replacing it with a hand-made minimal biome list.

## Tests
Static tests assert: a full 512-block noise span matching both dimension types; vanilla Nether roof/floor/lava and coordinate_scale=8; the unchanged Overworld and legacy Nether/End presets; seeded Grand End codec references. A standalone Java 21 smoke test checks End radial protections and dual altitude island envelopes.

New `grand-dimensions.yml` runs a dedicated NeoForge 1.21.1 server, starts the exact world preset, force-generates some real Nether and End chunks using localhost RCON, saves actual Anvil region files and verifies:
- the saved `level.dat` references the correct Nether/End codec names,
- the Nether has bedrock close to new Y511 and actual terrain above vanilla Y128,
- the original lower Nether is still present,
- the End central dragon-fight region remains air at Y160,
- and the actual outer End high-altitude end-stone count is reported.

**Limitations:** Phase I is not yet 768 or 2096 blocks tall in either secondary dimension. Remote End islands depend on seeded noise and may be absent at individual sampled coordinates. Nether Fortress / Bastion / End City / Dragon respawn and portal travel are not fully validated until real game playtests; dimensions should not be merged before server proof and a human compatibility test.

## Manual Minecraft checklist
1. NeoForge 21.1.249, Minecraft Java 1.21.1, Java 21.
2. Create a new `RealScale Grand Dimensions` world.
3. Nether: ensure the portal actually transports you, roof at Y~511, navigable caverns above Y128, lava stays at Y32, and Fortress/Bastion structures have intact walkable interiors.
4. End: verify dragon arena at X0/Z0 appears as vanilla, dragon battle and portal/gateways work, then fly into outer End beyond radius 1700 and look for elevated end-stone continent sheets at around Y150–430.
5. Test End city/ship spawning and chunk boundaries on both positive and negative coordinates. Supply seed, F3 position, screenshot and `latest.log` with anomalies.
6. Watch performance: 512-block secondary dimensions can cost substantially more CPU, disk, memory and chunk-generation time than vanilla 128-block noise.
