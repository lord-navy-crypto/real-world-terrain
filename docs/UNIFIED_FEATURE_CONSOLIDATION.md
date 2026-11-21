# RealScale Unified Feature Consolidation

**Audit date:** 2026-11-21  
**Audit base:** `main` at `de1ff487ad5b297d5bb6e7054fa903dd752ded54`  
**Status:** final integration candidate. Natural Automate is the only public generator; the six fitted kernels and Cave Exploration V2 are integrated in its default pipeline. Remaining release limits are explicit in §8.

## 1. Goal

RealScale has accumulated strong terrain, ecology, dimension, structure, visualization, and performance work, but the user-facing controls and developer artifacts have become fragmented.

The objective is not to delete features or merge every historical branch. It is to:

1. Preserve the newest working implementation of each subsystem.
2. Remove duplicate user-facing entry points and overlapping configuration models.
3. Make optional and experimental behavior explicit.
4. Preserve legacy registry IDs and saved-world compatibility.
5. Give Terrain Lab, Climate Inspector, and performance diagnostics one coherent control and visualization architecture; keep local-save scanning out of the product.
6. Promote features only after source-level, worldgen, saved-Anvil, and client validation.

### Final integration candidate — 2026-11-21

- The Create World tags expose one public generator: Natural Automate Three Realms.
- The public Natural Automate preset now references the versioned `natural_automate_v2` noise-settings key, which enables `gallery_v2: true` in its `grand_overworld_caverns` final-density codec. The previous `natural_automate` key remains unchanged for existing saves. The deterministic V2 loop network and tiered annexes are part of the same Overworld density field, not a second dimension or separate public preset.
- Legacy Landscape / Alpine / Canyon blend settings remain readable by the exporter and saved-draft migration, but their selector categories are no longer exposed in the player-facing Terrain Lab screen.
- Cave Marine Habitat, Cave Wonder Network and Geological Wonders retain their explicit opt-in boundaries; this integration does not silently promote unrelated experimental ecology features.
- APES Gallery's saved-world workflow now uses RCON independently of the Gradle parent-process lifetime and continues to require all 16 biome palettes plus real FULL satellite chunks.


**Do not force-merge old branches or remove worldgen resources merely because they are hidden from the Create World menu.** A hidden registry ID may still be required by an existing save or datapack.

## 2. Repository inventory

The audited pre-consolidation `main` baseline contained 1,605 repository paths. The Natural Automate branch adds the composite preset and Studio modules while removing the retired Atlas implementation. The audited baseline worldgen resource inventory included the following counts; the Natural Automate branch adds one preset, one noise setting and four density functions:

| Resource family | JSON files |
|---|---:|
| World presets | 41 |
| Noise settings | 33 |
| Density functions | 129 |
| Biomes | 69 |
| Configured features | 106 |
| Placed features | 122 |

These counts describe the source tree, not the number of player-selectable modes or proof that every resource is active.

The Create World `normal` and `extended` world-preset tags expose **exactly one** RealScale entry: Natural Automate Three Realms. It is the sole public terrain-generation mode. Its density graph composes the natural height baseline with all six fitted morphology kernels (Alpine, Glacial, Canyon, Volcanic, Karst and Dune), climate/topography/regional suitability gates, normalized cross-fades and exact Natural fallback.

The standalone Natural baseline and six fitted world-preset JSON resources remain registered and packaged as hidden compatibility/diagnostic references; they are not ordinary menu choices. Hiding these references does not disable or delete their kernels. USGS Big Sur modes remain hidden as previously decided.

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

Terrain Lab's only player-facing `natural_automate` profile composes the six fitted `TerrainArchetypeMath` kernels (alpine, glacial, canyon, volcanic, karst, dune) over the smoothed Natural height signal. A registered low-frequency `minecraft:offset` field supplies broad deterministic regional/geological affinity; raw shifted `minecraft:temperature` and `minecraft:vegetation` fields, together with the local terrain-height signal, gate each fitted family. Suitable families cross-fade through normalized weights. Where no family is suitable, the Natural baseline remains unchanged. The old Landscape / Alpine / Canyon blend schemas remain available to the exporter and legacy-draft migration but are not player-selectable. The content-addressed Terrain Lab exporter now derives new profiles from `natural_automate_v2`, copies the same climate-aware density graph and expanded cave flag, and keeps old generated profile IDs immutable. The fit uses climate proxies from the same raw noise fields as the Overworld climate sampler; it does not query a biome ID directly. Per-kernel editing and a live preview remain later enhancements.

#### Six fitted morphology kernels

The fitted engine is not one generic height filter. `TerrainArchetypeMath` dispatches to six distinct deterministic kernels:

