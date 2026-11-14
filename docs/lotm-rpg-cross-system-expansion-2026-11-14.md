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

Explosions now explicitly consume placement marks for all affected positions in one SavedData mutation, preventing stale records from surviving ordinary blast removal. Remaining limitations: previously placed blocks from old saves cannot be retrospectively distinguished; piston moves and third-party automation that bypass both player-break and explosion events may still need a future cleanup hook. In-world testing must verify save/reload, multiplayer, chunk boundaries and event cancellation interactions.

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
- Mystery Pryer primary surveys occult geology/botany; secondary highlights supernatural creatures and studies first sightings.
- Corpse Collector primary detects/weakens undead; secondary grants a bounded protective and restorative window.
- Sequence 8 passives are now distinct: Seer automatically slows descent after a meaningful fall; Mystery Pryer gains night vision in dark places; Sleepless keeps its night vision/mobility advantage; Corpse Collector resists freezing.
- These passives are server-authoritative and described in both localizations.
### 6. Documentation, localization and quality checks
- New `/lotm journal` shows recent discovered creature/block names and research progress.
- `/lotm guide creatures|flora|minerals|alchemy|progression` provides bilingual guidance.
- `qa/test_lotm_rpg_systems_expansion.py` checks recipe and source/resource integration, wired into the PR CI workflow.
- JUnit covers ingredient assignment, placement mark consumption and stable server-game-time cycle boundaries.

## Follow-on player progression, equipment and feedback pass

### 7. Functional Curios specialization
- The existing `occult_focus` Curios slot is size 1 and accepts either Spirit Focus or Divination Pendulum.
- Spirit Focus adds +1 spirituality to the configured recovery amount on each 40-server-tick recovery interval.
- Divination Pendulum, only for Seer, reduces secondary-skill cost by 4 and increases local omen radius by 2 blocks.
- Both items have bilingual effect tooltips; `/lotm status` reports the equipped specialization.
- This is a mutually exclusive choice, not two stackable buffs.

### 8. Direct-characteristic instability loop
- Directly taking a Sequence 9 characteristic now correctly initializes instability to 1; direct Sequence 8 characteristic advancement increments the existing value.
- Each instability point adds +2 spirituality to skill activation cost, capped at +8.
- A crafted Stability Sigil uses Echo Quartz, Lunar Mica, Spirit Ember and paper; consuming it reduces instability by one, only for active Sequence 9/8 players.
- The sigil is not consumed when the player has no instability. Creative players retain their item.
- Instability recovery is reversible and does not alter the legacy NBT key.

### 9. Progression feedback
- Advancement items now explain pathway, rank, prepared-vs-characteristic route and configured digestion thresholds in their tooltips.
- The first Sequence 9 characteristic is explicitly described as a path-establishing case with no prior digestion requirement; Sequence 8 characteristic still displays the configured threshold.
- `/lotm status` includes active Curios specialization and the current skill-cost surcharge.
- English and Chinese localizations and static QA contracts cover the new feedback.

### 10. Defensive state bounds and clearer advancement status
- Instability NBT is normalized into a bounded 0–100 range whenever read; direct-characteristic increments use saturating addition rather than potentially overflowing integer arithmetic.
- The skill-cost surcharge remains exactly +2 per instability point up to +8, including extreme or malformed legacy values.
- `/lotm status` now reports the configured Sequence 8 digestion thresholds for prepared potion vs. direct characteristic routes; at Sequence 8 it explicitly reminds players that Sequence 7 remains archived.
- JUnit and static QA cover negative, over-limit and `Integer.MAX_VALUE` values as well as both localized status messages.

### 11. Ability preview and activation cost parity
- `LotmPathwayAbility.getUseFailureMessage` now calculates the same effective skill cost as `LotmSkills.executeFromAbility`, including the Seer-only Divination Pendulum discount and instability surcharge.
- This removes a mismatch where an ability could appear usable in the Player Abilities interface but then fail its authoritative server activation due to the omitted costs.
- Static QA checks both call sites for the same inputs; the pure balance tests already cover the combined discount/surcharge arithmetic.

