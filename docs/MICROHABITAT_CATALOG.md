# RealScale Microhabitats — ecological design catalog

## Design principle
A microhabitat is defined by substrate, moisture, light exposure, gradient, disturbance, and succession stage rather than biome ID alone. Vanilla biome IDs are presently coarse proxies; future RealScale environmental samplers should gate each feature.

| Habitat | Physical drivers | Vanilla block palette | Candidate generation |
|---|---|---|---|
| Nurse log | old forest, high moisture, decaying wood | spruce/oak log, moss carpet, fern, brown mushroom | oriented 3–9 log sections with ground support and no cross-chunk mutation |
| Root wad / driftwood | river bank and high-water deposition | stripped log, roots, gravel, mud | floodplain-only structure, not in the live channel |
| Sediment point bar | inner bank, low energy | sand, gravel, clay, mud | hydrological bank-side adapter after river connectivity verification |
| Woodland fungi | shaded moist forest and podzol | brown mushroom, red mushroom, moss | small controlled patches |
| Forest gap | fallen trees, locally open canopy | sapling, fern, grass, logs | gap identification before vegetation placement |
| Talus / scree | steep rocky slope or footslope | stone, andesite, gravel | substrate patches with slope detection and stable surface checks |
| Rock fissure plants | rocky cracks with moisture | moss carpet, fern, grass | crack geometry sampler |
| Dry shrub islands | arid substrate, sparse cover | dead bush, coarse dirt, sand | isolated drought-tolerant clusters |
| Vernal pool | enclosed low-slope depression with seasonal flooding | water, mud, clay, short grass | only after watertight depression validation, avoid river edits |
| Alpine snowmelt ribbon | snowline, meltwater, seasonal thaw | snow, stone, moss, grass | seasonal moisture model |
| Tundra patterned ground | frost action, low vegetation | coarse dirt, gravel, snow, moss | spatial patterns matched to frozen biomes |
| Wetland hummock | saturated soil with raised rooting positions | mud, moss block, roots, grass | tiny emergent islands above local water |
| Coastal wrack | above normal shoreline, storm deposition | stripped logs, leaves, seagrass | coast-only wave deposition detector |
| Beaver-like woody dam | species habitat with channel narrowness | logs, sticks approximation, mud | future fauna module; never block main river on generation |
| Ant mound | dry soil, grassland, colonies | coarse dirt, dirt, grass | fauna-driven colony metadata |
| Badlands eroded ledge | sedimentary slope, aridity | terracotta, red sand, dead bush | slope and substrate-preserving decoration |

## Initial feature files committed on this branch
- river fern; swamp moss; old-growth forest fungi; alpine gravel/scree proxy; arid dead-bush clusters
- All are **experimental JSON worldgen registrations**, not proof of game runtime placement or ecological fidelity.
- Not implemented yet: complex structures, seasonal water dynamics, real proximity-to-water checks, protected-structure masks, slope-dependent scree deposition, animal-driven structures.

## Acceptance gates
- Inspect registry and biome modifier decoding on a dedicated Minecraft 1.21.1 NeoForge server.
- Verify all configured features are supported by Minecraft's feature codecs and block survival checks.
- Confirm gravel cannot form suspended columns or unstable cascading patches.
- Test multiple seeds, chunk edges, mountain slopes, river edges, and spawn-zone time budget.
- Exclude generated waterways, user structures, protected zones, and above-water plants on submerged surfaces.
- Compare water coverage, connectivity, terrain height, server chunk generation times to validated hydrology branch (when available).
- Treat biome-ID matching as a temporary approximation, not a physically based moisture/slope classifier.

## Ecological sources
- USFS Ecosystem Processes Related to Wood Decay: https://decaid.fs2c.usda.gov/views/ecosystem_processes.html
- USGS Riparian Vegetation and Fluvial Geomorphic Processes: https://www.usgs.gov/publications/riparian-vegetation-and-fluvial-geomorphic-processes
- EPA Vernal Pools: https://www.epa.gov/wetlands/vernal-pools
