# Microhabitat expansion: 40 candidate ecological structures

This is a **design/backlog catalog**, NOT an implementation claim. All structures use vanilla Minecraft blocks as building material unless stated otherwise. Real-world ecology motivates placement, but Minecraft approximations need visual testing.

## Temperate / boreal forests
1. Nurse logs: horizontal decayed logs, moss carpet, ferns, mushrooms.
2. Stump decay rings: stump log cross sections, moss, mushrooms.
3. Standing snags: bark columns with sparse attached vines, no artificial loot.
4. Root-throw pits and mounds: paired shallow depression and raised dirt.
5. Understory fern glades: shade, moisture and gentle slopes.
6. Mossy boulder gardens: stone boulder, moss carpet, sparse ferns.
7. Forest-gap succession: gap with saplings and coarse woody debris.
8. Needle litter simulation: podzol, coarse dirt, leaf-litter illusion from existing blocks.
9. Fungal rings: sparse mushrooms in approximate arcs, never circular monoculture everywhere.
10. Fallen branch piles: short sideways logs or fences with strict support.

## Rivers, floodplains, wetlands
11. Bank sedge stands: compact grass on damp earth above water.
12. Flood-debris lines: wood and leaves on high-bank elevation bands.
13. Point bars: inner meander gravel/sand, requires hydraulic bend detection.
14. Mid-channel islands: stable vegetated patches only where flow width allows.
15. Oxbow pond margin: mud, clay and wet vegetation in abandoned bends.
16. Wetland hummock/hollow microrelief: raised rooted hummocks and watery hollows.
17. Spring-fed moss seep: slope-base damp moss without fabricating huge water columns.
18. Reed/sedge islands: shallow freshwater fringe, air/water survival rules.
19. Beaver-style stick accumulation: animal-associated, only after hydrology protection exists.
20. River cobble benches: rounded gravel/stone material at floodplain transitions.

## Alpine, subalpine, tundra
21. Scree aprons: rockfall footslope with slope and sediment stability.
22. Snowbed edge vegetation: small plants at seasonal snow boundaries.
23. Frost-sorted stone polygons: coarse dirt and gravel rings, low-slope tundra.
24. Solifluction lobes: stepped gravel/coarse dirt stripes on cold gentle slopes.
25. Alpine cushion-plant analogue: compact moss/grass patches in sheltered pockets.
26. Cliff crack refugia: moss, tiny grasses in supported fissures.
27. Glacial erratic boulders: isolated stones on glaciated terrain.
28. Moraine micro-ridges: gravel bands along former ice edges.

## Desert, badlands, savanna
29. Biological soil crust illusion: patches of brown terracotta/coarse dirt and sparse moss where climate allows.
30. Shrub resource islands: shrubs with coarse dirt and protected grasses.
31. Windward/leeward dune vegetation: sparse dead bush and grasses in stable sand edges.
32. Wadi pebble ribbons: dry drainage traces, no false permanent water.
33. Badlands talus fans: terracotta and red sand on erosion footslopes.
34. Termite mound analogue: vanilla mud/packed mud mound tied to future colony activity.
35. Ant nest mound analogue: small dirt/coarse dirt cones with colony ownership data.
36. Savanna nurse-tree grass ring: grass under scattered trees where shade permits.

## Coastal
37. Strandline driftwood: short weathered logs above usual tide.
38. Dune pioneer grasses: sparse grass on stabilized dunes.
39. Salt-marsh hummock: mud/grass above water with tidal affinity.
40. Coastal gravel berm: sand and gravel bands marking storm deposit edges.

## Mechanism requirements
- Stage A uses configured/placed features and biome modifiers as approximate climate proxies.
- Stage B introduces environment sampler with local water-distance, slope, light, substrate and topography.
- Stage C adds bounded deterministic structure placement and explicit chunk-safe no-overwrite rules.
- Stage D enables limited ecology succession only where performance metrics and world save behavior permit.
- Never modify rivers using ecology feature decoration; do not place water into dry basin guesses.
- Never infer successful generation just from valid JSON. Dedicated-server tests and actual chunk observations remain mandatory.

## References
US Forest Service coarse woody debris: https://decaid.fs2c.usda.gov/views/ecosystem_processes.html
USGS biological soil crust fact sheet: https://pubs.usgs.gov/fs/2001/0065/
USGS USNVC fell-field ecology: https://data.usgs.gov/usnvc-explorer/unitDetails/900054
