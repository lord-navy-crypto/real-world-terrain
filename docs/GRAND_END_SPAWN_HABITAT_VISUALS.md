# Grand End habitat visuals — three-biome spawn invasion add-on

This patch layers on top of PR #60; no original `main` biome names,
noise router, End Dragon island or original world presets are edited.

## Arrival and habitat

New unified Overworld worlds choose **exactly one** of the three
`realscale:end_chorus_gardens`, `realscale:end_crystal_barrens` or
`realscale:end_shattered_glades` biomes for the complete 192-block
first-spawn area (implemented in parent PR). A guarded 9×9 illuminated
arrival platform sits at X/Z=0 for first-world creation only.

This PR adds **visible terrain accents inside the Overworld spawn biome**.
Previously, the new End decoration feature returned immediately outside
`Level.END`, so the starting biome could have the correct F3 biome label
but appear like ordinary Overworld land.

After this patch, the three custom End biome modifiers add appropriately
themed features in two places:
- Grand End islands where their custom biome is selected, without
  changing the original End terrain or foreign-mod biome IDs.
- The newly created unified Overworld's 192-block spawn invasion zone,
  **only** when the world has the active opt-in cross-realm codec.

| Biome | End/Overworld visual accent | Particles |
| --- | --- | --- |
| Chorus Gardens | living chorus plant groves rooted on End Stone with purple and pale stone mosaics | vanilla `minecraft:end_rod` |
| Crystal Barrens | calcite/amethyst mosaics, short supported amethyst spires | vanilla `minecraft:glow` |
| Shattered Glades | broken purpur masonry, End stone brick pathways and occasional End rods | vanilla `minecraft:reverse_portal` |

Particles are intentionally very rare (probabilities 0.0015–0.004).
All blocks are built-in Minecraft blocks; there are **no extra dependencies**.

## Conservative compatibility rules

The feature always checks the actual biome ID at each decoration position.
In the Overworld it ALSO requires `TVAWorldGuard.hasCrossRealmInvasion` and
`CrossRealmBiomeSource.isSpawnInvasionZone`. It decorates **only** dry,
natural stone/soil/gravel substrate and never touches water, lava, a
third-party mod block, a structure block, or a neighboring chunk. For
natural End islands, it continues to require End Stone.

A limited number of decorations (two positioned attempts per chunk, 9×9
bounded footprint) prevents tall-world pregeneration from making thousands
of off-chunk searches. The core spawn platform is placed after initial
generation and takes precedence over vegetation at X/Z=0.

Importantly, preserving foreign biome IDs does **not** by itself prove
compatibility with BetterEnd's independent custom End world generator.
Validate the exact NeoForge port and its dependencies in a disposable
test world before recommending mixed installation.

## Test requirements

- Run `python3 -m unittest discover -s qa -p
  test_grand_end_three_biomes.py -v`.
- NeoForge build; create new Sacred and Measured unified worlds and confirm
  initial F3 biome belongs to exactly one custom End biome ID.
- Visit first spawn and use the /locate biome coordinates to verify actual
  terrain accent blocks; check the saved `.mca` and reload. Test seed
  diversity, ocean/highland spawns, End City preservation and FPS.
- Keep these source changes in a draft PR until realistic visual and
  cross-mod integration tests complete.

Existing saves and previous built-in world profiles remain unchanged.
