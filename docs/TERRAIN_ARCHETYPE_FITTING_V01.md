# Terrain Archetype Fitting v0.2

## Design contract

RealScale has one generic **Natural Landscapes** default and six planned real-data-fitted archetypes. The archetypes are statistical models, not raw elevation playback:

1. Alpine Mountains — Everest/Himalaya
2. Canyons & Valleys — Grand Canyon
3. Glacial Valleys — Yosemite Valley
4. Volcanic Landscapes — Hawaiʻi shield volcanoes
5. Karst Landscapes — Guilin tower karst
6. Sand Dunes — Namib Sand Sea

Each target is defined in `research/v1.1/data/terrain_dem_targets.json`. The first common baseline is Copernicus DEM GLO-90, a global 90 m DSM in meters with WGS84 horizontal coordinates and EGM2008 vertical datum. GLO-90 is useful for broad morphology, but not fine cliffs, small dolines or individual dune ripples. Where available, USGS 3DEP bare-earth products are preferred for a higher-resolution second calibration of the U.S. targets.

## Data acquisition and provenance

Run:

```bash
python3 -m pip install -r research/v1.1/requirements.txt
python3 research/v1.1/tools/build_terrain_profiles.py --all --seed 42 --out-dir build/terrain-archetype-calibration
```

The acquisition tool downloads the exact public COG tile, crops the declared study window, and writes a provenance sidecar with the original tile hash and cropped DEM hash. Raw tiles and crops stay outside version control. The profile contains source URLs, product identifiers, hashes, CRS, grid spacing, vertical datum, elevation units and fit statistics.

The build workflow also runs this pipeline and publishes a 30-day artifact containing the compact JSON profiles, synthetic previews and summary—not the raw source tiles.

## Fitted parameters

The fitter extracts elevation percentiles, P05–P95 relief, slope quantiles, fine/mid/coarse RMS amplitudes, directional semivariograms, variogram exponent, anisotropy, and the dominant non-zero spatial wavelength/orientation. The preview kernel uses these measured parameters with a new seed to synthesize a new field. Family transforms create ridge-sharpened alpine terrain, cliff/valley canyon relief, broad U-shaped glacial valleys, shield cones/calderas, tower-karst/doline forms and wind-aligned dune ridges.

The preview is a statistical prototype, not a geological process simulator. A fitted morphology does not establish tectonic, glacial, volcanic, dissolution or aeolian causality.

## Runtime integration status

The current pipeline fits real DEMs and creates new parameter-only previews. It does **not yet** compile the profiles into NeoForge DensityFunctions, so the game must not be described as already using the six real-data fits. The next integration gate is to define a stable profile-to-density-function compiler, load each family through its own world-preset/noise-settings ID, and verify generated saved chunks across multiple seeds.

Minecraft's current Overworld height budget cannot preserve Everest's absolute elevation at vertical 1 block = 1 meter. The runtime must explicitly choose relative relief mapping or vertical compression; the profile must not silently claim absolute 1:1 vertical scale.

## Official data references

- Copernicus DEM public GLO-90 tile listing and COG naming: <https://copernicus-dem-90m.s3.amazonaws.com/readme.html>
- Copernicus DEM product handbook, including EGM2008 vertical reference: <https://dataspace.copernicus.eu/sites/default/files/media/files/2024-06/geo1988-copernicusdem-spe-002_producthandbook_i5.0.pdf>
- Copernicus DEM public dataset and license: <https://registry.opendata.aws/copernicus-dem/>
- USGS 3DEP products and resolutions: <https://www.usgs.gov/3d-elevation-program/about-3dep-products-services>
- USGS 3DEP 1/3 arc-second product metadata: <https://data.usgs.gov/datacatalog/data/USGS%3A3a81321b-c153-416f-98b7-cc8e5f0e17c3>


## Publishing the fitted presets

The controlled `.github/workflows/publish-fitted-terrain-presets.yml` workflow regenerates all six profiles from public source tiles, compiles the profile coefficients into the six runtime density graphs, merges the seven-mode menu/localizations, runs the focused regression gates, and commits only the compact profiles plus generated resources after every preflight test passes. It activates only when the head commit subject contains `[publish-terrain-presets]` on a same-repository pull request. Raw DEM tiles and cropped rasters remain build-local and are never committed.


The publication preflight deliberately runs the focused seven-preset, source-provenance, shared-biome-chain, and worldgen resource contract suites. The separate Terrain Lab suite contains older, unrelated assumptions about experimental dimension toggles; it is not used as a gate for publishing the fitted presets.
