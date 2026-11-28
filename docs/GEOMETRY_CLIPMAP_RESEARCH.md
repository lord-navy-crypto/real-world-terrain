# RealScale Geometry Clipmap Research

**Branch:** `integration/geometry-clipmap-runtime-toggle-20261124`  
**Base:** `main` (initial integration commit `582c74b940261c2fd7414bb19816b5d6db5bf588`)  
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
| Grid cells per level | 256 × 256 |
| Grid spacing by level | 4, 8, 16, 32, 64, 128 blocks |
| Clipmap levels | 6 |
| Camera-safe finest ring inner radius | 96 blocks (10-chunk near view) |
| Finest ring outer half-extent | 512 blocks |
| Coarsest ring outer half-extent | 16,384 blocks |

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
- The in-game pause-menu switch controls automatic rendering; the persistent client preference is stored in config/realscale-geometry-clipmap.properties.

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
- Iris-native terrain surfaces use a single neutral white atlas sample
  only to satisfy the active terrain shader's sampler requirement; all visible
  terrain materials are supplied by procedural vertex colors. Grass, dry
  ground, rock, sand and snow never sample block/item artwork. The block atlas
  remains available only to separate feature proxies such as trees and buildings.
- Exterior impostors: three crossed planes, lobed tree silhouettes and
  simple stylized building facade variation. These are not real full 3D
  buildings, nor Minecraft block textures.

The renderer is disabled by default and can be switched ON or OFF directly from the Minecraft pause menu using the `Geometry Clipmap: ON/OFF` button. A new installation starts with `enabled=false`; an explicitly saved preference is preserved in `config/realscale-geometry-clipmap.properties`. OFF stops new client requests, clears active snapshots and releases client render resources; ON requests a fresh six-level snapshot without restarting the game. Shader compilation under OpenGL 4.1, frame pacing, seam visibility and actual client screenshots still require dedicated client-side acceptance. Do not claim parity or superiority to Distant Horizons until those are measured in-game.

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

## 2026-11-22 coarser-height ring geomorph and shallow coast shading

Fine LOD surface vertices morph across their outer eight grid cells
toward the even-indexed 2x parent lattice, using the same compact R16
height map. This preserves full detail in the interior while matching
the coarser grid's boundary curve; down-facing seam skirts remain a
backup against sampling discontinuities. The outermost level does not morph.

A pure CPU reference with JUnit parity checks tests border interpolation
and weight falloff. The fragment shader adds a subtle shallow-ocean
coastline colour. Client GPU seams, FPS and macOS shader behavior still
require in-game evidence.


## 2026-11-22 view-scaled fog and exterior near-region suppression

