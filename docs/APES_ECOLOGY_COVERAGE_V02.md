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

## FeatureSorter cycle regression fix (NeoForge real server)

At commit `df9bbdbb`, the saved-world server test crashed at the
`minecraft:swamp` / `realscale:seasonal_floodforest` boundary with
`Feature order cycle found`. Both biomes inherited the *same placed-feature
registry holder* `realscale:wetland_moss_patch`, but new floodplain features
were registered in a conflicting relative order. All five ecotone decoration
groups now receive **their own placed-feature aliases** pointing at the same
existing configured features and placement predicates. This prevents implicit
cross-biome identity/order constraints without changing the underlying plants.
The static alias test is not proof of success; repeat real server tests.

## APES Gallery: actual category-coverage QA

The diagnostic-only `realscale:apes_gallery_three_realms` uses a 16-biome
checkerboard to force all nine terrestrial climate representatives, alpine
controls and freshwater/marine categories into a compact test world.

- NOT for natural survival: artificial 32-ish-block biome interfaces are
  deliberate and unsuitable for ecological realism.
- Preserves Grand Nether and End settings exactly from PR #7.
- Dedicated `.github/workflows/apes-gallery.yml` boots a real NeoForge
  server, persists a world and invokes `qa/neoforge/check_apes_gallery.py`
  to require all sixteen **actual saved Anvil biome palettes**. It catches
  registration and FeatureSorter failures that a single realistic spawn may
  never exercise.
- Vanilla block palette signatures are informational only. They cannot
  establish that any custom tree, shrub, snow patch or sea grass actually
  came from the new `GrandEcologyFeature`.
- Natural climate realism, cross-chunk visual quality and rare-feature
  occurrence frequency remain outstanding real-player QA tasks.

## v0.3 — coherent macro vegetation, relative height, and gallery QA repair

- Added `EcologyDistributionMath.java`: continuous deterministic 768-block
  macro suitability with 128-block sub-patches. Fifteen different habitat
  salts and densities give groves, open areas and denser wetland strands,
  rather than independent white-noise attempts.
- The existing NeoForge placed features consult the field **before any block
  modification**. The seed-dependent final roll remains Minecraft's feature
  random stream. No new generator, fluid, aquifer or biome registry changes.
- Relative-height **visual preferences** attenuate very tall lowland tropical
  and savanna trees on extremely elevated ground; alpine decoration becomes
  more likely as Y increases. **No physical temperature lapse rate, basin
  warmth, precipitation or rain shadow is modeled here.** Biome IDs remain
  controlled by vanilla multi-noise and natural ecotone ribbons.
- Pure Java smoke tests sample >50k coordinates across negative and positive
  positions, validate smooth patch boundaries, diversity and preferences.
- APES Gallery used scale=3 and examined only 49 full chunks. This sampled
  just three biome IDs near spawn, while its test incorrectly required
  >=100 chunks and 16 IDs. The debug-only gallery is now scale=0, and
  accepted complete spawn chunks are >=32; the **16 ID saved-Anvil check
  remains mandatory**. Only the real server rerun can confirm the fix.

## v0.4 — enforce real 16-biome gallery coverage and shallow marine plants

The APES gallery originally generated only 49 FULL spawn chunks: despite
the scale-0 checkerboard, its saved palettes were missing three IDs
(`minecraft:river`, `minecraft:snowy_plains`, `minecraft:stony_peaks`).
Instead of weakening the rule, the isolated gallery CI now sends an actual
`forceload add 160 0` command after server startup, waits for its generated
satellite chunks, flushes the save, and requires at least one **fully saved
outpost** chunk. All 16 distinct biome IDs are still required.

For vegetation physics, `EcologyDistributionMath.aquaticDepthSuitable`
now limits ocean seagrass and estuary decoration to shallow ocean floors,
with a separate allowance for shallow warm-reef undergrowth; freshwater is
not forced under global sea-level because high-elevation streams exist. This
changes only decoration acceptance probability — never the ocean floor,
fluid generation, original ocean/river IDs, terrain density or currents.
Unit tests cover shallow vs deep ocean, warm reef and upland freshwater.
