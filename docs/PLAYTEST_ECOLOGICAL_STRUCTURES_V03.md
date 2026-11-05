# PLAYTEST: RealScale ecological structures v0.3 (NeoForge 1.21.1)

## What is actually implemented
Four custom, compiled-feature types with Minecraft vanilla blocks:
- `realscale:fallen_oak_log`: 3–6 horizontal oak logs with complete, flat ground support
- `realscale:fallen_spruce_log`: 3–6 horizontal spruce logs with complete support
- `realscale:woodland_hummock`: small 2-block rooted-dirt/moss mound
- `realscale:upland_scree`: three contiguous, supported rock/gravel blocks

Each feature first checks the whole footprint for full dry ground support and free space, then commits all blocks. No terrain carving, fluid manipulation, world preset alteration or cave-noise changes. Understory short grass/fern may be replaced; trees, leaves, water, flowers and other obstructions are rejected. Feature checks avoid unsupported floating single-block placements and partial logs.

## How to install
1. Open the successful **Source integrity, tests and NeoForge build** GitHub Actions run at https://github.com/lord-navy-crypto/real-world-terrain/actions?query=branch%3Afeature%2Fecology-riparian-v1
2. Choose the latest **green** run matching this PR head SHA. Download the `RealScale-NeoForge-build-and-replay` artifact (requires GitHub sign-in). Extract the `.jar` from `neoforge/build/libs/`.
3. Use Minecraft Java **1.21.1**, NeoForge **21.1.249** (project version), Java **21** and install the JAR in your instance `mods` folder. Do not install two copies of RealScale.
4. Make a **new world** for testing and make a backup of existing saves. These features attach to existing vanilla forest, taiga, swamp and mountain biomes; no special world preset is required to see them.
5. Use `F3` to confirm you are in the correct biome and explore **newly generated chunks**.

## Where to look
| Structure | Target biomes | Approx. attempt frequency | Expected appearance |
|---|---|---|---|
| fallen_oak_log | Forest, Birch Forest, Dark Forest, custom woodland variants | 1 opportunity / 9 chunks | fully supported 3–6 block horizontal oak log |
| fallen_spruce_log | Taiga, old-growth taiga, snowy taiga, custom subalpine | 1 / 10 chunks | fully supported spruce log |
| woodland_hummock | forests and swamp edges | 1 / 12 chunks | rooted dirt adjacent to moss |
| upland_scree | windswept hills, stony peaks, meadow | 1 / 12 chunks | 3-piece small supported scree |

**Important:** rarity filter counts opportunities, not guaranteed successful placements. Structures may appear much less frequently because of canopy, grass, slopes, fluids or missing support. Search several hundred new chunks if necessary. The previous JSON vegetation prototypes are also present but not separately runtime-verified.

## Regression checklist
- Game starts, mod shows in Mods list, new world loads with no failed `feature` or `biome_modifier` registry keys
- Logs run along X or Z and never float, overwrite water or split across mismatched terrain heights
- Rock clusters are contiguous and supported; hummocks do not replace flowerbeds, tree trunks or rivers
- No noticeable sudden terrain, river or cave changes attributable to the structures
- Generate a new world twice with the same seed and mod list and compare structure positions
- Record exact Minecraft/NeoForge/mod versions, seed, X/Y/Z, F3 biome, `latest.log` and a screenshot on failures

## What has not been validated
JVM/Gradle and static JSON checks do **not** confirm worldgen registry decoding or the visual frequency in-game. The PR also contains unfinished 40 ecotone and six cave biome scaffolds and `biome_lab`/`ecotone_gallery` diagnostic checkerboard presets; neither diagnostic preset is a natural worldgen simulation. The server workflow is configured to test mod startup and DEM worldgen, but not all four feature sightings. Keep branch draft until human playtest succeeds.
