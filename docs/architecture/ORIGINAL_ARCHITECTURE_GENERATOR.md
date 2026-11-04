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

The original generator currently creates:

| Structure ID | Design | Intended biome families |
|---|---|---|
| `forest_ranger_lodge` | Timber cabin, porch, stone footing and chimney | Temperate forest, birch, taiga and RealScale woodland transitions |
| `plains_farmstead` | Farmhouse, barn, fenced crop rows and water trough | Plains, meadow, savanna and grassland ecotones |
| `alpine_waystation` | Stone-and-spruce mountain shelter with hearth | Meadow, grove, snowy foothills and alpine transitions |
| `coastal_lighthouse` | Tapered red-white beacon with lantern room | Beach, stony shore and RealScale coastal biomes |
| `desert_caravanserai` | Sandstone courtyard inn, arcade and cistern | Desert, badlands, savanna and semi-arid RealScale biomes |

## Build and generation flow

```text
Apache-2.0 source schematics ──> licensed converter ──┐
                                                     ├─> optional architecture datapack ──> mod JAR
Original design rules ─────────> original generator ─┘
```

Gradle runs both generators during `processResources`. Only the resulting
Minecraft `.nbt` structure templates and their worldgen JSON definitions are
embedded. The original design script, licensed source files and source
websites are not required at runtime. No network request or external
structure-library dependency is made by the game.

Minecraft's `random_spread` structure sets choose candidate locations using
world seed, spacing, separation and a per-structure salt. Biome allow-lists
prevent a lighthouse from appearing inland or a desert courtyard in a snowy
forest. Each design is a separate structure with its own spacing and biome
list; the architecture datapack remains opt-in and can be disabled at world
creation.

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

## Validation

```sh
python3 scripts/generate_original_architecture.py --output /tmp/realscale-original
python3 -m unittest discover -s qa -p test_architecture_pilot_pack.py -v
cd neoforge && gradle --no-daemon build
```

The architecture server workflow then boots a real NeoForge server and uses
`/place template` for all imported and original templates. This proves the
Minecraft registry can load each NBT template; separate fixed-seed worldgen
playtests are still required before increasing structure frequency or
marking the pack stable.
