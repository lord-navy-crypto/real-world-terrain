# RealScale TVA — real-world geomorphology extension (experimental)

## Verified baseline, do not regress

The canonical Sacred and Measured overworld preset JSONs already use
`realscale:sky_ocean` over the `realscale:natural_ecotones` biome source.
The newest unified `*_end3_spawn3` presets wrap the latter with
`realscale:cross_realm_spawn3` while keeping the outer sky-ocean source.
Thus **sky islands are enabled as a generator capability** in both profiles,
but actual islands appear only over suitably isolated deep ocean, not at
every chunk. The custom End-invasion spawn circle remains protected.
No new global sky flag is introduced here.

## Existing Earth features not duplicated

RealScale already provides optional world-space playa basins, alluvial fans,
yardang/hoodoo landforms, glacial moraine rings, travertine terraces,
karst doline rims, delta distributaries and other real-world-inspired
surface accents. These are experimental worldgen decorations, not a DEM
or glaciology solver.

## New pilot: ice-wedge-style polygonal patterned ground

- **New ID:** `realscale:geo_region_permafrost_polygons`.
- **Scope:** opt-in `GeoWondersBiomeSource` worlds, Overworld only, plus
  cold snowy Minecraft/RealScale tundra biomes.
- **Technique:** pure `PermafrostPolygonMath.isRim(worldX, worldZ)` uses
  jittered 24-block Voronoi cells; thin gravel/andesite lines over safe
  natural surfaces suggest patterned frost-heaved ground.
- **Spatial control:** the existing 256-block `GeoWonderGeometry` lattice
  selects rare (1/21) world-space sites. Only the **current 16×16 chunk**
  is written, and only if selected, dry, unobstructed, biome-matched,
  and at most 0.15 base temperature.
- **Safety:** no water or soil excavation, no fluid replacement,
  no generated world presets rewritten, no direct edits to legacy saves
  or structures. If terrain is unsuitable, the visual effect is skipped.
- **Limitations:** an illustrative surface pattern, **not** a physical
  ice-wedge process or a simulated thermokarst ecosystem.

Scientific inspiration: National Park Service,
https://www.nps.gov/articles/aps-v12-i2-c7.htm
and https://www.nps.gov/articles/aps-v6-i2-c8.htm

## Next candidates (research, NOT implemented)

1. **Drumlin fields:** subglacially sculpted, elongated sediment hills
   with a consistent regional flow orientation. Should use height/density
   logic, not merely a pile of gravel.
2. **Esker systems:** winding meltwater sediment ridges, ideally following
   a stable regional flow network rather than random columns.
3. **Kettle lakes / thermokarst:** depressions and closed-basin hydrology
   require water-level and adjacent-chunk stability checks.
4. **Pingo and solifluction lobes:** permafrost landforms to incorporate
   only with appropriate slope and climate rules.

USGS definitions: https://pubs.usgs.gov/of/2004/1216/text.html

## Release gates

- [x] Pure Java 21 geometry smoke tested offline locally
      (determinism; negative coordinates; 16-block seam coverage).
- [ ] Python resource/registry QA in GitHub Actions.
- [ ] NeoForge 1.21.1 compile + server smoke completed.
- [ ] Fresh opt-in Natural Wonders test world: actual generated Anvil
      surface blocks verified near eligible Arctic regions.
- [ ] Cross-mod, cross-seed profiling, biome density and FPS validation.

Only after the last gates pass should this optional region be considered
for a stable TVA release. Existing Sacred/Measured/sky-island/Grand End
worldgen and the always-on unified invasion design must remain unchanged.
