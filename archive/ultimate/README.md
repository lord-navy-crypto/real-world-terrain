# RealScale Ultimate Archive

This directory is the single source of truth for the hidden and legacy world-generation registry definitions that are no longer ordinary player choices.

## Inventory

- **40** hidden/legacy world-preset JSON definitions.
- **31** non-core / legacy noise-setting JSON definitions.
- **3** current noise settings remain regular active source files: `natural_automate_v2`, `grand_end`, and `grand_nether`.
- **1** regular active public world preset remains: `natural_automate_three_realms`.

The archived definitions live under `resource-overlay/data/realscale/worldgen/`, preserving their original registry-relative paths. The files at the corresponding paths under `neoforge/src/main/resources/data/realscale/worldgen/` are relative symbolic-link compatibility pointers to these archived files. This keeps existing source consumers, Gradle's standard main-resource processing, runtime registry IDs, old saves, and datapack references on the same paths while avoiding two independent copies of the JSON content.

The public menu tags remain unchanged and expose only Natural Automate. Archive files are not deleted from the JAR: compatibility IDs are intentionally retained.

## Why density functions were not moved

The 132 density functions are shared by multiple noise settings and presets, including Natural Automate's active dependency graph. Biomes, configured/placed features, dimension types, and Java kernels are similarly shared. Moving these resources without a complete transitive dependency and runtime registry audit could break old saves or the active generator, so they remain in their existing source locations.

## Integrity and tests

- `MANIFEST.json` lists every archived file, original registry path, and original Git blob SHA-1.
- `performance/benchmark-summary.json` records the paired A/B measurements and their one-sample limitation.
- `qa/test_ultimate_archive_layout.py` verifies the regular-file / archive / symlink split.
- `scripts/validate_preset_catalog.py` validates the catalog and compatibility pointers.
- The unified JAR workflow checks that all 41 preset and all 34 noise-setting registry resources remain packaged at their original JAR paths.

Do not edit a compatibility pointer as though it were the source file. Edit the corresponding file under `resource-overlay`, then run the archive tests and JAR packaging checks.
