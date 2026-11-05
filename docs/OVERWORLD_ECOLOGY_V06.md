# Overworld Ecology v0.6 — Regional Mosaics and Native-Habitat Enrichment

**Target:** Minecraft Java 1.21.1 / NeoForge 21.1.x. Parent: v0.5 draft. New draft branch: `feature/overworld-ecology-v06-regional-mosaics`.

## 60 registered surface ecotones (48 existing + 12 new)
All twelve v0.6 biomes have real `worldgen/biome/*.json` entries, full *vanilla 1.21.1* geology, all 11 placed-feature stages, passive/hostile spawning and mood sound inherited from a defensible original biome. They have unique climate values/color identity, are wired into `NaturalEcotoneBiomeSource.RULES`, and are added to the catalog, explicit vanilla habitat donor mappings, 60-biome gallery and matching ecotone groundcover modifiers.

| v0.6 new natural variant | Underlying original biome boundary | Supporting vanilla habitat |
|---|---|---|
| cherry_oak_woodland | cherry grove ↔ forest | cherry grove |
| cherry_meadow_fringe | cherry grove ↔ meadow | meadow |
| birch_snowline_scrub | birch forest ↔ snowy slopes | grove |
| spruce_snowdrift_edge | grove ↔ snowy slopes | grove |
| mangrove_freshwater_margin | mangrove swamp ↔ river | mangrove swamp |
| coastal_marsh_woodland | swamp ↔ beach | swamp |
| dune_pine_woodland | taiga ↔ beach | taiga |
| montane_oak_foothills | forest ↔ windswept forest | windswept forest |
| rocky_savanna_foothills | savanna ↔ stony peaks | savanna |
| badlands_peak_scree | badlands ↔ stony peaks | badlands |
| bamboo_meadow_fringe | bamboo jungle ↔ meadow | jungle |
| snowy_spruce_riverbank | snowy taiga ↔ frozen river | snowy taiga |

These new biome boundaries have approximately **16–32 block** target widths, sampled from original *vanilla multi-noise climate biome* adjacency. Rivers and all ocean biomes are never replaced. The two riverbank variants apply to the *adjacent land biome*, not river channel biome cells. Rare biome pairs do not necessarily appear around a single world spawn; registration is not proof of natural frequency.

## Original Minecraft biome enhancement: 18 separate ecological microfeatures
Existing 10 v0.5 native biome enhancements remain; add **eight more** sparse, substrate-guarded, non-carving plant motifs:

- Plains cornflower clearings, with support checked on grass/dirt
- Forest lily-of-the-valley groundcover
- Cherry grove pink petal floor
- Swamp and mangrove swamp blue orchid pockets on land
- Boreal old-growth brown mushroom pockets
- Mountain meadow allium wildflowers
- Sparse dry beach/desert dead bush patches
- Jungle/bamboo forest moss understory

Each uses one configured `minecraft:random_patch` feature, a rarity-controlled placed feature on `MOTION_BLOCKING_NO_LEAVES` with a biome filter, and one NeoForge feature modifier exclusively targeting `minecraft:` land biomes. It checks for `minecraft:air` *and* allowed dry substrate underneath, does not carve blocks or replace water and does not change vanilla spawn registrations. See `neoforge/ecotones/vanilla_microhabitats.json`.

**Important:** Unlike the optional 60-biome ecotone world preset, these 18 extra decorators affect original Minecraft biomes *globally while the experimental JAR is installed*. Always test a fresh world and back up old saves.

## Performance and preservation
- No Overworld density functions, aquifer settings, DEM tiles, world height, sea level, river/ocean biome replacement, vanilla ores/structures, Nether or End source altered.
- No original vanilla feature removed. Native decorations are additive and sparse; profiler and visual comparisons still required.
- A shared immutable `CARDINAL` directions array avoids repeated allocation in each climate-source biome query, preserving the same boundary detection algorithm.
- Does not claim geologically accurate 3D cave ecology. Deep cave variants currently remain separate registered scaffolds, unselected by surface biome lookup, until an aquifer-safe 3D cave classifier can be proven.
- The biome donor is a **single representative ecosystem**, not an ecological weighting of both adjoining climates. Mountain slopes, groundwater and true coastline types cannot be inferred solely from biome adjacency.

## Verification and future gates
Static checks now require exactly **60** natural ecotone rules, catalog entries, vanilla donors and gallery registrations, plus six existing cave prototypes, all with valid references. The 18 native decorators are validated for correct feature/placed/modifier paths, air-before-placement, substrate, rarity, heightmap and river/ocean protection. NeoForge build, a vanilla saved-world server boot and a Natural Ecotone saved-world worldgen boot are mandatory.

Additional **future** gates: record in-game screenshots for all new variants; multi-seed QA checking real selection frequency; verify how many added flowers/trees/animals truly spawn; measure generation time and compatibility with other biome-decoration mods. Only merge into stable `main` after those pass.
