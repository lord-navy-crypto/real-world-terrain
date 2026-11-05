# RealScale 2,096-block underground Section performance audit

**Purpose:** diagnose whether tall underground columns hurt *client* rendering
once the world is generated. No features are removed. This is separate from
worldgen RCON `forceload` timeouts in PR #32/#34 and from distant-terrain
generation continuity.

## What is and is not rendered

- Overworld range `[-64, 2032)` occupies **131** addressable 16-block
  vertical Sections per X/Z chunk column. A normal 384-block range has 24.
- Those are **not** 131 GPU draw calls or proof every buried Section is
  meshed and drawn. Solid rock can have few or no exposed faces; visibility
  depends on camera/frustum/occlusion and potentially connected caves.
- Mature client renderers already implement section occlusion traversal.
  Consult upstream Sodium `OcclusionCuller` and `RenderSectionManager`.
  Never hard-code a Y cutoff: a ravine/cave opening may expose the floor
  hundreds of blocks below, and a mountain cave may open upward.
- The upstream Sodium developers warn that little visible terrain in a
  section does **not** imply visibility cannot travel through that section:
  https://github.com/CaffeineMC/sodium/issues/3904

## A: Saved-world palette inventory (safe & read-only)

Use a **copy** of a pre-generated disposable world's region folder:

```sh
python3 qa/neoforge/vertical_section_budget.py \
  "/path/to/RealScale-test-world/region" \
  --min-y -64 --height 2096 --max-chunks 128 \
  --output build/vertical-section-inventory.json
```

The report counts explicitly persisted Section block palettes by Y: uniform
air; uniform *known rock* candidates; mixed palettes containing air; other
palettes; absent slots; invalid/duplicate Y. A uniform rock Section is **not**
safe-to-cull evidence: its outer surface might border an open cliff/cave.
Mixed-with-air palette also does not prove air connectivity or visibility.
No renderer code is modified, no world files are written.

## B: Client-controlled A/B

Use Minecraft **1.21.1**, NeoForge **21.1.x**, and matching JARs. Work on
fresh, fully pre-generated test worlds with identical seed and world preset;
allow camera-area chunks to finish loading before stationary measurements.

Suggested comparison order:
1. RealScale only, no shader.
2. Same world/camera/graphics settings plus **one** Sodium NeoForge version.
   Never combine Sodium and Embeddium.
3. Optional additional **MoreCulling** (face/block/model optimization; not
   chunk-section occlusion replacement).
4. Optional **Entity Culling** (entities/block entities rather than underground
   terrain).
5. Distant Horizons only in a **separate** compatibility sweep.

Do not install them as hard RealScale dependencies. Confirm exact loader
version and mod-pair compatibility. Alternate A/B order, repeat at least
three stationary samples per scene. Keep render distance, simulation
distance, clouds, GPU driver, resolution, JVM memory and screenshots identical.

Scenes:
- normal surface near lowland, looking out horizontally and down;
- high mountain top and face, looking **down a deep ravine**;
- fully enclosed cave, looking at a long winding tunnel;
- kilometer-scale cave chamber with a skylight/open exit;
- ocean surface with sky island structures;
- a **previously generated** far region beyond X/Z 8,192.

Collect: FPS and frame-time *p50/p95* (not just peak FPS), count of
visible vs retained/queued render Sections when available in current
renderer diagnostics, mesh rebuild and upload queues, CPU/GPU utilization,
heap/GC, server mspt, and whether generation/loading was active. Distinguish
GPU-bound rendering from server worldgen stalls. Separate shader-on
comparisons from first no-shader results.

Visual acceptance: no floor/roof holes, cave popping, disappearing ravine
walls, distant terrain seams, transparent fluids/glass leakage, missing
sky-island geometry, or stale visibility after mining/placing blocks.

## Implemented proof-of-concept: opt-in client environment telemetry

This experimental branch now contains:
- `AdaptiveVisibilityBudget.java` — **implemented** three-state, fail-open
  hysteresis classifier and advisory, starvation-free work-priority values.
  This class has no Minecraft imports and is tested independently.
