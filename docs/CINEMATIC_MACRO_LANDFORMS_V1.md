# Cinematic macro landforms v1 — actual 4-km terrain density generation

Status: prototype in PR #75; requires client chunk-generation screenshots and all CI
and existing saved-world checks. The algorithm is original and informed by
photographs of real Wulingyuan sandstone, South China karst, dry mesas and
river-cut canyons. No Genshin assets or prebuilt terrain maps are imported.

## Implementation (not merely surface decoration)

**New codec:** `realscale:cinematic_landforms_v1`
(`CinematicLandformDensity.java` + pure `CinematicLandformMath.java`).

**Selected noise settings:** `realscale:tva_cinematic_landforms_v1`.
The player-facing Natural Exploration preset
`tva_sacred_biodiversity_end3_spawn3_v3` now points at this new
**versioned** noise-settings ID, while retaining the complete original
Sky Ocean -> rare Earth -> geo wonders -> End invasion -> v3 ecotone
biome source stack. Other player world presets and historic `tva_natural_smoothed`
remain unchanged.

The modified height signal is computed *before* the existing vertical
Y-clamped gradient and cavern-carving stage; new terrain morphology affects
the actual density/solid surface, not 5-block decorative landmarks.

- 4096-block site cells, with site centers jittered <200 blocks.
- Site footprint radius 1075–1350 blocks and an envelope exactly zero before
  cell boundaries. World-absolute positions avoid chunk-order seams.
- Seed-resolved vanilla temperature and vegetation noise at the site center
  gate the archetype (wet warm pillars, dry warm mesas, temperate dry canyons),
  while continentalness and original height gate out ocean and lowland impact.
- **Sandstone towers:** jittered 205-block-spacing local lattice of steep
  ~2.65 density-unit pillars, linked by lower wooded terrain.
- **Karst cones:** rounder 300-block-spacing peaks with smoother shoulders.
- **Caprock mesa:** broad plateau and abrupt continuous escarpment;
  ~3.05 density-unit rise.
- **Incised canyon:** curving channel, depressed floor and elevated shoulders.
- No terrain-changing fluid actions, biome swaps, chunk neighbor reads or
  replacement of pre-existing ore/biome feature registries.
- Approximate surface-height conversion ~128 blocks per RealScale density unit;
  actual height varies by base terrain and continental/upland gates.

## Crucial boundaries

This is a **landscape-morphology prototype**, not actual 1:1 DEM of any named
place, and it is not tied to georeferenced polygons. Climate classification
uses Minecraft's seeded temperature/vegetation noise as a proxy; it is not a
scientific ecological niche model.

The measured/USGS player's `usgs_global_hybrid_marine` settings and its
data-preservation behavior are unmodified. Historic
`tva_natural_smoothed` is left as-is to avoid silently rewriting earlier
saved-world noise IDs. A new world's biome/density is generated from the new
profile. Save backups and loaded-chunk parity require separate in-game checks.

**Not yet in v1:** actual hydrologically linked Huanglong colored travertine
pools, long cliff-side waterfalls, real-world geolocated biome assignment,
large structure-safe natural rock bridges, or shader-defined valley fog.

## Regression gates

1. New JSON noise codec referenced and registered; all 4 player menu entries
   preserved; original noise settings exactly preserved except 3 intentional
   router pointers in the *new* profile.
2. Actual JDK compilation and execution of
   `CinematicLandformMathSmoke`: deterministic selection, all archetype
   relief signatures, safe 4-km cell boundaries, finite bounded heights,
   ocean/lowland invariance and hundreds-of-block prototype tower height.
3. Full NeoForge compile/registry parse and saved-chunk boot: no warnings or
   world loading errors, acceptable generation CPU/memory and cross-chunk
   smoothness in a new Natural Exploration world.
4. Client screenshots from 3 distances, seed-to-seed sampling,
   waterproof cave/canyon checks and verified unchanged USGS mode.
