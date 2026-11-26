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

function generateMap() {
  if (isGenerating) return;
  const seedText = seedInput.value.trim() || "realscale";
  const seed = hashSeed(seedText);
  const size = Number(sizeSelect.value);
  const reliefScale = Number(reliefSlider.value) / 100;
  const started = performance.now();
  const heights = new Float32Array(RESOLUTION * RESOLUTION);
  const temperatures = new Float32Array(RESOLUTION * RESOLUTION);
  const humidities = new Float32Array(RESOLUTION * RESOLUTION);
  const step = size / (RESOLUTION - 1);
  let min = Infinity;
  let max = -Infinity;
  let landCount = 0;
  let sampleRow = 0;

  isGenerating = true;
  generateButton.disabled = true;
  generateButton.classList.add("is-loading");
  loading.hidden = false;
  statusText.textContent = "Sampling terrain fields… 0%";
  mapTitle.textContent = "Generating landscape";
  mapSeedLabel.textContent = "SEED: " + seedText.toUpperCase();

  function finishGeneration() {
    latestMap = {
      heights,
      size,
      step,
      min,
      max,
      landPercent: landCount / heights.length * 100,
      seedText
    };
    const elapsed = ((performance.now() - started) / 1000).toFixed(2);
    mapTitle.textContent = "World " + seedText;
    mapScale.textContent = (size / 1000) + " KM × " + (size / 1000) + " KM";
    mapCoordinates.textContent = "X " + Math.round(-size / 2) + " … " + Math.round(size / 2)
      + " · Z " + Math.round(-size / 2) + " … " + Math.round(size / 2);
    statDimensions.innerHTML = size.toLocaleString() + " × " + size.toLocaleString() + " <small>blocks</small>";
    statLand.textContent = latestMap.landPercent.toFixed(1) + "% land";
    statElevation.textContent = Math.round(min).toLocaleString() + "–" + Math.round(max).toLocaleString() + " m";
    statusText.textContent = "Preview generated in " + elapsed + "s";
    inspectPosition.textContent = "Select a point on the map";
    inspectElevation.textContent = "—";
    loading.hidden = true;
    generateButton.disabled = false;
    generateButton.classList.remove("is-loading");
    isGenerating = false;
  }

  function renderMap() {
    let renderedView = currentView;
    let renderRow = 0;
    let image = ctx.createImageData(RESOLUTION, RESOLUTION);

    function renderBatch() {
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
          const left = heights[py * RESOLUTION + Math.max(0, px - 1)];
          const right = heights[py * RESOLUTION + Math.min(RESOLUTION - 1, px + 1)];
          const up = heights[Math.max(0, py - 1) * RESOLUTION + px];
          const down = heights[Math.min(RESOLUTION - 1, py + 1) * RESOLUTION + px];
          const dx = (right - left) / Math.max(step * 2, 1);
          const dz = (down - up) / Math.max(step * 2, 1);
          const slope = Math.hypot(dx, dz);
          const shade = clamp(0.86 - dx * 0.8 - dz * 0.55, 0.62, 1.16);
          const color = colorForTerrain(heights[index], temperatures[index], humidities[index], slope, renderedView);
          const contourValue = (heights[index] - SEA_LEVEL) / 120;
          const isContour = heights[index] > SEA_LEVEL + 30
            && Math.abs(contourValue - Math.round(contourValue)) < 0.026;
          const contourShade = isContour ? 0.74 : 1;
          const pixel = index * 4;
          pixels[pixel] = clamp(color[0] * shade * contourShade, 0, 255);
          pixels[pixel + 1] = clamp(color[1] * shade * contourShade, 0, 255);
          pixels[pixel + 2] = clamp(color[2] * shade * contourShade, 0, 255);
          pixels[pixel + 3] = 255;
        }
      }

      renderRow = endRow;
      const progress = 70 + Math.round(renderRow / RESOLUTION * 30);
      statusText.textContent = "Rendering " + renderedView + " map… " + progress + "%";
      if (renderRow < RESOLUTION) {
        window.requestAnimationFrame(renderBatch);
        return;
      }

      ctx.putImageData(image, 0, 0);
      finishGeneration();
    }

    window.requestAnimationFrame(renderBatch);
  }

  function sampleBatch() {
    const endRow = Math.min(RESOLUTION, sampleRow + ROWS_PER_FRAME);
    for (let py = sampleRow; py < endRow; py += 1) {
      const z = (py / (RESOLUTION - 1) - 0.5) * size;
      for (let px = 0; px < RESOLUTION; px += 1) {
        const x = (px / (RESOLUTION - 1) - 0.5) * size;
        const sample = sampleTerrain(x, z, size, seed, reliefScale);
        const index = py * RESOLUTION + px;
        heights[index] = sample.elevation;
        temperatures[index] = sample.temperature;
        humidities[index] = sample.humidity;
        min = Math.min(min, sample.elevation);
        max = Math.max(max, sample.elevation);
        if (sample.elevation >= SEA_LEVEL) landCount += 1;
      }
    }

    sampleRow = endRow;
    const progress = Math.round(sampleRow / RESOLUTION * 70);
    statusText.textContent = "Sampling terrain fields… " + progress + "%";
    if (sampleRow < RESOLUTION) {
      window.requestAnimationFrame(sampleBatch);
      return;
    }
    renderMap();
  }

  window.requestAnimationFrame(sampleBatch);
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
  inspectElevation.textContent = elevation.toLocaleString() + " m";
  mapCoordinates.textContent = "X " + x.toLocaleString() + " · Z " + z.toLocaleString();
}

function setView(view) {
  currentView = view;
  document.querySelectorAll(".view-button").forEach(button => {
    button.classList.toggle("active", button.dataset.view === view);
  });
  reliefLegend.hidden = view !== "relief";
  biomeLegend.hidden = view !== "biomes";
  if (latestMap) generateMap();
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
refreshButton.addEventListener("click", generateMap);
sizeSelect.addEventListener("change", () => {
  statusText.textContent = "World size changed · regenerate to update preview";
});
document.querySelectorAll(".view-button").forEach(button => {
  button.addEventListener("click", () => setView(button.dataset.view));
});
canvas.addEventListener("click", inspectMap);

generateMap();
