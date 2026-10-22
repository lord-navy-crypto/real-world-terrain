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
