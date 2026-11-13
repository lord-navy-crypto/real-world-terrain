# LOTM RPG — Integrated Cross-System Expansion & Validation Plan

Date: 2026-11-14 (China Standard Time)  
Repo: `lord-navy-crypto/real-world-terrain`  
Branch: `feature/lotm-sequence9-pilot-20261111` / PR #155 (Draft)

## Contract and scope
- Integrated inside RealScale for Minecraft 1.21.1, NeoForge 21.1.249 and Java 21.
- Four pathways at Sequence 9 and Sequence 8 only: Seer, Mystery Pryer, Sleepless, Corpse Collector.
- Sequence 7 stays archived. Keep historical item IDs and world saves; no S7 recipes, progression, new spawns or structure loot.
- Preserve RealScale WGS84/climate/terrain, existing registries, vanilla structure drops, no custom ruins or dimensions.
- Preserve existing GeckoLib and Player Abilities contract; add no mandatory dependencies.
- Do not merge PR #155 into main before user authorization and full acceptance.

## Current usable systems
| System | Current active content | Status of this pass |
| --- | --- | --- |
| Creatures | 10 independent S9/S8 EntityTypes with biome-scoped spawns, signature behaviors and drops | No new species this pass; world-spawn and animation polish still open |
| Occult geology | 6 active ores (stellar/cursed/grave/lunar mica/echo quartz/abyssal salt), stone/deepslate targeting | Preserved; all 6 now count for eligible fieldwork |
| Flora | 4 active occult flowers with worldgen and loot | 4 additive botanical material recipes |
| Alchemy | 2x2 input + Spirit Ember fuel, S9/S8 normal and characteristic routes | Shapeless ingredient matching now handles overlapping tags |
| Acting/digestion | Exploration, skills, research and fieldwork, daily caps 12/8/9/6 | Stable server-game-time clock, novel type-based research and persisted player-placement anti-farming |
| Pathway skills | 2 abilities per pathway; S9/S8 scaling | Hermit secondary focuses on occult creatures; Death secondary on defense/recovery |
| Field notes | `/lotm` guide and acting commands | New journal, five guide topics, bilingual labels and hints |

## Implemented code changes this pass

### 1. Deterministic shapeless recipe matching
- `LotmIngredientMatching` tests all possible one-to-one assignments, at most 4 ingredients.
- `LotmAlchemyRecipe.matches` constructs a compatibility matrix instead of greedy slot assignment.
- Unit tests cover overlapping tags, duplicate ingredients, malformed matrices and four-item backtracking.

### 2. First-discovery occult research
- `LotmActing.recordResearchDiscovery` uses persistent per-player, per-pathway, per-sequence NBT.
- Nearby occult block and entity IDs generate unique research clues via `LotmSkills` scans.
- Repeated scans of the same entity or block type do not pay again.
- If the category cap is full, unclaimed discoveries are not consumed.
- The discovery ledger is copied on death and exposed via `/lotm journal`.

### 3. Fieldwork provenance and game-clock consistency
- `LotmResourceProvenance` is a SavedData ledger stored per dimension.
- Player placement of any eligible six ores or four flowers is recorded; breaking a tracked block consumes its mark and gives no fieldwork reward.
- The ledger is world-scoped rather than individual-player-scoped, so a second player cannot gain the same placed-block reward.
- `LotmActing` now uses the Overworld's monotonic game time for 24,000-tick reward cycles; sleeping or changing daylight time cannot instantaneously reset a cap.
- All six active ores count; creative mode does not award fieldwork.

Known limitations: previously placed blocks from old saves cannot be retrospectively distinguished; blocks destroyed by external automation or explosions can leave stale provenance entries until cleaned up. In-world testing must verify save/reload, multiplayer, chunk boundaries and event cancellation interactions.

### 4. New flora to material economy
Four optional shapeless crafts were added. None removes or changes existing recipes:

