# LOTM RPG Branch Handoff

## 2026-11-14 cross-system update (newest handoff)

Before continuing, read `docs/lotm-rpg-cross-system-expansion-2026-11-14.md`. This adds an actual development and validation record beyond the 2026-11-12 creature-only snapshot below.

- Alchemy: bounded backtracking ingredient matching in `LotmIngredientMatching`, with dedicated JUnit tests. Broad item-tag overlap no longer relies on greedy slot order.
- Acting: research now rewards one novel discovered entity/block ID per pathway and sequence; discoveries survive death and have a read-only `/lotm journal`. Progression's daily cap uses the Overworld server game-time cycle, not mutable daylight time.
- Fieldwork: all six active ores are eligible. `LotmResourceProvenance` persists player-placed eligible resource coordinates per dimension. Breaking marked placed blocks yields no fieldwork reward; provenance tests added.
  Accepted explosions bulk-remove provenance marks in `ExplosionEvent.Detonate`; cancellation belongs to `ExplosionEvent.Start` (which prevents detonation), since `Detonate` is non-cancellable. Piston/third-party automation handling remains a runtime follow-up.
- Pathways: Mystery Pryer secondary studies supernatural creatures; Corpse Collector secondary is a bounded self-protection/recovery ability. The other pathway roles remain unchanged.
- Flora: four additional optional crafting-table recipes turn each existing occult flower into catalysts or Spirit Embers; no existing recipe removed.
- Guide: `/lotm guide creatures|flora|minerals|alchemy|progression`; journal and guide text translated into English and Chinese.
- CI: `qa/test_lotm_rpg_systems_expansion.py` added and wired into `.github/workflows/ci.yml`.
- Creature spawn contract: `LotmCreatureRegistryTest` now parses all ten biome-modifier resources and asserts exact parity with `LotmCreatureKind` habitats, weights and group sizes; this protects data-driven spawn configuration from metadata drift but does not replace in-world spawn-rate testing.
- Mineral generation guard: the existing active-ore QA now locks exact biome lists and ordered placement modifiers for Star Crystal, Cursed Crystal and Grave Crystal, while preserving the Spirit Quartz neoforge:none archive gate; this is static data validation, not a measured vein-density test.

- Equipment: `LotmCurios` activates the existing size-1 occult_focus slot. Spirit Focus adds +1 spirituality per configured 40-tick recovery interval; Divination Pendulum gives Seer-only secondary-skill -4 cost and +2-block omen radius. The items are mutually exclusive and have bilingual tooltips.
- Instability: direct Sequence 9 characteristic use now correctly starts at instability 1; direct Sequence 8 characteristic use increments it. `LotmBalance` adds +2 skill cost per instability, capped at +8. A crafted `lotm_stability_sigil` (Echo Quartz + Lunar Mica + Spirit Ember + paper) removes one point; it is not consumed when no instability exists.
- Progression feedback: `LotmPotionItem` tooltips explain pathway/rank and configured digestion requirements, including the special first Sequence 9 characteristic case. `/lotm status` reports equipped focus and the current surcharge.
- State hardening: instability reads are normalized to 0–100 and direct-characteristic increments use saturating arithmetic; the +8 skill-cost surcharge cannot overflow. `/lotm status` now shows live configured Sequence 8 digestion thresholds and the Sequence 7 archive boundary in both languages.
- Ability UI parity: `LotmPathwayAbility` pre-checks now include the same Seer Pendulum discount and instability surcharge as server activation, preventing misleading 'available' states; QA guards both code paths.
- Alchemy UX: the machine/menu now synchronize six explicit states (no recipe, brewing, missing fuel, blocked output, disabled, unavailable recipe); the screen shows localized reason text. `/lotm skills` reports effective cost after modifiers and cooldown ticks for both slots.
  Processing rechecks the Spirit Ember item ID even after inventory NBT load, preventing malformed/legacy fuel contents from being consumed as fuel.
