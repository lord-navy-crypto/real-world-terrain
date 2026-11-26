const MAX_GRID_SIZE = 97;

function isFloat32Array(value, expectedLength) {
  return ArrayBuffer.isView(value)
    && Object.prototype.toString.call(value) === "[object Float32Array]"
    && value.length === expectedLength;
}

function validateMap(map) {
  const resolution = map?.resolution;
  if (!Number.isInteger(resolution) || resolution < 2 || resolution > 1024) {
    throw new TypeError("Invalid terrain resolution");
  }
  const expectedLength = resolution * resolution;
  if (!isFloat32Array(map.heights, expectedLength)
    || !isFloat32Array(map.temperatures, expectedLength)
    || !isFloat32Array(map.humidities, expectedLength)) {
    throw new TypeError("Invalid terrain arrays");
  }
  if (!Number.isFinite(map.size) || map.size <= 0
    || !Number.isFinite(map.step) || map.step <= 0
    || !Number.isFinite(map.min)
    || !Number.isFinite(map.max)) {
    throw new TypeError("Invalid terrain metadata");
  }
  return resolution;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

export function buildIsometricMesh(map, camera = {}) {
  const resolution = validateMap(map);
  const gridSize = Math.min(MAX_GRID_SIZE, resolution);
  const angle = Number.isFinite(camera.angle) ? camera.angle : -0.65;
  const pitch = clamp(Number.isFinite(camera.pitch) ? camera.pitch : 0.78, 0.25, 1.2);
  const zoom = clamp(Number.isFinite(camera.zoom) ? camera.zoom : 1, 0.65, 1.8);
  const cosine = Math.cos(angle);
  const sine = Math.sin(angle);
  const heightRange = Math.max(map.max - map.min, 1);
  const vertices = new Array(gridSize * gridSize);

  for (let gridZ = 0; gridZ < gridSize; gridZ += 1) {
    const sampleZ = Math.round(gridZ * (resolution - 1) / (gridSize - 1));
    const worldZ = sampleZ / (resolution - 1) * 2 - 1;
    for (let gridX = 0; gridX < gridSize; gridX += 1) {
      const sampleX = Math.round(gridX * (resolution - 1) / (gridSize - 1));
      const worldX = sampleX / (resolution - 1) * 2 - 1;
      const sampleIndex = sampleZ * resolution + sampleX;
      const height = map.heights[sampleIndex];
      const normalizedHeight = clamp((height - map.min) / heightRange, 0, 1);
      const rotatedX = worldX * cosine - worldZ * sine;
      const rotatedDepth = worldX * sine + worldZ * cosine;

      const leftX = Math.max(0, sampleX - 1);
      const rightX = Math.min(resolution - 1, sampleX + 1);
      const upZ = Math.max(0, sampleZ - 1);
      const downZ = Math.min(resolution - 1, sampleZ + 1);
      const left = map.heights[sampleZ * resolution + leftX];
      const right = map.heights[sampleZ * resolution + rightX];
      const up = map.heights[upZ * resolution + sampleX];
      const down = map.heights[downZ * resolution + sampleX];
      const dxDistance = Math.max((rightX - leftX) * map.step, 1);
      const dzDistance = Math.max((downZ - upZ) * map.step, 1);
      const dx = (right - left) / dxDistance;
      const dz = (down - up) / dzDistance;

      vertices[sampleIndexForGrid(gridZ, gridX, gridSize)] = {
        x: 192 + rotatedX * 128 * zoom,
        y: 192 + rotatedDepth * 84 * Math.sin(pitch) * zoom - normalizedHeight * 96 * zoom,
        depth: rotatedDepth * Math.cos(pitch) + normalizedHeight * Math.sin(pitch) * 0.35,
        sampleIndex,
        slope: Math.hypot(dx, dz)
      };
    }
  }

  const cells = [];
  for (let gridZ = 0; gridZ < gridSize - 1; gridZ += 1) {
    for (let gridX = 0; gridX < gridSize - 1; gridX += 1) {
      const a = gridZ * gridSize + gridX;
      const b = a + 1;
      const c = a + gridSize;
      const d = c + 1;
      const depth = (
        vertices[a].depth + vertices[b].depth + vertices[c].depth + vertices[d].depth
      ) / 4;
      cells.push({ a, b, c, d, depth });
    }
  }
  cells.sort((left, right) => left.depth - right.depth);

  return { gridSize, vertices, cells, angle, pitch, zoom };
}

function sampleIndexForGrid(gridZ, gridX, gridSize) {
  return gridZ * gridSize + gridX;
}
