# LOTM System Research & Hardening Plan

Date: 2026-11-12  
Repository: `lord-navy-crypto/real-world-terrain`  
Working branch: `feature/lotm-sequence9-pilot-20261111`  
Minecraft: 1.21.1 · NeoForge: 21.1.249 · Java: 21 · GeckoLib: existing GeckoLib 4 dependency

## 1. Mission

Turn the current LOTM pilot into a coherent, testable RPG expansion by researching authoritative technical references, checking the current implementation against them, and then improving gameplay design, balance, presentation, and reliability.

This is a hardening and expansion plan, not permission to replace working systems wholesale. Preserve stable registry IDs, NBT keys, existing worlds, RealScale terrain generation, WGS84 climate logic, and the current dependency contract.

## 2. Non-negotiable constraints

- Keep the feature integrated into the existing RealScale NeoForge mod; do not create a separate mod or repository.
- Target Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21. Check every code-level API against this pinned environment. Newer NeoForge docs may explain concepts but are not proof that a newer signature exists in 1.21.1.
- Keep the four active pathways: Seer, Mystery Pryer, Sleepless, and Corpse Collector.
- Keep active gameplay at Sequence 9 and Sequence 8. Sequence 7 remains archived and must not gain recipes, spawns, loot, or advancement.
- Keep the ten independent creature EntityTypes and their stable IDs.
- Do not create custom ruins, relic structures, or new mandatory dependencies.
- Vanilla structure loot must remain additive. Do not replace vanilla loot tables.
- Do not merge PR #155 or change `main` without explicit user approval.
- Every claimed build/test result must come from a fresh run against the current branch head.

## 3. Evidence hierarchy

Use evidence in this order:

1. **Pinned runtime and source code:** Minecraft 1.21.1, NeoForge 21.1.249, Java 21, and the project's resolved GeckoLib 4 artifact. Compile/runtime behavior in this exact environment is decisive for API compatibility.
2. **Official technical documentation:** NeoForge 1.21.1 documentation for registries, biome modifiers, loot modifiers, loot conditions, recipes, networking, and dedicated-server testing; GeckoLib 4 documentation for entity models and animations.
3. **Original LOTM canon and user-confirmed project decisions:** use to validate pathway identity, sequence identity, characteristic relationships, and creature concepts. Do not treat fan summaries as the only authority, and do not reproduce long copyrighted passages.
4. **Scientific and professional references:** use USGS geology for mineral formation and credible ecological sources (for example NPS, NOAA, Cornell Lab) for real-world habitat and behavior analogies. These analogies inform Minecraft behavior; they do not override LOTM canon.
5. **Game-design reasoning and playtests:** use explicit goals, counterplay, measurable outcomes, and observed gameplay rather than arbitrary stat inflation.

## 4. Initial source-backed technical findings

### NeoForge 1.21.1

- Biome modifiers are data-driven and can add/remove spawns and features. The documented resource path is `data/<modid>/neoforge/biome_modifier/<path>.json`.
- The built-in `neoforge:none` modifier can disable an existing biome modifier without deleting its stable resource path.
- `neoforge:add_spawns` uses entity type, weight, and min/max group size. A custom entity also needs a spawn restriction registered through `RegisterSpawnPlacementsEvent`; otherwise it may spawn in unsafe positions.
- NeoForge 1.21.1 documents `neoforge:add_spawn_costs` as a way to spread mobs out and reduce clustering.
- Global Loot Modifiers are designed to modify loot without replacing many vanilla tables. Their global list is at `data/neoforge/loot_modifiers/global_loot_modifiers.json`; `replace:false` is the recommended mod behavior. The built-in `neoforge:add_table` modifier and `neoforge:loot_table_id` condition support scoped supplementary loot.

### GeckoLib 4

- An AnimationController handles one concurrent animation. Multiple controllers can run together; broad locomotion should be registered before more specific action controllers.
- Server-triggered animations are supported. Triggered animations override the controller predicate while they play and are best suited to non-looping actions.
- Current locomotion/action controller separation is a sound starting point. The next pass should improve state selection, animation timing, and per-species animation coverage rather than replacing the entire renderer without evidence.

