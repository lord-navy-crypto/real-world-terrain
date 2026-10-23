# RealScale — Changelog / 更新日志

本文件记录 **RealScale 地形主模组**的版本变化。Distant Horizons × Chunky Guard 是单独安装的兼容补丁，版本说明见 `dh-chunky-guard/CHANGELOG.md`。实验构建不能直接视为正式稳定版。

## 2.0.0-preview.2-test.21 — 2026-10-23 — Grand Overworld Cave Corridor Geometry Reuse (Experimental)

### Optimized world generation
- Applied fixed geometry reuse to the actual Grand Overworld cave network (2096-height worlds). The four-layer, 768-block cellular network had already cached halls and seeded passage shapes, but every candidate tunnel voxel recomputed endpoint canonicalization, conservative 3D AABBs, direction-vector lengths, bend-key modulo arithmetic and the costly horizontal `Math.hypot(vx,vz)` call.
- At region initialization, build immutable `TunnelGeometry` descriptors for 16 cardinal links, 8 side galleries, 3 interlayer shafts and up to 8 arterial connections. The eight-slot, per-worker, two-way region graph cache retains these descriptors; eviction only recomputes identical geometry.
- Both full `strength()` and the 47-component masked `strengthFromColumnWithMask()` use identical descriptors. Unchanged: the original `tunnelStrength()` formula (retained as a frozen oracle), the projection numerator/division, bend and sin cross-sections, density warp, XY/Z limits, cave entrances, water/aquifers, noise, biome identities, terrain heights and block/save format.
- Per-voxel distance math keeps IEEE-754 operation order rather than replacing divisions with reciprocal multiplication. Region cache remains bounded and independent for every generation thread.

### QA/release limits
- Added `GrandOverworldTubeGeometrySmoke`, invoking the original per-voxel `tunnelStrength()` as an independent oracle and comparing raw IEEE-754 bits with the precomputed production function on more than 75,000 seeded locations, including main corridors, satellite tunnels, vertical shafts and long arteries.
- Retain all pre-existing Grand Overworld broad-phase mask, corridor-sine, cache-collision and saved-world parity tests alongside full NeoForge, multi-seed real chunk, ocean, sky, Nether and End workflows.
- **Draft only:** CI passing establishes correctness for covered cases, not a faster measured cold chunk-generation rate. Benchmark same-seed/same-hardware Test.20 vs Test.21 overworld fresh-chunk throughput, p95, heap/GC and saved Anvil block identity before any merge. Reject if extra precomputation/regional allocations cost more than repeated geometry.
- Stable `main` and separate DH–Chunky Guard remain untouched.

## 2.0.0-preview.2-test.20 — 2026-10-23 — Grand Nether Tunnel Constant Geometry Precomputation (Experimental)

### Worldgen CPU path
- In the Grand Nether density path, `tube()` previously re-canonicalized its hall endpoints and recomputed the conservative AABB, 3D direction vector and squared centerline length for **each carved density sample**. Those quantities depend on static per-256-block cave regions, not the sampled block.
- Construct 13 immutable `TubeSegment` descriptors once per regenerated cave region: eight center-to-neighbor corridors, four center-to-gallery branches and one interlayer shaft. They are retained with Test.19's four per-worker cached region snapshots.
- The per-voxel algorithm still performs exactly the original projection, uses division by the **identical squared length** (no inverse approximation), calculates bend phase with the original key, preserves the old operation order, and checks precisely the same inclusive bounds. The frozen `tubeBendKey`, component-mask logic, noise warp, source cell and 3D carving formula are unchanged.
- Precomputation is deterministic for any world seed or region. The four-snapshot worker-local bound remains; no global world cache or main-thread synchronization, and no change to lava, structures, bedrock, cave topology, aquifers, overworld, biomes or files.

