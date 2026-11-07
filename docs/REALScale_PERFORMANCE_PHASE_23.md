# Phase 23 — Fail-open mode freshness and release invariant repair

## CI repair

The unified TVA source invariant had still permitted a TEST-only FPS profiler
with `System.getProperty(flag, "true")`, conflicting with the newer explicit
default-OFF client profiler. The invariant is now strict and requires the
visibility profiler to be `Boolean.getBoolean(...)` and the FPS profiler to
have an explicit `"false"` default.

## Upload-mode snapshot freshness

The client tick classifier and the render frame run at different moments.
Even with the existing teleport invalidation on the next client tick, a
client render frame could previously consume the old `SEALED_CAVE` mode after
a world/dimension switch or a >24-block camera jump. The read-only
`currentModeFor(world,player)` now checks (without any chunk load, heightmap
lookup, block access, or raycast):

- same tracked client world and an actual prior local sample;
- Overworld and non-submerged camera;
- finite movement coordinates;
- no more than 24 blocks horizontally or vertically from the last sample.

If any requirement fails, it returns TRANSITION immediately, causing the
experimental upload budget to bypass and Minecraft to drain its own original
FIFO queue. The opt-in FPS profiler also labels such stale frames as
TRANSITION to avoid mixed-mode benchmark windows.

## No stale pressure from menus

When the game displays a menu or leaves gameplay, the optional slow-frame
governor resets. A previous scene's constrained state cannot incorrectly
remove the next scene's 2x upload allowance.

## Tests and scope

- Pure Java smoke tests verify dimension/teleport/NaN fail-open.
- JUnit verifies freshness boundary conditions and unsupported dimensions.
- Python source checks protect the render-thread read-only snapshot hook,
  default-OFF FPS profiler, and menu reset.
- Worldgen, 2096-height dimension, saved blocks, cave routing, DH visibility
  and Section mesh data remain unchanged.

This is a correctness and responsiveness hardening step, not a measured FPS
increase or a claim of native per-Section culling. Physical-client A/B remains
required for release qualification.
