# DH–Chunky Guard v0.3: in-game bridge diagnostics

**Scope:** a tiny independent NeoForge 1.21.1 compatibility addon for
Distant Horizons 3.3.3 and Chunky 1.4.23. This is experimental and does not
touch worldgen, shader rendering, save files, or DH's native generation planner.
Keep the user's already Mac-verified v0.1 as a rollback.

## Improvements after working v0.2
- Tracks native DH listener binding, delayed Chunky-provider checks, recovery
  signals, and total number of provider probes across process lifetime.
- Measures cumulative and maximum time spent in the early provider check.
  Nothing polls per tick; once the listener is bound there is no probe on
  regular full chunk updates. No unbounded log messages or background threads.
- Command registration uses NeoForge 1.21.1's RegisterCommandsEvent.

## In-game and dedicated-server diagnostics
Type **/dhchunkyguard status** or **/dhchunkyguard advice** in chat.
RCON/server console omits the leading slash. Status contains:
DHChunkyGuard v0.3 enabled=... probes=... notReady=... deferred=...
readySignals=... nativeBindsSinceLaunch=... avgProbeUs=... maxProbeUs=...
GPU_LOD=unverified.

These are counters **since launch** (including any worlds you entered).
A bound listener means DH registered its Chunky event handler; it does NOT
prove that DH's distant LOD geometry has been displayed correctly by
Sodium, Iris or your active shaderpack. Status does not report player
coordinates, identities, world paths, or log contents.

Advice differentiates a delayed-but-unbound listener from a completed
listener binding and reminds players to troubleshoot rendering separately.
Neither command starts, stops, throttles or changes Chunky operations.

## Acceptance tests
- Both RealScale world presets (Measured seed 2, Sacred seed 42)
  remain 2096-height and reach a real distant fully loaded chunk.
- Both patched runs must show DH native listener binding and its
  created worldgen queue; original crash absent.
- Each patched arm runs the real Minecraft RCON commands for status
  and advice and rejects missing/incomplete responses.
- Unpatched arms are diagnostics for the baseline failure signature
  and are not falsely counted as fully loaded if a crash occurred.
- Compile the addon via Gradle, verify the JAR contains only the
  own classes, and run all source-integrity, shader-triage and runtime
  contract unit checks.

**Client validation remains necessary:** once the 0.3 CI is green,
try a copied Mac game profile with no shaders, then Sodium, then Iris,
then a DH-compatible shaderpack. Compare world reload, high snowy
mountains, coastal LOD seams, and Chunky idle versus pre-generating.
Never install the 0.1, 0.2 and 0.3 addon JARs together.

Offline log diagnostics: python3 qa/neoforge/audit_lod_stack.py latest.log.
