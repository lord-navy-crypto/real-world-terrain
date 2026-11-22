# Ultimate Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all 40 hidden/legacy world presets and 31 non-core noise settings into one ultimate archive while preserving their exact JAR registry paths and leaving Natural Automate as the only active source preset.

**Architecture:** `archive/ultimate/resource-overlay` becomes an additional Gradle resource root. The active resource tree retains the current Natural Automate preset and its three required noise settings; the archive overlay supplies the other registry JSONs at their original paths, so the built JAR and old save compatibility remain intact.

**Tech Stack:** GitHub Git Data API, Gradle/NeoForge 1.21.1, Java 21, Python 3.12 unittest, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-22-ultimate-archive-design.md`

## Global Constraints

- Preserve the one public preset ID: `realscale:natural_automate_three_realms`.
- Preserve all 41 world-preset registry paths and all 34 noise-setting registry paths in the built JAR.
- Keep `natural_automate_v2.json`, `grand_end.json`, and `grand_nether.json` in the active resource tree.
- Do not change density functions, biomes, configured features, placed features, Java generation kernels, or worldgen formulas.
- Do not delete legacy IDs or change saved-world compatibility.
- Do not merge PR #163 into `main`.

## Review Focus

- A resource moved into the archive overlay is still packaged at exactly the same JAR path.
- No hidden/legacy preset is accidentally included in either public menu tag.
- No archive path duplicates an active resource path.
- The three active noise settings remain available without depending on archive ordering.
- The performance report does not turn a one-sample A/B delta into a statistically stable claim.

---

### Task 1: Add archive-layout regression tests

**Files:**
- Create: `qa/test_ultimate_archive_layout.py`
- Modify: `qa/test_realscale_branding_compat.py` only as needed to define the desired archive lookup contract.

**Interfaces:**
- The active preset root is `neoforge/src/main/resources/data/realscale/worldgen/world_preset`.
- The archive overlay root is `archive/ultimate/resource-overlay`.
- The expected archive inventory comes from `docs/PRESET_CATALOG.json`: 40 hidden/legacy presets and 31 non-core noise settings.

- [ ] **Step 1: Write the failing archive-layout tests.** Assert that the active preset directory contains only `natural_automate_three_realms.json`; the archive contains exactly the catalog's 40 hidden/legacy presets; the active noise-settings directory contains exactly `natural_automate_v2.json`, `grand_end.json`, and `grand_nether.json`; and the archive contains the remaining 31 noise settings.
- [ ] **Step 2: Run the new test and verify the expected RED result.**

Run: `python3 -m unittest qa.test_ultimate_archive_layout -v`

Expected: FAIL because the current branch still stores the hidden presets and legacy noise settings under `neoforge/src/main/resources`.

- [ ] **Step 3: Commit the failing test only.**

```sh
git add qa/test_ultimate_archive_layout.py
git commit -m "test: define ultimate archive resource layout"
```

### Task 2: Move legacy resource files and wire the Gradle overlay

**Files:**
- Move: 40 files from `neoforge/src/main/resources/data/realscale/worldgen/world_preset/` to `archive/ultimate/resource-overlay/data/realscale/worldgen/world_preset/`.
- Move: 31 non-core files from `neoforge/src/main/resources/data/realscale/worldgen/noise_settings/` to `archive/ultimate/resource-overlay/data/realscale/worldgen/noise_settings/`.
- Modify: `neoforge/build.gradle`
- Create: `archive/ultimate/README.md`

**Interfaces:**
- Gradle source overlay: `sourceSets.main.resources.srcDir(file("../archive/ultimate/resource-overlay"))`.
- Public resources remain in the default `src/main/resources` root.

- [ ] **Step 1: Move files without changing their bytes or logical data paths.**
- [ ] **Step 2: Add the archive overlay as a main resource source directory in `neoforge/build.gradle`.**
- [ ] **Step 3: Add an archive README documenting source locations, runtime JAR paths, preserved compatibility, and the deliberate decision to leave density functions/biomes/features in place.**
- [ ] **Step 4: Run the focused archive-layout test and verify GREEN.**

Run: `python3 -m unittest qa.test_ultimate_archive_layout -v`

Expected: PASS; one active preset, 40 archived presets, three active noise settings, and 31 archived noise settings.

- [ ] **Step 5: Commit the resource migration.**

```sh
git add neoforge/build.gradle neoforge/src/main/resources archive/ultimate qa/test_ultimate_archive_layout.py
git commit -m "chore: consolidate legacy worldgen resources in ultimate archive"
```

### Task 3: Update catalog, integrity checks, docs, and CI packaging assertions

**Files:**
- Modify: `docs/PRESET_CATALOG.json`
- Modify: `docs/SHA256SUMS.json`
- Modify: `docs/UNIFIED_FEATURE_CONSOLIDATION.md`
- Modify: `README.md`
- Modify: `scripts/validate_preset_catalog.py`
- Modify: `qa/test_realscale_branding_compat.py`
- Modify: `qa/verify_repository.py`
- Modify: `.github/workflows/public-eight-mode-test-jar.yml`
- Modify: worldgen CI path filters in `.github/workflows/`
- Create: `archive/ultimate/performance/benchmark-summary.json`

- [ ] **Step 1: Update the catalog validator to load preset/noise-setting definitions from either the active resource root or archive overlay, while enforcing the exact active/archive split.**
- [ ] **Step 2: Update source tests and checksum verification to read archived definitions from the archive overlay and to compare archived resources against the built JAR's original data paths.**
- [ ] **Step 3: Update the JAR workflow to assert that all 41 preset IDs and all 34 noise-setting IDs are packaged, while both menu tags still expose only Natural Automate.**
- [ ] **Step 4: Add the measured A/B results and one-sample caveat to the archive performance report.**
- [ ] **Step 5: Update README, feature consolidation, preset catalog, and relevant CI path filters so future archive changes rerun the required tests.**
- [ ] **Step 6: Run focused tests.**

Run:
- `python3 -m unittest qa.test_ultimate_archive_layout -v`
- `python3 -m unittest discover -s qa -p 'test_realscale_branding_compat.py' -v`
- `python3 scripts/validate_preset_catalog.py`
- `python3 qa/verify_repository.py`

Expected: all commands exit 0.

- [ ] **Step 7: Commit docs, tests, and CI updates.**

```sh
git add docs README.md scripts qa .github/workflows archive/ultimate
git commit -m "docs: validate and document the ultimate worldgen archive"
```

### Task 4: Full build and real-world verification

**Files:** No additional production files unless a failing check requires a test-first repair.

- [ ] **Step 1: Run the full Python QA suite.**

Run: `python3 -m unittest discover -s qa -v`

Expected: all tests pass.

- [ ] **Step 2: Build the NeoForge JAR.**

Run: `cd neoforge && gradle --no-daemon build`

Expected: exit code 0.

- [ ] **Step 3: Verify the packaged JAR contains all 41 preset and 34 noise-setting registry resources, and both menu tags contain only Natural Automate.**

Expected: the JAR packaging assertion in `public-eight-mode-test-jar.yml` prints its explicit success marker.

- [ ] **Step 4: Verify relevant GitHub Actions workflows.**

Expected: source integrity, Gradle build, fitted-profile real-server boots, multi-seed saved-world QA, measured strict-180-second tests, and paired A/B benchmark all complete successfully. Report any workflow that remains pending or fails; do not infer success from a partial run.

- [ ] **Step 5: Commit any final verification-only documentation update if required.**

```sh
git status --short
git log -1 --oneline
```

