# RealScale: Branch archaeology and vanilla-block ecology experiment

Checked on 2026-10-12 against the remote repository's actual branch tips.

## Six remote refs; only four distinct tips

| Branch | SHA (abbreviated) | Judgment |
| --- | --- | --- |
| main | edd75989 | Baseline, leave intact |
| feat/realism-max-sea100-v1.2 | edd75989 | Same tip as main: no distinct sea-level-100 work to merge |
| import/geocore-original-sources-20261011 | edd75989 | Same tip as main: historical naming only |
| development/v1.2-continuous-realism-caves | f6eb3998 | Six commits ahead of main; ancestor of current v1.3 |
| archive/realism-georeal-smoothing-experiment-20261012 | 87f7a1dd | Ancestral experiment, not unmerged independent functionality |
| development/v1.3-all-presets-continuous-caves | moving tip | Actual active experimental branch |

There is NO abandoned branch with new registered production biome JSONs
among these six refs. The v1.2 and archived cave/smoothing work already lie
in the ancestry of v1.3; do NOT cherry-pick those complete branches.

## Resurrect IDEAS from research/ instead of blindly merging branches

* research/v0.7: deterministic multi-scale geology, priority-flood, erosion,
  drainage; standalone numerical Java, not an installed world generator.
* research/v0.8: tectonic uplift, SI runoff, lithological rock resistance,
  sediment ledger. Its engineering plan mentions karst, glacial valleys,
  alluvial fans, volcanic rock, angled strata and climate-driven materials,
  but most are design proposals and not compiled Minecraft features.
* research/v0.9: region-wide watershed and GeoSnapshotCache;
  VanillaColumnAdapter maps modeled geology and water to existing
  Minecraft stone, granite, andesite, deepslate, dirt, sand, gravel, clay,
  snow and water. It DOES NOT drive the current NeoForge chunk generator.
  Outlets between entire separate macro-regions are NOT connected.
* research/v1.0: measured DEM importer and terrain data provenance.
  The limited Big Sur tile is the part used in existing mod worldgen.

Priority order for revival:
1. P0: SAFE decoration-only microhabitats with existing block IDs and
   individual opt-in placed features, no noise geometry or sea-level edits.
2. P1: Low-cost lithology/surface substrate masks: calcite, tuff,
   granite, andesite, gravel, clay, sandstone, basalt; avoid replacing
   ores, structures, caves and all established chunk borders.
3. P1: Different CAVE MICROHABITATS: limestone/calcite dripstone,
   damp mossy galleries, dry gravel passages and sparse lichen;
   separate decoration from carving density. Do not assume vanilla cave
   biomes have been replaced or that historical research implements them.
4. P2: Region watershed and depositional shelves, AFTER multi-region
   outlets, heightmaps, fluids, player structures and TPS are verified.
5. P3: Full GeoCore ChunkGenerator integration, isolated to a NEW world
   preset. Preserve existing Realism MAX, GeoReal, Himalayan and USGS IDs.

Not recommended: restore old unconditional cache_2d smoothing that caused
generation slowdowns, old hollow-mountain cave thresholds, experimental
sea-level changes, or the old water source implementation without banks.

## First implemented opt-in experiment: vanilla-block microhabitats

Command at repository root:

    python3 scripts/make_vanilla_ecology_pack.py

Outputs a standalone zip in build/RealScale-Vanilla-Ecology-Lab-EXPERIMENTAL.zip.

This pack is NOT enabled by compiling or running RealScale.
It has a Minecraft 1.21.1 pack format 48 descriptor, three individually
namespaced configured features, three placed features and three NeoForge
add_features biome modifiers using entirely pre-existing block IDs:

| Microhabitat label | Existing vanilla biomes | Added vanilla blocks |
| --- | --- | --- |
| Alpine mossy scree | stony_peaks, windswept_gravelly_hills | moss_carpet on stone, andesite and gravel |
| Arid canyon scrub | badlands, eroded_badlands, wooded_badlands | dead_bush on sand, red_sand or terracotta |
| Cool taiga fern pockets | old_growth_pine_taiga, old_growth_spruce_taiga, windswept_forest | fern on grass, dirt, podzol and coarse_dirt |

The first label describes a visual moss mat, NOT a new lichen species.
Features only attempt to add a thin/plant vanilla block in air above
the listed natural substrates. No new blocks, mobs, noise densities,
climate signals, terrain heights, fluids, carving or actual biome IDs.

IMPORTANT: Enabling this experimental datapack affects listed vanilla
biomes in any world that has the datapack enabled. Install ONLY in a NEW
disposable test world's Data Packs screen before creating chunks, with
RealScale installed in Minecraft 1.21.1 NeoForge. Do not add it to global
packs or existing valuable saves. Removing it later does not delete
already generated decoration. Back up worlds separately.

Inspect using biome locate commands and a same-seed control world with
the pack absent. A passing ZIP/JSON unit test is NOT proof of real-game
generation or good visual results. The generated pack has not yet
received its own multi-seed Minecraft server validation.

NeoForge 1.21.1 add_features documentation:
https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/

Verified vanilla 1.21.1 random_patch/simple_block reference:
https://mcasset.cloud/1.21.1/data/minecraft/worldgen/configured_feature/patch_grass.json

## Future: ACTUAL new ecological biome IDs

The pilot makes habitat patches INSIDE EXISTING BIOME IDs; it does not
register realscale:alpine_scree or realscale:karst_grotto in the biome
registry. Real new biomes need full biome worldgen resource JSON and a
biome source which chooses those biomes based on geography, humidity,
altitude and three-dimensional underground setting, while protecting
existing biomes, cave networks, structures and decoration order.

This must be opt-in in a separate NEW world preset, and only promoted
after real server/Anvil audits for climate, chunk boundaries, caves,
player spawn, TPS and other biome interactions.
