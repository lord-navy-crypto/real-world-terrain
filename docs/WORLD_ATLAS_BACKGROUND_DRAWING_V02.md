# RealScale World Atlas v0.2 — background rendering from saved heightmaps

This experiment adds a **Start background drawing** button to the World
Atlas v0.1 world map. The task runs on a **single low-priority daemon Java
worker** and progressively paints true height information from already-saved
Anvil `.mca` chunk NBT. It does NOT generate chunks or open a world server.

## Instructions

1. In Minecraft Singleplayer -> World Atlas, select a saved world.
2. Pan to the map region and select the radius (512–4096 blocks).
3. Press **Draw terrain / 后台绘图**. View the map while progress
   advances from 0 to 2304 sampled map pixels.
4. Press **Pause / Resume / Cancel** to control the task. Press Back
   to return to the world picker: **the drawing continues while Minecraft
   itself remains running**. Reopen that world and the same center/radius to
   view its latest progressive map snapshot.
5. Normal Chunky gameplay pregeneration is separate: Chunky itself is not
   bundled, copied, launched or controlled by this worker.

## What is actually drawn?

The worker reads the exact persisted region slot, sector header and compressed
NBT for one **representative saved chunk per 48×48 map pixel**. It extracts
`Heightmaps.WORLD_SURFACE` and `OCEAN_FLOOR` from real disk data. Colors
indicate observed terrain elevation (green lowlands; ochre/gray high ground;
white peaks), with a **conservative sea-level water hint**. Water detection is
not a full biome or block-state analysis. Unavailable, corrupt, partially
generated or unsupported external `.mcc` chunks remain indicated by the
previous Atlas saved-coverage colors; they are **not invented elevations**.

This is a deliberately *sampled 2D elevation map*, not a 3D GPU renderer,
satellite photography, guaranteed biome classification, cave tomography,
full-world preview of not-yet-generated terrain, or an image of future
structures. Use Chunky to pregenerate the world beforehand if desired, and
then press Refresh and redraw the Atlas map.

## Performance and persistence

- Read-only local `region/r.<x>.<z>.mca` access; checks header sizes,
  sector bounds, supported Anvil compression methods (gzip/zlib/raw),
  NBT nesting depth and 4 MiB expanded-chunk allocation cap.
- Uses signed world/chunk coordinates and handles 2,096-tall RealScale
  heightmaps (12-bit packed columns, base Y=-64) and vanilla 384-height
  heightmaps (9-bit packed columns).
- 48×48 samples maximum per draw with bounded memory, single daemon worker,
  incremental immutable pixel snapshots and cancellation / pause.
- **No writes into world folders.** The prepared map is held in memory
  only. It is not a new generated region, a Chunky task or a permanent PNG.
- The map keeps drawing if you close the Atlas screen **but stop Minecraft
  or shut down the computer and the task stops**. Persistent background
  drawing while Minecraft is entirely closed requires an explicit separate
  process and save locking; it is not implemented.
- Multiple background tasks are deliberately not launched concurrently.

## Acceptance and caveats

Automated tests include real synthetic compressed Anvil/NBT fixtures:
-2096-height bit-packed mountain samples, negative-coordinate ocean samples,
raw/gzip/zlib formats, immutable region data, bounded 2304-pixel completion,
and exact asynchronous worker behavior. Static contracts test two languages,
buttons, immutable world inputs and no hidden Chunky/second server.

Java21/NeoForge compilation is required; actual GUI click-through at
multiple screen scales and a real Chunky-present/absent modpack are still
manual acceptance tests. This feature does not claim to fix Measured/USGS
far-chunk generation or FPS, and must not be merged until the full parent
worldgen and client acceptance gates also pass.
