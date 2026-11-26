import './style.css';
import { clamp, hashSeed, sampleTerrain } from './terrain-core.js';

const canvas = document.querySelector("#terrain-canvas");
const ctx = canvas.getContext("2d", { alpha: false });
const seedInput = document.querySelector("#seed-input");
const sizeSelect = document.querySelector("#size-select");
const reliefSlider = document.querySelector("#relief-slider");
const reliefValue = document.querySelector("#relief-value");
const generateButton = document.querySelector("#generate-button");
const refreshButton = document.querySelector("#refresh-map");
const downloadPreviewButton = document.querySelector("#download-preview");
const downloadCsvButton = document.querySelector("#download-csv");
const cancelButton = document.querySelector("#cancel-generation");
const computeMode = document.querySelector("#compute-mode");
const randomSeedButton = document.querySelector("#random-seed");
const statusText = document.querySelector("#generation-status");
const loading = document.querySelector("#map-loading");
const mapTitle = document.querySelector("#map-title");
const mapSeedLabel = document.querySelector("#map-seed-label");
const mapScale = document.querySelector("#map-scale");
const mapCoordinates = document.querySelector("#map-coordinates");
const inspectPosition = document.querySelector("#inspect-position");
const inspectElevation = document.querySelector("#inspect-elevation");
const statDimensions = document.querySelector("#stat-dimensions");
const statLand = document.querySelector("#stat-land");
const statElevation = document.querySelector("#stat-elevation");
const reliefLegend = document.querySelector("#relief-legend");
const biomeLegend = document.querySelector("#biome-legend");

const RESOLUTION = 384;
const ROWS_PER_FRAME = 8;
const SEA_LEVEL = 63;

let currentView = "relief";
let latestMap = null;
let isGenerating = false;
let isExportingCsv = false;
let renderToken = 0;
let generationSequence = 0;
let activeGenerationId = null;
let activeGenerationStartedAt = 0;
let activeGenerationOptions = null;
let terrainWorker = null;
function colorForTerrain(height, temperature, humidity, slope, view) {
  if (height < SEA_LEVEL - 180) {
    const depth = clamp((SEA_LEVEL - height) / 520, 0, 1);
    return [14 + depth * 8, 47 + depth * 22, 67 + depth * 25];
  }
  if (height < SEA_LEVEL + 18) return [181, 169, 125];
  if (view === "biomes") {
    if (height > 850 || temperature < -0.38) return [206, 216, 211];
    if (temperature > 0.16 && humidity < -0.18) return [190, 151, 91];
    if (humidity > 0.24) return [51, 105, 75];
    if (humidity < -0.12) return [133, 151, 91];
    return [111, 145, 91];
  }
  if (height > 1050 || temperature < -0.5) return [199, 207, 202];
  if (height > 760 || slope > 0.72) return [125, 130, 119];
  if (temperature > 0.16 && humidity < -0.18) return [169, 139, 91];
  if (humidity > 0.24) return [56, 105, 72];
  if (height < 180) return [119, 145, 92];
  return [103, 132, 83];
}

