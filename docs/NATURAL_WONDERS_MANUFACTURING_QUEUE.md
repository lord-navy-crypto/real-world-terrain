# RealScale TVA — Natural Wonders Manufacturing Queue

Status: **PLANNED / NOT YET IMPLEMENTED**. Created 2026-10-17. This is a production backlog, not a claim of existing worldgen.

## Non-regression gate
Keep PR #30 sky-island real Anvil verification and four-family multiseed worldgen QA running independently. Preserve normal ocean floor, rivers, caves, sky islands, climate and latitude, both enlarged dimensions, structures and old save compatibility. No merge to main without required QA. No background worker is implied by this document.

## Production stages
1. **Survey** — verified real-world process and biome/geology constraints, avoid decorative-only pseudo-science.
2. **Prototype** — deterministic pure layout function using chunk-safe coordinates, no cross-chunk block writes; preserve seeds.
3. **Integration** — opt-in configured/placed features and biome modifiers; apply to valid geology and dimensions only; do not replace existing features.
4. **Acceptance** — compile, unit geometry tests, worldgen startup, multi-seed persisted Anvil proof, coastline/river/structure exclusions, runtime profile and before/after screenshots or saved palettes.
5. **Promotion** — merge after the required checks pass and old saves remain readable.

## Phase A — physically grounded surface environments

| Priority | Feature | Natural conditions | Implementation target | Status |
|---|---|---|---|---|
| P0 | Playa / seasonal salt basin | Arid closed-drainage low basins, flat hardpan, rare shallow pools | Basin-mask + elevation-limited salt-like vanilla palette + optional low water | Planned; existing earth_salt_playa accent must be audited/reused |
| P0 | Alluvial fan system | Canyon exit to low-gradient foreland; fan spreads downgradient | Fan geometry, gravel/sand sorting, ephemeral distributaries | Planned; existing earth_alluvial_fan accent must be audited/reused |
| P0 | Cold desert and desert pavement | Dryness independent of temperature; gravel lag and exposed rock | Slope and humidity masks, rocky/gravel surface, sparse scrub | Planned; existing earth_cold_desert accent must be audited/reused |
| P1 | Hoodoo amphitheater | Layered erodible plateau escarpment, rain/frost erosional pillars | Deterministic connected wall-window-pillar transition; sandstone/terracotta | Planned |
| P1 | Travertine spring terraces | Local carbonate-rich hydrothermal circulation, stepped mineral precipitates | Terraced basin and safe shallow pools; avoid creating huge waterflows | Planned |
| P1 | Mangrove tidal estuary | Warm muddy protected coasts with tidal intertidal rooting | Mud banks, tangled rooted trees, estuary-water continuity | Planned; reuse vanilla mangrove biome |
| P1 | Young/old lava-field mosaic | Basaltic outflows, joints, overlapping flow lobes and vegetation recolonization | Rough cooled stone flow, scarps and cracks; avoid indiscriminate lava | Planned; existing earth_volcanic_highland accent must be audited/reused |
| P2 | Lava tubes and collapse chambers | Beneath lava fields, distinct from solution caves | Capped safe cave-carve module tied to flow field geometry | Planned |
| P2 | Delta / braided alluvial plain | River mouth gradients and sediment supply | Drainage-aware splitting and estuarine deposition | Planned |
| P2 | Glacial cirques, moraine chains | High cold mountains with erosional heads and sediment ridges | Ice-free/icy elevation-sensitive bowl and ridgeline | Planned; audit earth_glacial |
| P2 | Karst sinkholes and connected caves | Soluble bedrock, drainage captured underground | Sinkhole/cave linkage, prevent ocean/structure leakage | Planned; audit earth_karst |

## Phase B — rare dimension-invasive variants
Translate real geology into invasion **only after** ordinary-world version works. Six directions (Nether/End/Overworld each invading each other), preserving host dimension restrictions, rare rates, and dedicated spawn-near showcase preset. The biome ID layer exists experimentally; complete terrain, vegetation, structures and climate transitions are not yet proven.

- **Nether:** lava-field, cooled basalt rifts, hot springs, lava tubes.
- **End:** hoodoo/badlands spires, pale playa, sparse eroded plateaus.
- **Overworld:** mangrove wetlands, green riparian fans, carbonate terraces in alien realms.
- Must validate actual source material and biome assignment independently, and not imply all six terrain versions already exist.

## QA per feature
- Determinism for fixed seed, including negative world coordinates.
- No block edits beyond decorating chunk; height bounds always enforced.
- No uncontrolled liquids, fluid columns, bedrock/structure writes or river closures.
- Placement/biome filters and dimension gates; no accidental global vanilla preset activation.
- Persistent Anvil real-block evidence, not only JSON registration or biome names.
- Performance budget at long-distance chunks and compatibility with existing ecotones.
- Record implemented/tested/merged separately; do not mark PLANNED items complete.

### Research
- USGS Desert Features: https://pubs.usgs.gov/gip/deserts/features/
- USGS Alluvial Fan: https://www.usgs.gov/news/science-snippet/earthword-alluvial-fan
- NPS Bryce hoodoos: https://www.nps.gov/brca/planyourvisit/faqs.htm
- NPS Yellowstone travertine: https://www.nps.gov/yell/learn/kidsyouth/places.htm
- NOAA mangroves: https://oceanservice.noaa.gov/facts/mangroves.html
- NPS lava landforms: https://www.nps.gov/subjects/volcanoes/lava-flows.htm
