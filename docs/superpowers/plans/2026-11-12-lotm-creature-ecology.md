# LOTM Creature Ecology Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the current ten-creature ecology so nocturnal species spawn and behave at night, Corpse Collector detects vanilla undead through the canonical entity-type tag, and the project builds cleanly on its pinned runtime.

**Architecture:** Keep `LotmCreatureKind` as the source of truth. Put pure time and habitat decisions in `LotmCreatureSpawnRules`, then consume them from both the spawn predicate and AI goals. Use `EntityTypeTags.UNDEAD` for undead classification; do not duplicate vanilla's tag contents.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, GeckoLib 4, JUnit 5, Python 3 QA scripts.

**Spec:** `docs/superpowers/specs/2026-11-12-lotm-creature-ecology-design.md`

## Global Constraints

- Integrate into the existing RealScale NeoForge mod; do not create a separate mod or repository.
- Target Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21 / existing GeckoLib 4 dependency.
- Keep the four active pathways and ten independent creature EntityTypes; preserve their stable IDs.
- Active gameplay remains Sequence 9 and Sequence 8; Sequence 7 stays archived.
- Preserve stable registry IDs, NBT keys, saved worlds, RealScale terrain, and WGS84 climate logic.
- Keep biome lists, spawn weights, group sizes, item IDs, drops, and vanilla structure loot unchanged in this plan.
- Do not add a mandatory dependency, custom ruin, relic structure, or new dimension.
- Do not merge PR #155 or change `main` without explicit user approval.
- Every build/test claim must come from a fresh run against the final branch head.

## Review Focus

1. **Absolute and wrapped world time:** test dayTime values before, at, and after both boundaries, including values greater than 24000 and negative values.
2. **Mixed nocturnal and diurnal roster:** verify all metadata-marked nocturnal species are gated while every non-nocturnal species remains unrestricted.
3. **Habitat and placement interaction:** a correct night time must not bypass biome, fluid-column, or vanilla ground-placement checks.
4. **AI priority interaction:** daytime rest must not block higher-priority flee/avoidance, retaliation, or combat behavior.
5. **Undead tag loading:** confirm vanilla tag membership in the actual 1.21.1 runtime; if JUnit lacks loaded tag contents, use a loader-aware runtime assertion rather than a hard-coded fallback list.

---

### Task 1: Restore compile baseline and use the vanilla undead entity-type tag

**Files:**
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmSkills.java`
- Create: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmSkillsTest.java`
- Test: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmSkillsTest.java`

**Interfaces:**
- Consumes: Minecraft 1.21.1 `EntityTypeTags.UNDEAD` and `EntityType#is(TagKey)`.
- Produces: package-visible `static boolean isUndeadType(EntityType<?> type)`; private `isUndead(LivingEntity)` delegates to it.

- [x] **Step 1: Restore the last known compiling classification as a temporary baseline**

  Replace the unsupported `MobType` import and `LivingEntity#getMobType()` call with the prior three-class predicate (`Zombie`, `Skeleton`, `Phantom`). This is a narrow rollback of the uncompiled API attempt, not the final implementation.

- [x] **Step 2: Add the null-guard test**

  Add `LotmSkillsTest.nullEntityTypeIsRejectedBeforeVanillaTagLookup`. The JUnit test covers null safety only; vanilla tag membership is checked by the existing source/resource QA and still needs a loader-aware runtime assertion.

- [ ] **Step 3: Record the RED run**

  A pre-implementation focused RED run was not captured. Do not claim a complete TDD red-green cycle for this classifier.

- [x] **Step 4: Implement the tag-based classifier**

  Add `static boolean isUndeadType(EntityType<?> type)` to `LotmSkills`, returning false for null and otherwise checking `type.is(EntityTypeTags.UNDEAD)`. Make `isUndead(LivingEntity entity)` delegate to `isUndeadType(entity.getType())`.

- [x] **Step 5: Verify the available JUnit guard and static tag contract**

  On final head `e510ef3c361e11b4a68b2790b56851265771705b`, `LotmSkillsTest.nullEntityTypeIsRejectedBeforeVanillaTagLookup()` passed. The Python QA test `test_corpse_collector_uses_the_vanilla_undead_entity_type_tag` also passed and checks the production call against `EntityTypeTags.UNDEAD`. Runtime membership for Wither, Skeleton, Zombie, and other tagged entities is not yet verified in a loaded server tag registry.

- [x] **Step 6: Commit Task 1**

  ```bash
  git add neoforge/src/main/java/org/realscale/neoforge/lotm/LotmSkills.java neoforge/src/test/java/org/realscale/neoforge/lotm/LotmSkillsTest.java
  git commit -m "fix(lotm): classify undead through vanilla entity tags"
  ```

### Task 2: Add one shared, tested nocturnal spawn-time policy

