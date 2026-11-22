# RealScale Geometry Clipmap Research

**Branch:** `research/geometry-clipmap-renderer-20261122`  
**Base:** PR #163 head `86909e0c36d7cb18203c9436a98d74e7ebf7aeef`  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.x / Java 21  
**Primary test machine:** Apple M2, 16 GB unified memory, macOS OpenGL 4.1

## Product decision: visible surfaces only

RealScale's distant renderer is a **visible-surface renderer**, not a distant simulation or reconstruction of the complete Minecraft world.

The renderer only needs to reproduce surfaces that can contribute to the player's exterior view. It must not spend time or memory reconstructing hidden interiors, underground networks, or geometry that is occluded and cannot affect the current distant view.

The design has two complementary layers:

1. **Terrain Geometry Clipmap:** a continuous, multiresolution heightfield for the main terrain surface and ocean floor/surface.
2. **Visible-surface feature LOD:** simplified outer-shell meshes, impostors, or low-detail surface patches for exposed features that a 2D heightfield cannot represent, such as floating islands, cave entrances and externally visible cave surfaces, building exteriors, structures, tree crowns/canopies, and other above-ground silhouettes.

This is deliberately not a full 3D voxel renderer. A feature is represented only to the extent that its exterior surfaces can be seen from the outside. Hidden rooms, cave networks, underground galleries, internal block arrangements, unseen backsides, and occluded geometry are out of scope.

### Visibility rule

A surface is eligible only when it can affect the rendered exterior view:

- It lies within the relevant view/frustum and LOD range.
- It is not fully occluded by nearer terrain or another opaque surface.
- Its visible silhouette or surface contributes meaningfully at that distance.
- Its detail level is appropriate for the projected screen size.

Back-face culling is a normal mesh optimization, not a substitute for visibility testing: surfaces that become visible from another camera angle must still be represented by the feature's outer shell. Geometry hidden behind a mountain, behind a building wall, or inside an underground system does not need to be generated for the far renderer.

For cave-like features, render the visible opening and the exposed surfaces that can actually be seen through it; do not generate the cave's hidden continuation or underground network. For buildings, render the exterior envelope, roof, and visible facade only; no room interiors. For vegetation, render the visible canopy/crown and any meaningful exterior trunk silhouette, not every internal leaf/block. Floating islands need only their visible outer shell and silhouette, not internal geology.

## Decision

Prototype a RealScale-owned Geometry Clipmap renderer instead of requiring distant terrain to be generated as complete Minecraft chunks and then converted to LOD.

The renderer will coexist with the vanilla chunk renderer and can be tested independently of Distant Horizons. DH remains an optional fallback/compatibility layer until the prototype proves visual and performance parity.

## Why this is a good fit for RealScale

The active Natural Automate density graph already exposes a continuous, seed-dependent surface-height signal. Its top-level `realscale:natural_automate/height_signal` uses the composite `TerrainArchetypeDensity` family `natural_automate`, which evaluates all six fitted morphology kernels (Alpine, Glacial, Canyon, Volcanic, Karst, Dune) with shared continentalness, region, temperature, and humidity inputs.

This means the main distant terrain can be sampled at sparse grid points directly from the same world-generation density graph. It does **not** need to generate 16×16×world-height block chunks, place blocks, build chunk meshes, or create block entities just to draw a far mountain silhouette.

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

## Sampling and surface-feature architecture

1. Resolve the active world's seed-specific Natural Automate density graph on the logical server.
2. Locate/evaluate the current `realscale:natural_automate/height_signal` field using block-coordinate function contexts. Do not create or request full chunks for this operation.
3. Sample only the vertices needed by each terrain clipmap ring. The initial CPU prototype accepts a `SurfaceHeightSampler` callback and intentionally has no chunk dependency.
4. Convert height signal to world Y with the exact mapping above.
5. Produce ocean classification from the surface relative to sea level 63; send a separate water-surface mask/height.
6. For non-heightfield features, use a separate surface-feature LOD pipeline. Its input should be compact exterior geometry or a simplified proxy generated from feature metadata/visible block surfaces, never a full far-distance chunk reconstruction.
7. Apply frustum, distance, projected-size, and occlusion/horizon tests before uploading or drawing feature proxies.
8. Run tile/proxy preparation on bounded, low-priority worker queues. Cache by dimension, world seed, tile origin, LOD level, and generator/settings fingerprint.
9. Send compact tile/proxy updates to the client. Use server-authoritative world data in multiplayer so client and server cannot disagree about seed, datapack, or generator settings.