function renderMap(map, onComplete) {
  const token = ++renderToken;
  let renderedView = currentView;
  let renderRow = 0;
  let image = ctx.createImageData(RESOLUTION, RESOLUTION);

  function renderBatch() {
    if (token !== renderToken) return;
    if (renderedView !== currentView) {
      renderedView = currentView;
      renderRow = 0;
      image = ctx.createImageData(RESOLUTION, RESOLUTION);
    }

    const pixels = image.data;
    const endRow = Math.min(RESOLUTION, renderRow + ROWS_PER_FRAME);
    for (let py = renderRow; py < endRow; py += 1) {
      for (let px = 0; px < RESOLUTION; px += 1) {
        const index = py * RESOLUTION + px;
        const left = map.heights[py * RESOLUTION + Math.max(0, px - 1)];
        const right = map.heights[py * RESOLUTION + Math.min(RESOLUTION - 1, px + 1)];
        const up = map.heights[Math.max(0, py - 1) * RESOLUTION + px];
        const down = map.heights[Math.min(RESOLUTION - 1, py + 1) * RESOLUTION + px];
        const dx = (right - left) / Math.max(map.step * 2, 1);
        const dz = (down - up) / Math.max(map.step * 2, 1);
        const slope = Math.hypot(dx, dz);
        const color = colorForTerrain(
          map.heights[index],
          map.temperatures[index],
          map.humidities[index],
          slope,
          renderedView
        );
        const contourValue = (map.heights[index] - SEA_LEVEL) / 120;
        const isContour = map.heights[index] > SEA_LEVEL + 30
          && Math.abs(contourValue - Math.round(contourValue)) < 0.026;
        const contourShade = isContour ? 0.74 : 1;
        const shade = clamp(0.86 - dx * 0.8 - dz * 0.55, 0.62, 1.16);
        const pixel = index * 4;
        pixels[pixel] = clamp(color[0] * shade * contourShade, 0, 255);
        pixels[pixel + 1] = clamp(color[1] * shade * contourShade, 0, 255);
        pixels[pixel + 2] = clamp(color[2] * shade * contourShade, 0, 255);
        pixels[pixel + 3] = 255;
      }
    }

    renderRow = endRow;
    const progress = Math.round(renderRow / RESOLUTION * 100);
    statusText.textContent = "Rendering " + renderedView + " map… " + progress + "%";
    if (renderRow < RESOLUTION) {
      window.requestAnimationFrame(renderBatch);
      return;
    }

    ctx.putImageData(image, 0, 0);
    if (onComplete) onComplete();
  }

  window.requestAnimationFrame(renderBatch);
}

function syncGenerationControls() {
  const busy = isGenerating || isExportingCsv;
  generateButton.disabled = busy;
  refreshButton.disabled = busy;
  cancelButton.disabled = !isGenerating;
  downloadPreviewButton.disabled = busy || !latestMap;
  downloadCsvButton.disabled = busy || !latestMap;
  seedInput.disabled = busy;
  randomSeedButton.disabled = busy;
  sizeSelect.disabled = busy;
  reliefSlider.disabled = busy;
}

function finishGeneration(map, startedAt) {
  const elapsed = ((performance.now() - startedAt) / 1000).toFixed(2);
  mapTitle.textContent = "World " + map.seedText;
  mapSeedLabel.textContent = "SEED: " + map.seedText.toUpperCase();
  mapScale.textContent = (map.size / 1000) + " KM × " + (map.size / 1000) + " KM";
  mapCoordinates.textContent = "X " + Math.round(-map.size / 2) + " … " + Math.round(map.size / 2)
    + " · Z " + Math.round(-map.size / 2) + " … " + Math.round(map.size / 2);
  statDimensions.innerHTML = map.size.toLocaleString() + " × " + map.size.toLocaleString() + " <small>blocks</small>";
  statLand.textContent = map.landPercent.toFixed(1) + "% land";
  statElevation.textContent = Math.round(map.min).toLocaleString() + "–" + Math.round(map.max).toLocaleString() + " m est.";
  statusText.textContent = "Preview generated in " + elapsed + "s";
  inspectPosition.textContent = "Select a point on the map";
  inspectElevation.textContent = "—";
  loading.hidden = true;
  isGenerating = false;
  generateButton.classList.remove("is-loading");
  syncGenerationControls();
}

function failGeneration(message) {
  isGenerating = false;
  loading.hidden = true;
  generateButton.classList.remove("is-loading");
  statusText.textContent = "Terrain generation failed · " + message;
  syncGenerationControls();
}

function validTerrainMap(map, options) {
  const expectedLength = options.resolution * options.resolution;
  const isFloat32 = value => ArrayBuffer.isView(value)
    && Object.prototype.toString.call(value) === "[object Float32Array]"
    && value.length === expectedLength;
  return Boolean(map)
    && isFloat32(map.heights)
    && isFloat32(map.temperatures)
    && isFloat32(map.humidities)
    && map.size === options.size
    && map.seedText === options.seedText
    && map.resolution === options.resolution
    && Number.isFinite(map.step)
    && Number.isFinite(map.min)
    && Number.isFinite(map.max)
    && Number.isFinite(map.landPercent)
    && map.landPercent >= 0
    && map.landPercent <= 100;
}

