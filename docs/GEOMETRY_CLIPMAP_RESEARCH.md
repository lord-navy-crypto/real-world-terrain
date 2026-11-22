# RealScale Geometry Clipmap Research

**Branch:** `research/geometry-clipmap-renderer-20261122`  
**Base:** PR #163 head `86909e0c36d7cb18203c9436a98d74e7ebf7aeef`  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.x / Java 21  
**Primary test machine:** Apple M2, 16 GB unified memory, macOS OpenGL 4.1

## Decision

Prototype a RealScale-owned Geometry Clipmap renderer instead of requiring distant terrain to be generated as complete Minecraft chunks and then converted to LOD.

The first renderer is a **surface-heightfield renderer**, not a full replacement for Minecraft's 3D world renderer. It will coexist with the vanilla chunk renderer and can be tested independently of Distant Horizons. DH remains an optional fallback/compatibility layer until the prototype proves visual and performance parity.

## Why this is a good fit for RealScale

The active Natural Automate density graph already exposes a continuous, seed-dependent surface-height signal. Its top-level `realscale:natural_automate/height_signal` uses the composite `TerrainArchetypeDensity` family `natural_automate`, which evaluates all six fitted morphology kernels (Alpine, Glacial, Canyon, Volcanic, Karst, Dune) with shared continentalness, region, temperature, and humidity inputs.

This means the distant terrain can be sampled at sparse grid points directly from the same world-generation density graph. It does **not** need to generate 16×16×world-height block chunks, place blocks, build chunk meshes, or create block entities just to draw a far mountain silhouette.

The current `terrain_density.json` adds a Y-clamped gradient:
- `from_y = -64`, `to_y = 2032`
- `from_value = 0.9921875`, `to_value = -15.3828125`

The gradient slope is exactly `-1/128`. Its zero crossing therefore gives the current Natural Automate surface mapping:

```
surfaceY = 63 + 128 * height_signal
```

The configured sea level is also 63. A negative height signal therefore describes seabed below sea level; the far renderer must draw a separate sea-surface layer at Y=63 for ocean cells. This mapping is covered by unit tests and must be revisited if the density graph changes.

## First-pass clipmap configuration

The CPU layout prototype currently uses:

| Parameter | Initial value |
|---|---:|
| Vanilla near render distance | 10 chunks (configurable; test 8 and 10) |
| Grid cells per level | 128 × 128 |
| Grid spacing by level | 4, 8, 16, 32, 64, 128 blocks |
| Clipmap levels | 6 |
| Finest ring inner radius | 160 blocks |
| Finest ring outer half-extent | 256 blocks |
| Coarsest ring outer half-extent | 8,192 blocks |

Each level is a nested regular grid. The finest region is left to Minecraft's real chunks; coarser levels render only the surrounding ring. Camera positions are snapped independently to each level's cell spacing. The layout planner calculates newly exposed L-shaped sample strips so future updates can avoid rebuilding the entire height texture after small player movements.

The initial 128-cell grid is a starting point for measurement, not a final quality/performance claim.

## Sampling architecture

1. Resolve the active world's seed-specific Natural Automate density graph on the logical server.
2. Locate/evaluate the current `realscale:natural_automate/height_signal` field using block-coordinate function contexts. Do not create or request full chunks for this operation.
3. Sample only the vertices needed by each clipmap ring. The initial CPU prototype accepts a `SurfaceHeightSampler` callback and intentionally has no chunk dependency.
4. Convert height signal to world Y with the exact mapping above.
5. Produce ocean classification from the surface relative to sea level 63; send a separate water-surface mask/height.
6. Run tile sampling on a bounded, low-priority worker queue. Cache by dimension, world seed, tile origin, level, and generator/settings fingerprint.
7. Send compact tile updates to the client. Use server-authoritative samples for multiplayer so client and server cannot disagree about seed, datapack, or generator settings.

The exact 1.21.1 server-side access path to the active, seed-resolved density graph is an explicit integration spike. It must be proven with an in-world test before any renderer is enabled by default. If the server does not have RealScale or cannot supply the protocol, the feature stays unavailable and the existing renderer remains usable.

## GPU and platform strategy

The baseline must work on the user's Apple M2/macOS OpenGL 4.1 path.

Use:
- GLSL 4.10 vertex and fragment shaders.
- Static shared VBO/IBO geometry for regular clipmap patches.
- Vertex-shader height texture sampling.
- A normalized 16-bit height texture (R16) as the first memory-efficient format; retain an R32F debug option.
- `glTexSubImage2D` updates for newly exposed rows/columns.
- Ordinary OpenGL 4.1 buffer updates and a small bounded staging-buffer pool.
- Camera-relative coordinates to avoid precision loss at large Minecraft coordinates.

