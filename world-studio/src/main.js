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
const SEA_LEVEL = 63;
const TAU = Math.PI * 2;
const FAMILIES = [
  { name: "alpine", macro: 0.920334, meso: 0.388031, micro: 0.04917, relief: 0.965848, ridge: 0.353898, cliff: 0.442335, valley: 0.493463, ridgeIndex: 0.506537, anisotropy: 1.018168, angle: -Math.PI, wavelength: 18151.56 },
  { name: "glacial", macro: 0.976883, meso: 0.211697, micro: 0.029717, relief: 0.713694, ridge: 0.347762, cliff: 0.176078, valley: 0.502144, ridgeIndex: 0.497856, anisotropy: 0.990941, angle: 1.570796, wavelength: 17311.0169 },
  { name: "canyon", macro: 0.918247, meso: 0.387271, micro: 0.082729, relief: 0.363634, ridge: 0.597412, cliff: 0.291044, valley: 0.520377, ridgeIndex: 0.479623, anisotropy: 1.045483, angle: -2.243785, wavelength: 34289.3669 },
  { name: "volcanic", macro: 0.993788, meso: 0.111131, micro: 0.005939, relief: 0.735566, ridge: 0.129572, cliff: 0, valley: 0.496722, ridgeIndex: 0.503278, anisotropy: 1.058617, angle: -1.800358, wavelength: 25035.3016 },
  { name: "karst", macro: 0.860842, meso: 0.497984, micro: 0.104702, relief: 0.263639, ridge: 0.30154, cliff: 0.19569, valley: 0.483278, ridgeIndex: 0.516722, anisotropy: 1.095601, angle: 1.863521, wavelength: 7936.9415 },
  { name: "dune", macro: 0.972392, meso: 0.222451, micro: 0.070499, relief: 0.147684, ridge: 0.211133, cliff: 0, valley: 0.52343, ridgeIndex: 0.47657, anisotropy: 1.160985, angle: 1.570796, wavelength: 4775.6305 }
];

let currentView = "relief";
let latestMap = null;
let isGenerating = false;

function clamp(value, low, high) {
  return Math.max(low, Math.min(high, value));
}

function smoothstep(edge0, edge1, value) {
  const t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
}

function hashSeed(text) {
  let hash = 2166136261;
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  hash ^= hash >>> 16;
  hash = Math.imul(hash, 0x7feb352d);
  hash ^= hash >>> 15;
  hash = Math.imul(hash, 0x846ca68b);
  hash ^= hash >>> 16;
  return hash >>> 0;
}

function hash2(seed, x, z, salt = 0) {
  let h = seed ^ Math.imul(x | 0, 0x9e3779b1) ^ Math.imul(z | 0, 0x85ebca77) ^ salt;
  h ^= h >>> 16;
  h = Math.imul(h, 0x7feb352d);
  h ^= h >>> 15;
  h = Math.imul(h, 0x846ca68b);
  h ^= h >>> 16;
  return h >>> 0;
}

function random01(seed, x, z, salt = 0) {
  return hash2(seed, x, z, salt) / 4294967295;
}

function fade(t) {
  return t * t * (3 - 2 * t);
}

function noise2D(seed, x, z, salt = 0) {
  const x0 = Math.floor(x);
  const z0 = Math.floor(z);
  const tx = fade(x - x0);
  const tz = fade(z - z0);
  const a = random01(seed, x0, z0, salt) * 2 - 1;
  const b = random01(seed, x0 + 1, z0, salt) * 2 - 1;
  const c = random01(seed, x0, z0 + 1, salt) * 2 - 1;
  const d = random01(seed, x0 + 1, z0 + 1, salt) * 2 - 1;
  const top = a + (b - a) * tx;
  const bottom = c + (d - c) * tx;
  return top + (bottom - top) * tz;
}

function fbm(seed, x, z, octaves = 4, salt = 0) {
  let total = 0;
  let amplitude = 0.5;
  let frequency = 1;
  let normalization = 0;
  for (let octave = 0; octave < octaves; octave += 1) {
    total += noise2D(seed, x * frequency, z * frequency, salt + octave * 1013) * amplitude;
    normalization += amplitude;
    amplitude *= 0.5;
    frequency *= 2;
  }
  return total / normalization;
}

function siteGeometry(seed, cellX, cellZ) {
  const h = hash2(seed ^ 0x4d3cdbe3, cellX, cellZ, 0x8197a5d3);
  const centerX = cellX * 2048 + 1024 + (h % 301) - 150;
  const centerZ = cellZ * 2048 + 1024 + ((h >>> 9) % 301) - 150;
  const sinkHash = hash2(seed ^ 0x6a09e667, cellX, cellZ, h);
  return {
    centerX,
    centerZ,
    sinkX: centerX + ((sinkHash % 301) - 150) * 1.8,
    sinkZ: centerZ + (((sinkHash >>> 10) % 301) - 150) * 1.8
  };
}