function handleTerrainWorkerMessage(event) {
  const message = event?.data;
  if (!message || message.requestId !== activeGenerationId || !isGenerating) return;

  if (message.type === "progress") {
    if (Number.isFinite(message.progress)) {
      const progress = Math.max(0, Math.min(100, Math.round(message.progress)));
      statusText.textContent = "Sampling terrain fields… " + progress + "%";
    }
    return;
  }

  if (message.type === "cancelled") {
    cancelGeneration();
    return;
  }

  if (message.type === "error") {
    switchToMainThreadFallback(message.message || "Worker reported an error");
    return;
  }

  if (message.type === "complete") {
    if (!validTerrainMap(message.map, activeGenerationOptions)) {
      switchToMainThreadFallback("Worker returned an invalid terrain grid");
      return;
    }
    latestMap = message.map;
    renderMap(latestMap, () => finishGeneration(latestMap, activeGenerationStartedAt));
  }
}

function handleTerrainWorkerFailure(event) {
  if (!isGenerating) {
    if (terrainWorker) terrainWorker.terminate();
    terrainWorker = null;
    computeMode.textContent = "MAIN THREAD FALLBACK";
    return;
  }
  switchToMainThreadFallback(event?.message || "Worker runtime error");
}

function switchToMainThreadFallback(reason) {
  const failedWorker = terrainWorker;
  terrainWorker = null;
  if (failedWorker) failedWorker.terminate();
  computeMode.textContent = "MAIN THREAD FALLBACK";
  if (!isGenerating || !activeGenerationOptions) return;
  statusText.textContent = "Worker unavailable · switching to local sampling";
  startFallbackGeneration(activeGenerationOptions, activeGenerationId);
}

function createTerrainWorker() {
  if (typeof Worker === "undefined") return null;
  try {
    const worker = new Worker(new URL("./terrain-worker.js", import.meta.url), {
      type: "module"
    });
    worker.addEventListener("message", handleTerrainWorkerMessage);
    worker.addEventListener("error", handleTerrainWorkerFailure);
    return worker;
  } catch {
    return null;
  }
}

function startFallbackGeneration(options, requestId) {
  const { size, seed, seedText, reliefScale, resolution } = options;
  const heights = new Float32Array(resolution * resolution);
  const temperatures = new Float32Array(resolution * resolution);
  const humidities = new Float32Array(resolution * resolution);
  const step = size / (resolution - 1);
  let min = Infinity;
  let max = -Infinity;
  let landCount = 0;
  let sampleRow = 0;

  function sampleBatch() {
    if (!isGenerating || requestId !== activeGenerationId) return;
    const endRow = Math.min(resolution, sampleRow + ROWS_PER_FRAME);
    for (let py = sampleRow; py < endRow; py += 1) {
      const z = (py / (resolution - 1) - 0.5) * size;
      for (let px = 0; px < resolution; px += 1) {
        const x = (px / (resolution - 1) - 0.5) * size;
        const sample = sampleTerrain(x, z, size, seed, reliefScale);
        const index = py * resolution + px;
        heights[index] = sample.elevation;
        temperatures[index] = sample.temperature;
        humidities[index] = sample.humidity;
        min = Math.min(min, sample.elevation);
        max = Math.max(max, sample.elevation);
        if (sample.elevation >= SEA_LEVEL) landCount += 1;
      }
    }

    sampleRow = endRow;
    const progress = Math.round(sampleRow / resolution * 70);
    statusText.textContent = "Sampling terrain fields… " + progress + "%";
    if (sampleRow < resolution) {
      window.requestAnimationFrame(sampleBatch);
      return;
    }

    latestMap = {
      heights,
      temperatures,
      humidities,
      size,
      step,
      min,
      max,
      landPercent: landCount / heights.length * 100,
      seedText,
      reliefScale,
      resolution
    };
    renderMap(latestMap, () => finishGeneration(latestMap, activeGenerationStartedAt));
  }

  window.requestAnimationFrame(sampleBatch);
}

