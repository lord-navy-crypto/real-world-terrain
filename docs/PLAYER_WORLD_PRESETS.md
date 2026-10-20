# Four player-facing RealScale world modes

This release intentionally exposes **four** world presets in both
`data/minecraft/tags/worldgen/world_preset/normal.json` and
`extended.json`.

| Player label | World preset ID | Main purpose |
| --- | --- | --- |
| Natural Exploration | tva_sacred_biodiversity_end3_spawn3_v3 | Rich ecological transitions and realm encounters |
| Measured Earth / USGS | tva_measured_global_landforms_three_realms | Measured highlands combined with world geological wonders |
| Alpine World | tva_alpine_three_realms | Distinct mountainous terrain |
| Canyon World | tva_canyon_three_realms | Distinct canyon terrain |

All four use the shared `realscale:rare_earth_biomes_v1` layer under
`realscale:sky_ocean`, enabling rare-Earth ecosystem selection alongside
their pre-existing terrain settings. This does **not** imply all four
implement every historical v2/v3 ecotone or realm invasion setting.

## Internal versus player-facing

The *other historical world preset JSON files* remain **unlisted** in both
menus for now, because some are explicitly read by legacy exporter workflows
(`scripts/unified_world_settings.py` uses the historical Sacred / Measured
worlds) and versioned regression tests still validate v2/v3 worldgen
identity. These files are not four more player-selectable modes.

The separately invented `tva_rare_earth_ecosystems_three_realms` JSON was
deleted because it duplicated the common ecology feature without serving a
necessary exporter or saved-world compatibility role.

Physical deletion of the remaining historical world preset JSON files requires
a separate archive and migration audit. Do not delete legacy canonical
templates just to remove menu entries; doing so risks breaking exported
datapacks or regression fixtures.

## Validation

- Exact four player entries in both tags.
- Each selected preset has an overworld, Nether and End.
- Each selected preset retains its terrain settings and rare Earth wrapper.
- Explicit bilingual world names.
- NeoForge boot, server saved-chunk tests and old saved world regression must
  succeed before releasing.
