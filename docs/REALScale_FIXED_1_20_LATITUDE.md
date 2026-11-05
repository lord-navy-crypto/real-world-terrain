# RealScale fixed WGS84 1:20 climate latitude

## One supported geographic climate model

All new RealScale Terrain Lab profiles use a single meridional latitude model:

- Fixed equator: `Z = 0`.
- North is negative Z; south is positive Z.
- WGS84 equator-to-pole meridional distance: approximately 10,001,966 metres.
- Climate-only compression: 1:20.
- Fixed equator-to-pole distance: approximately 500,098 Minecraft blocks.
- X is not used as longitude or as a climate coordinate.

The 1:20 factor applies only to the climate-latitude calculation. It does not rescale terrain, DEM sampling, cave geometry, structure coordinates, or horizontal X/Z terrain sampling. Terrain remains 1 block = 1 metre.

## Shared calculation

The live Geographic Climate Inspector and the worldgen `LatitudeTemperatureDensity` function both call `GeographicClimateMath.geodeticLatitudeDeg`. This keeps the report and biome-temperature input aligned, including the WGS84 meridional correction rather than using a separate linear triangle approximation.

The Terrain Lab GUI exposes only a latitude-strength slider. Editable merged families default to the fixed model; immutable legacy presets retain their existing climate graph. Strength 0 disables the latitude contribution; nonzero values always use the same fixed model. The CLI exposes only `fixed_1_20` (plus `off` for compatibility). There are no selectable 1:1, preview, radial, equal-area, or movable-equator models.

## Backward compatibility

Old YACL drafts containing historical mode names are normalized to `fixed_1_20` when read or exported. Their old equator/ring-center fields are ignored. Old worldgen JSON still deserializes because the legacy codec fields are retained, but the runtime meridional calculation uses the fixed constants.

The radial and climate-budget codecs remain registered only to deserialize older saved-world datapacks. New UI, CLI, and exporter paths do not emit radial density nodes.