## 5. Current-code audit: priority findings

These are code-review findings to validate with focused tests and runtime playtests. They are not all confirmed player-facing defects.

### P0 — correctness and integration

1. **Seer detection versus independent creatures:** the current Seer primary skill searches for the legacy `LotmEvents.VARIANT` persistent-data key. The ten new independent `LotmCreature` entities do not inherently carry that legacy tag. Update detection to recognize both supported legacy entities and the new independent entity types.
2. **Nocturnal Night Owl behavior:** `NIGHT_OWL` is non-hostile and nocturnal. The current generic goal branch only installs the nocturnal player-target goal for hostile entities, while the spawn predicate does not impose a night-time condition. Its current nocturnal flag therefore does not implement a full nocturnal ecology. Add explicit night-time spawning/behavior and test day/night transitions.
3. **Registry test isolation:** the latest CI run shows `LotmCreatureRegistryTest.everyActiveCreatureHasOneStableRegisteredEntityIdAndSupplier` failing during `LotmCreatureEntities` class initialization with `ExceptionInInitializerError` / `IllegalArgumentException`. The dedicated-server and saved-block verification workflow passed. Investigate the test's interaction with NeoForge's `DeferredRegister` lifecycle; keep pure metadata tests independent of mod-loader bootstrap, and verify actual registry entries in a loader-aware integration test. Do not label this a Gradle 9.8 regression without separate evidence.

### P1 — gameplay correctness and design

4. **Undead classification:** Corpse Collector currently recognizes only Zombie, Skeleton, and Phantom subclasses. Review the pinned 1.21.1 entity taxonomy and use the appropriate vanilla undead type/tag classification so other undead entities are not silently excluded.
5. **Single source of truth for spawns:** `LotmCreatureKind` already stores habitats, spawn weight, and group sizes, while ten biome-modifier JSON files repeat these values. Generate the JSON from a shared manifest or add tests that compare every JSON field against the Java metadata.
6. **Spawn ecology:** add spawn-cost tuning where it improves distribution. Review category caps, light/time requirements, heightmaps, fluid columns, terrain suitability, group size, and despawn behavior. Avoid tuning weights from intuition alone; measure density in saved worlds.
7. **Species-specific AI:** `LotmCreature` currently shares most goals, with many signature traits implemented as status effects on damage or melee hit. Design explicit species states and behaviors: habitat seeking, idle/rest, alert, avoidance, defense, attack, retreat, and return-to-habitat where appropriate. Each species needs a recognizable non-combat loop, a clear gameplay identity, and readable player counterplay.
8. **Acting reward anti-exploit:** `LotmActing.onBlockBreak` awards fieldwork for breaking selected LOTM blocks/plants. Check whether player-placed copies can be broken repeatedly for rewards. Track placed blocks or otherwise award only eligible naturally generated resources.
9. **Research reward uniqueness:** check whether repeatedly detecting the same mob/block can repeatedly grant research points. Preserve daily caps, but prefer meaningful discovery/research events over repeatable scan spam.
10. **Daily clock consistency:** daily acting counters currently derive the day from the current level's day time. Verify dimension changes, sleeping, server restarts, and death/respawn cannot reset or desynchronize daily limits unexpectedly.
11. **Pathway identity:** Mystery Pryer and Corpse Collector each have two abilities with overlapping scan/reveal or undead-detection behavior. Rework the eight abilities so every pathway has a distinct primary and secondary role, with Sequence 8 being a meaningful refinement of Sequence 9 rather than only a larger radius or stronger vanilla effect.
12. **Alchemy matching:** `LotmAlchemyRecipe.matches` greedily assigns each occupied slot to the first unmatched ingredient. If recipe ingredients overlap (for example, a broad tag and a narrower ingredient), order-dependent matching may reject a valid shapeless recipe. Add overlap tests and use a correct matching algorithm or a validated ingredient ordering.
13. **Mineral/plant world generation:** `LotmWorldgen.java` currently registers the legacy relic-site feature. Before claiming that ores and plants naturally generate, audit all datapack configured/placed features and biome modifiers. Keep the relic-site modifier disabled.
14. **Characteristic instability:** verify whether the instability counter has actual gameplay consequences, clear explanations, and safe persistence, or whether it is only recorded. Design meaningful consequences only after balancing and user approval.

