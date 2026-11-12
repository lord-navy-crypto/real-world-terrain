# LOTM Creature Ecology Hardening — Design

Date: 2026-11-12  
Repository: `lord-navy-crypto/real-world-terrain`  
Target: Minecraft 1.21.1 · NeoForge 21.1.249 · Java 21 · existing GeckoLib 4 dependency  
Parent research specification: `docs/superpowers/specs/2026-11-12-lotm-system-research-hardening.md`

## Intent

Improve the existing ten independent LOTM creatures so their habitat, time-of-day rules, and signature behavior agree with their metadata and remain reliable in saved worlds. This is the first bounded implementation slice of the wider LOTM RPG hardening program.

The immediate goals are to:
- Restore a compiling, testable baseline after the prior undead-classification change used an API that does not exist in the pinned Minecraft 1.21.1 mappings.
- Make every creature marked nocturnal spawn only during the Overworld night window.
- Give nocturnal creatures a consistent day-rest / night-activity loop without turning passive species into player-hostile mobs.
- Use Minecraft's vanilla `minecraft:undead` entity-type tag for Corpse Collector detection, rather than a fragile hand-maintained list.

## Goals

1. Preserve all ten stable entity IDs, item IDs, language keys, NBT keys, and existing saved-world compatibility.
2. Keep current biome lists, spawn weights, group sizes, spawn categories, and aquatic/ground placement rules unchanged.
3. Define the night interval as normalized world day-time ticks `13000 <= floorMod(dayTime, 24000) < 23000`. This matches the current vanilla Overworld day/night boundary and makes the rule safe for absolute world-time values.
4. Apply the nocturnal time gate to every `LotmCreatureKind` whose existing metadata has `isNocturnal() == true`; non-nocturnal creatures remain time-unrestricted.
5. Keep the Night Owl non-hostile. Its daytime rest blocks routine wandering but higher-priority avoidance and defensive behavior can still run.
6. Classify vanilla undead through `EntityTypeTags.UNDEAD`, which is present in the 1.21.1 API.

## Non-goals

- No new creature species, sequences, pathways, dimensions, custom ruins, or mandatory dependencies.
- No changes to existing creature stats, spawn weights, drop rates, biome lists, or loot probabilities.
- No new spawn-cost values until density is measured in a real saved world.
- No wholesale AI rewrite, new attack mechanics, or animation asset replacement in this slice.
- No changes to RealScale terrain, WGS84 climate, world presets, Chunky, or Distant Horizons.

## Approaches considered

### A. Shared metadata-driven rules — selected

Keep `LotmCreatureKind` as the source of truth. Put pure time/habitat decisions in `LotmCreatureSpawnRules`; call the same time helper from the spawn predicate and the nocturnal AI goal. Use the vanilla entity-type tag for undead classification.

**Trade-off:** Adds a small shared helper and a goal class, but gives us one tested definition of night and avoids per-species condition duplication.

### B. Hard-code time checks in each entity and spawn predicate — rejected

This duplicates time boundaries across registration and AI code. It can drift when one path changes and is harder to test independently.

### C. Move all time rules into datapack biome modifiers — rejected

Biome modifiers select which biomes receive spawn entries; they do not express the runtime day-time predicate needed here. The existing spawn-placement event is the correct boundary for the time gate.

## Architecture and file responsibilities

- `LotmCreatureSpawnRules.java`: pure biome/time policy, including a normalized night-window helper.
- `LotmCreatureEntities.java`: applies the pure spawn policy inside the existing server-side spawn-placement predicate; keeps fluid-column and vanilla placement checks.
- `LotmCreature.java`: applies day-rest behavior to nocturnal creatures while preserving higher-priority avoidance, retaliation, and attack goals.
- `LotmSkills.java`: delegates Corpse Collector classification to `EntityTypeTags.UNDEAD`.
- `LotmCreatureSpawnRulesTest.java` and a focused `LotmSkillsTest.java`: pin boundaries and classification behavior.
- `qa/test_lotm_creature_assets.py`: existing habitat/spawn JSON assertions remain authoritative for the current ten resource files; do not duplicate that mapping in another test.

## Design decisions left to the implementer

- Use a dedicated nested `NocturnalRestGoal` with the MOVE flag. It stops navigation and horizontal/vertical drift while the normalized world time is outside the night window; it requests an every-tick update so flying creatures do not slowly drift between rest ticks. Higher-priority panic, avoidance, retaliation, and attack goals retain precedence.
- Keep `NocturnalPlayerTargetGoal` for hostile nocturnal creatures, and require both `canUse()` and `canContinueToUse()` to remain inside the same night window so a target is not retained across sunrise.
- Keep all spawn resource JSON unchanged in this slice.
- If the JUnit environment does not load vanilla entity-type tag contents, move the tag-membership assertion to a loader-aware runtime test rather than weakening production classification or inventing a parallel undead list.

## Acceptance criteria

- The prior `MobType` compile failure is gone.
- Corpse Collector's classifier recognizes tagged undead types and rejects an ordinary non-undead type in a loader-aware environment.
- All nocturnal creature types pass the night time gate at 13000 and 22999 and fail it at 12999 and 23000; absolute day times wrap correctly.
- Non-nocturnal creatures retain their current all-day spawn behavior.
- Spawn checks still enforce configuration, biome membership, water columns for aquatic creatures, and vanilla ground placement for land creatures.
- Day-rest behavior does not make passive nocturnal creatures hostile and does not suppress higher-priority avoidance/defense; hostile nocturnal target goals stop continuing when daylight begins.
- The full JUnit suite, Python source/resource tests, Gradle 8.14.3 build, Gradle 9.8 compatibility lane, and dedicated-server boot all pass on the final branch head.
- Sequence 7 remains archived and untouched.
