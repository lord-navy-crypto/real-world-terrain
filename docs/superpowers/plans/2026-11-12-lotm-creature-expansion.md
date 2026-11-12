# LOTM Creature Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ten active LOTM vanilla-mob stand-ins with ten independent GeckoLib entities, biome-aware spawning, and kind-specific drops.

**Architecture:** One shared creature implementation is configured by `LotmCreatureKind`; ten distinct EntityTypes use stable IDs and per-kind stats, habitats, behavior flags, and drops. A shared GeckoLib renderer resolves per-kind generated geometry, texture, and animation resources.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, GeckoLib 4, JUnit 5, Python 3 standard library for deterministic asset generation.

**Spec:** `docs/superpowers/specs/2026-11-12-lotm-creature-expansion-design.md`

## Global Constraints

- Keep all work on `feature/lotm-sequence9-pilot-20261111`; do not modify `main`.
- Keep the feature inside the existing `realscale` mod.
- Preserve existing potion, characteristic, material, and player-save IDs.
- Sequence 7 stays archived and cannot spawn.
- No custom relic structures or new structure sets.
- Do not change terrain density, climate, biome-source, or world-preset code.
- GeckoLib is already required; do not add dependencies.
- New creature spawns are disabled when LOTM or the creature-spawn config is disabled.
- Legacy vanilla stand-ins default off.

## Review Focus

- A disabled LOTM config must prevent new creature natural spawns and creature drops.
- Each entity type must have an attribute supplier; missing one can crash on spawn.
- Invalid habitat IDs and null/negative spawn inputs must fail closed.
- Client renderer registration must not load client-only classes on a dedicated server.
- Existing archived Sequence 7 IDs and old creature drops must remain readable without re-enabling archived spawning.

---

### Task 1: Define the creature roster and pure spawn rules

**Files:**
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureKind.java`
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRules.java`
- Test: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRulesTest.java`

**Interfaces:**
- Produces `LotmCreatureKind.values()`, `id()`, `sequence()`, `dropId()`, `spawnCategory()`, `isHostile()`, `isAquatic()`, `isFlying()`, `width()`, `height()`, `maxHealth()`, `movementSpeed()`, `attackDamage()`, `armor()`, `spawnWeight()`, `minGroupSize()`, `maxGroupSize()`.
- Produces `LotmCreatureSpawnRules.canSpawnInBiome(LotmCreatureKind kind, String biomeId)`.

- [ ] Write tests asserting exactly ten active kinds, stable IDs, correct sequence group, and non-empty drop IDs.
- [ ] Write tests asserting every intended habitat is accepted and clearly incompatible habitats are rejected.
- [ ] Run `./gradlew test --tests org.realscale.neoforge.lotm.LotmCreatureSpawnRulesTest` and confirm it fails because the new API does not exist.
- [ ] Implement the enum and pure habitat selector with immutable sets.
- [ ] Run the focused test and then the full `./gradlew test` suite.
- [ ] Commit as `feat(lotm): define independent creature roster and spawn rules`.

### Task 2: Register ten independent entity types and server behavior

**Files:**
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreature.java`
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureEntities.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmConfig.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmEvents.java`
- Test: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureRegistryTest.java`

**Interfaces:**
- Consumes `LotmCreatureKind` and `LotmCreatureSpawnRules` from Task 1.
- Produces ten registered EntityTypes, each constructed with exactly one `LotmCreatureKind`.
- Produces server-authoritative kind-specific material drops.
- Produces `creatureEntities` config defaulting true and changes legacy `creatureVariants` default to false.

- [ ] Write registry metadata tests for ten distinct IDs, category, dimensions, and kind-to-drop mapping.
- [ ] Run focused test and confirm missing registry metadata fails.
- [ ] Implement a shared `PathfinderMob` entity with per-kind AI goals, attack response, and GeckoLib animation cache.
- [x] Register all ten EntityTypes and their default attributes.
- [x] Register ground/water spawn placements and gate them on LOTM + creature config.
- [x] Update drops to use the custom entity kind; preserve legacy drop mapping for already-tagged old entities.
- [x] Add distinct signature behavior profiles and server-side special effects for the ten active creatures.
- [ ] Run focused and full JUnit suites against the latest branch head.
- [ ] Commit as `feat(lotm): register independent occult creatures`.

