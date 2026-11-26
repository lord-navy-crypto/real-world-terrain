# RealScale World Studio Isometric 3D Preview Plan

**Goal:** Add an interactive, dependency-free isometric terrain surface view using the existing browser preview samples.

**Architecture:** A pure mesh-projection module converts existing height/climate arrays into decimated projected vertices and depth-sorted cells. The Canvas 2D renderer draws facets in animation-frame batches. Camera state never changes terrain samples.

**Tech Stack:** Existing Vite 6, JavaScript ES modules, Canvas 2D, Node built-in test runner.

**Spec:** \`docs/superpowers/specs/2026-11-26-world-studio-isometric-3d-design.md\`

## Completed implementation

- [x] Added \`terrain-3d.js\` with deterministic 97 × 97 mesh projection, edge coverage, local slopes, camera projection, and depth sorting.
- [x] Added six geometry tests for mesh dimensions, edge coverage, finite values, painter ordering, camera sensitivity, and invalid arrays.
- [x] Added the 3D view button, 3D legend, and reset-camera control.
- [x] Added batched Canvas triangle rendering from the existing map arrays.
- [x] Added pointer-drag yaw/pitch, wheel zoom, reset, and touch-drag support.
- [x] Kept the point inspector in 2D modes and explicitly disabled misleading coordinate inspection in 3D.
- [x] Added \`3d\` to validated share-link view modes.
- [x] Preserved PNG export of the selected view and CSV export of source samples.
- [x] Documented the isometric view and its limitations in the World Studio README.

## Verification evidence
- GitHub Actions World Studio run \`45853990962\` completed successfully.
- \`npm test\`: **48 passed, 0 failed**.
- \`vite build\`: passed; Vite emitted the separate 8.60 kB module-worker bundle, 20.64 kB main JS, and 17.76 kB CSS (uncompressed sizes).
- The first runtime pass exposed a mismatch between source sample indices and decimated render-grid indices. A dedicated \`gridIndex\` now addresses facet colors; the full suite passes.
- A render-token handoff handles view changes while a 3D render is in progress.
- Deployment and hosting remain out of scope.
