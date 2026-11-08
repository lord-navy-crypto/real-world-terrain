# RealScale Phase 21: Opt-in surface/cave mode-aware upload budget

This phase wires the already implemented conservative exposure classifier into
the experimental **real vanilla render-thread upload queue**.

## Activation

The policy is disabled unless **both** JVM options are supplied:

```text
-Drealscale.experimental.client.uploadbudget=true
-Drealscale.experimental.client.uploadbudget.modeaware=true
```

Without either flag, vanilla and existing budget behavior are unchanged.

## Behavior

| Classifier state | Upload behavior |
| --- | --- |
| OPEN_SURFACE | Existing configured 2ms/8 task limit (defaults) |
| SEALED_CAVE | 2x the frame budget (4ms/16 tasks by default) |
| TRANSITION / UNKNOWN | Bypass this Mixin; vanilla drains normally |
| Any confident mode with >=64 pending uploads | Bounded catch-up: >=2x base budget |
| Menu, unload or synchronous renderer drain | Vanilla behavior |

Catch-up is capped at 64 tasks and 10ms per frame. This only changes the
rate of executing already-completed FIFO Runnable uploads. Existing Minecraft
section visibility, rebuild order, occlusion, camera clipping, worldgen and
render distance are unchanged. No block or saved-world format changes.

The classifier activates when mode-aware upload scheduling is requested even
if Distant Horizons is absent. In stable states it probes once/20 ticks, and
in transitions or meaningful movement once/10 ticks. Its existing conservative
unknown/ravine/portal/teleport fail-open behavior is retained. An unknown
render dispatcher bypasses the budget.

## What this does NOT solve

This is an **actual mode-sensitive GPU upload pacing experiment**, not
per-section Y classification or a true section build priority queue. The
vanilla queue exposes Runnables, not a stable public per-Section ID/visibility
API; we explicitly do not guess their height or reorder them. This implementation
also does not reduce distant GPU draw submissions. A renderer-specific and
documented API plus full client A/B proof would be required before doing so.

## Verification

- Pure-Java smoke and JUnit policy tests: limits, transition bypass, backlog
  catch-up, no dropped completed tasks, disabled/menu behavior.
- NeoForge 21.1.249/Java 21 build and Gradle 9 compatibility.
- Physical-client A/B with identical pregenerated 2096-high worlds, same DH
  settings, fixed view distance and matched FPS frame profiles.
- Include cave exits, deep ravines, sky islands, mining/block updates,
  high-altitude caves and teleport to detect delayed mesh appearance.
- Reject the experiment if p95/p99 worsens or terrain appearance latency
  increases unacceptably.

The development flag remains OFF by default.
