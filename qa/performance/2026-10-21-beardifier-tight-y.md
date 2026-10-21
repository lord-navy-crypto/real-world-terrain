# Experimental Beardifier Y256 versus Y64 support guard

Branch experiment/vanilla-beardifier-tight-y. Original conservative Y256 remains the default, and production main is unchanged.

The optional property -Drealscale.experimental.beardifier.tight_y=true narrows the envelope around every known vanilla structure piece and jigsaw source from 256 to 64 blocks. Unknown metadata still disables optimization. No original structures, density formula, world height, cave, aquifer or noise changes.

Each NEW 64-only zero shortcut is independently replayed with unmodified vanilla compute 1/256 of the time, separately from existing Y256 shortcuts. This experiment requires nonzero tight-only replay samples and raw-bit exact zero on all sampled shortcuts.

Same-source same-seed real dedicated NeoForge worldgen benchmark compares Y256 / Y64 / Y256 at three fresh distant waypoints for Measured seed 2 and Sacred Timeline seed 42. Saved block parity diagnostics and baseline-baseline drift are kept. Never claim FPS improvements; this changes CPU world generation only.

## Follow-up: unbiased benchmark design

The first successful Y64 A-B-A (Actions run 39628091630) tested Y64 with sampled original-algorithm verification enabled, while Y256 controls had that costly verification disabled. This biased the recorded wall-clock timings. The first run still confirmed 13,819,136 + 6,964,224 new shortcuts with 53,981 + 27,204 direct vanilla replay samples, all +0.0. It measured Y64 82.614s vs Y256 80.722s and 81.778s (Measured) and Y64 74.785s vs Y256 71.642s and 74.335s (Sacred). Do not claim speedup from these timings.

A second, dedicated workflow now measures three **uninstrumented** servers Y256 -> Y64 -> Y256, each with `verify=false`. A **fourth isolated Y64 server** with `verify=true` runs after the A-B-A and supplies independent correctness evidence. This fourth server's load time is not included in performance ratios. All four use the same commit, seed, 2096-block world, three distant virgin positions and normal structures. Save Anvil semantic mismatch diagnostics are still non-gating until a deterministic decoration baseline can distinguish optimization-caused differences.
