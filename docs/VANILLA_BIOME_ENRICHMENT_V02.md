# Vanilla biome enrichment v0.2 — five additional patch prototypes

This iteration adds five optional vanilla biome feature sets through NeoForge `add_features` modifiers:

| Prototype | Biomes | Candidate patch | Approximate rarity (placement chance) |
|---|---|---|---|
| old_growth_forest_fern_beds | old growth pine/spruce taiga | large ferns on podzol/grass | 1/28 |
| jungle_floor_melon_patches | jungle/sparse jungle | occasional melon patch | 1/115 |
| savanna_dry_grass_islands | savanna and savanna plateau | clustered short grasses | 1/19 |
| cherry_grove_flowery_floor | cherry grove | pink petal clusters | 1/26 |
| dark_forest_mossy_floor | dark forest | moss carpets in damp forest understory | 1/24 |

Each feature contains `configured_feature`, `placed_feature` and `neoforge/biome_modifier` JSON definitions.

**Caveat:** These are prototypes using vanilla random patches, not custom multi-block structures or a biome remapping system. Placement predicates currently check substrate only. A dedicated server must confirm above-ground replaceability and plant survival, in particular pink petals, ferns and moss carpets. Visual QA must reject floating plants or unnatural melon clusters. No existing climate, terrain, rivers or cave density functions were modified.

Future: root-throw pit/mound feature, fallen supported logs, local deadwood fungi, wetlands hummocks, talus scree and nested vegetation distributions with coordinate-seeded randomness.
