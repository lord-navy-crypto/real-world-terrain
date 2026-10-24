# RealScale far-distance rendering and refresh: client acceptance (Minecraft 1.21.1 / NeoForge)

This is a **real client/GPU test**, not the same thing as the server-side `distant_refresh` RCON check or the saved-heightmap Atlas raster.

## Version / integration prerequisites

- Keep the Minecraft 1.21.1 **NeoForge** profile identical across before/after captures. Record the full exact RealScale, Distant Horizons, Chunky, Iris and Sodium JAR filenames, driver and GPU.
- The official Distant Horizons 3.3.3 **1.21.1 neo/fabric** build (published 2026-09-29) documents a change to *disable its own world generation only while Chunky world generation is active*. Do not force another RealScale background chunk generator or claim the Chunky/DH bridge fixed the renderer.
- Iris' official site supports NeoForge 1.21.1+, but **only shader packs explicitly compatible with Distant Horizons** can be accepted. First prove the LOD renders without shaders, then repeat with a supported pack.
- Official sources:
  - https://www.curseforge.com/minecraft/mc-mods/distant-horizons/files/9004764
  - https://modrinth.com/mod/distanthorizons
  - https://irisshaders.dev/

## Reproducible three-location test

Use a COPY of a real 2096-high RealScale world, never an irreplaceable original save. Set a reproducible seed, record world preset, keep all vanilla structures and fluids enabled, and pre-generate distant terrain around these block X/Z targets in **separate Chunky tasks**:

| Position (block X/Z) | Saved chunk (X/Z) |
|---|---|
| 1024, 1024 | 64, 64 |
| -2048, 1536 | -128, 96 |
| 4096, -2048 | 256, -128 |

1. Confirm `distant_refresh` RCON's first pass fully **generated and persisted** all three chunks, second pass **reacquired FULL chunk tickets**, and saved Anvil block/biome/heightmap content was unchanged. A forceload ACK or completed Chunky command alone is insufficient.
2. On the client with DH installed, compare **same camera X/Y/Z, yaw/pitch, render distance and weather/time** with DH rendering ON and OFF. Set vanilla render distance low enough (e.g. 8 chunks) and DH LOD radius large enough to include the target (e.g. 256 chunks, within hardware budget). Check that the distant target is beyond the vanilla render limit and that its actual profile appears when DH is ON.
3. Before enabling shaders, let DH actually build/load its **own persisted LOD** data; generating the Minecraft Anvil chunks with Chunky **does not automatically prove the DH LOD database contains those chunks**. Record DH status/logs and capture screenshot with debug overlay, with far mountain outline visible at each target.
4. Return from another distant area, reopen the world, and revisit the exact saved camera. Confirm the old LOD is not stale or blank, the new region becomes visible after DH processes it, no holes/flicker/black slabs remain and no exception or unscheduled chunk regeneration occurs.
5. Repeat with Iris enabled and an **explicitly DH-compatible shader pack**, and then with shaders OFF again. Mark separate results for base DH, DH+Sodium, DH+Iris and DH+Iris+shader (whichever are actually installed). No unsupported shader combination may turn a no-shader PASS into a claim of universal shader compatibility.
6. Record a 30-60 second capture or frame-time log at each fixed camera: FPS mean/p95 frame time, CPU load, heap/GC, GPU/VRAM, worldgen queue, LOD generation progress, saved target existence and exact mod versions. Do not claim a performance win from a single screenshot or from a server-only load duration.

## Pass/fail evidence

| Contract | Required evidence |
|---|---|
| Real terrain generated | Server FULL, saved block/biome/heightmap semantic parity |
| Atlas distant map refreshed | Fresh read-only heightmap colors after Chunky clean STOPPED, including compact/padded 12-bit Anvil |
| DH base LOD visible | Actual client screenshot/capture beyond vanilla distance with DH ON and comparison OFF |
| DH LOD refresh | Before/after photo at same camera after save and DH generation, with logs and no false placeholder |
| DH with Iris shader | Separate visible screenshot using a confirmed DH-compatible shader |
| Stable/fast | Repeatable matched client frame-time logs with exact runtime modpack and same view |

**Do not mark GPU visible LOD as PASS based solely on RCON, server Anvil, a World Atlas raster, LOD cache file size, or an enabled DH configuration toggle.** The atlas is a read-only view of saved Minecraft terrain, not the native Distant Horizons OpenGL/Vulkan renderer.

Failures should attach: exact game/mod versions, world preset/seed, target coordinates, same-camera before/after screenshots, latest.log (redact private paths), `distant_refresh` timing JSON and whether shaders were enabled.
