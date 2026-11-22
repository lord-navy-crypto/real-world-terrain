# Ultimate Archive Design

**Date:** 2026-11-22  
**Repository:** `lord-navy-crypto/real-world-terrain`  
**Target branch:** `fix/natural-automate-shared-climate-work-20261122`

## Intent

Keep Natural Automate as the only active source-level and player-visible world preset while consolidating historical preset definitions and obsolete noise-setting definitions into one auditable archive. Preserve old registry IDs and existing-save/datapack compatibility; this is a source-layout consolidation, not a destructive deletion.

## Current evidence

- The release menu tags `normal` and `extended` each expose exactly `realscale:natural_automate_three_realms`.
- The source tree contains 41 world-preset JSONs: one public profile plus 40 hidden/legacy profiles (31 hidden and 9 explicitly legacy).
- The source tree contains 34 noise-setting JSONs. The current public preset needs `natural_automate_v2`, `grand_end`, and `grand_nether`; the other 31 are legacy/diagnostic settings.
- The latest performance and worldgen validation commit completed 21/21 workflow runs and 47/47 checks successfully.

## Goals

1. Leave exactly one world-preset JSON in `neoforge/src/main/resources/data/realscale/worldgen/world_preset/`: `natural_automate_three_realms.json`.
2. Move the other 40 world-preset JSONs into `archive/ultimate/resource-overlay/data/realscale/worldgen/world_preset/`.
3. Keep only the three current noise settings in the active resource tree; move the other 31 into the matching archive resource-overlay path.
4. Add the archive resource overlay as a Gradle main-resource source root so the built JAR retains the exact original registry paths and compatibility IDs.
5. Keep all density functions, biomes, configured features, placed features, Java kernels, and test logic in place until a complete transitive reference audit proves individual files safe to relocate.
6. Update the catalog, integrity checks, source tests, JAR checks, CI path filters, and README to describe and enforce the new layout.
7. Preserve the measured performance results in the archive with their one-sample caveat.

## Non-goals

- Do not delete or rename any registry ID.
- Do not change Natural Automate equations, weights, coefficients, climate gates, cave geometry, dimension settings, world height, or biome-source chain.
- Do not remove any of the six fitted morphology kernels.
- Do not remove CI tests or claim a statistically stable speedup from one paired sample.
- Do not merge PR #163 into `main`.

## Approaches considered

1. **Delete old definitions:** rejected because the catalog explicitly preserves legacy registry IDs and saved-world/datapack compatibility.
2. **Move old definitions into an archive resource overlay (selected):** keeps source organization clean while Gradle packages the same resource paths into the JAR. This is the safest way to archive the source without changing runtime registry availability.
3. **Duplicate old definitions in both source and archive:** rejected because it leaves two sources of truth and makes future edits ambiguous.

## Architecture and compatibility

The archive is a resource overlay, not a separate standalone datapack. Gradle adds `../archive/ultimate/resource-overlay` as a resource source directory. Thus a file stored at `archive/ultimate/resource-overlay/data/realscale/worldgen/world_preset/<id>.json` is still packaged at `data/realscale/worldgen/world_preset/<id>.json` in the JAR.

The two Create World tags remain unchanged and expose one public choice. Hidden/legacy presets and noise settings remain available in the built JAR for explicit compatibility and controlled QA, but their source files live in the ultimate archive.

Density functions and other low-level worldgen registries remain in the active resource tree for now. Moving them without a complete dependency closure could break the current preset, old settings, custom codecs, or saved worlds.

## Performance evidence to preserve

For the same-runner paired A/B workflow (one paired sample per profile):
- Natural Automate, seed 2: 92.619 s baseline vs 87.371 s candidate; observed +5.67%.
- TVA Sacred Timeline, seed 42: 51.377 s vs 51.218 s; observed +0.31%.
- Measured, seed 2: 44.557 s vs 44.524 s; observed +0.07%.

The benchmark itself labels each result as one paired baseline-then-candidate sample, not a stable benchmark. Report these as observed single-sample deltas only. The separate strict 180-second tests and all saved-world QA jobs passed.

## Acceptance criteria

- Active source world-preset directory contains exactly one JSON, the Natural Automate public preset.
- Active source noise-settings directory contains exactly the three current settings.
- The archive contains exactly the 40 hidden/legacy preset definitions and 31 non-core noise settings, with no duplicate active IDs.
- The JAR contains all 41 original world-preset registry entries and all 34 noise-setting registry entries at their original paths.
- Both Create World tags still contain exactly one value: `realscale:natural_automate_three_realms`.
- Catalog validation, source-integrity checks, Python tests, Gradle build, and the relevant real-server workflows pass.
- The archive performance report preserves the raw observed values and caveat.