| Recipe | Key flower | Output |
| --- | --- | --- |
| `lotm_spirit_ember_midnight_bloom` | Midnight Bloom | 3 Spirit Embers |
| `lotm_occult_catalyst_face_rose` | Face Rose | 3 Occult Catalysts |
| `lotm_occult_catalyst_soul_bellflower` | Soul Bellflower | 3 Occult Catalysts |
| `lotm_spirit_ember_blood_rose` | Blood Rose | 4 Spirit Embers |

Ingredients additionally require mundane Minecraft materials and sometimes other occult minerals. Flower gathering remains meaningful for potion progression, so these alternate paths represent a tradeoff rather than free output.

### 5. Distinct pathway roles
- Mystery Pryer primary stays a geology/botanical scan; secondary now highlights supernatural creatures and studies first sightings, with short night vision.
- Corpse Collector primary still detects/weakens undead; secondary now provides a bounded protective and restorative window, instead of repeating the detection pulse.
- Seer and Sleepless paths keep their existing main/secondary identity.

### 6. Documentation, localization and quality checks
- New `/lotm journal` shows recent discovered creature/block names and research progress.
- `/lotm guide creatures|flora|minerals|alchemy|progression` provides bilingual guidance.
- `qa/test_lotm_rpg_systems_expansion.py` checks recipe and source/resource integration, wired into the PR CI workflow.
- JUnit covers ingredient assignment, placement mark consumption and stable server-game-time cycle boundaries.

## Next development batches (do not claim complete until validated)

### A. In-game and multiplayer acceptance
1. Test 6 ores across multiple seeds: sampled blocks/chunk, vertical distribution, Silk Touch, Fortune, explosion yield and tool requirements.
2. Confirm all 4 flower features actually place in expected biomes and require reasonable substrates.
3. Spawn-check all 10 creatures across night/day and dimensions; inspect animations, navigation, avoid/alert/defense behavior, drops and despawns.
4. Load existing S9/S8 worlds, place/break resources, reload the server, test a second player mining player-placed blocks.
5. Validate all 16 potion recipe variants in the actual GUI (8 normal + 8 characteristic) including fuel pause, output blocking, save/load and block-break inventory drops.
6. Inspect translated field guide, skill activation and journal persistence in real client.
7. Test the complete S9 to S8 playthrough on each pathway with normal settings.
8. Keep Sequence 7 archived and compare main's terrain/worldgen behavior for regressions.

### B. Resource balance and ecology
1. Sample creature spawn density over fixed durations and seeds before modifying weights.
2. Verify plant placement/survival and only then consider botanical harvesting tiers and non-destructive propagation.
3. Define rarity, use and drop-rate curves with expected exploration time (avoid excessive RNG).
4. Add unique non-combat behaviors, state-driven animations and original sounds for the ten existing species.
5. Plan an explicit clean-up or chunk-attachment strategy for provenance marks when resources are destroyed without a player break.

### C. Spirituality, instability and risk/reward
1. Profile spirituality regeneration and actual combat/exploration cost for each pathway.
2. Introduce configuration and a measured consequence for direct-characteristic instability only after design review; never silently change saved NBT semantics.
3. Consider optional rituals, field studies and visual cues using existing Minecraft assets; never require extra dimensions or NPC quests for the basic loop.
4. Build readable progression feedback and consistent bilingual tooltips/recipe hints.

### D. Release gate
- All current-head source/resource QA, JUnit, NeoForge 8.14.3 and Gradle 9.8 builds, dedicated server boot and saved-block verification.
- At least one tested client session for alchemy, skills, all ten creatures, all six ores and four flowers.
- No unreviewed worldgen changes, no missing textures, no S7 progression leak.
- PR remains Draft until manual gameplay tests are explicitly completed and the user requests merging.

## Evidence standard
A successful PR build proves compilation and automated contracts, not observed spawn rates, true loot behavior, graphical quality, multiplayer event timing or end-to-end gameplay. Record exact head SHA and each workflow conclusion at acceptance time.
