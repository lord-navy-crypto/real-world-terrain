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

- [ ] **Step 1: Restore the last known compiling classification as a temporary baseline**

  Replace the unsupported `MobType` import and `LivingEntity#getMobType()` call with the prior three-class predicate (`Zombie`, `Skeleton`, `Phantom`). This is a narrow rollback of the uncompiled API attempt, not the final implementation.

- [ ] **Step 2: Add the failing tag-classification test**

  Add `LotmSkillsTest.usesVanillaUndeadEntityTypeTagForClassification`. Assert that zombie, skeleton, phantom, and wither entity types are classified as undead; assert that cow and creeper are not; assert that null is rejected.

- [ ] **Step 3: Run the focused test and confirm RED**

  Run from `neoforge/`:

  ```bash
  ./gradlew test --tests org.realscale.neoforge.lotm.LotmSkillsTest
  ```

  Expected: test compilation fails because `LotmSkills.isUndeadType(EntityType<?>)` has not yet been implemented. The main source set must compile.

- [ ] **Step 4: Implement the tag-based classifier**

  Add `static boolean isUndeadType(EntityType<?> type)` to `LotmSkills`, returning false for null and otherwise checking `type.is(EntityTypeTags.UNDEAD)`. Make `isUndead(LivingEntity entity)` delegate to `isUndeadType(entity.getType())`.

- [ ] **Step 5: Run the focused test and confirm GREEN**

  Run the same command. Expected: all assertions pass. If vanilla tags are not loaded in the plain JUnit environment, retain the tag-based production implementation and move the membership assertion to a loader-aware test; do not substitute a hand-maintained entity list.

- [ ] **Step 6: Commit Task 1**

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

- [ ] **Step 1: Add boundary tests before implementation**

  Add tests asserting:
  - `isNocturnalActive(12999)` is false.
  - `isNocturnalActive(13000)` is true.
  - `isNocturnalActive(22999)` is true.
  - `isNocturnalActive(23000)` is false.
  - Absolute times `37000` and `47000` wrap to the same night/day outcomes as their normalized values.
  - `canSpawnAt(NIGHT_OWL, "minecraft:forest", 14000)` is true and the same call at `6000` is false.
  - A non-nocturnal creature in its valid biome can still spawn at `6000`.
  - Null kind, blank biome, and unrelated biome fail closed.

- [ ] **Step 2: Run the focused test and confirm RED**

  Run:

  ```bash
  ./gradlew test --tests org.realscale.neoforge.lotm.LotmCreatureSpawnRulesTest
  ```

  Expected: compile or assertion failure because the new helper methods do not exist yet.

- [ ] **Step 3: Implement the pure time policy**

  In `LotmCreatureSpawnRules`, normalize absolute world time with `Math.floorMod(dayTime, 24000L)`. Define the night interval as `>= 13000L && < 23000L`. `canSpawnAt` must first require the existing biome predicate, then require the night interval only when `kind.isNocturnal()` is true.

- [ ] **Step 4: Wire the actual spawn predicate**

  In `LotmCreatureEntities.registerSpawnPlacements`, obtain world time through the pinned 1.21.1 `ServerLevelAccessor#getLevel().getDayTime()` API and call `canSpawnAt`. Preserve config checks, current biome check semantics, aquatic water-column validation, and `Mob.checkMobSpawnRules` for ground entities.

- [ ] **Step 5: Run the focused test and confirm GREEN**

  Run the same focused command. Expected: all boundary, wrap, habitat, and diurnal/nocturnal assertions pass.

- [ ] **Step 6: Commit Task 2**

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

- [ ] **Step 1: Add the behavior-policy test**

  Extend `LotmCreatureBehaviorTest` to assert that the existing nocturnal species (Nightmare Eye, Night Owl, Shroudman, Death Crow) are the only roster entries with nocturnal metadata; keep the existing unique signature-profile assertions.

- [ ] **Step 2: Run the focused behavior test**

  Run:

  ```bash
  ./gradlew test --tests org.realscale.neoforge.lotm.LotmCreatureBehaviorTest
  ```

  Expected: PASS for the existing roster and signature-profile contract before adding the goal.

- [ ] **Step 3: Add the daytime rest goal**

  Add a nested `NocturnalRestGoal` with the MOVE flag. It can start and continue while `!LotmCreatureSpawnRules.isNocturnalActive(creature.level().getDayTime())`; its tick stops navigation. Register it at priority 4 only for nocturnal kinds. Preserve the higher-priority attack, hurt-by-target, avoidance, and panic goals.

- [ ] **Step 4: Run the full JUnit suite**

  Run:

  ```bash
  ./gradlew test
  ```

  Expected: all existing and new tests pass.

- [ ] **Step 5: Commit Task 3**

  ```bash
  git add neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreature.java neoforge/src/test/java/org/realscale/neoforge/lotm/LotmCreatureBehaviorTest.java
  git commit -m "feat(lotm): add daytime rest behavior for nocturnal creatures"
  ```

### Task 4: Full regression and evidence update

**Files:**
- Modify: `docs/superpowers/specs/2026-11-12-lotm-system-research-hardening.md`
- Modify: `docs/superpowers/specs/2026-11-12-lotm-creature-ecology-design.md`
- Modify: this plan's progress section / implementation ledger.

- [ ] **Step 1: Run the complete Java and Python test suites**

  Run from `neoforge/`:

  ```bash
  ./gradlew test build
  ```

  Run from repository root:

  ```bash
  python3 -m unittest discover -s qa -p "test_*.py"
  ```

  Expected: both commands exit 0.

- [ ] **Step 2: Verify all CI lanes on the final branch head**

  Confirm fresh successful runs for:
  - Source integrity, tests, and NeoForge build.
  - Gradle 9.8 client task and NeoForge build compatibility.
  - NeoForge dedicated server and saved-block verification.

  Expected: all three workflows complete successfully on the exact final HEAD SHA. A successful server boot alone does not make the full branch green.

- [ ] **Step 3: Record evidence and known limits**

  Update the parent spec and progress ledger with exact commit SHA, test counts, workflow run IDs, and any deferred issues. Distinguish unit-tested, statically validated, runtime-verified, and player-playtested behavior.

- [ ] **Step 4: Commit documentation**

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
