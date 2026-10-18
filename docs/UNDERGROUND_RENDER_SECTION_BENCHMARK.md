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
