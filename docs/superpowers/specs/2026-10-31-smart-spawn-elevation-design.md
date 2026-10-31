# Smart Spawn Elevation — Design

## Intent
Choose a safer, lower-to-mid-elevation initial Overworld spawn for RealScale worlds. Avoid spawning on extreme mountain peaks, steep slopes, or liquid surfaces, which can make first entry unpleasant and can increase the amount of high-altitude terrain loaded around the player.

## Goals
- Use the NeoForge first-world-initialization spawn event only for RealScale Overworld noise generators.
- Derive a deterministic target elevation from the world seed and the active noise-settings preset.
- Give Natural, Alpine/Himalayan, Canyon, Glacial, Volcanic, Karst, Dune, and measured USGS settings distinct target bands, all biased toward low-to-mid terrain.
- Search a bounded set of deterministic candidate coordinates near world origin.
- Sample terrain with ChunkGenerator.getBaseHeight/getBaseColumn and the existing RandomState, avoiding forced chunk loads.
- Prefer solid, dry, clear, gently sloped ground near the target elevation.
- If no safe candidate is found, leave the event uncancelled so vanilla spawn selection remains the fallback.

## Non-goals
- Do not move existing saved-world spawn points or change respawn behavior.
- Do not generate or force-load chunks as part of spawn selection.
- Do not change terrain density, biome distribution, climate, or preset files.
- Do not guarantee a specific absolute elevation across different presets; the target is relative to each generator's sea level.

## Approach
Add a pure deterministic RealScaleSpawnMath helper for preset profiles, target-height selection, and candidate scoring. Add a small event adapter that samples a fixed, bounded set of coordinates using the generator's base terrain APIs. Register the event listener on NeoForge.EVENT_BUS. Use conservative candidate limits and vanilla fallback.

## Acceptance
- Pure math tests prove deterministic seed behavior, preset-specific target bands, and slope/elevation scoring.
- Source-level QA proves the listener is restricted to RealScale Overworld initialization, uses base-height sampling, and does not force chunk loads.
- Existing spawn safety and chunk-probe QA remains green.
- NeoForge compiles and the candidate JAR packages the new spawn classes.