### Task 3: Add GeckoLib renderers and deterministic original assets

**Files:**
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmCreatureModel.java`
- Create: `neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmCreatureRenderer.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmClientEvents.java`
- Modify: `scripts/generate_lotm_visuals.py`
- Test: `qa/test_lotm_creature_assets.py`

**Interfaces:**
- Consumes ten stable `LotmCreatureKind.id()` values.
- Produces per-kind `geo/lotm_<id>.geo.json`, `animations/lotm_<id>.animation.json`, and `textures/entity/lotm_<id>.png`.
- Renderer uses the same stable ID and never performs file or network access at runtime.

- [x] Write asset-generator tests that assert ten complete model/animation/texture outputs and valid JSON references.
- [x] Run the test first and confirm it fails because the new generated assets do not exist.
- [x] Extend the existing standard-library-only generator to create deterministic 64×64 original textures and species-specific geometry/animation JSON.
- [x] Add one dynamic GeckoLib model and renderer class and register the ten entity renderers on the client mod bus.
- [ ] Run generator tests and all QA tests against the latest branch head.
- [ ] Commit as `feat(lotm): add original GeckoLib creature assets`.

### Task 4: Add habitat spawns and remove custom relic-site placement

**Files:**
- Create: ten JSON files under `neoforge/src/main/resources/data/realscale/neoforge/biome_modifier/lotm_<id>_spawns.json`
- Modify: `neoforge/src/main/resources/data/realscale/neoforge/biome_modifier/lotm_relic_site.json` to disable it with `neoforge:none` while preserving its stable path
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmConfig.java`
- Modify: `docs/lotm-rpg-alchemy-system.md`
- Create: `neoforge/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json`
- Create: four scoped modifiers under `neoforge/src/main/resources/data/realscale/loot_modifiers/`
- Create: four supplementary chest tables under `neoforge/src/main/resources/data/realscale/loot_table/lotm/structure/`
- Test: `qa/test_lotm_creature_assets.py` (also checks habitat spawns, relic-site shutdown and vanilla loot integration)

**Interfaces:**
- Spawn modifiers use `neoforge:add_spawns` and only intended biome IDs.
- The existing relic-cache block and feature registration may remain for old-world compatibility, but no normal biome modifier places it.
- Vanilla structure loot uses additive `neoforge:add_table` modifiers scoped by `neoforge:loot_table_id`; `replace:false` preserves existing loot.
- Supplementary loot tables contain registered LOTM materials and Sequence 9/8 characteristics only; Sequence 7 remains excluded.

- [ ] Write tests for all ten biome modifier JSON files, expected categories, group sizes, and archived-variant exclusion.
- [ ] Run tests first and confirm missing modifier files fail.
- [x] Add ten habitat biome modifiers using each kind's spawn weight and group-size metadata.
- [x] Disable the custom relic-site biome modifier without removing existing block/entity IDs or unrelated worldgen.
- [x] Add four additive, low-probability loot modifiers for vanilla dungeon, mineshaft, stronghold corridor and ancient-city chests.
- [x] Add supplementary loot tables with registered LOTM materials and rare Sequence 9/8 characteristics; preserve vanilla loot and exclude Sequence 7.
- [x] Update the RPG guide to explain that rare finds come from vanilla chest loot, not custom ruins.
- [ ] Run spawn tests, full QA tests, and the PR server smoke workflow.
- [ ] Commit as `feat(lotm): spawn occult creatures in vanilla and RealScale biomes`.

### Task 5: Publish the branch handoff and verify the complete milestone

**Files:**
- Create: `docs/LOTM_BRANCH_HANDOFF.md`
- Modify: `docs/lotm-rpg-alchemy-system.md`
- Modify: `docs/lotm-sequence9-pilot.md`

- [ ] Document branch purpose, PR, runtime dependencies, architecture, source paths, asset generator, config flags, test commands, and explicit non-goals for future agents.
- [ ] Run `./gradlew test`, `./gradlew build`, Python QA tests, and the PR server smoke workflow against the latest branch head.
- [ ] Inspect the PR diff to confirm no main-branch changes and no terrain-generation files changed.
- [ ] Commit as `docs(lotm): add creature expansion handoff`.

