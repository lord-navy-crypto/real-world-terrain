# RealScale Overworld Ecology v0.4 — functional surface transition habitats

Target: Minecraft Java **1.21.1**, NeoForge 21.1.x. Experimental branch, tested separately from stable `main`.

## Why this matters
The opt-in Realism MAX Natural Ecotones biome source can select **40 surface ecotone IDs**. Before this patch, their actual biome JSON registries had **empty feature arrays and no creature or monster spawning lists**. A few additional NeoForge undergrowth modifiers cannot make up for missing vanilla ores, geodes, springs, flowers, shrubs, trees, animals, and cave ambient behavior.

## Implementation
For every one of the 40 selectable surface ecotones, bring in the corresponding 1.21.1 vanilla biome's full **eleven feature-generation stages**, **all mob spawn-category entries**, **spawn costs**, and its **cave ambient mood sound**.

Donor choices are explicit and reviewable in `neoforge/ecotones/habitat_donors.json`; examples:
- `woodland_meadow` inherits *plains* (scattered oak and meadow undergrowth)
- `marshy_woodland` inherits *swamp*
- `subalpine_woodland` inherits *taiga*
- `rocky_alpine_meadow` inherits *meadow*
- `snowy_treeline` inherits *snowy_taiga*
- `mangrove_transition` inherits *mangrove_swamp*
- `tropical_forest_edge` inherits *sparse_jungle*

Ecotones retain their unique **temperature**, **downfall/precipitation**, **grass/foliage/water colors**, and **carvers**. Existing RealScale placed-feature biome modifiers continue adding localized accents without the loss of vanilla biome generation stages.

## Preservation and boundaries
- No alterations to the primary world's terrain noise, density, aquifers, cave carving, biome-source rules, world height, sea level, river courses, beaches, coastlines, oceans, Nether, End or default world preset.
- The biome-source's existing river/frozen-river/ocean guard remains in place: it does not paint new custom biomes into those original water biome cells.
- Six separate underground cave-biome registry scaffolds remain untouched and **do not magically become part of the active cave selector**.
- Donor-based vegetation is an initial usable ecology baseline. It is **not** a perfect weighted blend of both bordering vanilla biomes; certain strips may still visually resemble their donor too strongly.
- Minecraft biome setup affects **newly generated chunks in new experimental worlds**. Back up worlds and do not treat a source-data change as a live fix for old terrain.
- Reference source: [misode/mcmeta 1.21.1-data](https://github.com/misode/mcmeta/tree/1.21.1-data/data/minecraft/worldgen/biome).

## Verification
The offline registry check requires every surface ecotone in the catalog to be mapped, and verifies all eleven stages, vanilla underground ores and geodes, vegetation, nonempty monster/ambient spawns and a cave mood sound.

Release still requires a green **Gradle+NeoForge build** and dedicated server startup with the opt-in preset, plus saved Anvil biome coverage. Realistic fauna abundance, actual surface tree density, cliff vegetation, riverbanks, cave minerals and multi-seed chunk-generation performance need further in-game evidence before stable merge.
