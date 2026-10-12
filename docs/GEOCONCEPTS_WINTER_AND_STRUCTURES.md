# RealScale GeoEcology Winter and Vanilla Structures Laboratory

Status: independently installable, opt-in EXPERIMENTAL datapacks.
Minecraft 1.21.1 NeoForge. Never automatically installed.

## Build

At the RealScale repository root:

    python3 scripts/make_geoecology_suite.py
    python3 scripts/make_structure_density_packs.py

Outputs under build/:

- RealScale-GeoEcology-Winter-EXPERIMENTAL.zip
- RealScale-Vanilla-Structures-2x-EXPERIMENTAL.zip
- RealScale-Vanilla-Structures-3x-EXPERIMENTAL.zip

Use Create World -> Data Packs in a NEW disposable test save. Do NOT
add to global datapacks or existing valuable survival worlds. New
decoration is not reverted by later removal. Extended GeoEcology ZIP
already includes the first 3-microhabitat pilot: do not load both packs.

## What was actually recovered

- Historical v0.7: erosion and drainage concept.
- Historical v0.8: lithological rock resistance, karst, snow and moraine
  environments; full geology model still standalone.
- Historical v0.9: VanillaColumnAdapter mapping simulated substrate to
  vanilla block IDs, macro-regional watershed; full engine not live in mod.
- Historical v1.0: DEM provenance and measured terrain import; only the
  small Big Sur DEM is presently integrated in the mod.

The GeoEcology pilot offers only substrate-dependent decoration:
Alpine dry powder snow, windblown snow, winter taiga snow, glacial
moraine scree, rocky hills, swamp mud, fern pockets, dripstone cave
calcite, high mountain calcite and deep tuff. All are existing vanilla
block IDs; no registered new biome IDs, blocks, structures or mobs.

Powder snow can hinder movement or trap players. Test on a creative or
spectator world, not a valuable survival game. This is NOT a seasonal
winter/summer cycle: snowfall patches are generation-time decoration.
These are NOT simulations of true glacial movement, groundwater
circulation, fault tectonics or karst dissolution. Do not mistake the
old research plan for existing in-game implementations.

GeoEcology uses only built-in NeoForge add_features biome modifiers.
It may affect qualifying VANILLA biomes in the test world, even if
the selected world preset is not RealScale. Existing terrain densities,
heightmaps, cave noise fields and sea level remain unchanged.

## Structures

Existing RealScale world presets still use NoiseBasedChunkGenerator
with vanilla structures registered; however this does NOT guarantee
actual villages/chambers spawn or that their foundations are sound.

The new QA script locates vanilla structure candidates using RCON
in actual Himalayan and Realism MAX NeoForge servers, generates
Anvil chunks, then inspects persisted structures.starts and
piece bounding boxes, with a warning on possible floating floors.

Selectable 2x and 3x packs override exactly 12 vanilla random_spread
Overworld structure sets, preserving choices/weights/salt/separation
and spread type, while lowering the candidate spacing. Villages:
spacing 34 -> 24 (approx 2x candidate density) or 34 -> 20
(approx 3x). This estimates POTENTIAL candidate grid density,
not the actual count of constructed structures, which also depends
on biome validity and terrain.

Structure sets deliberately NOT modified: ruined portals (shared by Overworld and Nether), stronghold rings,
pillager outposts, special-frequency buried treasures/mineshafts,
Nether and End. More common villages could indirectly reduce
outposts because outposts exclude nearby village candidates.
The 2x/3x pack affects all worlds where enabled, including vanilla
worldgen with NeoForge. Install EITHER 2x OR 3x, NEVER BOTH.

## Validation

Static:

    python3 -m unittest discover -s qa -p 'test_ecology_pack.py' -v
    python3 -m unittest discover -s qa -p 'test_structure_and_geoecology.py' -v

GitHub workflows:
- geoecology-and-structures.yml builds ZIP artifacts and tests JSON.
- vanilla-structures-anvil-server.yml checks actual saved vanilla
  structure starts and support samples in Himalayan and Realism MAX.

Real in-game placement, biome feature cycle compatibility, performance,
powder snow gameplay, and exact increase in actual structure counts
have NOT been confirmed by static tests. Run real NeoForge validation
before any promotion to default presets.

Reference:
https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
https://mcasset.cloud/1.21.1/data/minecraft/worldgen/structure_set/
