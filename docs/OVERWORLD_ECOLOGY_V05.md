# RealScale Ecology v0.5 — Luminous Vanilla Habitats & Eight Ecological Boundaries

**Minecraft Java 1.21.1 / NeoForge 21.1.x. Experimental only.**
Base branch: `feature/overworld-ecology-v04-vanilla-habitats` (40 surface ecotones with fully functioning vanilla habitat generation).

## A. Actual native-biome enrichment: 28 vanilla biomes / 10 microhabitat motifs
Unlike new biome registrations, these are **added to the original `minecraft:` biomes** by NeoForge `add_features` modifiers. They operate in *all worlds running a built mod containing this branch*, irrespective of world preset. The changes are intentionally sparse and non-carving.

| Microhabitat | Original Minecraft biome families | Added visual elements |
| --- | --- | --- |
| forest_wildflower_glades | forests & flower forest | patches of azure bluets |
| spruce_berry_understory | taiga / old growth / snowy taiga | naturally sparse sweet berry thickets |
| mixed_forest_fern_mosaic | deciduous and dark woods | understory ferns |
| savanna_seasonal_grasses | savanna plateaux and windswept savanna | seasonal grass tufts |
| meadow_oxeye_pockets | meadow, cherry grove, flower forest | oxeye daisy flower patches |
| wetland_fern_islands | swamp and mangrove swamp | wet-ground fern stands |
| highland_grassy_terraces | windswept and highland biomes | wind-tolerant grass where soil exists |
| arid_scrub_mosaics | deserts and badlands | sparse dead bushes on sand/terracotta |
| old_growth_moss_floors | old-growth forests and dark woods | modest moss carpet floors |
| jungle_understory_ferns | jungle and bamboo forest | subtropical ground-layer ferns |

Each motif has its own `configured_feature`, `placed_feature` and one dedicated biome modifier. **No vanilla placed feature is duplicated as a new biome modifier** (avoids NeoForge feature-order cycles). Patches first require `minecraft:air` at their target location and an appropriate dry support block; their heights are `MOTION_BLOCKING_NO_LEAVES` so they can decorate beneath foliage. Each uses low-frequency rarity filtering, small clusters, and a final biome filter.

No ocean or river biome is targeted by these new modifiers. No surface block is excavated or lowered, and original vanilla trees/spawners remain in place. The behavior is *in addition to* existing RealScale microstructures and groundcover.

## B. Eight additional naturally selected 16–32 block-wide climate-edge variants
The 40 surface ecotones are expanded to **48 registered and naturally selectable** ecotones, retaining vanilla ore/geode/decorations, animals and hostile spawns from matching donor-biome JSONs.

| New ecosystem | Underlying vanilla climate interface | Primary donor |
| --- | --- | --- |
| birch_parkland | birch forest ↔ plains | birch forest |
| boreal_parkland | taiga ↔ plains | taiga |
| peat_bog_taiga | taiga ↔ swamp | swamp |
| seasonal_savanna_wetland | savanna ↔ swamp | savanna |
| tropical_littoral_forest | jungle/sparse jungle ↔ beach | sparse jungle |
| frost_riparian_banks | snowy plains ↔ frozen river | snowy plains |
| montane_spruce_meadow | taiga ↔ meadow | meadow |
| dry_grass_dunes | desert ↔ plains | desert |

Actual `NaturalEcotoneBiomeSource.RULES` matches each new transition from underlying vanilla climate samples, not after-the-fact placed blocks. The first two are placed before broad forest/grassland rules for their narrower habitat distinctions. New IDs are also in the 48-biome gallery, ecotone catalog, donor mapping and matching RealScale undergrowth modifiers.

### Preservation guarantees, limitations & honesty
- Vanilla `river`, `frozen_river`, and all original ocean biome cells are still hard protected by the existing source. River **banks** can receive e.g. `frost_riparian_banks`, but not the channel.
- No water, cave density routing, sea level, geodata/DEM, terrain height, main world preset, Nether or End modified.
- Feature JSONs and biome modifiers **alter native biome decorations globally if the branch's mod JAR is installed**; they are not restricted to the opt-in ecotone preset. Test on a disposable world before broader use.
- These variants use climate-boundary adjacency; they are not a true measured slope, landform, soil depth or groundwater classifier. A new deep geology/cave biome selector has **not** been activated, because doing it solely from Y or without aquifer knowledge could misclassify underground stone and damage vanilla caves.
- Verification checks registry consistency and world boot; visual diversity and worldwide geographic plausibility require screenshot-based field tests and multi-seed density audits.
- No claim that every new transition appears within a single spawn area; it must be reachable where underlying biome pairs actually meet.

## Release gates
1. Static biome-resource consistency tests, 1.21.1 NeoForge Gradle build and original-world boot.
2. Dedicated natural ecotone server startup/persisted palettes and no registry parse errors.
3. Targeted multi-seed scans for each of the 8 new IDs (note rare biome pairings may not be seen around spawn); original river/ocean unchanged.
4. Manual forest canopy and wetland inspection; confirm small plants are not overwriting leaves, water, trees, flowers or structures.
5. Measure chunk-generation throughput with and without the extra 10 surface features before offering this as a stable release.

NeoForge docs: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
