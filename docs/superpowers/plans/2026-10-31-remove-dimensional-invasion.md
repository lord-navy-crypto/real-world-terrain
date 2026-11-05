# Remove Dimensional Invasion Implementation Plan

> **For agentic workers:** Execute task-by-task on the isolated branch. Steps use checkbox syntax.

**Goal:** Remove all dimensional-invasion gameplay while preserving latitude climate, Geo Wonders, Sky Ocean, and all three dimensions.

**Architecture:** Delete the isolated invasion classes and data assets; remove only their registrations and wrapper hooks. Keep the independent Natural Wonders path.

**Tech Stack:** Java 21, NeoForge 21.1.249 / Minecraft 1.21.1, Python unittest, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-31-remove-dimensional-invasion-design.md`

## Global Constraints
- Do not alter latitude climate density functions or latitude-related registrations.
- Preserve Natural Ecotones, Geo Wonders, Sky Ocean, cave systems, USGS, and the Overworld/Nether/End topology.
- Do not modify the original Natural or USGS noise-settings resources.

## Review Focus
- GeoWondersBiomeSource must remain a valid identity wrapper after invasion hooks are removed.
- SkyOceanBiomeSource must still unwrap Geo Wonders for offshore ocean classification.
- Existing presets must not retain dangling codec or feature IDs.
- The public normal/extended terrain menu must remain unchanged.
- CI must not retain tests that require deleted invasion classes or resources.

---

### Task 1: Remove invasion registrations and wrapper hooks

**Files:**
- Modify: `neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/TVAWorldGuard.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/GeoWondersBiomeSource.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/SkyOceanBiomeSource.java`
- Delete: `CrossRealmBiomeSource.java`, `CrossRealmShowcaseBiomeSource.java`, `CrossRealmTerrainPatchFeature.java`, `DimensionalInvasionFeature.java`
- Delete: invasion-only presets and every `invasion_*` biome modifier/configured feature/placed feature resource.
- Test: `qa/test_no_dimensional_invasion.py`, `qa/test_tva_woven_optional_features.py`, `qa/test_tva_branch_unification.py`, `qa/test_natural_wonders_manufacturing.py`, `.github/workflows/ci.yml`

- [ ] Add a failing Python regression test asserting invasion registrations, classes, presets, and data assets are absent while latitude climate and Geo Wonders remain.
- [ ] Run the focused test and confirm it fails on the current branch because the invasion system is still present.
- [ ] Remove only invasion registrations, methods, classes, presets, and data assets.
- [ ] Update existing QA to validate the independent Geo Wonders opt-in and remove obsolete invasion assertions.
- [ ] Run focused invasion-removal, branch-unification, Natural Wonders, worldgen graph, and climate tests.
- [ ] Commit the isolated removal.