The exact 1.21.1 server-side access path to the active, seed-resolved density graph and the source of exterior feature proxies are explicit integration spikes. They must be proven with in-world tests before the renderer is enabled by default. If the server does not have RealScale or cannot supply the required protocol/data, the feature stays unavailable and the existing renderer remains usable.

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
- Separate compact vertex/proxy buffers or impostor atlases for visible-surface features.

Do not require:
- OpenGL 4.3 compute shaders.
- SSBOs.
- Persistent buffer storage / persistent mapped buffers.
- Multi-draw-indirect.
- Vulkan, Metal, or a platform-specific renderer.

The Geometry Clipmap handles the broad continuous terrain. It is not forced to represent overhangs, floating islands, buildings, or vegetation as fake heightfield data; those belong to the visible-surface feature layer. The GPU Gems 2 Geometry Clipmap implementation uses nested grids, vertex textures, toroidal addressing, and incremental L-shaped updates. Its GPU update path is a useful reference, but this prototype intentionally starts with CPU-generated sparse height samples because the worldgen density graph is Java-side and the M2 OpenGL 4.1 path must remain portable.

## Render integration

Use NeoForge 1.21.1's public `RenderLevelStageEvent` as the initial hook, with the opaque far terrain drawn before ordinary opaque chunk geometry (candidate stage: `AFTER_SKY`). The renderer must:
- preserve/restore every changed GL and RenderSystem state;
- use camera-relative positions and the active projection/model-view matrices;
- write depth so near vanilla blocks occlude distant terrain and feature proxies;
- apply compatible fog at the far horizon;
- avoid drawing inside the vanilla near-chunk radius;
- avoid overlapping DH terrain during A/B tests;
- perform visibility/occlusion filtering for exterior feature proxies;
- clean up GPU resources on world/dimension change and client shutdown.

The event hook and actual state-management details are an integration spike, not assumed proven by the layout tests.

## Explicit non-goals

The distant renderer will **not** reconstruct or render:
- underground cave networks, cave interiors, deep galleries, or hidden underground passages;
- underground ore veins, aquifers, or internal geology;
- rooms, building interiors, hidden floors, or internal block layouts;
- geometry fully hidden behind nearer terrain or opaque objects;
- complete far-distance Minecraft chunks, block entities, or entity simulation;
- backfaces and internal surfaces that cannot contribute to the exterior view.

These are intentional exclusions, not missing features.

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
- Identify a compact source representation for exterior feature proxies without requiring complete far chunks.

### M2 — OpenGL 4.1 terrain renderer spike
- Static ring patch mesh and R16/R32F vertex texture.
- Draw one terrain clipmap ring with correct camera-relative transforms, fog, depth, and GL-state restoration.
- No menu/config integration yet; opt-in JVM/config flag only.

### M3 — Incremental clipmap updates and transitions
- Toroidal height texture addressing.
- L-shaped row/column updates only.
- Fine/coarse geomorph transition bands and seam tests.
- Bounded sampling/upload budgets and cancellation on teleport/world change.

### M4 — Visible-surface feature LOD
- Add exterior-only proxies for floating islands, cave mouths/exposed cave surfaces, building/structure exteriors, and tree crowns/canopies.
- Use distance-dependent mesh simplification or impostors.
- Apply frustum, projected-size, and occlusion/horizon filtering.
- Never generate hidden interiors or underground continuations.

