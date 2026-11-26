# RealScale World Studio Isometric 3D Preview Plan

**Goal:** Add an interactive, dependency-free isometric terrain surface view using the existing browser preview samples.

**Architecture:** A pure mesh-projection module converts the existing height/climate arrays into decimated projected vertices and depth-sorted cells. The existing Canvas 2D renderer draws the facets in animation-frame batches. Camera state is local to the viewer and never changes terrain samples.

**Tech Stack:** Existing Vite 6, JavaScript ES modules, Canvas 2D, Node built-in tests.

**Spec:** \`docs/superpowers/specs/2026-11-26-world-studio-isometric-3d-design.md\`

## Constraints
- Do not add dependencies.
- Do not alter the Java terrain kernels or the worker sampler.
- Keep the 384 × 384 source sample grid; use 97 × 97 only for 3D drawing.
- Do not describe this as a Minecraft runtime render or exact world preview.
- Preserve PNG/CSV semantics and share-link settings.

### Task 1: Pure mesh projection

**Files:**
- Create: \`world-studio/src/terrain-3d.js\`
- Create: \`world-studio/src/terrain-3d.test.js\`

- [ ] Test mesh dimensions, finite projected coordinates, edge coverage, deterministic output, and camera sensitivity.
- [ ] Observe the tests fail before the mesh module exists.
- [ ] Implement mesh projection and depth sorting.
- [ ] Run all mesh tests.

### Task 2: Canvas 3D renderer

**Files:**
- Modify: \`world-studio/src/main.js\`
- Modify: \`world-studio/index.html\`
- Modify: \`world-studio/src/style.css\`
- Modify: \`world-studio/src/main.runtime.test.js\`

- [ ] Add the 3D view button, 3D legend, and reset-camera control.
- [ ] Add batched triangle drawing from the existing latestMap arrays.
- [ ] Add runtime tests proving 3D draws facets without resampling terrain.
- [ ] Run runtime and terrain suites.

### Task 3: Camera and share-link integration

**Files:**
- Modify: \`world-studio/src/main.js\`
- Modify: \`world-studio/src/world-settings.js\`
- Modify: \`world-studio/src/world-settings.test.js\`
- Modify: \`world-studio/src/main.runtime.test.js\`

- [ ] Add pointer-drag yaw/pitch, wheel zoom, and camera reset.
- [ ] Disable direct X/Z click inspection while in the projected 3D view.
- [ ] Add \`3d\` to valid share-link view modes and test round-trip restoration.
- [ ] Verify PNG export captures the selected 3D canvas view.

### Task 4: Documentation and verification

**Files:**
- Modify: \`world-studio/README.md\`

- [ ] Document the isometric surface view and its limits.
- [ ] Run the complete Node test suite and Vite production build.
- [ ] Inspect final PR state and update this plan with verification evidence.
