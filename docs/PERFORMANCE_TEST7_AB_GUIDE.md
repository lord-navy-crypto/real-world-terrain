# RealScale Test.7 Performance A/B Guide

## Why the maintenance update exists
RealScale Test.6 had three default-on diagnostic paths unrelated to normal
gameplay: FPS frame tracking, client render-queue polling (despite a disabled
upload Mixin), and Aquifer sampling. Test.7 makes all three opt-in. This does
not change terrain density, cave/ocean content, Minecraft world height, world
preset IDs or the separate Beardifier experimental optimization.

## Default JVM switches
| JVM property | Test.7 default | Role |
| --- | --- | --- |
| realscale.client.fps.profile | false | Frame-interval collector |
| realscale.client.chunkqueue.profile | false | Renderer queue observer |
| realscale.experimental.client.uploadbudget | false | Optional upload pacing |
| realscale.experimental.aquifer.audit | false | Read-only fluid sampler |
| realscale.client.visibility.profile | false | Advisory visibility classifier |
| realscale.experimental.beardifier.ycull | true | Unchanged experimental worldgen optimization |

## Physical-client test protocol
1. Back up important worlds and keep one RealScale version per game instance.
2. Test Test.6 and Test.7 with the same pre-generated world or seed, identical
   view distance, graphics, JVM/RAM, shader pack and loaded mod versions.
3. Start with DH/Chunky/Iris/Sodium absent to isolate RealScale; repeat with
   the combined stack and the appropriate Guard addon later.
4. Compare the same camera route and warmed-up viewpoint. Keep VSync and FPS
   caps consistent (preferably OFF in both measured runs). Record FPS, frame
   spikes, memory usage and Minecraft latest.log.
5. For an optional instrumented A/B run, add BOTH launch flags below to both
   instances; collect at least three 30s steady frame windows per version:

   -Drealscale.client.fps.profile=true
   -Drealscale.client.fps.scene=surface_alpine

6. Analyze matched scenes/modes using the existing repository script:

   python3 qa/compare_client_fps.py baseline.log test7.log --scene surface_alpine --mode TRANSITION

Select the observed mode reported in each log; it can be OPEN_SURFACE,
SEALED_CAVE, or TRANSITION. Results describe frame intervals, NOT GPU times.
Profiling itself adds overhead: leave all instrumentation OFF in normal play.

## Interpretation
A smoother client frame graph is not evidence of faster server-side chunk
generation. A valid worldgen result needs comparable real NeoForge A/B timings,
saved-chunk parity and loaded-mod traces. Native DH listener binding is not
proof of GPU-rendered distant LOD or Iris shader support.

If Test.7 runs worse, restore Test.6 and include the same route, screenshot,
logs, seed and list of loaded mods in the issue. Do not experiment on your only
copy of an existing world.