### Verification and promotion policy
- Add standalone Java 21 `GrandNetherTubeGeometrySmoke`: reconstruct the previous release's tube formula (including canonical order and operation precision) and assert raw double-bit parity for **39,000** synthetic positions across 25 regions, their 13 corridors and shafts.
- Keep all existing independent Nether frozen-reference full-strength, >100k seeded-warp column-mask, region A/B/A cache retention, full NeoForge and real-server saved-world CI gates.
- The experiment does not claim higher chunks/second until same-seed, same-hardware, cold-generation A/B measurements compare Test.20 against Test.19, including p95/GC and complete saved-block equality. If the extra 13 objects per region offset CPU savings, reject rather than promote.
- Stable `main` remains untouched; DH–Chunky Guard remains separately distributed `0.3.1-experimental`.

## 2.0.0-preview.2-test.19 — 2026-10-23 — Bounded Multi-Region Grand Nether Cavern Graph Reuse (Experimental)

### Optimized
- Replace Grand Nether's single mutable per-worker 256x256 cave cell snapshot with **four lazy, bounded, two-way region snapshots**. Repeated region A -> B -> A height queries can reuse already-generated halls, galleries, corridor bend keys and the matching 512-column broad-phase masks instead of rebuilding the entire procedural cave graph after every switch.
- Retain the existing CellCache.initialize() contents, seeds, corridor keys, mask geometry and exact strength() arithmetic. Cache eviction only forces deterministic recalculation; a different worker never accesses another worker's graph.
- Explicitly select the correct region in both the column-mask lookup and strength evaluation so no stale site can be mistaken for the queried X/Z region. Lazy allocation bounds the cost to four snapshots per worldgen worker, rather than an unbounded world cache.
- No vanilla Perlin/Noise, Beardifier or Aquifer mixin; no changes to Y144..440 protected bands, lower Nether lava, upper bedrock, portals, structures, blocks, saved worlds, biomes or dimensions. The Overworld remains unchanged.

### QA and release gate
- New pure Java `GrandNetherRegionCacheSmoke` validates A/B/A retention, two-way hash collision handling, epoch/column-mask retention, forced eviction, four concurrent worker isolation and raw IEEE-754 equality with the independently frozen legacy Grand Nether cave reference.
- Continue running the comprehensive preexisting `GrandNetherCaveNetworkSmoke` and `GrandNetherCaveNetworkParitySmoke`, including >100k warped column parity samples, and all real-server, Nether/End, saved Anvil, overworld, ocean and sky regression workflows.
- Experimental, stacked on Test.18: **do not merge into main** without all new SHA CI checks, matching saved-world samples and a real alternating-jigsaw / cold chunk-generation A/B throughput and p95 memory/GC comparison. Cache retention is a reproducible work avoidance, not by itself proof of TPS/FPS/chunks-per-second gain.
- Optional DH–Chunky Guard remains separate `0.3.1-experimental`.

## 2.0.0-preview.2-test.18 — 2026-10-23 — Exact USGS DEM Boundary Row Elimination (Experimental)

### Optimized
- In `RealDemSeamlessMath.measuredHeight`, the original four-row spline always evaluated `sampleRow(iz-1)` and `sampleRow(iz+2)`, but ignored the first row at the north edge (`iz==0`) and the last row at the south edge (`iz==GRID-2`).
- Compute the two essential in-bounds rows first. Evaluate the outer rows **only if selected by the existing boundary extrapolation ternary**. At those dataset boundaries, this avoids one of four cubic `sampleRow` calculations per measured-height query.
- Retain original X/Z clamps, row samples, endpoint extrapolation coefficients, `smoothSegment` evaluation order, output bits, USGS source rasters, cache keys/capacity, world height and 2048-block transition interpolation.
- No changes to Perlin/Noise, Beardifier, Aquifer, structures, fluids, dimension codecs, caves, oceans, biome identities, sky islands or RNG.

