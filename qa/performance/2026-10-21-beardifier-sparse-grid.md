# RealScale vanilla Beardifier bounded sparse-cell support index

EXPERIMENT ONLY. Branch: experiment/vanilla-beardifier-sparse-grid, built on original Y+XZ union experiment. Production main is untouched.

## Mechanism

Before, a union bounding rectangle across multiple distant structure pieces or jigsaw junctions could contain unoccupied space. A new 16x16-block grid marks every cell touched by each individual piece or junction after expanding each influence by a conservative 32-block X/Z guard. Only unmarked cells may skip original vanilla Beardifier computations.

Additional opt-in JVM flag: -Drealscale.experimental.beardifier.sparsegrid=true (requires ycull and xzcull true). OFF by default. The grid supports at most 4096 cells; impossible, oversized or invalid metadata disables its optimization, retaining the existing union/vanilla algorithm.

## Validation

Pure Java cell-boundary and randomized property tests, real NeoForge Y+XZ union / Y+XZ sparse / Y+XZ union on identical full-height virgin far routes in Measured seed 2 and Sacred seed 42. Independent direct vanilla method replays sample sparse-only skips at 1/64 and test demands nonzero zero-equivalent samples. Save block diagnostics remain separate from inherent decoration variance.

No GPU FPS claims; separate client rendering stage is still required.
