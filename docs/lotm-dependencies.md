# LOTM dependency and setup profile (Minecraft 1.21.1 / NeoForge 21.1)

## Required

| Dependency | Version | Side | Purpose |
|---|---|---|---|
| NeoForge | 21.1.x | Client + server | Mod loader |
| GeckoLib | 4.9.3 | Client + server | Animated alchemy apparatus and future animated items |
| YetAnotherConfigLib (YACL) | 3.8.1+1.21.1-neoforge | Client | Existing RealScale configuration screens |

GeckoLib is a separate runtime mod. It must be present in the client and server `mods` folders; it is not shaded into the RealScale JAR. CI now downloads the exact official GeckoLib 4.9.3 NeoForge 1.21.1 file, verifies its Modrinth version metadata and SHA-512, and includes it in both the development and packaged-server boot tests.

## Optional integration

| Mod | Compatible line | Purpose | Behaviour without it |
|---|---|---|---|
| Curios API | 9.5.1+1.21.1 NeoForge | Adds an Occult Focus accessory slot for the Spirit Focus and Divination Pendulum | Both items remain usable from the normal inventory |
| Pufferfish's Skills | NeoForge 1.21.1 build | Candidate for a visual skill-tree framework and online layout editor; integration is not yet enabled | Existing RealScale Z/X skills remain active |
| Patchouli | 1.21.1-93 NeoForge | Candidate for an in-game field guide; book integration is not yet enabled | Existing commands and tooltips remain available |

Optional mods are not bundled into the RealScale JAR. Install the exact NeoForge 1.21.1 build on the sides required by each mod. Curios integration is active. Pufferfish's Skills and Patchouli are evaluated candidates, not active dependencies in this build. Pufferfish's Skills is a framework rather than a ready-made tree; if selected, the LOTM categories and progression bridge must be configured by this project. We will keep `LotmState` as the authoritative pathway/sequence record rather than create a second, conflicting progression system.

## Curios integration

When Curios is installed, the project registers one `occult_focus` slot on players. The `lotm_spirit_focus` and `lotm_divination_pendulum` items are accepted there. Curios is optional: no Curios API classes are referenced by the core mod, so a server without Curios can still load RealScale.

## Runtime acceptance

- Build the RealScale JAR.
- Start a clean NeoForge 21.1.249 dedicated server with RealScale + GeckoLib.
- Start the packaged DH + Chunky smoke server with RealScale + GeckoLib + the pinned DH/Chunky versions.
- Confirm no missing-dependency errors, then run LOTM recipe, inventory, save/reload and client-render tests.
- Curios and other optional integrations are tested in separate matrix lanes so they cannot make the base server depend on optional mods.
