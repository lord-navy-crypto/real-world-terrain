# RealScale World Studio — Isometric 3D Terrain Preview

## Intent
Add an oblique surface-relief view to the browser map viewer so users can inspect the generated preview as an isometric terrain model, not only as a top-down raster.

## Product choice
Use a dependency-free Canvas 2D isometric mesh renderer. The existing browser preview already uses Canvas 2D; this first 3D surface view does not need a large WebGL framework. It is an isometric surface visualization, not a full WebGL scene or Minecraft renderer.

## Goals
- Add Relief, Biomes, and 3D map modes.
- Build a deterministic, decimated 97 × 97 mesh from the existing 384 × 384 sample arrays.
- Draw depth-sorted terrain facets with relief colors and slope shading.
- Support drag rotation, pitch adjustment, wheel zoom, and camera reset.
- Reuse the current sample grid; changing view must not resample terrain.
- Export PNG from the currently selected view.
- Allow share links to restore the 3D view.
- Keep the 2D coordinate inspector in Relief/Biomes; avoid false X/Z claims in projected 3D mode.

## Non-goals
- No WebGL/Three.js dependency.
- No new terrain sampling or authoritative Minecraft noise-router claims.
- No 3D buildings, caves, underground geometry, or playable save export.
- No deployment or hosting work.

## Rendering
- Select evenly spaced sample indices across the full preview grid, including edges.
- Normalize height using the current map's estimated min/max.
- Rotate normalized X/Z coordinates by camera yaw and project to screen X/Y using pitch and zoom.
- Sort cells by approximate camera depth and draw two triangles per cell.
- Shade facets using the existing relief classification and local slope.
- Render in requestAnimationFrame batches; invalidate stale render batches with the existing render token.

## Acceptance
- Mesh projection is deterministic, finite, and covers the sample footprint.
- Camera parameters alter projected coordinates.
- 3D view draws facets without rerunning terrain sampling.
- Drag, wheel zoom, reset, share-link restoration, and PNG export work.
- 2D views, inspection, worker, fallback, and CSV behavior remain intact.
- Node tests and Vite production build pass.
