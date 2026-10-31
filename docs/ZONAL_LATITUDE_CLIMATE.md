# RealScale Zonal Latitude and Longitude Climate Model

## Decision

The default geographic projection is a planar, equirectangular-style map:

- **X is longitude:** positive X moves east.
- **Z is latitude:** negative Z moves north; positive Z moves south.
- **Y is elevation.**
- **equator_z** anchors latitude 0°.
- **prime_meridian_x** anchors longitude 0°.

The former concentric-ring model remains registered and available for experimental and historical world presets. The four current canonical climate profiles now use the Z-axis zonal model; the four opt-in PR #25 historical profiles intentionally retain their radial model.

## Coordinate conversion

At one Minecraft block per metre, the meridional quarter-distance is:

[
Q = 10{,}001{,}966 	ext{ blocks}
]

The equatorial circumference used for longitude wrapping is:

[
C = 40{,}075{,}017 	ext{ blocks}
]

Latitude is finite and clamped at the poles:

[
phi(Z)=90^circ,
operatorname{clamp}left(
rac{Z_e-Z}{Q},-1,1
ight)
]

Longitude increases eastward and wraps to the conventional interval [-180°, 180°):

[
lambda(X)=
operatorname{wrap}_{[-180,180)}
left(
360^circrac{X-X_0}{C}
ight)
]

Here Z_e is the equator Z and X_0 is the prime-meridian X. The finite latitude clamp is intentional: moving beyond either pole stays in that polar climate instead of repeating another equator and hemisphere. The old periodic triangle-wave method remains unchanged for older serialized settings.

The projection is a rectangular map, not a sphere. Minecraft keeps equal block spacing in X at every Z, so east-west ground-distance distortion increases toward the poles. Longitude is a geographic coordinate and future regional-field input; it is **not** used as a direct baseline temperature control.

## Climate calculation

The existing temperature noise remains the regional baseline. The zonal node applies the same balanced latitude and altitude treatment previously used by the canonical radial profiles:

[
T_{	ext{lat}} =
T_{	ext{base}}+
sleft(0.22-0.90sin^{1.35}(|phi|)ight)
]

The balanced layer retains the tropical/temperate buffers and altitude-cooling refund. The existing mountain-basin, valley, basin, and climate-budget nodes remain separate layers. The climate budget limits the combined correction; it does not claim global energy conservation or a fixed global biome-area fraction.

Latitude controls the broad annual-mean temperature pattern. X-dependent noise, continentalness, local moisture, the ocean/land layout, and terrain remain available to create regional differences at the same latitude. No longitude-only temperature gradient is added.

This is a static world-generation climate approximation. It does not simulate seasonal axial tilt, weather, dynamic winds, or time-evolving ocean currents.

## Default profile anchors

| World profile | Equator Z | Prime-meridian X | Purpose |
|---|---:|---:|---|
| TVA Natural | 0 | 0 | Synthetic global map |
| TVA Alpine | 0 | 0 | Synthetic global map |
| TVA Canyon | 0 | 0 | Synthetic global map |
| USGS Big Sur v2 | 4,027,458 | 13,559,827 | Nominal project source anchor: 36.24°N, 121.81°W |

The Big Sur offsets follow the project's documented default USGS import anchor. A custom measured DEM imported at a different latitude/longitude must be recalibrated from its own manifest; local DEM coordinates alone do not establish geodetic position.

## Compatibility and validation

- Existing latitude-temperature records without clamp, balanced, or prime-meridian fields retain their prior defaults.
- The new default profiles use pole clamping, balanced latitude response, and the Earth-scale quarter-meridian.
- Existing radial codecs and equal-area algorithms are unchanged.
- Historical tva_pr25_*_climate profiles remain opt-in and radial.
- Tests cover pole clamping, longitude wrapping, Big Sur's nominal anchor, default density-graph wiring, and radial regression parity.

## Scientific references

- NOAA National Ocean Service, [What is latitude?](https://oceanservice.noaa.gov/facts/latitude.html)
- NASA Earth Observatory, [Climate and Earth's Energy Budget](https://science.nasa.gov/earth/earth-observatory/climate-and-earths-energy-budget/)
- NASA Earth Observatory, [Ocean and Climate](https://science.nasa.gov/earth/earth-observatory/ocean-and-climate/)
- NASA GPM, [Geographical Influences on Climate](https://gpm.nasa.gov/education/sites/default/files/lesson_plan_files/geographical%20influences/Geographical%20Influences%20-%20TG.pdf)