| Archetype | Morphology kernel |
|---|---|
| Alpine | Ridge and high-relief mountain morphology |
| Canyon | Incised valleys and canyon-ridge morphology |
| Glacial | Glacial valley geometry |
| Volcanic | Shield-volcano / caldera geometry |
| Karst | Karst towers and sink depressions |
| Dune | Directional dune ridges |

The shared codec exposes fitted controls including `macro_gain`, `meso_gain`, `micro_gain`, `relief_gain`, `ridge_sharpness`, `cliff_index`, `valley_index`, `ridge_index`, `anisotropy_gain`, `dune_axis_radians`, and `dominant_wavelength_m`. The unified editor should keep family-specific controls and validated defaults; it should not flatten these into one generic slider set or discard the measured fit coefficients.

#### Single registered dimension, two internal world domains

RealScale keeps one `minecraft:overworld` DimensionType, one coordinate system, one chunk store and one saved world. World generation is split internally into two density branches: the Surface Domain owns the natural/fitted height field; the Underground Domain owns cave networks and small-cave subtraction. `RealScaleDomainComposition` merges them into the one final density field before block placement. The resulting blocks, fluids, lighting, entities and saved chunks remain unified.

On the client, `RealScaleDomainManager` maps the conservative exposure classifier into SURFACE, UNDERGROUND or TRANSITION policies. The existing optional Distant Horizons cave-LOD adapter and FIFO upload pacing consume those policies. This is domain-aware scheduling, not two independent Sodium/vanilla section-render queues: vanilla section order and visibility remain unchanged, uncertain boundaries fail open, and player-visible work is never demoted.

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

**Unified composite source chain:** Natural Automate and its six hidden standalone fitted reference presets use the same explicit layer order:

`sky_ocean → geo_wonders_opt_in → natural_ecotones → cave_depth_habitats → vanilla multi_noise`

The conservative Natural Three Realms baseline intentionally remains:

`sky_ocean → natural_ecotones → vanilla multi_noise`

Selecting Natural Automate explicitly opts into the geological-wonder marker as part of the six-profile composite. The wrapper preserves the underlying vanilla biome selection; it gates the experimental wonder features. The baseline preset remains unchanged.

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
| World Atlas | Removed from the active product at the user's direction | All Atlas screens, save-directory scanning, map readers, localization keys and Atlas-specific tests are removed; no replacement save scanner is planned |
| Terrain Lab | Natural Automate composite editor; legacy blend schemas remain internal for saved-draft migration/export compatibility | No live terrain preview; individual fitted-kernel controls remain a later enhancement |
| Geographic Climate Inspector | Read-only current-position report for 1:20 latitude, climate belt, virtual solar season/daylight, top-of-atmosphere irradiance and altitude lapse estimate | Not local weather; not a live weather simulation; independent of the active NoiseRouter |
| Adaptive Visibility | Classifies OPEN_SURFACE / SEALED_CAVE / TRANSITION and records telemetry | Does not itself suppress vanilla section meshes |
| Client FPS profiler | Records Post-to-Post frame intervals and scene labels | Includes VSync/idle pacing; not a GPU-only measurement |
| Client chunk-upload budget | Optional pacing of already-built section uploads | Does not reduce mesh compilation, GPU draw calls, visible sections or GPU memory |
| Distant Horizons cave LOD controller | Optional DH API integration | Must remain fail-open and be validated on actual client setups |
| install_realscale_visuals.py | Installs pinned Iris/Sodium and external shader/texture packs into a local development run | A development-instance installer, not an in-game visualization subsystem |

The Natural Automate branch adds a single **RealScale Studio** pause-menu entry, with routes to Terrain Lab, Performance Studio and Climate Inspector. World Atlas has been retired and must not be reintroduced.

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

The four `RealScalePerformanceProfile` modes from PR #156 have been selectively integrated into this branch and extended to include the Natural Automate morphology caches: three horizontal morphology caches plus volcanic and karst shape caches. `RealScale Studio` now saves the profile and diagnostic switches to `config/realscale-performance.json`; the runtime reads them at startup, so changes require restart. `Performance Studio` displays live FPS, frame-interval percentiles when diagnostics are enabled, chunk queue state, JVM heap and an approximate cache-array budget for one cache set (not total JVM memory, all density instances, or GPU VRAM). Low Memory can increase recomputation; Smooth Render only time-slices FIFO uploads and does not establish an FPS improvement. Physical-client A/B tests remain mandatory.

**Promotion rule:** keep the existing balanced behavior as default. Do not claim FPS improvement or promote client pacing until repeatable physical-client A/B tests show acceptable p95/p99 frame intervals and no unacceptable terrain-appearance delay.

## 4. Preset classification

