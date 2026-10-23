# RealScale × Chunky × Distant Horizons 3.3.3: bridge v0.2 (experimental)

## Purpose and limits
A real Mac/NeoForge 1.21.1 client confirmed the **previous v0.1** Chunky startup guard stops the original crash AND can display RealScale's far mountain LOD. Preserve that known working JAR during v0.2 testing.

v0.2 does not rewrite DH LOD, terrain, high-Y textures, shaders, Sodium renderer, Chunky pregeneration, or saved world data. It targets the narrowly confirmed `ChunkyProvider.get()` lifecycle race from upstream DH issue #1329, plus the RealScale test build's opt-in client safety.

## Exact runtime behavior
- The previous v0.1 skipped DH's initial `AbstractChunkyAccessor.tryRunFirstTimeSetup` call until `ChunkyProvider.get()` stopped throwing the literal "Chunky is not loaded." (all other exceptions still surface).
- v0.2 caches ChunkyProvider's Java reflective method after first successful lookup. Once DH's original listener-binding code has returned, it writes `REALSCALE_DH_CHUNKY_GUARD_BOUND`, so startup validation cannot confuse `READY` (pre-bind) with a successful bind.
- No extra DH generator-plan override. **Official DH 3.3.3 already pauses DH own background generation while Chunky pregeneration runs**, then resumes after Chunky becomes inactive. Avoiding a second override prevents an unwanted permanently disabled LOD queue.
- The RealScale `realscale.experimental.client.uploadbudget` TEST flag now defaults **false** for freshly built RealScale test JARs. This only affects experimental interception of vanilla chunk uploads, not DH's shader renderer. Manual opt-in to compare FPS: `-Drealscale.experimental.client.uploadbudget=true` (not recommended with DH/Iris/Sodium before visual proof).
- A separate test asserts the canonical overworld vertical dimensions **minY=-64, height=2096, logical_height=2096**.

## Four real-server experiments
The workflow `.github/workflows/dh-chunky-real-neoforge-test.yml` checks original DH 3.3.3 + Chunky 1.4.23 + RealScale:
- Measured world, seed 2, with/without bridge
- Sacred Timeline, seed 42, with/without bridge

The server must actually start, fully load a distant chunk and log DH worldgen queue creation. For **patched** arms it must also log `GUARD_HOOK_ENTERED` and `GUARD_BOUND`; deferred attempts must subsequently emit `GUARD_READY`. For unpatched arms the exact original startup crash is an expected reproduction, **not** counted as a successful full chunk.

**Important:** Headless CI does NOT prove GPU-level distant LOD rendering, shaders, or performance on macOS. Only real client comparisons can do so.

## Controlled GPU / shader comparison
1. Keep the **v0.1 known-good JAR** as fallback. Back up saves; copy profile and create a fresh disposable world.
2. Confirm RealScale + Chunky + DH + guard with Iris shaderpack *disabled*: far mountains and nearby Minecraft chunks should connect smoothly. Keep DH Enable Rendering ON. If distant generation was disabled for crash isolation, restore appropriate generation carefully.
3. Add Sodium with the RealScale client upload interceptor still OFF. Check chunk-border holes, camera pans, teleports and full world reload.
4. Add Iris (still no shaderpack), repeat the check.
5. Enable a shaderpack **explicitly advertising DH support**; compare distant snow, mountain fog, shadows, clouds, sky, sea, and waterline transitions. Iris installation alone does not guarantee shaderpack integration.
6. Compare Chunky idle vs actively pre-generating chunks. Check that DH background generation pauses only when Chunky generates and later resumes naturally; do not force simultaneous huge-scale generation.
7. For visual artefacts, separately try DH `Surface Only` versus `Chunk Only`; Surface is an approximation and may not exactly reproduce RealScale custom worldgen. Keep actual Minecraft chunk geometry canonical.
8. Use `python3 qa/neoforge/audit_lod_stack.py path/to/latest.log [audit.json]` to generate redaction-safe flags. It does not output raw paths, user identity or full logs. `gpu_lod_visible` and `shader_pack_support` explicitly remain `unverified` by log inspection alone.

## Delivery discipline
- The original working v0.1 patch and stable RealScale main remain untouched.
- v0.2's standalone addon must only be installed in addition to the **exact** DH 3.3.3 and Chunky 1.4.23 on NeoForge 1.21.1.
- No modifications to third-party binaries or redistribution of their JARs.
- If a physical-client test regresses, use v0.1 again and attach *sanitized* new crash stack/error markers.

Official DH 3.3.3 release notes: https://modrinth.com/mod/distanthorizons/version/3.3.3-1.21.1
Official DH shader compatibility FAQ: https://modrinth.com/mod/distanthorizons
