# LOTM S9–S8 Mineral Family Expansion Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans or subagent-driven-development. Track each test and commit.

**Goal:** Add three complete occult ores and feed their harvests into existing craftable alchemy inputs, preserving previous work and S7 boundaries.

**Architecture:** Static data-driven configured + placed features and biome modifiers, three new DeferredRegister blocks and block-items plus three materials, existing ore loot pattern and generator. Resource QA first and on exact commit. 

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, Python unittest, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-11-13-lotm-mineral-family-expansion-design.md

## Global constraints
- Feature branch `feature/lotm-sequence9-pilot-20261111`, PR #155, do not merge.
- Stable existing IDs, recipes, terrain and WGS84 climate remain unchanged.
- No custom ruins, dimensions, required external runtime plugins, S7 recipes or Spirit Quartz worldgen.
- New recipes complement existing S9/S8 acquisition routes.
- Game-generated assets must be deterministic and original.

## Task 1 — New family resource QA (RED)
- [ ] Add `qa/test_lotm_mineral_family_expansion.py` with the three-family manifest covering exact biomes/altitudes/counts/veins, complete registered ID chain, loot, models, textures, translations and recipes.
- [ ] Run Python test against pre-feature files. Expect missing resources/registration failure (rather than Python syntax failure).
- [ ] Commit test-only change.

## Task 2 — Registry and harvesting
- [ ] Add six DeferredRegister IDs in `LotmContent.java`: three block + block-item pairs and three material items; all three blocks require correct tool.
- [ ] Extend the vanilla pickaxe block tag.
- [ ] Create block loot tables with `minecraft:alternatives`, Silk Touch, Fortune ore_drops, explosion_decay.
- [ ] Add configured, placed, biome-modifier JSON triples with specified sizes/counts/Y/biomes and stone/deepslate targets.
- [ ] Preserve existing three ore triples and legacy S7 archive.
- [ ] Commit.

## Task 3 — Material uses and presentation
- [ ] Add three alternate recipes for Spirit Focus, Occult Catalyst and Spirit Ember with outputs 1/3/3.
- [ ] Add blockstates, block+item models and lang keys for new items/blocks, without modifying unrelated assets.
- [ ] Register six generated textures through the existing standard-library visual generator and ensure output PNGs are distinct.
- [ ] Run new Python QA; fix only actual failures.
- [ ] Commit.

## Task 4 — Whole project verification and handoff
- [ ] Run all Python QA and Gradle JUnit tests.
- [ ] Validate current-HEAD Source Integrity+NeoForge Build, Gradle 9.8 and Dedicated Server workflow conclusions separately.
- [ ] Report any unrelated CI failures separately, without claiming entire repository green.
- [ ] Update parent hardening plan, mineral handoff and this plan with exact head SHA, outputs, and remaining runtime tests.
- [ ] Keep user-visible counts accurate: six active ores only if all six registered and have complete resource triples; S7 still archived.

## Testing / review focus
- Item, block, loot, recipe IDs must all agree.
- No missing generated textures, invalid 1.21.1 resource schema or registered-item collisions.
- New ores must not appear in nonsensical biomes or above the configured maximum Y.
- New recipe must not produce S7 potions; original recipes remain discoverable.
- Retained old worldgen and block IDs must be byte-identical except the expanded pickaxe tag.
