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

export function clamp(value, low, high) {
  return Math.max(low, Math.min(high, value));
}

function smoothstep(edge0, edge1, value) {
  const t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
}

export function hashSeed(text) {
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

const UINT64_MASK = (1n << 64n) - 1n;
const SITE_GEOMETRY_CACHE = new Map();

function mix64(value) {
  let hash = value & UINT64_MASK;
  hash ^= hash >> 30n;
  hash = (hash * 0xbf58476d1ce4e5b9n) & UINT64_MASK;
  hash ^= hash >> 27n;
  hash = (hash * 0x94d049bb133111ebn) & UINT64_MASK;
  return (hash ^ (hash >> 31n)) & UINT64_MASK;
}

function siteHash(cellX, cellZ) {
  const input = BigInt(cellX) * 0x9e3779b97f4a7c15n
    ^ BigInt(cellZ) * 0xc2b2ae3d27d4eb4fn
    ^ 0x4d3cdbe3f81907a5n;
  return mix64(input);
}

function centerOffsetX(hash) {
  return Number((hash >> 11n) % 301n) - 150;
}

function centerOffsetZ(hash) {
  return Number((hash >> 32n) % 301n) - 150;
}

export function siteGeometry(cellX, cellZ) {
  const key = cellX + "," + cellZ;
  const cached = SITE_GEOMETRY_CACHE.get(key);
  if (cached) return cached;

  const hash = siteHash(cellX, cellZ);
  const centerX = cellX * 2048 + 1024 + centerOffsetX(hash);
  const centerZ = cellZ * 2048 + 1024 + centerOffsetZ(hash);
  const sinkHash = mix64(hash ^ 0x6a09e667f3bcc909n);
  const site = Object.freeze({
    centerX,
    centerZ,
    sinkX: centerX + centerOffsetX(sinkHash) * 1.8,
    sinkZ: centerZ + centerOffsetZ(sinkHash) * 1.8
  });
  SITE_GEOMETRY_CACHE.set(key, site);
  return site;
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

function familyDelta(index, original, continent, signal, x, z) {
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
    const cellX = Math.floor(Math.trunc(x) / 2048);
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
    const blockX = Math.trunc(x);
    const blockZ = Math.trunc(z);
    const cellX = Math.floor(blockX / 2048);
    const cellZ = Math.floor(blockZ / 2048);
    const site = siteGeometry(cellX, cellZ);
    const radius = clamp(family.wavelength * 0.025, 250, 800);
    let shape = 0;
    if (Math.abs(blockX - site.centerX) < radius && Math.abs(blockZ - site.centerZ) < radius) {
      const radial = Math.hypot(blockX - site.centerX, blockZ - site.centerZ) / radius;
      if (radial < 1) {
        const shield = Math.pow(Math.max(0, 1 - radial), 1.35);
        const caldera = 0.16 * Math.exp(-Math.pow(radial / 0.12, 2));
        shape = shield - caldera;
      }
    }
    const result = original + landGate * family.relief * 0.55 * Math.max(family.macro, 0.12) * shape;
    return result - original;
  }
  if (index === 4) {
    const blockX = Math.trunc(x);
    const blockZ = Math.trunc(z);
    const cellX = Math.floor(blockX / 2048);
    const cellZ = Math.floor(blockZ / 2048);
    const site = siteGeometry(cellX, cellZ);
    const towerRadius = clamp(family.wavelength * 0.025, 80, 450);
    const sinkRadius = clamp(family.wavelength * 0.012, 40, 200);
    let tower = 0;
    let sink = 0;
    if (Math.abs(blockX - site.centerX) < towerRadius && Math.abs(blockZ - site.centerZ) < towerRadius) {
      tower = 1 - smoothstep(towerRadius * 0.55, towerRadius, Math.hypot(blockX - site.centerX, blockZ - site.centerZ));
    }
    if (Math.abs(blockX - site.sinkX) < sinkRadius && Math.abs(blockZ - site.sinkZ) < sinkRadius) {
      sink = 1 - smoothstep(sinkRadius * 0.45, sinkRadius, Math.hypot(blockX - site.sinkX, blockZ - site.sinkZ));
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

export function sampleTerrain(x, z, size, seed, reliefScale) {
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
        if (weights[i] > 0.01) weightedDelta += familyDelta(i, original, continent, signal, x, z) * (weights[i] / total);
      }
      value += weightedDelta * coverage * reliefScale;
    }
  }

  const elevation = clamp(SEA_LEVEL + value * 600, -180, 1750);
  return { elevation, temperature, humidity };
}

export const TERRAIN_FAMILY_NAMES = Object.freeze(FAMILIES.map(family => family.name));
