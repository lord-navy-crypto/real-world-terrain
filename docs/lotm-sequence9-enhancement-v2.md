# LOTM Sequence 9 — RealScale Enhancement v2

Single-module internal expansion in `org.realscale.neoforge.lotm` (no standalone mod). All additions continue in draft PR #155 until CI and world acceptance pass.

## Source-backed, scope-bounded Sequence 9

- **Seer / 占卜家 (Fool pathway)**: sensory perception, divination, pendulum, ritual knowledge; **not** a low-level combat spellcaster. Craft a pendulum, receive approximate N/E/S/W/vertical hints to nearby already-generated occult material. Unclear outcomes are possible.
- **Mystery Pryer / 窥秘人 (Hermit pathway)**: broad introductory occult knowledge and observation of hidden things. Focus scans occult materials; future work: phased aura visualization and ritual research.
- **Sleepless / 不眠者 (Darkness pathway)**: night vision, sustained energy and night alertness. Gives night vision and limited mobility at night with faster spirituality recovery.
- **Corpse Collector / 收尸人 (Death pathway)**: limited spirit perception, undead knowledge and cold adaptation. Focus highlights simple undead; reduced powder-snow freeze.
- A **practice** counter and **spirituality** resource (0–100 each) add gameplay pacing without inventing Sequence 8 progress. Status is available through `/lotm status`; commands are server-side and accessible without op rights.
- Existing v1 pathway NBT keys remain unchanged; missing spirituality in old saves defaults to full. On death, copy progression; switching from the End does not duplicate state.

## Expanded occult ecology

- Pure lookup rules identify eligible vanilla and RealScale climate habitats, separate from expensive terrain density functions.
- Existing 4 magical creature types remain; a fifth test variant ("shroudman") appears among wetland zombies and drops `lotm_shroudman_crystal`.
- The game craft recipe for Corpse Collector uses the black-spotted frog and shroudman materials, aligning the fantasy ingredient names more closely with sourced lore. This **does not** reproduce real-world preparation instructions.
- Ores and rare flowers remain datapack-controlled at defined worldgen stages, with opt-out via existing override mechanism.
- Existing variant IDs and collected items retain their IDs for old saved worlds.

## Interactions / commands

```text
/lotm guide
/lotm status
/give @s realscale:lotm_spirit_focus
/give @s realscale:lotm_divination_pendulum
```

Spirit Focus consumes a small quantity of spirituality; successful observations increase practice. Spirituality regenerates naturally on server ticks. Pendulum scanning is on-demand and capped in both radius and Y, and it never forces chunk loading.

## References and provenance

Official novel setting collection: https://h5.if.qidian.com/h5/workSet/main?albumId=131&bookId=1010868264&catelogId=69153

Secondary cross-checks (fan-maintained, not official):
- https://lom.huijiwiki.com/wiki/占卜家途径
- https://lom.huijiwiki.com/wiki/不眠者途径
- https://lom.huijiwiki.com/wiki/收尸人途径
- https://lom.huijiwiki.com/wiki/窥秘人途径

Official NeoForge 1.21.1 biome modifier docs:
https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/

**Source-backed story facts and game-designed mechanics must not be conflated.**
Geographical spawns, spirit costs, exact ranges, material drops, practice counters, and placeholder mobs are author-created gameplay adaptations.

## Acceptance checklist

1. Gradle 9.8: `test` and `build` succeed, including new JUnit unit tests.
2. Actual NeoForge dedicated server starts; no missing registry / asset references.
3. New worlds generate all four features in intended RealScale and vanilla biomes. No large density cache churn.
4. `/lotm status`, potion one-time lock, spirit spend/recovery, save/load and respawn validated.
5. Explore dark forest, deep ocean and swamp; verify each named variant and material drop in actual gameplay.
6. Existing world presets / saved-world terrain parity verified before merge.

**Incomplete:** original art textures/models for all mobs and any real client-world QA beyond CI; do not claim fully finished.