### P2 — presentation and polish

15. Replace procedural placeholder-looking creature art incrementally with coherent silhouettes, UV layouts, species palettes, and animation sets. Keep original assets or assets with clear licenses.
16. Add appropriate idle, locomotion, alert, flee, defend, attack, hurt, and death animation coverage where relevant. Add sound design only after behavior and animation states are stable.
17. Improve item, mineral, flower, alchemy-apparatus, and ability icons as a unified visual language.
18. Add bilingual item/entity/GUI descriptions that explain function, rarity, habitat, and pathway relevance without exposing hidden high-sequence knowledge.

## 6. Design standards

### Creature profile

Every active creature should have a reviewed profile containing:

- Stable ID, sequence, and LOTM concept reference.
- Intended gameplay role and temperament.
- Habitat, altitude/depth, time of day, weather, fluid/ground/air requirements.
- Spawn category, weight, group size, spawn placement, spawn cost, and despawn/persistence rules.
- Health, movement, sensing, defensive behavior, attack behavior, and retreat behavior.
- At least one signature behavior that is not merely a generic potion effect.
- Readable animation/audio cue and player counterplay.
- Material drop, drop rate, and links to recipes/progression.
- Unit tests and a saved-world observation checklist.

### Pathway abilities

For each of four pathways, define two abilities at Sequence 9 and their Sequence 8 improvements. Each ability must specify:

- Canonical fantasy and intended gameplay purpose.
- Target type, range, duration, cooldown, spirituality cost, and failure conditions.
- Information revealed and its uncertainty.
- Whether it is exploration, sensing, defense, mobility, control, or combat support.
- Server-side validation and multiplayer synchronization.
- Anti-farming rules for acting/research rewards.

Avoid making all paths variants of glowing, slowness, night vision, and regeneration.

### Materials and geology

For each mineral or plant, define:

- Geological/ecological analogue and authoritative source.
- Compatible RealScale biomes and world depth/altitude.
- Formation model: vein, cluster, surface deposit, sedimentary layer, hydrothermal association, or plant habitat.
- Rarity, cluster size, mining/harvesting requirements, and expected yield.
- Which creatures, alchemy recipes, and progression steps consume it.
- Compatibility with existing terrain density, biome source, WGS84 climate, and saved chunks.

USGS deposit models should inform distribution patterns, not force real-world economic geology into a fantasy setting.

### Alchemy and progression

- Keep recipes data-driven and validate every ingredient/result against registered item IDs.
- Test shapeless ingredient matching, fuel consumption, output capacity, pause/resume, recipe changes, NBT save/load, automation, and block removal.
- Make prepared potions and direct characteristic use distinct, legible routes with intentionally different risks and digestion requirements.
- Keep Sequence 7 sealed in both code and data, including loot tables, recipes, spawn lists, and commands.
- Balance progression against realistic play sessions; do not use repeatable trivial actions as the primary advancement route.

### Loot economy

The current four supplementary chest tables use one extra table roll after a low-probability trigger. The trigger probability is not the same as the chance of finding a characteristic. For each table, calculate the actual per-chest probability from the trigger chance and the characteristic entries' share of total weight.

Before changing the existing 4%, 5.5%, 7.5%, and 10% triggers:

- Verify all item IDs are registered.
- Calculate the per-chest probability for each material and each characteristic.
- Simulate large samples with deterministic seeds.
- Check expected progression time and the value of repeated chest farming.
- Keep `replace:false`, table-scoped conditions, and no Sequence 7 entries.
- Verify in a saved world that the actual chest tables and loot modifier are loaded.

## 7. Research and implementation phases

