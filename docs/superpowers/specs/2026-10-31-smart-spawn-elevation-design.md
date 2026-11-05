# Smart Spawn Elevation — Design

## Intent
Choose a deterministic low-to-mid elevation initial Overworld spawn for RealScale worlds. Avoid extreme peaks, steep local slopes, liquid surfaces, and unsafe headroom without assuming that sea level is close to the land surface.

## Goals
- Use the NeoForge first-world-initialization spawn event only for RealScale Overworld noise generators.
- Derive a deterministic target percentile from the world seed and active noise-settings preset.
- Give Natural, Alpine/Himalayan, Canyon, Glacial, Volcanic, Karst, Dune, and measured USGS settings distinct low-to-mid percentile bands.
- Sample a bounded set of candidate locations across a preset-specific search radius.
- Derive the target Y from the sorted safe surface heights, so the algorithm adapts to RealScale's very different vertical scales.
- Prefer solid, dry, clear, gently sloped ground near the selected percentile.
- Use ChunkGenerator.getBaseHeight/getBaseColumn and the existing RandomState, avoiding forced chunk loads.
- If no safe candidate is found, leave the event uncancelled so vanilla spawn selection remains the fallback.

## Non-goals
- Do not move existing saved-world spawn points or change respawn behavior.
- Do not generate or force-load chunks as part of spawn selection.
- Do not change terrain density, biome distribution, climate, or preset files.
- Do not impose a fixed absolute Y band or assume sea level is the terrain median.

## Approach
Add a pure deterministic RealScaleSpawnMath helper for preset-specific percentile profiles, seed-derived percentile selection, and candidate scoring. Add a small event adapter that samples a fixed, bounded set of positions using the generator's base terrain APIs. Sort the safe sampled elevations and choose the target Y from the preset's lower-to-middle percentile band. Register the event listener on NeoForge.EVENT_BUS. Use vanilla fallback if no safe sample exists.

## Acceptance
- Pure math tests prove deterministic seed behavior, preset-specific quantile bands, and slope/elevation scoring.
- Source-level QA proves the listener is restricted to RealScale Overworld initialization, uses base-height/base-column sampling, and does not force chunk loads.
- A dedicated-server test proves the selected spawn is persisted in level.dat and matches the logged RealScale selection.
- Existing spawn safety and chunk-probe QA remains green.
- NeoForge compiles and the candidate JAR packages the new spawn classes.
