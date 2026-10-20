# Four-player-mode feature rescue + original scenic landforms

## Player world modes

All four normal/extended player modes intentionally include this overworld biome-source nesting:

`sky_ocean → rare_earth_biomes_v1 → geo_wonders_opt_in → cross_realm_spawn3 → natural_ecotones_diverse_v3 → vanilla multi_noise`.

Their original distinct **overworld noise settings remain**:

- Natural Exploration: `realscale:tva_natural_smoothed`
- Measured World: `realscale:usgs_global_hybrid_marine` (USGS + global landforms)
- Alpine: `realscale:tva_alpine_smoothed`
- Canyon: `realscale:tva_canyon_smoothed`

All now use `cross_realm_invasion` in Nether and `end_accents → cross_realm_invasion` in End, reusing historic worldgen IDs rather than adding a fifth world preset. This shares the experimental geo-wonders opt-in that was previously only accessible in a hidden named preset.

**Important:** this is a *configuration migration*. It changes the behavior of these world preset IDs for **new worlds**. Existing saved worlds should be backed up and tested; preserving an existing level.dat generator setting and earlier chunk placement is not guaranteed by this test alone. The four worlds also remain DIFFERENT in density/relief and DEM sources.

Terrain Lab dynamic exports, georeferenced Chaotianmen maps, and the historical archive are separate compatibility surfaces. This project does not claim every archival experiment is integrated until server validation and the exported-profile audit are done.

## Original landscapes inspired by exploration-game environmental design

Official geographic art direction references:
- Genshin world map overview: https://genshin.hoyoverse.com/en/map
- Fontaine exploration guide: https://genshin.hoyoverse.com/en/news/detail/112567
- Tsurumi fog-island diary: https://genshin.hoyoverse.com/en/news/detail/104058

New **original** procedural vanilla-block formations; no assets or direct copies:
- Rainforest terrace: irregular moss-covered limestone crag/terrace in suitable jungles and rainforest
- Misty coast crag: moss-topped rocky coast and cloud-forest pillars. Real-time fog itself is not implemented
- Basalt upland: eroded basalt column clusters in rocky badlands/highlands and geothermal landscapes
- Underwater arch: underwater tuff/calcite stone arch with the water passage left unobstructed (only source water is replaced for the structure)

All four are registered through biome modifiers, guarded to the RealScale overworld, limited to one chunk, and sparse deterministic 96-block districts. Placement writes only into air for land scenery or water for underwater scenery, with full preflight and no cave carving, new fluid sources, ore modifications, or procedural terrain-density rewrites.

This is an initial scenic layer, *not* 1:1 reconstruction of proprietary game environments, full atmospheric weather/fog simulation, or large-scale terrain sculpting.

## Release gate

QA must verify four-menu list, old/new feature chains, distinct terrain settings, configured/placed/modifier references, build, dedicated server boot, saved-chunk outputs, ocean water retention and structure safety. Keep Draft until the gates pass.
