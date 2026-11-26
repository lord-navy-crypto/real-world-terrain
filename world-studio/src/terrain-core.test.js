import test from "node:test";
import assert from "node:assert/strict";
import { familyDelta, familySuitability, hashSeed, sampleTerrain, siteGeometry, TERRAIN_FAMILY_NAMES, TERRAIN_FAMILY_PROFILES } from "./terrain-core.js";

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

test("volcanic and karst site geometry matches the Java world-absolute hash", () => {
  assert.deepEqual(siteGeometry(0, 0), {
    centerX: 947,
    centerZ: 1121,
    sinkX: 1035.2,
    sinkZ: 1310
  });
  assert.deepEqual(siteGeometry(-1, 2), {
    centerX: -990,
    centerZ: 5029,
    sinkX: -810,
    sinkZ: 5129.8
  });
});

test("fitted morphology coefficients remain aligned with the Java Natural Automate profile", () => {
  assert.deepEqual(TERRAIN_FAMILY_PROFILES.map(profile => [
    profile.name, profile.macro, profile.meso, profile.micro, profile.relief,
    profile.ridge, profile.cliff, profile.valley, profile.ridgeIndex,
    profile.anisotropy, profile.angle, profile.wavelength
  ]), [
    ["alpine", 0.920334, 0.388031, 0.04917, 0.965848, 0.353898, 0.442335, 0.493463, 0.506537, 1.018168, -Math.PI, 18151.56],
    ["glacial", 0.976883, 0.211697, 0.029717, 0.713694, 0.347762, 0.176078, 0.502144, 0.497856, 0.990941, 1.570796, 17311.0169],
    ["canyon", 0.918247, 0.387271, 0.082729, 0.363634, 0.597412, 0.291044, 0.520377, 0.479623, 1.045483, -2.243785, 34289.3669],
    ["volcanic", 0.993788, 0.111131, 0.005939, 0.735566, 0.129572, 0, 0.496722, 0.503278, 1.058617, -1.800358, 25035.3016],
    ["karst", 0.860842, 0.497984, 0.104702, 0.263639, 0.30154, 0.19569, 0.483278, 0.516722, 1.095601, 1.863521, 7936.9415],
    ["dune", 0.972392, 0.222451, 0.070499, 0.147684, 0.211133, 0, 0.52343, 0.47657, 1.160985, 1.570796, 4775.6305]
  ]);
});

test("cold wet highlands prefer the glacial morphology family", () => {
  const glacial = familySuitability(1, 0.84, -0.85, 0.75, -0.5);
  const alpine = familySuitability(0, 0.84, -0.85, 0.75, -0.5);
  assert.ok(glacial > 0.85);
  assert.ok(glacial > alpine);
});

test("warm dry lowlands prefer dune morphology", () => {
  const dune = familySuitability(5, 0.05, 0.9, -0.9, 5 / 6);
  const alpine = familySuitability(0, 0.05, 0.9, -0.9, 5 / 6);
  assert.ok(dune > 0.95);
  assert.equal(alpine, 0);
});

test("all six morphology families are reachable under representative climate and terrain inputs", () => {
  const inputs = [
    [1.5, -0.9, 0.1, -5 / 6],
    [0.84, -0.85, 0.75, -0.5],
    [0.2, 0, -0.9, -1 / 6],
    [0.5, 0.9, 0, 1 / 6],
    [0.3, 0.5, 0.9, 0.5],
    [0.05, 0.9, -0.9, 5 / 6]
  ];
  inputs.forEach(([original, temperature, humidity, region], index) => {
    assert.ok(
      familySuitability(index, original, temperature, humidity, region) > 0.85,
      TERRAIN_FAMILY_NAMES[index] + " should be reachable in the composite"
    );
  });
});

test("volcanic and karst kernel centers match the Java block-coordinate geometry", () => {
  const site = siteGeometry(0, 0);
  const volcanic = TERRAIN_FAMILY_PROFILES[3];
  const volcanicExpected = volcanic.relief * 0.55 * Math.max(volcanic.macro, 0.12) * 0.84;
  assert.ok(Math.abs(familyDelta(3, 1, 0.5, 0, site.centerX, site.centerZ) - volcanicExpected) < 1e-12);

  const karst = TERRAIN_FAMILY_PROFILES[4];
  const signal = 0.25;
  const noiseTower = signal * 0.08 * Math.max(0.12, karst.macro + 0.5 * karst.micro);
  const karstExpected = karst.relief * (0.36 * Math.max(karst.ridgeIndex, 0.12) + noiseTower);
  assert.ok(Math.abs(familyDelta(4, 1, 0.5, signal, site.centerX, site.centerZ) - karstExpected) < 1e-12);
});
