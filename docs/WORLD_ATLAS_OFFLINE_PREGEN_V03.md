# RealScale World Atlas v0.3 — real offline chunk pre-generation (experimental)

**This is a genuine world-writing experiment**, not the earlier read-only
World Atlas terrain painter. It launches a *separately installed* local
NeoForge dedicated server to the EXACT existing save and delegates every
chunk-generation task to an independently installed Chunky mod.

## Why this does not run two generators

Chunky is the ONLY pre-generation scheduler. The Atlas button never calls
Minecraft `getChunk`, `forceload add`, creates its own task scheduler or
repackages Chunky. It sends the supported NeoForge Chunky console command:

```text
chunky start minecraft:overworld square 0 0 512
```

The dimension key `minecraft:overworld` comes from Chunky's NeoForge
`NeoForgeWorld.getName()`, not the save folder name. Chunky handles existing
chunks, async generation, task persistence, and work allocation. An
already-running or persisted task can require the player's decision to
continue; World Atlas never sends Chunky's destructive `confirm` or
`cancel` command automatically.

**Important limitation:** just having Chunky installed in the Minecraft
*client* is not sufficient for offline generation. Minecraft has to run a
real server to generate chunks; this experimental version uses a
**separate local dedicated server installation**, rather than silently
starting two integrated worlds or building a second Chunky replacement.

## One-time setup (macOS / Linux only in v0.3)

Install the **matching Minecraft 1.21.1 NeoForge dedicated server** yourself
using the official NeoForge installer, in:

```text
<Minecraft gameDirectory>/realscale-atlas-server/
    run.sh
    eula.txt
    server.properties
    mods/
      realscale-<matching-version>.jar
      chunky-<matching-1.21.1-neoforge-version>.jar
```

Copy every additional worldgen dependency you intentionally use in your
normal RealScale world into this server's `mods` directory too, with
compatible versions. The launcher checks the two core mod filenames exist;
**it cannot automatically prove all optional mod versions match**.
Back up the save before ever switching an existing world between differing
server/client modpacks. Never start a world with unknown missing biome or
block registries merely to test performance.

Read the Minecraft EULA and choose whether to accept it yourself. The Atlas
does **not** auto-accept the EULA or install or download software. The
separate server must have:

```properties
# in realscale-atlas-server/server.properties
server-ip=127.0.0.1
enable-rcon=false
```

We deliberately prohibit a public-address server in this workflow.
All other server files and the EULA must already be prepared correctly;
this button does not repair those automatically.

## Steps

1. **Close the selected world** and return to Minecraft Singleplayer.
   Ensure no other Minecraft or server process has that save open.
2. Open **World Atlas** and select that world.
3. Select X/Z and a pre-generation radius (start conservatively with 512
   blocks, especially in a 2,096-high RealScale world).
4. Click **后台预生成 / Pre-generate**. Atlas checks `session.lock`,
   existing `level.dat`, server setup, installed Chunky/RealScale JARs,
   localhost-only configuration and the EULA. If anything is missing, it
   refuses to launch and displays an error.
5. The local server starts headlessly with `--universe` pointed at the
   Minecraft saves folder and `--world` pointed at the selected existing
   save name. After the server reports ready, Atlas submits the one
   explicit Chunky selection. The screen displays status/log messages.
6. Use **暂停生成 / 继续生成** to delegate to Chunky.
7. **保存停止 / Save + stop** first requests `chunky pause`, waits for
   Chunky's actual task-stop confirmation (the worker writes resumable
   state), then asks the server to `save-all flush` and `stop`. Wait
   until **STOPPED** and the process is gone before opening this world
   normally. If acknowledgement is missing, the server remains running
   to protect task progress; resolve it via its own console.
8. The Atlas saved-region map and background saved-heightmap painter
   can be refreshed after the dedicated server has stopped.

**Only one Atlas offline generator per client process at a time.** The
vanilla Minecraft save lock is also checked. Another vanilla client, a
standalone Chunky server, or a separate modpack writing the same world is
not allowed; do not attempt to bypass `session.lock`. If the task already
exists, use the appropriate Chunky `continue` operation only after checking
that its saved selection is the intended one.

## What keeps running in the background?

- You do NOT join gameplay; the Minecraft window can stay on the World
  Atlas or Singleplayer screen while the separate NeoForge server runs.
- Closing Atlas does not stop a running job; you can reopen it to see the
  latest status.
- When quitting the Minecraft application, Atlas *requests* graceful
  server shutdown, but it cannot guarantee a badly hung dedicated server
  will exit. Its world lock must be free before reopening that world.
- This version supports Unix/macOS `run.sh` only. Windows `run.bat`
  support remains pending to avoid unsafe shell command handling.
- This does not remove the previously observed 180-second Measured/USGS
  generator hotspot or guarantee FPS improvement. Instead it moves
  heavy generation out of the interactive playing session.
- Chunky may take many minutes or hours for a large radius; the UI does
  not fabricate progress percentages. Any percentage shown must come from
  Chunky's actual console output.

## Tests and release gates

The CI smoke deliberately uses a *fake local shell server* to test
exclusive save locking, refusing missing server/EULA/public bind,
serialized Chunky start/pause/continue/progress commands, and the
pause-acknowledged save-and-stop sequence. That fake-server smoke is **not**
a real Minecraft performance benchmark or evidence a player can load an
existing world with the same registry set.

Before publishing, test a matching **real** NeoForge 1.21.1 server and Chunky
with an expendable/backup save on macOS or Linux; ensure single-player
re-entry works and original world settings, caves, heightmaps, structures,
ocean, sky and seed all remain correct. All historic strict worldgen CI
gates continue to apply to the parent TVA branch.
