# RealScale — Changelog / 更新日志

本文件记录 **RealScale 地形主模组**的版本变化。Distant Horizons × Chunky Guard 是单独安装的兼容补丁，版本说明见 `dh-chunky-guard/CHANGELOG.md`。实验构建不能直接视为正式稳定版。

## 2.0.0-preview.2-test.28 — 2026-10-24 — Lazy Precomputed Nether Cave-Height Candidate Bits (Experimental)

### Hot-path optimization
- Test.27 safely avoided unnecessary seeded noise warp on Nether density voxels outside the vertical influence of the source column's 19-component cave mask, but determined that reach by scanning up to 19 caves and tubes **on every Y sample**. Because Nether region graph geometry is immutable, those comparisons produce the same result for all subsequent calls at the same integer world Y.
- Test.28 replaces repeated Y bound scans with a worker-local primitive table: `CellCache.heightComponents[Y-MIN_Y]` is an exact 19-bit mask of all rooms/tunnels that could touch the full seeded +/-2 Y warp interval. The actual candidate check becomes `(sourceColumnMask & heightComponents[index]) != 0`. This preserves Test.27's inclusive comparisons, cave topology, 19 bit positions, and exact original zero-strength guarantee.
- Lazily populate the height table only on the first **nonzero source column mask** in a 256-block cave graph, avoiding setup in one-off/empty regions. The table uses **297 primitive ints (~1.2 KB) per initialized snapshot**, bounded by Test.19's four per-worker region snapshots. On cache eviction/reinitialization, invalidate and safely recompute from the new seeded graph; never share the table between threads or cave regions.
- The `mask=-1` edge sentinel always bypasses the height table and runs the original density path: X/Z warp can enter another region. The out-of-band Y helper also keeps conservative fallback instead of indexing invalid ranges. Vanilla noise math, Nether lava and structures, actual world blocks, saved Anvil chunks, and all Overworld/ocean/sky/End presets are unchanged.

