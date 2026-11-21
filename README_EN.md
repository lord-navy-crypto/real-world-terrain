# RealScale Terrain

An experimental real-scale terrain and ecology mod for Minecraft 1.21.1 / NeoForge 21.1.x / Java 21. The current source version is **2.0.0-preview.2-test.9**. This is an experimental development build, not a Stable release.

The repository is undergoing an architecture consolidation. The goal is to organize terrain, ecology, dimensions, structures, visualization and performance into one coherent product rather than continuing to add overlapping presets and configuration entry points.

## Current Create World menu (2026-11-21)

The `normal` and `extended` world-preset tags currently expose seven choices:

1. **Natural Three Realms** — natural terrain baseline.
2. **Alpine Three Realms** — fitted alpine terrain (experimental).
3. **Canyon Three Realms** — fitted canyon terrain (experimental).
4. **Glacial Three Realms** — fitted glacial terrain (experimental).
5. **Volcanic Three Realms** — fitted volcanic terrain (experimental).
6. **Karst Three Realms** — fitted karst terrain (experimental).
7. **Dune Three Realms** — fitted dune terrain (experimental).

The six fitted modes remain experimental. Validation of a subset must not be presented as proof that all six are fully validated geographic fits.

**USGS Big Sur / Big Sur v2 were removed from the ordinary new-world menu following the earlier product decision.** Measured-terrain resources and internal profiles such as `measured_three_realms` remain for compatibility, data import and controlled experiments; they are not current public menu choices. USGS 3DEP import is an optional offline workflow and does not make the entire world measured terrain.

## Core systems

- **Terrain Engine:** Realism MAX, Terrain Archetypes, DEM, mountain relief, erosion, strata and river terrain.
- **Ecology Engine:** Natural Ecotones, Sky Ocean, Sky Islands, Cave Depth Habitats, marine/cave ecology and optional Geological Wonders.
- **World Systems:** expanded Overworld caves, Grand Nether / End, sky islands, structure-density packs, the 100-structure architecture library and optional water transport.
- **Visualization:** World Atlas, Terrain Lab and Geographic Climate Inspector.
- **Performance:** world-generation sampling, cache diagnostics, client frame intervals and optional chunk-upload pacing.
- **Compatibility:** optional Distant Horizons / Chunky hooks and external client-visual tools.

See [Unified Feature Consolidation](docs/UNIFIED_FEATURE_CONSOLIDATION.md) for the detailed inventory, status classification, conflict review and integration plan.

## Current visualization status

These tools exist, but they are not yet one unified workbench:

- **Terrain Lab:** YACL configuration and new-world datapack export. It currently edits only Landscape, Alpine and Canyon families, does not cover all seven public profiles, and has no live terrain preview.
- **World Atlas:** read-only saved-Anvil region coverage and heightmap inspection. It does not generate new chunks, start a hidden server or control Chunky.
- **Geographic Climate Inspector:** read-only 1:20 geographic latitude, virtual solar season, daylight and altitude-temperature estimates. It is not a live weather simulator.
- **Performance diagnostics:** currently provided mainly by opt-in samplers and logs, not a performance heatmap workbench.

These capabilities will be progressively integrated into a unified **RealScale Studio**. Do not describe live terrain preview, performance heatmaps or a complete 3D editor as existing functionality yet.

## Optional architecture library

The main mod contains an optional 100-structure architecture library. It can be enabled in the Data Packs screen when creating a new world; it is not forced into every world. Structure-density packs have mutual-exclusion constraints, so select only one applicable Overworld density pack.

## Distant Horizons / Chunky compatibility

The current main branch embeds optional DH–Chunky compatibility hooks in the main mod, targeting the tested Distant Horizons 3.3.3 + Chunky 1.4.23 combination. A historical standalone `dhchunkyguard` addon must not be installed alongside the embedded version; there is no standalone top-level `dh-chunky-guard/` subproject in the audited main tree.

World Atlas remains independent of Chunky: it does not copy commands, start a background server or take control of Chunky tasks.

## Repository layout

- `neoforge/` — NeoForge mod source, registries, worldgen resources and client screens.
- `scripts/` — Terrain Lab exporter, USGS 3DEP importer, structure-template generators and developer tools.
- `qa/` — source-integrity, data, Anvil, worldgen and performance regression checks.
- `docs/` — current architecture, validation scope, provenance and historical design notes.
- `assets/architecture/` — structure-template source assets and license records.
- `archive/` — historical datapacks and old-version materials.
- `research/` — numerical geology, hydrology and data-import prototypes not yet fully integrated.
- `dist/v1.1/` — historical v1.1 test-candidate JAR, not the current seven-mode source build.

## Build and validation

Requirements: Java 21, Minecraft 1.21.1, NeoForge 21.1.x and Python 3.

```sh
python3 qa/verify_repository.py
cd neoforge
gradle --no-daemon build
```

**Known current build blocker:** the main test tree contains an unrelated LOTM test that references `LotmBalance`, which is absent from main. LOTM is separate development work and must not be mixed into the RealScale core. Do not label the current source as passing all build gates until this test boundary is isolated and the full CI suite is rerun.

Do not replace generator settings in an existing world. Back up the instance before new-world testing, and never install multiple RealScale main-mod JARs together.

## Version and licensing

The current source version is defined in `neoforge/build.gradle` and `META-INF/neoforge.mods.toml`; see the [CHANGELOG](neoforge/CHANGELOG.md). Historical recovery status is documented in [VERSIONS](docs/VERSIONS.md).

The recovered source did not include an explicit open-source grant, so this migration does not invent an MIT license. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).
