# LOTM RPG Branch Handoff

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
4. Later, use vanilla Minecraft structures and their loot tables as possible places for rare characteristics and materials.
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
- `LotmEcologyRules.java`: current active/archived creature roster, legacy biome selection and drop IDs.
- `LotmEvents.java`: server-side join, drop, tick, clone hooks.
- `LotmWorldgen.java` / `LotmRelicSiteFeature.java`: custom relic-site feature registration and placement support.
- `scripts/generate_lotm_visuals.py`: deterministic, standard-library-only generation of LOTM item/block/ability textures.
- `neoforge/src/main/java/org/realscale/neoforge/lotm/client`: GeckoLib alchemy model and renderer; follow this existing pattern.

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
4. Add habitat-specific biome spawns; remove custom relic-site placement.
5. Expand materials and minerals; then alchemy/potion recipes; then skill trees and energy systems.
6. Add vanilla structure loot-table integration only after the RPG core is stable.

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