function orientedAcross(family, x, z) {
  return x * Math.cos(family.angle) + z * Math.sin(family.angle);
}

function familySuitability(index, original, temp, moisture, region) {
  const center = -5 / 6 + index / 3;
  const regional = 1 - smoothstep(0.18, 0.62, Math.abs(region - center));
  if (regional <= 0) return 0;
  const warmth = smoothstep(-0.45, 0.45, temp);
  const coldness = 1 - smoothstep(-0.45, 0.35, temp);
  const wetness = smoothstep(-0.5, 0.45, moisture);
  const dryness = 1 - smoothstep(-0.15, 0.55, moisture);
  let topography = 0;
  let climate = 0;
  switch (index) {
    case 0:
      topography = smoothstep(0.65, 1.7, original);
      climate = 0.15 + 0.85 * coldness;
      break;
    case 1:
      topography = smoothstep(0.18, 0.62, original);
      climate = coldness * (0.4 + 0.6 * wetness);
      break;
    case 2:
      topography = 0.45 + 0.55 * smoothstep(-0.1, 0.28, original);
      climate = 0.18 + 0.82 * dryness;
      break;
    case 3:
      topography = smoothstep(0.05, 0.42, original);
      climate = 0.6 + 0.4 * warmth;
      break;
    case 4:
      topography = smoothstep(-0.12, 0.22, original) * (1 - smoothstep(0.72, 1.05, original));
      climate = (0.3 + 0.7 * wetness) * (0.4 + 0.6 * warmth);
      break;
    case 5:
      topography = 1 - smoothstep(0.12, 0.58, original);
      climate = warmth * dryness;
      break;
  }
  return clamp(regional * topography * climate, 0, 1);
}

function familyDelta(index, original, continent, signal, x, z, seed) {
  const family = FAMILIES[index];
  const landGate = smoothstep(-0.1, 0.22, continent);
  const across = orientedAcross(family, x, z);

  if (index === 0) {
    const highland = smoothstep(0.65, 1.7, original);
    const phase = TAU * across / family.wavelength;
    const ridgeBands = 0.85 + 0.15 * Math.pow(Math.max(0, Math.cos(phase)), 2);
    const result = original + landGate * highland * family.relief * 0.22 * family.macro * family.ridge * ridgeBands * (1 - Math.abs(signal));
    return result - original;
  }
  if (index === 1) {
    const glacialWidth = clamp(family.wavelength * 0.035, 180, 1600);
    const cellX = Math.floor(x / 2048);
    const centerX = cellX * 2048 + 1024 + glacialWidth * 0.22 * Math.sin(TAU * z / family.wavelength + cellX * 1.17);
    const trough = 1 - smoothstep(glacialWidth * 0.28, glacialWidth, Math.abs(x - centerX));
    const result = original - landGate * family.relief * 0.44 * Math.max(family.valley, 0.12) * trough;
    return result - original;
  }
  if (index === 2) {
    const valleyDistance = Math.abs(Math.sin(Math.PI * across / family.wavelength));
    const trough = 1 - smoothstep(0, 0.18, valleyDistance);
    const shoulder = smoothstep(0.18, 0.25, valleyDistance) * (1 - smoothstep(0.25, 0.42, valleyDistance));
    const result = original - landGate * family.relief * (0.28 * family.meso * family.cliff * Math.abs(signal) + 0.1 * family.valley * trough)
      + landGate * family.relief * 0.025 * family.cliff * shoulder;
    return result - original;
  }
  if (index === 3) {
    const site = siteGeometry(seed, Math.floor(x / 2048), Math.floor(z / 2048));
    const radius = clamp(family.wavelength * 0.025, 250, 800);
    let shape = 0;
    if (Math.abs(x - site.centerX) < radius && Math.abs(z - site.centerZ) < radius) {
      const radial = Math.hypot(x - site.centerX, z - site.centerZ) / radius;
      if (radial < 1) shape = Math.pow(Math.max(0, 1 - radial), 1.35) - 0.16 * Math.exp(-Math.pow(radial / 0.12, 2));
    }
    const result = original + landGate * family.relief * 0.55 * Math.max(family.macro, 0.12) * shape;
    return result - original;
  }
  if (index === 4) {
    const site = siteGeometry(seed, Math.floor(x / 2048), Math.floor(z / 2048));
    const towerRadius = clamp(family.wavelength * 0.025, 80, 450);
    const sinkRadius = clamp(family.wavelength * 0.012, 40, 200);
    let tower = 0;
    let sink = 0;
    if (Math.abs(x - site.centerX) < towerRadius && Math.abs(z - site.centerZ) < towerRadius) {
      tower = 1 - smoothstep(towerRadius * 0.55, towerRadius, Math.hypot(x - site.centerX, z - site.centerZ));
    }
    if (Math.abs(x - site.sinkX) < sinkRadius && Math.abs(z - site.sinkZ) < sinkRadius) {
      sink = 1 - smoothstep(sinkRadius * 0.45, sinkRadius, Math.hypot(x - site.sinkX, z - site.sinkZ));
    }
    const noiseTower = Math.max(0, signal) * 0.08 * Math.max(0.12, family.macro + 0.5 * family.micro);
    const result = original + landGate * family.relief * (0.36 * Math.max(family.ridgeIndex, 0.12) * tower + noiseTower - 0.22 * Math.max(family.valley, 0.12) * sink);
    return result - original;
  }
  const phase = TAU * across / family.wavelength + signal * 0.12;
  const wave = Math.sin(phase) + 0.28 * Math.sin(2 * phase + 0.6);
  const result = original + landGate * family.relief * 0.1 * Math.max(family.macro, 0.12) * family.anisotropy * wave;
  return result - original;
}

