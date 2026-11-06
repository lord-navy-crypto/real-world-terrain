# RealScale original architecture generator

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

This module intentionally combines two independent sources of building designs:

1. **Licensed imports** — a small, individually reviewed selection of GDMC assets
   under Apache-2.0. Their provenance, source hashes, attribution and conversion
   limits are recorded in [ASSET_PROVENANCE.md](ASSET_PROVENANCE.md).
2. **Original RealScale designs** — authored from scratch in
   `scripts/generate_original_architecture.py`. The script composes block
   layouts from reusable primitives (foundations, shells, roofs, windows,
   doors, rooms, crop plots, courtyards and vertical features) and writes
   Minecraft 1.21.1 structure NBT.

The original design pipeline now has three generators:

- `scripts/generate_original_architecture.py`: five foundational original designs.
- `scripts/generate_architecture_batch.py`: 50 additional designs produced in one deterministic batch from reusable building primitives, including fifteen multi-building settlement compounds.
- `scripts/generate_architecture_expansion.py`: 42 further original designs, including alpine refuges, jungle canopy sites, marsh markets, desert works, civic buildings, bridges, aqueducts and industrial compounds.

The complete original catalog currently contains:

| Structure ID | Design | Intended biome families |
|---|---|---|
| `woodland_cottage`, `village_blacksmith`, `schoolhouse`, `healers_clinic`, `brick_bakery` | Five distinct village services and homes with different room plans and interiors | Temperate forests, plains, meadows and woodland transitions |
| `two_storey_townhouse`, `snowfield_shelter`, `savanna_outpost`, `marsh_stilt_house`, `mesa_trading_post` | Multi-storey urban house, snow shelter, dryland outpost, raised wetland house and badlands trading post | Forests, snowy biomes, savanna, marsh and badlands |
| `market_hall`, `railway_station`, `windmill` | Civic market, regional rail stop and four-sail windmill | Plains, grasslands, meadow and selected forest ecotones |
| `fishing_hut`, `coastal_pier`, `stone_bridge`, `watermill` | Fishing landing, timber pier, masonry crossing and riverside mill | Shores, rivers, floodplains and coastal transitions |
| `forest_watchtower`, `botanical_greenhouse`, `orchard_farm`, `lumber_camp` | Lookout tower, glazed crop house, tree orchard and timber camp | Forests, birch/taiga, meadow and riverine woodland |
| `desert_well`, `mountain_observatory`, `ruined_watchpost`, `garden_pavilion` | Water well, high-altitude observatory, weathered ruin and open garden structure | Desert, alpine, forest and flower/meadow biomes |
| `woodland_hamlet`, `plains_market_square`, `river_trade_hamlet`, `alpine_watch_compound`, `desert_oasis_hamlet` | Multi-building settlements combining homes, civic services, river infrastructure, mountain outpost and oasis facilities | Temperate woodland, plains, rivers, alpine and arid regions |
| `stone_mason_workshop`, `quarry_shed`, `charcoal_kiln`, `charcoal_burners_camp`, `riverside_sawmill` | Stoneworking, quarry services, charcoal production and timber processing | Stony uplands, badlands, forests and river margins |
| `canal_lock`, `railway_signal_box`, `railway_depot` | Canal engineering, railway signalling and regional freight infrastructure | Plains, rivers, meadows and forest-steppe |
| `monastery_chapel`, `hilltop_manor`, `frontier_fort` | Religious, residential and defensive compounds with courtyards and perimeter walls | Temperate woodland, meadows, foothills and open plains |
| `apiary_garden`, `vineyard_estate`, `orchard_cider_estate`, `sheepfold_ranch` | Beekeeping, trellised cultivation, fruit processing and livestock infrastructure | Flower meadows, plains, woodland margins and grasslands |
| `snowbound_storehouse`, `desert_cistern`, `tropical_boardwalk_house`, `fishing_village_compound`, `marsh_reed_hamlet` | Climate-specific storage, water supply, raised homes and coastal/wetland settlements | Snowy slopes, arid basins, tropical wetlands, shores and marshes |
| `forest_ranger_lodge`, `plains_farmstead`, `alpine_waystation`, `coastal_lighthouse`, `desert_caravanserai` | Foundational cabin, farmstead, mountain shelter, lighthouse and courtyard inn | Temperate forest, plains, alpine, coastal and semi-arid biomes |

