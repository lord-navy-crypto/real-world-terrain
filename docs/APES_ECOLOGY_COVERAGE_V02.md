# AP Environmental Science Unit 1 landscape coverage matrix — Grand Ecology v0.2

This document is an **implementation audit**. A feature registered in a vanilla biome is a
visual enhancement; it is NOT an independent dynamically selected climate biome, a complete
biotic community, or evidence of real-world ecology. All features use vanilla Minecraft blocks.

Reference: College Board AP Environmental Science, Unit 1.2 Terrestrial Biomes and 1.3
Aquatic Biomes: https://apcentral.collegeboard.org/courses/ap-environmental-science
Engine integration: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/

## All nine APES terrestrial biome categories

| APES biome | Registered feature | Target Minecraft areas | Implemented visual effect |
|---|---|---|---|
| Tropical rainforest | `tropical_rainforest` | Jungle, bamboo jungle, sparse jungle | 18–29-block jungle emergent canopy |
| Temperate rainforest | `temperate_rainforest` | Old-growth spruce/pine taiga | Tall spruce with tiered wet-forest canopy |
| Temperate seasonal forest | `temperate_seasonal_forest` | Forest, birch forest | 14–21-block irregular broadleaf trees |
| Taiga | `boreal_taiga_floor` | Taiga, snowy and old-growth taiga | Shaded ferns and mossy understory |
| Temperate grassland | `grassland_mosaic` | Plains, meadow | Patchy low herbs and poppies |
| Savanna | `savanna_canopy` | Savanna and plateau | Broad flat-topped acacia trees |
| Shrubland | `chaparral_scrub` | Drier savanna / heath / wooded badlands | Low dense leaf shrub islets |
| Desert | `desert_outcrops` | Desert/badlands/dry edges | Ground-supported red/sandstone outcrops |
| Tundra | `arctic_tundra` & `alpine_tundra` | Snowy plains, ice spikes, alpine areas | Rocky frostland patches |

## Aquatic systems (APES Unit 1.3), minimal non-carving features

| System | Feature / vanilla representative | Stage |
|---|---|---|
| Freshwater rivers and streams | `freshwater_bed` in river + existing vanilla | Decorative seagrass proxy implemented; flow dynamics untouched |
| Lakes and ponds | Existing vanilla freshwater + `floodplain_bank` on adjacent dry terrain | Lake-specific biome selection **not implemented** |
| Freshwater wetlands | `floodplain_bank`, `freshwater_bed`, preexisting marshy woodland | Bank and underwater vegetation prototypes |
| Estuary / brackish transition | `estuary_shallows` mangrove/swamp/lukewarm ocean | Sparse shallow plant proxy; actual tidal/salinity model **not implemented** |
| Intertidal/coastal shallows | `coastal_seagrass` in ocean/lukewarm/deep ocean | Native submerged meadow proxy |
| Warm-water coral reef | `coral_reef_floor` in warm ocean | Seagrass/sea pickle enhancement of existing vanilla coral reefs |
| Open/deep marine biome | Vanilla deep-ocean systems plus limited `coastal_seagrass` | Existing seafloor protected; pelagic ecology **not implemented** |

## Safety and physical fidelity constraints

- Never modify noise_router, base climate sampler, cave density, sea level, river carve,
  aquifers, ocean-floor sediment or underwater terrain in this phase.
- Only native aquatic plants replace eligible still-water blocks; no terrain excavations.
- Rare frostland exposed rocks may replace a thin snow layer only when anchored
  to intact solid ground below; they do not change snow/ice climate or terrain.
- Aboveground structures are placed only after full-footprint check where possible;
  all proposed changes stay in the initiating chunk.
- Existing natural ecotone sampler remains bounded, with protected ocean and river selections.
- `realscale:grand_ecology_three_realms` remains **opt-in for the biome source**;
  NeoForge `add_features` apply to affected biomes in any world with this experimental JAR.
- Dedicated server/Anvil and three-seed visual tests are required before merge.

## Not yet implemented

- Realistic global 1,000–4,000-block climate macrozones; 3D Y-dependent mountain
  treeline and basin/precipitation/seasonal climate logic.
- Real ecosystem food webs, succession, soil nutrient cycles, species interactions,
  hydrological seasons and full aquatic lake/estuary dynamics.
- Fully distinct biome IDs for all nine APES macrobiomes; current IDs are vanilla
  mapped to APES categories plus existing narrow ecotone IDs.
- Full 40–80-block branched giant forests, forest density calibration and structure
  conflict/protected masks beyond conservative block replacement checks.
