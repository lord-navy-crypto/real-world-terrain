# TVA Sacred Timeline — promotion audit (2026-10-17)

Scope: PR #30 integration branch vs `realscale:tva_sacred_timeline_three_realms`. **Evidence is per commit SHA.** Compilation success does not prove actual Anvil output. Older green test runs cannot certify new changes.

## Current sacred world definition (already installed)
| Setting | Evidence / configuration | Decision |
|---|---|---|
| Overworld terrain | `realscale:tva_natural_smoothed`; 2096-block vertical span starting Y=-64; aquifers; grand-overworld cavern final density | Retain sacred |
| Concentric latitude | `radial_latitude_temperature` with mountain/basin temperature and `climate_budget` in density router | Retain wiring; real biomes require multi-seed audit |
| Ecotone climate | `realscale:natural_ecotones` underneath sky-ocean | Retain sacred |
| Ocean | Native marine biome source via underlying multi-noise, and existing marine integrity CI | Retain; fresh seed Anvil proof required |
| Sky archipelago | `realscale:sky_ocean` wrapper | Retain existing requested feature; **fail: physical stone core Anvil proof** |
| Grand Nether / End | `realscale:grand_nether` / `realscale:grand_end` noise settings | Retain, repeat dedicated proof |
| Rivers and wetland patches | Existing worldgen/biome modifiers and river protections | Preserve; do not close open river channels |
| USGS DEM, four-family terrain lab | Independent world presets and QA | Keep optional, not proof of default sacred |
| Overworld special nature accents | Existing `EarthSystemsAccentFeature` single-column original variants | Retain previous behavior; new expanded clusters now opt-in |

## CI outcomes reviewed
Integration head `c2847de1d141a14a8a3a95707f62fd94605b0345`:
- **PASS** Source integrity, Python/unit geometry tests, NeoForge build.
- **PASS** Gradle 9.8 client and mod compatibility.
- **FAIL** Forced sky islands persisted Anvil proof: at (16,47,Y175) got air instead of expected stone/end stone; only 4 saved chunks with marked island blocks.
- **NOT YET CERTIFIED AT SAME SHA** Sacred Timeline server boot, 2 marine seed audits, Grand Nether/End save proof, ecotones, Grand Ecology, APES gallery, smoothing A/B, distant four-family multi-seed QA. Jobs were in progress or queued when observed.

Latest post-audit feature gate (new SHA on PR #30): re-run all above. Do not report old green checks as if new SHA was certified.

## Experimental features explicitly isolated — not promoted
- Eight new EarthWonder microstructures, ten new GeoWonderRegion patterns and two experimental rock-carving CaveWonderNetwork features: gated by `GeoWondersBiomeSource` marker, opt-in preset `realscale:tva_natural_wonders_experimental_three_realms`.
- Existing salt playa/alluvial fan/cold desert **single block** behavior retained in sacred; their expanded deposit clusters require experimental gate.
- All new six-direction cross-realm biome and terrain patches only in independent invasion presets, not canonical sacred.
- Three source-invasion ruins now also require an opt-in invasion biome source instead of triggering in every RealScale preset.
- None of these are eligible for sacred promotion until live saved palettes, deterministic multi-seed tests, negative-coordinate chunk borders, ocean/river safety, and runtime performance have been proven.

## Promotion gates and instructions
1. Fix sky-island missing geological core; actual Anvil center and satellite fields all match predicted physical blocks.
2. Re-run sacred preset boot and Anvil biome palettes with two seeds; preserve deep-ocean seafloor and structures.
3. Verify connected large Nether/End dimensions on saved blocks and probe remote chunks.
4. Resolve four-family far chunk RCON freezes (1024,1024), inspect saved JVM thread dumps and fix hot path.
5. Validate biome and physical block changes in experimental geology + six-way invasion presets, not only JSON names. Record exact seed, XYZ and observed palette.
6. Only then selectively move accepted natural wonder IDs into sacred without lifting global experimental guard for untested IDs.
7. Do not merge PR #30 to `main` while critical validation fails. The PR contains hundreds of unrelated commits; review per-system regression.

## References
- PR: https://github.com/lord-navy-crypto/real-world-terrain/pull/30
- Manufacturing issue: https://github.com/lord-navy-crypto/real-world-terrain/issues/31
- World presets: `neoforge/src/main/resources/data/realscale/worldgen/world_preset/`
- Test: `qa/test_sacred_timeline_promotions.py`
