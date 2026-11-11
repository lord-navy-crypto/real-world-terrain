# LOTM Sequence 9 Pilot (RealScale internal expansion)

This is one NeoForge mod and one repository. No separate project or mod dependency is created.

## Four Sequence 9 pathways

| Pathway | Sequence 9 | Initial playable mechanic |
|---|---|---|
| Fool | Seer / 占卜家 | Spirit Focus highlights nearby occult creature variants |
| Hermit | Mystery Pryer / 窥秘人 | Spirit Focus scans for unusual crystals and blooms |
| Darkness | Sleepless / 不眠者 | Night vision at night; focus senses nearby hostile mobs |
| Death | Corpse Collector / 收尸人 | Freezing hardiness; focus senses nearby undead |

Player pathway and sequence 9 are stored in persistent player data and copied upon respawn. Only the first potion can be used, and abilities are server-authoritative.

## Ecology and progression loop

Three small occult ore patches are biome-limited (ocean/coast, dark forest, wetlands). A rare midnight flower patch is placed on grass in shadowy forests.

Existing vanilla creatures occasionally become visibly named occult **variants**; each supplies one fantasy-only crafting component. These are explicitly placeholders, **not new creature models**:
- Deep ocean squid: Starlit Squid → essence
- Dark forest spider: Nightmare Eye → shard
- Forest/cave bat: Night Owl → owl token
- Swamp frog: Black-Spotted Frog → frog token

Each Sequence 9 fictional game potion requires a fantasy vial, one environmental material, and one variant item. There is deliberately **no real-world chemical or consumable preparation**.

Use `/give @s realscale:lotm_spirit_focus` to test the reusable ability item. Use `/give @s realscale:lotm_seer_potion` (or `lotm_mystery_pryer_potion`, `lotm_sleepless_potion`, `lotm_corpse_collector_potion`) for testing.

## Data/compatibility limits

- Original RealScale density functions, dimension presets and terrain generation code remain unchanged.
- Config `lotm.enabled` disables gameplay and `lotm.creatureVariants` disables new variant assignment and drops.
- Biome feature injection is **datapack-controlled**. Override the four `realscale/neoforge/biome_modifier/lotm_*.json` entries with `{"type":"neoforge:none"}` to opt out of new worldgen; already generated chunks stay unchanged.
- Existing mob visuals and Minecraft textures are **development placeholders**. Replace them with original models/textures before a polished release.
- Abilities are deliberately a conservative subset of canon. High-sequence mechanics, true prophetic outcomes, spirit world, acting/digestion, and full progression are not included.

## Source distinction

Canonical baseline: Qidian official LOTM setting collection (https://h5.if.qidian.com/h5/workSet/main?albumId=131&bookId=1010868264&catelogId=69153).
Secondary cross-check: fan-maintained Lord of the Mysteries path summaries.
Variant rates, geographical ore placement, in-game vial crafting and Spirit Focus are **original gameplay adaptations**, not canonical facts.

## Verification checklist

- Gradle 9.8 NeoForge `test`, `build`, `runServer` and actual client world entry.
- Load existing RealScale saves; compare terrain and structure generation with baseline.
- Generate fresh dark forest, deep-ocean and swamp chunks; check ore, flower and special mobs.
- Verify each potion chooses only one pathway and saves across logout/respawn.
- Ensure client-only rendering resources do not crash dedicated servers.
