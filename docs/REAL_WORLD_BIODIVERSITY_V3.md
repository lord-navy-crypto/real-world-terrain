# RealScale TVA biodiversity v3 — eight additional real ecological habitats

**Experimental and additive.** `natural_ecotones` (60 original transitions) and
`natural_ecotones_diverse_v2` (60 + six additions) still deserialize their
saved worlds with the same biome assignments. Only new worlds explicitly
selected with `natural_ecotones_diverse_v3` use the 8 extra biomes, giving
74 surface transition targets (60 historical + 6 v2 + 8 v3).

| New biome | Source-climate boundary | Rainfall & ecosystem |
| --- | --- | --- |
| Mediterranean chaparral | savanna ↔ windswept forest | Dry woody shrubland / grasses |
| Temperate tallgrass prairie | plains ↔ meadow | Mesic tallgrass flowering habitats |
| Monsoon woodland | jungle ↔ savanna | Warm seasonal tropical mixed woodland |
| Montane cloud scrub | grove ↔ windswept forest | Moist, cool evergreen shrubs |
| Brackish tidal marsh | mangrove swamp ↔ beach | Tidal wet meadow margins without regrading water |
| Coastal pine barrens | stony shore ↔ forest | Exposed sparse coastal conifer woodland |
| Frost heath moor | snowy plains ↔ gravelly windswept hills | Cold rocky dwarf heath |
| Arid rocksteppe | desert ↔ stony peaks | Extremely dry rocky-steppe vegetation |

Each contains distinct biome temperature, precipitation, water and foliage
colors, selected vanilla vegetation and passive animal spawns, and a
per-biome undergrowth modifier. These are **Minecraft ecological proxies**;
they do not model ocean salinity, soil chemistry, monsoons or actual seasons.

New selectable world presets:
- `realscale:tva_sacred_biodiversity_end3_spawn3_v3`
- `realscale:tva_measured_biodiversity_end3_spawn3_v3`

Both preserve the original noise settings, existing caves, Nether/End dimensions,
spawn-zone End invasion with the guarded 9×9 pad, the End's three added biomes,
and the outer `sky_ocean` generator. Only inner Overworld ecotone source ID
changes from `natural_ecotones_diverse_v2` to `natural_ecotones_diverse_v3`.

Scientific inspiration:
- https://www.nps.gov/seki/learn/nature/foothills.htm — chaparral mosaics
- https://www.nps.gov/articles/southwest-grasslands.htm — shrub-steppe
- https://www.worldwildlife.org/resources/explainers/what-are-grasslands-and-why-do-we-need-to-protect-them/ — grasslands
- https://www.nps.gov/im/sodn/ecosystems.htm — woodland moisture gradients

## Release gates

- [x] New biome registry, JSON, climate-edge lists and menu profiles committed.
- [ ] Static catalog/serialization tests and NeoForge 1.21.1 build pass.
- [ ] Real v3 Sacred/Measured world boot, actual End spawn and persisted
      `level.dat` codec proof pass.
- [ ] Confirm every new surface biome in generated Anvil chunks and test
      natural distribution over multiple distinct seeds.
- [ ] Profiling, biome-ordering cycles, visual quality and mod compatibility.

Keep this stacked experimental PR in Draft, never merge directly into main.
