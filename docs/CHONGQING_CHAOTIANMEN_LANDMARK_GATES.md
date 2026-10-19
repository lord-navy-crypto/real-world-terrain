# 朝天门真实地标复刻验收：权威工程参数与 GIS 缺口

**Status:** source-referenced research checklist; NOT automatically generated
Minecraft buildings. Geometry is still blocked by unavailable verified
site-specific OSM footprint data. The GIS exporter must never infer an
unmapped tower or skybridge from this written factsheet alone.

## Scope and rights

- Original Minecraft DEM origin: 29.5667°N, 106.5874°E; X east, Z south;
  horizontal 1m = 1 block; vertical DEM-relative Y ≈ elevation + 96.
- Official property and architect descriptions may validate **counts and
  coarse dimensions**. They do not grant us a surveyed footprint, tower
  coordinates, 3D mesh, photographic texture, or the right to redistribute
  proprietary CAD/BIM data.
- Model/map geometry must separately come from a legal, georeferenced source
  (e.g., independently verified OSM features licensed ODbL), measured
  survey, or explicitly labeled manually reconstructed interpretive model.
- The native UI is only a placement interface for preflighted geometry; it
  does not make the geometry authentic.

## Raffles City Chongqing / 重庆来福士

Authoritative public references:
1. Developer engineering description:
   https://www.capitaland.com/en/about-capitaland/newsroom/news-releases/international/2021/may/RCCQ-scores-hat-trick-at-the-2021-CTBUH-Awards.html
2. Developer official 2020 factsheet:
   https://www.capitaland.com/en/about-capitaland/newsroom/news-releases/international/2020/jun/CapitaLand-opens-250-metre-high-glass-bottom-Exploration-Deck-at-RCCQ.html
3. Architect project profile:
   https://www.safdiearchitects.com/projects/raffles-city-chongqing

| Measurable fact | Reference value | What is needed before world placement |
| --- | --- | --- |
| Separate towers | 8 | Eight independently sourced, positioned footprints, not one merged roof outline |
| North towers | 2 × 350 m | Distinguish tower-specific heights and site elevations |
| South towers | 6 × 250 m | Validate each tower's footprint and 3D roof profile |
| The Crystal / 水晶连廊 | 300 m long | Obtain true plan alignment, anchor points and as-built placement |
| Crystal cross-section | 32.5 m wide × 26.5 m tall | Verify enclosure shape, not a rectangular speculative bar |
| Crystal viewing level | Approx. 250 m above ground | Establish local ground datum / absolute MC Y at each support |
| Mall | 5 levels above ground | Separate retail podium from tower footprints |
| Parking | B1–B3 | Underground modelling deferred until authenticated subsurface data |
| Chaotianmen Square | Adjacent public-space axis | Requires distinct legally reusable plaza boundary/elevation geometry |

Developer identifies the development as 8 towers; of them 2 are 350 m,
6 are 250 m. Crystal is 300 × 32.5 × 26.5 m and connects 6 towers
(above 4 with additional cantilever connectors to 2 other towers).
These are useful **architectural constraints**, not substitute OSM geometry.

### Release blockers specific to The Crystal

- No automatic skybridge from `building:levels`: source-derived massing
  currently generates only vertical columns. A horizontal bridge would
  require verified start/end supports, total length, true direction, deck
  datum and non-rectangular enclosure geometry.
- If tower and bridge height are expressed from distinct ground datums,
  first reconcile relative 250/350 m heights with the DEM anchor surface.
  Do NOT confuse elevation above sea level with height above plaza.
- Save separate layer categories for footprint, podium, tower facade,
  Crystal enclosure, transport passages and plaza; do not fill all of
  these using one OSM `building=yes` feature.

## Riverfront and transport blockers

- 长江 / Yangtze and 嘉陵江 / Jialing: require licensed closed waterbody
  polygons and independent water-surface datum. A DEM terrain surface
  does not define surveyed riverbanks or a navigable water level.
- Bridge deck / tunnel / skyrail: require vertical Z **and** deck elevation
  with explicit layer separation, e.g. bridge top and clearances. An
  `OSM layer=1` tag is an ordering hint, not the true 3D height.
- In-game comparison: manually verify at least 8 separated tower footprints,
  the two 350 m towers, all six 250 m towers, Crystal connection count,
  Chaotianmen Square, first waterbank contours and at least one true
  multilevel intersection before calling this a built cultural reconstruction.

## Current evidence states

- **VERIFIED:** original DEM resource shape/hash; exporter and bounded
  plan code; source-backed public factsheet counts and dimensions.
- **SOURCE CANDIDATE ONLY:** any OSM tags found by the GIS audit;
  matching `name=重庆来福士` does not prove all eight tower outlines.
- **UNKNOWN:** authentic building/bridge/water polygon geometry, per-tower
  foundation Y, facade geometry, exact plaza altitude and actual Minecraft
  in-world placement/visual alignment.
- **NEVER MARK COMPLETE:** until authentic snapshot provenance, proper
  licensed source, client gameplay and backup/re-entry checks pass.

This is an engineering specification for future work, not an automatically
fabricated architectural blueprint.
