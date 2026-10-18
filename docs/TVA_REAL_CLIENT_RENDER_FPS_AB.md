# RealScale — REAL client surface/cave/exit FPS gate

**Priority 2: distant-rendering stutter/FPS.** This documents testing on
a real graphical Minecraft 1.21.1 NeoForge client. CI cannot emulate a GPU
or prove an FPS increase. The existing `AdaptiveVisibilityBudget` is
**advisory only** and does NOT change renderer mesh queues or chunk
visibility. Do NOT claim this gate optimizes GPU draw calls.

## Procedure

1. Pre-generate identical coordinates and capture an immutable save-folder
   SHA-256/version ID. Same Mac/PC, operating system, GPU/driver, Java21,
   NeoForge, view and simulation distances, shader, resolution, FOV,
   mods, geometry/camera position, weather, time and player route.
   Keep the viewport in foreground. Disable VSync and FPS caps.
2. Turn on **both** opt-in diagnostics:
   `-Drealscale.client.visibility.profile=true` and
   `-Drealscale.client.fps.profile=true`.
3. In a pre-generated world collect **three or more valid 30-second frame
   windows per scene, per candidate**, after the 10s warmup:
   - `open_surface` with `OPEN_SURFACE` mode (mountain/horizon and islands);
   - `deep_cave` with `SEALED_CAVE` mode (giant underground cave);
   - `cave_exit` with `TRANSITION` mode (sky shaft/cave mouth/ravine).
   Set `-Drealscale.client.fps.scene=open_surface` etc individually.
   Stay within the same mode during measurement. The profiler discards
   mixed-mode windows. Ignore tests on worlds still generating chunks
   when evaluating pure rendering FPS; analyze chunk generation separately.
4. Record a JSON manifest with one baseline and one candidate. For each
   scene, give path to the REAL Java client stdout/stderr log. Paths are
   relative to the manifest directory. The following JSON is only a schema
   **example, not an actual benchmark or generated FPS data**:

```json
{
  "baseline": {
    "variant": "PR46",
    "environment": {
      "machine": "same-computer-id",
      "gpu": "your-GPU",
      "gpu_driver": "installed-driver",
      "os": "your-OS",
      "minecraft": "1.21.1",
      "neoforge": "21.1.249",
      "java": "21",
      "renderer": "same-renderer-version",
      "shader": "same-shader-or-off",
      "resolution": "1920x1080",
      "render_distance": 16,
      "simulation_distance": 8,
      "fov": 70,
      "vsync": false,
      "fps_cap": 0,
      "world_seed": 2,
      "world_preset": "realscale:tva_sacred_timeline_three_realms",
      "world_save_hash": "SHA256-of-frozen-save"
    },
    "scenes": {
      "open_surface": {"mode": "OPEN_SURFACE", "log": "baseline_surface.log"},
      "deep_cave": {"mode": "SEALED_CAVE", "log": "baseline_cave.log"},
      "cave_exit": {"mode": "TRANSITION", "log": "baseline_exit.log"}
    }
  },
  "candidate": {
    "variant": "RENDER_CANDIDATE",
    "environment": {
      "machine": "same-computer-id",
      "gpu": "your-GPU",
      "gpu_driver": "installed-driver",
      "os": "your-OS",
      "minecraft": "1.21.1",
      "neoforge": "21.1.249",
      "java": "21",
      "renderer": "same-renderer-version",
      "shader": "same-shader-or-off",
      "resolution": "1920x1080",
      "render_distance": 16,
      "simulation_distance": 8,
      "fov": 70,
      "vsync": false,
      "fps_cap": 0,
      "world_seed": 2,
      "world_preset": "realscale:tva_sacred_timeline_three_realms",
      "world_save_hash": "SHA256-of-frozen-save"
    },
    "scenes": {
      "open_surface": {"mode": "OPEN_SURFACE", "log": "candidate_surface.log"},
      "deep_cave": {"mode": "SEALED_CAVE", "log": "candidate_cave.log"},
      "cave_exit": {"mode": "TRANSITION", "log": "candidate_exit.log"}
    }
  }
}
```

5. Run:

```bash
python3 qa/assess_real_client_render_ab.py manifest.json \
  --output build/client_render_ab.json
```

The script rejects mismatched GPU, driver, world save, shader/view distances,
FPS limiter, missing scene or fewer than 3 compatible 30s windows. Its
`REVIEW_REGRESSION` is conservative (FPS -5%, p95 frame interval +5%, or
>2/1000 extra >50ms stutters in ANY scene). It exits nonzero on a detected
regression; the bounds are **review triggers**, not scientific significance
or proof of a real renderer gain. A descriptive improvement must still be
repeated and visually checked.

## Renderer implementation boundaries

**Current delivered client code:** a fail-open, low-frequency observer of
sky/roof/short clear sightlines, advisory render-task priorities,
zero-load chunk section inventory, and post-to-post frame interval records.
All probes are off unless explicitly enabled. It has **NO supported actual
Sodium/NeoForge render-queue integration**.

To optimize FPS next, first get these hardware A/B traces and a sampled
CPU/GPU profile (camera in rendered tall cliff, giant cave, narrow tunnel,
cave mouth). Identify whether the issue is visible mesh count, chunk mesh
rebuilds, GPU fill, or main-thread chunk upload. Implement a supported
interface for task priorities **only after identifying that hotspot**;
never omit a potentially visible Section, forcibly lower render distance,
or hide sky/shaft/ravine to manufacture FPS gains.

The server-side Measured/USGS 2096-high RCON timeout remains separate,
tracked by PR #48 and the strict 4×2 test matrix. A fast renderer cannot
fix new chunks stalled before completion.
