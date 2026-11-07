# Phase 22 — Removing default FPS profiler overhead and guarded upload throughput

## Found and fixed

`RealScaleClientFrameProfiler.ENABLED` read
`System.getProperty("realscale.client.fps.profile","true")`.
The documentation had claimed opt-in, but the default unexpectedly enabled
per-frame timestamping, client-state checks and histogram allocations.

Phase 22 changes this default to `false` and adds an explicit source guard.
For real FPS experiments, pass `-Drealscale.client.fps.profile=true`
on *both* comparison builds.

## Optional frame-pressure governor

With all three flags:

```text
-Drealscale.experimental.client.uploadbudget=true
-Drealscale.experimental.client.uploadbudget.modeaware=true
-Drealscale.experimental.client.uploadbudget.adaptive=true
```

the client render thread observes RenderFrameEvent.Pre to
RenderFrameEvent.Post elapsed time. This includes frame pacing and may include
VSync blocking, so it is a conservative wall-time signal, not GPU timing.

- Two consecutive >=28ms intervals -> temporarily remove the mode-aware 2x
  upload boost, using the original base quota (default 2ms/8 tasks).
- Four consecutive <=18ms intervals -> re-allow the cave/backlog 2x boost.
- Invalid, zero and >500ms frame times reset the streak rather than causing
  a false transition.
- TRANSITION/unknown, menus, world unload and blocking renderer drains still
  use Minecraft's own upload queue without interception.
- Stable surface/cave states retain FIFO ordering; every allowed gameplay
  render frame can complete at least one original queued Runnable.
- The 64-task / 10ms cap still holds and the experimental feature remains
  off by default.

## What remains open

A static upload throttle cannot know which queued Runnable corresponds to a
visible or occluded vertical Section. This governor moderates real upload
throughput to avoid amplifying detected slow frames. It does **not** reduce
Section compilation, GPU draw counts or render height. It makes no claim of a
measured end-to-end FPS increase.

Performance acceptance requires paired graphical-client tests with matched
camera paths and already-pregenerated RealScale 2096-height worlds. Compare
p95/p99 frame times, queued uploads, and time-to-visible nearby Sections.
Disable the new adaptive flag if upload latency worsens.