The 41 world-preset resources are enumerated in the machine-readable [PRESET_CATALOG.json](PRESET_CATALOG.json). The catalog is an audit snapshot and the validator checks it against both menu tags, the actual world-preset resources, the single public composite biome-source chain and the audited resource counts. It should become the single source of truth for later UI/exporter generation rather than treating all 41 resources as equivalent user options.

### Public profile

- Natural Automate Three Realms — the only ordinary Create World choice.

### Hidden internal / compatibility profiles

The following seven standalone references remain packaged but are no longer separate player-facing modes:

- `natural_three_realms` — Natural baseline used by the composite and retained for compatibility.
- `alpine_three_realms`, `canyon_three_realms`, `glacial_three_realms`, `volcanic_three_realms`, `karst_three_realms`, `dune_three_realms` — standalone reference presets for the six kernels composed inside Natural Automate.

Their kernel code, coefficients and research provenance remain intact. The six internal kernels still require representative multi-seed terrain-quality validation; hiding their standalone presets does not imply that every terrain family has identical validation coverage.

### Complete hidden / legacy resource inventory

The other 33 hidden/legacy resources are intentionally not ordinary menu choices:

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
4. **RealScale Studio** — Terrain Lab, bounded generated-terrain preview, climate/geography overlays, profile comparison and Performance Studio.
5. **Performance Core** — cache profile, memory estimates, worldgen profiling, frame telemetry and optional upload pacing.
6. **Compatibility & Advanced** — Distant Horizons, Chunky lifecycle diagnostics, optional client visuals, experimental toggles and migration information.

### One configuration model

Introduce one versioned, immutable `TerrainProfile` / `WorldProfile` model as the source of truth. It must include an explicit `model_kind` such as `natural_baseline`, `procedural_blend`, `fitted_archetype`, or `measured_dem`; do not force the older three-family blend model and the six fitted morphology kernels into one misleading parameter list. The model is the source of truth for:

- the Create World profile selector;
- Terrain Lab controls;
- offline datapack export;
- preview generation;
- profile comparison;
- diagnostics and provenance.

The profile catalog should describe each ID, display name, status, terrain source, biome-layer stack, dimension set, defaults, legacy aliases, and validation level. Do not maintain separate hand-written lists for the YACL screen, Python exporter, world-preset tags and docs.

Natural Automate is the only player-facing composite profile; it exposes morphology strength, terrain relief and the shared fixed 1:20 climate strength. The three old blended-noise families remain internal for migration/export compatibility and are not shown as selectable player modes. Cave Exploration V2 is enabled in the default Natural Automate final-density codec; legacy presets without the flag retain their original cave behavior. Individual per-kernel tuning and live preview remain future work. Keep measured DEM read-only and optional.

### One Visualization Studio

The unified Studio should provide these tabs or modes:

- **Terrain:** profile selection, shared controls and a sampled height/slope/relief preview.
- **Ecology:** biome and habitat layers, cave-depth selection, ocean/sky systems and explicit wonder opt-in.
- **Climate:** current 1:20 geographic report, virtual solar estimates and altitude-aware climate diagnostics.
- **Overview:** Studio status, current profile and links to the active workspace pages. No local-save scanning or persisted-heightmap view.
- **Compare:** compare profile A/B and preview-versus-saved terrain.
- **Performance:** worldgen stage samples, cache metrics, client frame intervals, upload queue and memory estimates.

World Atlas has been removed. Any future terrain preview must operate only on a bounded synthetic/sample model or the currently loaded world, with no offline save-directory scanning and no hidden server startup.

The preview must distinguish sampled procedural predictions from actual saved Anvil data. Do not claim exact preview parity until the same generator path has been measured against real generated chunks.

## 6. Integration sequence

### Phase 0 — establish a buildable baseline

- Keep unrelated LOTM work out of the RealScale build. The orphan LOTM test and its QA file have been removed from this Natural Automate branch; the separate LOTM PR remains an independent workstream.
- Reconcile the README, version inventory, changelog and current artifact status.
- Clarify the DH–Chunky packaging claim: current main contains `compat/dhchunky` code, while the README describes a separate top-level `dh-chunky-guard/` project that is not present in the audited main tree.

### Phase 1 — catalog and configuration convergence

- Add a machine-readable preset catalog.
- Make the single public composite profile and all hidden/legacy profiles explicit by status.
- Keep Natural Automate as the only player-facing world-generation editor; retain the three Legacy blend schemas only for migration and exporter compatibility.
- Preserve old config migrations, world-preset IDs and saved-world datapack behavior.
- Remove duplicate hand-maintained profile lists by generating menu labels, exporter mappings and docs from the catalog where feasible.

### Phase 2 — ecology composition convergence

