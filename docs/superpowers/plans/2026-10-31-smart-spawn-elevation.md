# Smart Spawn Elevation Implementation Plan

> **For agentic workers:** Execute task-by-task on the isolated branch. Steps use checkbox syntax.

**Goal:** Select a deterministic low-to-mid elevation initial spawn for new RealScale Overworlds without forcing chunk loads.

**Architecture:** A pure math/profile helper determines a seed-specific percentile for each terrain family. A NeoForge CreateSpawnPosition event adapter samples a bounded set of base-terrain columns, derives a target Y from the sampled elevation distribution, and applies the best safe candidate.

**Tech Stack:** Java 21, NeoForge 21.1.249 / Minecraft 1.21.1, standalone Java smoke tests, Python unittest, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-31-smart-spawn-elevation-design.md`

## Global Constraints
- Do not move existing saved-world spawns or alter player respawn behavior.
- Do not modify terrain density, latitude climate, Natural/USGS resources, or public preset menus.
- Do not force-load chunks; use ChunkGenerator base-height/base-column sampling only.
- If no safe candidate is found, allow vanilla spawn selection.
- Never assume the sea-level Y is near the land-surface elevation in RealScale.

## Review Focus
- Non-RealScale worlds and non-Overworld dimensions must be untouched.
- Liquid surfaces, ocean/river biomes, insufficient clearance, and extreme slopes must be avoided.
- Negative coordinates and different world seeds must remain deterministic.
- Search work must remain bounded (maximum 40 candidates) and must not call getChunk/getHeight.
- Unrecognized RealScale settings must use a conservative Natural low-to-mid percentile profile.

---

### Task 1: Deterministic preset percentile math

**Files:**
- Create: `neoforge/src/main/java/org/realscale/neoforge/RealScaleSpawnMath.java`
- Create: `neoforge/src/test/java/org/realscale/neoforge/RealScaleSpawnMathSmoke.java`
- Modify: `.github/workflows/ci.yml`
- Test: `qa/test_realscale_spawn_profile.py`

- [ ] Write smoke assertions first for deterministic seed percentiles, preset-specific bands, and elevation/slope scoring.
- [ ] Run the smoke test before the helper exists and confirm the expected missing-class failure.
- [ ] Implement profile mapping and pure scoring with no Minecraft dependencies.
- [ ] Compile/run the smoke test and require all assertions to pass.
- [ ] Add Python contract tests for the profile table and bounded deterministic sample budget.
- [ ] Commit the math helper and tests.

### Task 2: RealScale initial-spawn event integration

**Files:**
- Create: `neoforge/src/main/java/org/realscale/neoforge/RealScaleSpawnPosition.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java`
- Modify: `qa/neoforge/geo_wonders_optin_boot.py`
- Test: `qa/test_realscale_spawn_profile.py`

- [ ] Add source-level assertions for event registration, RealScale namespace guard, Overworld-only behavior, base-height/base-column sampling, safe surface checks, and vanilla fallback.
- [ ] Run the focused test and confirm it fails before event integration exists.
- [ ] Implement a bounded deterministic candidate search using the existing generator RandomState; derive the target Y from the safe candidates' preset-specific lower-to-middle percentile.
- [ ] Set ServerLevelData spawn and cancel vanilla selection only after a valid candidate is found.
- [ ] Run spawn profile, existing spawn safety, and spawn chunk-probe tests.
- [ ] Build the NeoForge JAR and verify the new classes are packaged.
- [ ] Boot a disposable server and verify the selected spawn persists to level.dat.
- [ ] Commit the event integration.