- Promotion matrix QA: the resource graph now requires exactly 16 active S9/S8 alchemy recipe files, validates distinct registered ingredients, result models and bilingual names; progression JUnit checks exact configured threshold edges for both routes.
- Runtime efficiency: unauthorized ability grants remain blocked immediately by the grant event, while the defensive full-grant audit runs once per 20 player ticks and on login/progression sync. Resource place/break listeners run at `EventPriority.LOWEST` with `receiveCanceled=true`, so earlier cancellations are observed before provenance changes or fieldwork rewards.
- Alchemy output safety: the server rejects result stacks larger than their max stack size and uses long arithmetic for occupied-output capacity; JUnit covers full, mismatched and overflow cases.
- State arithmetic: spirituality and digestion additions use bounded long arithmetic; daily acting counters normalize corrupt values and scaled rewards cannot overflow or exceed remaining daily caps.
- Sequence 8 passives: Seer now receives brief server-side slow falling after a meaningful fall; Mystery Pryer receives night vision in dark areas; Sleepless retains its night advantage and Corpse Collector retains freezing resistance. English/Chinese skill descriptions and QA checks were updated.
- New files: `LotmCurios.java`, `LotmStabilitySigilItem.java`, `lotm_stability_sigil.json` and its model/texture generation entry. JUnit and `qa/test_lotm_rpg_systems_expansion.py` cover balance, recipe, localizations and source integration.

**Important verification caveats:** Treat all new changes as implemented but unaccepted until Source Integrity / Gradle 9.8 / dedicated server checks on the latest exact PR head have passed. Even a successful build does not prove actual save-world ore/plant spawn rates, client GUI, visual assets, anti-farming persistence across world reload, multiplayer placement, or complete gameplay. The source-only material economy and high-level QA remain separate from end-to-end tests. No merge to `main` is authorized. Sequence 7 remains archived.


> This file is the entry point for any AI or developer continuing the RealScale LOTM work. Read this before editing the branch.

## Branch and repository

- Repository: `lord-navy-crypto/real-world-terrain`
- Working branch: `feature/lotm-sequence9-pilot-20261111`
- Pull request: #155, targeting `main`
- Mod ID: `realscale`
- Minecraft: 1.21.1
- NeoForge: 21.1.249
- Java: 21
- GeckoLib: existing required GeckoLib 4 dependency
- Do not merge to `main` unless the user explicitly asks after verification.

## Product scope

This is an integrated RealScale RPG feature inspired by Lord of the Mysteries. It is not a separate mod. The initial playable progression is four pathways at Sequence 9 and Sequence 8:

- Seer / Fool pathway
- Mystery Pryer / Hermit pathway
- Sleepless / Darkness pathway
- Corpse Collector / Death pathway

Sequence 7 IDs and documentation are retained for save compatibility and future work, but Sequence 7 gameplay remains archived.

## User's current direction

1. Complete the ten active supernatural creatures first.
2. Then expand creature materials, minerals, alchemy/potions, skills, and supernatural energy.
3. Keep the current milestone RPG-focused. Do not create new ruins or custom structures.
4. Use additive global loot modifiers on selected vanilla Minecraft structures for rare materials and characteristics; never create custom ruins or overwrite vanilla loot.
5. Preserve RealScale terrain generation and existing worlds.

## Existing LOTM code map

- `LotmPath.java`: four active pathway IDs.
- `LotmState.java`: player pathway, sequence, spirituality, digestion and persistent state.
- `LotmProgression.java`: potion/characteristic advancement.
- `LotmActing.java`: digestion/acting rewards and caps.
- `LotmSkills.java` and `LotmPathwayAbility.java`: current skill definitions and execution.
- `LotmPlayerAbilityRegistry.java` / `LotmPlayerAbilitySync.java`: Player Abilities integration.
- `LotmContent.java`: stable items, blocks, materials, potions, and characteristics.
- `LotmAlchemyContent.java`, `LotmAlchemyBlockEntity.java`, `LotmAlchemyRecipe.java`: alchemy apparatus and recipes.
- `LotmEcologyRules.java`: active/archived legacy variant roster and stable material drop IDs.
- `LotmCreatureKind.java` / `LotmCreatureSpawnRules.java`: ten active independent creature definitions and pure habitat checks.
- `LotmCreatureEntities.java` / `LotmCreature.java` / `LotmCreatureBehavior.java`: ten registered EntityTypes, attributes, movement, AI and signature behavior profiles.
- `LotmEvents.java`: server-side legacy compatibility, drops, player tick and clone hooks.
- `LotmWorldgen.java` / `LotmRelicSiteFeature.java`: legacy relic-site feature remains registered for compatibility, but its biome modifier is disabled (`neoforge:none`) and it must not be re-enabled.
- `scripts/generate_lotm_visuals.py`: deterministic, standard-library-only generation of item/block/ability textures plus ten creature texture/geometry/animation bundles.
- `neoforge/src/main/java/org/realscale/neoforge/lotm/client`: shared GeckoLib creature model/renderer and alchemy model/renderer.
- `neoforge/src/main/resources/data/realscale/neoforge/biome_modifier/lotm_*_spawns.json`: ten habitat-limited natural spawn modifiers.
- `data/neoforge/loot_modifiers/global_loot_modifiers.json` plus `data/realscale/loot_modifiers/lotm_*_loot.json`: additive, table-scoped vanilla structure loot integration.
- `data/realscale/loot_table/lotm/structure/*.json`: small supplementary loot tables containing registered LOTM materials and S9/S8 characteristics.