A pure Java, JUnit-tested \`ClipmapFogRange\` now derives the fog fade
band from the actual outermost half-extent of the six-level clipmap.
The previous fixed 6500–19000-block interval could leave hard square
horizon boundaries at small render distances, while excessive fog at
large distances hid generated terrain. Terrain and exterior proxies
share the SAME fog range, with the outermost ring fading by 98% of
its radius. Tree and building proxies are also suppressed within the
vanilla nearby chunk radius, reducing duplicate near-field silhouettes.
Visual verification is still needed to characterize remaining artifacts.


## 2026-11-22 asynchronous session safety and render-thread allocation reduction

* Each server clipmap session now carries a world-generation epoch. Worker
  completions and failure callbacks must match that epoch before mutating the
  session, preventing stale work from an earlier dimension/seed/settings
  generation from clearing or replacing a newer request.
* Completion resolves the current \`ServerPlayer\` by UUID, so respawn/player
  object replacement no longer strands a session in \`inFlight\`. It also
  recomputes the current snapped layout and discards a snapshot if the player
  crossed a snap boundary while sampling.
* Queue rejection and transient sample failures now defer retry for 20 server
  ticks, avoiding per-tick queue hammering and repeated log spam.
* Proxy rendering reuses a bounded 4096-entry visibility list and direct
  instance/matrix buffers. Its shader uniform locations are cached at link
  time. Terrain matrix uploads also reuse a render-thread scratch buffer.
* These changes reduce avoidable Java/native allocation and harden asynchronous
  lifecycle handling. Performance impact still needs in-game profiling.


## 2026-11-22 render-loop allocation and proxy horizon culling

* Both terrain and proxy renderers now reuse their JOML model-view and
  view-projection matrices instead of allocating two matrix objects every
  frame. Direct matrix upload buffers were already reused and remain so.
* Proxy visibility now rejects objects wholly beyond the configured fog horizon
  (with a 64-block silhouette allowance) before allocating an AABB or issuing
  an instanced draw. This avoids processing proxies that the fragment shader
  would render entirely as fog.
* Proxy instance and matrix buffers plus the visibility scratch list remain
  bounded by the 4096-proxy network contract.


## 2026-11-22 cached proxy bounds and low-copy height transfer

* \`VisibleSurfaceProxy\` now computes its immutable world AABB once when
  generated or decoded. The per-frame frustum loop no longer allocates one
  AABB for every proxy. Its accessors and record-style equality/hash semantics
  are preserved.
* \`ClipmapHeightTilePayload.copyHeightsTo(ShortBuffer)\` writes the payload's
  private immutable samples directly into the renderer's reusable upload
  buffer. Network encoding also reads the owned array internally rather than
  cloning it through the public defensive accessor. Public \`heights()\`
  remains defensive.
* Added unit tests for cached bounds, value semantics, defensive payload
  ownership and direct height-buffer copying.


## 2026-11-22 complete 2D pixel-unpack state isolation

The R16 uploader now normalizes and restores not only unpack alignment but
also row length, skipped rows/pixels and byte swapping. This prevents another
renderer/mod from leaving non-default pixel-store values that would corrupt
the clipmap texture rows even when alignment is set correctly.


## 2026-11-22 cancellation of obsolete server sampling work

The session generation is now volatile and checked between each LOD ring and
before proxy generation. Replacing a session because the player changes
render distance, resetting its world, or cleaning up a disconnected player
advances the generation. The single bounded sampler can therefore abandon
obsolete work instead of spending the remaining sampling budget on a player
or world that no longer needs that snapshot.


## 2026-11-22 optional profiling telemetry

Enable timing telemetry with `-Drealscale.experimental.geometryclipmap.debug=true`. This flag only controls diagnostic output; the pause-menu switch controls whether the renderer runs.\n\nThe server reports accepted snapshot centre, newly sampled vertices, proxy
count, worker queue wait and sample time. The client reports a five-second
window of average frame rate while the clipmap is active, average/maximum CPU
render-submission time, uploaded height bytes and maximum proxy-set size.
The client metric intentionally does not call \`glFinish\`: it measures CPU
submission overhead, not GPU execution time, and does not force a GPU stall.


## 2026-11-22 CI follow-up

* Corrected the proxy-bounds unit test to use Minecraft 1.21.1's public AABB
  coordinate fields rather than nonexistent accessor methods.
* Added a group-scoped Google-hosted Maven Central mirror for Gson in plugin
  management, ahead of Maven Central, to reduce transient HTTP 429 failures
  when multiple independent CI workflows resolve the same plugin dependency.


The telemetry source-contract test checks for an actual \`glFinish(...)\` call,
not for the word in the explanatory comment. This keeps the guard focused on
blocking a synchronous GPU stall rather than blocking its documentation.


## 2026-11-23 client request-generation fencing and immediate session retirement

The experimental payload protocol is now version 3. Every client request receives
a monotonically increasing positive request ID. The server echoes that ID in
each of the six height tiles and the matching visible-feature proxy payload.
The client rejects packets whose ID does not match its active request, and the
snapshot assembler includes the ID in its immutable snapshot key. This prevents
late packets from a prior dimension or render-distance request from completing
or replacing a newer client snapshot.

A newer request invalidates the previous server sampling epoch even when the
render distance is unchanged. The previous grids may be retained as a sampling
reuse candidate, but no previous-generation result can be published. Unsupported
generators/settings and disconnected players now advance the epoch before
their sessions are removed; disconnected sessions are retired on the next
server tick rather than waiting for the former 200-tick cleanup interval.

The terrain renderer now also captures, unbinds, and restores
GL_PIXEL_UNPACK_BUFFER_BINDING around its texture transaction. This is
required because a nonzero unpack PBO changes a client-memory upload pointer
into a byte offset and can corrupt or reject the R16 texture upload.

These changes harden request identity, task retirement, and OpenGL state
isolation. They do not by themselves establish client-side frame-time gains;
the version-3 protocol requires both client and server to use the same build.


## 2026-11-23 integrated-server shutdown cleanup

The server now handles NeoForge's ServerStoppingEvent. It advances every active
session epoch, clears the session map, and clears queued sampler work. This is
important because the sampler executor and session map are static and can
survive an integrated-server world transition in the same client process.
It prevents a previous world's density graph or player UUID session from being
reused after the next world starts.


## 2026-11-23 toroidal GPU height-texture updates

The client no longer uploads all six 129x129 R16 height textures whenever a
new snapped snapshot arrives. Each LOD now keeps a toroidal texture window:
the shader maps logical grid coordinates through a per-level texture offset,
and the texture uses repeat addressing. When the new tile origin moves by an
integer number of sample cells, the client advances that offset and uploads
only the newly exposed edge strips with bounded rectangular
`glTexSubImage2D` calls. Diagonal shifts upload the union of the two strips
without uploading their shared corner twice.

A pure `ClipmapTextureUpdatePlan` computes the offset and dirty rectangles.
It falls back to one full upload when metadata is incompatible, the origin
shift is not sample-aligned, or at least 70% of the texture would be dirty.
The packet exposes a bounded region-copy method so the renderer can pack each
rectangle into its existing reusable upload buffer without exposing or cloning
the backing height array.

This reduces GPU texture-upload volume for normal snapped recentering. It does
**not yet reduce network payload size**: the server still sends complete
height tiles, and the client still receives and validates the full snapshot.
It also does not claim GPU-time improvement; the existing CPU submission
telemetry remains distinct from GPU execution time.


## 2026-11-23 snapshot-cached exterior proxy instances

The proxy renderer now uploads compact instance data only when the immutable
proxy snapshot or its snapped clipmap centre changes. Instance positions are
stored relative to that centre, and the per-frame camera displacement is sent
as one uniform; this avoids rebuilding and streaming every proxy's
camera-relative position on every frame.

Near-field and fully fogged proxies are rejected in the vertex shader, while
the OpenGL clipper handles view-frustum clipping. This removes the per-frame
Java list filtering and the per-proxy AABB objects. The renderer still draws
the existing three crossed billboard planes and keeps the existing exterior
silhouette materials. This is a CPU submission/allocation optimization; no
GPU-time or visual-quality gain is claimed without an in-game client capture.


## 2026-11-23 recentering correction: formerly unsampled inner holes

The initial toroidal planner copied overlapping texels without considering the
previous nested ring's **strictly interior unsampled hole**, which encodes as
minimum-height values. A camera recenter could expose this old hole and show a
false trench. The updated plan now adds up to four disjoint rectangles covering
the difference between the projected old inner hole and the new inner hole,
alongside ordinary newly exposed outer strips. The exact inner-hole radius is
taken from the corresponding ClipmapRingMesh topology, not guessed.

JUnit coverage checks all cardinal and diagonal snapped movements, including
negative displacements and wrapped offsets, to ensure no newly visible vertex
can reuse an unsampled old-hole texel. The renderer also avoids per-frame plan,
record, and texture-bind work for immutable unchanged packet objects.
This establishes CPU-side correctness tests only, not actual GPU screenshot
or frame-time measurements.


## 2026-11-23 final client hardening: packet order, cancellation, and first-frame telemetry

The Geometry Clipmap client now keeps a bounded, one-snapshot staging area for
exterior proxies. It no longer assumes the proxy packet must arrive after the
sixth height tile: proxies are attached only when their request ID, dimension
and snapped centre match a fully assembled height snapshot. This avoids a
missing-canopy frame when packets arrive out of order and prevents feature
impostors leaking across dimension/teleport boundaries.

The server passes its active sampling epoch into the height grid sampler.
Every 32 grid vertices the worker checks whether the request is still
current and throws a cancellation exception if it was invalidated. The server
already ignores failures from superseded epochs, so normal travel/world
transitions no longer need to finish an obsolete full ring before work for
another player can proceed.

Debug mode now reports `firstSnapshotReady` and
`firstEligibleRenderSubmission` milliseconds relative to the client's
request. These measure client-observed state changes and CPU render submission;
they do not prove the first displayed GPU frame or GPU execution duration.
The default remains opt-in/off. The network protocol remains version 3.


## 2026-11-23 test-candidate usability: opt-in by default + visible status

**Only in this Draft PR's experimental TEST JAR**, Geometry Clipmap is now
disabled by default. A new installation starts with the renderer OFF; the
player explicitly enables it using the pause-menu button. The user installs
one complete RealScale JAR and does not need to edit launcher JVM parameters.
An explicitly saved `enabled=true` preference remains enabled across launches.
The feature remains restricted on the server to Natural Automate's supported
noise settings and the matching experimental client/server protocol;
installing the JAR into a vanilla-only world does not cause distant sampling.

The client writes distinct Minecraft **chat notices**, as well as diagnostic
log lines, on state transitions after the player enables the feature:
`ON / WAITING` after the request; `SNAPSHOT READY` after all six height
tiles arrive; and `DRAW SUBMITTED` after the renderer receives a valid frame.
If no snapshot arrives within about 15 seconds, it prints `STILL WAITING` once,
explaining that Natural Automate and the server must be checked. These are
intentionally not claims that a GPU image was displayed. They are also not
permanent HUD lights or commands; chat messages remain available in the
in-game log.

Use the pause-menu `Geometry Clipmap: ON/OFF` button to disable or re-enable the renderer at runtime. The choice persists across game restarts. The optional `-Drealscale.experimental.geometryclipmap.debug=false` argument only silences client timing output; it does not change the ON/OFF state.


## 2026-11-23 critical horizon visibility fix: extend the projection far plane

A source audit found the main reason the experimental renderer could report
successful sampling and draw submission without producing a genuine distant
horizon. `GeometryClipmapRenderer.draw` reused the projection matrix supplied
by Minecraft's normal camera. In vanilla, the perspective far plane is tied
to the ordinary render distance (normally four times that distance in blocks).
At 10 chunks this is about 640 blocks, while this clipmap's fogged horizon
ends at about 16,056 blocks. Thus the outer LOD rings were outside clip space
and were clipped before rasterization. The visible mesh could never reach its
advertised range.

The renderer now copies the event projection and changes only the perspective
depth coefficients, preserving the original FOV, aspect ratio, and near plane.
It extends the far plane to at least 1.5 times the configured clipmap fog-end
distance. At the standard 10-chunk/8-block finest spacing layout, that is
approximately 24,084 blocks, enough to include the full outer square (including
its corners) before the clipmap fog fades it into the sky. The shader fog still
ends before the hard mesh boundary.

Debug output now records both the vanilla and extended projection far planes.
JUnit tests verify extraction of near/far values, extension from a 640-block
vanilla projection, preservation of FOV/aspect coefficients, and that an
already-longer far plane is never shortened.

This fixes a confirmed mathematical clipping defect in the source. A real
client screenshot and measured visible horizon are still required to certify
the final result on the user's GPU/driver and with optional shaders/DH.


## 2026-11-23 waiting-state and delivery-recovery upgrade

The previous client emitted `STILL WAITING` after 15 seconds but had no
server acknowledgement, no partial-tile visibility, no heartbeat, and no
recovery if a completed server snapshot was not fully assembled client-side.
The server also ignored duplicate request IDs, so the client had no safe way
to ask for a resend without forcing a full resample.

The new protocol adds a bounded `clipmap_status` server-to-client payload.
It reports request acknowledgement, unsupported generator/settings, queue
state, sampling progress (LOD 1/6 through 6/6), snapshot sent, busy, failure,
and a stalled-worker watchdog. The client replaces the ambiguous
`STILL WAITING` message with actionable states and counts the height-tile
levels received so far.

While the first complete snapshot is absent, the client repeats the same
request ID every eight seconds. Same-ID requests are idempotent: the server
reports in-flight progress or resends the cached six height tiles and proxy
snapshot, avoiding duplicate terrain sampling. A 90-second no-progress
watchdog invalidates a stalled sampling epoch and allows a bounded retry.
The snapshot assembler exposes pending-level count and bitmask for QA.

The client now labels the first renderer event as GPU draw submission, not
proof of visible pixels. A real in-game horizon screenshot remains the
visual acceptance criterion.


## 2026-11-23 live sampling progress and reconnect hardening

The waiting protocol now reports progress inside each individual LOD ring,
not only after an entire ring completes. The sparse grid sampler emits
monotonic scan progress; the server sends bounded 25/50/75/100% milestones
and refreshes the sampler watchdog on each scan callback. A long first ring
therefore no longer looks like an unexplained zero-of-six wait.

The server also binds each per-player clipmap session to the active network
connection identity. If a client restarts and its local request counter begins
again at a low value while an old UUID session still exists, the new connection
replaces that stale session instead of having its request silently discarded.

The test-package instructions and JAR class-integrity check were updated for
the status packet and the new actionable chat messages. The experimental
artifact is still not visually certified until the user verifies distant
terrain in a real Minecraft client.


### Progress presentation

Frequent LOD percentage updates and partial tile counts are shown in the
Minecraft action bar rather than chat. Chat is reserved for major state
transitions and actionable errors, preventing a slow first sample from
flooding the player's chat history.


## Iris / shader-pack compatibility audit (2026-11-23)

The standalone OpenGL 4.1 renderer remains the no-shader fallback.
For Iris 1.21.1 with an active shader pack, the native path now uses a
client-only mixin at the end of `LevelRenderer.renderSectionLayer`, immediately
before the active `ShaderInstance.clear()` call. This point is deliberately
inside the vanilla/Iris terrain phase: Iris has selected the current G-buffer
program and the current RenderType state is still active.

The native path:
- uses the vanilla `BLOCK` vertex format and GPU-resident `VertexBuffer` meshes;
- draws opaque terrain/buildings/islands with `RenderType.solid()`;
- draws tree canopy cutouts with `RenderType.cutout()`;
- draws ocean surface with `RenderType.translucent()` in the main pass;
- writes the same six-level ring geometry, edge morphing and skirts from the
  immutable height-tile snapshot, rather than generating distant chunks;
- sets and restores the active shader's `CHUNK_OFFSET` so the cached mesh is
  centred on the clipmap snapshot but rendered relative to the current main or
  Iris shadow camera;
- extends both the active vanilla shader projection and Iris's captured
  `gbufferProjection` to the clipmap fog horizon, then restores both after each
  draw; Iris shadow projections retain their own shadow-distance and
  orthographic settings;
- participates in Iris solid/cutout shadow terrain passes, while translucent
  water is intentionally omitted from shadow maps;
- does not create a private shader program, bind a private framebuffer, or
  bypass the active Iris terrain shader.

Iris is detected reflectively through IrisApi v0. The native path also
uses a narrow, version-pinned reflective bridge to
`CapturedRenderingState.setGbufferProjection`; without that projection bridge,
the native path fails closed rather than submitting geometry with inconsistent
depth reconstruction. No compile-time Iris dependency is added. When no shader
pack is active, the existing standalone OpenGL renderer remains in use. If the
Iris API or projection bridge cannot be queried, custom rendering fails closed.

This establishes the native submission architecture, not visual certification.
A real client run with Iris and a shader pack is still required to verify
G-buffer output, terrain visibility, water appearance, shadow-map participation,
LOD seams, proxy silhouettes, and frame-time impact. The client reports
`IRIS NATIVE TERRAIN DRAW SUBMITTED` only after a matching active snapshot is
uploaded and drawn; this is not proof that visible pixels were produced.


## Camera-matched fog color

The terrain and exterior-proxy shaders no longer use a permanently hard-coded
blue fog. Both now upload Minecraft's current `RenderSystem.getShaderFogColor()`
value, with finite/range validation and a conservative fallback. This keeps
the custom vanilla renderer's horizon fade aligned with the active camera's
biome fog, weather, time-of-day and dimension fog instead of showing a bright
blue band at night or in non-overworld environments.


## Shared far-plane projection for exterior proxies

The proxy renderer previously rebuilt its view-projection matrix from the
vanilla event projection, while the terrain renderer used the extended
clipmap projection. As a result, distant trees/buildings could be clipped at
the original Minecraft far plane even when the ground mesh remained visible.
The terrain draw now passes its already-extended projection matrix to the
proxy renderer, keeping both systems on the same horizon and fog range.


## Distance-dependent voxel-to-continuous terrain transition (2026-11-25)

The Iris-native terrain mesh now has a Minecraft-like stepped band before it
becomes a continuous far-field surface. The transition is selected from the
first ring whose outer edge reaches beyond the vanilla render radius plus the
shared clipmap-centre snap allowance. This makes the behavior work with the
64/128/256 grid range presets without applying voxel steps inside the protected
vanilla near field.

Within that selected ring:
- Each valid height-grid cell derives a representative height from its four
  corner samples and snaps that height to the nearest integer block level.
- The cell's top triangles blend toward one flat height, while adjacent cells
  receive vertical riser faces wherever their blended edge heights differ.
- Surface normals and procedural terrain colors blend toward flat, cell-level
  values together with the geometry.
- The voxel influence fades in outside the protected vanilla overlap and fades
  back to zero by the selected ring's outer boundary. Other LOD rings remain
  continuous and retain the existing parent-height morph.

This is a stepped heightfield, not yet a full one-block reconstruction outside
vanilla chunk render distance. Its horizontal cell footprint is the selected
ring's sample spacing (four blocks per cell in the usual ten-chunk/default-grid
case, and potentially larger in reduced-range presets). The vanilla chunk
renderer continues to provide exact block geometry in its normal view range.
The stepped transition is capped at eight horizontal blocks per cell; when the
first visible ring is coarser than that, the far terrain remains continuous.
A dedicated one-block near-detail strip would require additional high-resolution
surface sampling and a separate CPU, network, mesh-memory and frame-time budget;
it must be validated before replacing this conservative first transition.

The transition has pure-Java tests for protected-overlap behavior, adaptive ring
selection, fade endpoints, integer-height quantization and geometry/color blend
endpoints. The Iris client render smoke and full NeoForge build remain required
before the visual change can be considered accepted.

## LOD-aware material-noise filtering and mid-frequency aliasing (2026-11-28)

The Iris-native client evidence showed a regular diagonal/dot pattern on distant
terrain even though the existing high-frequency RMS gate passed. The working
diagnosis is that the world-anchored 256- and 512-block palette-noise octaves
were sampled too sparsely by the coarsest clipmap rings. At 128 blocks per
vertex, the 256-block octave has only two samples per noise cell and can alias
into a repeated grid-aligned pattern after perspective projection.

The Iris-native palette now receives the active ring's world-space sample
spacing. Each 256/512-block octave fades toward its neutral value when fewer
than six samples cover one noise cell, with a smooth transition beginning at
three samples per cell. The 2048-block macro field, heightfield, biome classes,
shoreline, slope and snowline logic remain intact. Fine rings retain the
existing world-anchored variation; coarse rings suppress detail they cannot
represent reliably. This changes only client-side material color sampling,
not world generation, height samples, geometry topology, camera movement or
LOD placement.

The client smoke adds a 2px-to-8px Gaussian band-pass measurement over the
fixed terrain viewport, alongside the existing absolute high-pass RMS,
normalized high-pass ratio, color count and same-location A/B contrast checks.
The new fixed-camera and returned-camera gates require mid-frequency RMS at or
below 0.00500 and contrast-normalized mid-frequency energy at or below 0.090.
These limits are intended to catch the previously missed 10-20px periodic
pattern without relaxing any existing gate.

The change is provisional until the latest Iris + Complementary client run
confirms that the mid-frequency metrics improve while terrain contrast,
high-frequency energy, six-level LOD movement, return-to-baseline stability,
and the existing world-generation regressions continue to pass. The feature
remains opt-in and the PR remains Draft pending that evidence.


## Iris per-cell shading pattern: atlas UV regression and block metadata audit (2026-11-28)

The first real Iris + Complementary run after LOD-aware palette filtering
measured fixed-camera mid-frequency RMS `0.00583` and normalized ratio
`0.09786`, against gates of `0.00500` and `0.09000`. Its high-frequency
RMS was `0.00627` (ratio `0.10515`). After moving to a new six-LOD centre,
mid-frequency RMS was `0.00467` (ratio `0.09351`) and high-frequency RMS
was `0.00533` (ratio `0.10677`). Thus movement/LOD refresh worked, but the
fixed-camera mid-frequency visual gate failed.

A follow-up diagnostic widened the neutral UV patch from `0.125 × 0.125`
sprite pixels to `0.75 × 0.75`. The second actual Iris + Complementary
screenshot disproved the tangent-conditioning hypothesis: fixed-camera
high-frequency RMS rose to `0.01910` (ratio `0.24361`) and mid-frequency RMS
rose to `0.01013` (ratio `0.12920`). The moved-region high-frequency RMS
was `0.01528` (ratio `0.22674`), and final mid-frequency RMS was `0.00827`
(ratio `0.12268`). The wider patch sampled adjacent atlas/PBR texels and is
rejected. Restore the compact 1/8-texel patch; do not relax any visual gate.

The next source-level lead is Iris's block metadata contract. Iris's own
`MixinBufferBuilder` fills `mc_Entity`, `mc_midTexCoord`, and tangent
attributes for its extended terrain format. Its `BlockSensitiveBufferBuilder`
interface expects renderers to call `beginBlock`/`endBlock`, and its
NeoForge block-render hook supplies the block-state ID from
`WorldRenderingSettings.INSTANCE.getBlockStateIds()`. RealScale's synthetic
clipmap terrain currently writes the Iris terrain vertex format but never
provides this block context, leaving `currentBlock` and `currentRenderType`
at their default `-1` values. That is a concrete integration gap, independent
of the UV-size hypothesis.

The new diagnostic candidate resolves actual Iris block-state IDs reflectively
(no compile-time Iris dependency) and writes a white-concrete solid block
context for terrain vertices and a water/fluid context for water vertices.
The compact neutral UV patch is retained. Each vertex receives a local
clipmap-relative mid-block anchor, avoiding large world-coordinate offsets.
If the Iris API or block-state map is unavailable, the bridge fails safely and
keeps the previous neutral fallback metadata.

This remains an experiment, not an accepted fix. The next actual Iris +
Complementary client run must report the resolved block IDs and demonstrate
lower fixed-camera mid-frequency energy while preserving high-frequency energy,
terrain contrast, six-level movement, return-to-baseline stability, and all
world-generation regressions. Keep the PR Draft until that evidence passes.


## Pause-menu OCR false negative in Iris client smoke (2026-11-28)

The first client run with the Iris block-metadata bridge compiled successfully,
but the render smoke stopped before the clipmap toggle or terrain capture. The
failure was not a renderer crash: the framebuffer screenshot visibly contained
the Minecraft "Game Menu" title and the RealScale controls, but the harness's
Tesseract `--psm 6` pass returned an empty string for the pixel font. Re-running
the exact captured title crop locally with `--psm 8` consistently returned
`Gane Nery`, a recognisable OCR misclassification of "Game Menu".

The harness now uses single-word PSM 8 for this fixed title crop and accepts
either the exact title or the specific observed alias `gane nery`. It still
requires the PauseScreen initialization marker, captures Minecraft's own
framebuffer screenshot, validates screenshot dimensions, and checks the
title crop. This is a narrow OCR compatibility adjustment, not a relaxation
of the actual rendering or texture-quality gates.

The next run must get past the pause-menu check and exercise the actual clipmap
toggle, initial screenshot, camera movement, six-LOD refresh, return movement,
and all visual metrics. The block-metadata change remains unvalidated until
that complete client render evidence exists.


## Runner-specific pixel-font OCR alias (2026-11-28)

The follow-up client run passed the world-ready and PauseScreen initialization
gates and captured a correct framebuffer: the "Game Menu" title and all
RealScale pause-menu buttons are visibly present. The remaining stop was again
only OCR: this GitHub runner's Tesseract PSM 8 output was `gone nery`, whereas
the same title crop on the local inspection environment returned `gane nery`.
The framebuffer screenshot itself is visually correct; no clipmap toggle or
terrain render capture has happened yet.

The fixed title-crop check now accepts the exact title or either observed
pixel-font OCR alias (`gane nery`, `gone nery`). It still requires the
PauseScreen initialization event, Minecraft's F2 framebuffer screenshot,
minimum screenshot dimensions, and the fixed title crop. No terrain-render
metric threshold has been relaxed.


## Bounded OCR matching for Minecraft's pixel-font title (2026-11-28)

The third client run again reached a real world and captured the PauseScreen
framebuffer correctly, but Tesseract returned `gane hen` for the visible
"Game Menu" title. Previous runner outputs were `gane nery` and `gone nery`.
This variability makes a list of exact OCR aliases brittle, while the
underlying screenshot and PauseScreen initialization event are valid.

The harness now uses a bounded Levenshtein comparison for the two words in the
fixed title crop: OCR token 1 must be within two edits of "game", and token 2
within three edits of "menu". Exact "game menu" still passes directly. This
does not accept arbitrary text elsewhere on screen: it applies only to the
small framebuffer crop at the known PauseScreen title position, after the
PauseScreen initialization marker and screenshot-dimension checks have passed.

The clipmap toggle and actual terrain capture still have not been reached, so
the Iris block-metadata change remains visually unvalidated. The next run must
pass this UI check and then complete the full movement/return and image-metric
gates.
