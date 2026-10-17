# RealScale TVA — Grand Overworld Cave Restoration (experimental)

Minecraft Java **1.21.1**, NeoForge, Java 21; for **NEW worlds only**.

## Why the caves seemed to disappear

The four-menu TVA Natural, Alpine and Canyon noise settings had their
`noise_router.final_density` connected directly to their terrain-only density
function, without the available cave/noise subtraction. Performance-only
optimizations to Grand Nether did not restore these Overworld voids. Measured
USGS had an older cave-density range guard but not a mountain-scale network.

This branch connects an actual `realscale:grand_overworld_caverns` final-density
codec to Natural/Alpine/Canyon and Measured USGS Big Sur v2. The old 1.3
`realism_max`, `himalayan`, `grand_canyon` etc. presets are unchanged;
the four public three-realm TVA world preset names and heights remain intact.

## Authoritative geological inspiration

- NPS Mammoth Cave: passage networks occur at several vertical levels, with
  large tube passages, tall canyon passages, composite cave passages, vertical
  shafts, underground drainage, and changing water table:
  https://www.nps.gov/maca/learn/nature/how-mammoth-cave-formed.htm
- NPS Carlsbad Caverns: Big Room and very large limestone chambers:
  https://www.nps.gov/cave/learn/news/interesting-facts-about-carlsbad-caverns.htm
- USGS cave / groundwater hydrology: solution openings can evolve from
  fractures into large underground drainage corridors:
  https://pubs.usgs.gov/circ/circ1139/htdocs/natural_processes_of_ground.htm
- USGS Lava Beds: lava tube caves arise from cooling volcanic flows and should
  not all look like karst solution tunnels:
  https://www.usgs.gov/volcanoes/medicine-lake/science/lava-tubes-lava-beds-national-monument
- NPS Mammoth karst entrances, surface sinkholes and vertical connections:
  https://www.nps.gov/maca/planyourvisit/mammoth-cave-map-guide.htm/index.htm

This module borrows the **geometrical patterns**, not the full geology. It
does not simulate millions of years of groundwater chemistry or impose
geologically correct lithology constraints yet.

## New in-code geometry

| Type | Procedural implementation |
| --- | --- |
| Large fossil cave halls | Four vertical levels at about Y135, 395, 790 and 1330, each independently varied |
| Grand chambers | Ellipsoid horizontal radii ~78-157 blocks and vertical radii ~44-111 |
| Ordinary passages | Some slender corridors retain exploration variety |
| Broad phreatic-style tubes | Common horizontal corridors with nominal radius 29-51 |
| Tall vadose canyon passages | Corridor width radius 24-50 and height scaled 2.25x |
| Extraordinary avenues | Radius 61-93; significantly more room than old 7-11 block tunnels |
| Long links | Hall-to-neighbor passages cross about 512 blocks, including chunk borders |
| Exceptional fossil arteries | Rare 3-cell group links spanning about 2304 blocks |
| Vertical chimneys | Interlayer connections linking upper and lower hall networks |
| Surface entrances | Rare, winding shafts tied to an actual low/middle hall and only opened on high ground |

The cave layout is generated using absolute, deterministic world coordinates.
Worker-local geometry caches avoid needing to load adjacent chunks. Adjacent
chunks query the *same* passage geometry. A seeded noise function supplies
minor wall displacement.

The `GrandOverworldCaverns` density wrapper leaves existing topography,
bedrock and sea level unchanged, and normally requires **positive density
>0.24** (an approximate protective rock roof) before removing rock.
Sparse highland entrance shafts deliberately penetrate that barrier to
produce discoverable cave openings, but stop below Y110 and are rare.
This is a **heuristic**, not a guaranteed 30-block physical roof on arbitrary
terrain, and MUST be verified with saved-world ocean/river tests.

## Non-goals / important caveats

- **Newly generated chunks change.** Do not install the cave experimental JAR
  into an irreplaceable existing save. New/old chunk transitions may be visible.
- A zero cave-density function produces no carved cave in its volume; a
  positive `GrandOverworldCaveNetwork.strength` produces a candidate void,
  but only rock that actually exists can be excavated.
- Caves above aquifer fluid levels are generally dry; deeper rooms may be
  flooded by normal Minecraft aquifers. No lava/sea level rule is overwritten.
- Even huge rooms cannot appear as solidly enclosed caves in open sky.
  Their placement needs natural covering rock. The higher levels are most
  relevant to tall mountains.
- Entrances may not appear in every tested biome. Underwater entrances remain
  deliberately restricted to protect the vanilla ocean.
- Rounded halls and smoothly varying tubes are an initial **shape** model.
  More geologically faithful limestone-dominant routing, faults, talus
  collapse, cave floor sediment, underground river courses, stalactite
  placement, and volcanic tube biomes require separately tested layers.
- This change can increase CPU costs for new world generation. No speedup,
  sustained server TPS or cave walkability claims before real profiling.

## QA and success definition

1. Source check: all four public Overworld `final_density` routers use the
   registered codec; no legacy raw-world presets regress.
2. Standalone Java: assert a spectrum of room/passage cross-sections, wide
   768-block corridors, *real* 1536-block carved route midpoints, cave
   altitude tiers, deterministic evaluation across worldgen threads, and
   discoverable highland shafts.
3. NeoForge Java 21 full build plus all CI dimensions, saved Anvil ocean and
   cave QA, and fixed-seed worldgen regression suite.
4. **In-game inspection:** for the same seed, traverse newly generated
   highland volumes in spectator; record widest dry tunnel diameter, longest
   continuous navigable route, number of multi-room connections, visible
   natural surface entrances, and per-chunk cave air volume.
5. Measure real chunks/sec, p95 chunk generation latency, CPU profiles and
   memory, and compare to the parent branch. If huge cave density overloads
   worlds, optimize spatial culling and cache, not simply remove the caves.

Until items 3-5 pass, leave the PR draft and distribute any JAR strictly
as a **test candidate**, not a stable release.
