import { sampleTerrain } from "./terrain-core.js";

const ALLOWED_FOOTPRINTS = new Set([4096, 8192, 16384, 32768, 65536]);
const MIN_RESOLUTION = 16;
const MAX_RESOLUTION = 1024;
const MIN_RELIEF_SCALE = 0.65;
const MAX_RELIEF_SCALE = 1.4;
const DEFAULT_ROWS_PER_BATCH = 8;

function validateOptions(options) {
  if (!options || typeof options !== "object") {
    throw new TypeError("Terrain generation options are required");
  }

  const { size, seed, seedText, reliefScale, resolution = 384 } = options;

  if (!ALLOWED_FOOTPRINTS.has(size)) {
    throw new RangeError("Unsupported preview footprint");
  }
  if (!Number.isInteger(seed) || seed < 0 || seed > 0xffffffff) {
    throw new RangeError("Seed must be an unsigned 32-bit integer");
  }
  if (typeof seedText !== "string" || !seedText.trim() || seedText.length > 48) {
    throw new RangeError("Seed text must contain 1 to 48 characters");
  }
  if (!Number.isFinite(reliefScale)
    || reliefScale < MIN_RELIEF_SCALE
    || reliefScale > MAX_RELIEF_SCALE) {
    throw new RangeError("Relief scale must be between 0.65 and 1.4");
  }
  if (!Number.isInteger(resolution)
    || resolution < MIN_RESOLUTION
    || resolution > MAX_RESOLUTION) {
    throw new RangeError("Terrain resolution must be an integer from 16 to 1024");
  }

  return { size, seed, seedText, reliefScale, resolution };
}

export async function generateTerrainGrid(options, hooks = {}) {
  const validated = validateOptions(options);
  const { size, seed, seedText, reliefScale, resolution } = validated;
  const rowsPerBatch = hooks.rowsPerBatch ?? DEFAULT_ROWS_PER_BATCH;
  const onProgress = hooks.onProgress ?? (() => {});
  const isCancelled = hooks.isCancelled ?? (() => false);
  const yieldControl = hooks.yieldControl
    ?? (() => new Promise(resolve => setTimeout(resolve, 0)));

  if (!Number.isInteger(rowsPerBatch) || rowsPerBatch < 1 || rowsPerBatch > resolution) {
    throw new RangeError("Rows per batch must be between 1 and the terrain resolution");
  }
  if (typeof onProgress !== "function"
    || typeof isCancelled !== "function"
    || typeof yieldControl !== "function") {
    throw new TypeError("Terrain generation hooks must be functions");
  }

  const sampleCount = resolution * resolution;
  const heights = new Float32Array(sampleCount);
  const temperatures = new Float32Array(sampleCount);
  const humidities = new Float32Array(sampleCount);
  const step = size / (resolution - 1);
  let min = Infinity;
  let max = -Infinity;
  let landCount = 0;

  for (let startRow = 0; startRow < resolution; startRow += rowsPerBatch) {
    if (isCancelled()) return { cancelled: true };

    const endRow = Math.min(resolution, startRow + rowsPerBatch);
    for (let row = startRow; row < endRow; row += 1) {
      const z = (row / (resolution - 1) - 0.5) * size;
      for (let column = 0; column < resolution; column += 1) {
        const x = (column / (resolution - 1) - 0.5) * size;
        const index = row * resolution + column;
        const sample = sampleTerrain(x, z, size, seed, reliefScale);

        heights[index] = sample.elevation;
        temperatures[index] = sample.temperature;
        humidities[index] = sample.humidity;
        min = Math.min(min, sample.elevation);
        max = Math.max(max, sample.elevation);
        if (sample.elevation >= 63) landCount += 1;
      }
    }

    onProgress(Math.round(endRow / resolution * 100));
    if (endRow < resolution) await yieldControl();
  }

  if (isCancelled()) return { cancelled: true };

  return {
    heights,
    temperatures,
    humidities,
    size,
    step,
    min,
    max,
    landPercent: landCount / sampleCount * 100,
    seedText,
    reliefScale,
    resolution
  };
}
