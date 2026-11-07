# Phase 24 — Optional predictive FIFO upload frame pacing

## Reason for the experiment

The existing opt-in section-upload budget checks the time already consumed
**after** each completed Runnable. Three 0.7ms uploads may be admitted
to a 2ms frame because the third begins at 1.4ms and finishes at 2.1ms.
A budget of previously completed time cannot prevent this overshoot.

## Implementation

An additional JVM opt-in flag, requiring the mode-aware upload budget:

```text
-Drealscale.experimental.client.uploadbudget=true
-Drealscale.experimental.client.uploadbudget.modeaware=true
-Drealscale.experimental.client.uploadbudget.predictive=true
```

The client measures completed original vanilla upload Runnables (already
recorded by the Mixin), and estimates next-task cost with a bounded
3:1 moving average. When at least one task has already completed in the
frame and remaining time is less than the estimate, it defers remaining
FIFO work to the following frame.

- First task of every active gameplay frame is ALWAYS allowed, regardless of
  the historical estimate (no starvation from prediction).
- TRANSITION/unknown, teleports, menus and synchronous drains still run
  through vanilla with no throttling.
- Estimates reset after world changes and menu pauses; input samples are
  capped at 20ms and cannot overflow the moving average.
- No reordering, omission, persistence, Section Y inference or renderer mesh
  changes.
- Without the extra predictive flag, the previous time-and-count budget
  is byte-for-byte equivalent in behavior.

## Unit test and expected benefit

The deterministic test uses 0.7ms Runnables with a 2ms upload allowance:
the optional predictor defers the third task at 1.4ms instead of admitting
it and spending 2.1ms in that frame. The same test verifies that the old
nonpredictive behavior remains unchanged. This is a **synthetic CPU-budget
example**, not an observed user FPS gain.

The predictor can conservatively delay chunk visibility when individual
Runnable costs vary sharply. Retain it as a third, disabled-by-default
experiment until paired physical-client A/B confirms p95/p99 improvement
without unacceptable nearby-section appearance delay.
