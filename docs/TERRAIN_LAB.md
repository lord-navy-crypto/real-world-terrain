# RealScale Terrain Lab — experimental new-world mode editor

Supported: Minecraft Java 1.21.1 / NeoForge. Seven procedural presets editable; measured USGS Big Sur DEM is locked.

## What actually changes

Each Apply creates a unique worldgen registry ID, for example `realscale:lab_realism_max_<digest>_g6`,
with its own world preset, noise settings and four referenced density functions.
The new world type is appended to Minecraft's **Create World → World → World Type** selector.
Eight original preset IDs, archived releases, saved worlds and Y=63 sea level are untouched.
**Keep generated profile files installed as long as a saved world uses them.**
Worldgen registries cannot safely be changed while the world is already running.

## Start the editor before launching Minecraft

From the RealScale repo root, with the game closed:

```bash
python3 scripts/terrain_lab.py --gui
```

Choose the preset and numeric sliders, click **Create selectable world preset**, then:

```bash
cd neoforge
gradle --no-daemon --console=plain runClient
```

On the Create New World screen, pick the new **RealScale Lab** entry. Create a NEW world.
The GUI uses Python's built-in `tkinter`. On a Mac Python without Tk, use the CLI:

```bash
python3 scripts/terrain_lab.py --list
python3 scripts/terrain_lab.py --preset realism_max --profile alpine --relief 1.12 --elevation 12 --caves 55 --river-grade 6
cd neoforge && gradle --no-daemon --console=plain runClient
```

If `gradle` and Java 21 are on PATH, add `--launch` to the CLI command for one-step build and launch.
`--dry-run` prints the planned new registry ID without editing files.

## Adjustable values

| Control | Values | Implemented effect |
| --- | --- | --- |
| Base mode | Realism MAX, GeoReal, Himalayan, Grand Canyon, Continental, Realistic Mountains, Ultimate | Reuses the source terrain/biome algorithm |
| Tuning mode | Natural, Gentle, Alpine, Dramatic | Populates values below |
| Terrain relief | 0.80–1.25 | Multiplies the base height density function; modifies land relief |
| Elevation offset | −48 to +48 blocks | Adds signed density offset of 1/128 per block |
| Cave activity | 20–80 | Cave density bias (higher usually yields more cavities) |
| River grade | Off=0, Conservative=3, Normal=6, Flexible=9 | Max neighbor River surface grade allowed by safe-bank generator |
| Prefer inland spawn | yes/no | Raises or preserves lower continentalness spawn threshold |

These are experimental terrain controls, not guaranteed physical elevations or continuous watershed rivers.
Higher relief also influences coastlines. Cave/river responses are bounded but remain seed-dependent.
The measured USGS Big Sur mode cannot be edited: doing so would distort the measured DEM.

## Dependency decision

- This phase uses the **existing Minecraft world-type menu**, vanilla density functions and Python `tkinter`; **no extra Minecraft runtime dependency**.
- For a later fully native client slider menu, **YetAnotherConfigLib (YACL 3)** release `3.8.1+1.21.1-neoforge` is compatible and LGPL-3.0-or-later, with dropdowns, categories and numeric sliders.
  Official source: https://modrinth.com/mod/yacl/version/3.8.1%2B1.21.1-neoforge
  Verified Maven coordinates: `maven.modrinth:1eAoo2KR:h8Px82QV`.
- Adding YACL alone does not safely update running worldgen registries; it needs a create-new-world handoff.
- No TerraBlender dependency: it helps biome integration but does not fix RealScale's noise-driven river continuity or terrain-height model.

## Checks

```bash
python3 -m unittest discover -s qa -p 'test_*.py' -v
python3 qa/verify_repository.py
cd neoforge && gradle --no-daemon --console=plain build
```

Use multiple NEW world seeds to inspect actual saved chunk heights, river connectivity, caves, flooding and spawn.
Passing JSON and compile checks does **not** establish realistic drainage or perfect rendering.
The historical USGS seed-42 spawn verification issue remains an open limitation.
