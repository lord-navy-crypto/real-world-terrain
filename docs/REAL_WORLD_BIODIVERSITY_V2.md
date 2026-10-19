# RealScale TVA — six real-world-inspired ecological habitats (experimental v2)

## Why a versioned biome source?

The original `realscale:natural_ecotones` codec and its 60 rules remain
unchanged. Existing worlds are never silently migrated to the new ecology
map. This update adds `realscale:natural_ecotones_diverse_v2` and six rules
that only activate in **newly selected biodiversity-v2 world presets**.

## Real ecological transitions (six NEW biome IDs)

| Biome | Transition from vanilla biomes | Interpretation | Minecraft expression |
|---|---|---|---|
| `tropical_dry_woodland` | Sparse jungle ↔ savanna | Seasonal dry-tropical tree/grass boundary | Warm drier palette, sparse savanna trees, grass, armadillos |
| `cool_temperate_rainforest` | Dark forest ↔ old-growth spruce/pine taiga | Moist, moss-rich temperate conifer transition | Deep green palette, abundant rainfall, lichen/mushrooms/forest undergrowth |
| `alpine_sedge_fen` | Meadow ↔ (frozen) river | Moist meadow at headwater corridor | Cool wet grass/flowers with wetland undergrowth |
| `polar_fellfield` | Snowy plains ↔ stony peaks | Cold, windswept rocky polar upland | Cold pale palette, sparse lichen/stony undergrowth |
| `arid_riparian_woodland` | Badlands ↔ river | Dryland river-edge woodland | Sparse trees, grass, sugar cane and wetland patches |
| `windy_coastal_headland` | Stony shore ↔ meadow/plains | Wind-exposed grassy coastal cliffs | Coastal palette and salt-spray-tolerant visual meadow |

These are **Minecraft interpretations of ecological boundaries**, not
measured climate grids, complete ecological food webs or a wetland groundwater
simulation. They do not mutate the DEM, climate budget, cave density or rivers.

## New selectable presets

- `realscale:tva_sacred_biodiversity_end3_spawn3_v2`
- `realscale:tva_measured_biodiversity_end3_spawn3_v2`

Both retain their original noise settings and dimension types. In Overworld,
the source chain is:

```
sky_ocean
└── cross_realm_spawn3  (End-invasion spawn, one of three)
    └── natural_ecotones_diverse_v2
        └── vanilla multi-noise Overworld
```

Nether remains wrapped by `cross_realm_invasion`; End by
`end_accents` wrapping `cross_realm_invasion`. Thus the dimension invasion
mechanism stays active, sky islands stay enabled in eligible offshore ocean,
and biome IDs/structures in the End remain delegated to their existing
generation logic.

The 60 original ecotones, original Sacred/Measured presets, and existing
world IDs do not change. The six v2 biomes add real vanilla vegetation and
passive-animal spawn definitions and custom biome-specific undergrowth.

## Research inspiration

- [WWF: global terrestrial ecoregions and biome diversity](https://files.worldwildlife.org/wwfcmsprod/files/Publication/file/o0z7qqe0l_Ecoregions_Map.pdf)
- [National Park Service: alpine tundra ecosystem](https://www.nps.gov/romo/learn/nature/alpine_tundra_ecosystem.htm)
- [National Park Service: Arctic ecosystems and wet meadows](https://home.nps.gov/im/arcn/ecosystems.htm)

## Release gates

- [x] Versioned codec and six climate-boundary rules on experimental branch.
- [x] Six biome resource files and vegetation modifiers, bilingual labels.
- [x] Sacred/Measured biodiversity world presets with sky islands,
      always-on invasion and existing three-End-biome spawn selected.
- [ ] `qa/test_biodiversity_v2.py` and `scripts/check_custom_biomes.py`
      pass in Actions on this branch.
- [ ] NeoForge 1.21.1 Gradle build, server boot and save-file persistence.
- [ ] Real v2 world confirms all six via `/locate biome` across seeds and
      actual vegetation/Anvil blocks, not only registered IDs.
- [ ] Worldgen speed, chunk loading, structures, mixed-mod conflicts.

Only promote to stable after actual runtime proofs. This is a Draft PR and
must not be merged directly to `main`.
