# RealScale World Atlas — read-only saved-chunk coverage

**Current scope.** NeoForge 1.21.1, Java 21. World Atlas only indexes
existing saved-region coverage. It does not sample or draw terrain heightmaps,
pre-generate chunks, preload chunks, invoke Chunky, connect to Distant Horizons,
start a server, or launch an external process.

## Where to open it

1. Minecraft -> Singleplayer (Select World).
2. Click **World Atlas** at the top right of the vanilla world list.
3. Pick a local saved world (folder containing `level.dat`).
4. The map shows a **read-only 48x48 grid** of persisted Anvil
   region-location-table slots centered on X/Z=0 by default.
5. Pan X/Z by 512 blocks and change square preview radius among 512,
   1024, 2048 and 4096 blocks. Press Refresh to read saved coverage again.
6. Green: all saved slots in that coarse map cell; amber: partial; grey:
   no saved slots. This indicates saved slots only, not FULL chunk status,
   biome details, terrain height, completed generation work or FPS.

All save I/O is asynchronous, bounded to 4096 blocks radius, read-only,
uses `region/r.<rx>.<rz>.mca` location headers, and never loads the world.
Directory symlinks are refused. Region header entries with missing sectors
or offsets past EOF do not count as present. The game will never start a
hidden server, overwrite saves, force-generate chunks or preload chunks.

## Explicitly removed

The following are not part of RealScale and must not be bundled or reintroduced:
- Offline World Atlas background painter and heightmap sampler.
- Atlas Draw, Pause, Resume and Cancel task controls.
- DH–Chunky startup guard, Mixin, server commands and standalone addon JAR.
- DH–Chunky compatibility build, runtime and bytecode-inspection workflows.
- Automatic Chunky command generation, clipboard integration or task control.

Chunky or Distant Horizons installed independently by a player remain separate
third-party mods; RealScale neither detects nor controls their pre-generation
tasks.

## Scope boundary

World Atlas is a local-save coverage viewer, not a world-generation utility.
It reads existing region metadata only. It must not introduce server-side
listeners, worldgen hooks, Mixin injection, chunk tickets, Chunky APIs,
background generation workers or hidden server startup.

## Acceptance criteria

- Pure Java synthetic Anvil file checks, negative region positions,
  truncated/unallocated slots, bounded radius and no-write parity.
- Existing world-list UI and bilingual language smoke tests.
- Java 21 NeoForge client compile and runtime GUI interaction on a real
  player machine, including GUI-scale 2/3 and many saves. Static tests are
  not substitutes for real interactive checks.
- Existing terrain world-generation QA remains unchanged.
