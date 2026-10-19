# Global Highlands & Basin Expedition — Eight Regional Landform Analogues

This is a NEW, separately selectable **procedural analogue world**, not a
georeferenced 1:1 replica or a substitute for the USGS/Chongqing DEM/GIS
reconstruction. It is inspired by real morphologies and deliberately does not
assign fictional features to real map coordinates.

## Geography / eight morphologies

| Region interpreted | Regional landform | Procedural height-shaping approach |
| --- | --- | --- |
| Qinghai–Tibet Plateau | Broad high cold plateau | 2.7 density-unit uplift across a large shelf |
| Tibetan interior basins | Broad U-shaped plateau valley | Twin higher shoulders / wide central floor |
| Peru–Bolivia Altiplano | High plateau with enclosed basin | Shelf and raised bounding ring, shallow closed depression |
| Equatorial Andes / Ecuador | Isolated volcano cone | High massif with shallow crater; climate is separately generated |
| Sichuan Basin | Low interior, surrounding hills | Subdued floor, gentle internal hills and raised margins |
| Chongqing landscape | Parallel ridges and incised winding gorge | Ridge field perpendicular to a sinuous lowered valley |
| Yunnan–Guizhou Plateau | Karst fengcong peak clusters / dolines | Multiple conical knolls with residual negative depressions |
| Yungui 'bazi' | Intermontane flat-bottom depression | Broad level valley floor and surrounding raised rim |

**Important:** Real-world altitude is not set explicitly in metres. The
values modify the RealScale horizontal density signal, which already carries
regional baseline relief and altitude climate. There is no assertion that
Peru's salt flats contain saline water or that a Chongqing river channel
flows continuously just because the terrain looks like a gorge. Geological
lithology, tectonics, dams, river routing, deep groundwater and vegetation
are much more involved than a height signal.

## Implementation

- New pure-Java `RegionalLandformMath` provides deterministic profiles, 4,096
  block region cells, site-local smooth boundary falloff and a non-destructive
  inland/upland eligibility gate.
- New `realscale:regional_landform` codec wraps the original mountain-relief
  height signal before vertical gradient, aquifers and cave carving, without
  editing historical density function resources.
- New `realscale:tva_regional_atlas` noise settings preserve the Sacred
  original sea level, caves, fluids, temperature, vegetation, surface rules,
  and original height signal dependency.
- New selectable `realscale:tva_regional_landform_expedition_three_realms`
  preset keeps the existing cross-realm End arrival pad, Grand Nether/End,
  rich v3 ecotone biomes and offshore sky archipelago.
- The measured `realscale:usgs_big_sur_v2` profile and existing exact
  Chongqing OSM/DEM project are **not** modified. Real GIS-georeferenced
  Chongqing/Sichuan/Yunnan maps require local DEM and datasets.

## Scientific reference examples

- USGS, Oligocene–Miocene northward growth of the Tibetan Plateau:
  https://www.usgs.gov/publications/oligocene-miocene-northward-growth-tibetan-plateau-insights-intermontane-basins-west
- Chinese Academy of Sciences, Guizhou peak cluster depression karst:
  https://english.gyig.cas.cn/ps/rpAbstracts/202007/t20200706_240221.html
- Geomorphology 2020, Guizhou epikarst peak-cluster-depression:
  https://doi.org/10.1016/j.geomorph.2019.107015

## Quality gates

- [x] Eight morphologies coded, opt-in density registry and menu world preset.
- [x] Original noise settings stay available and unmodified.
- [ ] Pure-Java deterministic seam/unit test and static profile preservation.
- [ ] NeoForge server boots real atlas preset, End arrival platform persists.
- [ ] Render actual 4096-block atlas regions and sample physical terrain heights
      from Anvil for distinct regions and independent seeds.
- [ ] Check performance and chunk/structure/cave regressions.

DO NOT merge this experiment directly into main. Draft PR only.
