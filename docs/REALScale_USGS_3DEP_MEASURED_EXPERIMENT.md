# RealScale TVA — USGS 3DEP Measured Terrain Experiment

**Minecraft 1.21.1, NeoForge 21.1.x, Java 21.** This is experimental, on
**new worlds**. The feature changes the already existing USGS Big Sur v2 DEM
profile only when a validated local real-data import is installed.

## What was wrong

The original mod bundled only a 16x16 USGS-derived interpolated tile with
32m spacing. That's just 480m x 480m. The rest of the measured preset
was extrapolated for 128m and blended to purely procedural mountains over
the next 2048m. Those surrounding procedural heights are **not** actual
USGS terrain. They can cause obviously unrealistic cliffs and boundary
mismatches. Do not claim the whole procedural RealScale world is measured.

This branch allows **real USGS 3DEP F32 bare-earth elevations** to be
sampled over much wider regions, imported once, verified with a matching
SHA-256 and a source-provenance manifest, then interpolated in the original
Minecraft NoiseBasedChunkGenerator with the same world-scale metric units.
No external network calls occur at chunk-generation time.

## Authoritative sources

- USGS 3DEP Elevation ImageServer (actual floating-point elevations,
  not a rendered hillshade or mountain-looking image):
  https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer
- USGS EPQS (alternate point lookup; interpolated values, not monuments):
  https://epqs.nationalmap.gov/v1/docs
- 3DEP 1/3-arc-second seamless DEM: approximate native horizontal resolution
  10m; coverage is widespread in the US. Downloaded 3DEP product tiles may
  come from multiple sources at different underlying resolutions:
  https://www.usgs.gov/3d-elevation-program/about-3dep-products-services
- USGS DEM horizontal reference NAD83; CONUS DEM vertical typically NAVD88:
  https://www.usgs.gov/faqs/what-projection-horizontal-datum-vertical-datum-and-resolution-a-usgs-digital-elevation-model

The new importer uses the USGS 3DEPElevation bare-earth dynamic service,
**NAD83 / UTM 10N (EPSG:26910)** near Big Sur and geodetically correct
metre-scale spacing. Its defaults request **513 x 513 float32 samples** at
**16 metres**: about 8.192 x 8.192 kilometres (16m pixel coverage, with 512
intervals between grid points). This is service *resampling*, not a claim
that every 16m source pixel is a separately surveyed laser point. Where
1m sources exist, users may later import a finer real source; no precision
is invented beyond the selected raster pixel size.

## How to create a real measured region

Prepare Python with rasterio and numpy (this work is done *offline*):

```bash
python3 -m pip install rasterio numpy
python3 scripts/import_usgs_3dep.py \
  --latitude 36.24 --longitude -121.81 \
  --grid 513 --spacing 16 \
  --output "/path/to/MinecraftInstance/config/realscale"
```

The `/path/to/MinecraftInstance` folder is the same one containing your
Minecraft mods and saves. On macOS modpack managers often create **separate
instance directories**, so use the instance's real folder.

The output consists of:
- `config/realscale/usgs_3dep.rgt.gz` — deterministic compressed source
  elevation grid, neutral (zero) placeholders for absent rainfall/lithology
  inputs rather than invented rain data.
- `config/realscale/usgs_3dep.sha256` — exact file digest, validated by mod.
- `config/realscale/usgs_3dep.json` — coordinates, CRS, official source
  service URL, source GeoTIFF SHA, raster units, min/max and acquisition time.

For testing geometry without downloading data:
```bash
python3 scripts/import_usgs_3dep.py --dry-run
```
This requires rasterio but does not issue a request to the USGS service.

**Critical:** If the region includes ocean gaps outside USGS ground DEM
coverage, the importer refuses the dataset rather than writing invented
seabed elevations. Choose a covered inland Big Sur site or use a smaller
window (e.g. `--grid 257`) and regenerate. Bathymetry requires a different
authoritative source (NOAA and USGS integrated topobathymetric datasets).
This branch does not pretend that land DEM describes seafloor depths.

After copying the files, **restart both server and client**, and create a
**new** world using the existing RealScale measured Big Sur v2 preset. Old
Realism MAX and original 16x16 DEM profile intentionally continue using
the SHA-pinned bundled resource.

## Game coordinate mapping

- Big Sur source centre maps to Minecraft X=0, Z=0.
- The project's documented default Big Sur import anchor is 36.24°N,
  121.81°W. The zonal climate profile uses the corresponding nominal
  equator Z=4,027,458 and prime-meridian X=13,559,827 offsets, so the local
  DEM origin is not mistaken for the equator or Greenwich. If a custom DEM
  is imported at another coordinate, its manifest must be used to recalibrate
  these offsets before claiming geographic alignment.
- Minecraft X increases east and is the longitude axis; Minecraft Z increases
  **south** and is the latitude axis. GeoTIFF is north-up: raster first row is
  northmost and becomes the most-negative Z.
- World horizontal and vertical units are 1 Minecraft block per metre.
- RGT1 stores metres above the USGS elevation datum; game solid surface
  at a sample is Y = 63 + source_elevation_m.
- No per-region vertical scaling, no elevation clamps, no simulated
  erosion inserted into the measured tile. Any terrain modifications from
  separately registered Minecraft cave features, aquifers, structures,
  etc. are explicitly **gameplay additions**, not claimed USGS observations.
- Within the measured DEM grid, the RealDemSeamlessMath interpolation is
  monotone and preserves source sample elevations. Outside it, the preset
  still smoothly blends to **procedural** fallback over 2048 metres,
  which must NOT be mislabelled as real-world data.

## Data safety and reproducibility

An external raster is optional. Without it, the unchanged and
SHA-pinned original 480m 16x16 bundled DEM loads. When any one of the
three external files exists, all three must exist and pass metadata,
coordinate system, size, offset, elevation range and cryptographic checksum
checks. The runtime does not pin a fixed service URL; source provenance is
retained in the import manifest. A mismatch raises a startup error rather
than silently fabricating or switching terrain. This dataset is loaded
once at JVM startup; replacing data while a world is active is unsupported.

On multiplayer servers and clients, use exactly the same three files
(and hashes) for reproducibility. A saved world should retain a copy of
the exact import manifest. Never replace DEM inputs in an existing save
without a world backup; the new grid can cause old/new chunk boundaries.

## Validation requirements

1. Python unit tests: 3DEP-only source, raster float type, metric projection,
   valid 16/32m grids, reproducible binary serialization and NoData rejection.
2. NeoForge Java21 build and density graph tests, confirm dynamic array sizes.
3. On a new seed, compare named sample world coordinates to imported raw
   elevation values and ensure **metre-accurate heights** before carvers.
4. Save and inspect Anvil chunks through all four DEM edges, test ocean
   boundaries and actual river/lake continuity rather than merely showing
   screenshots.
5. Measure chunks/sec, p95 chunk-generation time, heap, GC and peak server
   tick time with 16m 513x513 input versus legacy 32m 16x16.
6. Do not mark stable until multiple fixed-seed server/client real-world
   test regions work. USGS coastal/ocean and source-coverage limitations must
   remain visible to the player.

**No source DEM was downloaded by CI.** The importer's unit tests use synthetic
numbers only to check binary geometry. Therefore a green CI proves the data
pipeline and compatibility, not that a particular new real-world tile
has been sourced, packaged or inspected in Minecraft.