### Independent proof, quality and promotion
- New `GrandNetherHeightBitsParitySmoke` independently reconstructs Test.27's original room and tube vertical AABB tests for each of 297 protected heights over 49 seeded region graphs. It verifies the **entire 19-bit table per height**, all 19 individual component masks, additional random masks, no eager setup on zero masks, `-1` boundary safety and reset under region eviction (more than 300,000 exact comparisons).
- Retains original `GrandNetherWarpHeightPruneSmoke` checking the **full frozen 3D strength** at every pruned position across multiple seeded X/Z/Y displacements, plus the Nether double-bit reference, cave topology, 19-bit column mask, A/B cache, multi-worker, real-server multi-seed saved-world and NeoForge tests.
- Draft based on Test.27 (#120), stable main untouched. All-green CI proves covered equivalence but does **not** prove faster chunk generation: compare Test.27 vs Test.28 using same-seed cold Nether chunk throughput/p95, CPU, memory/GC and saved-block parity before merging. Lazy precomputation has startup and memory costs, so reject if no measured net benefit.

## 2.0.0-preview.2-test.27 — 2026-10-24 — Conservative Nether Cave Height Exclusion Before Noise Warp (Experimental)

### Real world generation CPU reduction
- The protected Nether cavern density previously skipped warp noise only if the horizontal source-column's 19-component cave mask was empty, or if base density already lay below -1.8. Many source columns whose X/Z overlaps a tunnel or chamber still have hundreds of unaffected Y voxels: the old path sampled the seeded noise warp and 3D cave graph at all of those heights.
- Add a strictly conservative, per-voxel **vertical reach check** for the current 19-bit source-column mask. The already-cached two-layer halls, four galleries and 13 immutable tube AABBs bound all possible 3D cave influence. If the entire allowed `[Y-2,Y+2]` seeded warp interval is disjoint from every horizontally eligible 3D component, return the original base density *before invoking `warp.compute(context)`*.
- At source X/Z locations where a +/-5 X / +/-4 Z warp might enter another 256-block cave region, the existing `mask=-1` sentinel forces the original complete warp and cave graph evaluation without pruning. Any possible cave intersection, including a tangent boundary, falls through to the original branch. All operations use the original non-approximated bounds; no new cache entries or shared thread state.
- Keep intact all original cave void strengths, seeded noise, Java double calculation order, vanilla Nether lava/bedrock/structures, saved-world blocks, realm heights, Overworld, ocean, sky and End. The optimization does not touch Perlin/Noise, Beardifier or Aquifer math.

### Testing and promotion
- New standalone Java 21 `GrandNetherWarpHeightPruneSmoke` probes more than 35k source X/Z/Y combinations including 256-cell edges, cave-rich hall centers, signed far coordinates, both Nether cave strata and all protected Y bands. For every **positive-mask Y-pruned position**, the test evaluates the *full original `strength()`* over nine fractions of the full seeded +/-5 X, +/-2 Y, +/-4 Z displacement, requiring exact raw IEEE-754 zero, not an approximation. It also checks mask parity and forced no-pruning on cell edges.
- Preserve all previous frozen Nether cave, 19-component column mask, collision retention, 200k single-mask, 39k tunnel arithmetic and real server/Anvil three-realm tests. The new gate must pass on newest Test.27 SHA before any promotion.
- Draft stacked on Test.26 PR #119. This test documents **how many potential warp calls are safely avoided on sampled positions**; it is not a true measured whole-world or all-seed speedup. Require matched same-hardware cold Nether chunk throughput, CPU, p95 and heap/GC against Test.26 with exact Anvil saved-block parity before merging.
- Stable `main` and separate DH–Chunky Guard remain unchanged.

## 2.0.0-preview.2-test.26 — 2026-10-24 — Two-Way Nether Cave-Column Mask Retention (Experimental)

### CPU worldgen experiment
- Nether cave source columns originally retained one directly mapped 19-bit candidate mask per hash slot. Two different hot X/Z columns with an identical 9-bit hash evicted each other every time, regenerating all 19 chamber/tunnel broad-phase bounds on each alternating density call.
- Convert the **existing 512 primitive mask slots** to **256 two-way buckets**. On lookup, check both coordinates+epochs before accepting a mask. Insert into a free way first and use a one-byte per-bucket deterministic alternate victim when two entries collide; no mask or geometry is shared between worker threads or different source cells.
- Keep the exact original 19-component candidate expression, radius/AABB early rejection, world-seed cave graphs, four region snapshots, original 256-block graph boundaries, warp and fallback logic. Replacement changes only how long exact computed masks are retained; safe signed X/Z, cross-region and column-epoch checks remain.
- This is a fixed-capacity cache, not world-scale memoization: still exactly 512 `int` entries for X, Z, epoch and component bits, plus **256 bytes per live cave-region cache** for victim selection. Cache misses always recalculate the original candidate mask.
- No change to Nether lava/bedrock/structures or any saved blocks, biomes, dimensions, oceans, surface generation, Aquifer, Beardifier, vanilla noise or other realm behavior.

### QA and decision gate
- New `GrandNetherColumnMaskTwoWaySmoke` checks 12,000 alternating same-region old-hash-colliding A/B columns: original direct-map model has 12,000 misses while new two-way cache retains both with only two cold misses. This **counts prevented recomputations in a manufactured collision workload, not measured chunk speed**.
- Verify independently recomputed 19-bit candidate masks, forced third-key collision eviction, full cave strength raw-double parity, worker-local thread isolation, region switches and exactly 512 slots. Existing full Nether reference, 19-bit AABB, 200k source-mask parity, tube geometry and real NeoForge server/Anvil/multiseed tests remain.
- Keep Draft stacked on Test.25 until all latest-head CI and matched same-hardware, same-seed **cold Nether chunk/s, p95, CPU and allocation/GC** measurements show practical benefit. Two-way hit probes add extra checks and could reduce throughput on collision-free workloads; revert if no reliable gain.
- Stable `main` and DH–Chunky compatibility patch remain untouched.

## 2.0.0-preview.2-test.25 — 2026-10-24 — Grand Nether Tube Bend and Orientation Constant Reuse (Experimental)

### Actual worldgen hot-path work reduction
- After Test.20 cached immutable tunnel endpoints/vectors/AABBs and Test.24 avoided repeated mask lookups, the Grand Nether's per-voxel `tube()` still recalculated two seeded `Math.floorMod(long,...)` bend values, determined its dominant axis with multiple `Math.abs` calls, and multiplied the same radius by itself on every cave sample.
- Move those constant calculations into the existing region-local `TubeSegment` constructor: signed `bendSeed1` and `bendSeed2`, original ordered `bendAxis` selection (including tie semantics) and `radiusSquared`. The existing four-snapshot worker-local region graph automatically reuses these values.
- The density hot path still calculates `curve=4.0*t*(1.0-t)`, multiplies the unchanged integer bend seeds by the curve, computes the precise same per-axis displacements, and divides the same squared-distance numerator by the same radius square. No reciprocal multiplication, approximate trigonometry, changed floating-point evaluation order or RNG.
- The original `GrandNetherTubeGeometrySmoke` frozen pre-Test.20 tube equation remains unchanged and independently checks the actual optimized private `tube` on tens of thousands of fractional positions across all 13 links/shafts and 25 source regions. No impacts on Overworld, ocean, sky, Nether structures/lava/roof, Aquifer, Beardifier, seed, blocks or save format.

### Verification and release criteria
- Added `GrandNetherTubeBendConstantsSmoke`: assert exact unsigned-shift/signed-modulo bend constants, original axis comparison/tie selection and raw IEEE-754 radius-square parity for 31x31 region graphs (12,493 segments), including horizontal and vertical axes. CI runs the entire frozen reference, region cache, 19-component mask and single-mask tests as before.
- Keep all source integrity, NeoForge, multi-seed actual worldgen, Anvil saved-world, 180-second distant-chunk, ocean, sky and three-realm server gates. A pass validates covered behavior **not** a claimed throughput gain.
- Draft stacked on Test.24: compare Test.24 vs Test.25 cold same-seed chunk generation throughput, p95 generation time and GC/heap. Discard this optimization if real measurements do not justify extra per-segment constant storage. Stable main is unchanged.

## 2.0.0-preview.2-test.24 — 2026-10-23 — Grand Nether Single Candidate Mask Per Density Voxel (Experimental)

### Optimization
- `GrandNetherCaverns.computeUnprofiled` previously fetched the 19-bit, warp-inclusive X/Z component mask twice for most non-empty density voxels: once through `mayContainVoidNearColumn` to decide whether to skip noise, and once inside `strengthFromColumn` before testing the 3D caverns. Despite Test.23's faster mask construction, both checks repeated column cache hashing, region lookup and coordinate division.
- Compute the same candidate mask once; return immediately if it is zero. For all other cases, pass the integer mask through `GrandNetherCaveNetwork.strengthFromColumnWithMask` without re-querying the column cache.
- Preserve original `strengthFromColumn` as the compatibility entrypoint, including its original full fallback for Y and warp outside the permitted support. The new known-mask evaluator **validates the full 5-block X / 4-block Z warp and cave-cell coordinates before trusting any supplied mask, even zero**; its `-1` cell-edge behavior and original 19-component evaluation sequence are unchanged.
- No persistent mask sharing across source columns or worker threads, no changes to world seeds, biome/registry identifiers, Nether lava and bedrock, structures, noise equations, cave geometry, existing worlds or save files. Test.23's AABB fast path and Test.20's immutable region geometry stay enabled.

### Evidence / promotion
- New standalone `GrandNetherSingleMaskParitySmoke` checks more than 200,000 baseline double-mask vs single-mask raw IEEE-754 density outputs at negative/positive cell boundaries, distant coordinates, old and new cave levels, and seven exact warp fractions. Tests known positive hall cores and unsupported-warp full-strength fallback even when provided a zero mask.
- Continue the previously existing 19-bit frozen AABB, 39k tube geometry, 100k+ warped mask, multi-worker region reuse, NeoForge server/Anvil, sky/ocean/Nether/End and multi-seed overworld tests.
- Experimental stacked Draft: only promote after full newest-head CI, saved-block identity and matched Test.23-vs-Test.24 same-seed/cold Nether fresh-chunk throughput, CPU, p95 and GC measurement. Eliminating duplicate bookkeeping is a known logical saving, **not** yet a measured chunks-per-second improvement.
- Stable `main` and independent DH–Chunky Guard remain unchanged.

## 2.0.0-preview.2-test.23 — 2026-10-23 — Grand Nether Column Mask AABB Reuse (Experimental)

### Optimized
- The Grand Nether density source builds an up-to-19-bit, per-X/Z cave candidate mask before computing expensive noise warp and 3D rooms/corridors. Until Test.23, every mask-cache miss recomputed up to 13 tunnels' endpoint min/max bounds and expansion radii, although Test.20 already stored exactly these tunnel AABBs inside its region-local `TubeSegment` descriptors.
- Replace only the *horizontal candidate test* for eight neighbor corridors, four satellite branches and one interlayer shaft with comparisons against the stored `TubeSegment.minX/maxX/minZ/maxZ`. All hall candidate tests, bit ordering, region selection, near-cell boundary fallback and cached masks remain unchanged.
- Retain the original `tubeCouldTouch(Hall,Hall,double,...)` equation verbatim for independent parity reference. No new per-column allocations, persistent world caches or per-voxel approximation.
- All Nether dimensions, lava, bedrock, structures, seeded noise, cave topology, original block content, overworld biomes, DEM, aquifers, sky and ocean features remain unchanged.

### Validation and release gate
- Add `GrandNetherMaskAabbParitySmoke` (standalone Java 21) to independently reconstruct the full old 19-bit component mask for more than 19,000 negative/positive, empty, hall-adjacent and fractional-coordinate cases using the **pre-Test.23 Hall/endpoints/radius formula**, and compare each bit to the new cached-AABB mask.
- Preserve all existing frozen Nether bit parity, continuous tunnels, four-worker region cache, 39k tube raw-double-bit, 100k+ seeded-warp column parity, full server/Anvil, multi-seed, far-chunk, sky, ocean, Nether and End CI suites.
- Draft stacked on Test.22 PR #115. Green CI proves covered behavior equivalence, **not** a measured worldgen speed increase. Require same-seed/same-hardware fresh chunk Test.22-vs-Test.23 throughput, p95, GC and saved-block parity before promotion; reject if benefit is absent or memory regresses.
- The stable `main` and optional separate DH–Chunky Guard are untouched.

## 2.0.0-preview.2-test.22 — 2026-10-23 — Cached Grand Overworld Cave-Column Broad-Phase Bounds (Experimental)

### Worldgen optimization
- The 2096-high Grand Overworld cave density's per-X/Z broad-phase checks up to **47 room/tunnel components** to identify whether any giant cave can affect a warped column. On mask-cache misses, `columnComponentMask` recomputed horizontal endpoint min/max and radius reach for every eligible tunnel, despite Test.21 precomputing the identical bounds per cave region.
- Use the existing immutable `TunnelGeometry.minX/maxX/minZ/maxZ` from Test.21 in the column-mask scan. Keep exactly the original comparison inclusivity, mask bit positions, 768-block cell choices, height bounds and fallback near warp-cell boundaries.
- Retain the complete previous `tunnelCouldTouch(Hall,Hall,Passage,...)` formula untouched as a frozen reference. The new overload only compares the cached endpoints, with no floating-point approximation and no extra per-column heap allocation.
- No changes to cave geometry, RNG, Perlin/Noise, Beardifier, Aquifer, fluid updates, structures, biome identities, 2096-height world limits, chunk saves or existing Nether/End/sky/ocean features.

### Tests and gate
- New standalone Java 21 `GrandOverworldMaskAabbParitySmoke` independently constructs the original 47-component long mask from the original hall and passage AABBs and compares every bit to the Test.22 optimized mask across more than 5,000 positive/negative, empty, near-hall and boundary-oriented probes.
- All existing Overworld cave geometry, mask collision, full strength/warp, 4-family multiseed real server, 180-second distant chunk generation, Anvil saved-block and ecosystem tests remain enabled.
- A passed exact-mask test demonstrates equivalent candidates, **not** a measured throughput improvement. Keep stacked Draft until full latest SHA CI and a matched Test.21/Test.22 cold-chunk throughput, p95 and GC comparison confirms benefit, and saved blocks remain identical.
- Stable main and separate DH–Chunky Guard `0.3.1-experimental` are unchanged.

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
