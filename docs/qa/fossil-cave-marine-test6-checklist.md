# RealScale test.6 experimental cave & marine field QA

This is a **test-only** follow-up to `2.0.0-tva-preview.2-test.5` and draft PR #87. It must not be merged into stable `main` or recommended for important worlds until release gates pass.

## What changed (not yet a release claim)
- Rebalance existing 11-type cave region selection to make fossil limestone more likely **where the custom cave test biome source is active**. The overall rarity gate (1/4 for original cave biomes, 1/9 for eligible ordinary underground climate) is unchanged; Deep Dark, oceans, rivers, overworld surfaces and canonical preset biome sources are still protected.
- Increase low-altitude **fossil matrix feature attempts** from 1/3 to 1/2 eligible placement invocations. Each invocation must still find natural stone next to existing carved cave air, and never replaces ore or liquid.
- Report both palette incidences and distinct full-chunk incidences in ACTUAL saved Minecraft Anvil. A block palette observation is **not** proof of a beautiful visual fossil structure or of a 3D formation volume.

## Automatic QA
1. Pure-Java `CaveBiomeRegionMathSmoke`: deterministic, protected vanilla biomes and separate fossil-weight coverage across dripstone, badlands and plains.
2. Real Java 21 + NeoForge 1.21.1 server matrix: seeds 2, 42, 314159, 271828; complete world chunks read from Anvil; first two still require at least one of the new glow/fossil IDs; all tests report what was *actually observed*.
3. `audit_real_cave_biome_anvil.py --require-fossil` is an **optional strict gate**, to use only after finding a seed+exploration route with proven fossil ID coverage; a normal matrix run does not pretend this is achieved.
4. Marine seed QA remains in PR #87: cold/deep, warm, frozen and kelp placements are already supported by server evidence. Do not infer GPU appearance or FPS from a headless server.

## Human GPU-client checklist (fresh throwaway world)
- Select only `RealScale Cave + Ocean Ecology TEST` world preset with the updated build; record seed, Minecraft/NeoForge version and test mod JAR hash.
- Use F3 to record actual biome IDs in below-ground **air-filled pockets**. Photograph fossil limestone biome, natural limestone/calcite/bone patches, dripstone, and glowing grottos; capture XYZ with the screenshot. A biome label alone is insufficient for an actual feature-placement visual claim.
- Confirm normal diamonds/iron/copper, original Deep Dark/ancient cities, cave openings and groundwater remain. Check both below y=24 and higher underground slopes.
- Visit deep cold/frozen and warm oceans: capture underwater reef closeups, fan waterlogging, kelp heights, no dry cavities, no large ice-free anomalies or misplaced tropical living coral in cold water.
- Compare a fresh baseline test.5 world against the candidate with the **same seed, waypoints, simulation/view settings**. Never upgrade an irreplaceable world to test.6. Record screenshots, measured client frame timings, and any exceptions.
- Before promotion: investigate persisted Beardifier Y256 parity differences independently. A successful server boot or JAR build does not clear that gate.

## Pending release gates
- **Real fossil cave biome ID in actual saved sections:** pending four-seed field evidence.
- **Visually recognizable fossil cave and cold-water reef:** pending physical client screenshots.
- **Performance / biome+ore+fluid parity / old-world safety:** pending controlled comparison; stable `main` remains unchanged.
