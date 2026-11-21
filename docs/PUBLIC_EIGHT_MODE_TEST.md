# Unified Natural Automate single-mode experimental candidate (2026-11-21)

The standard and extended Create World menus expose **exactly one RealScale preset**:

- **RealScale Natural Automate — Unified Adaptive Terrain & Ecology**
  - World preset: `realscale:natural_automate_three_realms`
  - Base terrain: the existing smoothed Natural height signal
  - Fitted morphology kernels: Alpine, Glacial, Canyon, Volcanic, Karst and Dune
  - Selection inputs: broad regional affinity, raw shifted temperature and vegetation/humidity proxies, and local base topography
  - Blending: normalized suitability weights with a smooth coverage ramp
  - Fallback: the Natural baseline is preserved exactly where no fitted family is suitable

The composite keeps the shared ecology chain (`sky_ocean → geo_wonders_opt_in → natural_ecotones → cave_depth_habitats → vanilla multi_noise`) and the Overworld, Nether and End configuration.

## Hidden resources are retained, not exposed as separate modes

The Natural baseline and six standalone fitted world-preset JSON resources remain in the JAR for compatibility, controlled QA and developer comparison. They are **not** listed in either ordinary Create World menu. The six fitted morphology kernels are still active inside Natural Automate; hiding their standalone preset IDs does not disable or delete their algorithms, fit coefficients, caches or source provenance.

USGS Big Sur measured profiles also remain hidden as previously decided. No historical world-preset ID is removed by this menu consolidation.

## Validation contract

- Both `normal.json` and `extended.json` must contain exactly one entry: `realscale:natural_automate_three_realms`.
- The catalog validator must account for all 41 world-preset resources, classifying one as public and 40 as hidden/legacy.
- Source tests verify all six fitted-family suitability gates are reachable, climate/topography fallback remains exact, and transitions remain continuous.
- The candidate JAR must package the Natural Automate density graph and all retained hidden compatibility resources.
- Dedicated-server worldgen smoke and multi-seed QA remain separate from physical-client FPS testing.

This is an experimental single-mode candidate, not a Stable release. Physical-client performance, broad seed coverage and full terrain-quality acceptance are still required before release.