function sampleTerrain(x, z, size, seed, reliefScale) {
  const continentalWave = Math.max(8000, size * 1.35);
  const continent = fbm(seed, x / continentalWave, z / continentalWave, 4, 11);
  const land = smoothstep(-0.22, 0.24, continent);
  const broad = fbm(seed, x / Math.max(2400, size * 0.32), z / Math.max(2400, size * 0.32), 4, 71);
  const meso = fbm(seed, x / 1750, z / 1750, 4, 131);
  const micro = fbm(seed, x / 430, z / 430, 3, 191);
  const original = -0.58 + land * (1.28 + 0.5 * broad) + 0.12 * meso + 0.04 * micro;
  const signal = fbm(seed, x / 3100, z / 3100, 3, 251);
  const region = fbm(seed, x / 10500, z / 10500, 3, 311);
  const temperature = clamp(fbm(seed, x / 14500, z / 14500, 3, 401) - Math.abs(z) / Math.max(size * 1.7, 1) * 0.3, -1, 1);
  const humidity = fbm(seed, x / 8900, z / 8900, 3, 461);

  const weights = new Array(6);
  let total = 0;
  let maxSuitability = 0;
  for (let i = 0; i < 6; i += 1) {
    weights[i] = familySuitability(i, original, temperature, humidity, region);
    total += weights[i];
    maxSuitability = Math.max(maxSuitability, weights[i]);
  }

  let value = original;
  if (total > 1e-12) {
    const coverage = smoothstep(0.22, 0.58, maxSuitability);
    if (coverage > 0) {
      let weightedDelta = 0;
      for (let i = 0; i < 6; i += 1) {
        if (weights[i] > 0.01) weightedDelta += familyDelta(i, original, continent, signal, x, z, seed) * (weights[i] / total);
      }
      value += weightedDelta * coverage * reliefScale;
    }
  }

  const elevation = clamp(SEA_LEVEL + value * 600, -180, 1750);
  return { elevation, temperature, humidity };
}

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
  isGenerating = true;
  generateButton.disabled = true;
  generateButton.classList.add("is-loading");
  loading.hidden = false;
  statusText.textContent = "Sampling terrain fields…";
  mapTitle.textContent = "Generating landscape";
  mapSeedLabel.textContent = "SEED: " + seedText.toUpperCase();

  window.setTimeout(() => {
    const heights = new Float32Array(RESOLUTION * RESOLUTION);
    const temperatures = new Float32Array(RESOLUTION * RESOLUTION);
    const humidities = new Float32Array(RESOLUTION * RESOLUTION);
    const step = size / RESOLUTION;
    let min = Infinity;
    let max = -Infinity;
    let landCount = 0;

    for (let py = 0; py < RESOLUTION; py += 1) {
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

    const image = ctx.createImageData(RESOLUTION, RESOLUTION);
    const pixels = image.data;
    for (let py = 0; py < RESOLUTION; py += 1) {
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
        const color = colorForTerrain(heights[index], temperatures[index], humidities[index], slope, currentView);
        const contourValue = (heights[index] - SEA_LEVEL) / 120;
        const isContour = heights[index] > SEA_LEVEL + 30 && Math.abs(contourValue - Math.round(contourValue)) < 0.026;
        const contourShade = isContour ? 0.74 : 1;
        const pixel = index * 4;
        pixels[pixel] = clamp(color[0] * shade * contourShade, 0, 255);
        pixels[pixel + 1] = clamp(color[1] * shade * contourShade, 0, 255);
        pixels[pixel + 2] = clamp(color[2] * shade * contourShade, 0, 255);
        pixels[pixel + 3] = 255;
      }
    }
    ctx.putImageData(image, 0, 0);

    latestMap = { heights, size, step, min, max, landPercent: landCount / heights.length * 100, seedText };
    const elapsed = ((performance.now() - started) / 1000).toFixed(2);
    mapTitle.textContent = "World " + seedText;
    mapScale.textContent = (size / 1000) + " KM × " + (size / 1000) + " KM";
    mapCoordinates.textContent = "X " + Math.round(-size / 2) + " … " + Math.round(size / 2) + " · Z " + Math.round(-size / 2) + " … " + Math.round(size / 2);
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
  }, 24);
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
