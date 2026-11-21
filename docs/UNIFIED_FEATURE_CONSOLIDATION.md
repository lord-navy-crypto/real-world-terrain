# RealScale Unified Feature Consolidation

**Audit date:** 2026-11-21  
**Audit base:** `main` at `de1ff487ad5b297d5bb6e7054fa903dd752ded54`  
**Status:** architecture audit and integration plan; no production world-generation behavior is changed by this document.

## 1. Goal

RealScale has accumulated strong terrain, ecology, dimension, structure, visualization, and performance work, but the user-facing controls and developer artifacts have become fragmented.

The objective is not to delete features or merge every historical branch. It is to:

1. Preserve the newest working implementation of each subsystem.
2. Remove duplicate user-facing entry points and overlapping configuration models.
3. Make optional and experimental behavior explicit.
4. Preserve legacy registry IDs and saved-world compatibility.
5. Give Terrain Lab, World Atlas, Climate Inspector, and performance diagnostics one coherent control and visualization architecture.
6. Promote features only after source-level, worldgen, saved-Anvil, and client validation.

**Do not force-merge old branches or remove worldgen resources merely because they are hidden from the Create World menu.** A hidden registry ID may still be required by an existing save or datapack.

## 2. Repository inventory

The audited `main` tree contains 1,605 repository paths. The current worldgen resource inventory includes:

| Resource family | JSON files |
|---|---:|
| World presets | 40 |
| Noise settings | 32 |
| Density functions | 125 |
| Biomes | 69 |
| Configured features | 106 |
| Placed features | 122 |

These counts describe the source tree, not the number of player-selectable modes or proof that every resource is active.

The default `normal` and `extended` world-preset tags currently expose **seven** entries:

1. Natural Three Realms
2. Alpine Three Realms
3. Canyon Three Realms
4. Glacial Three Realms
5. Volcanic Three Realms
6. Karst Three Realms
7. Dune Three Realms

The USGS Big Sur modes are intentionally absent from the public menu following the earlier decision to remove them. Their resources and the `measured_three_realms` profile remain available for compatibility and controlled testing. Do not re-add them to the public menu without an explicit product decision.

## 3. Feature catalog and disposition

### A. Terrain and geography engine

| Existing implementation | Current role | Consolidation decision |
|---|---|---|
| Realism MAX density/noise graph | Main procedural terrain foundation | Retain as the stable procedural base |
| TerrainArchetypeDensity / TerrainArchetypeMath | Fitted and blended landform calculations | Retain; expose through one profile model |
| RealDemDensity / RealDemSeamlessDensity / RealDemDataSource | Measured DEM terrain and transitions | Retain; keep measured mode separate from procedural fits |
| USGS 3DEP importer | Offline source-data ingestion with checksum/provenance | Retain as an optional measured-data workflow |
| MountainReliefDensity / MountainReliefSiteCache | Regional mountain relief | Retain and share profile/region context |
| RidgeErosionDensity / StrataDensity | Secondary landform and subsurface shaping | Retain as optional profile layers |
| WatershedRoutingMath / RiverChannelFeature | River routing and protected channel generation | Retain; validate long-range connectivity and water safety |
| GeoMath and latitude/temperature density functions | Geographic and climate calculations | Consolidate around the fixed 1:20 WGS84 meridional model |

The three editable Terrain Lab families are currently `landscape`, `alpine`, and `canyon`. They do **not** cover all seven public world modes. The six fitted public modes are currently static world profiles and are not all editable through the same Terrain Lab family model. This is a primary integration gap.

### B. Biome and ecology engine

