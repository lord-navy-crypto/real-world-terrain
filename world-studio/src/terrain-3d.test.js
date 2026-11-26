import test from "node:test";
import assert from "node:assert/strict";
import { buildIsometricMesh } from "./terrain-3d.js";

function makeMap(resolution = 16) {
  const count = resolution * resolution;
  const heights = new Float32Array(count);
  const temperatures = new Float32Array(count);
  const humidities = new Float32Array(count);
  let min = Infinity;
  let max = -Infinity;

  for (let row = 0; row < resolution; row += 1) {
    for (let column = 0; column < resolution; column += 1) {
      const index = row * resolution + column;
      const height = 80 + column * 9 + row * 4 + Math.sin(column * 0.4) * 30;
      heights[index] = height;
      temperatures[index] = 0.1 - row / resolution * 0.4;
      humidities[index] = -0.2 + column / resolution * 0.5;
      min = Math.min(min, height);
      max = Math.max(max, height);
    }
  }

  return {
    heights,
    temperatures,
    humidities,
    size: 4096,
    step: 4096 / (resolution - 1),
    min,
    max,
    landPercent: 100,
    seedText: "isometric-fixture",
    reliefScale: 1,
    resolution
  };
}

test("isometric mesh covers the full sample grid with two triangles per cell", () => {
  const mesh = buildIsometricMesh(makeMap(16));

  assert.equal(mesh.gridSize, 16);
  assert.equal(mesh.vertices.length, 256);
  assert.equal(mesh.cells.length, 225);
  assert.equal(mesh.vertices[0].sampleIndex, 0);
  assert.equal(mesh.vertices[15].sampleIndex, 15);
  assert.equal(mesh.vertices[240].sampleIndex, 240);
  assert.equal(mesh.vertices[255].sampleIndex, 255);
  assert.equal(mesh.vertices[0].gridIndex, 0);
  assert.equal(mesh.vertices[255].gridIndex, 255);
});

test("isometric projection returns finite screen coordinates and local slopes", () => {
  const mesh = buildIsometricMesh(makeMap(16));

  for (const vertex of mesh.vertices) {
    assert.ok(Number.isFinite(vertex.x));
    assert.ok(Number.isFinite(vertex.y));
    assert.ok(Number.isFinite(vertex.depth));
    assert.ok(Number.isFinite(vertex.slope));
    assert.ok(vertex.slope >= 0);
  }
});

test("isometric cells are sorted from back to front for painter-order rendering", () => {
  const mesh = buildIsometricMesh(makeMap(16));
  for (let index = 1; index < mesh.cells.length; index += 1) {
    assert.ok(mesh.cells[index].depth >= mesh.cells[index - 1].depth);
  }
});

test("camera yaw, pitch, and zoom change projected terrain coordinates", () => {
  const map = makeMap(16);
  const standard = buildIsometricMesh(map);
  const rotated = buildIsometricMesh(map, { angle: 0.9, pitch: 1.05, zoom: 1.35 });

  assert.notEqual(rotated.vertices[0].x, standard.vertices[0].x);
  assert.notEqual(rotated.vertices[0].y, standard.vertices[0].y);
});

test("invalid terrain arrays are rejected before mesh projection", () => {
  const map = makeMap(16);
  map.heights = new Float32Array(12);
  assert.throws(() => buildIsometricMesh(map), /terrain arrays/);
});

test("full 384 sample maps are decimated to a stable 97 by 97 render mesh", () => {
  const mesh = buildIsometricMesh(makeMap(384));

  assert.equal(mesh.gridSize, 97);
  assert.equal(mesh.vertices.length, 97 * 97);
  assert.equal(mesh.cells.length, 96 * 96);
  assert.equal(mesh.vertices[0].sampleIndex, 0);
  assert.equal(mesh.vertices.at(-1).sampleIndex, 384 * 384 - 1);
});
