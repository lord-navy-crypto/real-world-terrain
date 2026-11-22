# Ultimate Archive Implementation Plan

**Goal:** Store 40 hidden/legacy world presets and 31 non-core noise settings in one ultimate archive while preserving their exact original resource paths through relative symlink pointers.

**Spec:** `docs/superpowers/specs/2026-11-22-ultimate-archive-design.md`

## Tasks

- [ ] Add regression tests for the one-public/40-archived preset split, the three-active/31-archived noise split, and all compatibility symlink targets.
- [ ] Move the 71 original JSON blobs byte-for-byte into `archive/ultimate/resource-overlay/data/realscale/worldgen/`.
- [ ] Replace their original resource paths with relative symlinks to the archive files. Keep the Natural Automate public preset and the three current noise settings as regular files.
- [ ] Add `archive/ultimate/README.md`, `MANIFEST.json`, and the measured performance summary.
- [ ] Update the preset catalog validator, README, feature consolidation record, and JAR workflow. The JAR workflow must assert all 41 preset and 34 noise-setting registry resources are packaged at the original paths.
- [ ] Add `archive/ultimate/**` to the JAR workflow's pull-request path filters. The main CI workflow already runs on pull requests and invokes the archive-layout regression test.
- [ ] Run `python3 -m unittest discover -s qa -p 'test_ultimate_archive_layout.py' -v`.
- [ ] Run `python3 scripts/validate_preset_catalog.py` and `python3 qa/verify_repository.py`.
- [ ] Run the full NeoForge Gradle build and the JAR packaging assertion. This is the critical proof that the Gradle resource pipeline follows the symlinks and retains all original registry paths.
- [ ] Wait for the fresh current-head CI and real-server workflows. Report every pending/failing job; do not reuse the prior head's green status as proof of the archive migration.
- [ ] Do not merge PR #163 into `main` as part of this task.

## Non-negotiable invariants

- Do not change worldgen math, density functions, biome/feature resources, dimension settings, height, cave geometry, or the six fitted kernels.
- Do not delete or rename any registry ID.
- Do not call a single paired A/B sample a stable performance improvement.
- If symlink packaging fails on the supported build runner, stop and repair the build rather than leaving registry resources missing.
