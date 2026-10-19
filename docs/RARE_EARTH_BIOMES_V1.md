# Rare Earth Biomes V1 — Registry foundation

Ten standalone biome JSON IDs:
- geothermal_sulfur_basin
- socotra_dragonblood_woodland
- white_dune_lagoon
- sundarbans_tidal_mangrove
- congo_peat_swamp_forest
- steppe_saline_lake
- acidic_geothermal_mudflat
- montane_mist_forest
- permafrost_fen
- limestone_island_scrub

## Honest implementation status
These are biome registry JSON definitions only. No biome source selects them yet.
Their generic vanilla feature lists and spawn settings are scaffolding inherited
from existing RealScale biomes, **not** a finished habitat simulation. The
visual colors, temperature and rainfall settings are preliminary.

## Required before release
1. Add a new opt-in, versioned biome source/preset; never mutate old codec semantics.
2. Climate/geology/hydrology eligibility checks; never scatter fumaroles into arbitrary forests.
3. Distinct biome surface rules and vanilla block flora/geothermal/saline decoration.
4. Protect rivers, structures, freshwater continuity, DEM survey data and existing saves.
5. Confirm 10 IDs resolve, all 10 can be generated, and actual saved Anvil chunks
   contain recognizable biome-specific blocks, across multiple seeds.
6. Benchmark chunk generation; keep old density/carvers unchanged.
