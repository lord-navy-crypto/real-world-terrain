# Beardifier Y256 vs fully original vanilla: release-blocking proof

This branch is stacked on the prior opt-in Y256 experimental branch. No production `main` change. The tighter Y64 candidate is **not included** here: that branch has conflicting speed data and saved blocks that sometimes differ even from two original controls. We must first establish whether the broader original 256-block structure support envelope can improve fully vanilla logic without any unexpected changed block.

Both presets are tested at 2096 world height, seeds 2/42 and 3 fresh distant points. Four real NeoForge server runs per preset: full original vanilla / Y256 / full vanilla again / Y256 with sampled replay. Replay results do not enter speed measurement. Use sampled original compute +0.0 bit parity and triage saved Anvil blocks/biomes/heightmaps/structures against TWO vanilla controls. The diagnostic triage is continue-on-error to keep full evidence; **any candidate-only variation is a block on deployment even if CI is green**. Any apparent speedup requires a second shuffled A/B/A with low vanilla drift.

The original vanilla status is determined by `ycull=false`; **no experimental mixin code executes**. Candidate uses `ycull=true` (256-block conservative vertical guard), never Y64. Do not conflate prior Y256-vs-Y64 results with original-vs-Y256 comparisons.