### 12. Alchemy feedback and player-facing skill telemetry
- Fuel safety: the server now rechecks the exact Spirit Ember item ID at processing time, not just slot non-emptiness. This also protects old or malformed saved inventories that may bypass normal slot insertion validation.
- The alchemy block entity now publishes a third synchronized menu datum for machine state: waiting for recipe, brewing, missing fuel, blocked output, server-disabled, or unavailable recipe.
- The screen displays the localized reason rather than leaving players to infer why processing has paused. Processing and ingredient state remain server-authoritative.
- `/lotm skills` now prints the effective spirituality cost for both slots after current instability and Curios modifiers, plus exact cooldown ticks for the active sequence.
- English/Chinese labels and static QA cover all six machine statuses and the live skill readout.

### 13. Promotion recipe matrix and progression threshold regression coverage
- Static QA now enumerates the exact active recipe filenames: 4 pathways × 2 ranks × 2 crafting routes = 16 recipes.
- Each normal alchemy recipe is checked for four distinct registered inputs; each complete-characteristic alternative is checked for three distinct registered inputs. All outputs and characteristics must have item models and bilingual display names.
- Progression JUnit now exercises the exact one-below and exact-at-threshold cases for both prepared Sequence 8 potions and direct Sequence 8 characteristics.

### 14. Runtime overhead and canceled-placement hardening
- Unauthorized Player Abilities grants are still rejected synchronously by `AbilityGrantedEvent`, and full audits still run on login/progression sync. The defensive fallback scan is now throttled to once per 20 player ticks rather than allocating/copying grant maps every tick.
- Canceled `EntityPlaceEvent` events are ignored before writing resource provenance, preventing phantom anti-farming marks when another event handler cancels placement.
- A focused JUnit test covers the audit cadence across ordinary, negative and large tick values; static QA protects event wiring and canceled-placement ordering.

### 15. Alchemy result stack invariants
- Output acceptance now validates result count against the item's maximum stack size even when the output slot is empty.
- Occupied slots require matching item/components and enough remaining capacity; count addition uses `long` arithmetic to avoid integer overflow from malformed recipe data.
- JUnit covers empty/occupied slots, component mismatches, zero/negative values, maximum stack boundaries and overflow cases.

### 16. Progression and daily acting arithmetic hardening
- Spirituality regeneration and digestion accumulation now add using `long` intermediates before clamping to their 0–100 limits.
- Daily acting counters normalize malformed negative/over-cap values before displaying or awarding progress.
- Scaled acting rewards use `long` multiplication and are capped by the category's remaining allowance, preventing integer overflow from corrupt or future scripted inputs.
- JUnit and static QA cover integer extremes and malformed counter values.

**Validation caveat:** these are code and resource changes, not yet confirmed by an in-game client or multiplayer server. Current-head workflow runs must be checked; a static QA pass cannot prove Curios equipment events, item consumption, tooltip rendering or save/reload behavior.

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
5. Validate the new explosion cleanup in a save-world test; investigate a chunk-local cleanup strategy only for remaining piston/third-party automation paths.

### C. Spirituality, instability and risk/reward
1. Profile spirituality regeneration and actual skill/exploration cost for each pathway, including the new mutually exclusive Curios bonuses.
2. Playtest the instability surcharge and Stability Sigil economy; tune only from measured usage, preserving the existing NBT key and no-free-consumption rule.
3. Consider optional rituals, field studies and visual cues using existing Minecraft assets; never require extra dimensions or NPC quests for the basic loop.
4. Continue the HUD/status experience beyond commands and item tooltips, with server-authoritative synchronization and accessibility-safe placement.

### D. Release gate
- All current-head source/resource QA, JUnit, NeoForge 8.14.3 and Gradle 9.8 builds, dedicated server boot and saved-block verification.
- At least one tested client session for alchemy, skills, all ten creatures, all six ores and four flowers.
- No unreviewed worldgen changes, no missing textures, no S7 progression leak.
- PR remains Draft until manual gameplay tests are explicitly completed and the user requests merging.

## Evidence standard
A successful PR build proves compilation and automated contracts, not observed spawn rates, true loot behavior, graphical quality, multiplayer event timing or end-to-end gameplay. Record exact head SHA and each workflow conclusion at acceptance time.