### Validation and limitations
- New Java 21 `RealDemEdgeRowSmoke` reconstructs the **frozen Test.17 original four-row formula** using the production row spline, clamps and smoothstep. It compares raw IEEE-754 bits at over 14,000 deterministic USGS interior, north/south boundary, fractional and out-of-raster input coordinates.
- Integrated the new smoke into the existing `RealDemSeamlessMathSmoke` CI compile/run step; all other NeoForge, multi-seed real-server/Anvil, ocean, sky, Nether and End gates remain in place.
- Test.18 is a draft stacked on Test.17 and must NOT merge into main before green workflows, saved-world comparison and matched real-worldgen cold-chunk A/B evidence. This is a reduction of unused edge interpolations, **not a measured FPS/TPS/chunks-per-second gain**.
- DH–Chunky Guard remains independent v0.3.1-experimental.

## 2.0.0-preview.2-test.17 — 2026-10-23 — Primitive Offshore Ocean Eligibility Probes (Experimental)

### Optimized
- Use a primitive-coordinate `SkyIslandLayout.IntCoordinatePredicate` in the **actual** `SkyOceanBiomeSource` ocean-tier classification to avoid boxing two integers for each remote climate probe. Eight rings of 16 fixed sample sites can require up to 128 callbacks per examined archipelago site.
- Keep the established `BiPredicate<Integer,Integer>` overload unchanged for existing callers and as a frozen reference; the new `oceanTierUnboxed` uses the same base deep-ocean gate, absolute X/Z sample positions, sampled ring order, cutoff indexes and tier `0/1/2/3` formula.
- No caching or changes to biome source inputs, RNG, skyland footprints, elevations, ocean floor, caves, structures, fluids, world height, saved world format or dimensions.

### Validation and limitations
- Add a pure Java 21 reference comparison over more than 5,000 curated and pseudorandom coast cases including negative coordinates and integer extremes, comparing *every climate coordinate visited* and the exact stop position alongside tier equality.
- Retain the multi-seed NeoForge server, ocean ecosystems, all sky/Anvil, ecotone, caves and far-worldgen QA; keep this version Draft until the newest complete CI run passes.
- Eliminating Java integer boxing is an allocation-pressure optimization hypothesis, **not proof of faster whole-world chunk generation**. Match same-seed real cold worldgen A/B and saved-block parity before promotion.
- Separate DH–Chunky Guard stays version `0.3.1-experimental`.

## 2.0.0-preview.2-test.16 — 2026-10-23 — Ecotone Climate-Ray Geometry Lookup (Experimental)

### Optimized
- Precompute all fixed quart-to-block squared distances for the existing eight ecotone scan headings and six possible step distances. During each biome-border search, read the table instead of re-evaluating direction validation, multiplications and distance arithmetic.
- Reuse that same squared distance for both nearest-border tie breaking and transition-width membership instead of calculating it twice.
- Preserve the original ordered cardinal/diagonal scan, first-different-biome stop, exact integer distance formula, radial-band width limits, `>=` tie priority, climate sampler queries, biome registry choices and protected oceans/rivers. No additional caching of biome identity or new worldgen threads.

### Validation and release gates
- Add `EcotoneRayTableSmoke`: direct old-math equality for all headings, steps and width thresholds; 50,000 deterministic synthetic first-boundary arrangements checked against the original search and tie rules.
- Retain NeoForge build, ecotone/cave unit checks, measured terrain, oceans, multi-seed server Anvil, Nether/End and sky-island regression. This test is stacked after Test.15 and remains **Draft**, separate from stable main.
- Reduction of per-ray integer operations is a hypothesis, **not yet a measured real-world chunk-generation speedup**. Compare matched seeds/chunk workloads and save parity before any merge or performance claim.
- Optional DH–Chunky Guard stays separately installable at `0.3.1-experimental`.

## 2.0.0-preview.2-test.15 — 2026-10-23 — USGS DEM Row Interpolation Hot-Path Cleanup (Experimental)

