# Deep Sea and Ecosystem Conservation Plan

Evidence bases:
- IUCN Global Ecosystem Typology: https://iucn.org/resources/conservation-tool/iucn-global-ecosystem-typology
- IUCN Red List of Ecosystems: https://iucn.org/resources/conservation-tool/iucn-red-list-ecosystems
- NOAA deep-sea coral habitats: https://www.fisheries.noaa.gov/national/habitat-conservation/deep-sea-coral-habitat
- NOAA deep habitats: https://oceanexplorer.noaa.gov/exploration-extras/24-national-ocean-month/

Priority ecological habitats to model:
1. Abyssal sediment plains; low productivity and sparse decorations.
2. Rocky continental slopes and seamount coral/sponge gardens.
3. Cold seep communities, kept distinct from volcanic hydrothermal fields.
4. Deep canyon rock outcrops and sediment fans.
5. Vulnerable coastal ecosystems: seagrass meadows, kelp forests, mangrove and salt marsh.
6. Grounded terrestrial rarity catalogue connected to IUCN ecosystems.

Worldgen integration principles:
- New changes must apply to every RealScale overworld preset.
- Use water depth, natural hard/soft substrate, bottom relief, and cached seed-stable regional patches.
- Use simple source-water and chunk-boundary checks.
- Never carve new seafloor, modify ore distribution, or create liquid sources.
- Existing seasons mod is authoritative; avoid duplicate seasonal state or frequent ticking.
- Conservation annotations are educational, not a claim that a Minecraft biome is officially IUCN-endangered.
- Validate with real server saved blocks and multi-seed statistics before merge.
