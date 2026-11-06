# RealScale Architecture Library — Expansion 3

Expansion 3 adds 42 original Minecraft 1.21.1 structures. Together with the
previous 58 templates, the optional Architecture Library now contains exactly
100 templates: 97 original RealScale designs and three individually licensed
Apache-2.0 GDMC conversions.

## New designs

| Biome family | New structures |
|---|---|
| Temperate forest and birch | Forest logging camp; birch sap harvest; forest ranger academy |
| Taiga and snowy regions | Taiga trapper station; taiga sled depot; glacial fishing station; reindeer herders camp; avalanche shelter; peat-bog timber camp |
| Alpine and mountain | Alpine shepherd refuge; cliffside hermitage; mountain ore survey; mountain monastery scriptorium |
| Jungle and wetlands | Jungle canopy outpost; bamboo craftsman yard; rainforest herbalist station; mangrove fishery; marsh reed market; delta reed market |
| Desert and badlands | Desert windcatcher house; oasis date grove; badlands saltworks; mesa mine headframe; pilgrim waystation; volcanic obsidian works |
| Savanna and grassland | Savanna cattle kraal; steppe grain depot; prairie seed elevator; rural dairy barn; horse relay post |
| Rivers and floodplains | River ferry crossing; riverside boathouse; floodplain granary; stone aqueduct; canal boatyard |
| Coast and cherry groves | Coastal signal station; island lighthouse outpost; coastal salvage yard; cherry-grove tea house |
| Civic and travel | Town clock tower; roadside inn; civic courthouse |

## Design approach

The new batch is not a set of recolored copies. It adds distinct footprints and
functional site plans: stilt-supported wetland buildings, canopy platforms,
granaries, livestock yards, market stalls, mine headframes, signal towers,
raised aqueducts, ferry crossings, civic porticos and multi-building compounds.

Every design is generated from source-controlled block-layout rules. The
expansion generator writes vanilla 1.21.1 NBT with explicit dimensions and no
entities or command blocks. The new templates are copied into both the mod's
base structure namespace (for explicit `/place template`) and the optional
architecture datapack (for natural generation).

## Natural-generation density

The 42 new structure sets use separate salts and biome allow-lists. Their
spacing is tuned per target biome against the existing 58 structure sets. The
QA budget sums the approximate random-spread candidate rate
`1 / spacing²` for every structure allowed in each biome and keeps the total
at or just below the vanilla village candidate baseline
`1 / 34²`. The current 42-biome budget evaluates between 94.6% and 100%
of that baseline (average 98.5%) across the new target biomes. Spacing and
separation are measured in chunks.

This is deliberately an aggregate budget: a jungle outpost can be frequent in
jungle without causing 100 independent structure sets to stack in every
forest or plains biome. Actual successful starts can be less frequent because
of terrain suitability, biome sampling and structure collisions.

## Acceptance

The 100-template acceptance workflow:
- regenerates all 100 NBT templates from source-controlled generators;
- checks NBT dimensions, palette indices, vanilla block namespaces and
  structure/biome registration;
- verifies the per-biome village-density budget;
- builds the NeoForge JAR;
- starts an isolated dedicated server and places all 100 templates through RCON;
- saves and cleanly shuts down the test world.

The server placement test verifies that every template can be loaded and placed.
A separate fixed-seed exploration playtest is still needed to evaluate the
visual distribution of naturally generated structures across large regions.