- `RealScaleAdaptiveVisibilityClient.java` — real NeoForge 1.21.1
  `ClientTickEvent.Post` subscriber restricted to the physical client:
  one bounded sample per **20 ticks**, maximum five local loaded-chunk
  sky probes, one saved heightmap sample. Unloaded data, rapid camera jumps,
  other dimensions, underwater viewpoints and ambiguous cave mouths fall
  back to `TRANSITION`.
- `AdaptiveVisibilityBudgetSmoke.java` — mode, transition/hysteresis,
  urgent-work and positive/negative altitude independence tests,
  checked in GitHub CI.

**It is disabled by default.** To enable diagnostic tracking in a *client*
JVM, include:

```text
-Drealscale.client.visibility.profile=true
```

Look for `[RealScale adaptive visibility]` client logs (transitions
and periodic summaries). They show classification evidence, **not** FPS
improvement, GPU culling or render-Section queue timing.

**Honesty boundary:** The priority numbers are an ADVISORY policy.
No vanilla/Sodium/Embeddium render queue is currently modified because a
stable compatible priority injection API for the pinned NeoForge/Sodium
combination has not been established. No future mod should pretend these
numbers alone speed up rendering. There are **zero extra sky queries**
when the flag is off, and the client-only subscriber is not loaded on
dedicated servers. No render distance, server tickets or chunk generation
changes. Actual priority-queue integration remains blocked on supported
API discovery and a reproducible client A/B proving an uncovered hot path.

## Stage 2: bounded cave-exit probes, motion-aware sampling and diagnostics

**Implemented in the separate Stage 2 draft branch** (without touching
Sodium, Embeddium, vanilla rendering queues, saves or real worldgen):

- Opt-in client sampler still defaults OFF. The observational cadence now
  adapts to state: `TRANSITION` or a player moving >4 blocks from the previous
  sample gets a probe every **10 ticks**; stable, stationary open-surface or
  sealed-cave states probe every **40 ticks**. Teleport-like jumps of >24
  blocks immediately reset policy to conservative transition.
- Five local loaded-chunk sky probes distinguish open sky, covered cave and
  mixed entrance. A solid-roof candidate uses the existing heightmap once;
  mixed exposure or thin cover bypasses extra calls and stays transition.
- For a potential sealed cave, test at most **four 24-block cardinal sightlines**
  toward sky-exposed, already-loaded endpoints; a clear view of the outside
  causes `TRANSITION`, not `SEALED_CAVE`. Unknown chunks and non-occluding
  obstructing material (e.g. glass) must revert to transition. This is
  bounded *heuristic exposure detection*, **not guaranteed full visibility
  or a legitimate terrain-culling criterion**.
- Fixed-size `AdaptiveVisibilityTelemetry` counts observations, unknown
  samples, mode distribution and **probe-only** mean/p95-bucket/max cost;
  it is not a frame-time/GPU/drawcall profiler. It allocates no per-sample
  histogram nodes; logging is rate-limited to every 20 observations.
- Pure-Java parity tests verify the mode machine, fail-open tunnel behavior,
  cadence, and bounded telemetry. NeoForge build is required independently.

To enable in a testing client only:

```text
-Drealscale.client.visibility.profile=true
```

**Still not implemented:** actual section queue reprioritization, reducing
underground GPU submissions, or proven FPS gains. A stable public renderer
integration and measured client A/B are mandatory before those changes.
A tunnel opening beyond the 24-block horizon cannot be disproven by this
heuristic; keep standard Sodium/vanilla occlusion even in `SEALED_CAVE`.

## Stage 3: real client FPS/frame-interval measurement (opt-in)

**Implemented measurement and parser, NOT proven FPS improvement.** NeoForge
1.21.1's supported `RenderFrameEvent.Post` fires once after each rendered
frame. `RealScaleClientFrameProfiler` measures consecutive Post-to-Post
`System.nanoTime()` intervals, a useful client responsiveness sample
that **includes VSync/frame limiting, render scheduling and pacing**.
It is **not** a GPU query or exact CPU shader/render time.

Use these two JVM switches on both benchmark candidates:

```text
-Drealscale.client.fps.profile=true
-Drealscale.client.fps.scene=enclosed_cave
```

Optional additional scene mode telemetry:

