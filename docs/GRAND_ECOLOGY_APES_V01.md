# Grand Ecology — AP Environmental Science landscape experiment v0.1

**Experimental branch only. Do not merge before CI, visual and survival QA.**

## Implemented, not merely proposed

Six registered NeoForge 1.21.1 surface features built exclusively from
vanilla blocks:

| Feature | Selected habitat | Landscape effect |
| --- | --- | --- |
| temperate_rainforest | old-growth spruce/pine taiga | Rare giant 18–29-block conifer silhouette |
| savanna_canopy | savanna / plateau | Rare 10–16-block acacia with broad umbrella crown |
| chaparral_scrub | semi-arid scrub / savanna / heath | Short irregular oak-leaf shrub island |
| grassland_mosaic | plains / meadow / steppe | Clustered low grass and occasional poppy |
| alpine_tundra | stony/alpine/mountain meadow | Supported stone, andesite and hardy grass patches |
| floodplain_bank | floodplain & nearby wet meadow | Fern/moss mosaic only within 5 blocks of existing surface water |

Features must be inspected in real saved Anvil worlds; registration, Java
compilation and server boot alone cannot prove visual realism or frequency.

## World selection

The NEW opt-in `realscale:grand_ecology_three_realms` world preset combines
the previously implemented **natural ecotone source** with the existing
`realscale:realism_max` overworld noise settings and the **unchanged**
grand Nether/End dimensions inherited from PR #7.

NeoForge `add_features` biome modifiers are **global within this
experimental build**, even when another preset is selected. They enrich
selected vanilla biomes in any world generated with this build. The opt-in
world preset applies only to the natural ecotone biome-source changes.
This is why the experiment is isolated on its own branch and must not be
described as entirely preset-gated.

## Invariants / safety

- No modification to noise routers, aquifers, river-carving logic, oceans,
  base biome climate distribution, vanilla structures or cave density.
- Natural surface features never overwrite existing solids, leaves, fluids,
  snow, structures, cave entrances or block entities.
- Trees preflight every planned block, then commit as a whole.
- All new decoration reads/writes are restricted to the initiating chunk.
- Floodplain features read existing water and never create or replace water.
- No biome IDs are replaced for river, frozen river or ocean in ecotone mode.

## Limitations / next iterations

- The APES terrestrial biomes describe ecological categories, not exact
  vanilla IDs. Temperate rainforest is presently approximated in a subset of
  old-growth taiga; this does not create a separate full temperate rainforest
  climate biome yet.
- Terrain-dependent mountain treelines, altitude corrections, climate/rain
  shadow and broader 1,000–4,000-block landscape zones are **not implemented**.
- Unlike global biome sampling, placement features can be partially rejected
  near chunk borders or existing vegetation; measure real acceptance rates.
- Realistic full-size rainforest canopy and hollow mountain Overworld system
  remain a later isolated density/feature project.

## Acceptance checklist

1. Run `python3 scripts/check_grand_ecology.py` and Gradle NeoForge `build`.
2. Create a NEW world selecting `realscale:grand_ecology_three_realms`.
3. Verify the overworld, Nether, End all boot; Nether caves and End dragon
   center remain identical to PR #7 using the same seed.
4. Inspect 20+ chunks each in old-growth taiga, savanna, plains,
   mountainous tundra and real riverbanks across at least three seeds.
5. Confirm no suspended leaves, floating logs, decaying shrubs,
   flooding/rivers blocked, vegetation under water or structure damage.
6. Compare chunk-generation throughput and memory against the baseline.
7. Inspect natural ecotone strips, including cold/wet boundary artifacts.

## Reference

- College Board, *AP Environmental Science Course and Exam Description*,
  Unit 1.2 Terrestrial Biomes and Unit 1.3 Aquatic Biomes:
  https://apcentral.collegeboard.org/courses/ap-environmental-science
- National Park Service, elevational vegetation zones:
  https://www.nps.gov/

## v0.2 APES Unit 1 category completion (code, not final ecology simulation)

Nine APES terrestrial categories now each have a targeted decoration feature.
Four more underwater features cover freshwater riverbed, ocean-floor seagrass,
warm-water reef undergrowth and estuarine/mangrove shallows. The terrestrial
tundra is distinct from alpine tundra. **See the truthful per-category matrix:
`docs/APES_ECOLOGY_COVERAGE_V02.md`.**

This does NOT add a global climate redistribution solver or change original
ocean biome choices. New marine plants replace only eligible source water with
standard water-compatible vanilla plants. Do not infer actual lake salinity,
coral community composition, or real-world aquatic productivity.
