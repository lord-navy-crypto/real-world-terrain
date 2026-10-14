# RealScale Sky Archipelago + Original Marine Preservation v0.5 (experimental)

## 1. Vanilla oceans are the baseline, not something to replace
Original ocean biomes, shipwrecks, underwater ruins, sea monuments, coral, kelp, and the base ocean floor are managed by vanilla Minecraft/NeoForge. Do not replace these biome definitions, carvers, structures, or default placed features.

**Change made:** the two `neoforge:add_features` modifiers `ocean_seagrass_patches` and `ocean_sediment_stones` were removed, because the latter could change the base seafloor material and both would globally attach worldgen work to vanilla ocean biomes. Their feature codecs remain as dormant opt-in experiments, with a hard `SkyOceanBiomeSource` guard in the Java marine feature to prevent affecting other world presets even if third-party packs reattach them. Thus, there are no RealScale default ocean-biome decorations and no seabed replacement from these features.

The skyland biome source delegates all biome queries below Y160 *unchanged* to the underlying RealScale Natural Ecotones/vanilla biome source. It does not generate rivers or replace ocean and beach biome mappings.

**Critical QA nuance:** a forced `deep_ocean` biome source does *not* force underwater terrain. A former test had stone at the island center Y63 despite the biome name `deep_ocean`. The forced test now checks both the vanilla deep-ocean biome at Y48–63 in saved chunks and actual sea-water columns elsewhere, rather than assuming the biome name establishes bathymetry. This does not prove all real-world seas, monuments or shipwreck spawns are perfect; test them with multiple real seeds and coordinates.

## 2. Concentric ocean-isolation tiers and sky archipelagos
The system is a **sampled ocean-isolation approximation**, not a computationally exact Euclidean distance to the nearest mainland shoreline. It reads only the underlying climate source without forcing adjacent chunks to generate. It samples 16 compass directions on 8 radial rings (max 128 samples for an eligible cell), with precomputed trigonometry and a 256-entry bounded thread-local tier cache. Coastal failure stops sampling immediately.

| Sampled surrounding ocean | Tier | Candidates per 512×512-block cell |
| --- | --- | --- |
| Ocean center not deep, or any land in the first four rings (96/192/288/384m) | 0 | 0 |
| All inner 384m rings are ocean, fails beyond this | 1 | 1 main floating island |
| Ocean rings through 640m are clear, fails beyond | 2 | Main island + 2 satellites |
| Ocean rings through 896m are clear | 3 | Main island + 4 satellites |

Islands form loose archipelagos as their cells become progressively farther offshore. Main islands have ~10–14 block radii; satellites have ~7–11 block radii, staggered at about 96 blocks from the central island. Floating island volume is stone/end stone underneath soil/moss/flowers at around Y180–206. It never modifies sea level, ocean floor, existing coral or rivers. Additional sky biomes are selected only in their local high-altitude discs, not as large ocean-replacement blanket biomes.

## 3. Test structure
- Pure Java `SkyIslandLayoutSmoke` checks that sampled coast distance maps to exactly `0/1/3/5` candidate islands, that islands do not overlap, and that the original water-column biome remains unchanged.
- Existing `SkyIslandLayout`, `SkyOceanBiomeSource` and `SkyIslandFeature` share identical candidate geometry.
- Dedicated NeoForge forced-ocean Anvil test checks a vertically continuous real island at X32/Z63, sky biome palette at Y176–191, original `deep_ocean` biome at Y48–63 and real submerged water elsewhere.
- Stable Realism Max/Natural Ecotones/USGS world startup regressions remain.
- **Still missing:** measured true nearest-land distance, systematic natural-seed ocean heightmaps, verified underwater structure counts and real-game speed benchmarks. Keep PR #6 Draft until verified. Do not merge the separate optimization PR #5 without independent regression testing.
