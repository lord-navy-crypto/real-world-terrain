# RealScale Terrain

An experimental real-scale terrain and ecology mod for Minecraft 1.21.1 / NeoForge 21.1.x / Java 21. The current source version is **2.0.0-preview.2-test.12**. This is an experimental development build, not a Stable release.

The repository is undergoing an architecture consolidation. The goal is to organize terrain, ecology, dimensions, structures, visualization and performance into one coherent product rather than continuing to add overlapping presets and configuration entry points.

## Current Create World menu (2026-11-21)

The `normal` and `extended` world-preset tags expose **one RealScale choice**:

1. **Natural Automate Three Realms** — the unified experimental engine, combining the natural-smoothed baseline, six fitted morphology kernels, climate/regional suitability, smooth cross-fades, shared ecology layers and the expanded Cave Exploration V2 underground network.

The natural baseline and six standalone fitted presets remain packaged as hidden compatibility and QA references. Their algorithms are not separate player-selectable world modes. The fitted kernels remain experimental geographic approximations; the unified selector does not imply that every generated region is an exact real-world match.

**USGS Big Sur / Big Sur v2 were removed from the ordinary new-world menu following the earlier product decision.** Measured-terrain resources and internal profiles such as `measured_three_realms` remain for compatibility, data import and controlled experiments; they are not current public menu choices. USGS 3DEP import is an optional offline workflow and does not make the entire world measured terrain.

## Core systems

- **Terrain Engine:** Realism MAX, Terrain Archetypes, DEM, mountain relief, erosion, strata and river terrain.
- **Ecology Engine:** Natural Ecotones, Sky Ocean, Sky Islands, Cave Depth Habitats, marine/cave ecology and optional Geological Wonders.
- **World Systems:** expanded Overworld caves, Grand Nether / End, sky islands, structure-density packs, the 100-structure architecture library and optional water transport.
- **RealScale Studio:** a unified home for Terrain Lab, Climate Inspector and Performance Studio. World Atlas has been retired and removed.
- **Performance:** world-generation sampling, cache diagnostics, client frame intervals and optional chunk-upload pacing.
- **Compatibility:** optional Distant Horizons / Chunky hooks and external client-visual tools.

See the [Preset Catalog](docs/PRESET_CATALOG.json) for all 41 public, hidden and historical world profiles, and [Unified Feature Consolidation](docs/UNIFIED_FEATURE_CONSOLIDATION.md) for the detailed inventory, status classification, conflict review and integration plan.

## Current visualization status

These tools exist, but they are not yet one unified workbench:

- **Terrain Lab:** Natural Automate is the only player-facing world-generation profile, with adaptive six-kernel morphology, terrain-relief and 1:20 climate controls. Legacy Landscape / Alpine / Canyon blend schemas remain internal for old-draft migration and exporter compatibility; they are not selectable in the player UI. There is no live terrain preview yet.
- **World Atlas:** retired and removed; the mod no longer scans local saves.
- **Geographic Climate Inspector:** read-only 1:20 geographic latitude, virtual solar season, daylight and altitude-temperature estimates. It is not a live weather simulator.
- **Performance Studio:** provides four performance profiles, bounded cache controls, FPS/frame-interval telemetry, chunk-upload queue state and JVM-heap statistics.

These tools now share a unified **RealScale Studio** entry point, but they are not yet a complete real-time 3D workbench. Do not describe live terrain preview or full terrain heatmaps as existing functionality yet.

## Optional architecture library

The main mod contains an optional 100-structure architecture library. It can be enabled in the Data Packs screen when creating a new world; it is not forced into every world. Structure-density packs have mutual-exclusion constraints, so select only one applicable Overworld density pack.

## Distant Horizons / Chunky compatibility

The current main branch embeds optional DH–Chunky compatibility hooks in the main mod, targeting the tested Distant Horizons 3.3.3 + Chunky 1.4.23 combination. A historical standalone `dhchunkyguard` addon must not be installed alongside the embedded version; there is no standalone top-level `dh-chunky-guard/` subproject in the audited main tree.

World Atlas save scanning and map-reading code have been deleted from this branch; no replacement save scanner is retained.

## Repository layout

- `neoforge/` — NeoForge mod source, registries, worldgen resources and client screens.
- `scripts/` — Terrain Lab exporter, USGS 3DEP importer, structure-template generators and developer tools.
- `qa/` — source-integrity, data, Anvil, worldgen and performance regression checks.
- `docs/` — current architecture, validation scope, provenance and historical design notes.
- `assets/architecture/` — structure-template source assets and license records.
- `archive/` — historical datapacks and old-version materials.
- `research/` — numerical geology, hydrology and data-import prototypes not yet fully integrated.
- `dist/v1.1/` — historical v1.1 test-candidate JAR, not the current eight-mode source build.

## Build and validation

Requirements: Java 21, Minecraft 1.21.1, NeoForge 21.1.x and Python 3.

```sh
python3 qa/verify_repository.py
cd neoforge
gradle --no-daemon build
```

**Current validation status:** the final integration candidate checks the single Natural Automate entry, six-kernel composite, default Cave Exploration V2 network, legacy-draft migration, saved-world generation and performance guards. It remains an experimental candidate, not a Stable release; physical-client FPS, shader and broad third-party-mod compatibility still require separate A/B testing.

Do not replace generator settings in an existing world. Back up the instance before new-world testing, and never install multiple RealScale main-mod JARs together.

## Version and licensing

The current source version is defined in `neoforge/build.gradle` and `META-INF/neoforge.mods.toml`; see the [CHANGELOG](neoforge/CHANGELOG.md). Historical recovery status is documented in [VERSIONS](docs/VERSIONS.md).

The recovered source did not include an explicit open-source grant, so this migration does not invent an MIT license. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).
