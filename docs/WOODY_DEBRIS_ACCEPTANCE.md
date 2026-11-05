# Forest Woody Debris Pile — experimental feature acceptance

The Minecraft `block_pile` feature is being prototyped as a cheap multi-block woody debris cluster. It is NOT the same as a fallen tree, nurse log, or ecology succession algorithm.

Current entries:
- configured_feature/forest_woody_debris_pile.json
- placed_feature/forest_woody_debris_pile.json
- neoforge/biome_modifier/forest_woody_debris_pile.json

## Required validation before merging
1. Start NeoForge Minecraft 1.21.1 dedicated server; inspect datapack codec errors including `minecraft:block_pile` and weighted state provider entries.
2. Generate at least 100 new taiga chunks per seed for several seeds; count woody debris features and visual orientation.
3. Confirm no logs float excessively, overwrite living trunks, interrupt rivers, break snow layers or appear inside protected structures.
4. Confirm no feature cycle or chunk generation timeout from biome modifier injection.
5. Benchmark terrain heights and river connectivity against verified hydrology branch separately; this repository branch does not include hydrology commit 8962a71.
6. If the vanilla `block_pile` implementation fails any placement or appearance checks, keep the feature disabled and replace it with a custom deterministic bounded multi-block feature with two-phase placement validation.

This is an experimental branch: JSON commit confirmation is not proof that the feature loads or works in-game.
