# LOTM S9–S8 Mineral Family Expansion — Design
Date: 2026-11-13
Base: Minecraft 1.21.1, NeoForge 21.1.249, Java 21; integrate into RealScale.

## Intent
Extend the three existing active occult ores with three **new** S9/S8-accessible, non-overpowered material families. Each must work end to end (block -> ore vein -> mining drop -> material item -> crafting use -> alchemy/sensing loop). Retain the stable IDs and worldgen of the existing ores. Spirit Quartz and S7 remain archived. No new ruins, dimensions or mods.

## Implemented baseline (do not overwrite)
Star Crystal, Cursed Crystal and Grave Crystal have correct-tool pickaxe mining, Silk Touch, Fortune and explosion decay. Four occult flowers have biome-scoped generation; existing alchemy apparatus and potions work. Keep their IDs and resource profiles unchanged.

## New ore families and rationale
| Suffix | Block / Item | Geology inspiration (not a claim of real magic) | Biomes | Y min .. max | Attempts/chunk | Vein |
|---|---|---|---|---|---|---|
| lunar_mica | lotm_lunar_mica_ore / lotm_lunar_mica | mica-rich metamorphic/pegmatitic seams | taiga, snowy_taiga, old_growth_pine_taiga, realscale:subalpine_woodland | -32 .. 48 | 2 | 4 |
| echo_quartz | lotm_echo_quartz_ore / lotm_echo_quartz | quartz veins in uplifted fractured stone | dripstone_caves, stony_peaks, jagged_peaks, windswept_hills, realscale:rocky_alpine_meadow | -56 .. 24 | 2 | 4 |
| abyssal_salt | lotm_abyssal_salt_ore / lotm_abyssal_salt | ancient marine brines and evaporites; fantasy reinterpretation | deep_ocean, deep_cold_ocean, deep_lukewarm_ocean | -56 .. -8 | 2 | 3 |

These are design hypotheses, not measured abundance. Do not claim 2 attempts/chunk means 2 guaranteed veins.

## Functional crafting uses (additive alternatives)
1. `lotm_spirit_focus_lunar`: shaped ` A /ALA/ A `, A = amethyst shard; L = lunar mica. Output 1 existing Spirit Focus. Does not replace Grave Crystal recipe.
2. `lotm_occult_catalyst_echo`: shapeless lapis + echo quartz + paper. Output 3 existing occult catalysts (existing vanilla/amethyst version outputs 2).
3. `lotm_spirit_ember_abyssal`: shapeless abyssal salt + glowstone dust + redstone. Output 3 existing spirit embers (existing version outputs 2).

All three results are existing registered IDs, and none directly unlock higher sequences. For existing alchemy recipes they provide alternate resource routes, not replacements.

## Mining and assets contract
- Stone and deepslate ore-replaceable tag targets; no placement in air/water itself.
- `underground_ores` biome modifier; count, in-square, uniform height range and biome filter.
- Copy existing ore stone hardness, use `requiresCorrectToolForDrops`; add each to `data/minecraft/tags/block/mineable/pickaxe.json` with `replace:false`.
- Silk Touch -> ore block, normal -> item with `apply_bonus` / Fortune `ore_drops` + `explosion_decay`.
- Distinct item and block models, blockstates, bilingual names, generated original block/item textures; all IDs under `realscale:lotm_`.
- No destructive changes to old ore IDs/worldgen or old recipes.
- Preserve current texture generator integration with Gradle processResources.

## Tests and acceptance
- Python resource QA for complete graph, exact placement parameters, allowed biomes, crafting outputs, bilingual strings, model references, generator texture outputs, S7 disabled and old ores unchanged.
- New-family tests must fail before resources are implemented (RED), then pass (GREEN).
- Full Python QA, JUnit and Gradle full build in GitHub Actions; Gradle 9.8 + NeoForge dedicated server on the exact latest code HEAD.
- Saved-world spawn density and mining mechanics are **not** player-tested merely because resource QA and builds pass.
- Avoid unnecessary new entity AI or reworking RealScale terrain in this slice.

## Next phases after this feature
Build item guide/tooltip, audit all creature drop-to-recipe links, improve real spawn distribution from multi-seed samples, alchemy ingredient overlap correctness, then add progression constraints and craftable tools. Keep each independent and tested.

## References
- NeoForge 1.21.1 biome modifiers: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/
- NeoForge 1.21.1 loot functions: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/lootfunctions/
- NeoForge 1.21.1 loot tables: https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/
- USGS research on porphyry and epithermal mineral deposits: https://www.usgs.gov/publications/porphyry-and-epithermal-mineral-deposits
