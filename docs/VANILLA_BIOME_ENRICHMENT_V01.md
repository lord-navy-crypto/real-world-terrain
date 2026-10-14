# Vanilla biome enrichment v0.1 (experimental)

All features extend existing vanilla biome IDs through NeoForge biome modifiers; no override of vanilla biome registry or terrain noise.

## Committed prototypes
- Birch forests: rare supported fern clusters.
- Flower forests: low-frequency allium glades.
- Windswept hills/forests: sparse short-grass patches on soil.
- Swamps/mangrove swamps: shaded brown-mushroom test patches (fungus survival and placement must be checked).
- Taiga: low-frequency mossy cobblestone block piles as rock-cluster analogues.
- Badlands: rare terracotta/red-sandstone rubble piles.

These six are experimental biome feature JSON sets (18 files). Four are random patches, two are block piles. They are not a validated overhaul of vanilla biomes.

## Real-world-inspired backlog (not implemented)
- Taiga nurse logs, root-throw pits/mounds, woodland stump decay communities, lichen boulder groups.
- Wetland hummock-hollow microtopography, tidal marsh fringes, willow-like riparian margins.
- Badlands talus fans, alluvial fans, dry washes and caprock erosion ledges.
- Grassland forb mosaics, shrub islands and locally disturbed bare-soil patches.
- Alpine scree aprons, frost-sorted stones, snowmelt turf strips.
- Coastal driftwood strandlines, stabilized dunes and salt-spray scrub.

## Safety gates
1. Run NeoForge 1.21.1 dedicated server and check worldgen feature/biome modifier registry decoding.
2. Inspect multiple chunks per target biome, confirm changes are visible and no features float or overwrite tree trunks, water or structures.
3. Compare river wet cell connectivity and DEM terrain snapshots against the independently validated hydrology source.
4. Check performance metrics and seeded repeatability, new vs old saves, and mod interaction with other biome modifiers.
5. If block pile creates implausible boulders/unstable piles, disable it pending custom support-aware structures.

No dedicated-world runtime verification performed yet. Existing default biome source and hydrology are untouched.