## Current implementation status (2026-11-12)

The current branch has moved beyond the original vanilla-variant pilot:

- Ten stable creature kinds are represented by ten independent `realscale:lotm_<id>` EntityTypes.
- Per-kind dimensions, spawn categories, attributes, habitats, group sizes and material drop IDs are centralized in `LotmCreatureKind`.
- Shared `LotmCreature` supplies land, water and flying navigation plus common AI goals. `LotmCreatureBehavior` assigns one signature behavior to each active creature:
  - Starlit Squid: chance-based ink-cloak evasion.
  - Nightmare Eye: disorienting gaze on hit.
  - Night Owl: nocturnal escape speed.
  - Black-Spotted Frog: spring/jump escape.
  - Shroudman: slowing attack.
  - Hornacis Goat: countercharge response.
  - Ironarm Beast: armored heavy attacker.
  - Four-Eyed Turtle: defensive resistance when weakened.
  - Crimson Howler: nearby same-kind pack speed buff.
  - Death Crow: aerial hunter with a slowing strike.
- The shared GeckoLib renderer resolves species-specific resources by stable creature ID. The generator emits 64x64 original textures, geometry JSON and idle/walk/attack/hurt/death animation JSON for all ten kinds during Gradle `processResources`.
- Ten `neoforge:add_spawns` biome modifiers use the intended vanilla/RealScale habitat lists and low spawn weights. The squid requires a water column.
- The old custom relic-site biome modifier is explicitly disabled with `neoforge:none`. Keep the old block and feature registrations only for compatibility; do not place custom ruins or relic sites.
- `CREATURE_ENTITIES` gates new natural spawning. Turning it off must not remove drops from creatures already in a world. `CREATURE_VARIANTS` defaults off; old tagged stand-ins remain readable.
- Sequence 7 remains archived and is not included in entity registration, spawn modifiers or vanilla structure loot tables.
- Four additive loot modifiers target vanilla dungeon, mineshaft, stronghold corridor and ancient-city chests. Their supplementary-roll chances are 4%, 5.5%, 7.5% and 10%; `replace:false` preserves all existing vanilla and other-mod loot.

**New runtime acceptance command:** `/lotm diagnose` (read-only). It checks registered ores/minerals/flora/creature materials/manuals, the alchemy apparatus and menus, the sixteen loaded skill-manual recipes, server config bounds, and the current player's Sequence 8/9 mastery/cost values. It never changes player data or consumes items. Source: `neoforge/src/main/java/org/realscale/neoforge/lotm/LotmRpgDiagnostics.java`.

**Implemented on the feature branch; still requiring verification / polish:**

