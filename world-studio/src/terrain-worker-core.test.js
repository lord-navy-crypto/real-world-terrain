import test from "node:test";
import assert from "node:assert/strict";
import { generateTerrainGrid } from "./terrain-worker-core.js";
import { hashSeed, sampleTerrain } from "./terrain-core.js";

const BASE_OPTIONS = {
  size: 4096,
  seed: hashSeed("worker-grid-fixture"),
  seedText: "worker-grid-fixture",
  reliefScale: 1,
  resolution: 16
};

const FAST_HOOKS = {
  rowsPerBatch: 4,
  yieldControl: async () => {}
};

test("grid sampler returns deterministic typed arrays and stable metadata", async () => {
  const first = await generateTerrainGrid(BASE_OPTIONS, FAST_HOOKS);
  const second = await generateTerrainGrid(BASE_OPTIONS, FAST_HOOKS);

  assert.ok(first.heights instanceof Float32Array);
  assert.ok(first.temperatures instanceof Float32Array);
  assert.ok(first.humidities instanceof Float32Array);
  assert.equal(first.heights.length, 256);
  assert.equal(first.temperatures.length, 256);
  assert.equal(first.humidities.length, 256);
  assert.deepEqual(first.heights, second.heights);
  assert.deepEqual(first.temperatures, second.temperatures);
  assert.deepEqual(first.humidities, second.humidities);
  assert.equal(first.size, 4096);
  assert.equal(first.step, 4096 / 15);
  assert.equal(first.seedText, "worker-grid-fixture");
  assert.equal(first.reliefScale, 1);
});

test("grid summary metadata is calculated from every sampled cell", async () => {
  const result = await generateTerrainGrid(BASE_OPTIONS, FAST_HOOKS);
  let expectedMin = Infinity;
  let expectedMax = -Infinity;
  let expectedLand = 0;

  for (let row = 0; row < 16; row += 1) {
    const z = (row / 15 - 0.5) * BASE_OPTIONS.size;
    for (let column = 0; column < 16; column += 1) {
      const x = (column / 15 - 0.5) * BASE_OPTIONS.size;
      const sample = sampleTerrain(x, z, BASE_OPTIONS.size, BASE_OPTIONS.seed, 1);
      expectedMin = Math.min(expectedMin, sample.elevation);
      expectedMax = Math.max(expectedMax, sample.elevation);
      if (sample.elevation >= 63) expectedLand += 1;
    }
  }

  assert.equal(result.min, expectedMin);
  assert.equal(result.max, expectedMax);
  assert.equal(result.landPercent, expectedLand / 256 * 100);
});

test("grid sampler reports monotonic progress and finishes at 100 percent", async () => {
  const progress = [];
  await generateTerrainGrid(BASE_OPTIONS, {
    ...FAST_HOOKS,
    onProgress: value => progress.push(value)
  });

  assert.deepEqual(progress, [25, 50, 75, 100]);
  assert.ok(progress.every((value, index) => index === 0 || value >= progress[index - 1]));
});

test("grid sampler includes both negative and positive footprint edges", async () => {
  const result = await generateTerrainGrid(BASE_OPTIONS, FAST_HOOKS);
  const first = sampleTerrain(-2048, -2048, 4096, BASE_OPTIONS.seed, 1);
  const last = sampleTerrain(2048, 2048, 4096, BASE_OPTIONS.seed, 1);

  assert.equal(result.heights[0], first.elevation);
  assert.equal(result.heights[255], last.elevation);
});

test("grid sampler rejects invalid footprint, seed, resolution, and relief settings before sampling", async () => {
  await assert.rejects(
    generateTerrainGrid({ ...BASE_OPTIONS, size: 4095 }, FAST_HOOKS),
    /Unsupported preview footprint/
  );
  await assert.rejects(
    generateTerrainGrid({ ...BASE_OPTIONS, seed: -1 }, FAST_HOOKS),
    /unsigned 32-bit integer/
  );
  await assert.rejects(
    generateTerrainGrid({ ...BASE_OPTIONS, resolution: 15 }, FAST_HOOKS),
    /resolution/
  );
  await assert.rejects(
    generateTerrainGrid({ ...BASE_OPTIONS, resolution: 1025 }, FAST_HOOKS),
    /resolution/
  );
  await assert.rejects(
    generateTerrainGrid({ ...BASE_OPTIONS, reliefScale: 1.41 }, FAST_HOOKS),
    /relief scale/
  );
});

test("grid sampler cooperatively stops between batches without returning partial terrain", async () => {
  let cancelled = false;
  const progress = [];
  const result = await generateTerrainGrid(BASE_OPTIONS, {
    rowsPerBatch: 4,
    onProgress: value => progress.push(value),
    yieldControl: async () => {
      cancelled = true;
    },
    isCancelled: () => cancelled
  });

  assert.deepEqual(result, { cancelled: true });
  assert.deepEqual(progress, [25]);
});
