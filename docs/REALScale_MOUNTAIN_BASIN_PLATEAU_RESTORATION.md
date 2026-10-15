# TVA Restoration: Mountains NEED Basins and Plateaus

Minecraft 1.21.1, NeoForge, Java 21; applies to **new chunks in new worlds**.

## Existing history and root cause

Previous generations already had high mountains, erosion, ravines, lowland valley
field shapes and `basin_temperature` climate math. But the old basin **climate**
class does NOT create any depressed physical basin, nor does a few canyon ridges
ensure broad elevated plateau tops. There was no explicit default-world relief
wrapper guaranteeing both landform types in the same mountain range.

This change adds *actual terrain height modifiers*, not just names or extra
JSONs, while keeping the original height graphs as separate references.

## Geometry

- The three procedural flagship worlds (TVA Natural, Alpine and Canyon) now
  calculate their height signals through `realscale:mountain_relief`.
- The original interpolated heights are preserved verbatim as
  `original_height_signal.json`; the original sea level, river features,
  structures, cave final-density, sky islands and dimension heights remain.
- Each 2048×2048-block region has one potential major landform selected
  from a seed-dependent erosion signal and an absolute-coordinate site hash.
  Regions can produce an enclosed upland basin, a wide plateau or no change.
  Landforms are ~470–700 block radius and taper to zero outside their
  bounded site footprint, so neighboring Minecraft chunks use identical
  continuous data regardless of loading order.
- **Basin**: lower bowl or valley-floor depression with a subtle raised
  geological rim and gradual internal/external relief. Max depth changes
  around 0.78–1.18 RealScale density units in the three families, before
  inland/upland gating. Not an automatic water lake or artificial dirt dam.
- **Plateau / mesa**: wide, anchored-height shelf with flattening over its
  interior, gradual escarpment edge and uplift of 0.56–0.86 density units,
  before gating. Existing mountain noise continues outside each site.
- `inlandUplandGate` gradually turns the change off in ocean, shallow
  coastal and lowland zones, preventing baseline seafloor corruption.
- Measured USGS Big Sur v2 high resolution elevation is not modified by
  invented basins/mesas. Legacy Realism MAX/Himalayan/Grand Canyon presets
  remain unchanged for compatibility.

## Climate

The already restored equal-area radial latitude and elevation cooling remain
in effect. Procedural basin locations now additionally receive at most
0.10–0.14 temperature units of positive *optional climate adjustment*, using
the SAME absolute cell, erosion selector and original baseline heights as
terrain relief. Existing `climate_budget` caps overall positive warming
and negative cooling. The climate modifier is a gameplay approximation:
real enclosed highland basins can exhibit cold-air pooling and nighttime
temperature inversions; it does not assert all real basins are warmer.

## Intentional limits and release acceptance

1. This is procedural regional relief inspired by natural basins, plateaus
   and mountain valleys, not a plate-tectonic uplift / million-year erosion
   simulation. A closed terrain bowl may become a lake only where separate
   hydrography places water; it is NOT immediately a navigable river.
2. It modifies new terrain and may create visible boundaries against
   old saved chunks. Always use a fresh test seed/save.
3. The wrapper samples some site anchor values but caches them per worker
   and per density instance with **bounded thread-local storage**.
4. Check in the saved Anvil world: genuine broad flat mesa tops, enclosed
   inland basins and rim profiles; contiguous rivers, unblocked waterways,
   dry/wet cave distribution, ocean floors, erosion diversity and average
   chunks/sec. Compare same seed and previously ungenerated coordinates.
5. Code and mathematical smoke tests DO NOT by themselves establish final
   geomorphological quality. Do not merge draft code until real dedicated
   server and cave/river/ocean regression tests pass.

## Regeneration and QA

```sh
python3 -m unittest discover -s qa -p test_mountain_basin_plateau_default.py -v
cd neoforge && gradle --no-daemon build
```

CI compiles and runs independent MountainReliefMathSmoke (basins, rims,
flattened mesas, coasts, arbitrary signed coordinates and smooth tapers);
the default-preset graph test verifies three real terrain/density graphs and
climate-budget inclusion, while checking measured DEM untouched.