Expansion 3 adds 42 more original designs across mountain, snowy, jungle, wetland, desert, badlands, savanna, prairie, river, coast and civic families. See [EXPANSION_3.md](EXPANSION_3.md) for the complete new-design inventory and biome-density policy.


## Enable the architecture library in a world

The pack is intentionally optional. In Minecraft 1.21.1:

1. Open **Create New World → Data Packs**.
2. Find **RealScale | Architecture Library (100 Structures)** under Available.
3. Move it to Selected, confirm the pack prompt, and create the world.

For an existing world, use **Edit World → Data Packs** and make a backup first.
Structures only generate in chunks that have not already been generated; they
are not retroactively inserted into explored terrain. The library uses
independent biome-specific random-spread sets, with conservative spacing so
the new buildings remain discoveries rather than a dense layer over every
region.

## Build and generation flow

```text
Apache-2.0 source schematics ──> licensed converter ──┐
                                                     ├─> optional architecture datapack ──> mod JAR
Original design rules ─────────> two procedural generators ─┘
```

Gradle runs both generators during `processResources`. The resulting
Minecraft `.nbt` templates are embedded in the mod's base data namespace,
so explicit `/place template` use does not depend on enabling the optional
worldgen pack. The biome tags, structure definitions, template pools and
structure sets live in the separate opt-in pack. The original design scripts,
licensed source files and source websites are not required at runtime. The batch pipeline adds 50 designs and Expansion 3 adds another 42, bringing the total to 97 original structures plus three separately licensed imports (100 templates overall). No network request or external
structure-library dependency is made by the game.

Minecraft's `random_spread` structure sets choose candidate locations using
world seed, spacing, separation and a per-structure salt. Biome allow-lists
prevent a lighthouse from appearing inland or a desert courtyard in a snowy
forest. Each design is a separate structure with its own spacing and biome
list; the architecture datapack remains optional and can be disabled at world
creation. Expansion 3 uses a per-biome candidate-density budget: the summed
random-spread candidate rate in each target biome is held at or just below the
vanilla village baseline (one candidate grid per 34×34 chunks). Spacing is in
chunks. This is a candidate-placement budget, not a guarantee that every
candidate becomes a successful structure.

## Design and generation constraints

- All original structures are created from vanilla 1.21.1 block states.
- Templates include an explicit air volume so the interior is cleared when
  placed; placement can replace small surface vegetation within the footprint.
- The generator validates every block coordinate against the declared
  dimensions and writes deterministic gzip-compressed NBT.
- The structures do not use command blocks, entities, custom blocks or
  runtime scripts.
- Current templates are surface structures. They are not yet a general
  terrain-conforming foundation system; very steep slopes, cliffs, rivers and
  narrow shorelines still need placement QA.
- Candidate spacing is not a promise of a successful structure count.
  Terrain, biome checks and collisions can reject starts.

## Existing-template detailing pass

The original five designs, the 50-design batch, and the 42 Expansion 3
designs share one deterministic detailing pass. It does not create new
template IDs or change declared dimensions. It adds only when the target
space is clear:

- entrance approach blocks and a small canopy where a design lacks a porch;
- role-specific work fixtures for farms, workshops, markets, civic buildings,
  transport sites and defensive outposts;
- additional lanterns where a layout is under-lit;
- roof vents/chimneys for suitable hearth, lodging and production buildings.

The pass is idempotent: running it twice does not stack duplicate fixtures.
It uses vanilla 1.21.1 block states. The three Apache-2.0 GDMC source files
remain byte-for-byte unchanged and their digests are verified before
conversion; their converted NBT copies receive the same additive detailing
without changing dimensions or removing attribution.

## Validation

```sh
python3 scripts/generate_original_architecture.py --output /tmp/realscale-original
python3 scripts/generate_architecture_batch.py --output /tmp/realscale-batch
python3 scripts/generate_architecture_expansion.py --output /tmp/realscale-expansion
python3 -m unittest discover -s qa -p test_architecture_pilot_pack.py -v
cd neoforge && gradle --no-daemon build
```

The architecture server workflow then boots a real NeoForge server and uses
`/place template` for all imported and original templates. This proves the
Minecraft registry can load each NBT template. The build also audits the 100
structure definitions and their per-biome candidate-density budget. Fixed-seed
biome-discovery and natural-generation playtests are still required before
marking the pack stable.
