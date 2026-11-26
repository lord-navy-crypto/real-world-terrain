import test from "node:test";
import assert from "node:assert/strict";
import { hashSeed, sampleTerrain, TERRAIN_FAMILY_NAMES } from "./terrain-core.js";

test("the preview model exposes all six terrain morphology families", () => {
  assert.deepEqual(TERRAIN_FAMILY_NAMES, [
    "alpine", "glacial", "canyon", "volcanic", "karst", "dune"
  ]);
});

test("seed hashing is stable and returns an unsigned 32-bit integer", () => {
  const first = hashSeed("realscale-world-studio");
  assert.equal(first, hashSeed("realscale-world-studio"));
  assert.notEqual(first, hashSeed("different-seed"));
  assert.ok(Number.isInteger(first));
  assert.ok(first >= 0 && first <= 0xffffffff);
});

test("terrain sampling is deterministic for the same seed and inputs", () => {
  const first = sampleTerrain(1234, -5678, 8192, hashSeed("fixed-seed"), 1);
  const second = sampleTerrain(1234, -5678, 8192, hashSeed("fixed-seed"), 1);
  assert.deepEqual(first, second);
});

test("terrain samples remain finite and inside the declared preview bounds", () => {
  const seed = hashSeed("terrain-bounds");
  const points = [
    [-32000, -32000, 65536],
    [-8192, 4096, 16384],
    [0, 0, 8192],
    [2048, -4096, 8192],
    [32000, 32000, 65536]
  ];
  for (const [x, z, size] of points) {
    const sample = sampleTerrain(x, z, size, seed, 1);
    assert.ok(Number.isFinite(sample.elevation));
    assert.ok(Number.isFinite(sample.temperature));
    assert.ok(Number.isFinite(sample.humidity));
    assert.ok(sample.elevation >= -180 && sample.elevation <= 1750);
    assert.ok(sample.temperature >= -1 && sample.temperature <= 1);
    assert.ok(sample.humidity >= -1 && sample.humidity <= 1);
  }
});

test("different seeds produce different terrain fields", () => {
  const a = sampleTerrain(1234, -5678, 8192, hashSeed("seed-a"), 1);
  const b = sampleTerrain(1234, -5678, 8192, hashSeed("seed-b"), 1);
  assert.notDeepEqual(a, b);
});
