# RealScale World Studio Worker Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Move deterministic preview sampling into a module Web Worker while preserving the existing map, inspection, and export behavior.

**Architecture:** A pure async grid sampler owns terrain computation; a thin worker adapter handles progress, cancellation, and transferable results. The main thread uses the worker by default and retains the current frame-batched sampler as a fallback.

**Tech Stack:** Existing Vite 6, JavaScript ES modules, Node.js built-in test runner, browser module Workers.

**Spec:** \`docs/superpowers/specs/2026-11-26-world-studio-worker-design.md\`

## Global Constraints
- Do not change the production RealScale Java terrain kernels or their fitted coefficients.
- Keep preview heights and climate fields explicitly estimated, not authoritative Minecraft world data.
- Do not add runtime dependencies.
- Do not perform deployment, hosting, domain, or Vercel work.
- Preserve PNG and CSV export semantics.

## Review Focus
- Invalid grid dimensions or non-finite parameters must be rejected before typed-array allocation.
- A cancelled request must not later publish a completion result.
- A late message from an older request must not replace the currently selected map.
- Worker construction/runtime failure must leave a working main-thread fallback.
- Transferred arrays must retain their exact lengths and values after reaching the main thread.

---

### Task 1: Deterministic grid sampler

**Files:**
- Create: \`world-studio/src/terrain-worker-core.js\`
- Create: \`world-studio/src/terrain-worker-core.test.js\`

**Interfaces:**
- Produces \`generateTerrainGrid(options, hooks)\` returning a Promise of a map result or a cancelled result.
- Consumes \`sampleTerrain\` and \`hashSeed\` from \`terrain-core.js\`.

- [ ] Write tests for deterministic output, summary metadata, monotonic progress, invalid inputs, and cooperative cancellation.
- [ ] Run \`npm test\` and confirm the new tests fail because the sampler module does not exist.
- [ ] Implement validation and batched sampling with a yield between batches.
- [ ] Run \`npm test\`; all terrain and grid-sampler tests must pass.

### Task 2: Worker message protocol

**Files:**
- Create: \`world-studio/src/terrain-worker.js\`
- Create: \`world-studio/src/terrain-worker.test.js\`

**Interfaces:**
- Consumes \`{ type: "generate" | "cancel", requestId, options }\`.
- Produces \`progress\`, \`complete\`, and \`error\` messages; completion transfers height, temperature, and humidity buffers.

- [ ] Test completion payloads, progress ordering, cancellation, malformed messages, and transfer-list contents.
- [ ] Run the targeted test and confirm it fails before the worker adapter exists.
- [ ] Implement the worker adapter with request-scoped cancellation.
- [ ] Run the targeted and complete test suites.

### Task 3: Main-thread integration and fallback

**Files:**
- Modify: \`world-studio/src/main.js\`
- Modify: \`world-studio/src/main.runtime.test.js\`

**Interfaces:**
- Main thread sends one generate request per accepted generation and ignores messages with a stale request ID.
- Main thread maps worker progress to the current status line.
- Existing Canvas rendering, map inspector, PNG export, and CSV export consume the worker result without format changes.
- If Worker is unavailable or fails, use the existing frame-batched sampler.

- [ ] Add runtime tests for worker success, stale responses, worker errors, and no-Worker fallback.
- [ ] Run the runtime tests and confirm they fail before integration.
- [ ] Integrate worker lifecycle, request IDs, safe control restoration, and fallback.
- [ ] Run the complete test suite and production build.

### Task 4: Documentation and CI contract

**Files:**
- Modify: \`world-studio/README.md\`
- Modify: \`.github/workflows/world-studio.yml\`

- [ ] Document worker execution, fallback behavior, and preview-data limits.
- [ ] Ensure CI runs all Node tests (including worker-core, worker-protocol, and browser runtime tests) before Vite build.
- [ ] Run the full CI workflow and inspect its logs for test counts, failures, and build output.

## Completion Contract
- All new tests have been observed failing before implementation and passing afterward.
- Full Node test suite and Vite production build pass.
- Worker and fallback paths are both covered.
- No claim of actual Minecraft save generation or deployment is made.