**Files:**
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRules.java`
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureEntities.java`
- Test: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRulesTest.java`

**Interfaces:**
- Produces: `static boolean isNocturnalActive(long dayTime)` and `static boolean canSpawnAt(LotmCreatureKind kind, String biomeId, long dayTime)`.
- Consumes: existing `canSpawnInBiome(LotmCreatureKind, String)` and `LotmCreatureKind.isNocturnal()`.

- [x] **Step 1: Add boundary tests before implementation**

  Add tests asserting:
  - `isNocturnalActive(12999)` is false.
  - `isNocturnalActive(13000)` is true.
  - `isNocturnalActive(22999)` is true.
  - `isNocturnalActive(23000)` is false.
  - Absolute times `37000` and `47000` wrap to the same night/day outcomes as their normalized values.
  - `canSpawnAt(NIGHT_OWL, "minecraft:forest", 14000)` is true and the same call at `6000` is false.
  - A non-nocturnal creature in its valid biome can still spawn at `6000`.
  - Null kind, blank biome, and unrelated biome fail closed.

- [ ] **Step 2: Record the RED run**

  A pre-implementation focused RED run was not captured. The boundary tests were present before the production helper was wired, but this plan does not claim a verified RED run.

- [x] **Step 3: Implement the pure time policy**

  In `LotmCreatureSpawnRules`, normalize absolute world time with `Math.floorMod(dayTime, 24000L)`. Define the night interval as `>= 13000L && < 23000L`. `canSpawnAt` must first require the existing biome predicate, then require the night interval only when `kind.isNocturnal()` is true.

- [x] **Step 4: Wire the actual spawn predicate**

  In `LotmCreatureEntities.registerSpawnPlacements`, obtain world time through the pinned 1.21.1 `ServerLevelAccessor#getLevel().getDayTime()` API and call `canSpawnAt`. Preserve config checks, current biome check semantics, aquatic water-column validation, and `Mob.checkMobSpawnRules` for ground entities.

- [x] **Step 5: Verify the focused tests in the full JUnit suite**

  On final head `e510ef3c361e11b4a68b2790b56851265771705b`, the boundary, wrap, habitat, and diurnal/nocturnal tests all passed in the 102-test JUnit run.

- [x] **Step 6: Commit Task 2**

  ```bash
  git add neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRules.java neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreatureEntities.java neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureSpawnRulesTest.java
  git commit -m "feat(lotm): gate nocturnal creature spawns by world time"
  ```

### Task 3: Give nocturnal creatures a daytime rest state

