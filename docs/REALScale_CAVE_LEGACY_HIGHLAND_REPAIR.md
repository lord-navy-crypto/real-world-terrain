# RealScale TVA — Cave decoration and highland recovery

Branch: feature/tva-cave-legacy-and-highland-ecology-recovery

## Audited root causes

1. New Overworld giant cave generation was wired into four default
   `noise_router.final_density` presets in Phase 23, restoring mountain
   galleries and >1 km exceptional routes. This is separate from the legacy
   spaghetti/cheese cave noise used by the cave wrapper.
2. The old `HabitatPatchFeature` still hard-coded **Y -58..45** for decorative
   cave floor search and refused cave placements above Y46, even when RealScale
   generated halls between Y~135 and Y~1330. The placed feature configuration
   also only sampled Y -38..32.
3. Old stalagmites required a preexisting `DRIPSTONE_BLOCK` beneath an air
   cell. Natural stone/calcite/tuff mountain cave floors were rejected.
4. Six named custom cave biome JSON files and `CaveHabitatMath` remain in the
   repository, but the default biome source has **not** been proved to select
   them. This change does NOT claim the six underground biome IDs are active.

## Repair implemented in actual worldgen

- Keep original low-altitude cave patch JSON unchanged for save/worldgen
  compatibility; add separate **middle** and **upper** placed-feature bands
  for moss and stalagmite (4 new placed features and 4 NeoForge biome modifiers).
- Middle: Y64..704; Upper: Y704..1776; 1 in 5 placement attempts per band.
- Target ordinary vanilla surface/mountain ecotone biomes as well as lush and
  dripstone cave biomes. A feature refuses vanilla/foreign-world generators
  through `TVAWorldGuard` and never opts marine/ocean biomes into cave patches.
- During actual feature placement, determine the column's local WORLD_SURFACE_WG
  height and require candidate cave air to be at least **20 blocks below** that
  surface, inside world bounds. Preserve the dry headspace, natural-stone
  foundation, fluid-empty tests and block.canSurvive guard.
- Permit dripstone/calcite/tuff/stone/deepslate substrates for stalagmites.
  Maintain no water/lava placement, no tunnel carving and no surface excavation.
- Extend the independent Java floor-search smoke to actual Y800..1559, with
  strict height and roof-limit cases. Add a Python gate for both newly deployed
  highland bands and preservation of the original low-altitude range.

## What is still NOT fixed

- Six cave-biome IDs need a genuine density-aware underground biome source and
  server/Anvil confirmation that they appear in buried areas without leaking
  into open mountain slopes or damaging ocean and surface biome selection.
- Other previous cave moods and decorations must be recovered against the old
  branch history, individually, not invented as a replacement for old features.
- The older 1.3 and legacy world presets remain a separate compatibility
  issue; this PR targets four primary TVA world presets, not saved historical
  worlds.
- Real saved-world cave volume, dry passage length, entry accessibility,
  decorations per large chamber and chunks/sec must be measured. Passing
  source tests alone does not prove that gameplay cave content is complete.
- Existing save users must back up before installing this experimental branch:
  newly generated chunks intentionally differ from older terrain.
