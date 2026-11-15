# LOTM RPG — World Systems Roadmap

Scope: RealScale NeoForge 1.21.1, current playable pathway tier Sequence 9/8. This roadmap extends the existing LOTM module in place and must not alter RealScale terrain density, climate, WGS84 scaling, or the main branch without explicit acceptance.

## Design invariants

- Keep all supernatural ingredients, potions, rituals, and effects fictional and represented only by Minecraft items, blocks, and server-side game rules.
- Keep the four current pathways and Sequence 9/8 as the active progression boundary. Sequence 7 remains registered for save compatibility but unavailable.
- Keep sanity, characteristic instability, spirituality, and potion digestion as four distinct values.
- All progression, potion backlash, trade rewards, discoveries, and ritual results are server-authoritative.
- Keep rare characteristics rare. Structure loot is additive, and characteristic rolls are separate from ordinary material rolls.
- Every registered item/block must have a creative-catalogue entry, model, bilingual display name, and either a recipe, an explicit natural-drop/worldgen source, or a documented trade source.
- No new mandatory dependency without a separate decision. Use vanilla and NeoForge interfaces where practical.

## Phase 1 — Catalogue, loot and mental-state foundation

- Creative catalogue iterates the registered material registry instead of maintaining a second incomplete ID list.
- Add the missing Spirit Quartz Ore and Relic Cache block items to the LOTM tab.
- Move structure characteristics into independent 0.5% bonus-pool rolls, separate from common/rare materials.
- Add a persistent 0–100 sanity stat independent of characteristic instability.
- Direct raw-characteristic advancement and failed wrong-path/undigested potion attempts lower sanity.
- Low sanity increases active-skill cost and narrows Seer divination range.
- Sleep and Stability Sigils restore sanity; Deep Dark exposure slowly unsettles strained low-sequence Beyonders.
- Display sanity in the status panel and compact HUD.
- Survival backlash is bounded and non-lethal; creative mode bypasses backlash.

## Phase 2 — Materials, recipes, tools and interfaces

- Build one authoritative item catalogue from LotmContent.registeredMaterials().
- Audit every item for model, texture, bilingual name, recipe or acquisition source.
- Show material source hints: biome, ore height band, creature drop, plant harvest, structure loot, or merchant.
- Extend the in-game recipe browser to cover all custom alchemy recipes and the core field tools.
- Keep the 2×2 alchemy apparatus server-authoritative, with clear fuel, progress, output, and blocked-state feedback.
- Audit tool recipes for the divination pendulum, Spirit Focus, Stability Sigil, empty vial, occult catalyst, and alchemy apparatus.
- Add a compact field-guide/recipe-note flow so players can inspect formulas without an external recipe mod.

## Phase 3 — Sequence 9/8 ability completion

- Audit all eight active abilities against the server-side ability registry and their actual effect implementations.
- Ensure every tooltip describes the exact server effect, range, duration, cooldown, spirituality cost, and sanity penalty.
- Make Seer divination, Mystery Pryer occult survey, Sleepless night adaptation, and Corpse Collector undead perception distinct rather than overlapping.
- Add explicit low-sanity and low-spirituality feedback.
- Add regression tests for multiplayer authority, cooldown persistence, target filters, range bounds, and configuration toggles.

## Phase 4 — Natural resource distribution

- Keep ore generation biome-scoped and geology-aware.
- Verify placed-feature count, vein size, vertical range, host-rock tags, and biome modifiers for every active mineral.
- Keep rare plants tied to suitable biome/ecology rather than uniform global placement.
- Audit silk touch, Fortune, explosion decay, and correct-tool requirements.
- Do not alter RealScale terrain density functions or climate generation.

## Phase 5 — Wandering traders and formula knowledge

- Potion Trader: offers formula notes, common alchemy materials, empty vials, spirit embers, and catalysts at expensive emerald prices.
- Night Church Trader: offers divination tools, Spirit Focus, Stability Sigils, and a limited selection of rare materials.
- Formula notes are server-generated items with pathway/sequence data; using one records a verified formula clue in the research journal.
- Traders are uncommon variants of the vanilla Wandering Trader, not a new mandatory entity dependency.
- Do not sell complete high-value characteristics as routine stock.

## Phase 6 — Divination, spiritism and ritual magic

- Divination: bounded local hints, uncertainty, and meaningful spirituality/sanity costs.
- Spiritism: interact with nearby spirit traces and recorded clues; do not create unrestricted entity control.
- Rituals: a dedicated altar/menu with validated ingredients, location/time conditions, and a clear preview of possible outcomes.
- Failed rituals can lower sanity and temporarily disrupt abilities, but must not create unavoidable death loops.
- Research journal records discovered, attempted, and verified ritual steps separately.

## Phase 7 — Sealed artifacts and organizations

- Add a small set of low-sequence sealed artifacts with a clear power/drawback pair.
- Artifacts require attunement or stability conditions and can affect sanity or instability while carried/used.
- Church/secret-organization reputation should unlock information and trade access, not bypass progression requirements.
- Keep artifacts distinct from raw Beyonder characteristics.

## Phase 8 — External boons

- A separate boon system lets a player request a temporary or persistent Sequence 9/8-style ability from an outer deity through a ritual.
- Boons are not normal pathway advancement and do not overwrite the player's pathway.
- Every boon has a strong, visible drawback: sanity drain, persistent instability, increased spirituality costs, limited duration, or a recurring ritual obligation.
- Each deity/pathway requires its own design and balance tests.
- The first proposed boon source is pending terminology confirmation: “恶棍途径” may mean “恶魔途径” or may be a custom pathway name. Do not implement the deity identity until confirmed.

## Acceptance gates

1. Python resource/integration tests pass.
2. NeoForge Java compilation and all LOTM JUnit tests pass.
3. Packaged JAR contains the new classes, models, language keys, recipes, and worldgen/loot resources.
4. Test in a clean client and a copied survival world.
5. Test dedicated server + two clients for item use, trading, panel sync, and journal persistence.
6. Keep all work on the LOTM feature branch until the user explicitly approves a main-branch merge.