| Phase | Research and work | Acceptance gate |
|---|---|---|
| 0 | Resolve the current single JUnit failure; preserve the passing dedicated-server check | Fresh JUnit, build, Gradle 9.8, and server results |
| 1 | Complete the ten-creature design profiles and repair P0 integration issues | Every creature has unique behavior and correct detection/spawn logic |
| 2 | Rework habitat, time, movement, spawn costs, and natural density | Multi-biome saved-world test with observed spawn counts |
| 3 | Research and implement ore/plant distribution and material loops | Worldgen JSON validation, seed tests, and no terrain regression |
| 4 | Redesign the eight pathway abilities and acting/research economy | Path-specific unit tests, multiplayer tests, and anti-farming tests |
| 5 | Strengthen alchemy recipes, ingredient roles, instability, and persistence | Recipe, NBT, blocked-output, automation, and save/reload tests |
| 6 | Tune loot and progression probabilities | Deterministic loot simulation and saved-world acceptance |
| 7 | Polish original models, textures, animations, sounds, bilingual UI | Client inspection plus dedicated-server safety |
| 8 | Full regression and release candidate | Fresh CI, dedicated server, existing-world compatibility, and user playtest |

## 8. Required test matrix

- Pure JUnit tests for creature metadata, behavior mapping, spawn rules, progression, balance, recipes, and acting rewards.
- Resource tests for every biome modifier, spawn-cost modifier, loot modifier, loot table, recipe, model, texture, animation, and language key.
- Registry integration tests inside the actual NeoForge lifecycle.
- Dedicated-server boot and entity spawn tests; client-only renderer classes must not load on a dedicated server.
- Saved-world tests for new entity persistence, old item IDs, old NBT keys, player death/respawn, alchemy inventory, and world reload.
- Multi-seed and multi-biome tests for ore/plant generation and creature density.
- Multiplayer checks for server-authoritative abilities, synchronization, cooldowns, and spirituality.
- Regression checks that RealScale terrain, climate, WGS84 latitude, world presets, Distant Horizons, and Chunky behavior remain unchanged.

## 9. CI history and current verification status

### Historical failure and recovery

- At the earlier branch head `07ff6ca`, both Source Integrity and Gradle 9.8 failed because the attempted Corpse Collector classifier referenced `MobType` / `LivingEntity#getMobType()`, which are not available in the pinned Minecraft 1.21.1 API.
- The unsupported API call was reverted in commit `e7141d3`. The Gradle 9.8 workflow `43240859784` then passed client-task discovery, JUnit discovery, and the full NeoForge build on that recovery commit.
- Source Integrity and dedicated-server workflows for that intermediate recovery head were cancelled as the branch advanced. The Gradle 9.8 success is a historical baseline check, not verification of the later feature work.

### First hardening pass

The branch added LotmCreatureRegistryIds, isolated the pure registry-ID test from NeoForge's DeferredRegister lifecycle, added a registration guard, and updated Seer detection to recognize the independent LotmCreature entities. The current-head source-integrity and build workflow passed, including the LOTM registry and creature tests.

### Current creature-ecology implementation

The following changes are present on the feature branch:

- Corpse Collector classifies undead through the pinned 1.21.1 EntityTypeTags.UNDEAD tag, replacing the earlier Zombie/Skeleton/Phantom-only check.
- LotmCreatureSpawnRules normalizes absolute world time with floorMod(dayTime, 24000) and defines the night interval as [13000, 23000).
- The actual entity spawn-placement predicate applies the shared biome/time rule while preserving the existing water-column and vanilla ground-placement checks.
- All four species whose metadata marks them nocturnal receive a daytime rest goal. The Night Owl remains non-hostile; higher-priority panic, avoidance, retaliation, and attack goals remain available. Hostile nocturnal target selection stops continuing when daylight begins.
- Unit tests cover the exact night boundaries, wrapped absolute time, habitat/time interaction, and the four-species nocturnal roster.
- The Corpse Collector Java unit test currently checks the null guard only. A Python source/resource test confirms the production implementation uses EntityTypeTags.UNDEAD; actual tag membership still needs a loader-aware runtime assertion.

### Current-head verification evidence

All references below are for exact feature head e510ef3c361e11b4a68b2790b56851265771705b (2026-11-12):

