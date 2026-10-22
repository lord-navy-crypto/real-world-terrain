# Experimental Beardifier Y256 versus Y64 support guard

Branch experiment/vanilla-beardifier-tight-y. Original conservative Y256 remains the default, and production main is unchanged.

The optional property -Drealscale.experimental.beardifier.tight_y=true narrows the envelope around every known vanilla structure piece and jigsaw source from 256 to 64 blocks. Unknown metadata still disables optimization. No original structures, density formula, world height, cave, aquifer or noise changes.

Each NEW 64-only zero shortcut is independently replayed with unmodified vanilla compute 1/256 of the time, separately from existing Y256 shortcuts. This experiment requires nonzero tight-only replay samples and raw-bit exact zero on all sampled shortcuts.

Same-source same-seed real dedicated NeoForge worldgen benchmark compares Y256 / Y64 / Y256 at three fresh distant waypoints for Measured seed 2 and Sacred Timeline seed 42. Saved block parity diagnostics and baseline-baseline drift are kept. Never claim FPS improvements; this changes CPU world generation only.

## Follow-up: unbiased benchmark design

The first successful Y64 A-B-A (Actions run 39628091630) tested Y64 with sampled original-algorithm verification enabled, while Y256 controls had that costly verification disabled. This biased the recorded wall-clock timings. The first run still confirmed 13,819,136 + 6,964,224 new shortcuts with 53,981 + 27,204 direct vanilla replay samples, all +0.0. It measured Y64 82.614s vs Y256 80.722s and 81.778s (Measured) and Y64 74.785s vs Y256 71.642s and 74.335s (Sacred). Do not claim speedup from these timings.

A second, dedicated workflow now measures three **uninstrumented** servers Y256 -> Y64 -> Y256, each with `verify=false`. A **fourth isolated Y64 server** with `verify=true` runs after the A-B-A and supplies independent correctness evidence. This fourth server's load time is not included in performance ratios. All four use the same commit, seed, 2096-block world, three distant virgin positions and normal structures. Save Anvil semantic mismatch diagnostics are still non-gating until a deterministic decoration baseline can distinguish optimization-caused differences.

## 2026-10-22 triage after unbiased A/B/A

Run https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39635789542 completed both jobs green; these greens indicate the CI reached all required operations, **not** that Y64 is approved for release.

Unbiased Measured: Y256 first 108.292s, Y64 117.349s, Y256 control 119.277s; baseline-to-baseline drift 10.14%, candidate not faster than both controls. Unbiased Sacred: Y256 first 74.736s, Y64 67.997s, Y256 control 74.049s; conservative single-run advantage 8.17%, reference drift 0.93%. Results conflict between presets and need more repeats.

Independent parity verification in the same run: Measured 13,819,136 additional Y64-only exclusion calls with 53,981 sampled vanilla +0.0 replays, Sacred 6,964,224 and 27,204 samples; no reported replay mismatches.

Anvil comparison discrepancies remain: Measured Y256-vs-Y256 passed all three waypoint saved-chunk samples but Y256-vs-Y64 had 73 blocks changed near 4096,-2048. Sacred Y256-vs-Y256 showed 4 gold ore / stone variations near -2048,1536; Y256-vs-Y64 had 70 and 57 blocks with heightmap differences near 1024,1024 and -2048,1536. They may include feature-order nondeterminism; not established. **No merge allowed until understood.**

New triage script `qa/neoforge/triage_beardifier_three_worlds.py` groups differences per voxel as (1) candidate-only while both Y256 worlds agree, (2) reference-variable where two Y256 baselines differ and Y64 matches either, or (3) third-value different from both Y256 results. Biomes, heightmaps and structures are included. Produces machine-readable 3-way report with real world block coordinates and top block transitions. Diagnostic checks continue-on-error: no false promotion from a green workflow. The report explicitly requests human review whenever candidate-only/third-value positions exist.
