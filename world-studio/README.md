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
