# Optional DH 3.3.3 + Chunky 1.4.23 integration guard — experimental

**Project:** `dh-chunky-guard/`, a separate tiny NeoForge 1.21.1 addon.
**Status:** compiles; do NOT assume a macOS or client rendering fix until independently tested.
**Source-of-truth crash:** user's anonymized stack + [upstream issue #1329](https://gitlab.com/distant-horizons-team/distant-horizons/-/work_items/1329).

## Exact upstream proof
Official 3.3.3 DH and 1.4.23 Chunky JAR bytecode was inspected after SHA512 validation.
`AbstractChunkyAccessor.tryRunFirstTimeSetup()V` checks private `listenerBound`,
then sets `listenerBound=true` *before* invoking
`ChunkyAccessor.bindOnGenerationProgressEvent()V`.
NeoForge DH immediately calls `ChunkyProvider.get()Lorg/popcraft/chunky/Chunky;`.
The latter throws `IllegalStateException("Chunky is not loaded.")` until its
`instance` has been registered.

## Narrow patch
Install **in addition to** official DH 3.3.3 and Chunky 1.4.23.
Before DH's `tryRunFirstTimeSetup()` changes its private listener flag,
check `ChunkyProvider.get()`: if it throws ONLY the exact known
"Chunky is not loaded." exception, cancel just this initial bind attempt.
The flag remains unset, allowing the next Minecraft full chunk event to retry.
Once Chunky is ready, let DH bind its original progress listener normally.
Other unexpected exceptions continue to surface; nothing modifies chunks,
biomes, caves, sea or 2096-high dimension settings.

## Install/test
1. **Back up Minecraft saves first.** Download the addon JAR from the successful
   "EXPERIMENT build isolated DH Chunky crash guard JAR" GitHub Actions artifact,
   and place the single `RealScale-DH-Chunky-Guard-NeoForge-1.21.1-0.1.0-experimental.jar`
   in the same `mods` folder as the official DH 3.3.3, Chunky 1.4.23, and RealScale.
   Do **not** replace either official mod JAR.
2. Test a **new disposable world** with shaders disabled, rather than risking
   old saved worlds.
3. Look in `latest.log` for
   `REALSCALE_DH_CHUNKY_GUARD_MOD_LOADED` and
   `REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED`.
   `REALSCALE_DH_CHUNKY_GUARD_DEFERRED` means we prevented exactly the early setup;
   `REALSCALE_DH_CHUNKY_GUARD_READY` means DH was allowed to bind when ready.
   The latter two may not both appear if Chunky is already ready.
4. For A/B testing only: supply
   `-Drealscale.dhchunky.guard.enabled=false` to disable the guard
   without deleting the JAR.
5. If a crash remains, record the *new* exception. Do not mask unrelated
   Mixin, worldgen or graphics errors.
6. The addon metadata deliberately requires **Minecraft 1.21.1**,
   **DH 3.3.3**, **Chunky 1.4.23**, **NeoForge**.
   Other versions require new testing.

Real-server A/B: `.github/workflows/dh-chunky-real-neoforge-test.yml`.
A headless server can prove mod initialization and saved full chunks but CANNOT
prove the fix works on macOS 27 / ARM64 or client GPU render LOD. Current
RealScale test branch uses NeoForge 21.1.249, while the user’s reported
client uses 21.1.262; exact client parity needs confirmation.

## Visibility troubleshooting after startup crash has been prevented
An actual macOS 1.21.1 player reported entering the world with this v0.1.0 guard,
but saw no usable distant LOD (empty unloaded-distance horizon). **Do not claim
this is solved by the crash guard alone.** The guard does not set
`enableDistantGeneration`, does not disable DH's normal renderer or generate
LOD data. This symptom requires a separate check.

The earlier crash-isolation experiment deliberately disabled independent DH
world generation. If that setting remains disabled, a new world with no
pre-existing outer chunks has nothing for DH to render at distance. In
**a fresh test world only**, check DH home screen **Enable Rendering = ON**,
then **Enable Distant Generation = ON**, and
**Distant Generator Mode = SURFACE**. Turn shaders off, stop any concurrently
running Chunky pre-generation job, and allow DH to accumulate LOD. Confirm
that its F3 task queue is progressing. If `SURFACE` is visibly incompatible
with RealScale's custom generator, isolate `INTERNAL_SERVER` at a modest
range separately; this is slower and generates saved Minecraft chunks.

For the exact uploaded Mac instance, Iris was installed alongside DH,
and macOS/OpenGL are untested by Linux server QA. Disable active shader
packs when making this A/B comparison. Check for Distant Horizons renderer
errors in the client `latest.log` and `debug.log`.

### Hardened runtime pass criteria
The headless 3-mod verification now also demands that DH's real
`Created world gen queue` marker appears. If the crash guard logged
`GUARD_DEFERRED`, it **must later log `GUARD_READY`** before the
run passes; this prevents inadvertently treating permanently blocked
Chunky event listeners as a successful fix. This proves DH server
generation queue setup and bridge recovery, **not client LOD rendering**.
Server artifacts in Actions run 39792666204 already show
`GUARD_DEFERRED` followed by `GUARD_READY` and started generation
queues.

Official DH troubleshooting:
https://gitlab.com/distant-horizons-team/distant-horizons/-/wikis/1-user-guide/1-frequently-asked-questions/2-problems-and-solutions/Problems-and-Solutions
