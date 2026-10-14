# Riparian Ecology v1 — validation status

This development branch adds two **experimental** vegetation patch features to the NeoForge 1.21.1 module. These are not multi-block fallen-log structures, new biome IDs, or a complete watershed model.

## What is included

- `realscale:riparian_fern_patch`: low-frequency fern patches in vanilla river biomes; rooted only on grass, dirt, podzol, or moss.
- `realscale:wetland_moss_patch`: low-frequency moss carpet patches in swamp and mangrove swamp biomes; rooted only on grass, dirt, mud, or moss.
- The generated blocks are vanilla blocks and no extra blocks or biome IDs are registered.
- NeoForge biome modifiers inject features during `vegetal_decoration`. No river water, ice, density function, DEM, or chunk-carving code has been modified.

## Verification to run (not yet executed)

1. Validate configured/placed feature registry loading on NeoForge 21.1.x and Minecraft 1.21.1. Fail the test if unknown feature IDs or placement filter serialization errors appear.
2. Create a new world, locate river and swamp biomes, and inspect multiple freshly generated chunks for actual patch appearance and ground support.
3. Compare river wet-cell counts and connectivity to the accepted reference build using the same presets/seeds. This repository's current main is `edd7598`, **not** the reported validated river commit `8962a71`; a direct regression claim is not possible until those source histories are reconciled.
4. Inspect crossings at chunk boundaries and visually confirm no floating plants, underwater fern blocks, disrupted ice caps, or modifications to terrain or player structures.
5. Re-run Gradle build and a real dedicated NeoForge server. CI success from another commit does not validate these new files.

## Follow-up backlog

- Implement a horizontal fallen-log feature with atomic placement checks, log orientation and ground support, without writing into adjacent ungenerated chunks.
- Add bankside root clusters and sediment bars only after deterministic riverbank detection is available from the validated hydrology source.
- Introduce separately registered biome IDs only after multi-seed terrain/river/ecology regression gates pass.
