# RealScale 1:20 Horizontal Map and Periodic Z-Latitude Climate

## Adopted coordinate model

The canonical RealScale world uses a **1:20 horizontal map scale**:

- Minecraft X block coordinates are converted to source-map metres by multiplying by 20.
- Minecraft Z block coordinates are converted to source-map metres by multiplying by 20.
- Y is elevation and is intentionally unchanged.
- **Z is the only geographic coordinate used by the climate model.**
- X is horizontally scaled with Z to preserve the map's horizontal aspect ratio, but no longitude is calculated and X adds no climate term.
- The old concentric/radial latitude algorithms remain available as opt-in experiments.

The scale is applied to the roots of the active noise router by
`realscale:horizontal_scale`. The density wrapper uses a reusable per-thread
context pool; it does not allocate a new context object for each density
sample. It scales X/Z only and delegates Y unchanged.

This is a horizontal compaction, not a fully isotropic 3D scale: terrain
elevations remain 1 block per source metre while horizontal distances use
20 source metres per block. The resulting relief is vertically exaggerated
relative to its horizontal footprint by a factor of 20. This is intentional
for the current design and must be considered when evaluating measured DEM
terrain and slopes.

## Earth-scale latitude constants

The meridional quarter-distance remains the physical Earth value:

`Q = 10,001,966 source metres`

At 20 source metres per Minecraft block:

- Equator to either pole: `Q / 20 = 500,098.3 blocks` (about 500,098 blocks).
- Equator to the opposite equator: about 1,000,197 blocks.
- Full latitude cycle: `4Q / 20 = 2,000,393.2 blocks`.

The density node stores `pole_distance` and `equator_z` in source metres.
The horizontal-scale wrapper supplies source-metre Z coordinates to the
latitude node. This keeps the geographic constants independent of the
Minecraft block scale.

## Periodic latitude sequence

Let `Z` be the Minecraft Z coordinate, `S=20` the horizontal scale,
`Z_e` the equator coordinate in source metres, and `Q` the quarter-meridian
distance. The periodic phase is:

`p = mod((Z_e - S*Z) / Q, 4)`

The signed latitude is:

- `90p` when `0 <= p <= 1`
- `90(2-p)` when `1 < p <= 3`
- `90(p-4)` when `3 < p < 4`

This produces one continuous full cycle:

`equator 0° -> north pole +90° -> opposite equator 0° -> south pole -90° -> original equator 0°`

The sequence repeats every approximately 2,000,393 Minecraft blocks along Z.
There is no polar clamp in the canonical presets. The old clamped calculation
remains available for legacy serialized settings.

The annual-mean temperature response is symmetric across hemispheres:
the climate correction uses the absolute value of latitude. The signed
latitude is retained by the coordinate function to distinguish north and
south in diagnostics and future geography work.

## Default profiles

| Profile | Equator Z (source metres) | Pole distance (source metres) |
|---|---:|---:|
| TVA Natural | 0 | 10,001,966 |
| TVA Alpine | 0 | 10,001,966 |
| TVA Canyon | 0 | 10,001,966 |
| USGS Big Sur v2 | 4,027,458 | 10,001,966 |

The Big Sur anchor preserves the project's documented source centre at
36.24°N. At local Minecraft Z=0, the source coordinate is 36.24°N.
No prime-meridian/X offset is stored or used by the climate model.

## Climate and regional variation

Latitude controls the broad annual-mean temperature pattern. Existing
temperature noise, continentalness, moisture, ocean/land layout, terrain
elevation, mountain-basin effects, valley warming, basin warming, and climate
budget remain separate inputs. X-dependent terrain and noise can still create
regional variation, but X is not converted to longitude and does not create
a longitude-driven climate gradient.

This remains a static world-generation climate approximation. It does not
simulate seasonal axial tilt, time-varying weather, dynamic winds, or
time-evolving ocean currents.

## USGS measured terrain

The DEM remains stored in source metres. The horizontal-scale wrapper passes
`X*20` and `Z*20` into the measured DEM density, so a source grid with
16 m cells has an effective 0.8-block spacing and a 32 m grid has a 1.6-block
spacing. The existing interpolation evaluates the original measured samples
in source coordinates. Elevation values remain 1:1 in Y.

## Compatibility and validation

- Existing worlds and their saved datapacks are not modified in place.
- The canonical four profiles use the horizontal scale wrapper and periodic
  Z-latitude.
- Terrain Lab unwraps and re-wraps the temperature node exactly once when it
  creates a new climate density function.
- Terrain Lab equator and radial-center inputs are entered in Minecraft
  blocks and converted to source metres at export.
- The optional legacy `prime_meridian_x` codec field is retained only so old
  datapacks can still be decoded; it is not calculated.
- Existing radial codecs and equal-area algorithms are unchanged. Their
  center coordinates and radius are stored in source metres inside the
  1:20-scaled worldgen router.
- Historical PR25 radial profiles remain opt-in: the concentric formula is
  preserved, while their former 8,192-block pole radius is represented as
  163,840 source metres so the playable radius remains 8,192 blocks at 1:20.
- Tests cover horizontal coordinate scaling, periodic latitude landmarks,
  north/south temperature symmetry, canonical density graph wiring, DEM
  source-coordinate mapping, and radial regression parity.

## Scientific references

- NOAA National Ocean Service, [What is latitude?](https://oceanservice.noaa.gov/facts/latitude.html)
- NASA Earth Observatory, [Climate and Earth's Energy Budget](https://science.nasa.gov/earth/earth-observatory/climate-and-earths-energy-budget/)
- NASA Earth Observatory, [Ocean and Climate](https://science.nasa.gov/earth/earth-observatory/ocean-and-climate/)
- NASA GPM, [Geographical Influences on Climate](https://gpm.nasa.gov/education/sites/default/files/lesson_plan_files/geographical%20influences/Geographical%20Influences%20-%20TG.pdf)
