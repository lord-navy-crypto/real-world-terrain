# RealScale: Genshin's Nod-Krai and Snezhnaya — latest 2026 geography study

Date: 2026-10-20. **Research/design only, not implemented Minecraft worldgen.**
Do not bundle game screenshots, maps, models, music, textures, other proprietary
assets or any inferred Teyvat geographic coordinates.

## Provenance and up-to-date region status

- **Nod-Krai / 挪德卡莱**: began with Lempo Isle, Hiisi Island and Paha Isle
  in Luna I (2025-09-10), subsequently expanded; official July 1, 2026 Luna
  VIII update introduced **Dunanna Pit / 杜南纳深坑** and permanent separate
  exploration area **Frost Moon / 霜月**.
  - Source: https://blog.playstation.com/2025/08/29/genshin-impact-version-luna-i-song-of-the-welkin-moon-segue-launches-september-10/
  - Source: https://www.hoyoverse.com/zh-tw/news/165102
  - Official patch transcription: https://news.17173.com/content/07012026/135514325.shtml
- **Snezhnaya / 至冬**: seventh nation opened in 7.0 (2026-08-12);
  latest official live version by Oct 20 is 7.1 (2026-09-23).
  HoYoverse's 7.0 Japanese press release specifically describes an
  extremely cold and blizzard-prone country, broad railway connectivity,
  icebound wildland, remote villages and a major industrialized city.
  - Developer/publisher's press release:
    https://prtimes.jp/main/html/rd/p/000000430.000096124.html
  - Official 7.1 notice: https://ys.mihoyo.com/main/news/detail/166090
  - Official 7.1 storyline announcement: https://ys.mihoyo.com/main/m/news/detail/166253

## Evidence-ranked visual references (never redistributed)

| Setting | Screenshot/article reference | Real Earth analog, NOT a claimed official inspiration | Reusable engineering rule |
|---|---|---|---|
| Nod-Krai — rocky snow-line bay and rugged cape | https://blog.playstation.com/2025/08/29/genshin-impact-version-luna-i-song-of-the-welkin-moon-segue-launches-september-10/ | Norway, Aleutians, Iceland and other cold maritime coasts are *visual analogues* | Exposed rock sea cliffs, wave-cut benches, asymmetric wind-exposed vegetation; tie coast to bathymetry and substrate, not a universal stone stripe |
| Nod-Krai — brown/green/snow vegetation intermix | https://opencritic.com/news/14612/genshin-impact-reveals-more-story-details-about-nod-krai | Boreal/montane ecotone and cold-ocean shore | Climate+elevation+exposure gradient: broadleaf/taiga/scrub/stone/snow; gradual transitions and deterministic weathered rocks |
| Nod-Krai — Dunanna Pit | https://news.17173.com/content/07012026/135514325.shtml | Glacial potholes, karst collapse, tectonic depressions are distinct analogues, not interchangeable | Deep pit requires process-specific geology, slope stability and cave-ceiling rules, not a giant negative Gaussian everywhere |
| Frost Moon — stark islands and blue sea | https://www.hoyoverse.com/zh-tw/news/165102 | Northern offshore islets and glacial coastal landscapes, plus fantasy moonlight | For RealScale keep terrestrial/geologic portions; no lunar magic mechanics or impossible gravity in Earth DEM preset |
| Snezhnaya — snow-bound far hills, railway, settlement | https://prtimes.jp/main/html/rd/p/000000430.000096124.html | Northern Eurasian taiga and glacial uplands | Mountain + valley macro silhouette, slope-driven snow cover, wind scoured ridge rock, sheltered forest; railway belongs to OSM/human structure data, not random geology |
| Snezhnaya — large open snowy steppe | https://gamedaily.com/games/genshin-impact-reveals-plans-snezhnaya-version-7-0-release-august-12 | Subarctic tundra/periglacial plains and seasonal snowfields | Broad low-relief surfaces, frozen basins conditioned on low temperatures; no permanent ice carpet over shallow coastal ocean |
| Snezhnaya — glacial valleys and lakes | https://games.gg/genshin-impact/guides/genshin-impact-all-snezhnaya-map-locations/ | Alaska and Fennoscandian glacial lake districts | U-shaped valleys, cirques, moraines, basins with water from genuine hydrology; retain flood/aquifer stability |
| Historical Inazuma (if user's speech meant 稻妻, not 至冬) | https://www.hoyolab.com/article/3612264 | Japanese maritime volcanic islands, coastal cliffs, humid forest | Separate volcanic coast, exposed intertidal rocks, high rainfall, wind-swept slopes and coastal fog; not a 2026 newest nation |

## What Earth science actually establishes

- NPS: glaciers produce **U-shaped valleys** with broad floor/steep walls;
  sea-flooded glacial valleys are fjords, and tributaries may form hanging valleys.
  https://www.nps.gov/articles/ushapedvalleysfjordshangingvalleys.htm
- NPS: a **fjord is not** simply an enclosed lake or a pile of cliffs; the
  former glacier channel is inundated and continuous with the sea.
  https://www.nps.gov/articles/rocky-coast-landforms.htm
- USGS: **kettles** (buried ice blocks melting), moraines (till ridge), and
  eskers (subglacial channel gravels) are distinct depositional landforms.
  https://pubs.usgs.gov/of/1993/0292c/report.pdf
- NSIDC: seasonal sea ice freezes during cold periods and may melt later;
  it cannot be modeled accurately as permanent frozen surface everywhere.
  https://nsidc.org/learn/parts-cryosphere/sea-ice/science-sea-ice

## RealScale implementation specification — not yet implemented

1. **Northern coast relief**: examine existing `tva_cinematic_landforms_v2`
   continentalness gate: currently it deliberately prevents all coastal
   large-scale changes. Future glacial fjord system MUST introduce a separate
   explicitly enabled, protected coastal mask, with ocean access and genuine
   negative-depth bathymetry, and never silently alter the USGS profile.
2. **Boreal ecotone**: reuse existing biome source, altitude, temperature,
   moisture and wind proxy. Sparse rock and shrub on exposed high points;
   taiga patches in more sheltered valleys; never turn every snowy region
   into a monoculture.
3. **Snow/ice**: temperature, season, windward/lee slope and elevation masks
   for deposition; avoid fluid/structure-block changes and unbounded
   floating snow layers. Prefer established biome/weather systems where
   available rather than adding an incompatible global snow replacer.
4. **Glacial basins and landforms**: distinguish cirques, U troughs,
   moraines, kettle lakes, fjords and wave-cut rocky coasts with different
   shape equations. Existing v2 U valley is inland-only, not a fjord.
5. **In-game acceptance**: preserve 4 player-facing menus, original old
   worlds, USGS DEM and cavern/aquifer invariants. Test at least three
   seeds, far/middle/close cameras, shoreline water continuity, ocean
   bathymetry, underwater access, and 3D surface palettes. Visual
   photographs are references; no Genshin copyrighted assets are included.

**Status distinction**: All above are researched design requirements.
No new fjord, snowfall, season, railway, settlement or moon environment
generator is added by this document.