| Existing implementation | Current role | Consolidation decision |
|---|---|---|
| NaturalEcotoneBiomeSource | Surface biome-boundary transitions | Retain as a shared ecology layer |
| SkyOceanBiomeSource / SkyIslandLayout / SkyIslandFeature | Offshore sky-island eligibility and placement | Retain as a shared world layer |
| CaveDepthBiomeSource / CaveBiomeRegionMath | Cave-depth habitat selection | Retain, but make its use explicit and consistent across profiles |
| GeoWondersBiomeSource | Identity wrapper marking experimental geological-wonder opt-in | Keep explicitly opt-in; do not hide this choice inside selected terrain profiles |
| GrandEcologyFeature | Surface, freshwater, coastal and habitat decoration | Retain as shared ecology features |
| CaveMarineHabitatFeature / CaveWonderNetworkFeature | Cave and marine habitats | Retain as optional ecology layers until full live-world validation |
| EarthSystemsAccentFeature / EarthWonderFeature / GeoWonderRegionFeature | Geological accents and larger wonder prototypes | Keep experimental until multi-seed saved-world evidence supports promotion |

**Important source-chain inconsistency:** Natural currently uses:

`sky_ocean → natural_ecotones → vanilla multi_noise`

The six fitted public profiles currently use:

`sky_ocean → geo_wonders_opt_in → natural_ecotones → cave_depth_habitats → vanilla multi_noise`

This means the six fitted modes carry additional ecology markers and cave-depth selection that Natural does not. The final architecture should represent these as explicit, independently testable layers, not accidental differences hidden in preset JSON. Do not silently change Natural's generation until regression tests pass.

### C. Dimensions, exploration and structures

| Existing implementation | Current role | Consolidation decision |
|---|---|---|
| GrandOverworldCaveNetwork / GrandOverworldCaverns | Large Overworld cave systems | Retain |
| GrandNetherCaveNetwork / GrandNetherCaverns | Expanded Nether caves | Retain |
| GrandEndDensity / GrandDimensionMath / fantasy dimension densities | Expanded End and dimension geometry | Retain with dedicated dimension QA |
| RealScaleStructurePacks | Built-in vanilla structure-density packs | Retain as optional new-world settings |
| Architecture Library (100 structures) | Original/licensed templates and optional natural generation | Retain as an optional structure module |
| HovercraftEntity / HovercraftItem / RealScaleVehicles | Navigable-waterway transport | Keep as an independent optional gameplay module |

Structure density packs must remain mutually exclusive where they override the same vanilla structure-set registry entries. The architecture library remains separately selectable for new worlds.

### D. Visualization and client tools

| Tool | What it actually does today | Current limitation |
|---|---|---|
| World Atlas | Read-only 48×48 saved-region coverage map; bounded 512–4096 block radius; background sampling of persisted Anvil heightmaps | Does not preview ungenerated terrain, render a 3D terrain model, control Chunky, or start a hidden server |
| Terrain Lab | YACL configuration for three editable families; saves a next-world draft; exporter creates a new-world datapack | No live terrain preview; only three editable families; disconnected from the seven-mode catalog |
| Geographic Climate Inspector | Read-only current-position report for 1:20 latitude, climate belt, virtual solar season/daylight, top-of-atmosphere irradiance and altitude lapse estimate | Not local weather; not a live weather simulation; independent of the active NoiseRouter |
| Adaptive Visibility | Classifies OPEN_SURFACE / SEALED_CAVE / TRANSITION and records telemetry | Does not itself suppress vanilla section meshes |
| Client FPS profiler | Records Post-to-Post frame intervals and scene labels | Includes VSync/idle pacing; not a GPU-only measurement |
| Client chunk-upload budget | Optional pacing of already-built section uploads | Does not reduce mesh compilation, GPU draw calls, visible sections or GPU memory |
| Distant Horizons cave LOD controller | Optional DH API integration | Must remain fail-open and be validated on actual client setups |
| install_realscale_visuals.py | Installs pinned Iris/Sodium and external shader/texture packs into a local development run | A development-instance installer, not an in-game visualization subsystem |

There is currently **no unified Visualization Studio**. The existing tools live in different entry points: world-list screen, mod config screen, pause menu, and log output.

### E. Performance and compatibility

Existing performance building blocks include:

