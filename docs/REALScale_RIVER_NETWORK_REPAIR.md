# RealScale TVA River Network Repair — Phase 1

Target: Minecraft 1.21.1, NeoForge, Java 21.

## Verified scope

The previous `RiverChannelFeature` creates local water/gravel columns in
RealScale river biomes. It is NOT a watershed-scale hydrology engine.
Its recent navigation-first three-water-block path is experimental and can
spread vanilla water through open slopes. Do not assume all biomes form a
continuous boat passage, or that floodwater cannot impact server tick time.

The experimental waterway/hovercraft branch is intentionally preserved; this
branch adds regional hydrology groundwork and improved saved-world QA.

## Implemented

1. `WatershedRoutingMath`: isolated, deterministic D8 routing of **strictly
   descending** DEM grid cells with explicit outward flow direction and local
   runoff accumulation. Confluences produce a higher Strahler stream order.
   Uses only coordinates and supplied immutable elevation/runoff data.
   Bounded region arrays and linear graph traversal. No writes or Minecraft
   chunk loads.
2. An in-chunk 16×16 surface-height snapshot in RiverChannelFeature is
   computed before any surface water modification, reducing traversal-order
   dependence of river-channel height decisions. Existing non-natural-block
   avoidance and per-chunk write boundaries remain.
3. Saved-world Anvil QA now distinguishes a *wet river biome* from a plausible
   navigable corridor. It reports contiguous depth>=2 liquid-water columns,
   within-height-step 0 (vanilla boat proxy) and 1 (experimental skimmer
   geometry), drops over one block, and cross-chunk corridor continuity.
4. Standalone Java / Python unit tests, plus the existing NeoForge/worldgen QA.

## Limitations: NOT YET a complete watershed solution

- Regional accumulation must not be treated as global discharge: inflow from
  outside a computation tile is not included. A production solver must
  synchronize/hydrologically stitch tile-edge upstream inflows, use a region
  larger than the target chunks or obtain precomputed global flow accumulation.
  **This math is not wired into worldgen to decide river width yet.**
- D8 descending-only paths terminate at closed depressions and flats. Natural
  lakes, floodplain wetlands and lake spillway solving need a dedicated
  depression-filling/routing layer; do not silently redirect water uphill.
- Realistic bed formation should use accumulated catchment discharge, local
  gradient, erodibility, alluvium and climate. Main channels can widen
  downstream, and naturally dry headwaters should be permitted.
- River bends, meanders, oxbows, braid bars, fans, deltas, and groundwater
  exchange require separate basin-scale procedural modelling. Keep native
  vanilla ocean and original seabed/ocean structures unaffected.
- Open vanilla fluid sources at different highland heights may trigger
  expensive and far-reaching fluid updates. Test GC, fluid tick costs and
  water spread. Hovercraft is a vehicle prototype, not flood prevention.
- Geometric water-column connectivity is only a proxy for gameplay: boat
  clearance, ice caps, steering, waterfalls and fluid current need hands-on
  Minecraft tests. No performance or whole-world navigability claim.

## Release gate

Run all current QA, especially:
```sh
python3 -m unittest discover -s qa -p 'test_water_connectivity.py' -v
python3 -m unittest discover -s qa -p 'test_river_navigation_audit.py' -v
python3 -m unittest discover -s qa -p 'test_river_channel_registration.py' -v
```
And Gradle Java 21 tests/build + dedicated-server saved-Anvil river audits.
Use matching world seed and route, fresh generated chunks, monitor water spread
and normal FPS/tick performance. Never test experimental hydraulic changes by
irreversibly altering a valuable saved world.

Sources: USGS Circular 1139, interaction of streams and groundwater
(https://pubs.usgs.gov/circ/circ1139/htdocs/natural_processes_of_ground.htm);
USGS Stream Channel and Floodplain Metric Toolbox and User Guide
(https://www.usgs.gov/data/stream-channel-and-floodplain-metric-toolbox-and-user-guide).
