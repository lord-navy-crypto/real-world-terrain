# LOTM Mineral Economy & Ore-Harvesting Implementation Plan

Goal: Make the three active LOTM crystal ores behave as complete Minecraft ores with correct pickaxe mining, Silk Touch, Fortune, and explosion-aware drops while preserving all current IDs and worldgen distribution.

Architecture: Keep the existing configured/placed feature pairs and biome modifiers unchanged. Add a merged vanilla pickaxe block tag, explicit correct-tool requirements, vanilla-style ore loot alternatives, and resource QA.

Runtime: Minecraft 1.21.1 · NeoForge 21.1.249 · Java 21.  
Design: docs/superpowers/specs/2026-11-13-lotm-mineral-economy-design.md

## Constraints

- Keep the current feature branch and PR #155. Do not merge or modify main.
- Preserve all block/item/resource IDs and current save compatibility.
- Preserve existing ore worldgen count, size, height range, biome lists, and generation step.
- Do not enable Spirit Quartz or any Sequence 7 content.
- Do not create new ruins, structures, dimensions, dependencies, tools, or combat items.
- Use the 1.21.1 loot schema only.
- Do not tune ore frequency without measured multi-seed data.

## Task 1 — Add tests for the ore mining contract

Files: modify qa/test_lotm_sequence9_8_resources.py.

- [ ] Test data/minecraft/tags/block/mineable/pickaxe.json for replace:false and the three active ore IDs.
- [ ] Test LotmContent.java to ensure each active ore registration explicitly calls requiresCorrectToolForDrops().
- [ ] Test each active ore loot table for:
  - minecraft:alternatives;
  - Silk Touch match-tool predicate as the first child;
  - exact ore block item as the first child result;
  - corresponding crystal item as the second child;
  - minecraft:apply_bonus with minecraft:fortune and minecraft:ore_drops;
  - minecraft:explosion_decay.
- [ ] Test each ore's configured feature targets stone and deepslate ore-replaceable tags and its biome modifier uses underground_ores.
- [ ] Assert Spirit Quartz's biome modifier remains exactly {"type":"neoforge:none"}.
- [ ] Run the LOTM resource suite and confirm it fails for the missing mining contract before implementation.

## Task 2 — Implement explicit pickaxe mining

Files: modify neoforge/src/main/java/org/realscale/neoforge/lotm/LotmContent.java; create neoforge/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json.

- [ ] Add .requiresCorrectToolForDrops() to STAR_ORE, CURSED_ORE, and GRAVE_ORE properties.
- [ ] Create the vanilla pickaxe block tag with replace:false and the three active ore block IDs.
- [ ] Do not add Spirit Quartz to the active tag in this slice.
- [ ] Preserve hardness, resistance, sound, models, and IDs.

## Task 3 — Implement vanilla-style ore loot

Files: modify the three active ore loot tables.

- [ ] For each ore, create an alternatives entry with a Silk Touch match-tool child that drops the block itself.
- [ ] Add a normal-drop child that returns the corresponding crystal item.
- [ ] Apply the vanilla ore_drops Fortune bonus to the crystal item.
- [ ] Apply explosion_decay to the crystal item.
- [ ] Keep the root loot type as minecraft:block.
- [ ] Leave Spirit Quartz's loot table untouched.

## Task 4 — Verify and update the research record

- [ ] Run the focused LOTM resource tests.
- [ ] Run all Python QA tests.
- [ ] Run JUnit and full NeoForge build under the project's pinned Gradle lane.
- [ ] Verify Gradle 9.8 compatibility.
- [ ] Verify dedicated-server startup and saved-block compatibility.
- [ ] Confirm worldgen JSON triples are unchanged and Sequence 7 remains sealed.
- [ ] Update the design document with exact final SHA and check IDs.
- [ ] Update the parent LOTM hardening spec with the completed mineral slice and remaining work.
- [ ] Do not claim player-tested ore behavior unless a human has tested it in-game.

## Final acceptance checklist

- [ ] No ID changes.
- [ ] Three active ore blocks require a pickaxe and drop nothing with an incorrect tool.
- [ ] Silk Touch returns the block.
- [ ] Fortune affects the crystal count.
- [ ] Explosion decay applies to crystal drops.
- [ ] Existing worldgen distribution remains unchanged.
- [ ] Spirit Quartz stays disabled.
- [ ] All relevant CI lanes pass on the exact final branch head.
