# RealScale: opt-in vanilla Beardifier horizontal exclusion (Y+XZ)

Experiment only. Branch: experiment/vanilla-beardifier-xz-cull. Production main not touched.

Goal: reduce original vanilla structure-blending calculations during fresh distant 2,096-block world generation. This DOES NOT accelerate GPU renderer or change draw distance.

New optional JVM flag: -Drealscale.experimental.beardifier.xzcull=true; requires existing -Drealscale.experimental.beardifier.ycull=true to install the Mixin. Both disabled by default.

Logic: inspect structure piece bounding X/Z rectangles plus junction positions while building the vanilla Beardifier. Outside their union plus a conservative 32-block guard, the original kernel has zero horizontal influence. Unknown metadata disables optimization; no structures or worldgen features are removed.

CI: existing Y-only / new Y+XZ / Y-only repeated server, exact same commit, same seed, same real preset and same three full-load remote waypoints. Every new XZ-only skip uses separate 1/256 same-context vanilla replay sampling. CI requires at least one successful NEW XZ sample and also compares saved region differences as diagnostics.

Expected proof standard: 100,000-case Java support bound test, real NeoForge build, new-XZ vanilla replay all zero, repeated latency improvement larger than baseline drift, and saved structure-adjacent geometry audit. No speedup claimed until all comparisons finish.

Future client-stage: perform separate real-GPU A/B of section mesh rebuild, upload and frame pacing; existing adaptive-visibility classifier is advisory only, and FPS profile reports intervals rather than renderer GPU work. Never drop mountains, caves, water, sky islands or visibility ranges just to hide cost.