### Optimized
- Select a clamped source raster row **once** in `RealDemSeamlessMath.sampleRow`, instead of doing the same 2–4 row/column boundary clamps for every corner read in the cubic row interpolation.
- Preserve sample ordering, Hermite smoothing, endpoint extrapolation, monotone slopes, measured DEM inputs, 2,048-block fades, cache size and hashing, source validation and world height.
- No changes to underlying vanilla Perlin/Noise, Beardifier, Aquifer, oceans, caves, biome registries, sky islands, fluids, structures, world generation RNG or save data.

### QA / release gates
- Independent Java comparison reconstructs original `corner(row,col)` calls and `smoothSegment()`; all 210 row/column/fraction combinations require raw IEEE-754 bit equality, including both raster edges.
- Existing measured DEM tile fidelity, cache-collision/eviction, ocean, cave, sky Anvil and server checks remain required. Test.15 remains stacked on Test.14 and **Draft**, not merged into stable main.
- Local spline arithmetic reduction is a candidate micro-optimization, not an independently demonstrated increase in cold chunk generation throughput. Require matched multi-seed A/B on the same hardware before claiming speedup.
- Separate optional DH–Chunky Guard remains `0.3.1-experimental`.

## 2.0.0-preview.2-test.14 — 2026-10-23 — Conservative Sky Island Candidate Group Culling (Experimental)

### Optimized
- Before hashing and allocating five satellite sites for each of the nine neighboring sky-island lattice bases, cheaply reject an entire base group when the existing maximum satellite reach cannot touch the decorating 16×16 chunk.
- Use the existing 94-block conservative axis limit (actual base radius <=14; satellite offsets 80 plus radius <=11) and 64-bit comparisons. Near Java integer overflow boundaries, fall back to the complete original enumeration instead of changing legacy behavior.
- Preserve original satellite site math and order, chunk candidate tests, biome gating, validation reads, immutable block writes, rock/decor/beacon palette and RNG sequence. No new cache or background worker.

### Validation and limitations
- Add an independent Java 21 oracle that re-evaluates the **original** five-site candidate overlap for each base group and rejects any false-negative shortcut across signed boundaries, world-border chunks and integer extremes.
- Retain all existing sky-island, DEM, cave, ocean, Nether, End, Anvil and server tests. Test.14 remains experimental until CI and real server saved-block comparison prove it safe.
- This targets sky-island feature generation overhead, not vanilla Perlin, Beardifier, Aquifer or rendering. No measured chunks/second, TPS or FPS claim without matched actual worldgen A/B data.
- DH–Chunky Guard is separate and unchanged at `0.3.1-experimental`.

## 2.0.0-preview.2-test.13 — 2026-10-23 — Sky Island Validation Allocation Cleanup (Experimental)

### Optimized
- Use a single local `BlockPos.MutableBlockPos` for repeated read-only sky-island column validation (block/fluid/overhead checks), rather than allocating a new immutable coordinate for each visited height.
- Preserve the exact original **immutable BlockPos writes** for all solid columns, flowers and beacon blocks; the mutable probe is never used with `WorldGenLevel.setBlock` or stored beyond synchronous preflight.
- Avoid constructing an amethyst-beacon coordinate for an island site that placed no blocks (where the previous boolean guard always prevented beacon placement).
- No new caches, background workers, world-height changes, extra RNG, render hooks or new dependencies.

### Validation and limitations
- The island lattice, chunk-clipped candidate iteration, biome check, floating-point geometry, block palette, flower rules, beacon conditions and original worldgen outcome are intended to remain unchanged.
- Added a candidate-order parity test comparing complete full-radius vs chunk-clipped scans across positive and negative coordinates; added explicit read-only mutable probe and immutable-write assertions.
- Fixed a stale sky-island source test that still demanded a deprecated out-of-chunk full-radius loop.
- Real dedicated-server saved-world checks and client FPS/TPS benchmarks remain separate. No actual speedup percentage is claimed.
- Optional DH–Chunky Guard remains version `0.3.1-experimental`.

## 2.0.0-preview.2-test.12 — 2026-10-23 — Cave and Seabed Patch Search Optimization (Experimental)

