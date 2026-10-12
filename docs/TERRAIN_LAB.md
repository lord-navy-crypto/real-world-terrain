# RealScale Terrain Lab — experimental new-world mode editor

Supported: Minecraft Java 1.21.1 / NeoForge. Three merged families in the YACL UI; all seven original procedural algorithms remain available through the advanced CLI. Measured USGS Big Sur DEM is locked.

## What actually changes

Each Apply creates a unique worldgen registry ID, for example `realscale:lab_landscape_<digest>_g6`,
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
python3 scripts/terrain_lab.py --preset landscape --profile alpine --blend 25 --relief 1.12 --elevation 12 --caves 55 --river-grade 6
cd neoforge && gradle --no-daemon --console=plain runClient
```

If `gradle` and Java 21 are on PATH, add `--launch` to the CLI command for one-step build and launch.
`--dry-run` prints the planned new registry ID without editing files.

## Adjustable values

| Control | Values | Implemented effect |
| --- | --- | --- |
| Base mode | Landscape, Alpine, Canyon | Blends two actual terrain height-density fields |
| Secondary feature blend | 0–50% | 0 = primary density only; up to 50% secondary height signal |
| Tuning mode | Natural, Gentle, Alpine, Dramatic | Populates values below |
| Terrain relief | 0.80–1.25 | Multiplies the base height density function; modifies land relief |
| Elevation offset | −48 to +48 blocks | Adds signed density offset of 1/128 per block |
| Cave activity | 20–80 | Cave density bias (higher usually yields more cavities) |
| River grade | Off=0, Conservative=3, Normal=6, Flexible=9 | Max neighbor River surface grade allowed by safe-bank generator |
| Prefer inland spawn | yes/no | Raises or preserves lower continentalness spawn threshold |

These are experimental terrain controls, not guaranteed physical elevations or continuous watershed rivers.
Higher relief also influences coastlines. Cave/river responses are bounded but remain seed-dependent.
The measured USGS Big Sur mode cannot be edited: doing so would distort the measured DEM.

## Dependencies and terrain editing freedom

- **YetAnotherConfigLib (YACL 3)**, NeoForge `3.8.1+1.21.1-neoforge`,
  provides the live settings *editor*, not live world generation. It is installed
  as a separate client dependency (Modrinth Maven coordinates
  `maven.modrinth:1eAoo2KR:h8Px82QV`). The Gradle development client bakes the
  saved YACL draft into new immutable resources BEFORE its next launch.
- Terrain geometry remains built on Minecraft's built-in, configurable
  `DensityFunction` tree and existing RealScale 3D cave functions. Combining
  two multiscale density fields with `minecraft:mul` and `minecraft:add` is
  genuine continuous composition, unlike toggling label-only modes.
- No TerraBlender dependency: it is primarily useful for biome integration.
  The current mod's hard problem is river watershed continuity, not biome
  registration.
- For more freedom, NeoForge permits custom codec-registered density functions
  (RealScale already registers its `realscale:dem_density` function), or an
  entirely custom `ChunkGenerator`, but both require significant coding and
  actual server tests. They are not a safe drop-in dependency for this module.
- WorldPainter is a separate freeform terrain editor with brush sculpting,
  16-bit heightmap import and a scripting API. It exports static Minecraft
  worlds, not infinite procedural worlds; using its data for a RealScale DEM
  is a future integration project, not automatic mod compatibility.
- Terra (dfsek) provides a powerful YAML-configurable generator for supported
  Bukkit / Fabric / Forge platforms, but its official compatibility listing
  does not establish a NeoForge 1.21.1 release; do not mix its JARs into
  this NeoForge client without a verified matching build.

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
2. On the YACL screen, choose **Landscape** (Realism MAX + GeoReal),
   **Alpine** (Himalayan + Realistic Mountains), or **Canyon**
   (Grand Canyon + Continental). Set **Secondary Landform Mix** (0..50%),
   Relief, Elevation, Caves, River Grade, Inland Spawn, then click **Save**.
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

## Four visible default choices, without corrupting eight original presets

In the normal vanilla world-type selector, the four primary *fallback*
algorithms remain:

1. Realism MAX (basis for merged Landscape)
2. Himalayan (basis for merged Alpine)
3. Grand Canyon (basis for merged Canyon)
4. USGS Big Sur (unaltered measured DEM)

The YACL editor creates three new **hybrid** world types, each blending
two authentic noise height signals (Landscape: Realism+GeoReal; Alpine:
Himalayan+RealisticMountains; Canyon: GrandCanyon+Continental). Once
generated, each hybrid appears in the new-world selector.

All original eight registry IDs stay in the extended world-preset tag:
GeoReal, Continental, Realistic Mountains and Ultimate have NOT been deleted,
aliased, or rewritten. Existing saves relying on them can still load.

For expert reproduction, CLI `--preset georeal_ultimate` etc. still uses the
exact legacy height signal and retains the original worldgen profile hash.
This preserves previously generated Lab worlds as well.

### Out-of-scope physics limitations

The merged presets *do not* solve disconnected frozen rivers, realistic
watershed routing, global erosion conservation or the pathological seed-42
USGS spawn QA. They merge actual functions and remove redundant menu
options, not claim a completed drainage simulator.

Minecraft 1.21.1's configurable dimension noise settings can cover a range
up to Y=2031, but doing so requires compatible dimension types and worldgen
height configuration; merely raising a slider will not automatically make
giant worlds stable or fast. The current four base modes retain their
original vertical bounds and default sea level Y=63.

Creating a new Lab hybrid world should always be followed by biome, spawn,
seam, river connectivity and cave audits on at least two seeds.
