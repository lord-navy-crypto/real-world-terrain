# TVA Earth Systems 2.x — Geomorphology and Habitat Expansion

Status: integration design + first implemented patch; **not** a claim that all landforms below generate. Grounded in USGS Desert Features, USGS Mojave Open-File Report 2004-1007 and USGS landform definitions.

## Non-regression contract
- No removal of existing presets, caves, rivers, large mountains, sky-island generation, radial climate, Nether/End expansions or vanilla oceans.
- Preserve cold high-altitude deserts: aridity and temperature are independent; snow may remain above dry terrain.
- Preserve explicitly requested **unrestricted open river overflow**. Waterfall/flooding is a feature, not a reason to suppress a source.
- Preserve deterministic seed-based worldgen and do not write into ungenerated neighboring chunks.
- Preserve measured USGS provenance, blending and compatibility with existing saves; migration must be intentional and verified.
- Only use vanilla blocks initially. Do not imply a named biome exists until biome selectors and registration actually select it.

## Requested geography matrix

| Landscape | Geological condition | Climate/water condition | Worldgen expression | Verification |
|---|---|---|---|---|
| Alpine cold desert / periglacial desert | High dry plateau, scree, frost-shattered rock | Low moisture, low temperature, thin snow permitted | Exposed stone, gravel, snow, sparse tundra vegetation | Snow is not suppressed by desert designation |
| Dry mesas / rock deserts | Erosional caprock, sparse isolated residuals | Arid | Anchored mesa relief + stone/gravel/terracotta accents | Terrain continuity and preserved structures |
| Saline playa / seasonal salt lake | Closed basin with very low relief | Episodic inflow, intense evaporative balance | Pale clay/calcite flats; seasonal water only after hydrology supports basin outlets | No false permanent water, no cross-chunk damage |
| Wet valley / cloud forest | Valley under frequent moisture interception | Humid and elevation-aware | Dense moss, fern canopy, draping wet vegetation proxies | Dry basins not turned into rainforests |
| Floodplain / alluvial fan / delta | River gradient decrease, deposition and tributary flow | Connected to river network | Sediment grading into gravel, mud and clay; branching delta distributaries | Water connectivity and ocean integrity |
| Volcanic highland / lava field | Volcanic provenance, coherent eruptive footprint | Independent of latitude | Basalt, tuff, blackstone, magma/lava only where safe | No spontaneous lava from climate alone |
| Badlands | Soft strata, erosion gullies | Arid to semi-arid | Terracotta stratification and runoff channels | Dry-land exposure without universal canyon carving |
| Yardang | Directional wind erosion in soft sediments | Arid persistent wind proxy | Aligned ridges and grooves in bounded noise density | Wind-direction consistency |
| Karst | Soluble rock and groundwater | Subsurface drainage | Limestone/calcite layers, sinkholes, connected caves | No cave mouths below unsupported roofs |
| Glacial cirque / U-valley / arête | Long-term alpine ice action | Cold with sufficient snowfall | Cirque bowls, broad valleys, glacial deposits and tarns | Smoothly joins natural highlands |
| Coastal estuary and wetlands | River meets ocean | Brackish and tidal proxy | Mud, shallow channels, vegetation | Never replace vanilla seabed indiscriminately |
| Sky archipelago and sky continental landmasses | Ocean distance and far-ocean selection | Fantasy exception | Nearshore fragmented islets, distant island chains and eventual density-function continents | Island frequency, chunk-boundary continuity |

## Implementation order
1. **Delivered in integration branch:** existing desert outcrop decoration now accepts gravel, stone, terracotta and coarse dirt, without removing snow or water; tests added. Existing mesa regional type is independent and already in branch.
2. Add a shared, deterministic geography field to expose lithology, slope, basin enclosure, land/ocean distance, humidity and temperature as independent signals. Never classify all 12 landscapes by altitude alone.
3. Add feature placement and biome-modifier registration by geological eligibility, then validate frequency in saved Anvil worlds. One set at a time.
4. Connect basin/playa/floodplain/delta to a hydrology graph, conserving fluid placement behavior and open overbank flows.
5. Add cross-chunk density-based yardang, glacial and large sky continental forms with fixed world coordinates; single-chunk Feature writes are inappropriate for landmasses.
6. Reconcile source climate branch and USGS geographic embedding before shipping the integrated main release.

## Validation gates
- Unit: deterministic world coordinates (negative coordinates too), domain and bounds of every geography factor.
- Integration: generation of each nominated landscape for a documented fixed seed and coordinates; biome, Y and block-state audit.
- Minecraft: ocean water-column integrity, rivers, deep caves, structures, and roof solidity; sky-island occupancy, no cross-chunk writes.
- Performance: 95th percentile chunk generation latency and distant forceload behavior, not only successful compilation.

Primary references:
- https://pubs.usgs.gov/gip/deserts/features/
- https://pubs.usgs.gov/gip/deserts/eolian/
- https://pubs.usgs.gov/of/2004/1007/playas.html
- https://apps.usgs.gov/thesaurus/thesaurus-full.php?thcode=4