- Make the biome-source layer stack explicit and consistent.
- Separate stable layers (natural ecotones, sky/ocean and approved cave habitats) from experimental geological-wonder opt-in.
- Preserve each current profile's output until the relevant multi-seed and Anvil regression tests pass.
- Add source-chain tests for every public profile.

### Phase 3 — Visualization Studio

- Connect Terrain Lab, Climate Inspector and Performance Studio through one RealScale Studio entry point. The shared pause-menu entry and Performance Studio page are implemented in this branch.
- Add bounded terrain preview and layer overlays.
- Reuse profile definitions and deterministic sampling code; previews must not scan local saves or modify existing worlds.
- Add preview-versus-generated Anvil comparison before promoting preview as authoritative.

### Phase 4 — Performance Core

- Run the Natural Automate branch's full CI for the selectively integrated PR #156 cache/profile work.
- Validate all cache capacities, eviction correctness and memory estimates, including the three horizontal morphology caches plus volcanic and karst caches.
- Keep `balanced` as default.
- Require physical-client A/B for `smooth_render`; no FPS claim based on compile or unit tests.

### Phase 5 — documentation and branch cleanup

- Make this document and the preset catalog the current architecture references.
- Move old phase-by-phase notes into a clearly labelled history/archive area only after checking inbound links.
- Audit open PRs and branches by commit ancestry and current source, not by their open status.
- Close or archive stale PRs only after confirming that their useful changes are already present or deliberately rejected. Never delete legacy worldgen IDs as a documentation-cleanup shortcut.

## 7. Evidence and blockers observed on 2026-11-21

- The final integration candidate exposes one public profile; README, catalog and both menu tags are aligned. Natural Automate enables Cave Exploration V2 by default, while legacy codecs retain the old false default. Source metadata is bumped to `2.0.0-preview.2-test.12`; a distributable JAR is not Stable until CI and in-game validation pass.
- The performance implementation in this branch is a selective integration of PR #156; avoid merging duplicate cache/profile files from that draft PR. Run the new branch's own CI and physical-client A/B tests before promotion.
- PR #144 and older fitted-profile branches have heads that are ancestors of main; their changes should not be re-merged just because their PRs remain open. In particular, the legacy `tva_alpine_three_realms` and `tva_canyon_three_realms` QA aliases use smoothed settings and must not be confused with the current public fitted profiles.
- PR #143's zonal/horizontal mapping branch is divergent and proposes a major coordinate-model change: X/Z become 20 source metres per Minecraft block while Y remains 1 metre per block, plus periodic latitude and preset-aware spawn selection. Current main uses 1:20 only for the geographic climate latitude model; terrain X/Z sampling remains 1 block = 1 metre. This changes apparent relief, DEM sampling, caves, structures, rivers and spawn geometry. It needs an explicit product decision and independent saved-world validation; do not merge it into the terrain-consolidation work as a routine cleanup.
- The old `feature/terrain-generation-optimization` branch is divergent and its own documentation labels its DEM cache work experimental; do not replace current main code with it without fresh parity and real-server A/B evidence.
- The USGS 3DEP importer, river-network implementation and several ecology/terrain features already have source-path coverage in main; avoid duplicate cherry-picks from stale branches.
- World Atlas is retired and removed in the Natural Automate consolidation branch. Older Chunky/offline-pregeneration experiments remain out of scope.

## 8. Non-negotiable acceptance gates

1. Java 21 / NeoForge build and all unit/source-integrity tests pass on the exact final commit.
2. The single public Natural Automate profile boots on a dedicated server and has multi-seed saved-Anvil evidence; each internal fitted kernel has direct suitability and terrain-quality regression coverage.
3. Ocean and river water columns, cave roofs and networks, sky-island cores, mountain/plateau geometry, structures, and dimension heights remain valid.
4. DEM imports validate source metadata, checksums, coordinate mapping, coverage gaps and seam behavior.
5. Every public profile's biome-source chain and optional-layer flags match the catalog.
6. Terrain Lab exports the exact profile that the user selected; old config drafts still migrate safely.
7. World Atlas is absent: no save-directory scanner, Anvil reader or hidden server startup remains.
8. Visualization preview is explicitly labelled as sampled prediction until measured parity is demonstrated.
9. Performance claims require repeatable A/B tests with comparable seeds, hardware, mod lists and new-chunk workloads; report p50/p95/p99 and GC/frame data where applicable.
10. Existing saved-world registry IDs and datapack selections remain compatible.

**Final principle:** one coherent product architecture, multiple well-defined internal systems, a small public world menu, explicit experimental controls, and one shared visualization/diagnostic workspace. Consolidation means removing duplication—not erasing working systems or pretending unverified prototypes are finished.