### Optimized
- Reuse one local `BlockPos.MutableBlockPos` for synchronous bounded vertical cave-floor searches in `HabitatPatchFeature`. Up to 81 candidate height probes now avoid creating a separate immutable coordinate for each tested Y position; the original search order, roof protection, highland radius, and return value remain unchanged.
- Reuse the above position during a cave-floor air/fluid validation without changing the original short-circuit read order.
- Replace per-candidate `new int[]{0,1,-1}` arrays with a single private static offset array. The sequence **0, +1, -1** and all RNG draw points are unchanged.
- Reuse the validated marine seabed `BlockPos` when applying sediment decoration, avoiding a second seabed coordinate allocation. Water, block/structure protection, and random rock choice remain unchanged.

### Validation and limitations
- Added source-regression tests for cave height limits, allocation behavior, strict cave-floor read order, unchanged RNG points, and marine water-before-sediment validation.
- Added a Java 21 `HabitatFloorSearchSmoke` gate checking high-altitude caves, descent ties, empty caves, and roof-restricted bounds.
- Same caves, oceans, biomes, ores, sea level, USGS terrain, save/registry IDs and dimension height.
- No measured FPS/TPS uplift is claimed. Real server generation checks and matched client A/B tests are still necessary. Independently distributed DH–Chunky Guard remains v0.3.1-experimental.

## 2.0.0-preview.2-test.11 — 2026-10-23 — Low-Allocation Cave and Marine Decoration (Experimental)

### Performance
- Avoid constructing temporary cave and marine `BlockPos` candidate objects for placement attempts outside the originating chunk. Candidate X/Z are compared against chunk bounds **before** creating world positions; the same Java signed-int arithmetic, radii, attempts and random draws are preserved.
- Reuse the existing "above" and "below" cave-pocket locations when checking block and fluid states. Preserve the original short-circuit ordering: air at target and above, fluid emptiness, then natural support rock and dry floor.
- Replace per-direction neighbor/below position allocations during cave spring water checks with one local mutable probe. Preserve the original four-direction enumeration and the standing-water / one-block-below checks.
- These are allocation/memory-pressure reductions, not new biome types or terrain algorithms. No extra caches, background threads or default profiling.

### Verification and release status
- Added static and signed-integer parity regression coverage for negative/extreme block coordinates, early chunk rejection, cave-pocket read order, water-check semantics and random-draw placement.
- Retain existing terrain, ecology, mountain height, underground ores, ocean floor and save-format behavior.
- **No measured FPS/TPS or chunk-generation speedup is claimed**; real server replay and client A/B validation remain required before stable release.
- The separate optional DH–Chunky Guard remains version `0.3.1-experimental`.

## 2.0.0-preview.2-test.10 — 2026-10-23 — Cave/Marine Worldgen Hot-Path Cleanup (Experimental)

### Performance improvements
- Reuse one local `BlockPos.MutableBlockPos` while searching for a real cave pocket, instead of allocating a separate immutable origin position for every depth probe. The exact `HabitatFloorSearch.nearest` visit order, height bounds and pocket validity checks are unchanged.
- Replace four repeated `Math.floorDiv(x, 16)` calls during cave and marine decoration's same-chunk checks with signed arithmetic shifts (`x >> 4`), which give the same result for positive, negative and extreme world coordinates.
- Compare the already available overworld dimension `ResourceKey` directly against `Level.OVERWORLD` rather than allocating a string representation on every feature placement.
- Turn per-placement `LongAdder` tracking and the end-of-process summary hook into **explicitly enabled QA diagnostics**. By default the feature no longer performs atomic attempt/placement increments or installs the reporting shutdown hook. Enable when testing with `-Drealscale.habitat.report=true`.

### Unchanged gameplay behavior
- The original cave floor search sequence, positions, feature attempts and RNG calls remain unchanged.
- No cave biome, marine coral, vegetation, ores, protected structures, fluid checks, dimension settings, height or seed calculations have been removed or altered.
- No claim of a measured FPS/TPS improvement: source and NeoForge regression tests establish basic correctness, but full client and dedicated-server performance A/B measurements are still required.
- The separately installed DH–Chunky Guard remains **0.3.1-experimental**, with no added background monitoring.

