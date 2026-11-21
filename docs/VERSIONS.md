# RealScale Terrain — version and recovery status

**Status snapshot:** 2026-11-21  
**Current source version:** `2.0.0-preview.2-test.10` (from `neoforge/build.gradle` and `META-INF/neoforge.mods.toml`)  
**Release status:** experimental development baseline; not Stable.

## Current source baseline

| Item | Status |
|---|---|
| Current source tree | `neoforge/`, Minecraft 1.21.1 / NeoForge 21.1.x / Java 21 |
| Public world menu | Eight entries: Natural Automate, Natural baseline, plus six fitted experimental profiles |
| Measured USGS profiles | Hidden from ordinary Create World menus by prior product decision; resources retained |
| Terrain Lab | Natural Automate composite profile with morphology/relief controls; three old blended-noise families retained as Legacy Advanced; no live preview yet |
| World Atlas | Removed; no local-save scanner, Anvil reader or Atlas UI remains |
| Geographic Climate Inspector | Read-only fixed 1:20 geographic/solar report |
| Performance Studio | Balanced / Low Memory / Smooth Render / combined profiles, bounded cache controls, live frame/queue/JVM-heap telemetry; physical-client A/B still required |
| Full build status | Natural Automate integration and performance consolidation are under CI validation; unrelated orphan LOTM tests have been removed from this RealScale branch |

The source version number is not proof that a matching distributable JAR has passed all release gates. The current eight-mode source baseline must be validated independently from historical artifacts.

## Historical recovery

| Version | Recovered material | Status |
|---|---|---|
| v0.4 AUDIT | `archive/v0.4/` datapack and historical material | Archived experiment |
| v0.5 GEOREAL | `archive/v0.5/` datapack | Archived experiment |
| v0.6 REALISM MAX | `archive/v0.6/` datapack; historical preset resources retained where needed | Archived experiment / compatibility source |
| v0.7 GeoCore | `research/v0.7/`: original Java numerical core and independent tests | Research prototype, not a complete playable integration |
| v0.8 Geology | `research/v0.8/`: uplift, erosion and deposition numerical kernels | Research prototype, not a complete playable integration |
| v0.9 GeoEngine | `research/v0.9/`: regional watershed/hydrology, cache and tests | Research prototype, not a complete playable integration |
| v1.0 Real Data | `research/v1.0/`: original measured-elevation importer, GIS scripts and tests | Research prototype; not the same as the current runtime DEM pipeline |
| v1.1 Final Battle | `dist/v1.1/` historical installable JAR and matching evidence | Historical test candidate; not the current eight-mode source baseline |

## Current world-preset policy

The current public Create World menus expose:

- Natural Automate Three Realms (experimental composite)
- Natural Three Realms
- Alpine Three Realms
- Canyon Three Realms
- Glacial Three Realms
- Volcanic Three Realms
- Karst Three Realms
- Dune Three Realms

The six fitted modes remain experimental. Hidden gallery, probe, measured, ecology and compatibility profiles must be classified in the unified preset catalog. Do not delete legacy registry IDs or datapack resources merely to reduce visible clutter; old saves may depend on them.

## Research provenance and limits

The recovered v0.7–v1.0 research directories are not proof that every numerical method has been integrated into the playable mod. The current runtime includes a separate USGS 3DEP offline importer and DEM density pipeline; its source coverage, resolution, coordinate mapping and procedural fallback limits must remain explicit.

The historical v1.1 JAR SHA-256 is retained in the repository's historical README/evidence records. It should not be described as the current eight-mode build.

See [RESEARCH_PROVENANCE.md](RESEARCH_PROVENANCE.md), [UNIFIED_FEATURE_CONSOLIDATION.md](UNIFIED_FEATURE_CONSOLIDATION.md), and [neoforge/CHANGELOG.md](../neoforge/CHANGELOG.md) for details.