**Files:**
- Modify: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreature.java`
- Test: `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureBehaviorTest.java`
- Verify: dedicated-server workflow and existing entity behavior/resource checks.

**Interfaces:**
- Consumes: `LotmCreatureSpawnRules.isNocturnalActive(long)`.
- Produces: nested `NocturnalRestGoal` in `LotmCreature`; no new public API.

- [x] **Step 1: Add the behavior-policy test**

  Extend `LotmCreatureBehaviorTest` to assert that the existing nocturnal species (Nightmare Eye, Night Owl, Shroudman, Death Crow) are the only roster entries with nocturnal metadata; keep the existing unique signature-profile assertions.

- [ ] **Step 2: Record the pre-implementation focused run**

  The focused behavior test was not run before adding the goal. The resulting roster and signature-profile tests passed in the full JUnit suite.

- [x] **Step 3: Add the daytime rest goal**

  Add a nested `NocturnalRestGoal` with the MOVE flag. It can start and continue while `!LotmCreatureSpawnRules.isNocturnalActive(creature.level().getDayTime())`; its tick stops navigation and zeroes drift, and it returns true from `requiresUpdateEveryTick()`. Register it at priority 4 only for nocturnal kinds. Update `NocturnalPlayerTargetGoal` so both `canUse()` and `canContinueToUse()` require the shared night window; this releases player targets at sunrise. Preserve the higher-priority attack, hurt-by-target, avoidance, and panic goals.

- [x] **Step 4: Run the full JUnit suite**

  Run:

  ```bash
  ./gradlew test
  ```

  Verified on final head `e510ef3c361e11b4a68b2790b56851265771705b`: 102 JUnit tests passed under both Gradle 8.14.3 and Gradle 9.8.

- [x] **Step 5: Commit Task 3**

  ```bash
  git add neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreature.java neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureBehaviorTest.java
  git commit -m "feat(lotm): add daytime rest behavior for nocturnal creatures"
  ```

### Task 4: Full regression and evidence update

**Files:**
- Modify: `docs/superpowers/specs/2026-11-12-lotm-system-research-hardening.md`
- Modify: `docs/superpowers/specs/2026-11-12-lotm-creature-ecology-design.md`
- Modify: this plan's progress section / implementation ledger.

- [x] **Step 1: Run the complete Java and Python test suites**

  Run from `neoforge/`:

  ```bash
  ./gradlew test build
  ```

  Run from repository root:

  ```bash
  python3 -m unittest discover -s qa -p "test_*.py"
  ```

  Verified on final head `e510ef3c361e11b4a68b2790b56851265771705b`: the source-integrity workflow completed successfully, with 102 JUnit tests passing, 301 Python tests passing across the workflow log, and both LOTM-specific resource suites passing (20 tests total).

- [x] **Step 2: Verify the LOTM-relevant CI lanes on the final branch head**

  Confirm fresh successful runs for:
  - Source integrity, tests, and NeoForge build.
  - Gradle 9.8 client task and NeoForge build compatibility.
  - NeoForge dedicated server and saved-block verification.

  Verified on exact final HEAD `e510ef3c361e11b4a68b2790b56851265771705b`:
  - Source integrity, tests and NeoForge build: run `43245209159` — success.
  - Gradle 9.8 client-task discovery, JUnit and full NeoForge build: run `43245208204` — success.
  - NeoForge dedicated-server and saved-block verification: run `43245208237` — success.

  One separate, non-LOTM APES 16-biome gallery workflow (`43245208196`) failed after the worldgen process stalled at 51% spawn preparation and its local RCON socket closed. It is recorded as an unrelated repository-level failure; it does not invalidate the LOTM-focused test/build evidence, but the whole PR workflow set is not completely green.

- [x] **Step 3: Record evidence and known limits**

  Updated the parent spec and this ledger with exact commit SHA, test counts, workflow run IDs, and known limits. Unit tests and static resource checks are green; a dedicated server booted; actual nocturnal spawn counts/day-night AI transitions and runtime undead-tag membership have not yet been player- or GameTest-verified.

- [x] **Step 4: Commit documentation**

  ```bash
  git add docs/superpowers/specs/2026-11-12-lotm-system-research-hardening.md docs/superpowers/specs/2026-11-12-lotm-creature-ecology-design.md docs/superpowers/plans/2026-11-12-lotm-creature-ecology.md
  git commit -m "docs(lotm): record creature ecology verification"
  ```

## Future sub-projects (separate plans, not part of this plan)

1. Non-ore occult materials and ore geology/world generation.
2. Four-pathway ability identity, progression, acting rewards, and anti-farming.
3. Alchemy recipe matching, apparatus persistence, potion variants, and characteristic instability.
4. Loot economy simulation and sequence progression balance.
5. Creature-specific models, textures, animations, and bilingual item/GUI polish.

Each sub-project gets its own design/spec and implementation plan so every slice can be tested and reviewed independently.


## Progress ledger — 2026-11-12

**Working branch:** `feature/lotm-sequence9-pilot-20261111`  
**Pull request:** #155 (open; do not merge without approval)

### Implemented on the branch

- Recovered the compile baseline by removing the unsupported `MobType#getMobType` attempt.
- Replaced the temporary three-class Corpse Collector classifier with `EntityTypeTags.UNDEAD`; added a focused JUnit contract test.
- Added a normalized 24,000-tick day clock and the shared night window `[13000, 23000)`.
- Applied the same habitat/time rule in the actual entity spawn-placement predicate, preserving aquatic water-column and vanilla ground-placement checks.
- Added a daytime rest goal for the four metadata-marked nocturnal creatures. The goal updates every tick, stops navigation and drift, and yields to higher-priority panic/avoidance/retaliation/attack goals.
- Updated hostile nocturnal target selection to stop continuing at sunrise.
- Added tests for time boundaries, absolute-time wrapping, diurnal/nocturnal behavior, and the four-species nocturnal roster.

### Verification status

- Tests were authored before their corresponding feature implementations, but the focused RED/GREEN commands have **not** yet been observed on the current branch head.
- A Gradle 9.8 test-and-build workflow succeeded on the earlier recovery commit `e7141d3`; that was before the new tag/time/AI changes and is not evidence that the current code builds.
- The source-integrity and dedicated-server runs for that earlier recovery head were cancelled when the branch advanced.
- No fresh successful workflow results have been observed for the current creature-ecology implementation. Treat Java tests, full build, dedicated-server boot, and in-world behavior as **pending verification**.
- The vanilla undead tag-membership test may require a loader-aware environment if the plain JUnit runtime does not load vanilla entity tags. If it fails for that reason, move only the membership assertion to runtime verification; keep the production tag-based classifier.

### Next required action

Run the focused JUnit tests, the complete `./gradlew test build`, Python QA, Gradle 9.8 compatibility, and dedicated-server verification against the exact current HEAD. Do not begin spawn-cost tuning until natural density has been measured in a saved world.

## Implementation ledger

- Code baseline recovery: `e7141d3dce6de9cf2ac406e5b0afd85a03ce47ac`.
- Shared nocturnal spawn policy, entity spawn predicate, behavior goal, and tests were committed on the feature branch before the verified code snapshot `e510ef3c361e11b4a68b2790b56851265771705b`.
- Verification record and design updates: `ab1d34a790c9d835811360950176bffcf22fb391` (parent research spec) and `93b1d201e95d156ae0ae1f3bc3b358c7c669aa1c3` (creature design spec).
- TDD RED runs were not captured for Tasks 1–3; do not describe these changes as a complete red-green implementation cycle.
- Runtime undead-tag membership and real-world spawn/day-night observations remain open. No PR merge or main-branch change was performed.