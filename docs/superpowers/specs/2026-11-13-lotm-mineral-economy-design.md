# LOTM Mineral Economy & Ore-Harvesting Design

Date: 2026-11-13  
Target branch: feature/lotm-sequence9-pilot-20261111  
Runtime: Minecraft 1.21.1 · NeoForge 21.1.249 · Java 21

## Purpose

Strengthen the existing mineral layer without adding new dimensions, custom ruins, new mandatory dependencies, or changing current ore distribution before it has been measured. This slice makes the three active crystal ores behave like proper Minecraft ores, creates a consistent mining contract, and documents how future material progression should be designed.

## Current-state audit

| Block | Existing drop | Existing generation biomes | Existing Y range | Attempts/chunk | Vein size |
|---|---|---|---:|---:|---:|
| Star Crystal Ore | Star Crystal | Deep oceans, gravel-shore fringe, coastal heath | -48 to 24 | 3 | 5 |
| Cursed Crystal Ore | Cursed Crystal | Dark forest, old-growth pine taiga, two RealScale forest biomes | -48 to 48 | 2 | 5 |
| Grave Crystal Ore | Grave Crystal | Swamps, mangrove swamps, marshy/floodforest biomes | -48 to 40 | 2 | 5 |
| Spirit Quartz Ore | Spirit Quartz | Disabled (neoforge:none) | -55 to 12 (placed feature retained) | 2 | 5 |

The placed-feature attempt count is not a guaranteed number of veins or ore blocks. Ore feature placement can fail when targets or terrain conditions do not match. Do not infer actual blocks-per-chunk from count multiplied by size.

The three active ore loot tables currently always drop one crystal item. They do not support Silk Touch, Fortune, or explosion decay. The ore blocks are copied from stone/deepslate properties but do not explicitly state their correct-tool requirement or appear in the mod's own pickaxe-mining tag.

## Design goals

1. Preserve all current block IDs, item IDs, placed-feature IDs, biome lists, Y ranges, attempt counts, vein sizes, and saved-world compatibility.
2. Make active ore blocks explicitly require a correct tool and be recognized as pickaxe-mineable.
3. Add vanilla-consistent Silk Touch behavior: Silk Touch returns the ore block; otherwise the ore yields its associated crystal item.
4. Add vanilla Fortune ore-drop scaling to the crystal item.
5. Apply explosion decay to non-Silk-Touch crystal drops.
6. Keep the existing Sequence 7 Spirit Quartz worldgen disabled and leave its loot semantics unchanged.
7. Add resource-level tests that validate every active ore's complete mining contract.
8. Keep geological distribution tuning separate until deterministic multi-seed samples and in-world observations exist.

## Chosen implementation

### Block mining contract

- Add the three active ores to data/minecraft/tags/block/mineable/pickaxe.json with replace:false.
- Explicitly call requiresCorrectToolForDrops() on the three active ore block properties.
- Do not add an iron-tier requirement in this slice. All three use the normal pickaxe progression unless later playtests show a need for different tiers.

### Loot contract

Each active ore's block loot table uses one minecraft:alternatives entry:

1. Silk Touch tool condition: return the exact ore block.
2. Otherwise: return the corresponding crystal item, apply minecraft:ore_drops using Fortune, then apply minecraft:explosion_decay.

This follows the vanilla ore-drop pattern for the pinned 1.21.1 loot schema. The project must not copy 1.21.2+ predicate formats into 1.21.1.

### Distribution policy

No placement values change in this implementation slice. Geological analogues are design inspiration, not a claim that fantasy minerals exist in real geology:

- Star Crystal: marine/coastal mineralization and quartz-rich hydrothermal analogy.
- Cursed Crystal: uncommon forest-hosted alteration pockets; low encounter frequency is intentional.
- Grave Crystal: wetland/sedimentary and organic-rich host-rock atmosphere.
- Spirit Quartz: archived high-tier material; keep its worldgen modifier disabled until that progression tier is explicitly reopened.

USGS references describe hydrothermal fluids, quartz veins, alteration, and host-rock controls. Use those concepts to make future deposits coherent, while keeping the current fantasy identity and RealScale biome distribution.

## Non-goals

- No new mineral IDs or blocks.
- No changes to worldgen density, height, biome membership, or existing recipes.
- No new progression unlocks.
- No new custom ruins or structures.
- No changes to RealScale terrain, WGS84 climate, biome generation, or world presets.

## Acceptance criteria

- All three active ore blocks have explicit correct-tool requirements and the shared vanilla pickaxe tag.
- Silk Touch returns the exact ore block, not its crystal item.
- Normal mining returns the correct crystal item.
- Fortune uses the vanilla ore-drop formula.
- Explosion decay is applied only to the non-Silk-Touch crystal drop.
- All loot JSON parses and matches the 1.21.1 schema.
- The three active ore worldgen JSON triples remain unchanged.
- Spirit Quartz remains disabled and its archived ID remains intact.
- All LOTM resource QA, full Python QA, JUnit, Gradle 8.14.3 build, Gradle 9.8 compatibility, and dedicated-server checks pass on the exact final head.

## Research references

- NeoForge 1.21.1 Biome Modifiers: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- NeoForge 1.21.1 Loot Functions: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/lootfunctions/
- NeoForge 1.21.1 Loot Conditions: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/lootconditions/
- Minecraft 1.21.1 diamond ore loot table reference: https://mcasset.cloud/1.21.1/data/minecraft/loot_table/blocks/diamond_ore.json
- U.S. Geological Survey, Porphyry and epithermal mineral deposits: https://www.usgs.gov/publications/porphyry-and-epithermal-mineral-deposits
- U.S. Geological Survey, Physical-chemical conditions of ore deposition: https://www.usgs.gov/publications/physical-chemical-conditions-ore-deposition

## Future work (separate implementation plans)

1. Build an auditable material graph linking creature drops, minerals, plants, and alchemy recipes.
2. Add meaningful item descriptions, rarity tiers, and bilingual field-guide entries.
3. Add multi-seed ore-distribution sampling and tune placement from measured results.
4. Audit plant survival substrates and natural-generation anti-farming.
5. Review the alchemy recipe matcher for overlapping ingredient/tag ambiguity.
