import test from "node:test";
import assert from "node:assert/strict";
import {
  MAX_FORCED_CHUNKS,
  WORLD_PRESETS,
  expectedChunkCoordinates,
  normalizeExportRequest
} from "./export-contract.js";

test("defaults to the pinned Natural Automate server preset and a bounded area", () => {
  const request = normalizeExportRequest({ seed: "world-seed" });
  assert.equal(request.presetId, WORLD_PRESETS["natural-automate"]);
  assert.equal(request.minecraftVersion, "1.21.1");
  assert.equal(request.neoforgeVersion, "21.1.249");
  assert.equal(request.chunkCount, 64);
  assert.equal(request.bounds.minChunkX, -4);
  assert.equal(request.bounds.maxChunkX, 3);
  assert.equal(request.bounds.minChunkZ, -4);
  assert.equal(request.bounds.maxChunkZ, 3);
});

test("accepts only allowlisted RealScale world presets", () => {
  for (const [key, id] of Object.entries(WORLD_PRESETS)) {
    assert.equal(normalizeExportRequest({ seed: 42, preset: key }).presetId, id);
  }
  assert.throws(
    () => normalizeExportRequest({ seed: "safe", preset: "minecraft:normal" }),
    /Unsupported RealScale world preset/
  );
});

test("rejects unsafe, empty, oversized, and control-character seeds", () => {
  for (const seed of ["", "  ", "x".repeat(65), "hello\nworld", "a\u0000b", {}, null]) {
    assert.throws(() => normalizeExportRequest({ seed }), /Seed/);
  }
  assert.equal(normalizeExportRequest({ seed: 20261127 }).seed, "20261127");
});

test("rejects dimensions outside the per-axis and total forced-chunk budget", () => {
  assert.throws(() => normalizeExportRequest({ seed: "x", widthChunks: 0 }), /widthChunks/);
  assert.throws(() => normalizeExportRequest({ seed: "x", depthChunks: 17 }), /depthChunks/);
  assert.throws(() => normalizeExportRequest({ seed: "x", widthChunks: 16, depthChunks: 16, centerX: 29_999_984 }), /world border/);
  assert.equal(normalizeExportRequest({ seed: "x", widthChunks: 16, depthChunks: 16 }).chunkCount, MAX_FORCED_CHUNKS);
  assert.throws(() => normalizeExportRequest({ seed: "x", widthChunks: 16, depthChunks: 16, centerX: 29_999_984 }), /world border/);
});

test("centres odd and even rectangles around the selected block coordinate", () => {
  const odd = normalizeExportRequest({ seed: "x", widthChunks: 3, depthChunks: 3, centerX: 160, centerZ: -160 });
  assert.deepEqual(
    [odd.bounds.minChunkX, odd.bounds.maxChunkX, odd.bounds.minChunkZ, odd.bounds.maxChunkZ],
    [9, 11, -11, -9]
  );
  const even = normalizeExportRequest({ seed: "x", widthChunks: 2, depthChunks: 4 });
  assert.deepEqual(
    [even.bounds.minChunkX, even.bounds.maxChunkX, even.bounds.minChunkZ, even.bounds.maxChunkZ],
    [-1, 0, -2, 1]
  );
});

test("enumerates each requested chunk exactly once, including negative coordinates", () => {
  const request = normalizeExportRequest({ seed: "x", widthChunks: 3, depthChunks: 2, centerX: -16, centerZ: 32 });
  const chunks = expectedChunkCoordinates(request);
  assert.equal(chunks.length, 6);
  assert.deepEqual(chunks[0], { x: -2, z: 1 });
  assert.deepEqual(chunks.at(-1), { x: 0, z: 2 });
  assert.equal(new Set(chunks.map(({ x, z }) => String(x) + "," + String(z))).size, 6);
});

test("rejects malformed request shapes and unsafe world coordinates", () => {
  assert.throws(() => normalizeExportRequest(null), /JSON object/);
  assert.throws(() => normalizeExportRequest([]), /JSON object/);
  assert.throws(() => normalizeExportRequest({ seed: "x", centerZ: 30_000_000 }), /centerZ/);
});
