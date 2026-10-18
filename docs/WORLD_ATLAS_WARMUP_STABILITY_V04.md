# RealScale World Atlas v0.4 — drawing, warm-up and safe pregeneration

**Experimental branch:** `feature/20261018-world-atlas-warmup-stability`, stacked on the current offline-pregeneration safety work. This is not merged into `main`.

## Three separate operations

### 1. Draw terrain — read-only map preview

- Reads `WORLD_SURFACE` and `OCEAN_FLOOR` heightmaps from already-saved Anvil chunks.
- Samples up to 48 × 48 map cells. Missing, partial or unreadable chunks are not invented as terrain.
- Uses a task-scoped LRU cache of at most 32 open region files, reducing repeated file opens during row-wise drawing.
- Runs on one low-priority daemon worker; pause, resume and cancel do not run on the render thread.
- Does not generate chunks, write PNGs, or modify world files. The map remains an in-memory preview and stops when Minecraft exits.

### 2. Warm up — a small real generation pass

The **Warm up** button starts the separately installed local NeoForge server and asks Chunky to generate a small square region with a 128-block radius around the current Atlas center. This is real chunk generation and writes chunks into the selected save; it is not a fake benchmark or a dry run.

When Chunky reports that no generation task remains, Atlas verifies task removal, sends `save-all flush`, and enters `WARMUP_READY`. The same dedicated-server process stays alive intentionally:

- Press **Pre-generate** to continue with the selected larger radius in that same server process. This preserves the warmed server JVM and avoids opening a second writer on the save.
- Press **Save + stop** if you do not want to continue. Wait for `STOPPED` before opening the world in the normal client.
- While `WARMUP_READY` is displayed, the background server still owns the save lock. Do not open that save in another Minecraft instance.

This warm-up primarily benefits the *same background server process* and the subsequent pregeneration task. It does not keep the interactive client's JVM warm, and it does not promise a particular FPS improvement.

### 3. Pre-generate — selected saved-world coverage

- Starts Chunky on the exact existing save with the selected center and radius.
- Uses Chunky's own task scheduler and actual console progress; Atlas does not implement a second chunk generator.
- Displays parsed completion percentage and chunks per second when Chunky emits its supported English progress line.
- Pause and resume wait for acknowledgements and for a paused task to drain from Chunky's active task map.
- Manual stop and natural completion both wait for the task-save/removal handshake before `save-all flush` and `stop`.
- Terminal states, including `FAILED`, remain visible in the Atlas status row rather than leaving an old “launching” message.

## One-time setup and safety

Version v0.4 still requires macOS/Linux and a manually installed matching Minecraft 1.21.1 NeoForge dedicated server at:

```
<Minecraft gameDirectory>/realscale-atlas-server/
  run.sh
  eula.txt
  server.properties
  mods/
    realscale-<exact same JAR as client>.jar
    chunky-<matching 1.21.1 NeoForge JAR>.jar
```

Configure `server-ip=127.0.0.1` and `enable-rcon=false`. Read and accept the Minecraft EULA yourself. Add any other world-generation dependencies and datapacks that the save requires, with compatible versions. Atlas verifies the RealScale client/server JAR bytes match, but it cannot prove every optional mod and datapack is identical.

Before starting:

1. Back up the selected world.
2. Close that world and ensure no other client or server has it open.
3. Open World Atlas from the Singleplayer world list.
4. Use **Warm up** for the small 128-block pass, or use **Pre-generate** directly.
5. If warm-up finishes, either continue with **Pre-generate** or choose **Save + stop**.
6. Reopen the world only after the Atlas reports `STOPPED` and the child server process has exited.

Never bypass the world's `session.lock`. If the controller reports `NEEDS_ATTENTION`, inspect the server output and lock before trying to open the save. Do not hard-kill a server that may still be writing the world.

## Verification status

Automated Java smoke tests use a fake local shell server to exercise command ordering, progress parsing, task draining, warm-up completion, same-process continuation, and safe shutdown. These tests are control-plane simulations only.

Before release, test on an expendable backup save with a real matching NeoForge 1.21.1 dedicated server and Chunky. Confirm that warm-up reaches `WARMUP_READY`, full pre-generation continues on the same process, manual pause/resume works, stop releases the lock, and the world reopens with its registries, terrain, caves, oceans, structures and seed intact.

This workflow moves work out of interactive exploration; it does not by itself repair the separate Measured/USGS cold-generation bottleneck or the GPU rendering path. Keep this branch in Draft until the real-server acceptance test and all parent world-generation release gates pass.
