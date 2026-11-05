# Experimental v0.5: evidence-gated DH LOD persistence

**No new mod JAR required.** Keep using Mac-confirmed v0.1 or experimental
v0.3. v0.5 only tightens real NeoForge CI acceptance, without touching the
Minecraft world, RealScale height, Chunky scheduler, DH renderer, or shader code.

## Why a second verification gate?
A successful Minecraft /forceload verifies a **real Minecraft chunk**, not a
Distant Horizons far-distance LOD. In the previous v0.4 dedicated-server runs,
both RealScale Measured (seed 2) and Sacred Timeline (seed 42) produced three
healthy DistantHorizons.sqlite files, but only one likely LOD-data table had
records. That is normal when the Overworld was visited and Nether/End databases
contain only setup metadata.

The improved runtime verdict now requires BOTH:
1. Former startup checks: full distant Minecraft chunk, active DH worldgen
   queue, and native DH–Chunky listener bound after Chunky became ready.
2. Independent after-exit SQLite scan: all discovered database files scanned
   successfully, healthy SQLite quick-check and no incomplete WAL snapshots,
   with **at least one likely LOD-data table containing a record**.

The second gate is used **only for patched worldgen trials**. Unpatched
baseline arms intentionally reproduce the original Chunky/DH startup crash,
so their failure to persist LOD data must not produce a misleading verdict.

The SQLite check is intentionally conservative: row presence is only
**candidate storage evidence**, not geometry decoding and absolutely not
visual/GPU shader proof. The result emits
\`lod_storage_level=candidate_table_presence_only\` and
\`gpu_lod_visible=unverified\`; it cannot claim DH/Iris/Sodium success.

## Safety and privacy
Audit operates only after the dedicated test server exits, uses read-only
immutable SQLite, ignores WAL contents while flagging snapshot uncertainty,
reports booleans and counts without raw table contents, personal paths,
world names or coordinates. It never alters any original saved world.

## How to read regression results
GitHub Actions workflow:
\`.github/workflows/dh-chunky-real-neoforge-test.yml\`.

* \`patch_runtime_pass\`: native DH/Chunky listener and actual distant chunk
  plus independent candidate LOD records verified.
* \`patch_or_lod_storage_not_proven\`: at least one of those gates failed.
  Inspect anonymized \`dh-chunky-lod-storage.json\` and server log signature.
* \`reproduced_known_DH_Chunky_crash\`: unpatched comparator hit original
  upstream initialization bug. This is not counted as fully loaded.

Mac client validation still needed for 2096-height mountains, Sodium,
Iris and the actual shaderpack, since Linux headless server CI cannot
observe the GPU renderer.
