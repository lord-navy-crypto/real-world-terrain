# LOTM Creature Expansion — Design

**Date:** 2026-11-12  
**Repository:** `lord-navy-crypto/real-world-terrain`  
**Target:** NeoForge 1.21.1, Minecraft 1.21.1  
**Mod ID:** `realscale`  
**Working branch:** `feature/lotm-sequence9-pilot-20261111`

## Intent

Turn the ten active LOTM occult-creature entries from renamed vanilla mob variants into distinct, registered RealScale entities with their own dimensions, attributes, AI profiles, GeckoLib geometry, textures, animations, spawn habitats, and material drops. This creates a stronger creature foundation for the later material, alchemy, energy, and pathway-skill work.

The feature remains part of RealScale. It does not create a second mod or alter the terrain-generation pipeline.

## Current state

- `LotmEcologyRules` currently defines ten active Sequence 9/8 variants and seven archived Sequence 7 variants.
- `LotmEvents.onJoin` currently tags eligible vanilla mobs with a variant name; those mobs keep their vanilla entity type, model, and AI.
- Creature drops already map to registered material item IDs.
- GeckoLib is already a required runtime dependency and is already used by the alchemy apparatus.
- Existing Sequence 9/8 potions, characteristics, progression, and alchemy are already registered and must remain stable.
- A custom relic-site biome modifier currently exists. The new scope does not need custom relic sites or custom structures.

## Chosen approach

Use one shared creature implementation configured by a stable `LotmCreatureKind` definition, but register a distinct `EntityType` for each of the ten creatures. Each kind owns its own dimensions, attributes, habitat list, spawn category, behavior flags, and material drop. A shared GeckoLib renderer resolves per-creature model, texture, and animation resources by the stable kind ID. This avoids ten duplicated entity implementations while still making every creature a real, independently summonable and identifiable entity.

### Approaches considered

1. **Keep vanilla stand-ins.** Lowest implementation cost, but creatures still look and behave like spiders, wolves, parrots, and other vanilla mobs. This does not satisfy the requested independent creature system.
2. **Ten fully separate Java implementations.** Maximum per-creature freedom, but duplicates entity lifecycle, animation, and persistence code and increases maintenance risk.
3. **Shared entity engine + ten registered types + per-kind data/assets.** Chosen. It provides distinct entity IDs and models while centralizing common lifecycle code; exceptional behavior can be added through explicit kind-specific hooks.

## Creature roster

| ID | Sequence | Habitat intent | Initial behavior profile | Material |
|---|---:|---|---|---|
| `starlit_squid` | 9 | Deep ocean and deep cold/lukewarm ocean | Aquatic, passive, evasive | `lavas_squid_essence` |
| `nightmare_eye` | 9 | Dark forest and dark woodland mosaics | Hostile ambusher; short disorienting effect | `nightmare_eye_shard` |
| `night_owl` | 9 | Forest, taiga, and woodland mosaics | Nocturnal, wary, aerial/perching profile | `night_owl_token` |
| `black_spotted_frog` | 9 | Swamp, mangrove, and wetland mosaics | Passive jumper; defensive escape | `black_spotted_frog_token` |
| `shroudman` | 9 | Swamp and seasonal flood forest | Hostile, slow pursuit, brief hindering effect | `shroudman_crystal` |
| `hornacis_goat` | 8 | Alpine meadow and highland woodland | Neutral, sturdy, retaliates when attacked | `hornacis_goat_horn` |
| `ironarm_beast` | 8 | Desert and badlands | Hostile, armored, slower heavy attack | `ironarm_spine` |
| `four_eyed_turtle` | 8 | Beach, stony shore, and coastal fringe | Passive, defensive, water-adapted | `four_eyed_turtle_heart` |
| `crimson_howler` | 8 | Forest, taiga, and woodland mosaics | Hostile pack hunter | `crimson_howler_voice` |
| `death_crow` | 8 | Jungle and tropical littoral forest | Aerial scout/harrier | `death_crow_eye` |

The roster and stable IDs are the source of truth. Sequence 7 creatures remain archived and must not spawn.

## Goals

- Register ten distinct entity types under `realscale:lotm_<id>`.
- Give each entity its own model, texture, and animation resource.
- Give each kind its own health, movement, attack, armor, size, spawn category, and habitat metadata.
- Add distinct AI behavior without replacing unrelated vanilla mob AI.
- Make creature drops server-authoritative and tied to the kind's material ID.
- Keep spawn frequency configurable and avoid flooding vanilla spawn caps.
- Disable legacy vanilla stand-ins by default while retaining their compatibility switch.
- Remove the custom relic-site placement from normal world generation; later special drops belong in vanilla structure loot tables.
- Preserve existing item IDs, potion recipes, player data, Sequence 7 archive IDs, and terrain generation.

## Non-goals

- No custom ruins, relic structures, dungeons, or new structure sets.
- No Sequence 7 or higher gameplay.
- No rewrite of the existing alchemy/progression system in this creature milestone.
- No changes to RealScale terrain density, biome climate, latitude, or world presets.
- No new required mod dependency.

## Runtime and asset constraints

- Use the already-pinned NeoForge 21.1.249 / Minecraft 1.21.1 build.
- Use the existing GeckoLib 4 dependency; do not add another animation library.
- Generate original creature geometry, textures, and animation JSON deterministically from the existing Gradle resource-generation task.
- Register entity attributes and spawn placements on the mod event bus.
- Spawn through NeoForge biome modifiers in the intended vanilla and RealScale biomes.
- Keep all item/entity IDs stable once committed.
- Natural spawns must be disabled when LOTM or the new creature-spawn config is disabled.
- Legacy vanilla stand-ins must not spawn by default alongside the new entities.

## Acceptance criteria

- All ten entity types register and can be summoned independently.
- All ten have a non-empty model, texture, and animation resource.
- Each entity has the correct kind metadata and drops only its assigned material.
- Each entity's spawn selector accepts intended habitats and rejects incompatible habitats.
- Spawn placement rejects invalid blocks/fluids and obeys the LOTM enable/config flags.
- Archived Sequence 7 variants remain non-spawning.
- Legacy vanilla stand-ins default off but remain explicitly configurable.
- No custom relic-site biome modifier places new structures.
- Existing LOTM progression and alchemy tests remain green.
- Gradle build and the PR server smoke workflow pass.
- No RealScale terrain-generation files or presets are changed by this milestone.
