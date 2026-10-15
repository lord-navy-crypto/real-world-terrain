# TVA Sacred Timeline — equal-area concentric climate recovery

Minecraft 1.21.1 / NeoForge / Java 21. Experimental **new-world** change.

## Confirmed regression

The code for radial latitude, altitude-biome cooling, valley warming,
enclosed-basin warming and climate-budget limits existed in the Java classes
and Terrain Lab exporter. But the four canonical TVA Overworld
`noise_router.temperature` definitions still pointed to unmodified vanilla
`minecraft:shifted_noise`. For ordinary Create World > TVA world selection,
the scientific climate system was **not invoked**.

This change does not merely restore the unused source files: it connects
each of the four actual canonical climate routers to a complete named
`biome_temperature` density function:
`tva_natural_smoothed`, `tva_alpine_smoothed`,
`tva_canyon_smoothed`, and `usgs_big_sur_v2`.

## Why the old concentric bands did not have equal areas

For a polar annulus with inner radius a and outer radius b, the land
area (in a planar world) is pi*(b*b - a*a). Equally wide radial rings
therefore do NOT give climate belts equal areas. The old
`balancedRadialLatitudeDeg` used a power transform of linear distance;
it did not enforce equal-area area fractions.

The **new, independent, opt-in** `radial_equal_area` mapping uses
absolute radius r from fixed world center (0,0), annular cycle
[2kQ,2(k+1)Q], and area fraction:

    q = (r*r - (2*k*Q)^2) /
        ((2*(k+1)*Q)^2 - (2*k*Q)^2)

    latitude = 90 * (1 - abs(2*q - 1))

This is implemented in factored form to avoid floating-point cancellation
at remote world coordinates. Because q is proportional to *annular area*,
latitude subranges 0..30, 30..60, and 60..90 each occupy one third of
the area of each complete ring cycle (before biome noise and land/ocean
masking). Geometry is radially symmetric, continuous at period
boundaries, and deterministic across chunk edges.

Default Q=8192 blocks, so the first polar-circle boundary moves from the
old 8192 radius to approximately **11585** blocks; equatorial seams still
occur at radius 16384, 32768, etc. The center is world coordinates (0,0),
NOT the player's possibly far-away spawn.

**Important:** Equal-area *temperature latitude classes* does NOT
mathematically guarantee equal *land biomes*. Humidity, noise, land/ocean
fraction, height and the Minecraft multi-noise biome picker still matter.
It is a principled geometric correction rather than forcibly repeating
every biome in equal percentages.

## Complete on-disk climate chain in EACH TVA preset

1. Original vanilla-seeded temperature noise (unchanged as climate baseline).
2. Relative elevation lapse/proxy cooling, smoothly ramping over Y96..Y768.
3. True area-balanced concentric latitude from radial ring center (0,0).
4. Tropical/temperate highland cooling compensation (keeps mountains varied).
5. Local valley warmth if inland relief qualifies.
6. Enclosed basin warmth only where all eight rim samples are higher.
7. A final clamp vs original temperature noise of -0.60..+0.45.

The science proxy does **not** simulate weather, dynamic seasons, real solar
irradiance, atmospheric pressure or wind. The capped adjustments preserve
a chance for warm, temperate and cold biomes; they do not enforce area
fractions of actual spawned biomes.

All vanilla oceans, original sea level Y63, existing world IDs, Grand
Nether and Grand End dimension settings, world terrain density,
grand-cavern `final_density`, and seed-deterministic non-climate
algorithms are left alone. Actual biome boundaries will change in
**newly generated chunks**, which is intended, and may affect biomes
at existing world edges if installed into older saves.

Measured USGS uses its real DEM terrain-density as an approximate
relief signal for valley and basin classification. The other three
world types use existing height-signal density functions. Because that
is more expensive than a simple temperature noise, benchmark actual
chunk-generation CPU and memory before declaring it optimized.

## Existing saves and Config

- This update restores the climate directly in all four **built-in**
  TVA presets. For these predefined modes, users do NOT need to export a ZIP
  or edit Config just to get the default balanced temperature system.
- YACL Mods > RealScale > Config > Shared Climate & Latitude / Export
  now offers **radial_equal_area** for **custom Terrain Lab worlds**.
  As before, custom Terrain Lab changes require saving and running the
  Config Exporter, then enabling its new-world datapack.
- The old `radial_balanced`, `radial_preview`, `radial_earth`,
  `earth` and `preview` values retain their previous math and
  serialization. They are not silently migrated.
- NEVER experiment with a non-backed-up important save; always start a
  fresh world or copy an existing one.

## Release / verification gate

- Independent Java 21 smoke verifies six ring area distributions via
  deterministic uniform-area samples, every important radius seam,
  negative coordinates, legacy numeric parity and temperature cap.
- JSON QA verifies every actual four TVA `noise_router.temperature` ID
  points to a resolvable complete chain and ensures the previously
  restored `grand_overworld_caverns` output remains in `final_density`.
- Terrain Lab exporter/CLI and YACL regression tests cover
  `radial_equal_area` while preserving previous `radial_balanced`.
- NeoForge Gradle compile, actual new-server world generation,
  fixed-seed biome sample histograms, ocean/sky/underground cave
  regressions, and CPU profiling must all pass before merging.