| Check | Workflow run | Result |
|---|---:|---|
| Source integrity, LOTM resource checks, 102 JUnit tests, full NeoForge build | 43245209159 | **Passed** |
| Gradle 9.8 client-task discovery, JUnit suite, full build | 43245208204 | **Passed** |
| NeoForge dedicated-server boot and saved-block verification | 43245208237 | **Passed** |
| LOTM Python resource suites | Included in 43245209159 | **20 tests passed** |

The source-integrity workflow log contains 102 passing JUnit tests and 301 passing Python test cases across its test steps. The two LOTM-specific Python suites contributed 20 passing tests. The Gradle 9.8 workflow independently ran JUnit and the full NeoForge build successfully. The dedicated-server workflow also booted successfully.

**Known unrelated CI failure:** APES 16-biome gallery workflow 43245208196 failed while preparing its separate worldgen test: spawn preparation remained at 51%, then its local RCON socket closed. This is not a LOTM test, but means the complete repository workflow set on that head was not entirely green.

**Still not runtime-verified:** actual nocturnal spawn frequency, dawn/sunrise AI transitions, and loaded-registry undead tag membership have not yet been checked in a targeted in-world/GameTest scenario. The successful dedicated-server boot proves integration/startup, not those specific gameplay outcomes.

## 9.1. Research and implementation documents

- Parent roadmap and evidence hierarchy: `docs/superpowers/specs/2026-11-12-lotm-system-research-hardening.md`.
- Creature ecology design: `docs/superpowers/specs/2026-11-12-lotm-creature-ecology-design.md`.
- Step-by-step creature ecology implementation plan and progress ledger: `docs/superpowers/plans/2026-11-12-lotm-creature-ecology.md`.

## 10. Primary references

### Pinned technical references

- NeoForge 1.21.1 — Biome Modifiers: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- NeoForge 1.21.1 — Global Loot Modifiers: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/glm/
- NeoForge 1.21.1 — Loot Conditions: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/lootconditions/
- NeoForge 1.21.1 — Registries: https://docs.neoforged.net/docs/1.21.1/concepts/registries/
- NeoForge 1.21.1 — Getting Started and dedicated-server testing: https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- GeckoLib 4 — Animation Controller: https://github.com/bernie-g/geckolib/wiki/The-Animation-Controller-(Geckolib4)
- GeckoLib 4 — Triggerable Animations: https://github.com/bernie-g/geckolib/wiki/Triggerable-Animations-(Geckolib4)
- NeoForge 1.21.1 generated Javadocs — `EntityTypeTags.UNDEAD`: https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/minecraft/tags/entitytypetags
- NeoForge 1.21.1 generated Javadocs — `Goal` and `Goal.Flag.MOVE`: https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/minecraft/world/entity/ai/goal/goal
- Minecraft 1.21.1 mappings — `ServerLevelAccessor#getLevel()`: https://mappings.dev/1.21.1/net/minecraft/world/level/ServerLevelAccessor.html

### Scientific and ecological references

- U.S. Geological Survey — Porphyry and epithermal mineral deposits: https://www.usgs.gov/publications/porphyry-and-epithermal-mineral-deposits
- U.S. Geological Survey — Physical hydrogeology of ore deposits: https://www.usgs.gov/publications/physical-hydrogeology-ore-deposits
- U.S. National Park Service — Mountain Goat Resource Brief: https://www.nps.gov/articles/mountain-goats.htm
- Cornell Lab of Ornithology — Eastern Screech-Owl life history: https://www.allaboutbirds.org/guide/Eastern_Screech-Owl/lifehistory
- NOAA — Green Sea Turtle species reference: https://repository.library.noaa.gov/view/noaa/35695/noaa_35695_DS1.pdf

## 11. Change policy

Every implementation phase must update this plan with:

- Sources consulted and what each source changed in the design.
- Files changed and stable IDs preserved.
- Tests added and exact test results.
- Known limitations and unresolved questions.
- A clear distinction between implemented, statically validated, runtime verified, and playtested behavior.

No phase is considered complete solely because code was committed.
