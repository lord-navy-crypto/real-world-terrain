# TVA Ultimate — unified research and safe player workflows

## Canonical new-world modes (four, and only four)

- **Natural**: `realscale:tva_sacred_timeline_three_realms` — base Realism MAX
- **Alpine**: `realscale:tva_alpine_three_realms` — Himalayan landscape
- **Canyon**: `realscale:tva_canyon_three_realms` — Grand Canyon landscape
- **Real-World**: `realscale:tva_measured_three_realms` — measured USGS Big Sur v2

Each uses Y -64 to 2031 Overworld, 512-block Grand Nether and 512-block
Grand End, the nested 60-ecotone / high-offshore sky source, and APES
vanilla-block habitats. There are no alternative canonical 256-block realms.
Original world-preset IDs remain in data for compatibility; older historical
presets are intentionally hidden from the new-world menu.

## Terrain Lab independent settings

The in-game NeoForge **Mods → RealScale Terrain** configuration, powered by
**YACL 3.8.1+** on the client, stores separate **Natural, Alpine and Canyon**
draft profiles. The measured USGS v2 is **read-only**, never height-scaled.

Draft settings include spatial blend, elevation/relief, connected cave
activity, guarded high-altitude river grade, ridge and erosion intensity,
optional geological strata, approximate radial/earth latitude, altitude
cooling, valley/basin warming, bounded cold budget, and opt-in Lithostitched
advanced geology. **These are geographic proxies, not an atmospheric or
hydrological simulation.** Radial latitude makes circular rings in Minecraft
coordinates; it is not physical Earth latitude.

Changing a running world's noise settings is not supported. Export the
saved draft into a new-world datapack *before* starting a new world:

```bash
cd /Users/jason/Desktop/RealScale-Gradle-Test
python3 scripts/export_terrain_lab_datapack.py \
  --jar neoforge/build/libs/RealScale-TVA-Ultimate-NeoForge-1.21.1-2.0.0-tva-preview.1.jar \
  --draft neoforge/run/config/realscale-terrain-lab.json \
  --output neoforge/run/TVA-Lab-Custom.zip
```

The exporter also produces a companion language resource pack. Put the
datapack in the new world's datapack selection before world creation and
enable its language pack. New Lab world presets still inherit the 512-block
Grand Nether and End and sky/ecotone source. The four canonical entries in
the installed mod JAR remain unchanged. The legacy `fantasyDimensions`
draft bit **cannot** replace the canonical Grand dimensions with older
256-block prototypes. The measured USGS profile cannot be edited.

## Separate, opt-in research tools (not automatically enabled)

- `scripts/make_vanilla_ecology_pack.py`: vanilla-block ecology pilot
- `scripts/make_geoecology_suite.py`: alpine snow, geology and karst microhabitats
- `scripts/make_vanilla_habitat_structures_pack.py`: fallen timber and river-edge structures
- `scripts/make_structure_density_packs.py`: experimental structure spacing changes
- `scripts/install_realscale_visuals.py`: isolated client shaders and textures
- `scripts/install_realscale_cinematic.py`: optional cinematic visuals

These are **additional opt-in tools**, not silent injections into the
canonical four world presets. Particularly, structure spacing and winter
research have NOT been proven compatible with every possible TVA world.

## Compatibility and safety

- Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.
- YACL is a separate **client-required** mod for the restored settings GUI.
  Servers do not need YACL. Lithostitched is needed only by explicitly
  exported profiles that reference `lithostitched:` density functions.
- **Do not enable Tectonic in the base RealScale preset**. Mod compatibility
  for Regions Unexplored, Terralith or Nature's Spirit needs distinct testing.
- Do not rewrite existing worldgen registries. Back up all important saves.
- The vanilla ocean biome source and seabed density remain untouched below
  Y160 by the sky wrapper; APES may plant vanilla-compatible seagrass in
  water, with ground intact. Oceans and structures still need broader
  generated-world evidence; upstream marine RCON QA has known failures.
- In-game sun/temperature/rainfall seasons, true global water balance,
  perfect 1:1 planet topography, and seamless mod compatibility are
  **not claimed**. All experimental features require measured outcomes.

## CI verification gates

The union runs: heritage checksum checks, 60-ecotone catalog,
9 inherited noise graph checks, 256-point USGS measurement fidelity,
DEM 4-side fading, optional radial/basin climate math, 3-family settings,
actual JAR-based datapack export, native NeoForge Gradle compilation,
client run task existence, and dedicated-server/Anvil tests of both Grand
realms and the optional offshore sky source.

A green build alone does not imply passing Anvil tests. **Do not merge
PR #15 until server checks conclude successfully**.

## TVA v2 continuous terrain — new-world registry mapping

The canonical Natural, Alpine and Canyon **new-world** presets now reference
new independent noise settings: `tva_natural_smoothed`,
`tva_alpine_smoothed`, and `tva_canyon_smoothed`.
The original `realism_max`, `himalayan` and `grand_canyon` noise settings
and all historical worldgen IDs remain intact for existing world saves.

Scientific A/B comparison of *saved Minecraft chunks* at seed 2, without
lowering thresholds:
* Natural 16-block boundary 4.97x → 1.07x, 4-block grid 67.52x → 1.01x.
* Alpine 16-block boundary 4.40x → 1.10x, 4-block grid 26.64x → 1.03x.
* Canyon with correct dimension-height decoding and interpolated signal:
  boundary 1.19x and grid 1.04x, zero isolated 4-neighbor spikes.

The high-level world presets remain four; the A/B investigation presets are
deliberately hidden from the player menu. The fourth, measured USGS v2, has
its own sampling algorithm and is not wrapped in the procedural interpolation.

The YACL Terrain Lab custom-profile exporter is still independent and must
be checked separately for its authored density signal and interpolation.
Existing saves must be backed up before changing versions.
