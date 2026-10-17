# PR #25 climate recovery: four optional historical world families

**Source:** `feature/tva-area-balanced-climate-recovery` (f485155). Migrated into PR #30 integration without replacing new climate.

Historical PR #25 has a different live temperature chain:
`climate_budget` (-0.60 / +0.45) → `basin_temperature` → `valley_temperature` → `radial_latitude_temperature`.

New core TVA default has newer terrain-aware `mountain_basin_temperature` and different temperature budgets. **Neither version is deleted or silently substituted for the other.**

| Optional restored world preset ID | Noisemodel referenced |
|---|---|
| `realscale:tva_pr25_natural_three_realms` | `realscale:tva_pr25_natural_climate` |
| `realscale:tva_pr25_alpine_three_realms` | `realscale:tva_pr25_alpine_climate` |
| `realscale:tva_pr25_canyon_three_realms` | `realscale:tva_pr25_canyon_climate` |
| `realscale:tva_pr25_measured_three_realms` | `realscale:tva_pr25_measured_climate` |

Each uses historical PR25 `biome_temperature.json` content. The **current** noise-router geology, original final cave density, terrain height, sea level, Grand Nether/End and sky/ecotone biome-source wrapper are cloned unchanged from the latest corresponding family. Restored preset is opt-in, not added to default world list.

**Limitations:** These are historical climate *prototypes*; the original PR25's named climate functions can be more CPU-intensive than the newer mountain-basin temperature. They are not yet eligible to replace the sacred default, and real client/server boot + fixed-seed Anvil samples remain mandatory before release. Do not open important existing saves in these experimental world modes.
