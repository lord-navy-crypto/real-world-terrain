# LOTM dependency and integration profile (Minecraft 1.21.1 / NeoForge 21.1)

## The three required LOTM dependencies

| Dependency | Version | Role |
|---|---|---|
| GeckoLib | 4.9.3 | Animated alchemy apparatus, animated items, and future animated entities |
| Curios API | 9.5.1+1.21.1 | Non-vanilla equipment slots and accessory handling |
| Player Abilities | 1.0.9 | Active/passive/triggered ability API, ability wheel, cooldowns, levels, and ability book |

All three are required on both client and dedicated server. They are separate runtime mods; none of their JARs is shaded into the RealScale JAR. Player Abilities is All Rights Reserved, so RealScale uses its public API and does not copy or redistribute its code.

The existing YetAnotherConfigLib dependency belongs to RealScale's broader configuration UI and is not an additional LOTM gameplay framework.

## Responsibility boundaries

- GeckoLib supplies animation infrastructure. RealScale owns the models, textures, sounds, animation definitions, and machine logic.
- Curios API supplies accessory slots and inventory handling. RealScale defines the occult-focus slot, item tags, accessory items, and any gameplay effects.
- Player Abilities supplies ability registration, levels, cooldowns, use lifecycle, active/passive/triggered behavior, a radial wheel, an ability book, and HUD display.
- RealScale LOTM remains authoritative for pathway, sequence, spirituality, digestion, material requirements, promotion, and the actual effect code.

## Player Abilities integration

The current pilot registers eight active abilities: primary and secondary skills for each of the four active pathways. Player Abilities ability level 1 maps to Sequence 9; level 2 maps to Sequence 8. The grant source is realscale:lotm_pathway, and LotmState drives grants on pathway initialization, promotion, login, and player cloning.

Player Abilities default controls are Z (ability wheel), X (use selected ability), C (cycle selection), and N (ability book). All are rebindable in its Controls category. RealScale's old duplicate Z/X packet and key mappings have been retired.

Player Abilities automatically creates Ability Tomes for registered abilities. LOTM abilities are guarded: only the RealScale pathway grant source is accepted. Other grant sources, including Ability Tomes, are revoked; the guard also checks every player tick so a tome cannot raise an already-granted skill to a higher level.

The Player Abilities ability book is the list of currently learned and usable abilities. The separate Pathway Codex skill-tree interface remains a RealScale-authored feature to build later; it will not replace Player Abilities' execution framework or create a second progression authority.

## Curios integration

Curios is required. The current pilot registers one occult_focus slot on players. lotm_spirit_focus and lotm_divination_pendulum are accepted through the vanilla item tag at data/curios/tags/item/occult_focus.json. Additional ring, necklace, charm, and relic slots can be added as the equipment catalogue expands.

## Development and runtime verification

- Gradle resolves the three pinned runtime dependencies.
- The build copies the official Player Abilities dependency to a separate smoke-test directory; it is not added to the RealScale JAR.
- CI verifies GeckoLib and Curios through official Modrinth version metadata and SHA-512.
- CI verifies the Player Abilities JAR's embedded player_abilities mod ID and 1.0.9 version before installing it in the disposable test server.
- The development and packaged-server tests boot RealScale with all three LOTM dependencies plus the pinned Distant Horizons and Chunky test mods.