function cancelGeneration() {
  if (!isGenerating) return;
  const cancelledRequestId = activeGenerationId;
  if (terrainWorker) {
    try {
      terrainWorker.postMessage({ type: "cancel", requestId: cancelledRequestId });
    } catch {
      // The active request is invalidated locally even if the worker is already gone.
    }
  }
  generationSequence += 1;
  activeGenerationId = "cancelled-" + generationSequence;
  renderToken += 1;
  isGenerating = false;
  loading.hidden = true;
  generateButton.classList.remove("is-loading");
  if (latestMap) {
    mapTitle.textContent = "World " + latestMap.seedText;
    mapSeedLabel.textContent = "SEED: " + latestMap.seedText.toUpperCase();
  } else {
    mapTitle.textContent = "Untitled world";
  }
  statusText.textContent = "Generation cancelled";
  syncGenerationControls();
}

function generateMap() {
  if (isGenerating || isExportingCsv) return;
  renderToken += 1;
  const seedText = seedInput.value.trim() || "realscale";
  const options = {
    size: Number(sizeSelect.value),
    seed: hashSeed(seedText),
    seedText,
    reliefScale: Number(reliefSlider.value) / 100,
    resolution: RESOLUTION
  };
  activeGenerationOptions = options;
  activeGenerationStartedAt = performance.now();
  activeGenerationId = "generation-" + (++generationSequence);
  isGenerating = true;
  generateButton.classList.add("is-loading");
  loading.hidden = false;
  statusText.textContent = "Sampling terrain fields… 0%";
  mapTitle.textContent = "Generating landscape";
  mapSeedLabel.textContent = "SEED: " + seedText.toUpperCase();
  syncGenerationControls();

  if (terrainWorker) {
    try {
      terrainWorker.postMessage({
        type: "generate",
        requestId: activeGenerationId,
        options
      });
      return;
    } catch (error) {
      switchToMainThreadFallback(error?.message || "Unable to start worker");
      return;
    }
  }

  startFallbackGeneration(options, activeGenerationId);
}

function safeSeedFilename(seed) {
  return seed.toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32) || "world";
}

function downloadPreview() {
  if (!latestMap || isGenerating || isExportingCsv) return;
  const link = document.createElement("a");
  link.href = canvas.toDataURL("image/png");
  link.download = "realscale-preview-" + safeSeedFilename(latestMap.seedText) + ".png";
  link.click();
  statusText.textContent = "Preview PNG downloaded · image only, not a Minecraft save";
}

