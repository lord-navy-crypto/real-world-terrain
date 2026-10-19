# Rare Earth Biomes V1 — Registry foundation

Thirteen standalone biome JSON IDs:
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
- namib_fog_desert
- serpentine_barrens
- socotra_endemic_scrub

## Honest implementation status
These are biome registry JSON definitions plus a shared, biome-aware surface mosaic feature. A new opt-in biome source codec `realscale:rare_earth_biomes_v1` can select all 13 IDs when a new preset/datapack explicitly wraps its base source. No shipped world preset selects this codec yet.
Their generic vanilla feature lists and spawn settings are scaffolding inherited
from existing RealScale biomes, **not** a finished habitat simulation. The
visual colors, temperature and rainfall settings are preliminary.

## Required before release
1. Add a new opt-in world preset that uses the new `realscale:rare_earth_biomes_v1` source codec; the codec is registered, but it is not wired into any preset. Never mutate old codec semantics.
2. Climate/geology/hydrology eligibility checks; never scatter fumaroles into arbitrary forests.
3. Distinct biome-specific fauna/flora, trees, geothermal and saline features. The V1 surface palette exists, but full ecological structures do not.
4. Protect rivers, structures, freshwater continuity, DEM survey data and existing saves.
5. Confirm 10 IDs resolve, all 10 can be generated, and actual saved Anvil chunks
   contain recognizable biome-specific blocks, across multiple seeds.
6. Benchmark chunk generation; keep old density/carvers unchanged.
