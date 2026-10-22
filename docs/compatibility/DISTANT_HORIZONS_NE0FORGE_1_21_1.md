# RealScale x Distant Horizons — compatibility experiment

**Not a fix claim.** Branch `experiment/distant-horizons-neoforge-1211-20261022` comes from integrated `test.5`. Stable `main` is untouched. Intended mod is **Distant Horizons**, not Decentraland.

## Evidence and suspected conflict areas
- Official Distant Horizons **3.3.3** supports Minecraft 1.21.1 NeoForge; pin its Modrinth version ID `9w34y8ai`, verify release metadata and SHA512 before launching.
- The RealScale Overworld is **2096 blocks high**, min_y=-64, logical height 2096. This is not ordinary vanilla 384-height terrain. A LOD renderer might have assumptions about vertical ranges; **unverified**, test high mountains and far sea instead of truncating the world.
- RealScale custom noise settings, layered biome-source codecs, long-range USGS interpolation and sky islands are genuine world generation extensions. DH independently creates/reads distant chunks and can use multithreading. Threading, seed/dimension identity and LOD-versus-physical chunk comparison are test targets, **not proven root causes**.
- Experimental RealScale client section-upload Mixin (`VanillaSectionUploadBudgetMixin`) is DEFAULT ON in `test.5`, but absent on dedicated servers. Run physical-client comparisons with `-Drealscale.experimental.client.uploadbudget=false` and true. Never silently conflate server boot with GPU compatibility.
- Beardifier Y256 changes can alter saved blocks; unrelated suspect turned OFF for initial isolated A/B. Do not promote any release without independent parity investigation.

## CI proof design
Four disposable **real NeoForge 1.21.1** servers using identical seeds and presets, first RealScale alone, second RealScale + exact Distant Horizons 3.3.3:
- Measured seed 2 and Sacred Timeline seed 42.
- Force-load a distant chunk; wait for its full generation and save it in real Anvil; parse logs and report mixin/worldgen/resource exceptions.
- DH-enabled run MUST actually load DH, not merely contain its jar on disk. Failure remains a *failed experiment*, never an artificial success.
- CI is **headless**: GPU rendering, DH LOD DB writes, FPS, cliff color, texture correctness and client-only crashes remain unverified even if all server jobs pass.
- Keep `main` and the canonical world height/configurations unchanged.

## Diagnostic matrix for physical client (fresh disposable worlds)
1. **Control**: DH 3.3.3 alone + vanilla preset / no shaders.
2. **RealScale only**: fresh Measured and Sacred; record vertical relief, seed, F3 biome at far landmarks.
3. **RealScale + DH**, no shaders: start with DH's distant generation OFF or SURFACE-only per official troubleshooting; fly between three known landmarks and check visible LOD transitions, ocean continuity, caves and sky islands.
4. With unchanged seed, set DH distant generation back to normal and compare errors / LOD DB / memory / loading times. Test `CLIENT uploadbudget false` then true in separate game restarts. Clear *only DH test cache* between runs, not the saved Minecraft world.
5. Only after no-shader pass, add a DH-supported shader and compatible graphics stack. Capture `logs/latest.log`, `crash-reports`, DH config (redact usernames, addresses and credentials) and screenshots.
6. Server-client compatibility: DH only on client; then DH on both server and client. Verify height clipping, LOD holes, dim changes (Overworld/Nether/End) and resource use.

## Candidate fixes, gated by evidence
- Server stalls only when DH is present: compare DH background generator configurations first and document any multi-threaded chunk-generation assumptions. Do not rewrite noise or aquifer for a hypothetical performance problem.
- Crash around client section upload only when client budget mixin is ON: disable that experimental mixin for DH by default **on a follow-up test branch only**, verify with real user client, and preserve an explicit override.
- LOD cliffs cut at Y boundaries: validate full-height biome/heightmaps and DH ability to represent > vanilla height. Never downgrade to 384-height canonical presets without approval.
- LOD missing or uneven but saved Anvil matches: investigate DH LOD cache/progression/render thresholds rather than blaming Minecraft worldgen.

## Sources
- Official Distant Horizons 3.3.3 NeoForge 1.21.1 Modrinth version: https://modrinth.com/mod/distanthorizons/version/3.3.3-1.21.1
- Distant Horizons maintainers' troubleshooting wiki: https://gitlab.com/distant-horizons-team/distant-horizons/-/wikis/1-user-guide/1-frequently-asked-questions/2-problems-and-solutions/Problems-and-Solutions
- This repository's RealScale actual server QA: `qa/neoforge/rcon_worldgen_smoke.py`.
