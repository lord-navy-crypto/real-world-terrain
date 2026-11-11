# RealScale × LOTM — RPG and Alchemy

This work remains inside the existing RealScale NeoForge 1.21.1 branch. It does not create a separate mod or repository. NPCs and dialogue are out of scope for this milestone.

## First playable loop

1. Choose one of the four pathways by consuming its Sequence 9 potion.
2. Explore unfamiliar biomes and locate occult materials.
3. Use the pathway's focus or divination tool in meaningful contexts.
4. Gain bounded digestion progress from exploration, skill use, research and fieldwork.
5. Brew the next potion in the in-world apparatus.
6. Consume the potion to advance one sequence; the potion item validates path and rank.

The initial acting tasks are descriptive prompts, not NPC quests. Server-observed actions award digestion. Unique-biome discovery and daily category caps reduce repetitive farming.

## Alchemical Apparatus

| Slot | Purpose |
|---|---|
| 0–3 | 2×2 recipe inputs |
| 4 | Spirit Ember fuel |
| 5 | Potion output |

The apparatus reuses vanilla shapeless recipe data, so recipes remain data-driven and visible to JEI without making JEI mandatory. Sequence 9/8/7 processing times are configurable. Processing pauses when fuel is missing or output is blocked. Inventory and progress are saved with the block entity.

## Characteristic substitution

A matching complete Sequence 9/8/7 characteristic can replace the two pathway-specific main ingredients. The alternative recipe still uses an empty vial and occult catalyst. The apparatus consumes one Spirit Ember as a fictional in-game workstation requirement.

Directly using an unprepared characteristic remains a separate high-risk game option; preparing it in the apparatus is the normal route.

## Optional dependencies worth considering

None is mandatory for the core RealScale feature. Add integrations only after the base RPG loop is stable.

1. **JEI** — recipe/item browser. The current apparatus reuses vanilla crafting recipes, so JEI can display ingredient combinations immediately. A dedicated machine category can be added later for time/fuel details. Minecraft 1.21.1 NeoForge builds exist. Project: https://modrinth.com/mod/jei. MIT.
2. **Patchouli** — in-game field guide for pathways, sequence abilities, material locations, acting tasks and recipes. A 1.21.1 NeoForge release line exists (1.21.1-93). Project: https://modrinth.com/mod/patchouli. CC-BY-NC-SA-3.0; use as an optional runtime mod and do not copy its assets/code.
3. **Curios API** — later accessory slots for pendulums, charms, seals and passive relics. NeoForge 1.21.1 release 9.5.1+1.21.1 exists. Project: https://modrinth.com/mod/curios. LGPL-3.0-or-later.

Do not make any of these mandatory until the user chooses them.

## Canon vs game adaptation

- Canon-inspired: pathway identities, Sequence 9–7 names, acting/digestion, potion advancement, and complete characteristics substituting for main ingredients.
- Game adaptation: exact item ingredients, Spirit Ember fuel, processing time, digestion rewards, daily caps, spirituality costs, and skill effects.
- All alchemy and supernatural mechanics are fictional Minecraft gameplay only.

## Acceptance checklist

- [ ] Gradle build and JUnit tests pass on the PR branch.
- [ ] Place apparatus, open GUI, insert ingredients and fuel, brew all four Sequence 9 potions.
- [ ] Test normal and characteristic-substitution recipes for all 12 potion outputs.
- [ ] Verify no input consumption when output is blocked or fuel is missing.
- [ ] Close/reopen GUI and reload world during processing; inventory and progress persist.
- [ ] Dedicated server starts without loading client GUI classes.
- [ ] Test digestion persistence, daily caps, biome uniqueness per pathway/sequence, death clone and relog.
- [ ] Test existing RealScale worlds; no terrain/worldgen code is changed.