function csvCell(value, protectFormula = false) {
  let text = String(value);
  if (protectFormula && /^[=+@-]/.test(text)) text = "'" + text;
  return '"' + text.replace(/"/g, '""') + '"';
}

function downloadElevationCsv() {
  if (!latestMap || isGenerating || isExportingCsv) return;
  isExportingCsv = true;
  const exportMap = latestMap;
  const chunks = [[
    "sample_x_blocks",
    "sample_z_blocks",
    "estimated_elevation_m",
    "temperature_index",
    "humidity_index",
    "seed",
    "footprint_blocks"
  ].map(value => csvCell(value)).join(",") + "\r\n"];
  let sampleRow = 0;

  syncGenerationControls();
  statusText.textContent = "Preparing elevation CSV… 0%";

  function finishCsvExport() {
    try {
      const csv = new Blob(["\uFEFF", ...chunks], {
        type: "text/csv;charset=utf-8"
      });
      const objectUrl = URL.createObjectURL(csv);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = "realscale-elevation-" + safeSeedFilename(exportMap.seedText)
        + "-" + exportMap.size + "m-preview.csv";
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      statusText.textContent = "Elevation CSV downloaded · " + (RESOLUTION * RESOLUTION).toLocaleString()
        + " estimated samples";
    } catch (error) {
      statusText.textContent = "CSV export failed · " + (error?.message || "unknown error");
    } finally {
      isExportingCsv = false;
      syncGenerationControls();
    }
  }

  function buildCsvBatch() {
    const endRow = Math.min(RESOLUTION, sampleRow + ROWS_PER_FRAME);
    const batchRows = [];
    for (let py = sampleRow; py < endRow; py += 1) {
      const z = Math.round((py / (RESOLUTION - 1) - 0.5) * exportMap.size);
      for (let px = 0; px < RESOLUTION; px += 1) {
        const x = Math.round((px / (RESOLUTION - 1) - 0.5) * exportMap.size);
        const index = py * RESOLUTION + px;
        batchRows.push([
          x,
          z,
          exportMap.heights[index].toFixed(1),
          exportMap.temperatures[index].toFixed(4),
          exportMap.humidities[index].toFixed(4),
          exportMap.seedText,
          exportMap.size
        ].map((value, column) => csvCell(value, column === 5)).join(","));
      }
    }

    chunks.push(batchRows.join("\r\n") + (endRow < RESOLUTION ? "\r\n" : ""));
    sampleRow = endRow;
    const progress = Math.round(sampleRow / RESOLUTION * 100);
    statusText.textContent = "Preparing elevation CSV… " + progress + "%";
    if (sampleRow < RESOLUTION) {
      window.requestAnimationFrame(buildCsvBatch);
      return;
    }
    finishCsvExport();
  }

  window.requestAnimationFrame(buildCsvBatch);
}

function inspectMap(event) {
  if (!latestMap) return;
  const rect = canvas.getBoundingClientRect();
  const px = clamp(Math.floor((event.clientX - rect.left) / rect.width * RESOLUTION), 0, RESOLUTION - 1);
  const py = clamp(Math.floor((event.clientY - rect.top) / rect.height * RESOLUTION), 0, RESOLUTION - 1);
  const x = Math.round((px / (RESOLUTION - 1) - 0.5) * latestMap.size);
  const z = Math.round((py / (RESOLUTION - 1) - 0.5) * latestMap.size);
  const elevation = Math.round(latestMap.heights[py * RESOLUTION + px]);
  inspectPosition.textContent = "X " + x.toLocaleString() + " · Z " + z.toLocaleString();
  inspectElevation.textContent = elevation.toLocaleString() + " m est.";
  mapCoordinates.textContent = "X " + x.toLocaleString() + " · Z " + z.toLocaleString();
}

function setView(view) {
  currentView = view;
  document.querySelectorAll(".view-button").forEach(button => {
    button.classList.toggle("active", button.dataset.view === view);
  });
  reliefLegend.hidden = view !== "relief";
  biomeLegend.hidden = view !== "biomes";
  if (latestMap && !isGenerating && !isExportingCsv) {
    statusText.textContent = "Updating map view…";
    renderMap(latestMap, () => {
      statusText.textContent = "Map view updated";
    });
  }
}

randomSeedButton.addEventListener("click", () => {
  const bytes = new Uint32Array(2);
  crypto.getRandomValues(bytes);
  seedInput.value = "rs-" + bytes[0].toString(16) + "-" + bytes[1].toString(16);
  seedInput.focus();
});
reliefSlider.addEventListener("input", () => {
  reliefValue.textContent = reliefSlider.value + "%";
});
generateButton.addEventListener("click", generateMap);
downloadPreviewButton.addEventListener("click", downloadPreview);
downloadCsvButton.addEventListener("click", downloadElevationCsv);
cancelButton.addEventListener("click", cancelGeneration);
refreshButton.addEventListener("click", generateMap);
sizeSelect.addEventListener("change", () => {
  statusText.textContent = "World size changed · regenerate to update preview";
});
document.querySelectorAll(".view-button").forEach(button => {
  button.addEventListener("click", () => setView(button.dataset.view));
});
canvas.addEventListener("click", inspectMap);

terrainWorker = createTerrainWorker();
computeMode.textContent = terrainWorker ? "WEB WORKER" : "MAIN THREAD FALLBACK";
generateMap();
