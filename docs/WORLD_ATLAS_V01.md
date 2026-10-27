# RealScale World Atlas — saved coverage and heightmap inspection

**Current main-branch scope.** NeoForge 1.21.1, Java 21. World Atlas reads
saved-region coverage and samples persisted heightmaps. It does not include
a Chunky command-copy button, launch external tools, start a background
server, or generate chunks from the Atlas UI.

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

## External chunk generation

Chunky, if installed separately by the player, remains an independent
in-game tool. The current Atlas does not detect it, prepare its commands,
copy commands to the clipboard, control its tasks, or invoke it. Use the
external tool's own documented interface outside World Atlas.

## Scope boundary

The earlier v0.2–v0.4 ideas for an in-Atlas Chunky adapter and offline
background-server session are not part of the current release scope. The
current implementation prioritizes read-only saved-region inspection and
bounded heightmap drawing.

## Acceptance criteria

- Pure Java synthetic Anvil file checks, negative region positions,
  truncated/unallocated slots, bounded radius and no-write parity.
- Existing world-list UI and language smoke tests.
- Java21 NeoForge client compile and runtime GUI interaction on a real
  player machine, including GUI-scale 2/3 and many saves. Static tests are NOT substitutes for real interactive checks.
- All pre-existing 14 worldgen/quality workflows on exact final SHA remain
  the release gates; nothing in Atlas may disable terrain QA or change
  worldgen math, sky islands, cave structures or 2096-high terrain.
