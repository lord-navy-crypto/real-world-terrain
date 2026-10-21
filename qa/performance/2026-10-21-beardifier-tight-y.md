# Experimental Beardifier Y256 versus Y64 support guard

Branch experiment/vanilla-beardifier-tight-y. Original conservative Y256 remains the default, and production main is unchanged.

The optional property -Drealscale.experimental.beardifier.tight_y=true narrows the envelope around every known vanilla structure piece and jigsaw source from 256 to 64 blocks. Unknown metadata still disables optimization. No original structures, density formula, world height, cave, aquifer or noise changes.

Each NEW 64-only zero shortcut is independently replayed with unmodified vanilla compute 1/256 of the time, separately from existing Y256 shortcuts. This experiment requires nonzero tight-only replay samples and raw-bit exact zero on all sampled shortcuts.

Same-source same-seed real dedicated NeoForge worldgen benchmark compares Y256 / Y64 / Y256 at three fresh distant waypoints for Measured seed 2 and Sacred Timeline seed 42. Saved block parity diagnostics and baseline-baseline drift are kept. Never claim FPS improvements; this changes CPU world generation only.