```text
-Drealscale.client.visibility.profile=true
```

Leave `visibility.profile` identical in both candidates. FPS profiling
is disabled by default and adds **no frame-event nanoTime cost while off**.
When ON it ignores menus/world loads, resets on pauses or >2s gaps,
warms up 10 seconds after the scene changes, and prints one readout for
each complete ~30-second *uninterrupted, steady-mode* window:

```text
[RealScale fps profile] interval=... scene=enclosed_cave mode=SEALED_CAVE frames=... mean_fps=... p50_interval_ms=... p95_interval_ms=... p99_interval_ms=... low1pct_fps_lower=... slow_over_50ms=... max_interval_ms=... method=post_to_post_including_frame_pacing_not_gpu
```

- `mean_fps` uses total frame count / total observed seconds.
- `p50/p95/p99_interval_ms` are **coarse 0.5ms upper bucket bounds**,
  not measured GPU times; `256+` denotes percentile overflow.
- `low1pct_fps_lower` is a **conservative inverse-p99 proxy** rather
  than a precisely measured industry-standard 1% low.
- `slow_over_50ms` counts long observed frame intervals.
- The classifier resets the window after surface/cave/transition changes,
  avoiding mixed-mode "surface" results.

**Controlled A/B steps (actual GPU hardware required):**
1. Use the SAME machine, RealScale generation preset/seed, completed
   pregenerated disposable world, Java/NeoForge, RAM allotment, resolution,
   render distance, simulation distance, quality flags, driver and window size.
   For a renderer mod benchmark, test supported Sodium separately; never
   use Sodium and Embeddium simultaneously.
2. Use a fixed viewpoint and view direction; do not move or open menus
   during each 30-second window. Set VSync and FPS cap consistently,
   preferably off for both if seeking GPU throughput. Turn off shaders
   for first-pass analysis.
3. Run at least three full recorded windows **per scene per candidate**
   (>=100 seconds including warmup), alternate run order A/B/A/B across
   conditions, and repeat surface, cave, skylight/exit, alpine and far
   pre-generated terrain. Do not force new chunks mid-FPS measurement.
4. Save each candidate's logs. **Do not** compare old RealScale builds
   lacking equivalent telemetry instrumentation to a new build with
   instrumentation and assume an apples-to-apples result.
5. Compare logs (use matching `--scene` AND `--mode`):

```sh
python3 qa/compare_client_fps.py baseline.log optimized.log \
  --scene enclosed_cave --mode SEALED_CAVE \
  --output build/fps-cave-comparison.json
```

The comparison script refuses incomplete, mixed or overflowing
observations. It reports the *median of per-window* FPS/p95 values,
not an incorrectly pooled global percentile. Positive differences
remain **descriptive** until hardware/thermal variation, repeatability
and build compatibility are assessed.

**Current scope:** Stage-1/2 mode and probe optimizations alone only
reduce *optional telemetry* overhead. They do **not** reprioritize
Sodium's actual mesh builds, GPU submissions or chunk drawing; therefore
they must not be advertised as increasing FPS. The known 180s distant
virgin-chunk worldgen failures are unrelated release blockers.

## Proposed D: Adaptive Visibility & Rendering Budget (DESIGN ONLY)

This proposal assigns **priorities to optional/asynchronous render work**
rather than forcing already visible scenery to disappear. There is no
independent RealScale renderer or Sodium patch in this branch.

### Environment state machine (observational, client only)

| State | Conservative indicators | Work *preference* |
| --- | --- | --- |
| `OPEN_SURFACE` | Direct or nearby sky/horizon exposures, unobstructed surface surroundings, or current camera can see exterior terrain | After all currently visible/urgent geometry, favor distant surface scenery and *supported* optional LOD updates. Buried and proven-occluded mesh *rebuilds* receive lower **background** priority. |
| `SEALED_CAVE` | Sustained nearby enclosure with a roof, no sampled sky exposure, enclosed camera neighborhood; sight corridor checks do not show the exterior | After all currently visible/urgent geometry, favor nearby and forward cave Sections and tunnel-adjacent geometry. Background updates for safely occluded distant surface/horizon geometry receive lower priority. |
| `TRANSITION_OR_UNKNOWN` | Cave mouth, skylight, open shaft, deep ravine, vast cavern with visible exit, near-surface ambiguity, incomplete chunk data, rapid camera motion, or mixed samples | Keep conservative normal visibility and mesh-work priorities; both surface and cave remain eligible. |

