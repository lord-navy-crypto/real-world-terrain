# RealScale World Atlas v0.1 — offline save coverage and Chunky guidance

**Development candidate only.** NeoForge 1.21.1, Java 21. Built on unified
TVA PR #46. This is **not** an in-game terrain renderer, an embedded Chunky
copy, nor a background server/pregeneration engine.

## Where to open it

1. Minecraft -> Singleplayer (Select World).
2. Click **World Atlas** at the top right of the vanilla world list.
3. Pick a local saved world (folder containing `level.dat`).
4. The map shows a **read-only 48x48 grid** of persisted Anvil
   region-location-table slots centered on X/Z=0 by default.
5. Pan X/Z by 512 blocks and change square preview radius among 512,
   1024, 2048 and 4096 blocks. Press Refresh to read saved coverage again.
6. Green: all saved slots in that coarse map cell; amber: partial; grey:
   no saved slots. The title shows the exact count for the selected
   square. This indicates **saved slots**, not FULL chunk-status proof,
   biome details, height values, completed Chunky work or FPS.

All save I/O is asynchronous, bounded to 4096 blocks radius, read-only,
uses `region/r.<rx>.<rz>.mca` location headers, and never loads the world.
Directory symlinks are refused. Region header entries with missing sectors
or offset past EOF do not count as present. The game will never start a
hidden server, overwrite saves or force-generate chunks from this UI.

## Chunky is a separate optional add-on

World Atlas detects whether the `chunky` mod is installed, and has a
**Copy Chunky steps** button. Its clipboard contains FOUR INDIVIDUAL
commands for the current Atlas centre and radius:

```text
/chunky center 0 0
/chunky radius 1024
/chunky shape square
/chunky start
```

In v0.1, go **back** to the vanilla world list, enter the world normally,
and run those commands **one at a time** with Chunky installed. Chunky
handles its own task progress, pause/continue and generation status; it is
never bundled or secretly invoked by RealScale. If Chunky is absent,
World Atlas remains useful as a read-only saved-chunk map.

Even with Chunky, a singleplayer world will not keep generating
automatically after exiting Minecraft. Offline background generation needs
a separate explicit, supervised server process, exclusive world-directory
lock, user cancellation and controlled shutdown. This is intentionally NOT
part of v0.1.

## Planned stages — not yet implemented

- v0.2: inspect real heightmaps, water, biomes from persisted chunk NBT and
  display terrain colors, not just region occupancy; design a bounded
  Chunky command-adapter for supported in-world use.
- v0.3: create-new-world-without-entering workflow and optionally an explicit
  offline background-server session, with saved-world locking and safe exit.
- v0.4: richer overlays (cliffs, basins, waterways, cave/water layers) and
  validated long-running task status. No map may fabricate blocks/chunks,
  silently move existing-world seed or force structures off.

## Acceptance criteria

- Pure Java synthetic Anvil file checks, negative region positions,
  truncated/unallocated slots, bounded radius and no-write parity.
- Existing world-list UI and language smoke tests.
- Java21 NeoForge client compile and runtime GUI interaction on a real
  player machine, including GUI-scale 2/3, many saves and Chunky present/
  absent. Static tests are NOT substitutes for real interactive checks.
- All pre-existing 14 worldgen/quality workflows on exact final SHA remain
  the release gates; nothing in Atlas may disable terrain QA or change
  worldgen math, sky islands, cave structures or 2096-high terrain.
