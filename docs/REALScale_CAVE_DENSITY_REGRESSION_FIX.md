# RealScale TVA — Grand Cave Density Repair (experimental)

## Actual saved-world QA evidence from PR #27

- Alpine, seed 2: subsurface air sampled 1,874,439 / 7,060,480 = 26.55%, violating the existing 20% ceiling.
- Canyon, seed 42: excess isolated spikes/one-block pits. Source not yet confirmed; could be terrain, features or caves.
- Some seeds: worldgen RCON timed out after 180 seconds while forcing distant chunks. Runtime bottleneck not yet isolated.
- Core Java compilation succeeded; that alone did not prove world generation quality.

## This repair

GrandOverworldCaveNetwork uses 768x768 cells instead of 512x512. Cave halls keep their full previous cross sections; ordinary hall-to-hall routes become roughly 768 blocks and exceptional three-cell arteries roughly 2304 blocks. Shifted site centers use CELL/2 to stay centered. All cave tiers, entrances and mixed passage types remain. No modifications to Minecraft oceans, aquifers, old-world terrain, Nether or End. Alpine, Natural, Canyon and measured presets continue to route final density through the same registered codec.

A standalone Java 21 test now samples cave-candidate volume over many mountain-height coordinates in addition to checking broad connected corridors, monumental rooms and parallel-thread determinism. Do not relax the saved-world void limit to make this pass.

## Important remaining issues

The six custom underground biome JSONs are present but not yet wired into the live biome source. Their biome feature arrays are currently empty, so enabling them directly could silently remove vanilla ore and underground decoration features. Do not introduce that regression; their feature parity and actual biome selection require a separate careful fix.

Actual saved-world void fraction differs from pure geometric void candidates, because terrain solidity, aquifers and other Minecraft carvers also contribute. These changes are experimental until real reruns of failing Alpine seed 2, Canyon seed 42, and Natural/Measured seeds succeed. RCON timeouts need profiling to distinguish expensive density computation from world loading.

Tests: source/Java21, Gradle, fixed-seed server Anvil audit, ocean and rivers, highland caves and cave walkability. Use new disposable worlds.
