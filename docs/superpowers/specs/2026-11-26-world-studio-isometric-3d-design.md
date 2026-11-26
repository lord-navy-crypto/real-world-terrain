# RealScale World Studio — Isometric 3D Terrain Preview

## Intent
Add a real surface-relief view to the existing browser map viewer so users can inspect the generated preview as an oblique terrain model, not only as a top-down raster.

## Product choice
Use a dependency-free Canvas 2D isometric mesh renderer. The preview already has a 2D Canvas context and the browser build is intentionally lightweight; introducing a large 3D framework is not necessary for this first terrain-surface viewer. This is an isometric surface visualization, not a full WebGL scene or Minecraft renderer.

## Goals
- Add a third view mode: Relief, Biomes, and 3D.
- Build a deterministic, decimated 97 × 97 mesh from the existing 384 × 384 sample arrays.
- Draw terrain facets back-to-front with morphology/biome-derived color and slope shading.
- Allow drag rotation, vertical camera tilt, wheel zoom, and a reset-camera control.
- Reuse the existing sampled map; switching to 3D must not resample terrain.
- Export PNG in whichever view is currently selected.
- Allow share links to restore the 3D view.
- Keep the 2D point inspector available in Relief/Biomes and clearly explain its 2D-only behavior in 3D.

## Non-goals
- No WebGL/Three.js dependency.
- No new terrain sampling or authoritative Minecraft noise-router claims.
- No 3D buildings, caves, underground geometry, or playable save export.
- No deployment or hosting work.

## Rendering approach
- Select evenly spaced sample indices spanning the entire preview grid, including all four edges.
- Normalize height using the current map's estimated min/max.
- Rotate normalized X/Z coordinates by a camera yaw, then project to screen X/Y with pitch and zoom.
- Sort grid cells by approximate camera depth and draw two filled triangles per cell.
- Shade facet colors from existing relief classification and local slope.
- Render in requestAnimationFrame batches so interaction remains responsive.
- A monotonically increasing render token cancels obsolete camera frames when the user keeps dragging.

## Interaction
- Pointer drag changes yaw and pitch.
- Mouse wheel changes zoom within safe limits.
- Reset returns to the default camera.
- A 3D-specific legend explains drag/zoom controls.
- 2D point inspection is disabled in 3D because the screen projection is not a direct X/Z map coordinate.

## Acceptance
- Mesh geometry is finite, deterministic, covers the sample footprint, and responds to camera changes.
- 3D rendering draws visible facets from the existing sample arrays.
- Switching views does not call the terrain sampler again.
- Camera drag, zoom, reset, and share-link restoration work.
- Relief, biome, PNG, CSV, worker, and fallback behavior remain unchanged.
- Node tests and Vite production build pass.