## 2.0.0-preview.2-test.9 — 2026-10-23 — Seamless USGS Cache Maintenance (Experimental)

### Improved
- Updated the existing **USGS Big Sur seamless bicubic-height cache (4,096 entries)** to bounded, per-worldgen-thread, two-way sets. Alternating coordinates that collide in the hash can now remain cached simultaneously; total capacity and world-height computations stay unchanged.
- Applied the same collision-tolerant scheme to the existing **1,024-entry USGS transition/fade-weight cache**, preserving all fade widths, transition-boundary rules and original floating-point expressions.
- Existing last-column fast paths, overflow-safe far-field rejection, and thread isolation remain unchanged; misses and evictions only recompute exactly the same original interpolations.
- No changes to worldgen density equations, mountain and cave geometry, sea level, dimension height, Minecraft renderer, biome IDs or save formats.

### Verification
- Existing USGS seam/slope/transition test now adds deliberate colliding-pair tests that check **both cache entries survive** and values remain raw-bit identical to original uncached calculations.
- Run the established Java 21, NeoForge, USGS test and real server CI before release. **No actual FPS or whole-world chunkgen speedup is asserted without matched tests.**
- Independently built DH–Chunky Guard remains v0.3.1; not included in the RealScale JAR.

## 2.0.0-preview.2-test.8 — 2026-10-23 — Experimental Generation Optimizations

### Optimized
- Converted the existing 4,096-entry USGS X/Z elevation-and-blend cache from direct-mapped to **two-way set associative** storage, keeping the *same total entry capacity*. Two frequently revisited columns whose hashes collide can now coexist without repeatedly rerunning DEM interpolation. Both coordinate and blend-width keys are verified on every cache hit; collisions can never substitute terrain values.
- Added an exact-height early return in the optional cave biome decorator. Above Y64 and below Y-60, return the original biome immediately without performing holder-key/namespace lookups. This matches the existing cave-region selection boundaries exactly.
- Retained Test.7's default-OFF frame/queue/Aquifer diagnostics. Neither change modifies noise equations, elevation samples, biome selection inside caves, saved preset IDs, ores, cave carving, ocean habitats or Minecraft's render distance.

### Verification
- Extended the pure-Java USGS DEM smoke test with a deliberately colliding pair that must retain *both* cached objects while matching original interpolation results bit-for-bit.
- Added static regression coverage for the cave-depth shortcut's unchanged boundary and original source delegation.
- Runtime speedups and client FPS changes are **not claimed** without matched worldgen and physical-client A/B measurements.

## 2.0.0-preview.2-test.7 — 2026-10-23 — Experimental

### 新增 / Added
- 新建世界菜单提供六个 **RealScale 自有品牌预设**：自然地貌、高山地貌、峡谷与河谷、USGS 实测地形、自然奇观与跨维度生态、洞穴与海洋生态（实验）。
- 为每个新预设增加与对应历史配置相同的世界生成 JSON 入口，并增加验证新旧生成配置一致的自动测试。
- 旧存档可能引用的历史内部世界预设资源仍保留，但不会再作为新的菜单选项显示。

### 变更 / Changed
- 模组显示名称：`RealScale — Terrain, Caves & Oceans (Experimental)`。
- JAR 文件名：`RealScale-NeoForge-1.21.1-2.0.0-preview.2-test.7.jar`。
- 更新中英文世界创建界面、项目说明、Terrain Lab 描述和构建检查。

### 修复 / Fixed
- 同步更新洞穴、海洋、自然生态和世界菜单测试中受到预设改名影响的断言。
- 修复 Terrain Lab 重命名后遗留的变量引用问题，保持原有维度配置保护。