1. Check the latest `Source integrity, tests and NeoForge build`, Gradle 9.8 and dedicated-server workflows against the exact current head before claiming the milestone passes.
2. Run `/lotm diagnose` in a loaded world and retain its full report; investigate any failed section. Then test `/lotm panel` in a real client: four tabs, synchronized values, button actions, no-path state, localization, scaling and menu close/reopen behavior.
3. Test all sixteen skill-manual recipes in the alchemy apparatus, including the separate binder, fuel pause, output blocking, save/load and item extraction.
4. Test reading manuals at S9 and S8, wrong-path rejection, configured mastery cap, respawn persistence, effective skill cost (including Divination Pendulum) and the actual skill range/duration changes.
5. Inspect generated item/mineral/block/ability icons and all ten creature textures in-game. The generator is deterministic and build-integrated, but visual quality still needs client review.
6. Test per-player pathway aura and eye motes in multiplayer, verify the global appearance switch, HUD interval and coexistence with other action-bar notifications. This is a particle-based cosmetic appearance layer, not a replacement player skin/model.
7. Confirm all ten entities actually spawn at reasonable rates in a saved test world; inspect the species-specific `special` animations, squid schooling and Howler regrouping. Night Owl roosting and original ambient sounds remain open.
8. The dedicated-server RCON smoke now summons all ten active entity IDs and reads their health attributes; verify this against the latest workflow result.
9. Verify the configurable material drop chance and all sixteen manual recipes in a saved world. No custom structure or ruin generation is in scope.

## Active creature roster

| Stable ID | Sequence | Intended habitat | Material ID |
|---|---:|---|---|
| `starlit_squid` | 9 | Deep oceans | `lavas_squid_essence` |
| `nightmare_eye` | 9 | Dark forests | `nightmare_eye_shard` |
| `night_owl` | 9 | Forests and taiga | `night_owl_token` |
| `black_spotted_frog` | 9 | Swamps and wetlands | `black_spotted_frog_token` |
| `shroudman` | 9 | Swamps and seasonal flood forests | `shroudman_crystal` |
| `hornacis_goat` | 8 | Alpine/highland biomes | `hornacis_goat_horn` |
| `ironarm_beast` | 8 | Deserts and badlands | `ironarm_spine` |
| `four_eyed_turtle` | 8 | Beaches and coastal biomes | `four_eyed_turtle_heart` |
| `crimson_howler` | 8 | Forests and taiga | `crimson_howler_voice` |
| `death_crow` | 8 | Jungles and tropical littoral forests | `death_crow_eye` |

The seven archived Sequence 7 variants are `mist_treant`, `evil_pattern_panther`, `stag_beast`, `grey_grandmother`, `dream_crow`, `nightmare_shadow`, and `spectral_cat`. Do not add them to active spawn lists.

## Compatibility rules

- Do not rename existing item IDs, potion IDs, characteristic IDs, player NBT keys, or pathway IDs.
- Keep old vanilla variant drop mapping readable for already-saved tagged entities, but disable legacy stand-in generation by default after independent entities are introduced.
- Do not remove the relic-cache block/entity registration if that could invalidate existing saved blocks. Remove its normal biome placement instead.
- Do not enable Sequence 7 recipes, spawns, or advancement.
- Do not modify RealScale density functions, biome sources, climate, WGS84 latitude, world presets, terrain features, or Distant Horizons/Chunky integration.
- Do not add a new required mod dependency. GeckoLib and Player Abilities are already part of the current dependency contract.

## Recommended implementation order

See `docs/superpowers/specs/2026-11-12-lotm-creature-expansion-design.md` and `docs/superpowers/plans/2026-11-12-lotm-creature-expansion.md`.

1. Define stable creature metadata and pure spawn rules.
2. Register ten independent entity types, attributes, AI and server-side drops.
3. Generate ten original GeckoLib models, textures and animations.
4. Add habitat-specific biome spawns; disable custom relic-site placement.
5. Additive vanilla structure loot is now wired for four original chest tables; verify it in-game and keep Sequence 7 excluded.
6. Skill manuals, mastery scaling, four-tab status panel, pathway aura and expanded material icon generation are implemented on this branch; validate them before further skill-tree and supernatural-energy expansion.

## Build and verification

From `neoforge/`:

- `./gradlew test`
- `./gradlew build`

The existing `.github/workflows/architecture-pilot-server.yml` runs a dedicated server smoke test for PR changes under `neoforge/**`. Check the PR's latest Actions checks after each runtime commit. Also run the LOTM and QA tests named by the implementation plan.

## Instructions for future AI agents

- Fetch the current branch head before editing; never assume the SHA in an old chat is current.
- Work only on the branch named above unless the user explicitly selects another branch.
- Read the design, plan, this handoff, and the specific existing source files before editing.
- Use test-first changes for gameplay rules and registry metadata.
- Use stable IDs and avoid broad refactors.
- Do not claim a build or test passed without a fresh run result.
- Do not merge the PR or change `main` without explicit user authorization.
