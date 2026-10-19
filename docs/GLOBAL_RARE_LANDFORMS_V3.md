# Global Rare Landform Expedition v3 — 24 Terrain Archetypes

## Why these places?

This is an **opt-in worldgen experiment** built from terrain morphology,
NOT a 1:1 DEM replica of any named place.

The original v1 (4,096-block sites, 8 shapes) and v2 (8,192-block sites,
16 shapes) retain exactly their own codecs and density graphs. V3 uses
**12,288-block geographic cells**, 4,150–4,900-block footprint radii,
smooth fading edges and eight new shapes:

| Source region and reference | What the new *terrain density* creates | Not yet physically simulated |
|---|---|---|
| Guiana Shield, Venezuela/Colombia **tepui** | Vast flat-top upland and steep cliff transition | Waterfalls and endemic summit flora |
| Central Suriname / tropical **inselbergs** | Several detached, broad granite-dome hills | Bedrock chemistry and inselberg plants |
| Northern Europe/North America **drumlins** | Ordered oval, ice-flow-aligned till hills | Actual ice flow / sediments |
| Finland / North America **eskers** | Thin, winding elevated ridge over flatter base | Gravel sediment layers |
| Namibia **star dunes** | Several radial dune arms and ridge crests | Wind erosion or dune migration |
| East African **rift** | Down-dropped linear graben floor and lifted shoulders | Active faults or plate tectonics |
| Utah **slot canyon** | Incised narrow, sinuous channel through higher plateau | Seasonal flash flood physics |
| Iceland **caldera** | Broad ring wall and lowered interior | Lava/eruption dynamics |

### Scientific context (primary geology / protected-land sources)

- UNESCO Guiana Shield tepuis, Chiribiquete: https://whc.unesco.org/en/list/1174
- UNESCO Canaima sandstone tepui landscapes: https://whc.unesco.org/en/list/701
- UNESCO/Suriname granitic inselbergs: https://whc.unesco.org/en/list/1017
- USGS drumlin, esker, kettle and glacial stratigraphy:
  https://pubs.usgs.gov/of/1993/0292c/report.pdf
- NPS slot canyon and narrow gorge landforms:
  https://www.nps.gov/brca/learn/nature/wallandnarrows.htm

These analogues are *geometric interpretations* of naturally shaped terrains.
Changing horizontal terrain density does not automatically generate a
fully accurate desert climate, glacial moraine gravel, permanent rivers,
proper waterfall runoff, or real marine erosion. World biome climate still
comes from the original RealScale v3 ecological source, and the engine does
not claim real measured coordinates.

## Exact compatibility contract

- Registered type: `realscale:regional_landform_global_v3`
  in the **new class** `RegionalGlobalLandformDensity`.
- Noise graph: `realscale:tva_global_landform_v3` with new
  height_signal/terrain_density/preliminary_density functions.
- New GUI world: `realscale:tva_global_landform_expedition_v3_three_realms`.
- Existing Sacred, Measured/USGS, prior regional v1/v2, and 1:1
  Chongqing OSM/DEM mapping are **not modified**.
- Overworld sky islands and protected End invasion spawn remain; Grand
  Nether and End settings, liquids, cave carving and climate noise are copied
  exactly from prior v2 global-exploration preset.
- The new biome/terrain compatibility does **not** claim that a new world
  is safe to merge until dedicated server and saved Anvil block testing.

## Release gates

- [x] Eight new worldwide geomorphologies wired to horizontal density.
- [x] Versioned codec and new GUI world preset registered.
- [x] Pure-Java 12km site extent, ocean protection, frequency and geometry smoke authored.
- [x] Python old-world/USGS, menu, noise router and biome validation authored.
- [ ] NeoForge build, static test and full CI pass.
- [ ] Dedicated v3 world boot and level.dat proving persisted codec + End pad.
- [ ] Real distant Anvil columns of all eight shapes across multiple world seeds.
- [ ] Geological transitions, biome suitability, hydrological continuity,
      environment richness and runtime performance audited.

Keep Draft. Do not merge into `main` before successful proof.