### 性能与兼容 / Performance & Compatibility
- **Test.6 主要是品牌命名、预设别名及相关测试修复；相较 Test.5 不宣称新的 FPS 或地形生成速度提升。**
- 保留实验性的 Beardifier 高度提前返回，默认开启；可使用 `-Drealscale.experimental.beardifier.ycull=false` 关闭。该实验尚不保证所有存档逐方块一致。
- Aquifer 只读诊断默认开启（`-Drealscale.experimental.aquifer.audit=false` 可关闭），不等于加速算法。
- 客户端实验性 GPU 区块上传节流默认**关闭**（仅显式启用 `-Drealscale.experimental.client.uploadbudget=true` 时开启）。
- DH–Chunky Guard 不是此 JAR 的内置部分；如需 DH + Chunky 兼容补丁，应单独安装版本匹配的独立 JAR。

### 测试与限制 / Validation & Limitations
- [CI 源码完整性、单元测试和 NeoForge 主模组构建成功](https://github.com/lord-navy-crypto/real-world-terrain/actions/runs/39913274639)（提交 `7bea910603b76beabf3dcc0d806b762be2ea920a`）。
- 此版本仍位于[实验 PR #96](https://github.com/lord-navy-crypto/real-world-terrain/pull/96)，不代表已合并主分支、全量旧存档通过或 Mac / Iris / Sodium / 特定光影全部认证。
- 不要在同一 `mods` 目录同时安装不同版本的 RealScale 主模组；测试前备份存档。

## 2.0.0-preview.2-test.5 — 2026-10-22 — Experimental

### 地形与生态 / Terrain & Ecology
- 增强洞穴生态：新增地下群系选择与装饰逻辑，包含八种洞穴群系，包括荧光苔藓溶洞与化石石灰岩洞穴。
- 增强海洋栖息地的方块装饰，如冷水礁、海草/海藻草甸和暖水珊瑚环境；**不是**海洋动物或流体物理重写。
- 新洞穴与海洋生态实验主要通过单独的可选预设体验，不能宣称四个基础预设均改为新的洞穴群系生成模式。

### 性能与诊断 / Performance & Diagnostics
- 新增 Beardifier 原版结构影响高度范围提前返回实验；特定测试下有生成耗时收益，但不等于 FPS 提升，也不保证与原版逐方块一致。
- 增加 Aquifer 只读统计及客户端相关性能诊断，保留显式开关。
- 新增或加强 RealScale、Distant Horizons、Chunky 兼容回归测试与 LOD 持久化检查；兼容补丁仍是独立 JAR。

## 2.0.0-preview.1 — Earlier preview baseline

- 已包含自然、高山、峡谷、USGS Big Sur 实测地形和原有跨维度、自然过渡与生态地貌系统。
- 用于与后续实验版进行对照与必要的回退；不意味着 Preview.1 获得全面 Stable 认证。

## 安装要求 / Installation

- Minecraft **1.21.1**、NeoForge **21.1.x**、Java **21**；客户端配置界面需要 YACL **3.8.1+**。
- 保留历史内部 ID 是为了保护已有存档；新版本仍需真实世界和多模组联合验证。
- 稳定发布前还需关注区块内容一致性、RealScale 高地形高度、长距离区块、DH 远景及 Iris/Sodium/光影客户端实际呈现。

### English release summary — 2.0.0-preview.2-test.7

- **Added:** Six RealScale-branded Create World presets, matching historical worldgen configurations without deleting legacy saved-world identifiers.
- **Changed:** Mod title, artifact filename, and English/Chinese player-facing preset labels.
- **Fixed:** Renaming-related regression tests and a Terrain Lab variable reference.
- **Performance:** No new speedup claimed over Test.5; experimental Beardifier height culling remains enabled by default, while the optional vanilla client GPU-upload override remains disabled by default.
- **Compatibility:** The DH–Chunky Guard is separate; shaderpack and GPU LOD support still require physical client validation.
- **Status:** Experimental release candidate; main NeoForge build CI passed, broader saved-world/client tests are not a blanket certification.
