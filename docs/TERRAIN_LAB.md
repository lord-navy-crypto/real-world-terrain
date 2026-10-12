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

## Native Minecraft YACL interface (v1.4.1 experiment)

The client-side NeoForge mod entrypoint now registers the **YACL config screen**.
YACL **3.8.1+1.21.1-neoforge** is a separate client-side dependency; Gradle
resolves `maven.modrinth:1eAoo2KR:h8Px82QV` and does NOT jar-in-jar it.
Players using a standalone NeoForge JAR must also install the corresponding
[YACL 3.8.1 NeoForge 1.21.1 JAR](https://modrinth.com/mod/yacl/version/3.8.1%2B1.21.1-neoforge)
into their client mods folder. Dedicated servers do not require YACL.

1. Open Minecraft's **Mods -> RealScale Terrain -> Config**.
2. On the YACL screen, set **Base Terrain, Relief (%), Elevation, Cave
   Activity, River Grade, Prefer Inland Spawn**, then click **Save**.
3. Exit Minecraft completely. This saves **a draft** in the client's
   `config/realscale-terrain-lab.json`; it intentionally does NOT modify
   the currently loaded worldgen registry.
4. Restart using the RealScale source project via
   `cd neoforge && gradle --no-daemon --console=plain runClient`.
   The Gradle `prepareTerrainLabDraft` task runs
   `scripts/terrain_lab.py --from-yacl ...` **before resources are packaged**,
   and creates a new immutable `realscale:lab_*` world preset. The generator's
   Java river-grade code reads the per-preset suffix, not runtime/global state.
5. Start a NEW WORLD and select the matching **RealScale Lab** preset under
   World Type. Existing worlds never change just because this config draft changed.

**Important limitations:** This build-time workflow applies only to the
Gradle **development client**, not standalone JAR installations where there is
no source tree to rebuild. For packaged deployments, use the Python CLI to
create the preset *before building the release JAR*. There is not yet a direct,
live world-generation menu or runtime per-world registry regeneration. A
YACL slider changes the draft, not a currently running world or existing save.

Dependency selection: YACL is useful for menus, **not a replacement for the
vanilla/NeoForge DensityFunction engine**. Additional terrain-generation
dependencies such as TerraBlender (biome blending) are deliberately not added
until the mod actually adds new biomes and the existing game-engine river
network passes continuity and flood checks. Adding libraries alone cannot
fix drainage, visual seams or spawn-selection faults.
