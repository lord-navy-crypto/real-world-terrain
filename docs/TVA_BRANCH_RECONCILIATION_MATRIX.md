# RealScale TVA: correctionist branch reconciliation audit
**Audit date:** 2026-10-17 · **target:** PR #30 `integration/tva-main-consolidation-20261016` → `main`
**Criterion:** preserve the newest behavior of each system, not blindly overwrite it with an older copy. Commit ancestry and file presence are different from proving runtime equivalence.

## Actual Git ancestry (GitHub compare evidence)
| Historical head(s) | Integration relationship | Correctionist action |
|---|---|---|
| PR #15–24, #26–29, plus Grand Ecology (#14), River/Structures/Perf (#16–22) | All examined heads are ancestors of PR #30 (ahead, behind=0) | **Already integrated** as history; do NOT cherry-pick again |
| PR #3/#4 merged into main | Already part of base history | Keep existing code |
| PR #5 (DEM locality), #6 (ocean/sky), #8–9 (Nether caves), #10–13 (ecology) | Diverged histories, but current candidate contains **every blob path** from each checked historical branch; representative source implementations were verified newer | Preserve newer implementation, use live-world regressions, avoid reverting to old snapshots |
| PR #25 equal-area climate recovery | Diverged by 17 commits; **7 unique historical paths missing** from integration before this review | Restored its distinct *single-pole* equal-area geometry as opt-in; left the integrated two-crest formula and active mountain/basin climate intact. Original PR25 settings are not directly safe to transplant |
| PR #1, #2 (v1.2 / v1.3 older terrain) | Diverged. **No missing active production Java/worldgen resource paths**; missing 3 and 31 paths, respectively, were historical CI/test/docs | Preserve modern sources. Keep old branches for archaeology; review old test assertions before reactivating them |
| 2026-10-12 archived smoother experiment | Diverged; absent paths overlap obsolete historical tests/docs/workflows | Historical evidence only; do not replace newer smoothing defaults |

## A real conflict: why PR #25 must not be force-merged
PR #25's old `noise_router.temperature` used separately named `biome_temperature` nodes with **`valley_temperature → basin_temperature`** and budget -0.60/+0.45. The current tested TVA world presets use an active **`mountain_basin_temperature`**, height-matched seeded relief, and budget -0.66/+0.34. Unconditionally copying the old four `biome_temperature.json` files and switching the router would regress the newer relief-aware climate. Both variants can coexist **only with an explicit option**.

PR #25 also used **one polar crest per full equal-area annulus**, at approximately `sqrt(2) * pole_distance`, versus the current TVA's **two polar crests**, one at `pole_distance`. New opt-in `radial_equal_area_single_pole` restores the earlier geometry, while original `radial_equal_area` is unchanged. A separate pure-Java test verifies numeric parity and annular seams.

## Source-level restoration / preservation gates
`qa/test_tva_branch_unification.py` now checks Java module presence for:
- Grand Overworld caves, ancient/satellite Nether caves, enlarged End and Nether
- vanilla-safe oceans and deterministic multi-island sky geometry
- 60+ ecotones, Grand Ecology, rivers/watershed connectivity
- DEM/geology/USGS and highland mountain-basin climate
- 8 + 10 + 2 natural wonder prototypes, isolated opt-in source
- six-direction invasive biome and block terrain, isolated from sacred
- both old and newly recovered single-pole area latitude profiles

**This is a structural check, NOT evidence that an omitted old algorithm still behaves identically.** Existing dedicated CI runs must continue checking real saved Anvil chunks, cave traversal, river/ocean water columns, sky archipelago, dimension geometry and performance.

## PR #30 → main release blockade
1. Full four-family/two-seed distant chunk suite has had reproducible 180-second `forceload add 1024 1024` stalls. Do not classify it as passed without a successful run on the exact final SHA.
2. The USGS measured-Big-Sur world is still an optional source-backed experiment. The separate demand to introduce USGS/3DEP-style features to normal worlds without forcing the Big Sur map is not yet implemented end-to-end.
3. Review unresolved PR #25 semantics and **all** older divergent heads for runtime behavior vs new code; do not equate Git path inclusion with full functionality equivalence.
4. Main merge must preserve existing-world IDs, sea level 63, original oceans, cave networks and opt-in safety.

**Main remains unchanged until required gates pass.** Historical draft PRs should be retained rather than closed/deleted prematurely.


## 2026-10-17 feature-first reconciliation: USGS enhancement explicitly deferred

**Request:** keep working functions from other branches in TVA now, without force-merging older implementations; *defer* ordinary-world USGS feature integration.

- Compared the full Git **trees by path** of all **12 diverged branch heads** against the integrated candidate, rather than interpreting the GitHub compare endpoint's merge-base `added` status as missing on the actual target. The full-tree audit found **zero missing paths** in `feature/terrain-generation-optimization`, `feature/caves-ocean-sky-islands`, both Grand-dimensions phase branches, ecology v0.4/v0.5/v0.6/v0.7, with all associated source/resource/test/doc paths present in the candidate.
- The PR25 climate branch's seven missing historical paths consist of four obsolete-path `biome_temperature.json` sources, one old Java smoke test, one old Python test and one old document. The earlier single-pole equal-area algorithm and PR25 weather variants have been restored under **separate opt-in IDs**; the old same-path temperature router must not replace the improved default mountain/basin temperature graph.
- The remaining v1.2, v1.3 and archived pre-consolidation heads had missing **historical** test/workflow/doc files, but not missing actively needed production Java/resources in this tree inventory. This establishes source-path coverage, **not** runtime behavioral equivalence.
- The new `tva_wonders_invasion_three_realms` preselected **opt-in combined world** composes SkyOcean → GeoWonders → CrossRealm → NaturalEcotones; Nether and End also opt into CrossRealm. Vanilla-safe world IDs and all noise settings are inherited from `tva_sacred_timeline_three_realms`. `SkyOceanBiomeSource` and `GeoWondersBiomeSource` now propagate the cross-realm guard safely through nested wrappers, enabling both feature families while preserving independent guards.
- The opt-in combined world is available as the **fifth** named creation preset in `normal` and `extended` tags, with English and Chinese menu names. The original four menu entries remain unchanged and retain their positions. No experiment leaks into the default Sacred preset.
- Grounded performance enhancements `RealDemColumnCache`, `QuartBiomeResultCache`, `WorldgenProfiler` remain in the integrated source; the combined world preserves the same production biome source and generator settings, thus inherits these where relevant. This is **not** evidence the 180-second distant-forceload issue is fixed.
- Static integration test `qa/test_tva_woven_optional_features.py` and a two-profile **real NeoForge** world-boot matrix are now required. A successful boot/codec/Anvil check does **not** yet establish that rare natural wonders or invasions spawned in sampled terrain; separate in-world proof remains necessary.

**Promotion rule:** preserve experimental choice and every modern feature; never merge PR #30 to `main` until fresh CI on the exact final head passes including the existing strict 4×2 distant Anvil suite and the new combined world-boot gate.
