# RealScale World Studio — Worker-Based Terrain Sampling

## Intent
Keep the browser interface responsive while sampling terrain for the current deterministic preview. The user should be able to interact with the page while the terrain grid is computed, and the rendered map, PNG export, and CSV export must continue to use the same sample data.

## Goals
- Move the normal 384 × 384 terrain sampling path into a module Web Worker.
- Preserve the existing \`sampleTerrain(x, z, size, seed, reliefScale)\` algorithm and its deterministic output.
- Report progress to the main thread without flooding the message queue.
- Transfer the three \`Float32Array\` buffers to the main thread rather than cloning them.
- Keep a frame-batched main-thread fallback for browsers/environments without Worker support.
- Prevent stale worker messages from replacing a newer map.
- Surface worker errors in the existing status line and restore controls safely.
- Keep PNG and CSV exports operating on the same \`latestMap\` structure.

## Non-goals
- No deployment, hosting, domain, or Vercel work.
- No server-side Minecraft generation or playable save export.
- No changes to the six morphology kernels or Java parity assumptions.
- No new runtime dependencies.

## Architecture
- \`terrain-worker-core.js\` owns deterministic grid sampling and returns typed arrays plus min/max/land metadata. It accepts progress, cancellation, and yield callbacks for testability.
- \`terrain-worker.js\` is a thin module-worker message adapter. It handles \`generate\` and \`cancel\` messages, emits progress/completion/error messages, and transfers typed-array buffers.
- \`main.js\` creates one module worker when supported, sends a request ID with each generation, ignores messages for older IDs, and renders completed arrays using the existing Canvas path. If Worker is unavailable or initialization fails, the existing frame-batched sampler remains available.
- Rendering, inspection, and exports stay on the main thread because they require the Canvas and DOM.

## Contracts
- Generate request: \`{ type: "generate", requestId, options: { size, seed, seedText, reliefScale, resolution } }\`.
- Cancel request: \`{ type: "cancel", requestId }\`.
- Progress response: \`{ type: "progress", requestId, progress }\`.
- Complete response: \`{ type: "complete", requestId, map: { heights, temperatures, humidities, size, step, min, max, landPercent, seedText, reliefScale } }\`.
- Error response: \`{ type: "error", requestId, message }\`.
- Cancellation is cooperative between row batches. A cancelled request must not emit a completion message.
- Grid options must be validated before allocation: size is one of the UI footprints (4096, 8192, 16384, 32768, 65536); resolution is an integer from 16 through 1024; seed is an unsigned 32-bit integer; relief scale is finite and between 0.65 and 1.4.

## Acceptance
- Same inputs produce identical arrays and metadata.
- Progress is monotonic and ends at 100 on success.
- Cancellation stops before completion and does not leak stale results.
- Worker transfer uses the three array buffers.
- Worker errors and unsupported Worker environments leave the page usable.
- Existing terrain, browser runtime, PNG, and CSV tests continue to pass.
- The World Studio production bundle builds successfully.
