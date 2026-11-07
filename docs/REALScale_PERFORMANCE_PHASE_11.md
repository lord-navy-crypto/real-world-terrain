# Performance Phase 11 — Exact fitted-kernel hot paths

This phase addresses two repeated costs in the six production Fitted terrain
morphology kernels.

1. **Alpine lowlands:** The alpine relief gate is identically zero for
   `original <= 0.65`, independent of continentalness or selector signal.
   The exact preexisting early return now occurs *before* evaluating the
   continental field. This skips a real source sampling call for those columns
   without changing the returned original density, including signed zeros.
2. **Fixed DEM-fit scales:** Glacier valley width, volcano radius and both
   karst radii depend only on the already validated measured wavelength
   coefficient. Store these four exact `double` results when constructing
   the morphology kernel instead of clamping/multiplying them during every
   terrain sample. The original mathematical expressions are unchanged.

The glacial and volcanic no-selector behavior also uses an immutable
constructor-derived flag rather than repeated per-block family comparisons.

Verification:
- JUnit compares all four cached constants against the frozen original
  coefficient formula using *raw IEEE-754 bits* over ten wavelength cases.
- Python contract tests assert early-out ordering before continental noise,
  preservation of all original morphology formulas, and selector-free families.
- Complete NeoForge CI, genuine fitted worldgen boots and the existing
  DEM heightmap / cave quality regressions remain mandatory.
- This does not change terrain raster samples, structure placement, cave
  formulas or worldgen registry IDs. No new LOD culling is activated.

The existing Dune-mode persisted-heightmap comparison and measured-DEM
16/4-grid seam regressions are separate quality blockers; no quality gate is
suppressed by this optimization.
