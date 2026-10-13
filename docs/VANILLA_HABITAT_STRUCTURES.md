# RealScale Vanilla Habitat Structures (NeoForge 1.21.1)

Status: NEW opt-in prototype, not bundled as an auto-enabled worldgen feature.
This extends the existing RealScale GeoEcology / Vanilla Microhabitat work.

Build from the repository root:

    python3 scripts/make_vanilla_habitat_structures_pack.py

Output: `build/RealScale-Vanilla-Habitat-Structures-EXPERIMENTAL.zip`

Install the ZIP through **Create World > Data Packs** in a NEW disposable
RealScale world, before any chunks are created. Requires the matching RealScale
NeoForge JAR (the feature codecs are implemented by the mod). Do not use the
ZIP as a resource pack. Do not load it into valuable existing saves.

## Two NEW multi-block habitat features

- **Fallen timber / moss-bearing logs** in taiga and old-growth forests: one
  horizontal 3–5-block spruce log, optionally topped with moss carpets.
  Validates every candidate column before any write. Surface must be level,
  naturally soiled and free of placed blocks. Never alters trees, water,
  stone, structures or neighboring chunks.
- **Riparian reeds and bank undergrowth** in vanilla river/frozen river:
  bounded small shoreline clusters using existing sugar cane, ferns and
  short grass. Must have naturally suitable ground and adjacent water/ice.
  Plant survival is validated before placement. Does not create, replace
  or redirect any water or ice source.

Both features are registered in `VanillaHabitatFeature.java`. A generator-ID
guard requires `realscale:` noise settings, so the datapack cannot place these
features in normal vanilla or foreign-generator worlds even if enabled there.
All target modifications remain inside the currently generating chunk.

## Not yet implemented

This datapack does **not** register or select new biome IDs (no
`realscale:alpine_scree`, `realscale:riparian_marsh`, etc.). Existing vanilla
biomes can look like new ecological sub-regions, but their registry biome IDs
do not change. It also does not add custom animals, mobs, blocks, templates,
seasonal migration, upstream/downstream flow or a true watershed network.

Regions Unexplored and Terralith need separate combination tests: their
new biome IDs are not targeted by this first conservative data pack.

## Validation gates

- `python3 -m unittest discover -s qa -p 'test_vanilla_habitat_structures.py' -v`
- `python3 scripts/make_vanilla_habitat_structures_pack.py`
- Full NeoForge Gradle compile and in-game datapack registry load.
- Multi-seed Anvil placement and modded biome-combination QA still needed
  before promising visual density, general third-party compatibility, or
  promoting the pack to default.

The previously green 14/14 workflows validate the river behavior of commit
`8962a71`; later ecology commits need fresh CI checks.