The player **Y coordinate is context, never the determinant**. A cave at
Y=1200 can still be sealed; Y=70 behind a mountain wall can be sheltered.
A skylight or canyon can expose substantial vertical distance. A cave
opening with transparent panes/fluids needs normal transparent rendering.

Possible **low-rate inputs**, subject to API compatibility and benchmarks:
- Camera-adjacent block and sky exposure from a small, bounded sample set.
- A few capped line-of-sight probes toward exterior horizons and shaft mouths.
- Camera motion/direction, saved surface height in the already loaded area.
- Renderer-visible Section list, if exposed by supported upstream APIs.
  Never re-run an expensive full raycast for each Section or every frame.
- Do not touch unloaded chunks to classify the environment; insufficient
  evidence always means `TRANSITION_OR_UNKNOWN`.

### Non-negotiable scheduling invariants

1. **Visible first**: camera-visible, newly opened, block-changed and
   immediately adjacent Sections always retain normal/urgent priority,
   independent of classification, world height or geometric layer.
2. **Never delete/delay indefinitely**: background work retains a bounded
   starvation-free share of the existing renderer's budget. Unknown or
   newly visible geometry immediately promotes to normal priority.
3. **No hard Y clipping**: underground caves, surface cliffs, sky islands,
   water and cross-height apertures must not pop or vanish.
4. **No automatic render-distance or server view-distance toggles**:
   these can force repeated unload/reload and cannot exceed server's sent
   chunk range. Do not force-load distant chunks or change server simulation.
5. **Avoid mode oscillation**: only enter a confident surface/cave mode after
   multiple consistent low-frequency samples (prototype idea: >=2 seconds).
   Switch to conservative transition immediately on missing visibility
   data, new opening or ambiguous geometry. Cooldown/hysteresis thresholds
   should be measured; not yet performance-validated.
6. **Upstream-first**: without a supported Sodium/other renderer API to
   reprioritize work safely, this stays a **telemetry and compatibility
   feature**, not a fragile client mixin. Use Distant Horizons when
   supported for optional horizon LOD; never recreate it.
7. **No worldgen changes**: client mesh/occlusion work cannot resolve
   dedicated-server 180-second virgin-chunk generation stalls.

### Required future proof before code integration

- Record mode decisions vs verified sky/exterior sightlines across
  lowland, alpine plateau, sealed mid-height cave, kilometer cavern,
  cave entrance, shaft, skylight, deep canyon and offshore sky archipelago.
- Baseline Sodium and optional Distant Horizons with the *same fully
  pregenerated worlds*, seed, camera path, graphics settings and hardware.
- Record p50/p95 frametime, mesh build queue and Section counts by mode;
  do not extrapolate gains from palette-only disk inventory.
- Test quick mode switching, camera teleport, missing chunks, block mining,
  water transparency, cave exits and >8,192-block distant scenery. Require
  **zero new holes/popping** and no additional stuttering.
- Only prioritize *identified redundant work* after evidence that the
  current renderer leaves a meaningful uncovered RealScale-specific gap.

## C: RealScale-specific fixes ONLY after objective evidence

If Sodium already culls the hidden sections and FPS/frametime recovers,
the issue is resolved by **external compatibility** and RealScale must not
duplicate that renderer. If retained Sections or rebuild queues remain
excessive specifically due to 2,096 height, isolate the exact client
bottleneck and propose **an optional, guarded, per-Section** strategy.
An optimization must prove line-of-sight correctness around open pits,
cave networks, tunnels, water, transparent blocks, and block updates.
Do **not** alter cave generation, disable hidden blocks or clip at player Y.

The server's 180-second remote RCON worldgen timeout is an independent
release blocker: reducing client rendering does not speed up generating
an untouched chunk on a dedicated server.

Related tracked scope: #35 (this audit), #33 (avoid duplicate mods),
PR #32 and PR #34 (worldgen performance/QA).
