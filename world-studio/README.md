# RealScale World Studio

A browser-first world creation interface for the RealScale project.

## Current MVP

- Deterministic seed input and random seed generation.
- Square world footprints from 4 km to 64 km.
- Natural Automate-inspired terrain preview.
- Browser-side relief and biome views.
- Elevation inspection, land coverage, and elevation range.
- Downloadable PNG map previews (image export only; not a playable Minecraft save).
- Downloadable CSV elevation grids with X/Z sample coordinates, estimated height, temperature/humidity indices, seed, and footprint metadata.
- Square map rendering so the displayed geometry preserves the selected square footprint.
- Frame-batched terrain sampling and shading, with live progress updates to keep the browser responsive.
- No executable downloads, launcher integration, or local filesystem access.

## Important generation boundary

The current browser preview is a **visual terrain preview**, not a byte-for-byte reproduction of Minecraft world generation. It ports the six fitted morphology kernels and Natural Automate suitability/cross-fade logic from the RealScale TerrainArchetypeMath implementation, while using deterministic browser noise fields as preview inputs.

The production RealScale worldgen also depends on Minecraft's seeded density functions, vanilla noise registries, climate sampling, biome source, surface rules, feature placement, and the current NeoForge runtime. The preview must not be presented as an exact world-save or as a validated playable Minecraft world.

## Planned architecture

1. Web preview — seed, map dimensions, terrain controls, and interactive map inspection.
2. Server-side generation worker — run a pinned Minecraft + RealScale server build in an isolated job/container; create a fresh world from the requested seed and approved settings.
3. Validation — confirm level.dat, region files, expected world preset, generation logs, and a successful clean reopen before packaging.
4. World export — produce a ZIP containing a normal Minecraft save folder. No executable or installer is required.
5. Community gallery — publish user-submitted worlds with metadata, checksums, moderation, size limits, and malware scanning.

The Vercel site should remain a control plane and preview renderer. Long-running Minecraft world generation and large file packaging should run on a separate worker/storage service, not inside a short-lived serverless request.

## Development

Run `npm install`, then `npm run dev`. Run `npm test` for deterministic terrain-core and browser-runtime smoke checks, and `npm run build` to create the production bundle.

## Security principles

- The browser preview is client-side and does not upload the seed or local files.
- Never accept or execute user-supplied JARs, scripts, datapacks, or arbitrary server plugins in the generation worker.
- Only generate worlds using a pinned, signed-off RealScale server image and allowlisted options.
- Apply quotas, timeouts, disk limits, checksums, and cleanup to generation jobs.
- Treat community uploads as untrusted files; validate archive paths and scan before publication.


## Full-world generation budget

The size selector currently controls the browser preview footprint, not a promise that every chunk in that square will be pre-generated. At the project's approximate 1 block = 1 metre horizontal scale, fully pre-generating a square area would require:

| Side length | Chunk width | Total chunks |
|---|---:|---:|
| 4 km | 256 | 65,536 |
| 8 km | 512 | 262,144 |
| 16 km | 1,024 | 1,048,576 |
| 32 km | 2,048 | 4,194,304 |
| 64 km | 4,096 | 16,777,216 |

The production export service must enforce an explicit chunk budget, disk quota, generation timeout, and archive-size limit. Larger maps should be treated as preview extents or split into controlled generation jobs; the browser must never imply that a 64 km save has already been generated just because its preview rendered.


## Vanilla-compatible export is a validation requirement

The goal is to let players open an exported save without installing the RealScale client mod. This is not guaranteed merely by generating chunks on a modded server: the current worldgen includes custom density-function codecs and custom biome/registry identifiers.

The export worker therefore needs a separate compatibility-bake stage:
- Generate the selected area with a pinned RealScale server runtime.
- Pre-generate only the approved chunk budget and set a matching world border.
- Convert or remap any mod-specific biome/registry references that vanilla cannot resolve; preserve the already-generated block terrain.
- Replace mod-specific generator metadata with a vanilla-loadable configuration where required.
- Reopen the exported archive with an unmodified vanilla server/client and verify there are no missing registry, codec, or data-pack errors before marking it downloadable.

Do not advertise vanilla compatibility until this clean vanilla reopen test passes for the exact Minecraft version being exported.


## Preview parity boundary

The browser preview now mirrors the six fitted morphology coefficient sets and the deterministic volcanic/karst site geometry used by the Java terrain kernels. The site-center hash uses the same 64-bit world-absolute mixing and cell-offset rules as `MountainReliefMath`; the Java block-coordinate truncation behavior is also mirrored for these kernels.

The browser preview is still not an exact Minecraft world-generation replica. Its base continental field, original height signal, regional selector, temperature, and humidity are generated from browser-side deterministic FBM noise. The production server instead samples Minecraft's registered density/noise fields, including the Natural Automate smoothed height signal, continental field, erosion/offset selectors, and shifted temperature/vegetation noise. The production noise router also applies its own vertical density gradient, surface rules, biome source, and feature placement. Therefore preview elevations are explicitly estimates, not authoritative block heights, and matching seeds do not yet guarantee matching terrain.

The next parity gate is a fixed-seed server comparison: sample identical X/Z coordinates from the actual NeoForge worldgen runtime, export the resulting surface heights and biome IDs as a reference fixture, and compare the browser preview against that fixture. Do not label the preview exact until those measured differences are within a documented tolerance.


## Data export boundary
The CSV export contains the complete 384 × 384 browser preview sample grid. Elevation and climate columns are estimated browser-model values, not authoritative Minecraft block heights or biome IDs. The file includes X/Z coordinates in blocks, the seed, and preview footprint so the samples can be inspected in a spreadsheet or GIS workflow without implying a playable-world export.

Relief/biome view switching reuses the already sampled height and climate arrays and only re-renders the map colors. It does not run the terrain sampler again.
