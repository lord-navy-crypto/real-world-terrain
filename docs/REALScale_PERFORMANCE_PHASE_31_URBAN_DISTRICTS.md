# Phase 31 — Modern city quarters (experimental, optional)

RealScale originally shipped 100 licensed/original building templates and
multiple hamlet/compound buildings, but its surface structures spawn as
independent candidates. This phase introduces an additional CITY-QUARTER
generator; it does not remove any of the existing 100 architecture templates.

## Design: four distinct, original 48 × 48 road-connected districts

- **commercial_skyscrapers**: an approximately 94-block high-rise (about
  15–18 Minecraft-floor levels), smaller glass-and-stone office towers,
  a weatherproof 30-block-level skybridge, shops and street access.
- **civic_centre**: city offices, a tall municipal building, a public garden
  and formal plaza.
- **residential_quarter**: four multi-storey apartment blocks, floor slabs,
  street doors and climbable maintenance ladder cores.
- **industrial_edge**: warehouses, workshops, an elevated utility tower
  and an industrial campus.

Each district has four coordinated parcels, one wide **east–west road** and
one wide **north–south road** that extend to every boundary, pedestrian
sidewalks, lane markings and streetlamps. If future districts are aligned
at 48-block offsets their road stubs join without mismatched coordinates.
Each district is one multi-building structure template; a single city
candidate places exactly ONE quarter, not an already proven 3×3 metropolis.

The templates are authored by `scripts/generate_urban_districts.py` using
the same original deterministic vanilla 1.21.1 NBT encoder as the 100
existing buildings. No external schematic or runtime dependency is used.

## Distribution and coexistence

The pack `RealScale | Modern Cities (4 High-Rise Districts)` is a separate
optional built-in datapack, OFF by default. In Terrain Lab,
**Enable Modern Cities (Experimental)** enables it for the NEXT world after
Save and a complete Minecraft restart. Existing saves retain their own
datapack choices. Explicit `/place template realscale:urban/{id}` works
even with the city pack off.

Cities use one random-spread structure set with spacing=128, separation=48
in CHUNKS (nominal square grid side 2,048 blocks), salt=140111, plus
a vanilla village exclusion radius of 8 chunks. The four district templates
are selected by fixed weights 2:2:3:1. Actual accepted starts can be much
rarer, because the terrain biome and exclusion tests can reject candidates.
Only plains, ordinary savannas and specified dry grassland RealScale
biomes are allowed; steeper mountain meadows and savanna plateaus,
oceans, rivers, cliffs, steep alpine mountains and
the Nether/End are not city start biomes.

The jigsaw start projects onto WORLD_SURFACE_WG and requests
`beard_thin` terrain adaptation. This can soften natural terrain around
a site but does NOT prove that the entire 48×48 block will be level. Actual
hills, ravines, caves, aquifers and near-coast starts require visual QA.
The current structure is a deliberately conservative first urban district
release; it is NOT a contour-fitting street system or an interconnected
multi-sector city generator.

The old 100-building optional pack, original seven world presets, USGS DEM,
2,096-block dimension settings, ecology, rivers, hydrology, cave systems,
WGS84/1:20 climate, Distant Horizons and Chunky are not modified.

## Acceptance

- Four generated compressed NBTs must decode as vanilla DataVersion 3955
  with the correct block palette and full explicit air volume.
- Check each road meets all four template boundaries and each of four
  parcels contains purposeful buildings or public space.
- Test the tower's upper floors, accessible maintenance ladders, doors,
  skybridge and independent building silhouettes.
- Verify biome exclusions, sparse structure_set placement, vanilla-village
  separation and default-disabled pack controls.
- Build a true NeoForge JAR with all old 100 templates unchanged.
- Dedicated-server RCON must enable the opt-in pack, load each of the four
  original NBTs, place them in fully loaded test bays, and save real Anvil
  chunks. A test may additionally locate a naturally generated candidate.
- Actual on-screen city silhouette, slopes/water/roof intersections,
  real-world natural spawn frequency, FPS and DH/Chunky client behavior
  remain separate acceptance gates for the user's physical playtest.


## Phase 31 entrance and terrain-risk correction

A pre-test audit found that all four original variants placed north-facing
doors even on the north city parcels, where the cross avenue is to the south.
This has now been corrected: each north-side building faces SOUTH and each
south-side building faces NORTH, with an explicit paved pedestrian link from
its door to the avenue sidewalk. The dedicated QA reads the actual generated
block states at each entrance instead of merely counting doors in a palette.

The city biome allowlist now excludes mountain meadows and savanna plateaus
to reduce steep-terrain candidates. No terrain-viability guarantee is implied:
world-surface projection and beard-thin are not substitutes for a full
four-corner slope/water check. The optional pack remains OFF by default.
