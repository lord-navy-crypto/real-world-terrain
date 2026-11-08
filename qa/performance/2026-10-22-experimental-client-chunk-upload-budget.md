# Experimental actual client-side chunk upload pacing — NeoForge 1.21.1

Branch: experiment/client-chunk-upload-budget. Based on stable main; NOT included in production main by default.

## Actual behavior, not merely advisory

The Minecraft 1.21.1 vanilla SectionRenderDispatcher.uploadAllPendingUploads() method drains completed section upload Runnables on the client render thread. If many distant full-height sections finish together, uploads may cluster on a frame. Opt-in Mixin timeslices these **same** FIFO upload Runnables across real NeoForge RenderFrameEvent.Pre/Post boundaries (the prior adaptive visibility detector was advisory only).

Properties:

- `-Drealscale.experimental.client.uploadbudget=true`: install client-only upload budget Mixin. Default OFF, and no default main modification.
- `-Drealscale.experimental.client.upload_budget_us=2000`: requested budget, microseconds per active gameplay frame, clamped to 250..10000.
- `-Drealscale.experimental.client.upload_max_tasks=8`: per-frame maximum completed upload Runnables, clamped to 1..32.
- `-Drealscale.client.chunkqueue.profile=true`: optional read-only profiling without activating the Mixin (baseline run).
- `-Drealscale.client.fps.profile=true`: existing frame interval profiler; use in BOTH scenarios with same scene label.

## No-lost-tasks safety design

Vanilla queue is consumed with the original Runnable.run() in queue order and no chunk mesh data is synthesized, omitted, or modified. Remnant tasks remain in the exact queue for the next frame. Menus, world loading, disconnect and calls outside RenderFrameEvent.Pre/Post are unmodified vanilla. A new frame resets the quota; queues cannot be permanently starved as every active frame grants at least one task. Does not alter render distance, occlusion graph, worker generation, blocks, biomes, terrain heights, shader state, nor Sodium.

Does not guarantee per-task upload time or total GPU duration: a single Runnable may exceed the requested time budget before we can stop it. This could **reduce throughput and delay distant terrain appearance**. Opt-in testing only.

## Actual physical-client acceptance test (not substituted by GitHub server CI)

1. On physical NeoForge client at identical full 2096-high measured/sacred worlds, same shader/mod list, window size, VSync/FPS cap, and view distance, capture 3-5 separate 30s sample windows on baseline with only `-Drealscale.client.chunkqueue.profile=true` and `-Drealscale.client.fps.profile=true`.
2. Repeat identical measured movements + cold chunk arrivals with `-Drealscale.experimental.client.uploadbudget=true` and the same profiling flags. Also test stationary warm camera orbit, teleport, returning from menu, dimension travel.
3. Compare 1%-low FPS, p95/p99 frame intervals, peak upload queue, frames with backlog, time to first visible distant section and time until all distant chunks appear. A larger queue and delayed terrain appearance can outweigh smoother frame intervals.
4. Verify no missing chunks after standing stationary long enough, no exception at world unload, no GL/render thread errors and compatibility with any renderer replacement. No raw FPS gain is claimed until A/B runs; turn the property OFF to revert.

## Verification

GitHub PR CI can compile the NeoForge 1.21.1 client & run pure-Java quota smoke tests and real dedicated-server smoke tests. Those cannot prove rendering correctness because GitHub Linux CI has no reliable playable graphical client. Physical-client A/B is explicitly required.

Reference: NeoForge 1.21.1 RenderFrameEvent.Pre/Post and Minecraft 1.21.1 SectionRenderDispatcher public API/mapping.


## Phase 21 — actual mode-aware vanilla upload scheduling (opt-in)

New additional JVM switch (requires the original uploadbudget=true):
`-Drealscale.experimental.client.uploadbudget.modeaware=true`.

This **connects the already-existing conservative cave/surface classifier to
the actual vanilla render-thread FIFO queue**. It does not infer Section Y from
Runnable objects or replace the renderer's original visibility traversal.

Per gameplay render frame:
- `TRANSITION`, cave entrance, unknown environment or renderer unavailable:
  **bypass** experimental upload throttling and let Minecraft drain normally.
- `OPEN_SURFACE`: use the normal configured frame budget (default 8 tasks /
  2 milliseconds).
- `SEALED_CAVE`: allow up to twice the configured frame budget (default
  16 tasks / 4 milliseconds) so near-cave geometry is uploaded more promptly.
- With >=64 queued upload Runnables, activate a bounded 2x catch-up budget.
  Per-frame limits never exceed 64 tasks or 10 milliseconds.
- Menus, world transitions, synchronous drains and disconnects still bypass
  the override. When the feature is off, every prior default is unchanged.

No tasks are dropped, reordered, hidden or permanently skipped. The policy
alters **upload throughput**, not Section-level compilation order, occlusion,
or draw calls. It is not a replacement for Sodium/Embeddium visibility
traversal. A real client A/B is still needed to check frame pacing against
terrain appearance latency; server CI cannot prove FPS or no popping.


## Phase 22 — optional frame-pressure feedback and a default-on profiler fix

The FPS logger accidentally used `"true"` as the fallback value for
`realscale.client.fps.profile` despite being documented as opt-in. This is
corrected to `"false"`: ordinary clients no longer sample per-frame FPS
histograms or query the client world in this event subscriber by default.

A third opt-in flag enables experimental frame-pressure feedback:

```text
-Drealscale.experimental.client.uploadbudget=true
-Drealscale.experimental.client.uploadbudget.modeaware=true
-Drealscale.experimental.client.uploadbudget.adaptive=true
```

It observes the previous frame's Pre-to-Post elapsed time (includes scheduling
and possible VSync; NOT pure GPU time). Two consecutive frames >=28ms restrict
the next stable surface/cave upload quota to the original base budget, even if
the cave/large-backlog path otherwise requests 2x. Four consecutive healthy
frames <=18ms restore the optional 2x budget. Intervening frames reset streaks
and cannot induce unstable rapid switching. A gap >500ms resets the governor
instead of treating a debugger pause or OS suspend as continuous frame lag.

TRANSITION and unknown states always bypass the Mixin. Every enabled
gameplay frame still permits at least one original FIFO upload Runnable.
No changes are made to Section mesh identity, section-compile queue order,
worldgen, DH cave LOD geometry or Minecraft's render distance.

The frame governor is entirely OFF by default. It may lower client stalls in
a given hardware configuration, but it could also delay distant geometry.
Only paired physical-client A/B can confirm the tradeoff.
