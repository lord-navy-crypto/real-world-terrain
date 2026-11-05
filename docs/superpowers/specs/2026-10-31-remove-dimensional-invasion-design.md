# Remove Dimensional Invasion — Design

## Intent
Remove the optional cross-realm / dimensional-invasion gameplay system from RealScale. The request is about dimensional invasion, not latitude-based climate simulation.

## Goals
- Remove invasion biome sources, worldgen features, biome modifiers, presets, registrations, and obsolete QA that only validates invasion.
- Preserve the Overworld, Nether, and End dimension topology and all normal world-generation behavior.
- Preserve latitude climate density functions, Natural Ecotones, Geo Wonders, Sky Ocean, cave systems, measured USGS terrain, and all public terrain presets.
- Keep the ordinary Geo Wonders opt-in preset working without an invasion wrapper.

## Non-goals
- Do not alter latitude temperature, radial latitude, climate budgets, noise settings, USGS data, or terrain density graphs.
- Do not remove the three vanilla dimensions.
- Do not rewrite or reformat unrelated world presets.

## Approach
Delete the isolated CrossRealmBiomeSource, CrossRealmShowcaseBiomeSource, CrossRealmTerrainPatchFeature, and DimensionalInvasionFeature family; remove only their registration and wrapper/guard hooks; delete the invasion-only data assets and presets. Retain GeoWondersBiomeSource and SkyOceanBiomeSource as ordinary independent wrappers.

## Acceptance
- No production Java registration or resource references remain for cross-realm invasion.
- No invasion-only world preset or biome modifier / configured feature / placed feature remains.
- Natural Wonders remains opt-in and works without invasion.
- Existing latitude climate and three-dimension worldgen regression tests remain intact.
