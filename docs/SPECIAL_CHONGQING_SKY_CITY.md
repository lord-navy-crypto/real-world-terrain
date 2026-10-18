# Special Chongqing — REAL Terrain Sky City (experimental)

**RealScale / NeoForge 1.21.1 — New worlds only.**
GitHub branch: `feature/20261018-special-chongqing-real-dem-sky-city`.

## What is implemented

A genuine Chongqing-area terrain pilot, not an AI-generated fake city:
- World preset in New World menu:
  `realscale:special_chongqing_sky_city` (Special Chongqing Sky City).
- 129×129 real DEM samples, ~64 m spacing (8.192 × 8.192 km around
  `29.5667 N, 106.5874 E`; Chaotianmen, Chongqing).
- Source observations **145–664 metres**; full grid SHA-256:
  `1db079896cd35c5dd795a1f121ac11d4b7afc5b7f788688f9be2cd7e3baaf6bc`.
- Map centre Minecraft (0,0) represents the source centre. Minecraft X
  increases east; Minecraft Z increases south. Horizontal scale 1:1
  (metres to blocks); vertical **relative** scale 1:1, with **+96**
  Y-offset to raise the scene for viewing (no exaggeration).
- Custom density node `realscale:chongqing_floating` generates a thin
  ~40-block **solid floating ground shell** below the DEM surface. The
  remainder is open air, including the entire space below the island.
  The top has a grass-block surface with a stone body. No deep caves,
  subterranean ores, aquifers, sea-level flood fill or bedrock floor.
- Distinct `chongqing_sky_overworld` dimension height **1024** (from
  -64 through Y=959) instead of scanning the full 2096-height TVA
  world for an 8-km museum pilot. All *other* RealScale presets continue
  using their existing settings (including the 2096-height normal TVA).
- Biome `minecraft:the_void` avoids fabricated vanilla villages,
  vegetation distributions, trees and mining networks on a culturally
  faithful area until REAL GIS layers exist. Blocks come from Minecraft's
  standard `minecraft:noise` generator, not direct file-level NBT editing.
- No terrain outside the measured DEM footprint: unknown regions are
  **VOID**, not procedurally invented Chinese topography. The pilot is a
  rectangular cropped floating landform. This is intentional until
  neighbouring authentic DEM coverage can be tiled seamlessly.

## REAL elevation source and auditability

Data is sampled directly from 9 publicly accessible AWS/Mapzen Terrarium
elevation PNG tiles (z=13, x=6520–6522, y=3390–3392), not from generated
Minecraft noise. Tile URL template:
`https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`.
Recorded 18 October 2026. Terrarium RGB decodes as
`R*256 + G + B/256 - 32768` **metres**.

Data were bilinearly interpolated from the underlying elevation raster
into a 129×129 grid using a local WGS84 tangent-plane approximation,
then rounded to integer metres. The source PNG is an elevation
**composite**, not a surveyed cadastral model; features narrower than
~64 m will be undersampled and geolocation accuracy is approximate.
The included grid is SHA-256 checked at runtime. Do not call its
heights 1 m-resolution LIDAR or exact building elevations.

**Attribution:** AWS Open Data / Mapzen Terrain Tiles and their
underlying global elevation dataset contributors. Consult the linked
provider/source credits and license terms before public distribution:
- https://geodataviewer.com/datasets/dem/aws-elevation-tiles/
- https://github.com/tilezen/joerd
- https://registry.opendata.aws/copernicus-dem/

An alternative for higher fidelity is the freely available Copernicus
GLO-30 Public DSM or GLO-90 COG with its respective license. DSM values
may include trees/buildings; a bare-ground DTM (where available) would be
better for reconstructing foundations. ALWAYS preserve original
provenance and legal usage terms.

## Replacing pilot data with higher-fidelity real Chongqing DEM

1. Download *authorized* genuine Chongqing raster elevation GeoTIFF
   covering the entire 8.2 km pilot; verify the source's CRS and license.
2. `pip install rasterio numpy pyproj` in a GIS virtual environment.
3. Run `python3 tools/chongqing_dem_import.py --dem my_real_chongqing.tif
   --citation "Source, acquired YYYY-MM-DD, official URL"
   --license "Applicable dataset license"
   --output neoforge/src/main/resources/assets/realscale/terrain/chongqing_chaotianmen_dem_v1.properties`.
4. Inspect min/max and image alignment, run test/demo.
   `qa/test_special_chongqing_worldgen.py` currently pins the original
   16k-source SHA for audit. If swapping source for a genuine higher-
   quality raster, update frozen attribution/hash/regression tests
   explicitly; never silently rewrite an existing world with new DEM
   heights. Open a **new world** for a different DEM asset version.

## NOT yet done, and why accuracy demands it

The prototype uses **only height information**. It DOES NOT yet
reconstruct:
- The precise Yangtze/Jialing **water polygons**, depth and banks.
  A DEM alone cannot uniquely delineate real river boundaries.
  The scene currently does not flood the river corridors.
- Streets, railway alignment, metro line through buildings, bridge decks,
  stairs or Chongqing's crucial multi-level road/bridge/tunnel crossings.
  These need correctly licensed OSM vectors and separate Y/3D data.
- Hongyadong, Chaotianmen architecture, Liziba station, cableway,
  bridge structures, roofs, landmarks or nighttime lighting.
  Real buildings require independent authoritative geometry or
  carefully checked human-made structures. It would be false to invent
  them from a generic Minecraft village template.
- Accurate underground tunnels, caves or underground metro levels.
  Underground was intentionally disabled by design to cut cost.

**Next stage:** GIS-aligned OSM water polygons (2D flood surfaces),
real roads/building footprints (and heights if licensed), landmark-specific
verified 3D model overlays, and a route through Chongqing's cultural sites.
Water, bridges and roads must use the exact same spatial reference system
as the DEM. Source credentials/licensing must not be hidden in the mod.

## Rendering, Chunky and performance

- This world mode is opt-in and separate; existing TVA optimizations and
  Chunky availability/World Atlas can still be used.
- The thin shell eliminates RealScale's expensive huge underground
  generation *for this preset only*, but does not by itself prove FPS
  improvement. Tall cliffs and distant visible landscape can still strain
  the GPU. Do not secretly clip visible surfaces/bridges.
- Chunky remains external/optional. Pregeneration can reduce exploration
  stalls. Do not embed, fork or duplicate Chunky's generation logic.
- New terrain nodes contain no per-voxel cross-chunk queries; the same
  immutable real sampled x/z height is cached per generation worker.

## Release gates

1. Integrity and source attribution SHA-256; Java21 DEM sampler smoke.
2. NeoForge codec bootstrap and dedicated server **actual** creation of
   Special Chongqing (not merely original TVA CI).
3. Compare sample world height with mapped true measurements for
   Chaotianmen, Yuzhong peninsula, river valleys and adjacent hills.
4. Check island shell below surface, no heavy underground generation,
   void beyond actual coverage, terrain side and top-surface continuity.
5. 4+ seeds in this specific preset, structure/biome/world re-entry and
   saved Anvil quality, Chunky compatible preload.
6. Real client world preview, landscape FPS, FPS 1% low, p95 frame time,
   rivers/bridge visibility only after those layers are added.

**Do not merge** to main until exact final SHA tests and real launch pass.
