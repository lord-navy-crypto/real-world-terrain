# Ultimate Archive Design

**Date:** 2026-11-22  
**Repository:** `lord-navy-crypto/real-world-terrain`  
**Target branch:** `fix/natural-automate-shared-climate-work-20261122`

## Intent

Keep Natural Automate as the only public world preset while moving the actual source content of all hidden/legacy presets and non-core noise settings into one auditable archive. Preserve every original registry path and ID so existing saves, datapacks, tests, and the built JAR continue to resolve the same resources.

## Verified inventory

- 41 world-preset JSON resources: 1 public + 31 hidden + 9 explicitly legacy.
- 34 noise-setting JSON resources: 3 current active settings + 31 non-core/legacy settings.
- 132 density functions, 69 biomes, and shared features remain outside this migration.
- The prior performance/worldgen validation head completed 21/21 workflows and 47/47 checks. The archive migration must receive its own fresh CI verification.

## Selected architecture: archived source plus compatibility symlinks

1. Move the actual bytes of 40 hidden/legacy world-preset JSONs to `archive/ultimate/resource-overlay/data/realscale/worldgen/world_preset/`.
2. Move the actual bytes of 31 non-core noise-setting JSONs to `archive/ultimate/resource-overlay/data/realscale/worldgen/noise_settings/`.
3. Keep the single public preset and three current noise settings as regular files in `neoforge/src/main/resources`.
4. Replace the 71 old source files at their original paths with relative symbolic links to the archive copies. The links preserve the original path contract and avoid a second editable copy.
5. Let the normal Gradle main-resource source set resolve these file symlinks. The built JAR must still contain all 41 preset and 34 noise-setting JSON resources at their exact original registry paths. A dedicated JAR assertion verifies this; if the Gradle/OS toolchain fails to follow file symlinks, stop and repair the packaging contract rather than shipping a broken archive.

This is intentionally not an extra datapack root or a registry rename. It is a source-of-truth relocation with compatibility pointers.

## Safety boundaries

- The `normal` and `extended` menu tags remain exactly `realscale:natural_automate_three_realms`.
- No registry ID is deleted or renamed.
- Natural Automate equations, coefficients, weights, climate gates, cave geometry, dimensions, world height, biome source chain, and all six fitted morphology kernels remain unchanged.
- Density functions, biomes, configured/placed features, dimension types, and Java kernels are not moved. Their shared dependency graph is too broad for a safe archive move in this task.
- PR #163 is not merged into `main` by this task.

## Archive evidence

`archive/ultimate/MANIFEST.json` lists all 71 moved resources, their original registry paths, and original Git blob SHA-1 values. `archive/ultimate/performance/benchmark-summary.json` preserves the one-sample A/B results and strict 180-second regression outcome. It explicitly avoids presenting a one-sample delta as a statistically stable speedup.

## Acceptance criteria

- Active regular source files: exactly one world preset and three noise settings.
- Archive regular source files: exactly 40 world presets and 31 noise settings.
- Compatibility symlinks at all 71 original resource paths resolve to their matching archive file.
- Catalog and checksum validation pass.
- All 41 preset and 34 noise-setting registry resources remain in the built JAR at the original paths.
- Both Create World menu tags expose only Natural Automate.
- Existing source QA and the relevant real-server worldgen workflows pass.
