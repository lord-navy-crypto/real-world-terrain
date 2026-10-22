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