- RealDemColumnCache and MeasuredColumnMemo
- QuartBiomeBaseCache and QuartBiomeResultCache
- MountainReliefSiteCache and SkyIslandSiteCache
- WorldgenProfiler and DensityPerfSampler
- ClientFrameIntervalStats and RealScaleClientFrameProfiler
- AdaptiveVisibilityBudget and AdaptiveVisibilityTelemetry
- ClientUploadFrameGovernor and ClientChunkUploadBudget
- Optional Distant Horizons and Chunky compatibility hooks

The newest candidate is [PR #156 — RealScale Performance Core](https://github.com/lord-navy-crypto/real-world-terrain/pull/156). It proposes `balanced`, `low_memory`, `smooth_render`, and `low_memory_smooth_render` profiles. It is still a draft. Its documentation correctly states that low-memory cache resizing can increase recomputation and that smooth-render upload pacing does not establish an FPS improvement.

**Promotion rule:** keep the existing balanced behavior as default. Do not claim FPS improvement or promote client pacing until repeatable physical-client A/B tests show acceptable p95/p99 frame intervals and no unacceptable terrain-appearance delay.

## 4. Preset classification

The 40 world-preset resources are now enumerated in the machine-readable [PRESET_CATALOG.json](PRESET_CATALOG.json). The catalog is an audit snapshot and the validator checks it against both menu tags, the actual world-preset resources, the seven public biome-source chains and the audited resource counts. It should become the single source of truth for later UI/exporter generation rather than treating all 40 resources as equivalent user options.

### Public profiles

- Natural Three Realms
- Alpine Three Realms
- Canyon Three Realms
- Glacial Three Realms
- Volcanic Three Realms
- Karst Three Realms
- Dune Three Realms

The six fitted profiles remain visibly experimental until each has its own representative multi-seed saved-Anvil validation. Current multi-seed evidence must not be generalized from Alpine and Canyon to all six fitted modes.

### Complete hidden / legacy resource inventory

The remaining 33 resources are intentionally not ordinary menu choices:

| Classification | IDs |
|---|---|
| Galleries and diagnostics | `apes_gallery_three_realms`, `biome_lab`, `cave_exploration_three_realms`, `cave_ocean_ecology_three_realms`, `ecotone_gallery`, `skylands_test_deep_ocean` |
| Smoothing / source-chain probes | `tva_alpine_smoothing_probe`, `tva_canyon_smoothing_probe`, `tva_natural_smoothing_probe`, `tva_cave_ocean_ecology_test_three_realms` |
| Measured / geological-wonder experiments | `measured_three_realms`, `tva_measured_three_realms`, `tva_natural_wonders_experimental_three_realms` |
| Historical / specialized ecology profiles | `grand_ecology_three_realms`, `tva_sacred_timeline_three_realms`, `tva_alpine_three_realms`, `tva_canyon_three_realms` |
| PR25 candidate profiles | `tva_pr25_alpine_three_realms`, `tva_pr25_canyon_three_realms`, `tva_pr25_measured_three_realms`, `tva_pr25_natural_three_realms` |
| Legacy height / noise profiles | `continental`, `georeal_ultimate`, `grand_canyon`, `himalayan`, `realism_max`, `realism_max_ecotones`, `realism_max_ecotones_skylands`, `realism_max_three_realms`, `realistic_mountains`, `ultimate`, `usgs_big_sur`, `usgs_big_sur_v2` |

The classification is a maintenance label, not permission to delete the resource. Preserve IDs until saved-world and datapack reference audits prove removal safe.

### Hidden diagnostics and showcases

Examples include:

- `apes_gallery_three_realms`
- `biome_lab`
- `ecotone_gallery`
- `cave_exploration_three_realms`
- `cave_ocean_ecology_three_realms`
- `tva_cave_ocean_ecology_test_three_realms`
- `skylands_test_deep_ocean`
- the Natural / Alpine / Canyon smoothing-probe presets

These belong in a developer/gallery catalog, not the ordinary Create World menu.

### Hidden optional experiments

Examples include:

- `tva_natural_wonders_experimental_three_realms`
- `measured_three_realms`
- `tva_measured_three_realms`
- `tva_sacred_timeline_three_realms`
- `grand_ecology_three_realms`

Keep these available for validation and compatibility, but expose them only through an Advanced / Experimental section when their acceptance level is clearly shown.

### Legacy compatibility resources

Historical names such as `realism_max`, `georeal_ultimate`, `himalayan`, `grand_canyon`, `continental`, `realistic_mountains`, `ultimate`, `usgs_big_sur`, and `usgs_big_sur_v2` must not be deleted solely to reduce the visible preset count. Preserve IDs required by old worlds and saved datapacks.

## 5. Proposed unified player-facing architecture

The target product should have six visible feature centers:

1. **Terrain Engine** — terrain profiles, fitted landforms, DEM source, relief, ridge, erosion, strata, elevation, caves and river grade.
2. **Ecology Engine** — natural ecotones, cave-depth habitats, ocean/sky layers, climate, marine habitats and optional geological wonders.
3. **World Systems** — Overworld/Nether/End, caves, sky islands, structures, architecture library and optional transport.
4. **Visualization Studio** — Terrain Lab, generated-terrain preview, World Atlas, climate/geography overlays and profile comparison.
5. **Performance Core** — cache profile, memory estimates, worldgen profiling, frame telemetry and optional upload pacing.
6. **Compatibility & Advanced** — Distant Horizons, Chunky lifecycle diagnostics, optional client visuals, experimental toggles and migration information.

### One configuration model

Introduce one versioned, immutable `TerrainProfile` / `WorldProfile` model as the source of truth for:

- the Create World profile selector;
- Terrain Lab controls;
- offline datapack export;
- preview generation;
- profile comparison;
- diagnostics and provenance.

The profile catalog should describe each ID, display name, status, terrain source, biome-layer stack, dimension set, defaults, legacy aliases, and validation level. Do not maintain separate hand-written lists for the YACL screen, Python exporter, world-preset tags and docs.

Expand the editable model to cover the seven public profiles while retaining migrations for old three-family YACL drafts. Keep measured DEM read-only and optional.

### One Visualization Studio

The unified Studio should provide these tabs or modes:

- **Terrain:** profile selection, shared controls and a sampled height/slope/relief preview.
- **Ecology:** biome and habitat layers, cave-depth selection, ocean/sky systems and explicit wonder opt-in.
- **Climate:** current 1:20 geographic report, virtual solar estimates and altitude-aware climate diagnostics.
- **Atlas:** existing saved-world coverage and persisted-heightmap view.
- **Compare:** compare profile A/B and preview-versus-saved terrain.
- **Performance:** worldgen stage samples, cache metrics, client frame intervals, upload queue and memory estimates.

World Atlas should remain read-only when inspecting saves. Preview generation must use a separate bounded worker and must never silently generate or modify the user's saved world.

The preview must distinguish sampled procedural predictions from actual saved Anvil data. Do not claim exact preview parity until the same generator path has been measured against real generated chunks.

## 6. Integration sequence

### Phase 0 — establish a buildable baseline

- Isolate unrelated LOTM work from the RealScale build.
- The current main tree contains `neoforge/src/test/java/org/realscale/neoforge/lotm/LotmSanityTest.java`, which references `LotmBalance`, while that production class is absent from main. The separate draft LOTM PR #155 contains the unrelated LOTM module. This orphan test is currently a compile-test blocker and must be isolated or restored as a complete independent module.
- Reconcile the README, version inventory, changelog and current artifact status.
- Clarify the DH–Chunky packaging claim: current main contains `compat/dhchunky` code, while the README describes a separate top-level `dh-chunky-guard/` project that is not present in the audited main tree.

### Phase 1 — catalog and configuration convergence

- Add a machine-readable preset catalog.
- Make the seven public profiles and all hidden/legacy profiles explicit by status.
- Expand Terrain Lab from three editable families to the seven public profiles.
- Preserve old config migrations, world-preset IDs and saved-world datapack behavior.
- Remove duplicate hand-maintained profile lists by generating menu labels, exporter mappings and docs from the catalog where feasible.

### Phase 2 — ecology composition convergence

- Make the biome-source layer stack explicit and consistent.
- Separate stable layers (natural ecotones, sky/ocean and approved cave habitats) from experimental geological-wonder opt-in.
- Preserve each current profile's output until the relevant multi-seed and Anvil regression tests pass.
- Add source-chain tests for every public profile.

### Phase 3 — Visualization Studio

- Connect Terrain Lab, World Atlas and Climate Inspector through one Studio entry point.
- Add bounded terrain preview and layer overlays.
- Reuse profile definitions and deterministic sampling code; keep saved-world reads read-only.
- Add preview-versus-generated Anvil comparison before promoting preview as authoritative.

### Phase 4 — Performance Core

- Repair and re-run PR #156 CI.
- Validate all cache capacities, eviction correctness and memory estimates.
- Keep `balanced` as default.
- Require physical-client A/B for `smooth_render`; no FPS claim based on compile or unit tests.

### Phase 5 — documentation and branch cleanup

- Make this document and the preset catalog the current architecture references.
- Move old phase-by-phase notes into a clearly labelled history/archive area only after checking inbound links.
- Audit open PRs and branches by commit ancestry and current source, not by their open status.
- Close or archive stale PRs only after confirming that their useful changes are already present or deliberately rejected. Never delete legacy worldgen IDs as a documentation-cleanup shortcut.

## 7. Evidence and blockers observed on 2026-11-21

- The default Create World tags expose seven profiles; the changelog says seven, while README text still describes four and `docs/VERSIONS.md` still identifies v1.1 as the latest installable candidate. These documents need one current source of truth.
- PR #156 is a draft. Its profile design is promising, but its current full-build jobs fail because of the unrelated missing `LotmBalance` source referenced by `LotmSanityTest`. Several separate worldgen and opt-in boot jobs on that head did pass; this does not make the full PR merge-ready.
- PR #144 and older fitted-profile branches have heads that are ancestors of main; their changes should not be re-merged just because their PRs remain open. In particular, the legacy `tva_alpine_three_realms` and `tva_canyon_three_realms` QA aliases use smoothed settings and must not be confused with the current public fitted profiles.
- PR #143's zonal/horizontal mapping branch is divergent and changes coordinate/DEM/spawn behavior. It needs an independent technical review and must not be merged into the terrain-consolidation work as a routine cleanup.
- The old `feature/terrain-generation-optimization` branch is divergent and its own documentation labels its DEM cache work experimental; do not replace current main code with it without fresh parity and real-server A/B evidence.
- The USGS 3DEP importer, river-network implementation and several ecology/terrain features already have source-path coverage in main; avoid duplicate cherry-picks from stale branches.
- World Atlas v0.1/v0.2 is a read-only saved-world inspector. Older Chunky/offline-pregeneration experiments are not part of its current release scope.

## 8. Non-negotiable acceptance gates

1. Java 21 / NeoForge build and all unit/source-integrity tests pass on the exact final commit.
2. Every public world profile boots on a dedicated server and has multi-seed saved-Anvil evidence.
3. Ocean and river water columns, cave roofs and networks, sky-island cores, mountain/plateau geometry, structures, and dimension heights remain valid.
4. DEM imports validate source metadata, checksums, coordinate mapping, coverage gaps and seam behavior.
5. Every public profile's biome-source chain and optional-layer flags match the catalog.
6. Terrain Lab exports the exact profile that the user selected; old config drafts still migrate safely.
7. World Atlas reads existing saves without writes or hidden server startup.
8. Visualization preview is explicitly labelled as sampled prediction until measured parity is demonstrated.
9. Performance claims require repeatable A/B tests with comparable seeds, hardware, mod lists and new-chunk workloads; report p50/p95/p99 and GC/frame data where applicable.
10. Existing saved-world registry IDs and datapack selections remain compatible.

**Final principle:** one coherent product architecture, multiple well-defined internal systems, a small public world menu, explicit experimental controls, and one shared visualization/diagnostic workspace. Consolidation means removing duplication—not erasing working systems or pretending unverified prototypes are finished.
