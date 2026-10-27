# Two-mode playable release candidate (Natural + USGS Big Sur v2)

This is a **candidate branch**, not yet a tested or merged stable release.
The source snapshot comes from `experimental/terrain-modes-20261026`
(commit `6b0c52b581ee0e5a64ff901365f024d30aaf62b0`).
PR #136 and that experimental branch are not merged here; they remain
separate places to develop the six real-DEM-fitted archetypes.

## Create World menu

Only these **RealScale** entries are advertised by BOTH Minecraft world
preset tags (`normal` and `extended`):

1. `realscale:natural_three_realms` — Natural Landscapes; existing
   `realscale:tva_natural_smoothed` terrain settings remain unchanged.
2. `realscale:usgs_big_sur_v2` — USGS Big Sur v2; existing
   `realscale:usgs_big_sur_v2` noise settings and its vanilla biomes remain unchanged.

`replace: false` intentionally keeps Minecraft/other datapack entries.
This branch only limits the RealScale additions. The USGS entry existed as a
world preset JSON but was absent from both menu tags. It is restored by
exposing the **existing** identifier, without renaming/mutating its generation
graph. The branded `realscale:measured_three_realms` remains present for saves,
but is NOT also shown as a third RealScale menu option.

The fitted Alpine, Canyon, Glacial, Volcanic, Karst and Dune presets, and
other historical/cave/biome experiments remain **registered resources** for
saved-world compatibility but not normal/extended Create World choices.
Use the experimental branch for ongoing development and acceptance testing.

## Scope and acceptance gates

The changes are intentionally limited to menu tags, regression checks and
release documentation. They do **not** remove experimental code or make
unverified performance, FPS, historical-save, or worldgen parity claims.

- [ ] `python3 -m unittest qa.test_realscale_branding_compat -v` passes.
- [ ] `python3 -m unittest qa.test_all_worldgen_graphs -v` passes.
- [ ] `python3 qa/verify_repository.py` and NeoForge Java 21 Gradle build pass.
- [ ] Both new worlds boot and save chunks with a fixed seed; compare creation
      timing, CPU, allocations, and 49+ representative saved heightmap chunks.
- [ ] Repeat at multiple distances/seeds and verify USGS DEM bounds, structures,
      biomes, caves and spawn safety.
- [ ] Test old save IDs with backup copies; check existing worlds never mutate
      their preset names or IDs unexpectedly.
- [ ] Review JAR contents and platform/version requirements before labelling
      anything Stable or merging to `main`.

No production merge, force-push or deletion of experimental resources is
part of this candidate.
