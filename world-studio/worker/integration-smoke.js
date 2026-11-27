import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { runMinecraftExport } from "./minecraft-runner.js";

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(moduleDirectory, "../..");
const exportRoot = path.join(repositoryRoot, "build", "world-studio-export-smoke");
const jobId = "realscale-export-smoke-2026";
let succeeded = false;

try {
  await fs.rm(exportRoot, { recursive: true, force: true });
  const result = await runMinecraftExport({
    seed: "realscale-world-export-smoke-2026",
    preset: "natural-automate",
    widthChunks: 2,
    depthChunks: 2,
    centerX: 0,
    centerZ: 0
  }, {
    jobId,
    repositoryRoot,
    exportRoot,
    startupTimeoutMs: Number(process.env.REALSCALE_SERVER_STARTUP_TIMEOUT_MS) || 900_000,
    generationTimeoutMs: Number(process.env.REALSCALE_GENERATION_TIMEOUT_MS) || 600_000,
    chunkTimeoutMs: Number(process.env.REALSCALE_CHUNK_TIMEOUT_MS) || 180_000,
    onProgress: update => {
      process.stdout.write("[world-export] " + update.percent + "% " + update.message + "\n");
    }
  });

  assert.equal(result.validation.valid, true);
  assert.equal(result.validation.requestedChunks, 4);
  assert.equal(result.validation.verifiedChunks, 4);
  assert.ok(result.archiveBytes > 0);
  assert.match(result.sha256, /^[a-f0-9]{64}$/u);
  assert.equal(result.manifest.vanillaCompatible, false);
  await fs.access(result.archivePath);

  process.stdout.write("REALSCALE_WORLD_EXPORT_INTEGRATION_PASS " + JSON.stringify({
    minecraftVersion: result.manifest.minecraftVersion,
    neoforgeVersion: result.manifest.neoforgeVersion,
    realscaleVersion: result.manifest.realscaleVersion,
    verifiedChunks: result.validation.verifiedChunks,
    archiveBytes: result.archiveBytes,
    sha256: result.sha256
  }) + "\n");
  succeeded = true;
} finally {
  if (succeeded) await fs.rm(exportRoot, { recursive: true, force: true });
  else process.stderr.write("World export smoke artifacts retained at " + exportRoot + "\n");
}