### M5 — Natural Automate presentation and benchmark
- Surface colors/biome palette.
- Sea surface and coastline transition.
- Test Alpine, Glacial, Canyon, Volcanic, Karst, Dune, ocean, and mixed climate boundaries.
- Test exposed feature silhouettes from multiple camera angles and elevations.
- Test DH disabled, DH reduced-range, and clipmap-only configurations.
- Compare identical seeds, camera paths, render distance, shader settings, and scene.
- Report mean FPS, 1% lows, P50/P95/P99 frame intervals, CPU render-thread time, GPU time where supported, memory, sample queue, and upload queue.
- Do not claim a performance win until repeated paired runs show it.

## Acceptance gates

- No worldgen chunk is created merely to sample a far-height tile.
- Height samples match the active Natural Automate surface field within a defined tolerance.
- Ocean floor and sea-surface classification are correct.
- Exposed feature silhouettes are represented without generating hidden interiors or underground continuations.
- No visible cracks or popping at clipmap ring boundaries.
- No visible LOD seams or proxy popping across feature/terrain boundaries.
- No GL-state leakage or renderer crash on the supported OpenGL 4.1 client.
- No unbounded sampling, network, memory, or GPU-upload queues.
- Baseline Minecraft/DH behavior remains unchanged when the feature is disabled.
- Feature remains opt-in until client-side A/B results are repeatable.


## 2026-11-22 visual quality and boundary safety iteration

- Camera-safe ring hole: the unsampled area stays inside the nearby vanilla
  chunk field even when the shared snap centre trails the moving camera.
  Very short view distances use a finer shared snap instead of a blank gap.
- GPU-only seam skirts: an added static vertex layer covers inner/outer
  boundaries; it reuses existing height texels, with no chunk work.
- Procedural terrain surface materials: three periodic world-aligned noise
  frequencies (including distant attenuation of microdetail), height/slope-
  dependent grass, dry ground, rock, beach, snow and ocean colours.
- Directional diffuse light using fragment derivatives of the rendered mesh
  replaces four extra vertex texture neighbour reads.
- Exterior impostors: three crossed planes, lobed tree silhouettes and
  simple stylized building facade variation. These are not real full 3D
  buildings, nor Minecraft block textures.

The experimental renderer still requires the opt-in JVM flag
\`-Drealscale.experimental.geometryclipmap=true\`.
Shader compilation under OpenGL 4.1, frame pacing, seam visibility and actual
client screenshots have not been established by dedicated server tests.
Do not claim parity or superiority to Distant Horizons until those are
measured in-game.

## 2026-11-22: actual multiplayer packet delivery and atomic snapshots

NeoForge 1.21.1 requires clientbound payload *codecs* to be registered on
both physical sides via the common mod-bus registration event. The original
Dist.CLIENT registration was not sufficient for a dedicated server encoder.
The common GeometryClipmapNetwork now owns all three payload registrations;
client-only delivery classes are reached only after a client-side guard.
The experimental network protocol version is now `2` (both sides must use
the same testing JAR).

Six height packets are staged and validated as one snapped-centre snapshot
before any new terrain texture is published to the render loop. A previous
complete snapshot remains active while new tiles arrive, and client rendering
is suppressed after a far teleport until a nearby snapshot is ready.
Visible-feature proxy packets carry the matching centre to prevent
old trees/structures from being drawn with new terrain. No 3D voxel chunks
are needed for any of these changes.

Real-world networking still requires an in-game connected-client test:
a dedicated server boot test alone cannot validate an S2C request/response.


## 2026-11-22 OpenGL texture-upload and state-isolation hardening

* R16 height rows contain 129 unsigned shorts (258 bytes), which are NOT
  aligned to the OpenGL default `GL_UNPACK_ALIGNMENT=4`. Use unpack alignment
  one while uploading and restore the previous state.
* The render transaction now captures GL state before shader/VAO
  initialization, before height texture uploads and before drawing; this avoids
  leaking temporary bindings into Minecraft's render pipeline.
* The height upload buffer is reused on the render thread instead of allocating
  six fresh direct native buffers for each new centre.
* GLSL uniform locations are queried when the shader program links rather
  than on each frame and each ring.
* These are correctness/overhead improvements. GPU frame times, Mac OpenGL
  client compatibility and any visible improvement still require measurement.
