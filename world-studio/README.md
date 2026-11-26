# RealScale World Studio

A browser-first world creation interface for the RealScale project.

## Current MVP

- Deterministic seed input and random seed generation.
- Square world footprints from 4 km to 64 km.
- Natural Automate-inspired terrain preview.
- Browser-side relief and biome views.
- Elevation inspection, land coverage, and elevation range.
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

Run npm install, then npm run dev. Use npm run build to create the production bundle.

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
