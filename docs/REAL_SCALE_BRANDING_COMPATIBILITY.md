# RealScale branding — legacy save compatibility

There is no affiliation with Marvel, and no Marvel branding should appear in
RealScale player-facing mod names, world-selection labels or new JAR filenames.

## New world preset IDs shown in Create World
| RealScale preset | New ID | Older save-data ID (read compatibility only) |
|---|---|---|
| Natural Landscapes | `realscale:natural_three_realms` | `realscale:tva_sacred_timeline_three_realms` |
| Alpine Mountains | `realscale:alpine_three_realms` | `realscale:tva_alpine_three_realms` |
| Canyons and Valleys | `realscale:canyon_three_realms` | `realscale:tva_canyon_three_realms` |
| Measured Terrain — USGS | `realscale:measured_three_realms` | `realscale:tva_measured_three_realms` |
| Earth Wonders / Cross-Realm | `realscale:wonders_invasion_three_realms` | `realscale:tva_wonders_invasion_three_realms` |
| Caves & Marine Habitats | `realscale:cave_ocean_ecology_three_realms` | `realscale:tva_cave_ocean_ecology_test_three_realms` |

The **new** presets are byte-for-byte JSON copies of the historical preset
configurations, so the same seed reaches the same noise-settings references,
dimension types and biome sources. The old JSONs are deliberately **not
deleted**: Minecraft existing level.dat records can still reference them.

Internal noise-settings/density-function resource paths and old script/class
names retain their earlier stable IDs temporarily: those are serialization
contracts, **not player-facing descriptions or an endorsement of a franchise**.
Renaming internal worldgen keys needs a separate codec/data migration and saved-
world A/B proof; silently deleting them could make old worlds unplayable.

Version labels now omit franchise text. The new naming-only build
`2.0.0-preview.2-test.6` is **experimental**; it has no asserted additional
performance optimization over its source parent `2.0.0-tva-preview.2-test.5`.

## Managed compatibility module
Independent `dh-chunky-guard/` Java addon builds next to `neoforge/` in the
same repo, but is distributed as a distinct JAR. Version-pinned to DH 3.3.3
and Chunky 1.4.23 on NeoForge 1.21.1; no upstream jars redistributed.

### Release validation before promoting to stable
- Build NeoForge JAR and check new world presets resolve;
- compare saved Anvil blocks/biomes/heights using both new and legacy preset IDs;
- review Beardifier Y256 experimental default and older save-block drift;
- confirm physical Mac DH/Sodium/Iris/shader visuals;
- choose a source license with the rights holder.

Do not remove legacy identifiers on a branding-only change.
