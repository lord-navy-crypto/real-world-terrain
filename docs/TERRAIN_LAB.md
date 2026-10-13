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

## v1.6 — Lithostitched and custom density functions (experimental)

**Pinned source:** [Lithostitched 1.6.0 for NeoForge 1.21.1](https://modrinth.com/mod/lithostitched/version/1.6.0-neoforge-1.21.1),
MIT licensed. The repository's Gradle mod dependencies resolve the artifact
`maven.modrinth:XaDC71GB:u0UUKAE1`. A standalone RealScale mod installation
also requires a matching Lithostitched JAR on **both client and server**.
YACL continues to be a separate **client-only** dependency. Lithostitched is
installed for future biome/structure and terrain interoperability; this
release does **not** force global biome injection or use unsupported custom
structure codecs.

### Two native custom `DensityFunction` codec implementations

1. `realscale:ridge_erosion`: takes `base`, seed-dependent `ridge_signal`,
   seed-dependent `erosion_signal`, `ridge_gain` (0..0.32 density units),
   and `erosion_gain` (0..0.24 density units). Smooth ridgeline uplift is
   decreased where erosion climate is higher; incision subtracts a bounded
   amount. The function is mathematically continuous when its inputs are
   continuous and bounded by `base.minValue()-erosion_gain` and
   `base.maxValue()+ridge_gain`. It is **not** a water-and-sediment transport
   model; no volume or flow conservation is claimed.
2. `realscale:strata`: takes `base`, noise-based `warp`, `strength`
   (0..0.06 density units) and `period` (24..128 blocks). Adds a subtle
   vertically periodic solidity variation with a clamped seed-sensitive warp,
   *not* ore/material layer replacement. The function retains safe min/max
   bounds and is applied only when the user selects nonzero strata strength.

The YACL editor now offers three additional opt-in controls:
`Ridge Enhancement 0..40`, `Erosion Incision 0..30`, and
`Warped Geologic Strata 0..20`; all default **OFF (0)**.
Gradle reads the YACL draft before resource compilation, and
`scripts/terrain_lab.py` writes new unique density registries for the
**three merged families only**. CLI equivalent:

```bash
python3 scripts/terrain_lab.py --preset landscape --blend 25 \
  --ridge 10 --erosion 8 --strata 4 \
  --relief 1.02 --elevation 4 --caves 50 --river-grade 6
```

The two new Java codecs are registered in `RealScaleMod.registerDensity`
alongside the existing immutable `realscale:dem_density`. They are visited,
seed-resolved and evaluated in the same regular `NoiseBasedChunkGenerator`
pipeline used by RealScale. **Old v1.5 generated profile IDs remain byte-for-byte
unchanged when all three new sliders equal zero**, and all eight historical
preset IDs remain available. Y=63 sea level remains fixed.

### Why no custom ChunkGenerator now?

Replacing the existing `NoiseBasedChunkGenerator` would require implementing
and validating the complete chunk lifecycle (terrain density, surface rules,
aquifers, biome climate, features, structures, heightmaps, cave intersection,
spawn and persistence). The current architecture can already register
custom math without giving up the proven chunk lifecycle. Full replacement
should be considered only if a future globally continuous watershed / DEM
tile solver needs algorithms that cannot be encoded efficiently as
density functions or ordinary placed features.

### Verification

```bash
python3 -m unittest discover -s qa -p 'test_*.py' -v
cd neoforge && gradle --no-daemon --console=plain test build
```

The dedicated experimental CI generates **a new landscape world with all
three custom options active** and scans actual saved terrain, rivers, caves
and spawn. This test must pass before the new functions are considered
verified in live NeoForge worldgen. Broad seed testing and in-game visual
verification remain separate unresolved checks.

## v1.7 Experimental — altitude-aware biome climate (safe per-world implementation)

### What is implemented

New **Altitude-Aware Biomes** slider (0..100, YACL default **65**) applies
ONLY when preparing a **new merged Terrain Lab** world type. It changes the
`noise_router.temperature` input of **that world's own** `noise_settings`.
Vanilla's existing `minecraft:multi_noise` biome picker therefore receives
lower temperature at higher sampling altitude, while humidity, erosion,
continentalness, weirdness, underground biome parameters, surface rules,
and sea level are left in their original registered pipelines.

The generated density node `realscale:terrain_lab/<id>/biome_temperature`
contains:

```json
{
  "type": "minecraft:add",
  "argument1": "<original temperature shifted-noise density function>",
  "argument2": {
    "type": "minecraft:mul",
    "argument1": -0.585,
    "argument2": {
      "type": "minecraft:y_clamped_gradient",
      "from_y": 96,
      "to_y": 320,
      "from_value": 0.0,
      "to_value": 1.0
    }
  }
}
```

This illustrative example uses **65%** slider strength; the `argument1` in
actual generated JSON contains the *unchanged real base temperature object*,
not a placeholder string. The cooling factor is
`0.009 * climate_percent * clamp((block_y - 96) / 224, 0, 1)`.
Temperatures below Y=96 remain identical to their base values. Maximum
adjustment is -0.9 climate noise units at 100%. This is a **Minecraft
biome-selection temperature parameter**, not a Celsius air-temperature
simulation, and remains affected by seed, humidity, inlandness and other
biome parameters.

Use:

```bash
python3 scripts/terrain_lab.py --preset alpine --blend 25 \
  --biome-cooling 65 --ridge 10 --erosion 8 --strata 4
```

Or save the slider in **Mods -> RealScale Terrain -> Config**, exit the game,
and use Gradle `runClient` again to bake the new immutable profile into
the worldgen registries. The revised `lab_*` ID includes nonzero climate
strength; existing v1.5/v1.6 IDs and old world registries are untouched.
`--biome-cooling 0` preserves byte-identical legacy profile JSON/hashes.
Measured **USGS Big Sur** stays read-only.

### Why not add a global Lithostitched biome injector?

Lithostitched 1.6.0 supports `data/<namespace>/lithostitched/biome_injector`
with custom density function parameter conditions. However, its published
injector format scopes to a **dimension key**, e.g.
`"dimension":"minecraft:overworld"`, not to a named *world preset*.
Unconditionally bundling an Overworld injector in the RealScale mod
would change **vanilla or third-party Overworlds as well** and could save
an incompatible wrapper in user worlds. We deliberately did **not**
register any automatic global injector. The installed Lithostitched mod
is still available for separate compatible worldgen integration; the
safe, per-preset climate-routing link uses Minecraft's own
`NoiseRouter`, which Lithostitched can coexist with.

### Verification and limitations

The saved Anvil world scanner now reports actual surface biome distributions
in four Y bands, including snowy/frozen and hot/dry/tropical groups:
Y63–95, Y96–159, Y160–239, and Y240+. Its observations distinguish
climate-routing effects from the surface material and terrain-shape tests.
These are diagnostics; they cannot establish a universally correct snowline.
The dedicated real NeoForge server job uses `--biome-cooling 65` plus the
custom geometry functions to check live registration and saved-block data.

Important: this is an **altitude temperature lapse**, not a complete
surface-height-prediction / temperature-rainfall / geomorphology model.
Deep underground biome selection may also be influenced by Y-dependent
temperature because it uses a 3D biome sampler. High desert and alpine
vegetation still require calibrated climate/humidity and surface rules;
Lithostitched alone does not solve that. No default worldgen preset, biome
injector, vanilla game climate, existing save, or external dimension is
modified by this optional Lab parameter.

## v1.8 Optional Valley Warming — local relative-depression proxy

YACL now offers **Valley Warming (Optional)**, from 0 to 100 in 5-point
increments, with a **safe default of 0 (OFF)**. The equivalent CLI setting is
`--valley-warming 35`. Combined use:

```bash
python3 scripts/terrain_lab.py --preset alpine --blend 25 \
  --biome-cooling 65 --valley-warming 35
```

When nonzero, `scripts/terrain_lab.py` wraps **only the new Lab world**
temperature density function in the new registered
`realscale:valley_temperature` custom `DensityFunction` codec.

This codec compares the local height signal to four samples 96 blocks away
(E/W/N/S). A positive relative depression, inland continentalness and
height below Y256 all provide smooth, bounded factors. The maximum warmth
at 100% is +0.35 *Minecraft biome-climate parameter* (not 0.35 °C).
Near-ocean regions and high ridges receive no guaranteed warming.
The default 0 does **not** add any codec or change previous v1.7 world
preset IDs, world files or old saves. The measured Big Sur DEM is excluded.

**Limitations:** This is an approximate topographic position index, NOT a
surface-aware weather forecast. A true atmospheric model would need
radiation, winds, moisture, time-of-day and cold-air pooling calculations;
cold-air drainage and inversion can make actual valleys colder than slopes.
Minecraft's biome climate is a fixed worldgen classification and cannot
represent daily temperatures. Samples may have extra noise-evaluation cost,
so this feature is opt-in until verified on multiple seeds.

Validation includes unit tests of bounds and coast/elevation masking, YACL
draft import and actual NeoForge server world generation with both the
altitude cooling (65) and valley warming (35) switched on. The tests do not
claim that all valleys become warmer, or that Lithostitched automatically
guarantees climatically correct biome distributions.

## v1.9 Experimental — continuous cyclic latitude and optional enclosed-basin climate

### Earth-equivalent latitude

The **Latitude Climate Cycle** selector has three modes:

- **off** (default): no latitude adjustment. Previously generated profiles,
  historic worlds, all eight old world types, and measured USGS Big Sur
  remain unchanged.
- **earth**: one block corresponds to one metre along the north-south
  Earth meridian, with **10,001,966 blocks** per 90° equator-to-pole quarter.
  The Earth's meridional circumference is approximately 40,007,864 m.
- **preview**: **16,384 blocks** per quarter; useful for physically travelling
  across the biome transitions. This deliberately compresses the climate
  latitude scale, **not** the terrain block size, which remains 1m/1 block
  only in the real Earth scale mapping.

The configured **Equator World Z** defaults to 0. Minecraft's Z decreases
northward, so north is -Z and south is +Z. Signed latitude folds continuously
from **0° → +90° → 0° → −90° → 0°** (for northward travel), repeating
every four quarters without a discontinuity at the poles. Its mathematical
period is only a climate-model tiling choice, **not** a physical global map
of a spherical Earth. Longitude (X) does **not yet** modulate climate.

**Spawn caveat:** Actual automatic world spawn is chosen *after* terrain
generation and can be far from world origin. The generator cannot safely
use that future coordinate as an equator anchor without circular worldgen
dependencies. Use `--equator-z <integer>` (or the YACL offset slider for
Z = -4096..4096) to pick an appropriate predefined equator line; default 0
is merely close to the preferred start region. We make no claim that the
saved spawn is exactly at latitude 0°. If necessary, a future world
creation UI can resolve the equator anchor before the terrain seed is
registered, not by rewriting a previously played world's worldgen profile.

The latitude codec `realscale:latitude_temperature` wraps the previous
biome temperature density function. At 100% strength, it adds a bounded
climate noise offset from +0.22 near the equator to −0.68 near the poles.
It remains **relative to regional temperature**, not a real-world
thermometer in Celsius; humidity, coastlines, mountains, erosion and existing
temperature noise also affect the final selected biome.
YACL **Latitude Climate Strength 0–100** defaults to 70, but the mode is
OFF by default for compatibility. This is seed-independent Z latitude; it
does not produce concentric rings around the player or the world origin.

Example with real Earth scale:

```bash
python3 scripts/terrain_lab.py --preset landscape \
  --biome-cooling 65 \
  --latitude-mode earth --latitude-strength 70 --equator-z 0
```

Example for practical biome inspection:

```bash
python3 scripts/terrain_lab.py --preset alpine \
  --latitude-mode preview --latitude-strength 70 --equator-z 0
```

### Enclosed basins vs ordinary valleys

**Enclosed Basin Warming 0–100** (default OFF) is separate from the existing
four-sample **Valley Warming** control. A `realscale:basin_temperature`
node uses **eight points** in a 192-block ring to check whether terrain
density-height signals indicate a consistently elevated rim. An open valley
with one lower outlet fails that strict proxy. Warming is further gated
by inlandness and bounded to +0.35 Minecraft climate units at strength 100.

This classifier **does not generate new basin terrain** or alter rock strata.
It changes which biome can appear in *preexisting basin-shaped landforms*.
A true basin geometry generator with drainage/outlet and groundwater
analysis remains a separate later implementation. A real basin may be
cold at night due to cold-air drainage and temperature inversions; this
optional warmer-basin proxy does **not** model diurnal climate, winds or
physical energy conservation.

Example:

```bash
python3 scripts/terrain_lab.py --preset alpine \
  --latitude-mode preview --latitude-strength 70 \
  --biome-cooling 65 --valley-warming 0 --basin-warming 20
```

The YACL screen saves `latitudeMode`, `latitudeStrength`, `equatorZ`
and `basinPercent` into its **next-new-world draft**. The Python
resource generator compiles it to a distinct hash-addressed world ID.
No auto-created global Lithostitched Overworld injector or retcon to
existing world settings is involved.

### Verification and limitations

Both new Java density functions implement Minecraft/NeoForge 1.21.1
`DensityFunction` codecs and use the same NoiseRouter climate path.
Standalone pure-math JUnit tests cover equator/north/south/pole continuity,
bounds and enclosed vs open valleys. Python tests check YACL draft import,
proper per-preset density graphs, historical hash stability and parameter
validation. The experimental CI workflow starts a **new** server with
preview latitude and enclosed basin warming enabled and checks the saved
world and region files.

A successful unit test cannot validate biome quality; final approval also
requires inspecting several actually generated worlds at different seeds
and measuring warm/cold biome distribution along north–south travel.
One-block-per-metre real-scale travel to a pole would require about
10 million blocks, well outside typical render-distance QA.

## v2.0 experimental — user's concentric radial latitude and bounded climate budget

**Two newly available concentric modes**: `radial_earth` and
`radial_preview`, alongside the historical north–south
`earth`/`preview` and `off`. A radius
`r = hypot(x - centerX, z - centerZ)` determines repeating unsigned latitude:
equator (`r=0`), circular north/south-neutral polar ring (`r=D`),
equator (`r=2D`), pole (`r=3D`), equator (`r=4D`), etc. The triangle wave
has no discontinuous jump at a pole. This is **not conventional spherical
Earth geography**: its "poles" are circles; there is no true meridian
longitude or signed hemisphere in the concentric model.

- `radial_earth` sets `D = 10_001_966` Minecraft blocks (approximately
  10,001.966 km at 1 block/meter).
- `radial_preview` sets `D = 16_384` blocks to inspect several belts
  without traveling millions of blocks.
- `--radial-center-x` and `--equator-z` set the center. (The latter
  field is also used by the old meridional Earth mode.)
- The initial center defaults to **world (0,0)**. The actual spawn is
  selected by the engine after generation begins and cannot be guaranteed
  to coincide with this fixed coordinate. We do not claim spawn anchoring.
- The radial codec is `realscale:radial_latitude_temperature`, with
  an explicit `center_x`, `center_z`, `pole_distance`, `strength`
  and `base`, registered in `RealScaleMod`.

**Protection against excessive ice/snow biome coverage:** The optional
`realscale:climate_budget` DensityFunction caps the **combined**
altitude/lattitude/valley/basin climate adjustments relative to the world's
ORIGINAL temperature field. The radial-only YACL slider
**Radial Cold Climate Limit** is 0..100 with default **60**:
`0` disables the cap, otherwise `max_cooling = coldLimitPercent/100`
noise units, with an upper warming limit of `+0.45` noise units.
Its default -0.60 cap stops the default max -0.585 altitude modifier
and polar modifier -0.476 from accumulating to -1.061 climate units.
Neither these numbers nor the +0.45 cap are degrees Celsius;
these are Minecraft biome-placement parameters, not actual air temperature.

The clamp limits **magnitudes, not biome area fractions**. There is no
claim of conserving physical energy, a 50/50 cold-hot split or universally
preserving all tropical biomes. Terrain elevation distribution and humidity
still matter; the saved-world Anvil audits must be used to tune the
parameters on representative seeds, across polar rings and warm belts.

Example:
```bash
python3 scripts/terrain_lab.py --preset landscape \
  --latitude-mode radial_preview --latitude-strength 70 \
  --radial-center-x 0 --equator-z 0 --cold-limit 60 \
  --biome-cooling 65 --valley-warming 25 --basin-warming 20 \
  --ridge 10 --erosion 8 --strata 4
```

All five latitude modes are an **option on new hashed Lab profiles**.
Legacy eight original worldgen IDs, measured Big Sur DEM, previously
generated meridional profiles and all existing saves stay unchanged. The
`climate_budget` density codec is only introduced when an active radial
mode is selected with a nonzero cold limit. The CI real-world job tests
the radial preview, cap and combined mountain/valley/basin options.

### Scientific interpretation

NOAA's global mean atmospheric environmental lapse rate is approximately
6.5 °C per km. However, the world's biome-temperature density noise is
**not** measured in °C, and Minecraft's height-gradient strength must be
calibrated empirically rather than using a 1:1 °C conversion. Mountain
valleys can be warmer than surrounding peaks; inversions and cold-air
pooling can make basin floors colder on some nights. Therefore, neither
uniform heating of all low-altitude blocks nor universal positive basin
warming would be a faithful physical law. The present optional valley
and basin proxies are bounded simplifications.

NOAA atmospheric rates:
https://www.noaa.gov/jetstream/appendix/weather-glossary-l

NOAA basin inversion and persistent cold-air pools:
https://repository.library.noaa.gov/view/noaa/25478

## Standalone JAR: export YACL Config to a new-world datapack (Beta)

Ordinary RealScale JAR users can now use the bundled RealScale-Config-Exporter.pyz
(Python 3.9+). No Gradle or source checkout is necessary. The exporter uses
the worldgen resources embedded inside the matching RealScale mod JAR.

1. Install Minecraft 1.21.1, NeoForge, Java 21, the RealScale JAR and
   its matching dependencies (Lithostitched on client/server; YACL on client).
   Back up the instance; install only one RealScale version.
2. Open Mods -> RealScale Terrain -> Config. Configure Landscape, Alpine or
   Canyon, Save, and exit Minecraft completely. Locate the instance's
   config/realscale-terrain-lab.json file.
3. Download the matching RealScale-Config-Exporter.pyz build artifact, and
   run the following with your own full file paths:

       python3 RealScale-Config-Exporter.pyz --jar /path/to/RealScaleTerrain.jar --draft /path/to/instance/config/realscale-terrain-lab.json --output /path/to/My-Lab-World.zip

4. In Create New World -> Data Packs, add and enable My-Lab-World.zip BEFORE
   selecting the custom realscale:lab_* World Type. Create a NEW world.
5. Optionally install My-Lab-World-Language.zip in Options -> Resource Packs
   to give the world type English and Chinese names instead of a raw ID.

The data pack format is 48 and the optional resource pack format is 34,
for Minecraft Java 1.21.1 only. The datapack references RealScale's custom
worldgen codecs, so keep the same RealScale mod JAR installed. Keep the
exported datapack with the new saved world; do not replace it with different
worldgen settings on an existing save. The tool will not overwrite an
existing exported ZIP, change the JAR, or modify old world saves.

This is a working standalone OFFLINE EXPORT flow, not live in-game worldgen
editing, and not a one-click Apply button inside YACL. Measured USGS Big Sur
remains read-only to preserve the original DEM elevations.

## YACL screen cleanup and real Lithostitched adaptive blending (opt-in)

The client YACL editor retains its supported NeoForge 1.21.1 version. The
control screen now separates terrain shape, climate/latitude, and height/caves/
rivers. The disabled fake "Restart/Build Required" boolean has been removed.
The title and base-mode tooltip explicitly explain that **Save writes a
NEXT NEW WORLD draft**, not an in-game change. Standalone JAR users must
exit Minecraft and use the accompanying RealScale-Config-Exporter.pyz to
turn config/realscale-terrain-lab.json into a world-creation datapack; then
enable it under Create New World > Data Packs and select the new Lab world.
YACL writes drafts atomically to avoid partially written JSON files.

**Lithostitched Adaptive Blend** is a new optional control (default OFF)
for the three merged Terrain Lab families. It also has an equivalent CLI
switch, --lithostitched-blend. When active with a nonzero secondary blend,
the generator ACTUALLY uses Lithostitched 1.6.0's
lithostitched:mix density codec, with a smoothly varying weight driven by
the secondary terrain family's existing erosion signal:

    local_weight = clamp(blend_fraction + 0.05 * secondary_erosion, 0, 0.5)
    mixed_height = lithostitched:mix(primary_height, secondary_height, local_weight)

This changes the world generator: it is not a cosmetic YACL toggle.
It changes the secondary blend by at most about five percentage points
where the erosion signal is within [-1,+1]. It is NOT watershed erosion,
does not add new biomes, and does not globally inject structures.

Enabled profiles use new content-hashed worldgen IDs marked
"lithostitched_adaptive_v1", so previous Lab profiles are not rewritten.
With the toggle OFF, previously generated profile IDs/JSON remain intact.
The nine fixed worldgen types (including both measured Big Sur DEM pilots)
remain untouched. Lithostitched 1.6.0 stays a required mod on both
client and dedicated server. The dedicated Terrain Lab CI now generates
and boots a real world with lithostitched:mix enabled and scans saved blocks.

## Lithostitched Deep Geology: opt-in seeded terrain, strata, and cave integration

This is a **NEW Terrain Lab profile** only; it is not a global biome or
structure modifier and does not touch the nine fixed RealScale presets,
measured USGS DEM, existing Lab profile hashes, or existing saves.

**Switch:** YACL -> Terrain Shape & Height ->
**Lithostitched Deep Geology (Experimental)** (default OFF).
CLI: `--lithostitched-geology`.
Requires one of Landscape / Alpine / Canyon and Secondary Landform Mix > 0.

The opt-in worldgen pipeline now uses multiple *actual* Lithostitched
1.6.0 / Minecraft 1.21.1 codecs in three separate systems:

1. **Regional terrain geometry**: a profile-specific seeded
   `lithostitched:perlin` fast-noise configuration and
   `lithostitched:fast_noise` generate smooth spatial variation.
   Minecraft's native `add`/`mul` density functions now blend the
   two EXPENSIVE family height fields with a bounded regional weight
   (secondary share varies by +/-4 percentage points). Lithostitched's
   `mix` only operates on two cheap, equally bounded Perlin inputs;
   `shift` resamples one Perlin signal at fixed offsets to add at most
   +/-0.02 to secondary height density (roughly +/-2.56 blocks with the
   standard 128-block height gradient). This avoids bypassing efficient
   Minecraft batch density calculation. The Y=63 sea level is unchanged.
2. **Solid-rock layer shaping**: when Strata > 0, a weak
   `lithostitched:sin` of the `lithostitched:axis` Y coordinate is
   blended with the existing erosion warp (20% contribution) inside
   RealScale's existing density-based strata codec. This changes *solidity*
   slightly; it does not replace Minecraft's geological block materials.
3. **Caves**: Lithostitched Perlin noise also informs a small
   regional cave-solidification signal. Native `minecraft:mul` and
   `minecraft:add` apply a bounded 0..+0.025 density bias while
   evaluating the expensive cave signal once. This avoids upstream
   Lithostitched `mix` output-bound under-reporting for unequal constant
   arguments. The cave exterior protection remains intact.
   It is NOT a watershed solver or hydraulic erosion simulation.

Each deep profile gets a unique content-hashed `lab_*` ID, and the
standalone `RealScale-Config-Exporter.pyz` includes the custom Lithostitched
fast-noise registry JSON in its new-world datapack. It still requires a
matching RealScale mod JAR **and** Lithostitched on both client and server.
A profile with the Deep Geology switch OFF retains its previous noise
trees/hashes (including prior Adaptive Blend v1 profiles).

The range-safe optimized pipeline is tagged `perlin_shift_strata_caves_v4` so it will
never overwrite earlier v1 experiment profile IDs. These are deliberate,
bounded experimental effects, NOT claims of measured geology or physical
erosion. The real-world CI creates a deep Landscape
world and inspects saved blocks; offline tests cover all three Lab families
and exporter pack contents. Deep mode takes precedence over the lightweight
Adaptive Blend v1 toggle, so both switch combinations produce the same
v3 density/profile ID. Benchmark high-altitude server-startup times before
promoting this experimental mode to stable.


**Dedicated NeoForge 1.21.1 compatibility:** Lithostitched 1.6.0
accepts the existing erosion registry reference directly in the strata
`mix` codec; nesting that reference inside `minecraft:clamp` caused a
registry load failure in the v3 experimental profile. The v4 profile
uses the already bounded direct erosion signal, and the unchanged
`sin(axis Y)` branch. The original nine fixed presets are unaffected.

## Consolidated preset catalog and per-family YACL profiles (release-candidate)

RealScale now has **four meaningful new-world choices** in both Minecraft's
normal and extended world-type menus:

| Visible core type | Underlying behavior | Custom controls |
| --- | --- | --- |
| Natural / Realism MAX | Existing Realism MAX fixed profile; GeoReal becomes the optional secondary height signal of the Landscape editor | Landscape YACL family |
| Alpine / Himalayan | Existing Himalayan fixed profile; Realistic Mountains becomes the editable secondary source | Alpine YACL family |
| Canyon / Grand Canyon | Existing Grand Canyon fixed profile; Continental becomes the editable secondary source | Canyon YACL family |
| Real-World / USGS Big Sur v2 | Measured DEM pilot with four-side transition fixes | **Read-only**; select directly in Create New World |

**What "consolidation" actually means:** The nine existing worldgen
registry IDs and their original noise/density/world-preset JSON are
**preserved**, including GeoReal Ultimate, Continental, Realistic Mountains,
Ultimate and USGS Big Sur Legacy. Only the world-type tags for **new**
world creation are reduced to four entries. Previously saved worlds can still
resolve their original registered generator types; there is no destructive
renaming, conversion, or forced relinking. The old IDs are not offered as
new UI choices. They remain usable by explicit old dedicated-server
\`level-type\` selections where Minecraft permits them.

**YACL has independent configuration slots per editable family.** Open
Mods -> RealScale Terrain -> Config:

1. In **Choose World Type**, pick Landscape, Alpine or Canyon. The
   fourth, USGS Big Sur v2, is measured and cannot be safely rescaled;
   choose it directly in Minecraft's Create New World screen.
2. Tune each family in its own YACL tab. Its blend, relief, elevation,
   ridges, erosion, strata, caves, river grade, altitude cooling, valley
   warming, enclosed basin warming, inland-spawn preference,
   Lithostitched options and fantasy dimensions **persist separately**.
3. Shared **Latitude & Export** options control latitude cycle/strength,
   ring center and radial cold budget for the active exported world.
4. Press **Save**. The editor writes all three family slots as
   \`profiles\` in the same JSON draft **and also copies the currently
   selected family's values to the legacy top-level fields**. Thus the
   existing offline Config Exporter and Gradle \`--from-yacl\` keep working.
   Exit Minecraft; export a new-world datapack with the matching JAR.
5. Enable the datapack *before* creating the new world. Nothing here can
   modify existing chunks or load a new preset dynamically.

Suggested **starting** values, not measured physical guarantees:

| Tuning | Landscape | Alpine | Canyon |
| --- | ---: | ---: | ---: |
| Secondary terrain mix | 25% | 30% | 30% |
| Relief | 100% | 110% | 105% |
| Elevation offset | 0 | +10 | 0 |
| Ridge | 6 | 18 | 6 |
| Erosion | 5 | 8 | 18 |
| Strata | 4 | 8 | 12 |
| Caves | 50 | 55 | 45 |
| Safe river grade | 6 | 3 | 6 |
| Altitude cooling | 60 | 70 | 35 |
| Valley warming | 15 | 25 | 35 |
| Enclosed basin warming | 10 | 10 | 30 |

The profiles intentionally differ, but their effects remain seed-dependent.
High mountains may be cold, and river continuity is **not** solved solely by
adjusting the river-grade selector. Advanced Lithostitched Deep Geology is
OFF by default for every family and requires Secondary Landform Mix > 0.

**Migration:** old YACL files without \`profiles\` migrate their selected
family's existing flat values intact; the other two families receive
their suggested starting values. Files with independent slots load each
slot separately. No old saved world is edited. Keep a backup before upgrading.

**Release gate:** The last pre-consolidation CI had two known failing jobs:
legacy \`usgs_big_sur / seed 2\` produced disconnected individual ice/water
columns, and \`himalayan / seed 2\` lacked a persisted spawn chunk in the
audited region. They remain genuine blockers pending repaired and rerun
worldgen evidence. Static tests and Java compilation alone cannot approve
the first stable release.
