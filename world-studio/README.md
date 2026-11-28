# RealScale World Studio 0.5.0

A browser-first world creation interface with a local, real Minecraft/NeoForge world-export worker.

## Current capabilities

- Deterministic browser preview seed and random seed generation.
- Preview footprints from 4 km to 64 km; this controls the visual preview only.
- Relief, biome, and interactive isometric 3D surface views.
- 384 × 384 browser terrain samples, elevation inspection, land coverage, and estimated elevation range.
- PNG image export and CSV export of estimated browser samples.
- Module Web Worker sampling with progress, cancellation, stale-result protection, and a main-thread fallback.
- Shareable preview URLs that restore seed, preview footprint, relief multiplier, and view.
- Local world-export UI connected to a loopback-only Node API.
- Real Minecraft 1.21.1 + NeoForge 21.1.249 + RealScale 2.0.0-preview.2-test.12 server-side generation.
- Bounded Anvil chunk generation, NBT/region validation, a second NeoForge boot that reopens the saved world, ZIP packaging, export manifest, and SHA-256 checksum.

## Browser preview is not Minecraft world generation

The browser preview is a visual model, not a byte-for-byte reproduction of Minecraft world generation. It ports RealScale's fitted morphology kernels and deterministic site geometry, while using browser-side preview noise for its base continental field, regional selector, temperature, and humidity.

Production world generation also depends on Minecraft's seeded density functions, registered noise fields, climate sampler, biome source, surface rules, feature placement, and the NeoForge runtime. Matching seeds do not guarantee matching browser-preview elevations and generated Minecraft blocks. Preview elevations are estimates, not authoritative block heights or biome IDs.

The server-side export does use the actual pinned Minecraft/NeoForge runtime and writes real Anvil chunks. It is a separate generation path, not a conversion of the browser's PNG or CSV.

## Real Minecraft export MVP

The local worker currently:

1. Validates the seed, allowlisted world preset, center, and chunk dimensions.
2. Temporarily configures the repository's NeoForge runServer environment with a unique world name and a private loopback RCON endpoint.
3. Starts the actual NeoForge dedicated server and selects the requested RealScale world preset through level-type.
4. Uses RCON to force-load and verify each requested chunk in the Overworld.
5. Removes the temporary force-load tickets, runs save-all flush, and stops the server.
6. Validates level.dat, the Minecraft DataVersion, Anvil region headers, chunk sector bounds, compressed chunk NBT, and the presence of every requested chunk.
7. Starts a second NeoForge server against the same world folder, confirms it loads the existing save rather than creating a new world, queries the saved seed, flushes, and stops cleanly.
8. Revalidates the persisted chunks, then packages the world folder and realscale-export.json into a ZIP and computes its SHA-256 checksum.

### Current export limits

- One active Minecraft server job at a time; up to three additional jobs may wait in the in-memory queue.
- Maximum 256 chunks per export, matching Minecraft's force-load ticket budget.
- UI presets: 4 × 4, 8 × 8, 12 × 12, or 16 × 16 chunks.
- The current export center is block coordinate X=0, Z=0.
- The selected browser preview footprint (4–64 km) is not the generated save size.
- The browser relief slider affects the preview only. The export selects an existing, allowlisted RealScale world preset.
- Jobs are held in memory and are not recovered after a worker restart.

### Compatibility boundary

The ZIP contains a normal Minecraft save folder, not an installer or executable. The generated world currently requires:

- Minecraft Java 1.21.1
- NeoForge 21.1.249
- RealScale 2.0.0-preview.2-test.12
- The matching client-side YACL dependency required by the RealScale mod metadata (Yet Another Config Lib 3.8.1+1.21.1)

**Vanilla compatibility is not implemented or claimed.** RealScale registers custom density-function codecs, biome sources, dimension types, and biome IDs. A vanilla-compatible compatibility-bake stage and clean vanilla reopen test remain future work.

## Local development

### Prerequisites

- Node.js 20 or newer
- Java 21
- Gradle 8.14.3 recommended (the CI worldgen workflow uses this version)
- Python 3.9 or newer for the NeoForge build's deterministic resource-generation tasks

Stop any existing Gradle runClient or runServer session using neoforge/run before starting an export. ModDevGradle launches the server with neoforge/run as its actual game directory, so the worker writes server.properties, eula.txt, the temporary world, and its lock there. It restores the original configuration files after the export. A lock prevents two export workers from sharing the same run directory.

Open two terminals:

**Terminal A — real Minecraft worker**

\`\`\`bash
cd world-studio
npm install
npm run worker
\`\`\`

The worker binds only to 127.0.0.1:4174. It is intentionally not a public HTTP service.

**Terminal B — browser interface**

\`\`\`bash
cd world-studio
npm run dev
\`\`\`

Open the local Vite address shown in the terminal. Vite proxies /api requests to the loopback worker.

### Checks

\`\`\`bash
npm test
npm run build
npm run worker:check
\`\`\`

### Worker configuration

- REALSCALE_NEOFORGE_DIR: override the default ../neoforge project path.
- REALSCALE_GRADLE_BIN: override the gradle executable.
- REALSCALE_EXPORT_ROOT: change where job ZIPs and server logs are written (default: world-studio/exports).
- REALSCALE_WORKER_PORT: change the local worker port (default: 4174).
- REALSCALE_SERVER_STARTUP_TIMEOUT_MS: server boot timeout.
- REALSCALE_GENERATION_TIMEOUT_MS: overall chunk-generation timeout.
- REALSCALE_CHUNK_TIMEOUT_MS: per-chunk load timeout.

The worker rejects non-loopback bind addresses. Do not expose it directly to the public internet. Public deployment needs authentication, durable job state, resource quotas, isolated runtime storage, and a separate long-running worker service.

## Security and data boundaries

- User-provided JARs, scripts, datapacks, and arbitrary server plugins are never executed.
- World presets are allowlisted; the browser cannot submit arbitrary level-type values or server commands.
- Seed text is bounded and rejects control characters before it reaches server.properties.
- The Minecraft server and RCON listener bind to loopback only; RCON uses a random per-job password.
- The worker serializes jobs because the Gradle run directory is shared.
- Save validation rejects symlinks, malformed NBT, invalid Anvil sector offsets, unsupported chunk compression, and missing requested chunks.
- ZIP packaging rejects symlinks, unsafe paths, oversized files, and ZIP32 overflow.
- Only the world folder and export manifest are packaged. Server logs remain outside the downloadable save.

## Next engineering gates

1. Add a server-side chunk-baking scheduler for areas larger than the 256-chunk MVP limit.
2. Persist job state and recover interrupted jobs safely.
3. Expand Anvil/NBT coverage across multiple region files, negative chunk coordinates, and larger export footprints.
4. Investigate a compatibility-bake path for vanilla clients; do not promise it until the exact exported ZIP opens in an unmodified vanilla runtime.
5. Compare fixed-seed server-generated height/biome samples against the browser preview and document measured parity.
6. Build a separately authenticated public worker and storage service. Deployment is intentionally out of scope for this code phase.