Do not require:
- OpenGL 4.3 compute shaders.
- SSBOs.
- Persistent buffer storage / persistent mapped buffers.
- Multi-draw-indirect.
- Vulkan, Metal, or a platform-specific renderer.

The GPU Gems 2 Geometry Clipmap implementation uses nested grids, vertex textures, toroidal addressing, and incremental L-shaped updates. Its GPU update path is a useful reference, but this prototype intentionally starts with CPU-generated sparse height samples because the worldgen density graph is Java-side and the M2 OpenGL 4.1 path must remain portable.

## Render integration

Use NeoForge 1.21.1's public `RenderLevelStageEvent` as the initial hook, with the opaque far terrain drawn before ordinary opaque chunk geometry (candidate stage: `AFTER_SKY`). The renderer must:
- preserve/restore every changed GL and RenderSystem state;
- use camera-relative positions and the active projection/model-view matrices;
- write depth so near vanilla blocks occlude the distant terrain;
- apply compatible fog at the far horizon;
- avoid drawing inside the vanilla near-chunk radius;
- avoid overlapping DH terrain during A/B tests;
- clean up GPU resources on world/dimension change and client shutdown.

The event hook and actual state-management details are an integration spike, not assumed proven by the layout tests.

## Scope boundaries for the first renderer

First version:
- continuous surface terrain and mountain silhouettes;
- ocean surface mask/plane at sea level;
- six Natural Automate morphology kernels through the existing height signal;
- no full chunk generation for distant terrain.

Not represented by a single heightfield:
- caves and underground galleries;
- overhangs, cliffs with multiple vertical surfaces, and arches;
- floating islands;
- trees, buildings, structures, entities, and block-level materials.

Those require separate impostor/HLOD/3D-volume layers or an optional existing renderer. Do not flatten them into a false surface mesh.

## Milestones

### M0 — Research and CPU proof (this branch's first commit)
- Layout planner for six snapped nested windows.
- Ring-only sparse height sampling API.
- Exact Natural Automate height-signal to Y mapping.
- 16-bit height quantization utility.
- JUnit tests for bounds, negative coordinates, L-shaped updates, sparse sample counts, ocean classification, and quantization.

### M1 — Worldgen sampling spike
- Resolve the seed-specific active Natural Automate density graph from the 1.21.1 server world.
- Evaluate `height_signal` directly at sparse coordinates without chunk creation.
- Compare sampled surface heights against generated chunk heightmaps across fixed seeds and land/ocean/terrain-family test locations.
- Measure samples/second and server tick impact.

### M2 — OpenGL 4.1 renderer spike
- Static ring patch mesh and R16/R32F vertex texture.
- Draw one clipmap ring with correct camera-relative transforms, fog, depth, and GL-state restoration.
- No menu/config integration yet; opt-in JVM/config flag only.

### M3 — Incremental clipmap updates and transitions
- Toroidal height texture addressing.
- L-shaped row/column updates only.
- Fine/coarse geomorph transition bands and seam tests.
- Bounded sampling/upload budgets and cancellation on teleport/world change.

### M4 — Natural Automate terrain + ocean presentation
- Surface colors/biome palette.
- Sea surface and coastline transition.
- Test Alpine, Glacial, Canyon, Volcanic, Karst, Dune, ocean, and mixed climate boundaries.

### M5 — Optional DH handoff and benchmark
- Test DH disabled, DH reduced-range, and clipmap-only configurations.
- Compare identical seeds, camera paths, render distance, shader settings, and scene.
- Report mean FPS, 1% lows, P50/P95/P99 frame intervals, CPU render-thread time, GPU time where supported, memory, sample queue, and upload queue.
- Do not claim a performance win until repeated paired runs show it.

## Acceptance gates

- No worldgen chunk is created merely to sample a far-height tile.
- Height samples match the active Natural Automate surface field within a defined tolerance.
- Ocean floor and sea-surface classification are correct.
- No visible cracks or popping at clipmap ring boundaries.
- No GL-state leakage or renderer crash on the supported OpenGL 4.1 client.
- No unbounded sampling, network, memory, or GPU-upload queues.
- Baseline Minecraft/DH behavior remains unchanged when the feature is disabled.
- Feature remains opt-in until client-side A/B results are repeatable.
